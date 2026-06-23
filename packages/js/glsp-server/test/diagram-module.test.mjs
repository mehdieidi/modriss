import assert from "node:assert/strict";
import test from "node:test";
import { ModlessDiagramConfiguration } from "../dist/modless-diagram-configuration.js";
import { ModlessDiagramModule, ModlessServerModule } from "../dist/modless-diagram-module.js";

test("modless diagram module exposes diagram type", () => {
  const module = new ModlessDiagramModule();
  assert.equal(module.diagramType, "modless-diagram");
});

test("modless diagram configuration provides default GLSP type mapping", () => {
  const config = new ModlessDiagramConfiguration();
  assert.ok(config.typeMapping.has("graph"));
  assert.ok(config.typeMapping.has("node"));
  assert.ok(config.typeMapping.has("edge"));
  assert.equal(config.layoutKind, 1);
});

test("modless server module can register diagram module", () => {
  const server = new ModlessServerModule();
  const configured = server.configureDiagramModule(new ModlessDiagramModule());
  assert.equal(configured, server);
});
