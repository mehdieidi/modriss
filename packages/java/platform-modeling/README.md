# platform-modeling

Modeling infrastructure: metamodel resolution, editor configuration, XMI import/export,
ELK-based layout, and MDE runtime path options. Key packages are `platform.modeling.metamodel`,
`platform.modeling.xmi`, `platform.modeling.layout`, `platform.modeling.config`, and
`platform.modeling.runtime`. Layout orchestration that mutates stored models lives in
`platform-model` (`StoredViewLayoutService`) to avoid a cyclic dependency with this module.
