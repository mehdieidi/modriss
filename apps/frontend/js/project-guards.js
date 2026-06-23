import { state } from "./state.js";

export function requireActiveProject(actionLabel = "continue") {
  const projectId = state.project?.id || state.project?.projectId || state.project?.uuid || null;
  if (!projectId) {
    throw new Error(`Select or create a project before you ${actionLabel}.`);
  }
  return state.project;
}
