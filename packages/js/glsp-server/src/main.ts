import "reflect-metadata";
import { configureELKLayoutModule } from "@eclipse-glsp/layout-elk";
import { LogLevel, WebSocketServerLauncher, createAppModule } from "@eclipse-glsp/server/node.js";
import { Container } from "inversify";
import { ModlessDiagramModule, ModlessServerModule } from "./modless-diagram-module.js";
import { ModlessSourceModelStorage } from "./modless-source-model-storage.js";

const port = Number(process.env.MODLESS_GLSP_PORT || 8081);
const host = process.env.MODLESS_GLSP_HOST || "0.0.0.0";
const path = `/${process.env.MODLESS_GLSP_PATH || "modless"}`;

function resolveLogLevel(): (typeof LogLevel)[keyof typeof LogLevel] {
  const raw = String(process.env.MODLESS_GLSP_LOG_LEVEL || "info").trim().toLowerCase();
  switch (raw) {
    case "debug":
      return LogLevel.debug;
    case "warn":
    case "warning":
      return LogLevel.warn;
    case "error":
      return LogLevel.error;
    default:
      return LogLevel.info;
  }
}

process.on("unhandledRejection", (reason) => {
  console.error("[GLSP-Server] Unhandled promise rejection", reason);
});

process.on("uncaughtException", (error) => {
  console.error("[GLSP-Server] Uncaught exception", error);
});

const appContainer = new Container();
appContainer.load(
  createAppModule({
    logLevel: resolveLogLevel(),
    fileLog: process.env.MODLESS_GLSP_FILE_LOG === "true",
  }),
);

const serverModule = new ModlessServerModule().configureDiagramModule(
  new ModlessDiagramModule(ModlessSourceModelStorage),
  configureELKLayoutModule({
    algorithms: ["layered", "mrtree", "radial", "force", "stress"],
  }),
);

const launcher = appContainer.resolve(WebSocketServerLauncher);
launcher.configure(serverModule);

await launcher.start({ port, host, path });

console.log(
  `[GLSP-Server] Listening on ws://${host}:${port}${path} (logLevel=${process.env.MODLESS_GLSP_LOG_LEVEL || "info"})`,
);
