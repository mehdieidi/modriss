import { clearDirtySaveHints, consumeViewSyncNeeded, getDirtySaveHints } from "./model-save-ui.js";
import { MODEL_TYPES } from "./config.js";
import { state } from "./state.js";
import { api } from "./api.js";
import { serializeModelAsync } from "./diagram.js";
import { syncActiveViewFromVisibleGraph } from "./graph-store.js";
import { stringifyJsonAsync, yieldToMain } from "./utils.js";

const MAX_PATCH_OPERATIONS = 500;

function pointerSegment(value) {
  return String(value).replaceAll("~", "~0").replaceAll("/", "~1");
}

function pointerJoin(base, segment) {
  return `${base}/${pointerSegment(segment)}`;
}

function jsonEqual(left, right) {
  if (left === right) {
    return true;
  }
  if (left == null || right == null) {
    return left === right;
  }
  const leftType = typeof left;
  const rightType = typeof right;
  if (leftType !== rightType) {
    return false;
  }
  if (leftType !== "object") {
    return false;
  }
  if (Array.isArray(left)) {
    if (!Array.isArray(right) || left.length !== right.length) {
      return false;
    }
    for (let index = 0; index < left.length; index += 1) {
      if (!jsonEqual(left[index], right[index])) {
        return false;
      }
    }
    return true;
  }
  if (Array.isArray(right)) {
    return false;
  }
  const leftKeys = Object.keys(left);
  const rightKeys = Object.keys(right);
  if (leftKeys.length !== rightKeys.length) {
    return false;
  }
  for (const key of leftKeys) {
    if (!Object.prototype.hasOwnProperty.call(right, key)) {
      return false;
    }
    if (!jsonEqual(left[key], right[key])) {
      return false;
    }
  }
  return true;
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

  // Array paths are positional. Removing an item changes every following index, so a patch
  // which removes entries and then updates retained entries at their old indexes is invalid.
  // That is particularly common when replacing a hand-built diagram with an imported XMI
  // model. Replace the complete array whenever its membership or ordering changes; only
  // generate nested patches when each item remains at the same position.
  if (
    previous.length !== next.length ||
    previous.some((item, index) => String(item.id) !== String(next[index].id))
  ) {
    return [{ op: "replace", path: path || "/", value: next }];
  }

  const operations = [];
  for (let index = 0; index < previous.length; index += 1) {
    operations.push(...diffJson(previous[index], next[index], pointerJoin(path, index)));
  }
  return operations;
}

function graphElementIndexById(baseModel) {
  const index = new Map();
  const elements = baseModel?.graph?.elements;
  if (!Array.isArray(elements)) {
    return index;
  }
  elements.forEach((element, elementIndex) => {
    const id = String(element?.id || "").trim();
    if (id) {
      index.set(id, elementIndex);
    }
  });
  return index;
}

function viewNodeIndexesByElementId(baseModel) {
  const paths = new Map();
  const views = baseModel?.views;
  if (!Array.isArray(views)) {
    return paths;
  }
  views.forEach((view, viewIndex) => {
    const nodes = view?.nodes;
    if (!Array.isArray(nodes)) {
      return;
    }
    nodes.forEach((node, nodeIndex) => {
      const elementId = String(node?.elementId || "").trim();
      if (!elementId) {
        return;
      }
      const existing = paths.get(elementId) || [];
      existing.push({ viewIndex, nodeIndex });
      paths.set(elementId, existing);
    });
  });
  return paths;
}

function buildPositionPatch(baseModel, positions) {
  const graphIndex = graphElementIndexById(baseModel);
  const viewPaths = viewNodeIndexesByElementId(baseModel);
  const operations = [];
  for (const [elementId, coords] of positions.entries()) {
    const graphElementIndex = graphIndex.get(elementId);
    if (graphElementIndex !== undefined) {
      const element = baseModel.graph.elements[graphElementIndex];
      const x = Math.round(coords.x);
      const y = Math.round(coords.y);
      if (element.x !== x) {
        operations.push({
          op: "replace",
          path: `/graph/elements/${graphElementIndex}/x`,
          value: x,
        });
      }
      if (element.y !== y) {
        operations.push({
          op: "replace",
          path: `/graph/elements/${graphElementIndex}/y`,
          value: y,
        });
      }
    }
    const nodePaths = viewPaths.get(elementId) || [];
    for (const { viewIndex, nodeIndex } of nodePaths) {
      const viewNode = baseModel.views[viewIndex].nodes[nodeIndex];
      const x = Math.round(coords.x);
      const y = Math.round(coords.y);
      if (viewNode.x !== x) {
        operations.push({
          op: "replace",
          path: `/views/${viewIndex}/nodes/${nodeIndex}/x`,
          value: x,
        });
      }
      if (viewNode.y !== y) {
        operations.push({
          op: "replace",
          path: `/views/${viewIndex}/nodes/${nodeIndex}/y`,
          value: y,
        });
      }
    }
  }
  return operations;
}

function applyPositionPatchToBaseModel(baseModel, positions) {
  if (!baseModel || !positions.size) {
    return;
  }
  const graphIndex = graphElementIndexById(baseModel);
  const viewPaths = viewNodeIndexesByElementId(baseModel);
  for (const [elementId, coords] of positions.entries()) {
    const x = Math.round(coords.x);
    const y = Math.round(coords.y);
    const graphElementIndex = graphIndex.get(elementId);
    if (graphElementIndex !== undefined) {
      const element = baseModel.graph.elements[graphElementIndex];
      element.x = x;
      element.y = y;
    }
    for (const { viewIndex, nodeIndex } of viewPaths.get(elementId) || []) {
      const viewNode = baseModel.views[viewIndex].nodes[nodeIndex];
      viewNode.x = x;
      viewNode.y = y;
    }
  }
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

async function buildModelPatchAsync(previousModel, nextModel) {
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

function adoptSavedBaseModel(nextModel) {
  if (!nextModel || typeof nextModel !== "object") {
    return;
  }
  delete nextModel._sourceXmiBase64;
  delete nextModel._sourceXmiToken;
  state.baseModel = nextModel;
}

export async function prepareModelForSave({ syncView = true, forceFull = false } = {}) {
  if (!forceFull) {
    const hints = getDirtySaveHints();
    if (hints.positionOnly && state.baseModel?.graph?.elements) {
      const operations = buildPositionPatch(state.baseModel, hints.positions);
      if (!operations.length) {
        const graphIndex = graphElementIndexById(state.baseModel);
        const viewPaths = viewNodeIndexesByElementId(state.baseModel);
        const canResolve = [...hints.positions.keys()].every(
          (id) => graphIndex.has(id) || (viewPaths.get(id)?.length ?? 0) > 0,
        );
        if (canResolve) {
          applyPositionPatchToBaseModel(state.baseModel, hints.positions);
          return {
            nextModel: null,
            operations: [],
            incremental: "positions",
            positions: hints.positions,
          };
        }
      } else if (
        operations.length <= MAX_PATCH_OPERATIONS &&
        !operations.some((operation) => operation.path === "/")
      ) {
        return {
          nextModel: null,
          operations,
          incremental: "positions",
          positions: hints.positions,
        };
      }
    }
  }

  await yieldToMain();
  if (syncView && consumeViewSyncNeeded()) {
    syncActiveViewFromVisibleGraph();
  }
  const nextModel = await serializeModelAsync({
    syncView: false,
    reconcileRelationships: false,
  });
  await yieldToMain();
  const operations = await buildModelPatchAsync(state.baseModel || {}, nextModel);
  return { nextModel, operations };
}

export async function flushCurrentModelPatch({ name, rethrow = false, prepared = null } = {}) {
  if (!state.modelId || !MODEL_TYPES[state.activeType]) {
    return false;
  }
  const { nextModel, operations } = prepared || (await prepareModelForSave());
  if (operations === null) {
    return false;
  }
  if (!operations.length) {
    await yieldToMain();
    if (prepared?.incremental === "positions") {
      applyPositionPatchToBaseModel(state.baseModel, prepared.positions);
    } else if (nextModel) {
      adoptSavedBaseModel(nextModel);
    }
    clearDirtySaveHints();
    return true;
  }
  try {
    const body = await stringifyJsonAsync({
      name,
      operations,
      expectedRevision: state.modelRevision || 1,
    });
    const updated = await api(`/${MODEL_TYPES[state.activeType].apiType}/${state.modelId}`, {
      method: "PATCH",
      body,
    });
    await yieldToMain();
    if (prepared?.incremental === "positions") {
      applyPositionPatchToBaseModel(state.baseModel, prepared.positions);
    } else if (nextModel) {
      adoptSavedBaseModel(nextModel);
    }
    clearDirtySaveHints();
    if (updated && typeof updated === "object") {
      state.modelRevision = Number(updated.revision) || state.modelRevision;
    }
    return updated || true;
  } catch (error) {
    const invalidPatchTarget =
      error?.status === 400 &&
      /Patch (replace|remove) path does not exist:|Patch array index is out of bounds\./i.test(
        String(error?.message || ""),
      );
    if (invalidPatchTarget) {
      return false;
    }
    if (rethrow) {
      throw error;
    }
    return false;
  }
}
