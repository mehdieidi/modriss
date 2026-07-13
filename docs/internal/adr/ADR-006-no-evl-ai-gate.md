# ADR-006: No EVL assistant apply gate

Assistant commits gate Ecore/EMF structure and JSON/XMI bridge integrity only. EVL remains
available elsewhere in the platform but is never invoked on the assistant apply path.
