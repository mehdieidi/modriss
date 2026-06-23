import { createServer } from "node:http";
import { WebSocketServer } from "ws";
import { ModlessSession } from "./session.mjs";

const port = Number(process.env.MODLESS_GLSP_PORT || 8081);
const host = process.env.MODLESS_GLSP_HOST || "0.0.0.0";
const pathPrefix = `/${process.env.MODLESS_GLSP_PATH || "modless"}`;
const backendUrl = process.env.MODLESS_BACKEND_URL || "http://127.0.0.1:8080";

const server = createServer((_req, res) => {
  res.writeHead(200, { "Content-Type": "text/plain" });
  res.end("Modless GLSP sidecar\n");
});

const wss = new WebSocketServer({ server, path: pathPrefix });

wss.on("connection", (socket) => {
  let session = null;

  socket.on("message", async (raw) => {
    try {
      const message = JSON.parse(String(raw));
      if (message.kind === "initialize") {
        session = new ModlessSession({
          level: message.level || "cim",
          modelId: message.modelId,
          authToken: message.authToken,
          viewId: message.viewId,
          backendUrl,
        });
        const payload = await session.load();
        socket.send(JSON.stringify({ kind: "model", ...payload }));
        return;
      }
      if (!session) {
        socket.send(JSON.stringify({ kind: "error", message: "Session not initialized" }));
        return;
      }
      if (message.kind === "operation") {
        const payload = await session.applyOperation(message.operation || {});
        socket.send(JSON.stringify({ kind: "model", ...payload }));
        return;
      }
      if (message.kind === "overlays") {
        const payload = await session.applyOperation({
          kind: "set-overlays",
          payload: message.payload || {},
        });
        socket.send(JSON.stringify({ kind: "model", ...payload }));
        return;
      }
      if (message.kind === "reload") {
        const payload = await session.load();
        socket.send(JSON.stringify({ kind: "model", ...payload }));
      }
    } catch (error) {
      socket.send(
        JSON.stringify({
          kind: "error",
          message: error instanceof Error ? error.message : String(error),
        }),
      );
    }
  });
});

server.listen(port, host, () => {
  console.log(`Modless GLSP sidecar listening on ws://${host}:${port}${pathPrefix}`);
});
