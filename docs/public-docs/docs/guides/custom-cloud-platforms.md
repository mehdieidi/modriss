# Custom cloud platforms

MODRISS keeps its fixed CIM and PIM languages and built-in AWS modeling path. Projects can
upload additional cloud languages and Epsilon pipelines. Each uploaded package has one PSM
model, so AWS, Azure and other providers can coexist without mixing their models or artifacts.

## Add a platform

Open **Cloud platforms** from the left workspace rail and choose **Add platform**.
This settings window manages packages and visual notation. Modeling, validation and generation
use the main workspace controls.

| Input              | Contents                                                                                                                |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| Ecore metamodel    | Required self-contained `.ecore` file. Multiple root and nested EPackages are supported; each package needs an `nsURI`. |
| Transformation ZIP | Optional `.etl` scripts and `.eol` helpers; choose the ETL entry script. Enables PIM → PSM generation.                  |
| Generation ZIP     | Optional `.egx`, `.egl` and `.eol` files; choose the EGX entry script. Enables artifact generation.                     |
| Validation ZIP     | Optional `.evl` constraints and `.eol` helpers. Used by the workspace Validate action.                                  |

Ecore alone supplies a working editor. Script packages must include their relative folders,
imports and templates. ETL reads `PIM` and writes `PSM`; EGX/EGL and EVL use `PSM`. The native
PIM aliases, including `COMPUTE`, `DATA` and `API`, remain available. External files, remote
imports and installed Java extensions are outside the package contract.

Ecore is limited to 5 MB. Each ZIP is limited to 10 MB compressed, 30 MB expanded, 250 files
and 500 directory entries. All files use UTF-8. The platform checks Ecore conformance and
script syntax before storing a package. Canceling a file picker or closing the settings window
preserves the upload draft and selected files for that project while the page remains open.

Packages belong to their project and are visible to its members. Uploaded metamodels and
scripts are immutable; visual notation is editable. Upload a new package to revise a language
or executable pipeline.

## Use the provider editor

The PSM navbar tab shows the selected provider's name. Clicking the tab opens its canvas;
clicking the small arrow beside it selects another provider. Provider selection is remembered
per user and project. Each provider retains its own model and workspace state when switching.

From the PIM tab, use the arrow beside **Generate PSM** to select the target provider, then
press the generation button. The result opens in that provider's canvas. A custom package
keeps one model: regeneration asks for confirmation, checks the existing revision and updates
the same model. It preserves prior artifact bundles. It never creates an additional model for
the same package. Concurrent or stale writes return HTTP 409.

Custom PSMs use the regular palette, canvas, property editor, views, Save, Validate,
Auto-layout, import/export and artifact workspace. **Save** checks structural Ecore/EMF
conformance. **Validate** explicitly runs the supplied EVL constraints, if present. Semantic
EVL does not run during ordinary saves or artifact generation. Packages without ETL or
EGX/EGL keep their editors; the corresponding generation action is unavailable.

Each artifact bundle records its provider package, model revision and source PIM provenance.
Whole-project exports include packages, scripts, PSM XMI, workspace state, visual notation
and generated artifacts. The chatbot continues to support CIM and PIM only; its structural
validation boundary is unchanged.

## Customize visual notation

Every custom language starts with one **Cloud model** view, one **Resources** palette group
and the same default icon for every EClass. Attributes, enums, typed references, inheritance,
containment and multiplicities come from Ecore. Types with identical names in different
namespaces remain distinct. Language features that share workbench transport names, such as
`id`, `views` or `x`, retain separate semantic values during serialization.

In **Cloud platforms → Visual notation**, select a package to:

- choose the root EClass before creating its model;
- rename and add palette groups;
- set each type's display label, icon, visual role and light/dark colors;
- create views and choose their visible element types and palettes.

Save the notation to apply it to the provider editor. Once a model exists, its root EClass
must remain compatible. The built-in AWS notation remains managed by the admin Visual Syntax
editor. Changing presentation never changes the uploaded Ecore or Epsilon scripts.

## Minimal Azure example

The repository includes `methodology/modeling-framework/samples/custom-psm-azure/` with:

- `azure.ecore`: an Azure application with a location and a collection of functions;
- `transformation.zip`: ETL entry `main.etl` importing `rules/functions.etl`;
- `generation.zip`: EGX entry `main.egx` and two EGL templates;
- `validation.zip`: explicit location and runtime constraints;
- `pim.xmi`: a tiny source PIM containing one service and one function.

Import the PIM in the normal PIM workspace and save it. Add the Azure package, choose `main.etl`
and `main.egx`, then select Azure beside the PIM generation button and generate its model. Its artifacts include `azure-application.txt` and
`functions/Hello.txt`. These small text manifests exercise the integration; they are not a
complete deployable Azure generator. Build the ZIPs again after changing example scripts:

```powershell
python methodology/modeling-framework/samples/custom-psm-azure/package.py
```

## Execution and deployment

User scripts execute in a fresh Docker container for every operation. Containers have no
network, host mounts, inherited application credentials or capabilities, run as UID 10001,
and have a read-only root filesystem. A temporary filesystem, memory, CPU, process count,
input/output size and execution deadline bound each run. The backend never evaluates uploaded
Epsilon scripts in its own JVM. There is no in-process fallback when Docker is unavailable.

The Compose backend image is tagged `modriss-backend:local` and also serves as the runner image.
Compose installs the Docker client and mounts the daemon socket into the backend coordinator;
the disposable script containers never receive that socket. Rebuild the backend before using
custom packages. `MODRISS_PSM_RUNNER_IMAGE` can override the local runner image tag. The image
must contain `/app/backend.jar` with the PSM worker and Spring Boot PropertiesLauncher.

The container entrypoint detects the mounted Docker socket's group, grants the application
user membership in that group, and drops privileges before starting the backend. No manual
group-ID setting or socket permission change is required. Treat the backend coordinator as
a trusted daemon client and restrict access to its configuration.
For source development, Docker must be running and the configured image must already be built:

```powershell
docker build -t modriss-backend:local -f deploy/Dockerfile .
```

Up to two custom package operations execute concurrently per backend instance. Each operation
has a three-minute container deadline. Generated bundles are limited to 2,000 files, 5 MB
per file and 50 MB in total. Custom package requests return their result synchronously.
