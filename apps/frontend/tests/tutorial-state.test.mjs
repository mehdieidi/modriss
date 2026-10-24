import assert from "node:assert/strict";
import { test } from "node:test";
import {
  tutorialStorageKey, readTutorialProgress, writeTutorialProgress,
  tutorialTaskComplete, TUTORIAL_STEPS,
} from "../js/tutorial-state.js";

test("completion is isolated by account and shared by subsequent guest sessions", () => {
  const values = new Map();
  const storage = { getItem: (key) => values.get(key), setItem: (key, value) => values.set(key, value) };
  const guest = tutorialStorageKey({ id: "guest-a", guest: true });
  writeTutorialProgress(storage, guest, { completed: true });
  assert.equal(readTutorialProgress(storage, tutorialStorageKey({ id: "guest-b", guest: true })).completed, true);
  assert.deepEqual(readTutorialProgress(storage, tutorialStorageKey({ id: "account-a" })), {});
  writeTutorialProgress(storage, tutorialStorageKey({ id: "account-a" }), { completed: true });
  assert.deepEqual(readTutorialProgress(storage, tutorialStorageKey({ id: "account-b" })), {});
});

test("unavailable or corrupt storage does not break onboarding", () => {
  const unavailable = { getItem() { throw new Error("blocked"); }, setItem() { throw new Error("quota"); } };
  assert.deepEqual(readTutorialProgress(unavailable, "key"), {});
  assert.deepEqual(readTutorialProgress({ getItem: () => "{bad" }, "key"), {});
  assert.deepEqual(readTutorialProgress(null, "key"), {});
  assert.doesNotThrow(() => writeTutorialProgress(unavailable, "key", { completed: true }));
  assert.doesNotThrow(() => writeTutorialProgress(null, "key", { completed: true }));
});

function fixture() {
  const state = {
    project: { id: "library" }, activeType: "cim", modelId: null,
    graph: { elementsById: new Map([["existing", { id: "existing", eClass: "BusinessGoal" }]]) },
    views: { activeViewId: "goals", byId: new Map([["goals", { id: "goals", definitionId: "strategy-goals" }]]) },
    modelSave: { dirty: true, saving: false, error: "", lastSavedAt: 10, successfulSaves: 0 },
  };
  const exercise = { projectId: "library", baselineIds: ["existing"], savesBefore: 0 };
  return { state, exercise, check: (step) => tutorialTaskComplete(step, state, exercise) };
}

test("CIM exercise requires new elements, committed fields, and the correct reference", () => {
  const { state, exercise, check } = fixture();
  assert.equal(check("cim-view"), true);
  state.views.activeViewId = "dashboard";
  assert.equal(check("cim-view"), false);
  assert.equal(check("goal"), false, "existing project content does not satisfy creation");
  const goal = { id: "goal", eClass: "cim:BusinessGoal", name: "BusinessGoal" };
  state.graph.elementsById.set(goal.id, goal);
  assert.equal(check("goal"), true);
  assert.equal(exercise.goalId, goal.id);
  assert.equal(check("goal-details"), false);
  goal.name = "Reduce borrowing wait";
  assert.equal(check("goal-details"), false);
  goal.successCriterion = "Readers receive a book within 2 days";
  assert.equal(check("goal-details"), true);
  assert.equal(check("requirement"), false);
  const requirement = { id: "requirement", eClass: "Requirement", name: "Reserve a book", fitCriterion: "A reader can reserve an available book" };
  state.graph.elementsById.set(requirement.id, requirement);
  assert.equal(check("requirement"), true);
  assert.equal(check("requirement-details"), true);
  requirement.supportsGoals = ["existing"];
  assert.equal(check("link"), false);
  for (const reference of ["goal", "#goal", { id: "goal" }, { $ref: "#goal" }]) {
    requirement.supportsGoals = [reference];
    assert.equal(check("link"), true);
  }
  state.activeType = "pim";
  assert.equal(check("link"), false);
  state.activeType = "cim";
  state.project.id = "another-project";
  assert.equal(check("link"), false);
});

test("save step requires a successful new save in the exercise project", () => {
  const { state, check } = fixture();
  assert.equal(check("save"), false);
  state.modelId = "model";
  state.modelSave.dirty = false;
  assert.equal(check("save"), false, "old saves do not count");
  state.modelSave.lastSavedAt = 30;
  assert.equal(check("save"), false, "loading a model updates lastSavedAt but is not a save");
  state.modelSave.successfulSaves = 1;
  assert.equal(check("save"), true);
  state.modelSave.error = "Network failure";
  assert.equal(check("save"), false);
  state.modelSave.error = "";
  state.modelSave.saving = true;
  assert.equal(check("save"), false);
});

test("step identities are unique so persisted progress resumes unambiguously", () => {
  assert.equal(new Set(TUTORIAL_STEPS.map((step) => step.id)).size, TUTORIAL_STEPS.length);
});
