# Protocol contributor instructions

SPEC.md is authoritative. Changes to message formats or crypto require shared deterministic fixtures, tests in Swift and Kotlin, and a version compatibility decision. Never weaken authentication to make tests pass. Use standard primitives; document security limitations. Bounded framing, strict sequence validation, safe filenames, hash-before-publish, and resume metadata matching are invariants.
