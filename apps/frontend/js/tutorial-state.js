// Keep the storage key stable when tutorial copy changes: finishing is permanent.
export function tutorialStorageKey(user) {
  if (user?.guest) return "modriss.editorTutorial:guest";
  return `modriss.editorTutorial:user:${user?.id || user?.email || "anonymous"}`;
}

export function readTutorialProgress(storage, key) {
  try {
    const value = JSON.parse(storage.getItem(key));
    return value && typeof value === "object" ? value : {};
  } catch {
    return {};
  }
}

export function writeTutorialProgress(storage, key, progress) {
  try {
    storage.setItem(key, JSON.stringify(progress));
  } catch {
    // Private browsing/storage restrictions must not prevent use of the editor.
  }
}

export const TUTORIAL_STEPS = [
  {
    id: "project",
    phase: "Project setup",
    title: "Create or open a project",
    target: ".project-create-form, #projectOverlay",
    body: "A project groups your models and generated files. Enter 'My first model' in the project name field and select Create Project, or open a project from the list. This tutorial explains the editor, then guides you through a CIM example. The example adds elements to the project you choose. Select Minimize if the instructions cover a control you need.",
    task: "Create a project or select one from the list.",
  },
  {
    id: "workspace",
    phase: "Editor overview",
    title: "The canvas",
    target: "#canvasViewport",
    body: "The central area is the canvas. It displays elements and relationships from the selected model view. The palette on the left provides element types you can add. Selecting an element opens its properties. You can use the editor while these instructions are open. Select Next to continue the overview.",
  },
  {
    id: "levels",
    phase: "Editor overview",
    title: "Modeling levels",
    target: "#modelTabs, #mobileModelLevelTabs",
    body: "CIM (computation-independent model) describes business needs, domain concepts, and business behavior. PIM (platform-independent model) describes a serverless software design without choosing a cloud provider. PSM (platform-specific model) describes resources and configuration for a chosen provider. Artifacts contains generated implementation files. Use the tabs to work at each level.",
  },
  {
    id: "views",
    phase: "Editor overview",
    title: "Model views",
    target: "#activeViewSelect, #modelWorkbenchPanel",
    body: "Use the View selector to choose which part of the model to display. Each view uses elements from the same underlying model. The selected view also determines the available palette items. Use Inspect to list elements or relationships for that view, or choose Root to inspect the model's own properties.",
  },
  {
    id: "palette",
    phase: "Editor overview",
    title: "The palette",
    target: "#palette, #mobileDockPaletteBtn",
    body: "The palette lists the element types available in the current modeling level and view. Use its search field to find a type, then drag the item onto the canvas to create an element. On a touch screen, press the item and drag it onto the canvas. Clear the search to see the other available types.",
  },
  {
    id: "canvas",
    phase: "Editor overview",
    title: "Arrange the diagram and edit properties",
    target: "#canvasZoomControl, #canvasViewport",
    body: "Use the zoom controls to change the scale and Fit to bring the diagram into view. Drag elements to change their positions, or use Auto Layout to arrange them automatically. Select an element to open its property panel. For CIM elements, Identity contains the name, Business Fields contains descriptive attributes, and Relationships contains references to other elements. Select Apply Changes after editing properties.",
  },
  {
    id: "process",
    phase: "Editor overview",
    title: "Modeling process",
    target: "#methodologyRailBtn, #mobileDockMenuBtn",
    body: "Select Process in the side rail to see the modeling phases and tasks for the current level. Tasks explain what information to add and which views to use. Follow them when developing a model beyond this exercise. The documentation link in the side rail provides definitions of the modeling concepts and their properties.",
  },
  {
    id: "save-intro",
    phase: "Editor overview",
    title: "Save and validate",
    target: "#saveModelBtn, #mobileDockSaveBtn",
    body: "Save stores changes to the model and its views. You can also use Ctrl+S or Command+S. Select Validate to check the model's structure and the configured modeling rules. Review the reported issues and open the affected elements to correct them. Saving a model does not mean it is complete or ready for transformation.",
  },
  {
    id: "generation",
    phase: "Editor overview",
    title: "Transform models and generate files",
    target: "#generateContextBtn, #mobileDockMenuBtn",
    body: "On CIM, the generation button transforms the saved model into an initial PIM. On PIM, it produces a PSM for the selected cloud provider. On PSM, it generates implementation files. Review the resulting model and any unresolved decisions before continuing. This exercise stops after saving the CIM example.",
  },
  {
    id: "assistant",
    phase: "Editor overview",
    title: "Modeling assistant",
    target: "#chatToggle, #mobileDockChatBtn",
    body: "Select the chat button to ask about the current model or request changes. Describe the business need and include any relevant constraints. Review suggested changes against your requirements before applying them. Use Validate when you want to check the model against the configured modeling rules.",
  },
  {
    id: "cim",
    phase: "CIM exercise",
    title: "Select CIM",
    target: '#modelTabs [data-type="cim"], #mobileModelLevelTabs [data-type="cim"], #modelTabs',
    body: "Select the CIM tab. The example is a library that wants readers to receive borrowed books sooner. You will record the desired outcome as a Business Goal, add a Requirement for reserving a book, and specify which goal that requirement supports. These elements become part of your selected project.",
    task: "Select the CIM tab, then select Next.",
  },
  {
    id: "cim-view",
    phase: "CIM exercise",
    title: "Select Goals and Requirements",
    target: "#activeViewSelect, #modelWorkbenchPanel",
    body: "Open the View selector and choose Goals and Requirements. This view includes Business Goal and Requirement in its palette and displays the relationships between them. Keep this view selected for the remaining steps.",
    task: "Choose Goals and Requirements in the View selector.",
  },
  {
    id: "goal",
    phase: "CIM exercise",
    title: "1. Add a business goal",
    target: '#palette [data-node-type="BusinessGoal"], #palette, #mobileDockPaletteBtn',
    body: "A business goal states an outcome the organization wants to achieve. Open the palette and search for Business Goal. Drag a Business Goal onto an empty part of the canvas. In the next step, you will give it a name and a criterion for judging whether the goal has been achieved.",
    task: "Drag a new Business Goal from the palette onto the canvas.",
  },
  {
    id: "goal-details",
    phase: "CIM exercise",
    title: "2. Name the goal and define success",
    target: "#attributePanel, #canvasViewport",
    body: "Select the goal on the canvas. In Identity, enter 'Reduce borrowing wait' as its name. In Business Fields, enter 'Readers receive a book within 2 days' in the successCriterion field. This criterion states the expected business outcome. Select Apply Changes to update the element. You will save the model at the end of the exercise.",
    task: "Set the name to 'Reduce borrowing wait', fill in successCriterion, and select Apply Changes.",
  },
  {
    id: "requirement",
    phase: "CIM exercise",
    title: "3. Add a requirement",
    target: '#palette [data-node-type="Requirement"], #palette, #mobileDockPaletteBtn',
    body: "A requirement records a needed behavior or constraint. For this example, readers need to be able to reserve a book. Return to the palette, clear the search, and search for Requirement. Drag a Requirement onto the canvas near the goal.",
    task: "Drag a new Requirement from the palette onto the canvas.",
  },
  {
    id: "requirement-details",
    phase: "CIM exercise",
    title: "4. Define the requirement's fit criterion",
    target: "#attributePanel, #canvasViewport",
    body: "Select the requirement. In Identity, enter 'Reserve a book' as its name. In Business Fields, enter 'A reader can reserve an available book' in the fitCriterion field. A fit criterion states how to judge whether a requirement is satisfied. Select Apply Changes. A fuller model can describe detailed test scenarios using Acceptance Criterion elements.",
    task: "Set the name to 'Reserve a book', fill in fitCriterion, and select Apply Changes.",
  },
  {
    id: "link",
    phase: "CIM exercise",
    title: "5. Link the requirement to the goal",
    target: "#attributePanel, #canvasViewport",
    body: "Keep 'Reserve a book' selected and open Relationships in its property panel. In supportsGoals, select 'Reduce borrowing wait', then select Apply Changes. This reference records which business goal the requirement is intended to support.",
    task: "Add 'Reduce borrowing wait' to supportsGoals and select Apply Changes.",
  },
  {
    id: "save",
    phase: "CIM exercise",
    title: "6. Save the CIM model",
    target: "#saveModelBtn, #mobileDockSaveBtn",
    body: "Select Save to store the elements and their relationship. If an error appears, follow the message and try saving again. The tutorial continues after a successful save. This example covers only a goal and a requirement. Further modeling and validation are needed before using it as the source of a transformation.",
    task: "Select Save and wait for it to finish successfully.",
  },
  {
    id: "complete",
    phase: "Exercise complete",
    title: "CIM example saved",
    target: "#canvasViewport",
    body: "The saved example contains a business goal, a requirement with a fit criterion, and a supportsGoals reference. To develop the model further, use Process to choose the next tasks and Validate to check for modeling issues. Select Finish tutorial to close these instructions. They will stay dismissed in this browser unless its site storage is cleared.",
    next: "Finish tutorial",
  },
];

export function tutorialElements(state) {
  const elements = new Map(state.graph?.elementsById || []);
  for (const node of state.diagram?.nodes || []) {
    if (!elements.has(node.id))
      elements.set(node.id, { ...node.meta, id: node.id, eClass: node.type, name: node.label });
  }
  return [...elements.values()];
}

function hasType(element, type) {
  return (
    String(element.eClass || element.type || "")
      .split(/[#:/.]/)
      .pop() === type
  );
}

function referenceId(value) {
  return typeof value === "string"
    ? value.replace(/^#/, "")
    : value?.id || value?.$ref?.replace(/^#/, "");
}

// Predicates inspect committed editor state; typing or clicking Apply/Save alone is insufficient.
export function tutorialTaskComplete(step, state, exercise) {
  if (step === "project") return Boolean(state.project?.id);
  if (step === "cim") return state.activeType === "cim";
  if (!state.project?.id || state.project.id !== exercise.projectId || state.activeType !== "cim")
    return false;
  const elements = tutorialElements(state);
  const findNew = (type) =>
    elements.find(
      (element) => hasType(element, type) && !(exercise.baselineIds || []).includes(element.id),
    );
  const goal = elements.find((element) => element.id === exercise.goalId);
  const requirement = elements.find((element) => element.id === exercise.requirementId);
  switch (step) {
    case "cim-view": {
      const view = state.views?.byId?.get(state.views.activeViewId);
      return Boolean(
        view &&
          (view.definitionId === "strategy-goals" ||
            view.sourceDefinitionId === "strategy-goals" ||
            view.viewpoint === "requirements" ||
            /goals.*requirements|requirements.*goals/i.test(view.name || "")),
      );
    }
    case "goal": {
      const created = goal || findNew("BusinessGoal");
      if (created) exercise.goalId = created.id;
      return Boolean(created);
    }
    case "goal-details":
      return (
        goal?.name?.trim() === "Reduce borrowing wait" && Boolean(goal.successCriterion?.trim())
      );
    case "requirement": {
      const created = requirement || findNew("Requirement");
      if (created) exercise.requirementId = created.id;
      return Boolean(created);
    }
    case "requirement-details":
      return (
        requirement?.name?.trim() === "Reserve a book" && Boolean(requirement.fitCriterion?.trim())
      );
    case "link": {
      const references = requirement?.supportsGoals || [];
      return Boolean(
        goal &&
          (Array.isArray(references) ? references : [references]).some(
            (ref) => referenceId(ref) === goal.id,
          ),
      );
    }
    case "save":
      return Boolean(
        state.modelId &&
          !state.modelSave?.dirty &&
          !state.modelSave?.saving &&
          !state.modelSave?.error &&
          state.modelSave?.successfulSaves > exercise.savesBefore,
      );
    default:
      return true;
  }
}
