import { clearDirtySaveHints, consumeViewSyncNeeded } from "./model-save-ui.js";
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

function isOptionalLayoutProperty(path, key) {
  if (key !== "x" && key !== "y") {
    return false;
  }
  return /^\/graph\/elements\/\d+$/.test(path) || /^\/views\/\d+\/nodes\/\d+$/.test(path);
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

function diffJson(previous, next, path = "", { preferAdd = false } = {}) {
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
    // RFC 6902 `add` replaces an existing object member as well as creating a missing one.
    // Prefer it for object members because persisted models can legitimately omit optional
    // layout fields (such as graph element x/y) even when the browser's saved baseline has
    // them. `replace` would reject that otherwise valid save.
    return [{ op: preferAdd ? "add" : "replace", path: path || "/", value: next }];
  }
  if (Array.isArray(previous) || Array.isArray(next)) {
    return diffArrays(previous, next, path);
  }
  const operations = [];
  const keys = new Set([...Object.keys(previous), ...Object.keys(next)]);
  for (const key of keys) {
    operations.push(
      ...diffJson(previous[key], next[key], pointerJoin(path, key), {
        preferAdd: isOptionalLayoutProperty(path, key),
      }),
    );
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

export async function prepareModelForSave({ syncView = true } = {}) {
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
    if (nextModel) {
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
    if (nextModel) {
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
      /Patch (parent path|replace|remove) does not exist:|Patch array index is out of bounds\./i.test(
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
