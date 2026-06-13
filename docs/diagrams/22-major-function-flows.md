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

## `AssistantCatalogService.refresh`

```mermaid
flowchart TD
    start([Backend startup or refresh])
    walk["Walk mde/ recursively"]
    filter["Keep .emf, .ecore, .evl files"]
    hash["Compute source hash"]
    changed{"Hash changed?"}
    skip["Skip unchanged file"]
    delete["Delete old documents for source"]
    parse{"File type"}
    emf["Parse Emfatic classes and features"]
    ecore["Parse Ecore classifiers and structural features"]
    evl["Parse EVL contexts, constraints, critiques"]
    embed["Generate vector literal with ONNX or hash fallback"]
    upsert["Upsert assistant_retrieval_documents"]

    start --> walk --> filter --> hash --> changed
    changed -- no --> skip
    changed -- yes --> delete --> parse
    parse -- emf --> emf --> embed
    parse -- ecore --> ecore --> embed
    parse -- evl --> evl --> embed
    embed --> upsert
```

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
