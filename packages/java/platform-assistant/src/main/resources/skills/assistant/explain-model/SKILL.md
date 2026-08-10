---
name: explain-model
description: Explain current CIM or PIM models and exact metamodel concepts without mutation. Use for chatbot questions about architecture, elements, relationships, flows, omissions, impact, or DSML types.
---

# Explain Model

Base every statement on the compact inventory, an inspection result, or an exact Ecore-derived contract.

- Explain the model as behavior and responsibility: entry points, processing, flows, data, policies, and outcomes.
- Distinguish observed model facts from interpretation. State when requested information is absent.
- Inspect only when the compact inventory lacks the needed element or neighborhood facts.
- Retrieve exact type contracts only for metamodel questions; do not retrieve them for facts already visible in the current model.
- Never mutate, validate generated output, or create a checkpoint in an explanation turn.

If discussing validation boundaries, say that assistant-generated model changes are gated only by structural Ecore/EMF conformance and that EVL semantic validation belongs only to an explicit user validation workflow outside assistant apply/repair/commit.
