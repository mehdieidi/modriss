"""Scope process-page styling and preserve former process-reference URLs."""

from html import escape
from pathlib import Path
from posixpath import dirname, relpath
from urllib.parse import urljoin


def on_page_content(html, page, **kwargs):
    if "<!-- Process documentation is maintained by hand. -->" in page.markdown:
        return f'<div class="process-guide">{html}</div>'
    return html


def on_post_build(config):
    targets = {
        "development-delivery": "guides/full-lifecycle-method.md",
        "operations-maintenance": "process-reference/operations-maintenance.md",
        "cim": "guides/cim-modeling-methodology.md",
        "pim": "guides/pim-modeling-methodology.md",
        "psm": "guides/psm-modeling-methodology.md",
        "artifact": "process-reference/artifact/activities.md",
    }
    redirects = {
        "process-reference/index.md": "guides/full-lifecycle-method.md",
        "process-reference/spem-structure.md": "process-reference/ongoing-activities.md#reuse-process-activities",
    }
    for scope, target in targets.items():
        for section in ("activities", "tasks", "roles", "work-products", "relationships", "guidance", "gates"):
            old = f"process-reference/{scope}/{section}.md"
            if old == target:
                continue
            destination = target
            if section in ("roles", "gates"):
                destination = f"process-reference/{section}.md"
            elif section == "relationships":
                destination = "process-reference/ongoing-activities.md#control-versions-and-trace-changes"
            redirects[old] = destination

    def output_path(source):
        stem = source.removesuffix(".md")
        if stem.endswith("/index"):
            return stem.removesuffix("index") + "index.html"
        return stem + ("/index.html" if config.use_directory_urls else ".html")

    for old, destination in redirects.items():
        target, _, fragment = destination.partition("#")
        old_path = output_path(old)
        target_path = output_path(target)
        relative = relpath(target_path, dirname(old_path))
        if config.use_directory_urls:
            relative = relative.removesuffix("index.html")
        if fragment:
            relative += "#" + fragment
        canonical = target_path.removesuffix("index.html") if config.use_directory_urls else target_path
        canonical = urljoin(config.site_url, canonical)
        output = Path(config.site_dir) / old_path
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            '<!doctype html>\n<html lang="en"><head><meta charset="utf-8">'
            '<title>Process documentation</title><meta name="robots" content="noindex">'
            f'<link rel="canonical" href="{escape(canonical, quote=True)}">'
            f'<meta http-equiv="refresh" content="0; url={escape(relative, quote=True)}">'
            '</head><body><p>This content is now part of the '
            f'<a href="{escape(relative, quote=True)}">updated process guide</a>.</p></body></html>\n',
            encoding="utf-8",
        )
