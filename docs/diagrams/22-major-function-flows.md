# Major Function and Algorithm Flows

## `ModelService.create`, `update`, and `patch`

```mermaid
flowchart TD
    start([Create/update/patch request])
    access["Verify project access and editor role"]
    lock{"Mutation on existing model?"}
    acquire["Acquire ModelLockService lock"]
    normalize["Normalize name, modelLevel, and root JSON"]
    revision["Check expectedRevision for updates/patches"]
    patchOps["Apply JSON Pointer operations if PATCH"]
    source["Resolve source XMI update<br/>uploaded hint, existing sidecar, or regenerated XMI"]
    canonical["Canonicalize XMI unless preserving existing sidecar"]
    meta["Resolve metamodel version and SHA-256"]
    persist["Persist model, XMI sidecar, and global index"]
    response["Return client-safe record or summary"]

    start --> access --> lock
    lock -- yes --> acquire --> revision --> patchOps --> normalize
    lock -- no --> normalize
    normalize --> source --> canonical --> meta --> persist --> response
```

## `ModelService.validate`

```mermaid
flowchart TD
    start([Validate model])
    null{"Model JSON null?"}
    stored{"Stored model validation?"}
    cache{"Validation cache hit?"}
    xmi{"Source XMI available?"}
    evlXmi["Run EVL against XMI bytes"]
    evlJson["Hydrate JSON references, export EMF resource, run EVL"]
    stale{"Recoverable stale source issue?"}
    repair["Regenerate XMI from JSON, attach sidecar, revalidate"]
    cim["Add CIM JSON methodology checks for CIM"]
    result["Return valid=false if any ERROR issue"]

    start --> null
    null -- yes --> required["ModelRequired ERROR"] --> result
    null -- no --> stored
    stored -- yes --> cache
    cache -- hit --> result
    cache -- miss --> xmi
    xmi -- yes --> evlXmi --> stale
    stale -- yes --> repair --> result
    stale -- no --> result
    xmi -- no --> evlJson --> cim --> result
    stored -- no --> evlJson
```

## `TransformationService.cimToPim`, `pimToPsm`, `psmToArtifact`

```mermaid
flowchart TD
    start([Transformation request])
    lock["Acquire source model lock"]
    source["Load source and verify revision"]
    temp["Create temp workspace"]
    sourceXmi["Choose sidecar or export source XMI"]
    budget["Enforce input budget"]
    op{"Operation"}
    c2p["CimToPimDefaults + EpsilonEtlExecutor"]
    p2p["PimToAwsPsmDefaults + EpsilonEtlExecutor"]
    p2a["AwsPsmToArtifactsDefaults + EpsilonEgxGenerator"]
    importModel["Import generated XMI to JSON and mark generated target"]
    storeModel["Persist generated PIM/PSM model"]
    readFiles["Read generated files and validate bounds/completeness"]
    storeArtifact["Persist artifact and files"]
    cleanup["Delete temp workspace"]

    start --> lock --> source --> temp --> sourceXmi --> budget --> op
    op -- CIM to PIM --> c2p --> importModel --> storeModel --> cleanup
    op -- PIM to PSM --> p2p --> importModel --> storeModel --> cleanup
    op -- PSM to artifact --> p2a --> readFiles --> storeArtifact --> cleanup
```

## `StoredViewLayoutService.layout`

```mermaid
flowchart TD
    start([Layout stored view])
    load["Load authorized model"]
    find["Find requested view"]
    materialize["Materialize view graph from elements and relationships"]
    existing{"Existing positions and force=false?"}
    restore["Return persisted layout"]
    compute["Call LayoutService with selected strategy"]
    merge["Merge node positions and edge routes into view"]
    patch["Patch model view under expected revision"]
    return["Return view, revision, counts, warnings"]

    start --> load --> find --> materialize --> existing
    existing -- yes --> restore --> return
    existing -- no --> compute --> merge --> patch --> return
```

## Assistant Durable Turn Processing

```mermaid
flowchart TD
    start([Queued turn])
    claim["DurableAssistantTurnWorker claims lease"]
    load["Load thread, model, expected revision, attachments"]
    split["Split source text into source units / source map when present"]
    route{"Durable state and strict<br/>adaptive strategy"}
    conceptual["Conceptual workflow<br/>obligations / types / blueprint / private slices / review / compiler"]
    loop["Inspect/contract AgentTurnLoop<br/>plan / inspect / describe / commit"]
    tools["Compile checked ModelCommandBatch<br/>against ModelWorkspace"]
    guards["Batch guards<br/>UUID ids, evidence IDs, references, containment"]
    validate["ModelService.validateStructural<br/>of resulting workspace"]
    outcome{"Outcome"}
    commit["Commit valid model revision and checkpoint"]
    provenance["Persist source provenance, coverage, and provider-call usage"]
    events["Append durable turn events"]
    terminal["Mark turn terminal"]

    start --> claim --> load --> split --> route
    route -- empty-model conceptual --> conceptual --> tools
    route -- existing/selected/resumed/destructive --> loop --> tools
    route -- answer --> outcome
    tools --> guards --> validate --> outcome
    outcome -- valid work --> commit --> provenance --> events --> terminal
    outcome -- needs input/confirmation/partial/failure --> events --> terminal
```

Both mutation branches use the same provider adapter, workspace, structural gate, revision commit,
checkpoint, provenance, audit, and event lifecycle. Routing never uses request keyword matching.

## `AssistantHardeningService.providerCall`

```mermaid
flowchart TD
    start([Provider call])
    proxy["Check proxy availability if enabled"]
    circuit{"Circuit open?"}
    reject["Fail fast with provider unavailable"]
    attempt["Call provider"]
    success{"Success?"}
    return["Return response"]
    classify["Classify timeout, rate limit, provider failure"]
    retry{"Attempts remaining?"}
    backoff["Sleep retry backoff"]
    record["Record failure and maybe open circuit"]

    start --> proxy --> circuit
    circuit -- yes --> reject
    circuit -- no --> attempt --> success
    success -- yes --> return
    success -- no --> classify --> retry
    retry -- yes --> backoff --> attempt
    retry -- no --> record --> reject
```

## `PostgresPlatformStore` Port Pattern

```mermaid
flowchart LR
    service["Service logical call<br/>read/write/list/delete path"]
    path["Path pattern<br/>projects/{id}/models/{level}/{id}.json etc."]
    adapter["PostgresPlatformStore"]
    route{"Record type/path"}
    json["JSONB table columns"]
    blob["BYTEA source/import payloads"]
    files["Artifact files rows"]
    tx["Transaction boundary"]

    service --> path --> adapter --> route
    route --> json --> tx
    route --> blob --> tx
    route --> files --> tx
```
