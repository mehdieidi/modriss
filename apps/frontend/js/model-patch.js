import { MODEL_TYPES } from "./config.js";
import { state } from "./state.js";
import { api } from "./api.js";
import { serializeModel } from "./diagram.js";
import { saveCurrentTabGraphState, syncActiveViewFromVisibleGraph } from "./graph-store.js";

const MAX_PATCH_OPERATIONS = 500;

function pointerSegment(value) {
  return String(value).replaceAll("~", "~0").replaceAll("/", "~1");
}

function pointerJoin(base, segment) {
  return `${base}/${pointerSegment(segment)}`;
}

function jsonEqual(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function keyedById(array) {
  if (!Array.isArray(array) || !array.length) {
    return null;
  }
  if (!array.every((item) => item && typeof item === "object" && String(item.id || "").trim())) {
    return null;
  }
  const ids = array.map((item) => String(item.id));
  return new Set(ids).size === ids.length ? ids : null;
}

function diffJson(previous, next, path = "") {
  if (jsonEqual(previous, next)) {
    return [];
  }
  if (previous === undefined) {
    return [{ op: "add", path: path || "/", value: next }];
  }
  if (next === undefined) {
    return [{ op: "remove", path: path || "/" }];
  }
  if (!previous || !next || typeof previous !== "object" || typeof next !== "object") {
    return [{ op: "replace", path: path || "/", value: next }];
  }
  if (Array.isArray(previous) || Array.isArray(next)) {
    return diffArrays(previous, next, path);
  }
  const operations = [];
  const keys = new Set([...Object.keys(previous), ...Object.keys(next)]);
  for (const key of keys) {
    operations.push(...diffJson(previous[key], next[key], pointerJoin(path, key)));
  }
  return operations;
}

function diffArrays(previous, next, path) {
  if (!Array.isArray(previous) || !Array.isArray(next)) {
    return [{ op: "replace", path: path || "/", value: next }];
  }
  const previousIds = keyedById(previous);
  const nextIds = keyedById(next);
  if (!previousIds || !nextIds) {
    return [{ op: "replace", path: path || "/", value: next }];
  }
  const previousById = new Map(
    previous.map((item, index) => [
      String(item.id),
      {
        item,
        index,
      },
    ]),
  );
  const nextById = new Map(
    next.map((item, index) => [
      String(item.id),
      {
        item,
        index,
      },
    ]),
  );
  if (
    previousIds.filter((id) => nextById.has(id)).join("\u0000") !==
    nextIds.filter((id) => previousById.has(id)).join("\u0000")
  ) {
    return [{ op: "replace", path: path || "/", value: next }];
  }
  const operations = [];
  for (let index = previous.length - 1; index >= 0; index--) {
    const id = String(previous[index].id);
    if (!nextById.has(id)) {
      operations.push({ op: "remove", path: pointerJoin(path, index) });
    }
  }
  for (const [id, entry] of nextById.entries()) {
    const previousEntry = previousById.get(id);
    if (!previousEntry) {
      operations.push({ op: "add", path: pointerJoin(path, "-"), value: entry.item });
      continue;
    }
    operations.push(
      ...diffJson(previousEntry.item, entry.item, pointerJoin(path, previousEntry.index)),
    );
  }
  return operations;
}

export function buildModelPatch(previousModel, nextModel) {
  const operations = diffJson(previousModel || {}, nextModel || {});
  if (!operations.length) {
    return [];
  }
  if (
    operations.length > MAX_PATCH_OPERATIONS ||
    operations.some((operation) => operation.path === "/")
  ) {
    return null;
  }
  return operations;
}

export async function flushCurrentModelPatch({ name, rethrow = false } = {}) {
  if (!state.modelId || !MODEL_TYPES[state.activeType]) {
    return false;
  }
  syncActiveViewFromVisibleGraph();
  const nextModel = serializeModel();
  const operations = buildModelPatch(state.baseModel || {}, nextModel);
  if (operations === null) {
    return false;
  }
  if (!operations.length) {
    state.baseModel = structuredClone(nextModel);
    return true;
  }
  try {
    const updated = await api(`/${MODEL_TYPES[state.activeType].apiType}/${state.modelId}`, {
      method: "PATCH",
      body: JSON.stringify({
        name,
        operations,
        expectedRevision: state.modelRevision || 1,
      }),
    });
    state.baseModel = structuredClone(nextModel);
    if (updated && typeof updated === "object") {
      state.modelRevision = Number(updated.revision) || state.modelRevision;
    }
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].modelId = state.modelId;
      state.tabs[state.activeType].modelRevision = state.modelRevision;
      state.tabs[state.activeType].baseModel = state.baseModel;
      state.tabs[state.activeType].diagram = state.diagram;
      saveCurrentTabGraphState(state.activeType);
    }
    return updated || true;
  } catch (error) {
    const stalePatchPath =
      error?.status === 400 &&
      /Patch (replace|remove) path does not exist:/i.test(String(error?.message || ""));
    if (stalePatchPath) {
      return false;
    }
    if (rethrow) {
      throw error;
    }
    return false;
  }
}
