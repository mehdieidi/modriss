---
name: evolve-existing-model
description: Add or revise features in an existing CIM or PIM model while preserving prior architecture. Use for follow-up chatbot turns, feature requests, incremental edits, and model changes that must reuse existing elements and avoid duplication.
---

# Evolve Existing Model

Start from the saved model as authoritative state.

1. Inspect named reuse targets and any endpoints needed for new connections.
2. Match by inspected identity and role, not name similarity alone.
3. Update an existing element when the feature changes its responsibility or configuration; create a new element only for a genuinely new responsibility.
4. Connect new behavior into the existing execution path. A feature update is incomplete if its new elements remain isolated.
5. Preserve unrelated elements, IDs, containment, and connections. Never rebuild the model merely to add a feature.
6. Avoid deletion unless explicitly requested. If deletion is required, let the backend confirmation workflow control it.
7. Report which existing elements were reused, which were changed, and whether later checkpoints remain.

Use conversation history only to understand intent. Use the current model inventory and inspection results—not prior assistant claims—to decide what actually exists.
