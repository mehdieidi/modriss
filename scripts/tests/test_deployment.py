"""Deployment guards. Set RUN_DOCKER_TESTS=1 to test Compose and real BuildKit contexts."""

import json
import os
import re
import subprocess
import tempfile
import unittest
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


class DeploymentFilesTest(unittest.TestCase):
    def test_every_reactor_pom_is_copied_before_sources(self):
        dockerfile = read("deploy/Dockerfile")
        reactor = ET.fromstring(read("pom.xml"))
        namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
        for module in reactor.findall("m:modules/m:module", namespace):
            self.assertIn(f"COPY {module.text}/pom.xml {module.text}/", dockerfile)
        self.assertNotIn("COPY . .", dockerfile)
        self.assertIn("id=modriss-maven", dockerfile)
        self.assertLess(dockerfile.index("dependency-plugin:3.9.0:go-offline"),
                        dockerfile.index("COPY apps/backend/src"))

    def test_npm_pins_match_committed_resolutions(self):
        for app in ("admin", "landing"):
            manifest = json.loads(read(f"apps/{app}/package.json"))
            lock = json.loads(read(f"apps/{app}/package-lock.json"))
            for group in ("dependencies", "devDependencies"):
                self.assertEqual(manifest.get(group), lock["packages"][""].get(group))
                for name, version in manifest.get(group, {}).items():
                    self.assertNotEqual("latest", version)
                    if not version.startswith(("^", "~")):
                        resolved = lock["packages"]["node_modules/" + name]["version"]
                        self.assertEqual(version, resolved)

    def test_production_routes_allow_only_api_and_exact_readiness(self):
        caddy = read("infra/caddy/Caddyfile.prod")
        self.assertIn("@backend path /api/* /actuator/health/readiness\n", caddy)
        self.assertIn("not path /actuator/health/readiness\n", caddy)
        self.assertIn("path /actuator /actuator/* /v3/api-docs* /swagger-ui*", caddy)
        self.assertIn("respond @internal 404", caddy)
        api = caddy.split("api.modriss.site {", 1)[1]
        self.assertIn("import backend_routes", api)
        self.assertNotIn("reverse_proxy backend:8080", api)
        self.assertIn("/actuator/health/readiness", read("scripts/deploy-prod.sh"))
        self.assertIn("/actuator/prometheus", read("infra/prometheus/prometheus.yml"))

    def test_production_disables_details_and_swagger(self):
        prod = read("apps/backend/src/main/resources/application-prod.yml")
        self.assertIn("include: health,prometheus", prod)
        self.assertIn("show-details: never", prod)
        self.assertIn("show-components: never", prod)
        self.assertIn("api-docs:\n    enabled: false", prod)
        self.assertIn("swagger-ui:\n    enabled: false", prod)
        self.assertNotIn("SHOW_DETAILS=always", read(".env.example"))
        # Development retains the richer documentation/metrics routes.
        self.assertIn("/actuator/* /v3/api-docs* /swagger-ui*", read("infra/caddy/Caddyfile.dev"))


@unittest.skipUnless(os.environ.get("RUN_DOCKER_TESTS") == "1", "opt-in Docker checks")
class DockerDeploymentTest(unittest.TestCase):
    def test_root_development_include_does_not_build_the_python_image(self):
        config = json.loads(subprocess.check_output(
            ["docker", "compose", "--env-file", str(ROOT / ".env.example"),
             "config", "--no-env-resolution", "--format", "json"],
            cwd=ROOT,
        ))
        frontend = config["services"]["frontend"]
        self.assertEqual("python:3.13-alpine", frontend["image"])
        self.assertNotIn("build", frontend)
        self.assertIn("python3 -m http.server", frontend["command"][-1])

    def test_caddy_production_endpoint_policy_over_http(self):
        # Exercise the real production routes with local HTTP and a dummy backend;
        # this harness never requests TLS certificates or starts the real stack.
        suffix = uuid.uuid4().hex[:12]
        network, backend, edge = (
            f"modriss-policy-{suffix}-{name}" for name in ("net", "backend", "edge")
        )

        def docker(*args):
            return subprocess.check_output(["docker", *args], stderr=subprocess.STDOUT)

        with tempfile.TemporaryDirectory() as temporary:
            config = re.sub(
                r"(?m)^([a-z.]*modriss\.site) \{", r"http://\1:8080 {",
                read("infra/caddy/Caddyfile.prod"),
            )
            config = config.replace("{\n", "{\n\tauto_https off\n", 1)
            path = Path(temporary) / "Caddyfile"
            path.write_text(config, encoding="utf-8")
            stub = (
                "from http.server import BaseHTTPRequestHandler, HTTPServer\n"
                "class Handler(BaseHTTPRequestHandler):\n"
                " def do_GET(self):\n"
                "  self.send_response(200); self.end_headers()\n"
                "  self.wfile.write(('backend:' + self.path).encode())\n"
                "HTTPServer(('0.0.0.0',8080),Handler).serve_forever()\n"
            )
            requests = (
                "import urllib.request, urllib.error, time\n"
                "probe=urllib.request.Request('http://edge:8080/actuator/health/readiness',"
                "headers={'Host':'api.modriss.site'})\n"
                "for attempt in range(60):\n"
                " try: urllib.request.urlopen(probe,timeout=2).close(); break\n"
                " except urllib.error.URLError: time.sleep(0.25)\n"
                "else: raise AssertionError('Caddy test edge did not become ready')\n"
                "for host in ('modriss.site','editor.modriss.site',"
                "'admin.modriss.site','api.modriss.site'):\n"
                " for path in ('/api/example','/actuator/health/readiness',"
                "'/actuator','/actuator/health',"
                "'/actuator/health/readiness/','/actuator/info','/actuator/prometheus','/actuator/env',"
                "'/swagger-ui.html','/swagger-ui/index.html','/v3/api-docs','/v3/api-docs.yaml'):\n"
                "  request=urllib.request.Request('http://edge:8080'+path,headers={'Host':host})\n"
                "  try:\n"
                "   with urllib.request.urlopen(request) as response:\n"
                "    status=response.status; body=response.read()\n"
                "  except urllib.error.HTTPError as error: status=error.code; body=error.read()\n"
                "  expected=200 if path in ('/api/example','/actuator/health/readiness') else 404\n"
                "  assert status==expected,(host,path,status,body)\n"
                "  if expected==200: assert body==('backend:'+path).encode(),(host,path,body)\n"
            )
            try:
                docker("network", "create", network)
                docker("run", "-d", "--name", backend, "--network", network,
                       "--network-alias", "backend", "python:3.13-alpine", "python", "-c", stub)
                docker("run", "-d", "--name", edge, "--network", network,
                       "--network-alias", "edge", "-v", f"{path}:/etc/caddy/Caddyfile:ro",
                       "caddy:2.8-alpine", "caddy", "run", "--config", "/etc/caddy/Caddyfile",
                       "--adapter", "caddyfile")
                docker("run", "--rm", "--network", network, "python:3.13-alpine",
                       "python", "-c", requests)
            finally:
                for container in (edge, backend):
                    subprocess.run(["docker", "rm", "-f", container], capture_output=True)
                subprocess.run(["docker", "network", "rm", network], capture_output=True)

    def compose(self, production=False, bind="127.0.0.1", profile="floci"):
        # Explicit env file and no env resolution prevent reading real service secrets.
        prefixes = ("COMPOSE_", "MODRISS_", "POSTGRES_", "GRAFANA_", "AWS_EMULATOR",
                    "LOCALSTACK_", "DOZZLE_", "MANAGEMENT_", "SPRINGDOC_")
        env = {key: value for key, value in os.environ.items() if not key.startswith(prefixes)}
        env.update(POSTGRES_PASSWORD="test-only-database",
                   GRAFANA_ADMIN_PASSWORD="test-only-grafana", MODRISS_DEV_BIND_ADDRESS=bind)
        command = ["docker", "compose", "--env-file", str(ROOT / ".env.example"),
                   "--profile", profile]
        overlay = "deploy/compose.prod.yaml" if production else "deploy/compose.dev.yaml"
        command += ["-f", "deploy/compose.base.yaml", "-f", overlay]
        command += ["config", "--no-env-resolution", "--format", "json"]
        return json.loads(subprocess.check_output(command, cwd=ROOT, env=env))

    def test_development_ports_are_loopback_with_explicit_lan_override(self):
        for profile in ("floci", "localstack"):
            for bind in ("127.0.0.1", "0.0.0.0"):
                config = self.compose(bind=bind, profile=profile)
                self.assertIn("dozzle", config["services"])
                for service in config["services"].values():
                    for port in service.get("ports", []):
                        self.assertEqual(bind, port["host_ip"])
                frontend = config["services"]["frontend"]
                self.assertNotIn("build", frontend)
                self.assertEqual("python:3.13-alpine", frontend["image"])

    def test_production_only_publishes_caddy_and_preserves_state(self):
        for profile in ("floci", "localstack"):
            config = self.compose(production=True, profile=profile)
            services = config["services"]
            self.assertNotIn("dozzle", services)
            self.assertNotIn("dozzle", services["caddy"]["depends_on"])
            for name, service in services.items():
                if name != "caddy":
                    self.assertFalse(service.get("ports"), name)
            ports = {(p["published"], p["target"], p["protocol"])
                     for p in services["caddy"]["ports"]}
            self.assertEqual({("80", 80, "tcp"), ("443", 443, "tcp"), ("443", 443, "udp")}, ports)
            backend_env = services["backend"]["environment"]
            self.assertEqual("never", backend_env["MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS"])
            self.assertEqual("never", backend_env["MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS"])
            self.assertEqual("false", backend_env["SPRINGDOC_API_DOCS_ENABLED"])
            self.assertEqual("health,prometheus",
                             backend_env["MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE"])
            self.assertIn("build", services["frontend"])
            self.assertFalse(services["frontend"].get("volumes"))
            self.assertEqual(["/var/lib/postgresql/data"],
                             [v["target"] for v in services["postgres"]["volumes"]])
            backend_mounts = {v["target"] for v in services["backend"]["volumes"]}
            self.assertTrue({"/app/uploads", "/app/logs", "/app/mde"}.issubset(backend_mounts))

    def test_buildkit_excludes_environment_canaries_in_every_context(self):
        contexts = ("", "apps/admin", "apps/landing", "apps/frontend", "docs/public-docs")
        for context in contexts:
            with self.subTest(context=context), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                source = directory / "source"
                source.mkdir()
                (source / ".dockerignore").write_text(
                    read(str(Path(context) / ".dockerignore")), encoding="utf-8",
                )
                prefixes = ("", "apps/backend/src/main/resources/", "packages/java/example/",
                            "mde/", "config/", "nested/")
                for prefix in prefixes:
                    for name in (".env", ".env.prod", ".env.local", ".env.example"):
                        canary = source / (prefix + name)
                        canary.parent.mkdir(parents=True, exist_ok=True)
                        canary.write_text("BUILD_CONTEXT_SECRET_CANARY", encoding="utf-8")
                (source / "pom.xml").write_text("safe reactor input", encoding="utf-8")
                java_path = Path("packages/java/platform-storage-api/src/main/java/io/") / (
                    "mehdieidi/modriss/platform/storage/api/PlatformStore.java"
                )
                (source / java_path).parent.mkdir(parents=True, exist_ok=True)
                (source / java_path).write_text("safe Java storage API", encoding="utf-8")
                dockerfile = directory / "Context.Dockerfile"
                dockerfile.write_text("FROM scratch\nCOPY . /\n", encoding="utf-8")
                output = directory / "output"
                subprocess.run(
                    ["docker", "build", "-f", str(dockerfile), "--output",
                     f"type=local,dest={output}", str(source)],
                    check=True, capture_output=True,
                )
                self.assertTrue((output / "pom.xml").exists())
                self.assertTrue((output / java_path).exists())
                self.assertFalse(list(output.rglob(".env*")), context)
