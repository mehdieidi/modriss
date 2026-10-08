# platform-modeling

Modeling infrastructure: metamodel resolution, editor configuration, XMI import/export,
ELK-based layout, and MDE runtime path options. Key packages are `platform.modeling.metamodel`,
`platform.modeling.xmi`, `platform.modeling.layout`, `platform.modeling.config`, and
`platform.modeling.runtime`. Layout orchestration that mutates stored models lives in
`platform-model` (`StoredViewLayoutService`) to avoid a cyclic dependency with this module.

Auto Layout uses one deterministic Eclipse ELK layered algorithm with orthogonal routing,
crossing minimization, separate connection ports, and spacing based on rendered node sizes.
The stored-view service preserves ELK node positions and edge routes; it does not replace them
with a semantic grid or a second edge router. Legacy strategy names resolve to this algorithm.
