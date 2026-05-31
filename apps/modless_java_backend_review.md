# Modless Java Backend / MDE Runtime Review

Repository reviewed: `mehdieidi/modless`  
Focus: Java backend and reusable Java runner modules that load EMF/Ecore metamodels,
serialize/deserialize XMI/JSON models, run Eclipse Epsilon ETL/EVL/EGX, persist models, and produce
artifacts.

This is a static code review based on the repository contents. I did not run the Maven test suite or
execute the Epsilon scripts in a local clone. The findings below are prioritized for a
thesis-quality, production-ready MDE platform where correctness, traceability, concurrency safety,
and future metamodel evolution are important.

## Executive summary

The backend already has a useful separation between reusable runner modules (`mde-etl-runner`,
`mde-evl-validator`, `mde-m2t-runner`) and platform services (`platform-core`, `apps/backend`). The
main architectural risks are not in the basic ability to execute Epsilon, but in runtime hardening:
synchronous long-running execution, stale XMI sidecars, global EMF registry mutation, duplicated
path/metamodel resolution, unbounded in-memory buffers, silent data loss during JSON-to-XMI
conversion, and weak concurrency/version control.

The most urgent fixes are:

1. Make transformation/generation asynchronous jobs with progress, timeout, cancellation, and
   per-project/model locking.
2. Fix the stale source-XMI bug so transformations never use old imported XMI after a JSON model
   update.
3. Introduce a single `MdeRuntimePaths` / `MetamodelResolver` service instead of duplicated
   `user.dir` scanning and mixed classpath/repository metamodel sources.
4. Stop mutating global EMF registries from request-time code.
5. Make JSON-to-XMI export strict: report invalid enum values, failed conversions, missing
   references, duplicate IDs, and wrong subtypes instead of silently dropping them.
6. Stream generated artifacts and large files instead of storing every generated file as a
   `Map<String,String>` in one JSON record.
7. Add model revisions/metamodel versions and optimistic locking.

---

## Issue 1 — Transformation and generation run synchronously inside HTTP requests

**Severity:** P0 / Production blocker  
**Area:** API orchestration, scalability, user experience

**Evidence**

`TransformationController` invokes service methods directly from POST handlers for CIM→PIM, PIM→PSM,
and PSM→artifact generation. `TransformationService` then runs ETL/EGX and returns the generated
model/artifact synchronously.

Relevant locations:

- `apps/backend/.../TransformationController.java`, methods `cimToPim`, `pimToPsm`, `psmToArtifact`.
- `packages/java/platform-core/.../TransformationService.java`, methods `formalCimToPimModel`,
  `formalPimToPsmModel`, `formalPsmToArtifactFiles`.

**Why this is an issue**

ETL/EVL/EGX execution can be CPU-heavy, memory-heavy, and sometimes blocked by malformed models or
script bugs. Running these tasks synchronously in a servlet request thread can:

- exhaust the web server thread pool;
- cause request timeouts for valid but large models;
- provide no reliable progress or cancellation;
- make retries unsafe because the same operation may create duplicate models/artifacts;
- make production deployment hard to scale horizontally.

**Concrete fix**

Introduce a persistent job model and run MDE operations on a bounded worker executor. The controller
should return `202 Accepted` with a job ID, not the generated model immediately.

Suggested data model:

```java
public record MdeJobRecord(
        String id,
        String projectId,
        String sourceModelId,
        ModelLevel sourceLevel,
        String operation, // CIM_TO_PIM, PIM_TO_PSM, PSM_TO_ARTIFACT, VALIDATE
        JobStatus status, // QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED
        int progressPercent,
        String resultModelId,
        String resultArtifactId,
        List<String> diagnostics,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {
}
```

Suggested controller shape:

```java
@PostMapping("/cim-to-pim")
ResponseEntity<JobResponse> cimToPim(@RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody TransformRequest request) {
    MdeJobRecord job = jobService.submitCimToPim(auth.user(token), request.sourceModelId());
    return ResponseEntity.accepted().body(new JobResponse(job.id(), job.status()));
}

@GetMapping("/jobs/{id}")
MdeJobRecord job(@RequestHeader("X-Auth-Token") String token, @PathVariable String id) {
    return jobService.get(auth.user(token), id);
}
```

Use a bounded `ThreadPoolTaskExecutor` or Java `ExecutorService` configured from
`BackendProperties`, for example `modless.mde.maxConcurrentJobs`, `modless.mde.queueCapacity`, and
`modless.mde.jobTimeout`.

---

## Issue 2 — No timeout, cancellation, or resource budget around Epsilon execution

**Severity:** P0 / Production blocker  
**Area:** Execution safety, availability

**Evidence**

`EpsilonEtlExecutor.execute`, `EpsilonEvlValidator.validateModule`, and
`EpsilonEgxGenerator.generate` call `module.execute()` directly. There is no timeout, cancellation
token, memory budget, maximum generated file budget, or script-level watchdog.

**Why this is an issue**

EOL/ETL/EVL/EGX scripts are effectively user-influenced computation because users provide models. A
model with many elements or a script bug can consume unbounded time. In production, one malformed
model should not block server resources indefinitely.

**Concrete fix**

Run each Epsilon execution through a controlled execution wrapper:

```java
public final class MdeExecutionGuard {
    private final ExecutorService executor;
    private final Duration timeout;

    public <T> T runWithTimeout(Callable<T> task, String operation) {
        Future<T> future = executor.submit(task);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new PlatformException(504, operation + " timed out after " + timeout);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new PlatformException(500, operation + " failed: " + cause.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PlatformException(503, operation + " was interrupted.");
        }
    }
}
```

Then call Epsilon inside job workers, not directly inside controller threads. Add model size limits
before execution and generated artifact size limits after execution.

---

## Issue 3 — Captured stdout/stderr/warnings are unbounded in memory

**Severity:** P1 / High  
**Area:** Memory safety, denial-of-service resistance

**Evidence**

The ETL, EVL, and EGX executors allocate `ByteArrayOutputStream` for stdout, warnings, and stderr.
Epsilon scripts can write arbitrary output into those streams.

**Why this is an issue**

A script or helper can accidentally or maliciously print a huge amount of text. Since output is
captured in memory, one request can exhaust heap memory. This is especially risky for an API-exposed
modeling platform.

**Concrete fix**

Replace `ByteArrayOutputStream` with a bounded output stream that truncates after a configured limit
and records that truncation happened.

Example:

```java
public final class BoundedByteArrayOutputStream extends OutputStream {
    private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    private final int maxBytes;
    private boolean truncated;

    public BoundedByteArrayOutputStream(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public void write(int b) {
        if (delegate.size() < maxBytes) {
            delegate.write(b);
        } else {
            truncated = true;
        }
    }

    public String asUtf8String() {
        String value = delegate.toString(StandardCharsets.UTF_8);
        return truncated ? value + "\n[output truncated]" : value;
    }
}
```

Use one configurable limit, for example `modless.mde.maxCapturedOutputBytes=1048576`.

---

## Issue 4 — Stale source XMI can override updated JSON models

**Severity:** P0 / Correctness blocker  
**Area:** Model persistence, transformation correctness

**Evidence**

`ModelService.update` persists normalized JSON and only consumes a new `_sourceXmiToken` if one
exists. It does not delete or invalidate a previously stored `.xmi` sidecar when the user updates
the JSON model. `TransformationService` prefers stored source XMI over exporting the current JSON.

Relevant locations:

- `ModelService.update(...)`
- `ModelService.sourceXmi(...)`
- `TransformationService.sourceCimXmi`, `sourcePimXmi`, `sourcePsmXmi`

**Why this is an issue**

A user can import XMI, edit the model in the browser, save JSON, and then transform. The
transformation may still use the original imported XMI sidecar instead of the updated JSON model.
This creates silent, severe correctness bugs: the user sees one model but the backend transforms
another.

**Concrete fix**

Choose one canonical source of truth.

Recommended option: **JSON is canonical for editor-saved models**; imported XMI is only a
historical/original artifact unless refreshed.

Patch direction:

```java
public ModelRecord update(UserRecord user, ModelLevel level, String id, String name,
        JsonNode modelJson) {
    ModelRecord existing = get(user, level, id);
    ProjectRecord project = projectService.get(user, existing.projectId());
    projectService.requireEditor(project, user.id());

    JsonNode normalizedModel = normalizeModel(name, level, modelJson);
    String sourceXmiToken = removeSourceXmiToken(normalizedModel);

    ModelRecord updated = new ModelRecord(existing.id(), existing.projectId(), level,
            requireName(name, level), normalizedModel, existing.createdAt(), Instant.now());

    store.write(modelPath(existing.projectId(), level, id), updated);

    if (sourceXmiToken == null || sourceXmiToken.isBlank()) {
        deleteSourceXmiIfPresent(existing.projectId(), level, id);
    } else {
        consumeSourceXmiToken(sourceXmiToken, updated);
    }

    return clientRecord(updated);
}

private void deleteSourceXmiIfPresent(String projectId, ModelLevel level, String id) {
    try {
        Files.deleteIfExists(store.resolve(sourceXmiPath(projectId, level, id)));
    } catch (Exception ex) {
        throw new PlatformException(500, "Could not invalidate stale source XMI.");
    }
}
```

Also add a regression test:

1. Import XMI with command A.
2. Update JSON to command B.
3. Run CIM→PIM.
4. Assert generated PIM contains B, not A.

---

## Issue 5 — Model JSON and source XMI sidecar writes are not transactional

**Severity:** P1 / High  
**Area:** Persistence consistency

**Evidence**

`ModelService.create` writes the JSON model record first, then consumes/moves staged source XMI.
`ModelService.update` also writes JSON first, then consumes the XMI token. If the second step fails,
JSON and XMI state diverge.

**Why this is an issue**

For a model-driven platform, metadata and model source must be consistent. A partial failure may
produce records whose JSON and XMI no longer match, causing later validations or transformations to
behave unpredictably.

**Concrete fix**

Introduce a small file-store transaction abstraction:

```java
public final class FileStoreTransaction implements AutoCloseable {
    private final List<Runnable> rollbackActions = new ArrayList<>();
    private boolean committed;

    public void onRollback(Runnable action) {
        rollbackActions.add(action);
    }

    public void commit() {
        committed = true;
    }

    @Override
    public void close() {
        if (!committed) {
            rollbackActions.reversed().forEach(Runnable::run);
        }
    }
}
```

More simply, write both JSON and XMI to staging paths, then atomically move both into place. If any
move fails, restore the old files.

---

## Issue 6 — Source XMI sidecar writes are not atomic

**Severity:** P1 / High  
**Area:** Persistence, crash safety

**Evidence**

`JsonFileStore.write` correctly writes JSON to a temporary file and uses `ATOMIC_MOVE`. In contrast,
`ModelService.writeSourceXmi` writes XMI directly with `Files.write`.

**Why this is an issue**

If the process crashes during `Files.write`, a `.xmi` sidecar can become partial or corrupted while
the JSON record appears valid.

**Concrete fix**

Move sidecar writes into `JsonFileStore` or add an atomic byte writer:

```java
public void writeBytesAtomically(Path path, byte[] bytes) {
    Path resolved = resolve(path);
    try {
        Files.createDirectories(resolved.getParent());
        Path temp = resolved.resolveSibling(resolved.getFileName() + "." + UUID.randomUUID() + ".tmp");
        Files.write(temp, bytes);
        Files.move(temp, resolved, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ex) {
        // fall back to non-atomic move only with warning/metric
    } catch (IOException ex) {
        throw new PlatformException(500, "Could not persist binary data.");
    }
}
```

Use this for stored XMI, generated ZIPs, and any future binary artifacts.

---

## Issue 7 — Staged XMI import tokens have no owner, expiry, or cleanup policy

**Severity:** P1 / High  
**Area:** Security, persistence hygiene

**Evidence**

Imported XMI is staged under `model-imports/<uuid>.xmi`; later create/update consumes the token. The
token is just a UUID stored in client-visible JSON.

**Why this is an issue**

Problems:

- orphaned staged files accumulate if users import but never save;
- a token is not bound to a user/project/session;
- a leaked token could attach someone else’s staged XMI to a model;
- there is no TTL, size limit, or cleanup job.

**Concrete fix**

Replace raw token files with a `StagedImportRecord`:

```java
public record StagedImportRecord(
        String token,
        String userId,
        String projectId,
        ModelLevel level,
        Path xmiPath,
        long sizeBytes,
        Instant createdAt,
        Instant expiresAt) {
}
```

Require `consumeSourceXmiToken(token, user, project, level)` to validate ownership and expiry. Add
scheduled cleanup:

```java
@Scheduled(fixedDelayString = "${modless.import-staging.cleanup-interval:PT15M}")
public void cleanupExpiredImports() { ... }
```

---

## Issue 8 — Public validate/export/import endpoints are unauthenticated

**Severity:** P0 / Security and availability blocker  
**Area:** API security

**Evidence**

`ModelController` requires `X-Auth-Token` for list/create/get/update/delete, but the validate,
export, and import endpoints do not require an auth header.

**Why this is an issue**

Validation and import can invoke JSON parsing, XMI parsing, EMF model creation, and EVL validation.
Leaving those endpoints unauthenticated exposes CPU, memory, and disk-heavy operations to anonymous
callers. It also bypasses project-level access control for model content.

**Concrete fix**

Require authentication on all model endpoints. For validation/export of unsaved models, authenticate
the user and optionally require a project ID when the operation depends on project context.

```java
@PostMapping("/api/{level:cim|pim|psm}/validate")
ModelService.ValidationResult validate(@RequestHeader("X-Auth-Token") String token,
        @PathVariable("level") String level,
        @RequestBody SaveModelRequest request) {
    auth.user(token);
    return models.validate(ModelLevel.fromApiName(level), request.model());
}
```

Also add rate limiting and upload size limits.

---

## Issue 9 — Multipart import loads the whole uploaded file into memory

**Severity:** P1 / High  
**Area:** Memory safety, API hardening

**Evidence**

`ModelController.importModel` calls `file.getBytes()` and passes the complete byte array to
`ModelService.importModel`.

**Why this is an issue**

Large uploads are fully materialized in heap memory. Combined with XMI parsing and validation, one
request can create high memory pressure.

**Concrete fix**

Add explicit upload limits and stream to a bounded temp file.

```java
@PostMapping(value = "/api/{level:cim|pim|psm}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
ModelService.ImportResult importModel(..., @RequestParam("file") MultipartFile file) {
    long max = properties.maxModelUploadBytes();
    if (file.getSize() > max) {
        throw new PlatformException(413, "Model file is too large.");
    }
    try (InputStream in = file.getInputStream()) {
        return models.importModelFromStream(..., in, file.getSize(), format);
    }
}
```

Add properties:

```java
@ConfigurationProperties(prefix = "modless")
public record BackendProperties(
        Path storageRoot,
        Duration sessionTtl,
        List<String> allowedOrigins,
        long maxModelUploadBytes,
        long maxGeneratedArtifactBytes,
        Duration mdeJobTimeout) {
}
```

---

## Issue 10 — Model records lack revision, metamodel version, and migration state

**Severity:** P1 / High  
**Area:** Metamodel evolution, concurrency, data migration

**Evidence**

`ModelRecord` stores `id`, `projectId`, `level`, `name`, `modelJson`, `createdAt`, and `updatedAt`
only.

**Why this is an issue**

The project is actively evolving metamodels. Without a stored `metamodelVersion` or `schemaVersion`,
the backend cannot reliably know whether an old model conforms to the current metamodel. Without a
`revision`, concurrent edits and transformations can overwrite each other.

**Concrete fix**

Extend `ModelRecord`:

```java
public record ModelRecord(
        String id,
        String projectId,
        ModelLevel level,
        String name,
        JsonNode modelJson,
        String metamodelVersion,
        String metamodelHash,
        long revision,
        String sourceXmiHash,
        Instant createdAt,
        Instant updatedAt) {
}
```

On update, require `expectedRevision` from the client and reject stale writes:

```java
if (request.expectedRevision() != existing.revision()) {
    throw new PlatformException(409, "Model was modified by another operation.");
}
```

Add migrations:

```java
interface ModelMigration {
    boolean supports(ModelLevel level, String fromVersion, String toVersion);
    JsonNode migrate(JsonNode source);
}
```

---

## Issue 11 — No optimistic locking or per-model lock for update/transform/generate

**Severity:** P1 / High  
**Area:** Concurrency, consistency

**Evidence**

`ModelService.update` performs read-modify-write without a revision check. `TransformationService`
reads a source model and creates derived models with no lock against concurrent updates to the
source.

**Why this is an issue**

Two tabs/users can save the same model concurrently and lose changes. A transformation can start
from revision N while another request updates to revision N+1; the generated PIM/PSM then looks
current but was derived from stale input.

**Concrete fix**

Use both optimistic locking and operation locks:

```java
public interface ModelLockService {
    <T> T withModelLock(String modelId, Duration timeout, Callable<T> operation);
}
```

For transformation jobs, record source revision/hash:

```java
GeneratedModel generated = transform(source);
target.modelJson().put("sourceModelRevision", source.revision());
target.modelJson().put("sourceModelHash", hash(source.modelJson()));
```

Reject transform if source changes while queued/running unless the job explicitly locks that source
revision.

---

## Issue 12 — Model lookup scans all project directories

**Severity:** P2 / Medium  
**Area:** Performance, authorization design

**Evidence**

`ModelService.find` lists all project directories and tries to read `models/<level>/<id>.json` from
each. `ArtifactService.find` uses a similar all-project scan.

**Why this is an issue**

This is O(number of projects) for every lookup and will degrade as the repository grows. It also
checks authorization after locating a model, creating avoidable cross-project lookup behavior.

**Concrete fix**

Change APIs and storage layout to resolve by project ID whenever possible:

```java
public ModelRecord get(UserRecord user, ModelLevel level, String projectId, String id) {
    ProjectRecord project = projectService.get(user, projectId);
    ModelRecord model = store.require(modelPath(projectId, level, id), ModelRecord.class, "Model not found.");
    return clientRecord(model);
}
```

For global IDs, maintain a small index:

```text
indexes/models/{modelId}.json -> { projectId, level, modelId }
indexes/artifacts/{artifactId}.json -> { projectId, artifactId }
```

---

## Issue 13 — Repository root discovery is duplicated and brittle

**Severity:** P1 / High  
**Area:** Deployment architecture, maintainability

**Evidence**

Both `TransformationService` and `ModelService` scan upward from `System.getProperty("user.dir")`
and check for hard-coded paths under `mde/...`.

**Why this is an issue**

This works in a development checkout but is brittle in:

- packaged Spring Boot JARs;
- Docker containers with different working directories;
- tests that run from module directories;
- production installations where MDE assets are classpath resources or external mounted config.

**Concrete fix**

Introduce one configurable component:

```java
@ConfigurationProperties(prefix = "modless.mde")
public record MdeProperties(
        Path repositoryRoot,
        Path validationRoot,
        Path transformationRoot,
        Path generationRoot,
        Path metamodelRoot,
        boolean preferClasspathAssets) {
}

public final class MdeRuntimePaths {
    private final MdeProperties properties;

    public Path cimToPimEtl() { return transformationRoot().resolve("cim-to-pim/cim-to-pim.etl"); }
    public Path cimMetamodel() { return metamodelRoot().resolve("cim/cim-combined.ecore"); }
    // ...
}
```

Inject `MdeRuntimePaths` into `ModelService`, `TransformationService`, CLI commands, and tests.

---

## Issue 14 — Metamodel source is inconsistent between JSON/XMI import and Epsilon execution

**Severity:** P1 / High  
**Area:** Metamodel consistency

**Evidence**

`XmiModelImportService` loads combined Ecore files from classpath resources under
`modeling/metamodels/...`. The Epsilon services use repository file paths such as
`mde/metamodels/cim/cim-combined.ecore`.

**Why this is an issue**

In a packaged backend, classpath metamodels may differ from the repository files used by
ETL/EVL/EGX. This can cause a model to export/import successfully but fail validation or
transformation, or the reverse.

**Concrete fix**

Create a single `MetamodelResolver` and use it everywhere:

```java
public interface MetamodelResolver {
    MetamodelDescriptor resolve(ModelLevel level);
}

public record MetamodelDescriptor(
        ModelLevel level,
        URI uri,
        Path file,
        List<EPackage> packages,
        String version,
        String sha256) {
}
```

For classpath-only deployment, copy metamodel resources to a controlled temp/cache directory and use
those same files for Epsilon. For repository deployment, use repository files everywhere.

---

## Issue 15 — Request-time code mutates global EMF registries

**Severity:** P1 / High  
**Area:** Concurrency, global state

**Evidence**

`XmiModelImportService.newResourceSet` writes to `Resource.Factory.Registry.INSTANCE`.
`registerPackage` writes to `EPackage.Registry.INSTANCE`.

**Why this is an issue**

Global EMF registries are JVM-wide mutable state. Mutating them per request can create race
conditions, cross-test pollution, and hard-to-debug behavior when multiple metamodel versions are
loaded.

**Concrete fix**

Only register factories/packages on the local `ResourceSet` unless application startup intentionally
registers immutable packages once.

Preferred per-request local-only approach:

```java
private ResourceSet newResourceSet() {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
            .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
            .put("xmi", new XMIResourceFactoryImpl());
    resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
    return resourceSet;
}

private void registerPackage(ResourceSet resourceSet, EPackage ePackage) {
    if (ePackage.getNsURI() != null && !ePackage.getNsURI().isBlank()) {
        resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
    }
    ePackage.getESubpackages().forEach(child -> registerPackage(resourceSet, child));
}
```

If global registration is needed, perform it once at startup under a lock and never replace packages
for a different metamodel version.

---

## Issue 16 — Metamodels are loaded repeatedly for every import/export/validation

**Severity:** P2 / Medium  
**Area:** Performance

**Evidence**

`XmiModelImportService` creates a new `ResourceSet` and reloads the classpath Ecore stream every
import/export. `ModelService.validateWithEvl` writes a temp XMI file and uses file-based metamodel
loading per request.

**Why this is an issue**

Metamodel loading is not the biggest cost, but it is repeated on every validation, import, export,
and transformation. With real-time editor validation, this becomes noticeable.

**Concrete fix**

Cache immutable metamodel descriptors:

```java
public final class CachedMetamodelResolver implements MetamodelResolver {
    private final ConcurrentMap<ModelLevel, MetamodelDescriptor> cache = new ConcurrentHashMap<>();

    public MetamodelDescriptor resolve(ModelLevel level) {
        return cache.computeIfAbsent(level, this::loadDescriptor);
    }
}
```

For EMF `ResourceSet`s, create a fresh `ResourceSet` per request, but register the cached `EPackage`
instances locally.

---

## Issue 17 — JSON-to-XMI export silently drops invalid values

**Severity:** P0 / Correctness blocker  
**Area:** Model serialization

**Evidence**

`XmiModelImportService.ExportContext.attributeValue` returns `null` for unknown enum literals and
for datatype conversion failures. `setAttribute` only sets the value if conversion returns non-null.

**Why this is an issue**

Invalid user JSON can be silently converted into an XMI model with missing attributes. Ecore lower
bounds or EVL may catch some missing values later, but the diagnostic will be indirect and the
original invalid value is lost.

**Concrete fix**

Make export strict and collect structured conversion diagnostics.

```java
private Object attributeValue(EObject owner, EAttribute attribute, JsonNode value,
        ExportDiagnostics diagnostics) {
    EDataType type = attribute.getEAttributeType();
    if (type instanceof EEnum eEnum) {
        String literal = scalarText(value);
        EEnumLiteral enumLiteral = eEnum.getEEnumLiteral(literal);
        if (enumLiteral == null) {
            enumLiteral = eEnum.getEEnumLiteralByLiteral(literal);
        }
        if (enumLiteral == null) {
            diagnostics.error(owner, attribute, "Unknown enum literal '" + literal + "'.");
            return null;
        }
        return enumLiteral.getInstance();
    }
    try {
        return type.getEPackage().getEFactoryInstance().createFromString(type, scalarText(value));
    } catch (RuntimeException ex) {
        diagnostics.error(owner, attribute, "Invalid value '" + scalarText(value) + "': " + ex.getMessage());
        return null;
    }
}
```

After export, fail if diagnostics contain errors. Surface those errors in `ModelService.validate`
and import responses.

---

## Issue 18 — JSON-to-XMI export silently ignores unresolved references

**Severity:** P0 / Correctness blocker  
**Area:** Model serialization, transformation correctness

**Evidence**

`ExportContext.resolveReferences` maps reference IDs through `objectsById`, filters nulls, and
continues. Missing referenced IDs are not reported.

**Why this is an issue**

A broken editor model can silently lose references such as command→event, route→function, or
workflow transition endpoints. Transformations may produce incomplete or semantically wrong target
models.

**Concrete fix**

Fail on unresolved references unless explicitly configured as lenient.

```java
private void resolveReference(PendingReference pending, String id, ExportDiagnostics diagnostics) {
    EObject target = objectsById.get(id);
    if (target == null) {
        diagnostics.error(pending.owner(), pending.reference(),
                "Unresolved reference id '" + id + "' for " + pending.reference().getName());
        return;
    }
    // assign target
}
```

Add an option:

```java
public record XmiExportOptions(boolean strictReferences, boolean strictAttributes) { }
```

Default backend validation and transformations should use strict mode.

---

## Issue 19 — Contained child type errors can be masked by fallback to expected type

**Severity:** P1 / High  
**Area:** Model serialization correctness

**Evidence**

`ExportContext.eClassFor` returns `expectedType` when a requested `eClass` is unknown.

**Why this is an issue**

If the frontend sends a malformed child object with the wrong `eClass`, export may silently create
the containment reference’s expected type instead of failing. This hides real editor/backend
contract bugs.

**Concrete fix**

Only use `expectedType` when `eClass`/`type` is absent, not when it is present but invalid.

```java
private EClass eClassFor(String requestedType, EClass expectedType) {
    if (requestedType != null && !requestedType.isBlank()) {
        EClass found = findEClass(requestedType);
        if (found == null) {
            throw new PlatformException(400, "Unknown model element type: " + requestedType);
        }
        if (expectedType != null && !expectedType.isSuperTypeOf(found)) {
            throw new PlatformException(400,
                    "Element type " + requestedType + " is not valid for containment " + expectedType.getName());
        }
        return found;
    }
    if (expectedType != null) {
        return expectedType;
    }
    throw new PlatformException(400, "Missing model element type.");
}
```

---

## Issue 20 — Imported XMI root type is not validated against the requested model level

**Severity:** P1 / High  
**Area:** Import correctness

**Evidence**

`XmiModelImportService.importModel` loads a resource and uses the first `EObject` root it finds.

**Why this is an issue**

A user can upload a PSM XMI through the CIM import endpoint or an arbitrary XMI root that happens to
use the loaded packages. The backend should reject wrong root types explicitly.

**Concrete fix**

Define expected root class per level:

```java
private String expectedRootEClass(ModelLevel level) {
    return switch (level) {
        case CIM -> "CIMModel";
        case PIM -> "PIMModel";
        case PSM -> "AwsPsmModel";
    };
}

private void validateRoot(ModelLevel level, EObject root) {
    String expected = expectedRootEClass(level);
    if (!root.eClass().getName().equals(expected)) {
        throw new PlatformException(400,
                "Expected " + expected + " root for " + level + " import but found " + root.eClass().getName());
    }
}
```

Also reject resources with zero roots or multiple roots unless multi-root import is explicitly
supported.

---

## Issue 21 — Generated target models are saved without running target-level EVL validation

**Severity:** P1 / High  
**Area:** Transformation quality gate

**Evidence**

After ETL, `TransformationService` imports the generated XMI into JSON and immediately creates the
target model record. It does not run PIM EVL after CIM→PIM or PSM EVL after PIM→PSM before saving
the result.

**Why this is an issue**

ETL bugs, stale metamodels, or invalid input can produce target models that conform structurally
enough to import but violate target semantic rules. Users may continue from a broken generated
model.

**Concrete fix**

Validate generated target XMI before saving:

```java
ValidationResult targetValidation = modelService.validateGeneratedXmi(ModelLevel.PIM, Files.readAllBytes(pimXmi));
if (!targetValidation.valid()) {
    ObjectNode target = (ObjectNode) imported;
    target.put("transformationStatus", "GENERATED_WITH_VALIDATION_ERRORS");
    target.set("validationIssues", mapper.valueToTree(targetValidation.issues()));
    // either save as review-required or fail based on policy
}
```

Suggested policy:

- structural/metamodel errors: fail transformation;
- EVL errors: save generated model with `BLOCKED` status and explicit issues;
- critiques/warnings: save as `REVIEW_REQUIRED`.

---

## Issue 22 — EVL validation uses temp files even though in-memory model support exists

**Severity:** P2 / Medium  
**Area:** Performance, simplicity

**Evidence**

`ResourceEvlModelConfiguration` supports `InMemoryEmfModel`, but `ModelService.validateWithEvl`
always exports JSON to a temp XMI file, then loads it as a `FileEvlModelConfiguration`.

**Why this is an issue**

Temp-file round trips add disk I/O and cleanup overhead. They also make validation more fragile in
containerized deployments with limited temp storage.

**Concrete fix**

Expose an XMI export method that returns an EMF `Resource` and validate it directly:

```java
public Resource exportResource(ModelLevel level, JsonNode modelJson, XmiExportOptions options) { ... }
```

Then:

```java
Resource resource = xmiImportService.exportResource(level, hydrateSemanticReferences(modelJson), STRICT);
List<EPackage> packages = metamodelResolver.resolve(level).packages();
EvlValidationReport report = evlValidator.validate(new EvlValidationRequest(
        validationRoot,
        List.of(Path.of(validationEntryFile(level))),
        List.of(ResourceEvlModelConfiguration.readOnly(validationModelName(level), resource, packages)),
        true));
```

Keep file-backed validation for CLI compatibility.

---

## Issue 23 — EVL module execution stops after the first module with diagnostics

**Severity:** P2 / Medium  
**Area:** Validation UX

**Evidence**

`EpsilonEvlValidator.validate` loops through modules and throws if a module report contains an error
diagnostic. That skips later modules.

**Why this is an issue**

For a multi-file validation suite, users need as many diagnostics as possible in one run. Stopping
after the first parse/runtime error hides errors in later modules and slows metamodel/script
stabilization.

**Concrete fix**

Continue parsing/running other independent modules after a module failure, unless the model cannot
load at all.

```java
boolean fatalModelLoadFailure = false;
for (Path moduleFile : modules) {
    EvlModuleReport moduleReport = validateModule(moduleFile, request, stdout, warnings, stderr);
    moduleReports.add(moduleReport);
    violations.addAll(moduleReport.violations());
    diagnostics.addAll(moduleReport.diagnostics());
}
EvlValidationStatus status = diagnostics.stream().anyMatch(d -> d.severity() == ERROR)
        ? FAILED : SUCCEEDED;
return report(status, ...);
```

Only short-circuit when request validation fails or every model fails to load.

---

## Issue 24 — EVL diagnostics may expose sensitive model attribute values

**Severity:** P1 / High  
**Area:** Security/privacy

**Evidence**

`EpsilonEvlValidator.elementReference` iterates all scalar attributes of the offending EObject and
includes them in the violation response.

**Why this is an issue**

Models may contain secrets, examples, tokens, personal data, or business-sensitive values. Returning
every scalar attribute in diagnostics can leak data to logs, clients, or other integrations.

**Concrete fix**

Whitelist safe fields and redact sensitive-looking fields:

```java
private static final Set<String> SAFE_DIAGNOSTIC_ATTRIBUTES = Set.of(
        "id", "name", "eClass", "logicalId", "stackName", "stageName", "pathTemplate", "method");

private boolean isSensitiveAttribute(EAttribute attribute) {
    String n = attribute.getName().toLowerCase(Locale.ROOT);
    return n.contains("secret") || n.contains("password") || n.contains("token")
            || n.contains("key") || n.contains("value") || n.contains("email");
}
```

Return only safe identifiers by default. Add a privileged/debug mode for full diagnostics in
development.

---

## Issue 25 — File-level EMF validation returns only the first Diagnostician problem

**Severity:** P2 / Medium  
**Area:** Validation UX

**Evidence**

`FileEvlModelConfiguration.validateModelResource` walks `Diagnostician.INSTANCE.validate(root)` and
throws on the first non-OK problem.

**Why this is an issue**

Users receive one structural error at a time even when a model has many missing required features.
This slows model correction and makes the platform feel less mature.

**Concrete fix**

Collect all EMF diagnostics into `EvlDiagnostic` entries instead of throwing on the first.

```java
public List<EvlDiagnostic> validateModelResource(EmfModel model, Path modelFile) {
    List<EvlDiagnostic> out = new ArrayList<>();
    for (EObject root : model.getResource().getContents()) {
        collectDiagnostics(Diagnostician.INSTANCE.validate(root), modelFile, out);
    }
    return out;
}
```

Then have `EpsilonEvlValidator` merge those diagnostics into the validation report before EVL
execution.

---

## Issue 26 — ETL target models are stored twice

**Severity:** P1 / High  
**Area:** Execution correctness/performance

**Evidence**

`EtlModelConfiguration.target` sets `storeOnDisposal=true`. `EpsilonEtlExecutor` also explicitly
calls `model.store()` in `storeModels`, then later disposes models in `finally`.

**Why this is an issue**

If Epsilon stores on disposal, explicit `store()` plus disposal can write the same model twice. This
is inefficient and can introduce subtle behavior if save options differ or the first store partially
succeeds.

**Concrete fix**

Choose exactly one persistence mechanism. Recommended: executor-controlled explicit store and
`storeOnDisposal=false`.

```java
public static EtlModelConfiguration target(...) {
    return new EtlModelConfiguration(name, aliases, modelFile, metamodelFiles,
            readOnLoad, false, false, false);
}
```

Keep `storeModels(...)` as the only save path and make disposal non-persisting.

---

## Issue 27 — ETL store failure report loses original runtime context

**Severity:** P2 / Medium  
**Area:** Diagnostics

**Evidence**

When `storeModels` fails, it creates an `EtlExecutionException` with a report built using
`Instant.now()` and new empty output streams instead of preserving the original start
time/stdout/warnings/stderr from the run.

**Why this is an issue**

The most useful diagnostics often appear before the store failure. Losing captured output and
duration makes troubleshooting harder.

**Concrete fix**

Pass the original `startedAt`, `stdout`, `warnings`, and `stderr` into `storeModels` and use them in
the failure report.

```java
private void storeModels(EtlExecutionRequest request, List<IModel> loadedModels,
        List<EtlDiagnostic> diagnostics, Instant startedAt,
        ByteArrayOutputStream stdout, ByteArrayOutputStream warnings,
        ByteArrayOutputStream stderr) throws EtlExecutionException { ... }
```

---

## Issue 28 — Java pre-seeds Epsilon global variables that are also managed by EOL scripts

**Severity:** P2 / Medium  
**Area:** Runtime/script coupling

**Evidence**

`EpsilonEtlExecutor.configureTransformationState` injects a long list of global variables such as
`cachedCimRoot`, `cachedPimRoot`, `cachedAwsRoot`, and many cache maps. The EOL scripts also
initialize these caches.

**Why this is an issue**

This is a hidden contract between Java and EOL scripts. Every time the EOL scripts add/remove cache
variables, Java must be updated. This is brittle and easy to forget.

**Concrete fix**

Move transformation state initialization fully into EOL, or expose a single Java-provided `runtime`
object.

Preferred:

```java
private void configureTransformationState(EtlModule module) {
    module.getContext().getFrameStack().putGlobal(
        new Variable("runtime", new MdeRuntimeFacade(), EolAnyType.Instance));
}
```

Then EOL scripts call `runtime.newLinkedHashSet()` if Java-backed collections are needed. Otherwise
use pure EOL variables initialized in `initialiseCimToPimCaches()` and
`initialisePimToAwsPsmCaches()`.

---

## Issue 29 — Runner and platform services are directly constructed instead of injected/configured

**Severity:** P2 / Medium  
**Area:** Modularity, testability, observability

**Evidence**

`TransformationService` constructs `XmiModelImportService`, `EpsilonEtlExecutor`, and
`EpsilonEgxGenerator` internally. `ModelService` constructs `XmiModelImportService` and
`EpsilonEvlValidator` internally.

**Why this is an issue**

Direct construction makes it hard to:

- inject timeouts and resource limits;
- mock runners in tests;
- add metrics/tracing/logging decorators;
- swap file-backed vs in-memory validation;
- centralize metamodel resolution.

**Concrete fix**

Make the dependencies constructor-injected beans:

```java
public ModelService(JsonFileStore store,
        ProjectService projectService,
        ModelingConfigService modelingConfig,
        XmiModelIo xmiModelIo,
        EpsilonEvlValidator evlValidator,
        MdeRuntimePaths runtimePaths) { ... }
```

Update `CoreServicesConfig`:

```java
@Bean
EpsilonEtlExecutor etlExecutor() { return new EpsilonEtlExecutor(); }

@Bean
XmiModelIo xmiModelIo(MetamodelResolver resolver, ObjectMapper mapper) {
    return new XmiModelIo(mapper, resolver);
}
```

---

## Issue 30 — Hard-coded reference-kind maps will drift from the metamodels

**Severity:** P1 / High  
**Area:** Maintainability, graph correctness

**Evidence**

`XmiModelImportService` contains large static maps for CIM, PIM, and PSM reference feature names to
graph edge kinds.

**Why this is an issue**

After metamodel changes, these maps must be updated manually. A missed entry will produce incorrect
visual graph semantics or missing/incorrect edge labels. This is especially risky because the
project intentionally evolves the metamodels.

**Concrete fix**

Derive graph edge metadata from the Ecore model itself. Options:

1. Add EAnnotations in Emfatic/Ecore, for example `@Graph(kind="PUBLISHES")`.
2. Maintain a generated JSON registry built from the metamodel during build.
3. Fall back to deterministic feature-name transformation only when no annotation exists.

Suggested EAnnotation lookup:

```java
private String relationshipKind(EReference reference) {
    EAnnotation graph = reference.getEAnnotation("https://modless.org/graph");
    if (graph != null && graph.getDetails().containsKey("kind")) {
        return graph.getDetails().get("kind");
    }
    return defaultKind(reference.getName());
}
```

---

## Issue 31 — EGX prelude injection is brittle and shifts template line numbers

**Severity:** P2 / Medium  
**Area:** Generation maintainability, diagnostics

**Evidence**

`PreludeInjectingTemplateFactory` prepends a hard-coded list of `import` statements to every EGL
template before parsing it.

**Why this is an issue**

This creates several problems:

- line numbers in EGL diagnostics are shifted by the injected prelude;
- imports are hidden from templates, making dependencies less explicit;
- the hard-coded import list must be updated whenever generator libraries change;
- duplicate imports and circular helper dependencies become harder to reason about.

**Concrete fix**

Prefer explicit imports in templates or a single generated `prelude.eol` imported by each template.
If injection stays, preserve source mapping:

```java
module.parse(eglPrelude + templateText, new File(uri));
// When reporting diagnostics, subtract preludeLineCount from template diagnostics.
```

Better: make EGX parse a top-level coordination module that imports the shared EOL libraries once
and passes helper state explicitly.

---

## Issue 32 — Artifact trace post-processing parses JSON by line scanning

**Severity:** P2 / Medium  
**Area:** Generation correctness

**Evidence**

`EpsilonEgxGenerator.finalizeGeneratedTraceFiles` reads `generated/trace/artifact-trace.json` line
by line and replaces placeholder strings based on line prefixes and the most recently seen `path`
field.

**Why this is an issue**

Line-based JSON manipulation is fragile. Formatting changes, reordered fields, escaped strings,
nested fields, or minified output can break checksum assignment.

**Concrete fix**

Parse and update JSON using Jackson:

```java
ObjectNode root = (ObjectNode) objectMapper.readTree(artifactTrace.toFile());
root.put("sourceModelHash", sha256Streaming(request.models().getFirst().modelFile()));
ArrayNode artifacts = (ArrayNode) root.path("artifacts");
for (JsonNode artifact : artifacts) {
    ObjectNode artifactObject = (ObjectNode) artifact;
    Path artifactPath = request.outputDirectory().resolve(artifactObject.path("path").asText()).normalize();
    artifactObject.put("generatedChecksum", Files.isRegularFile(artifactPath)
            ? sha256Streaming(artifactPath)
            : "MISSING");
}
objectMapper.writerWithDefaultPrettyPrinter().writeValue(artifactTrace.toFile(), root);
```

Use `DigestInputStream` instead of `Files.readAllBytes` for large files.

---

## Issue 33 — Generated artifacts are loaded and persisted as one in-memory `Map<String,String>`

**Severity:** P1 / High  
**Area:** Artifact storage, scalability

**Evidence**

`TransformationService.generatedFiles` walks the output directory and reads every file as a UTF-8
string. `ArtifactService.create` stores all generated files inside one `ArtifactRecord`. ZIP export
builds the whole ZIP in memory.

**Why this is an issue**

Generated projects can include many files, large templates, dependency manifests, and eventually
binary assets. A single JSON record containing every file does not scale and makes partial updates
inefficient.

**Concrete fix**

Store artifacts as a directory tree plus metadata:

```text
projects/{projectId}/artifacts/{artifactId}/metadata.json
projects/{projectId}/artifacts/{artifactId}/files/<generated tree>
```

Change `ArtifactRecord` to store metadata only:

```java
public record ArtifactRecord(
        String id,
        String projectId,
        String name,
        JsonNode metadata,
        int fileCount,
        long totalBytes,
        Instant createdAt,
        Instant updatedAt) {
}
```

Stream ZIP output directly to `HttpServletResponse`:

```java
@GetMapping("/api/artifacts/{id}/zip")
void zip(..., HttpServletResponse response) {
    response.setContentType("application/zip");
    artifactService.writeZip(auth.user(token), id, response.getOutputStream());
}
```

---

## Issue 34 — Generated file collection assumes all artifacts are UTF-8 text

**Severity:** P2 / Medium  
**Area:** Generation completeness

**Evidence**

`generatedFiles` calls `Files.readString(path, StandardCharsets.UTF_8)` for every generated file.

**Why this is an issue**

The project may later generate ZIPs, images, compiled bundles, certificates, binary schema examples,
or other non-text assets. The current implementation cannot represent them safely.

**Concrete fix**

Introduce an artifact file abstraction:

```java
public record GeneratedArtifactFile(
        String path,
        String mediaType,
        boolean binary,
        byte[] content,
        String textContent,
        long sizeBytes,
        String sha256) {
}
```

If staying file-backed, store bytes on disk and expose content type/encoding metadata.

---

## Issue 35 — Validation logic is duplicated between EVL and Java/UI configuration checks

**Severity:** P2 / Medium  
**Area:** Maintainability, consistency

**Evidence**

`ModelService.validate` runs EVL and then additional Java-side CIM validation driven by
`ModelingConfigService.config()`.

**Why this is an issue**

Required features and semantic constraints can diverge between the metamodel, EVL, and frontend
configuration. Users may see inconsistent validation results depending on whether the model came
from XMI, JSON, frontend graph, or backend export.

**Concrete fix**

Make EVL the authoritative semantic validation layer. Generate frontend required-field metadata from
Ecore/EVL, not the other way around. If Java preflight checks remain, keep them minimal and generic:

- unknown element type;
- duplicate IDs;
- broken graph references;
- JSON not object;
- upload size/format.

Move CIM-specific required-feature rules into EVL or Ecore lower bounds.

---

## Issue 36 — The custom required-feature validator is CIM-only and config-dependent

**Severity:** P2 / Medium  
**Area:** Validation coverage

**Evidence**

`validateCimModel` only runs for CIM. It indexes frontend modeling definitions and checks required
attributes/references from that config.

**Why this is an issue**

PIM and PSM JSON editor models can also miss required features before XMI export or validation.
Relying on frontend config for CIM only creates uneven validation quality.

**Concrete fix**

Replace with an Ecore-driven generic JSON preflight validator for all levels:

```java
public List<ValidationIssue> validateJsonAgainstEcoreShape(ModelLevel level, JsonNode model) {
    EPackage rootPackage = metamodelResolver.resolve(level).rootPackage();
    // recursively validate eClass existence, required attributes/references, multiplicity, enum literals, ID uniqueness
}
```

Run it before exporting to XMI so users get precise JSON-path diagnostics.

---

## Issue 37 — Model import/export API only supports JSON export publicly, despite internal XMI support

**Severity:** P3 / Improvement  
**Area:** API completeness, MDE usability

**Evidence**

`ModelService.exportModel(JsonNode modelJson, String format)` only supports JSON. Internal code can
export JSON to XMI via `XmiModelImportService.exportModel`.

**Why this is an issue**

For an academic MDE platform, being able to export XMI is important for reproducibility, integration
with Eclipse/EMF tools, and thesis evaluation.

**Concrete fix**

Expose authenticated XMI export:

```java
public byte[] exportModel(ModelLevel level, JsonNode modelJson, String format) {
    return switch (format.toLowerCase(Locale.ROOT)) {
        case "json" -> mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(modelJson);
        case "xmi" -> xmiModelIo.exportModel(level, hydrateSemanticReferences(modelJson));
        default -> throw new PlatformException(400, "Unsupported export format.");
    };
}
```

Update `ModelController.export` to pass the level and correct media type.

---

## Issue 38 — Epsilon model loading uses different EMF property styles in ETL/EVL versus EGX

**Severity:** P2 / Medium  
**Area:** Runtime consistency

**Evidence**

ETL/EVL use `PROPERTY_MODEL_URI` and `PROPERTY_FILE_BASED_METAMODEL_URI`. EGX uses
`PROPERTY_IS_METAMODEL_FILE_BASED`, `PROPERTY_MODEL_FILE`, and `PROPERTY_METAMODEL_FILE`.

**Why this is an issue**

Different loading paths can differ subtly in URI resolution, imported Ecore resolution, platform
paths, and Windows path behavior. This can produce “works in validation, fails in generation”
issues.

**Concrete fix**

Create a shared `EmfModelLoader` used by ETL, EVL, and EGX:

```java
public final class EmfModelLoader {
    public EmfModel loadFileModel(String name, List<String> aliases, Path modelFile,
            List<Path> metamodelFiles, EmfModelMode mode) throws EolModelLoadingException {
        StringProperties properties = new StringProperties();
        properties.put(Model.PROPERTY_NAME, name);
        properties.put(Model.PROPERTY_ALIASES, String.join(",", aliases));
        properties.put(Model.PROPERTY_READONLOAD, Boolean.toString(mode.readOnLoad()));
        properties.put(Model.PROPERTY_STOREONDISPOSAL, Boolean.toString(mode.storeOnDisposal()));
        properties.put(Model.PROPERTY_READONLY, Boolean.toString(mode.readOnly()));
        properties.put(EmfModel.PROPERTY_MODEL_URI, fileUri(modelFile));
        properties.put(EmfModel.PROPERTY_FILE_BASED_METAMODEL_URI, joinFileUris(metamodelFiles));
        properties.put(EmfModel.PROPERTY_VALIDATE, Boolean.toString(mode.validate()));
        EmfModel model = new EmfModel();
        model.load(properties);
        return model;
    }
}
```

---

## Issue 39 — Epsilon diagnostics are not persisted as first-class execution records

**Severity:** P2 / Medium  
**Area:** Traceability, thesis evidence, production operations

**Evidence**

ETL/EGX reports are summarized into HTTP errors or used transiently. Generated model records get
status fields, but there is no durable execution record containing exact script path, model hash,
metamodel hash, diagnostics, stdout/stderr, duration, and result IDs.

**Why this is an issue**

For academic reproducibility and production auditability, every transformation/generation should be
reproducible and traceable.

**Concrete fix**

Persist `MdeExecutionRecord`:

```java
public record MdeExecutionRecord(
        String id,
        String operation,
        String sourceModelId,
        String sourceModelHash,
        String sourceMetamodelHash,
        String targetMetamodelHash,
        String epsilonEntryPoint,
        String epsilonCommitOrHash,
        String status,
        List<DiagnosticRecord> diagnostics,
        Duration duration,
        String resultModelId,
        String resultArtifactId,
        Instant createdAt) {
}
```

Store it under `projects/{projectId}/mde-executions/{id}.json` and link generated models/artifacts
to it.

---

## Issue 40 — No structured metrics/tracing around MDE execution

**Severity:** P2 / Medium  
**Area:** Observability

**Evidence**

The executors create duration values in reports, but the backend does not expose metrics or
structured logs around validation/transformation/generation jobs.

**Why this is an issue**

Without metrics, production operators cannot answer:

- Which transformations are slow?
- Which metamodel level fails most often?
- What is the average generated artifact size?
- Are there memory spikes during EGX?
- Are user models frequently invalid after import?

**Concrete fix**

Add Micrometer metrics through Spring Boot Actuator:

```java
Timer.Sample sample = Timer.start(meterRegistry);
try {
    return etlExecutor.execute(request);
} finally {
    sample.stop(Timer.builder("modless.mde.etl.duration")
            .tag("operation", "cim-to-pim")
            .tag("status", status)
            .register(meterRegistry));
}
```

Add counters for validation errors, model import failures, generated file counts, and artifact
bytes.

---

## Issue 41 — No generated model/artifact size limits

**Severity:** P1 / High  
**Area:** Resource governance

**Evidence**

`TransformationService.generatedFiles` collects all files under the EGX output directory.
`ArtifactService.create` stores all files. `ArtifactService.zip` zips all files into memory.

**Why this is an issue**

A template bug can generate thousands of files or huge files. Without limits, the backend can
exhaust disk or heap.

**Concrete fix**

Add limits:

```java
public record GenerationLimits(
        int maxFiles,
        long maxSingleFileBytes,
        long maxTotalBytes,
        int maxPathLength) {
}
```

Enforce during file walking:

```java
long total = 0;
int count = 0;
for (Path path : files) {
    long size = Files.size(path);
    if (++count > limits.maxFiles()) throw ...;
    if (size > limits.maxSingleFileBytes()) throw ...;
    total += size;
    if (total > limits.maxTotalBytes()) throw ...;
}
```

---

## Issue 42 — Dependency versions are duplicated across module POMs

**Severity:** P3 / Maintainability  
**Area:** Build/dependency management

**Evidence**

The root POM manages Spring Boot and internal modules. Individual modules declare their own EMF,
Epsilon, JUnit, ELK, and Xtext versions in module-local properties.

**Why this is an issue**

Version drift becomes likely as the project grows. Epsilon/EMF/Xtext versions must remain compatible
across ETL, EVL, EGX, platform import/export, and tests.

**Concrete fix**

Centralize versions in the root POM:

```xml
<properties>
  <java.version>17</java.version>
  <epsilon.version>2.8.0</epsilon.version>
  <emf.version>2.40.0</emf.version>
  <xtext.version>2.40.0</xtext.version>
  <elk.version>0.11.0</elk.version>
  <junit.version>5.13.1</junit.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.eclipse.epsilon</groupId>
      <artifactId>org.eclipse.epsilon.etl.engine</artifactId>
      <version>${epsilon.version}</version>
    </dependency>
    <!-- repeat for Epsilon/EMF artifacts used by modules -->
  </dependencies>
</dependencyManagement>
```

Add Maven Enforcer rules for dependency convergence and Java version.

---

## Issue 43 — No dependency vulnerability/dependency health gate is visible

**Severity:** P3 / Production readiness  
**Area:** Supply-chain security

**Evidence**

The Maven setup includes normal dependencies but no visible OWASP Dependency-Check, CycloneDX SBOM,
Maven Enforcer dependency convergence, or CI gate in the inspected Java POMs.

**Why this is an issue**

A production-ready backend that accepts user-controlled model files and executes
transformation/generation logic should have dependency scanning and SBOM generation.

**Concrete fix**

Add at least:

```xml
<plugin>
  <groupId>org.owasp</groupId>
  <artifactId>dependency-check-maven</artifactId>
  <version>${owasp.dependency-check.version}</version>
  <configuration>
    <failBuildOnCVSS>7</failBuildOnCVSS>
  </configuration>
</plugin>

<plugin>
  <groupId>org.cyclonedx</groupId>
  <artifactId>cyclonedx-maven-plugin</artifactId>
  <version>${cyclonedx.version}</version>
</plugin>
```

Also run `mvn -DskipTests=false verify` and dependency checks in CI.

---

## Issue 44 — No dedicated model migration strategy for persisted JSON/XMI after metamodel changes

**Severity:** P1 / High  
**Area:** Long-term maintainability

**Evidence**

Models are persisted as JSON and optional XMI sidecars. There is no migration metadata in
`ModelRecord`, and no migration pipeline in `ModelService` before validation/transform/export.

**Why this is an issue**

The user already anticipates metamodel evolution as costly. Without migrations, existing projects
can become unreadable or transform incorrectly after metamodel changes.

**Concrete fix**

Add migration stages:

1. detect model metamodel version/hash;
2. migrate JSON tree;
3. regenerate canonical XMI if needed;
4. validate migrated model;
5. persist migrated revision with migration record.

Example migration API:

```java
public interface ModelMigrationService {
    MigrationResult migrateIfNeeded(ModelRecord record, String targetMetamodelVersion);
}

public record MigrationResult(ModelRecord migrated, List<MigrationChange> changes, boolean changed) {
}
```

Store migration history:

```text
projects/{projectId}/models/{level}/{id}.migrations.json
```

---

## Issue 45 — No separation between visual graph data and semantic model data in the Java conversion boundary

**Severity:** P2 / Medium  
**Area:** Model architecture

**Evidence**

`XmiModelImportService` serializes semantic containment and also constructs `graph.elements` /
`graph.relationships`. `ModelService` and `TransformationService` then hydrate semantic `source`/
`target` from graph relationships.

**Why this is an issue**

The boundary between abstract syntax and editor visualization becomes blurry. Visual graph edges can
accidentally repair or override semantic references. This is risky because the DSML models should be
authoritative for transformation and validation.

**Concrete fix**

Define a clear contract:

- semantic model JSON contains only conforming model data;
- graph/diagram contains only view metadata and references semantic IDs;
- graph data must never be used as the source of semantic truth except in a controlled
  import/adaptation step with diagnostics.

Replace `hydrateSemanticReferences` with an explicit `EditorGraphAdapter`:

```java
public JsonNode mergeEditorGraphIntoSemanticModel(JsonNode semantic, JsonNode graph) {
    // validate relationship element type, source/target compatibility, and report diagnostics
}
```

Run this adapter only when saving editor-originated JSON, not every transformation/validation path.

---

## Issue 46 — Import/export conversion does not preserve enough URI/fragment traceability

**Severity:** P2 / Medium  
**Area:** Traceability, debugging

**Evidence**

Imported XMI is serialized to JSON with generated or explicit IDs. Diagnostics later refer mostly to
class name/id/name. EMF URI fragments are not retained as stable source metadata in model JSON.

**Why this is an issue**

When validation or transformation fails, users benefit from locating the exact XMI element or
original source fragment. This is especially useful for thesis reproducibility.

**Concrete fix**

During import, attach source metadata to model elements:

```java
node.put("sourceUri", resource.getURI().toString());
node.put("sourceFragment", resource.getURIFragment(object));
```

Use existing `TraceableElement.sourceUri` / `sourceReference` fields where possible instead of
ad-hoc JSON fields.

---

## Issue 47 — Transformation outputs can create duplicate derived models without idempotency

**Severity:** P2 / Medium  
**Area:** UX, persistence

**Evidence**

`cimToPim` and `pimToPsm` always create a new target model named `source.name() + "-pim"` or
`source.name() + "-psm"`.

**Why this is an issue**

Repeated clicks or retries create multiple derived models from the same source. Users may lose track
of which one is latest. This also complicates traceability.

**Concrete fix**

Offer two modes:

- `createNewVersion=true`: create a new derived model revision/version;
- `replaceExistingDerived=true`: update the latest derived model for the same source and target
  level.

Persist derivation index:

```text
projects/{projectId}/derivations/{sourceModelId}/{targetLevel}.json
```

Include `sourceModelRevision` and `transformationExecutionId` in generated records.

---

## Issue 48 — Artifact update has no protected-region awareness at backend persistence level

**Severity:** P2 / Medium  
**Area:** Generated-code lifecycle

**Evidence**

The EGX templates generate protected-region trace files, but `ArtifactService.updateFile` simply
overwrites the file content in the artifact record.

**Why this is an issue**

The backend cannot enforce or validate that users only edit allowed protected regions. This makes
later regeneration and merge safety hard.

**Concrete fix**

Persist protected-region metadata from `generated/trace/protected-regions.json` and validate edits:

```java
public ArtifactRecord updateFile(...) {
    ProtectedRegionPlan plan = protectedRegionService.load(artifactId, path);
    if (!plan.allowsEdit(originalContent, newContent)) {
        throw new PlatformException(409, "Edit modifies generated region outside protected areas.");
    }
    ...
}
```

Alternatively, store user edits as patches against protected regions instead of whole-file
replacements.

---

## Issue 49 — Error responses summarize only three diagnostics

**Severity:** P3 / Improvement  
**Area:** Developer experience

**Evidence**

`TransformationService.summarizeDiagnostics` limits reported ETL/EGX diagnostics to three items in
exception messages.

**Why this is an issue**

The UI/API user may need the full structured diagnostic report. Limiting the summary is okay for the
exception message, but the full report should be accessible.

**Concrete fix**

With the job model, persist the full diagnostics and return only a short summary in the HTTP
response. Add an endpoint:

```java
@GetMapping("/api/mde-jobs/{id}/diagnostics")
List<DiagnosticRecord> diagnostics(...) { ... }
```

---

## Issue 50 — Tests should include concurrency, stale-XMI, strict-conversion, and resource-limit scenarios

**Severity:** P2 / Medium  
**Area:** Quality assurance

**Evidence**

The repository contains runner and service tests, but the reviewed implementation has several
high-risk runtime paths that require targeted regression tests.

**Why this is an issue**

MDE bugs often appear at boundaries: JSON↔XMI, metamodel evolution, concurrent edits, and generated
artifact handling. These require explicit test coverage.

**Concrete fix**

Add tests for:

1. stale XMI invalidation after JSON update;
2. duplicate concurrent update rejected by revision;
3. invalid enum literal fails export with explicit diagnostic;
4. unresolved reference fails validation/export;
5. wrong root XMI rejected by level;
6. ETL timeout returns failed job;
7. large Epsilon stdout is truncated, not heap-exhausting;
8. generated artifacts over configured size fail cleanly;
9. target EVL runs after transformation;
10. metamodel version mismatch triggers migration or rejection.

Example stale-XMI test outline:

```java
@Test
void updateJsonInvalidatesImportedSourceXmiBeforeTransformation() {
    // import XMI containing CommandA and save model
    // update same model JSON to contain CommandB without a new _sourceXmiToken
    // execute CIM->PIM
    // assert generated PIM contains CommandB handler and not CommandA handler
}
```

---

## Suggested implementation roadmap

### Phase 1 — Correctness and safety blockers

1. Fix stale source-XMI behavior.
2. Add auth to validate/export/import endpoints.
3. Make JSON-to-XMI export strict for invalid values and unresolved references.
4. Stop global EMF registry mutation.
5. Run target EVL after transformations.
6. Remove ETL double-store.

### Phase 2 — Runtime hardening

1. Introduce asynchronous MDE jobs with timeout/cancellation.
2. Add bounded output capture and generated artifact limits.
3. Add atomic XMI writes and staged-import ownership/expiry.
4. Centralize metamodel/path resolution.
5. Persist full execution records and diagnostics.

### Phase 3 — Scalability and maintainability

1. Add model revision/metamodel version/migration support.
2. Replace artifact `Map<String,String>` storage with file-tree artifact storage.
3. Use in-memory EVL validation for editor-side JSON validation.
4. Generate graph reference kinds from Ecore annotations or a generated registry.
5. Centralize Maven dependency versions and add dependency health gates.

---

## Closing note

The backend is already structured around clear MDE stages, but production-readiness depends on
making the model pipeline deterministic and auditable. The most important architectural principle to
enforce now is: **one canonical model source, one metamodel resolver, one execution/job pipeline,
and strict diagnostics at every boundary**. That will make future metamodel changes, transformation
debugging, and thesis evaluation much safer.
