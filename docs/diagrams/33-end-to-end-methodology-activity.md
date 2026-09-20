# End-to-End Methodology Activity Diagram

This activity view shows the vertical increment engine inside the full
`modriss.end-to-end.modeling` lifecycle. Initiation/tailoring, release/transition,
operations/evolution, and retirement/closure surround this engine in the normative
[full-lifecycle method](https://github.com/mehdieidi/modriss/blob/main/mde/process/software-development-process.md).

```mermaid
flowchart TD
    start((Start capability increment)) --> plan["Product Owner + Delivery Lead: frame thin vertical slice<br/>and evidence contract"]
    plan --> cim["Run CIM child process<br/>(modriss.cim.modeling)"]
    cim --> cimGate{"CIM gate evidence<br/>accepted?"}
    cimGate -- "No: rework CIM" --> cim
    cimGate -- "Yes" --> cp["Solution Architect: run CIM → PIM ETL"]
    cp --> pim["Refine PIM child process<br/>(modriss.pim.modeling)"]
    pim --> pimGate{"PIM gate evidence<br/>accepted?"}
    pimGate -- "No: rework PIM" --> pim
    pimGate -- "Yes" --> pp["Cloud Platform Engineer: run PIM → PSM ETL"]
    pp --> psm["Refine PSM child process<br/>(modriss.psm.modeling)"]
    psm --> psmGate{"PSM gate evidence<br/>accepted?"}
    psmGate -- "No: rework PSM" --> psm
    psmGate -- "Yes" --> m2t["Generate artifacts<br/>(awspsm-to-artifacts M2T)"]
    m2t --> artifactReview["Process Reviewer: complete artifact review<br/>and readiness sign-off"]
    artifactReview --> accepted{"Artifacts accepted?"}
    accepted -- "No: fix PSM and regenerate" --> psm
    accepted -- "Yes" --> deliver(["Deliver deployable increment"])
    deliver --> more{"Backlog has another slice?"}
    more -- "Yes: retrospect and adapt" --> plan
    more -- "No" --> release["Release assembly, progressive transition,<br/>operations, and learning"]
    release --> done((Return to lifecycle backlog or retire))
```
