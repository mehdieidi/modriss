# PSM cloud-provider extensions

The extension mechanism makes the PSM language a project-level choice. CIM and PIM retain
their fixed metamodels for business and generic serverless modeling. AWS remains the native
PSM target. A user-defined package supplies the Ecore metamodel and Epsilon scripts for
another provider, so the same upstream models can lead to several cloud-specific designs
and generated projects.

The [public guide](../../public-docs/docs/guides/custom-cloud-platforms.md) defines the upload
formats and workbench procedure. An Ecore file provides an editor; ETL, EGX/EGL, and optional
EVL ZIPs enable transformation, artifact generation, and explicit semantic validation.
Uploaded scripts retain their relative imports and template paths.

## Language and model ownership

Packages belong to a project and use its access-control boundary. The uploaded Ecore and
scripts are immutable; visual notation can be edited separately. Revising the metamodel or
executable scripts requires a new package. A package's notation determines its views, palette
groups, labels, and icons. Without customization, the workbench derives one view and one
palette group from Ecore and uses a common icon for all element types.

Each custom package maintains one PSM. Generation records the source PIM and its revision;
regeneration checks both source and target revisions and updates that PSM after confirmation.
Other provider models remain available. Existing artifact bundles remain stored when the
model is regenerated, and custom bundles identify the package, source PSM revision, and
upstream PIM. The artifact explorer selects output by provider.

Per-provider browser state keeps models, undo history, palette settings, and views separate.
Provider selection is remembered per user and project. Only the active PSM or Artifacts tab
displays its provider dropdown. The PIM generation
control selects the transformation target and names that provider in its label. These
operations use the main workspace; Cloud platforms contains package and notation settings.

## Implementation boundaries

| Concern                                                            | Source                                                                                                         |
| ------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------- |
| Authenticated package, model, notation, and artifact routes        | `apps/backend/src/main/java/io/mehdieidi/modriss/backend/api/PsmExtensionController.java`                      |
| Package ownership, model revisions, transformation, and generation | `packages/java/platform-mde-pipeline/src/main/java/io/mehdieidi/modriss/platform/psm/PsmExtensionService.java` |
| Disposable execution containers                                    | `ContainerPsmExecutionRunner.java` and `PsmPackageWorker.java` in the same PSM package                         |
| Structural model conversion between editor JSON and XMI            | `PsmModelCodec.java` in the same PSM package                                                                   |
| Provider selection and workspace state                             | `apps/frontend/js/cloud-providers.js`                                                                          |
| Ecore-derived editor configuration                                 | `apps/frontend/js/psm-notation.js`                                                                             |
| Package upload and editable notation                               | `apps/frontend/js/psm-platforms.js` and `psm-visual-editor.js`                                                 |

PostgreSQL stores custom package and model payloads in `psm_extension_records`. Generated
files use the normal artifact storage. Whole-project exports include extension packages,
models, visual notation, and generated artifacts.

Uploaded scripts execute in disposable containers with bounded resources and no network.
The backend coordinates these operations and does not evaluate uploaded Epsilon scripts in
its own JVM. The public guide describes runner configuration and package limits.

Saving checks structural Ecore/EMF conformance. The explicit Validate action runs supplied
EVL constraints; saves and artifact generation do not invoke semantic validation. The chatbot
supports CIM and PIM only. Its structural validation boundary is separate from this extension.

## Example and verification

`methodology/modeling-framework/samples/custom-psm-azure/` contains a small Ecore language,
ETL rules, EGX/EGL templates, EVL constraints, and a source PIM. The generated text manifests
exercise the extension mechanism. They do not constitute a complete Azure deployment package.

`apps/frontend/tests/psm-platforms.browser.mjs` checks the package manager and provider
controls against a mocked API. `psm-platforms.live.mjs` exercises the deployed Azure workflow,
including editor state, validation, artifact selection, notation, and regeneration. Focused
Java tests under `platform-mde-pipeline` cover the extension service and model codec.
