#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
cd "$(dirname "$0")/.."
MVN=${MVN:-mvn}
repo="$PWD/.m2"
jar=target/jazzerlang-0.1.0-SNAPSHOT.jar
out=examples/harness/target/generated-test-sources/jazzerlang
"$MVN" -B -ntp -Dmaven.repo.local="$repo" package
"$MVN" -B -ntp -Dmaven.repo.local="$repo" -f examples/harness/pom.xml clean
for spec in examples/*.jzl; do
  java -jar "$jar" compile "$spec" --out "$out" --package example.fuzz
done
"$MVN" -B -ntp -Dmaven.repo.local="$repo" -f examples/harness/pom.xml test
JAZZER_FUZZ=1 "$MVN" -B -ntp -Dmaven.repo.local="$repo" -f examples/harness/pom.xml -Dtest=MixedFuzzTest test
mkdir -p target/negative
cat > target/negative/Invalid.jzl <<'SPEC'
fuzz Invalid { target java.util.Arrays.hashCode; input data: bytes(max=0); property no_throw; }
SPEC
if java -jar "$jar" compile target/negative/Invalid.jzl --out target/negative/out --package example.fuzz > target/negative/cli.log 2>&1; then
  echo 'ERROR: invalid max accepted' >&2; exit 1
fi
grep -q 'max must be in 1..1048576' target/negative/cli.log
cat > target/negative/Instance.jzl <<'SPEC'
fuzz Instance { target java.lang.String.concat; input text: string(max=32); property no_throw; }
SPEC
java -jar "$jar" compile target/negative/Instance.jzl --out "$out" --package example.fuzz
trap 'rm -f "$out/example/fuzz/InstanceFuzzTest.java"' EXIT
if "$MVN" -B -ntp -Dmaven.repo.local="$repo" -f examples/harness/pom.xml test-compile > target/negative/compile.log 2>&1; then
  echo 'ERROR: instance target compiled' >&2; exit 1
fi
grep -q 'non-static method' target/negative/compile.log
echo 'Verified: generated compilation, JUnit regression, bounded Jazzer fuzzing, invalid max and instance target rejection.'
