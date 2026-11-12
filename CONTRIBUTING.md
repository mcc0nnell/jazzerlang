# Contributing

Use Java 21. Maven 4.0.0-rc-7 is the verified build version. Run `mvn test` for compiler tests and `scripts/verify-examples.sh` for generated-source compilation, JUnit regression, bounded Jazzer fuzzing, and negative checks. Set `MVN` to an absolute Maven executable path when it is not on PATH. Dependencies are downloaded from Maven Central; the verification script uses `.m2` inside this checkout.

Keep the language small and fail closed. Grammar changes need SPEC.md updates, positive and negative parser tests, deterministic golden output updates, and an example that compiles against the real Jazzer dependency. Test security boundaries, not just implementation details. Do not add generated binaries, corpora, dependency caches, secrets, or downloaded third-party source. Changes should explain the concrete problem, behavior, and actual verification results.

The compiler has no production library dependencies. Keep CLI filesystem logic separate from parsing/emission. Java compilation resolves target signatures; do not invent Jazzer APIs or claim test results that were not observed. New runtime integrations require official API references and an actual execution test.

Contributions are under Apache-2.0. Add an SPDX header to new source and scripts. No CLA or additional contributor agreement is currently required. Publication and release signing are project-owner responsibilities; local development does not authorize publishing artifacts or creating remote repositories.
