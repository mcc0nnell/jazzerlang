# JazzerLang

JazzerLang 0.1 is a Java 21 compiler front-end for [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer/tree/v0.30.0). It translates a small declarative `.jzl` file into a Java JUnit 5 `@FuzzTest`. Jazzer supplies mutation, instrumentation, corpus management, and execution. The compiler itself has no runtime dependencies.

```text
fuzz ArrayHash {
  target java.util.Arrays.hashCode;
  input data: bytes(max=1024);
  property no_throw;
}
```

Supported inputs are `bytes(max=N)`, `string(max=N)`, `int`, `long`, and `boolean`. Inputs become arguments in declaration order. Only public static Java targets are supported; downstream Java compilation resolves overloads and checks accessibility and static invocation. Return values are discarded. `no_throw` means exceptions propagate to Jazzer. An exception is a finding to investigate, not automatically a vulnerability.

## Build and run

Use JDK 21 and Maven. The seed has been verified locally with **Apache Maven 4.0.0-rc-7**, not every Maven 4 release. No wrapper or JazzerLang Maven plugin is provided.

```sh
mvn test
mvn package
java -jar target/jazzerlang-0.1.0-SNAPSHOT.jar compile examples/ArrayHash.jzl \
  --out examples/harness/target/generated-test-sources/jazzerlang \
  --package example.fuzz
mvn -f examples/harness/pom.xml test
JAZZER_FUZZ=1 mvn -f examples/harness/pom.xml -Dtest=ArrayHashFuzzTest test
```

The supplied harness POM uses the real `com.code-intelligence:jazzer-junit:0.30.0` test dependency and Maven Surefire. Without `JAZZER_FUZZ`, Jazzer runs regression inputs (including the empty input). With it, Jazzer mutates inputs. Generated methods use `@FuzzTest(maxDuration = "5s")`: this fixed v0.1 budget limits fuzzing, not JVM startup or a stuck target. Use an external process timeout for untrusted targets. Select one fuzz test per fuzzing invocation.

Output is `<out>/<package path>/<fuzz name>FuzzTest.java`. The CLI refuses to overwrite existing files; clean the harness **before** generation when rerunning. Do not run Maven `clean` between generation and test compilation. Option pairs can appear in either order. Errors print to stderr and exit 2; success prints the file path and exits 0.

Run the complete repeatable verification (build, all generated examples, regression, real five-second fuzzing, and negative paths):

```sh
scripts/verify-examples.sh
# Or, with a Maven installation outside PATH:
MVN="$PWD/.tools/apache-maven-4.0.0-rc-7/bin/mvn" scripts/verify-examples.sh
```

This script uses a workspace-local `.m2` cache. CI downloads rc7 from Apache's archive and verifies its published SHA512. Pushes to `main` automatically trigger a maintainer-operated GitLab **ci-small** runner, which verifies the exact GitHub push SHA, runs the compiler tests, and compiles and executes the generated `ArrayHashFuzzTest` with Jazzer/JUnit. This private CI job does not run the full coverage-guided fuzzing campaign. The GitHub-hosted Actions workflow is disabled; run `scripts/verify-examples.sh` locally for the full verification suite.

## Examples and Maven integration

`ArrayHash.jzl` hashes bytes; `ArrayEquals.jzl` compares two byte arrays; `StringEquals.jzl` compares two strings. These operations accept every generated value, including empty arrays and strings. `Mixed.jzl` exercises all five types against the included `example.MixedTarget` fixture. Equality examples assert only `no_throw`, not equality of the generated values.

The example POM sets `testSourceDirectory` to the generation directory. For your application, put its target classes or dependency on the test compilation classpath and generate into a test source root. You can generate into `src/test/java` explicitly, or use a separate generated source root registered by your existing build. There is no automatic lifecycle integration: invoke the CLI before `test-compile`. Use an independent harness package: the compiler rejects an output package that is a dotted prefix of the target name. This prevents Java package access from admitting non-public methods.

## Design and scope

A bounded lexer tracks line/column positions. A recursive-descent parser consumes the entire specification, validates names/types/bounds, and constructs an internal immutable model. The emitter uses fixed Java templates and synthesized `arg0`, `arg1`, etc. variables; input names never become Java snippets. Fully qualified API types avoid import collisions. The CLI validates destination paths and writes a new UTF-8 source file. The compiler never loads or executes target classes.

Java compilation is intentionally the authority for target existence, overload resolution, and compatible argument types. JazzerLang performs syntax validation without a target classpath. This is a small compiler, not a type resolver or security sandbox.

Non-goals: instance methods, constructors, arbitrary Java expressions, custom properties, multiple targets per file, null generation, custom mutators, a new fuzzing engine, Maven plugins, vulnerability classification, and automatic publication. No coverage or exhaustive-input guarantee is made. Sequential provider consumption can leave later arguments with little or no remaining input; declared maxima are upper bounds, not promised sizes.

See [SPEC.md](SPEC.md), [SECURITY.md](SECURITY.md), and [CONTRIBUTING.md](CONTRIBUTING.md). Our code is Apache-2.0; see [LICENSE](LICENSE). Third-party tools and dependencies retain their own licenses and are downloaded separately. No third-party implementation code or binary is included.

The generated API usage follows Jazzer's [official JUnit documentation](https://github.com/CodeIntelligenceTesting/jazzer/blob/v0.30.0/README.md#running-jazzer), [FuzzTest annotation](https://github.com/CodeIntelligenceTesting/jazzer/blob/v0.30.0/src/main/java/com/code_intelligence/jazzer/junit/FuzzTest.java), and [FuzzedDataProvider interface](https://github.com/CodeIntelligenceTesting/jazzer/blob/v0.30.0/src/main/java/com/code_intelligence/jazzer/api/FuzzedDataProvider.java).
