// SPDX-License-Identifier: Apache-2.0
package org.jazzerlang;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompilerTest {
    @TempDir Path temp;
    private static final String VALID = "fuzz ArrayHash { target java.util.Arrays.hashCode; input data: bytes(max=1024); property no_throw; }";

    @Test void independentPackageRequired() {
        assertThrows(IllegalArgumentException.class, () -> Compiler.compile(VALID, "java.util"));
        assertThrows(IllegalArgumentException.class, () -> Compiler.compile(VALID, "java"));
        assertDoesNotThrow(() -> Compiler.compile(VALID, "java.util.fuzz"));
    }
    @Test void golden() throws Exception {
        String expected;
        try (var in = getClass().getResourceAsStream("/golden/ArrayHashFuzzTest.java")) {
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(expected, Compiler.compile(VALID, "example.fuzz").source());
    }
    @Test void multipleArgumentsAndTypes() {
        String result = Compiler.compile("fuzz All { target example.Target.call; input provider: string(max=1); input arg0: int; input c: long; input d: boolean; input e: bytes(max=1048576); property no_throw; }", "example.fuzz").source();
        assertTrue(result.contains("java.lang.String arg0 = provider.consumeString(1)"));
        assertTrue(result.contains("int arg1 = provider.consumeInt()"));
        assertTrue(result.contains("long arg2 = provider.consumeLong()"));
        assertTrue(result.contains("boolean arg3 = provider.consumeBoolean()"));
        assertTrue(result.contains("consumeBytes(1048576)"));
        assertTrue(result.contains("example.Target.call(arg0, arg1, arg2, arg3, arg4);"));
        assertFalse(result.contains("catch"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1048577", "999999999999999999999", "1.5", "NaN", "1_024", "", "+1"})
    void invalidMax(String max) {
        assertThrows(Compiler.Diagnostic.class, () -> Compiler.compile(VALID.replace("1024", max), "example"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"../Escape", "foo;System.exit", "class", "record", "$bad", "é", "_", "a/b"})
    void maliciousNames(String name) {
        assertThrows(Compiler.Diagnostic.class, () -> Compiler.compile(VALID.replace("ArrayHash", name), "example"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"java.util.Arrays.hashCode();System.exit", "java..hashCode", "Arrays.hashCode", "java.util.Arrays.class", "java/util/Arrays.hashCode", "java.util.Arrays.<init>", "java.util.Arrays.$bad"})
    void invalidTarget(String target) {
        assertThrows(Compiler.Diagnostic.class, () -> Compiler.compile(VALID.replace("java.util.Arrays.hashCode", target), "example"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "../escape", "foo;System.exit", "java..util", "class.x", "a.", ".a", "a/b", "a.$b"})
    void badPackage(String value) {
        assertThrows(IllegalArgumentException.class, () -> Compiler.compile(VALID, value));
    }
    @Test void structuralFailures() {
        for (String source : new String[] {
                VALID + " extra", VALID.replace("no_throw", "equals"),
                VALID.replace("input data: bytes(max=1024);", ""),
                VALID.replace("property", "input data: int; property"),
                VALID.replace("bytes(max=1024)", "double"),
                VALID.replace("bytes(max=1024)", "int(max=2)"),
                VALID.replace(";", ""), VALID.replace("}", ""),
                VALID.replace("data:", "class:"), " ".repeat(65537)}) {
            assertThrows(Compiler.Diagnostic.class, () -> Compiler.compile(source, "example"), source);
        }
        String many = "fuzz Many { target example.Target.call; " + java.util.stream.IntStream.range(0, 65)
                .mapToObj(i -> "input x" + i + ": int; ").collect(java.util.stream.Collectors.joining()) + "property no_throw; }";
        assertThrows(Compiler.Diagnostic.class, () -> Compiler.compile(many, "example"));
    }
    @Test void diagnosticsHaveLocation() {
        var error = assertThrows(Compiler.Diagnostic.class, () -> Compiler.compile("fuzz Name {\n  target java.util.Arrays.hashCode;\n  input x: bytes(max=0); property no_throw; }", "example"));
        assertTrue(error.getMessage().startsWith("3:22:"), error.getMessage());
    }
    private int run(String... args) {
        return Main.run(args, new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
    }
    private Path spec() throws Exception {
        return Files.writeString(temp.resolve("input.jzl"), VALID);
    }
    @Test void cliSuccessAndNoOverwrite() throws Exception {
        Path spec = spec(); Path out = temp.resolve("out");
        assertEquals(0, run("compile", spec.toString(), "--package", "example.fuzz", "--out", out.toString()));
        Path generated = out.resolve("example/fuzz/ArrayHashFuzzTest.java");
        assertEquals(Compiler.compile(VALID, "example.fuzz").source(), Files.readString(generated));
        assertEquals(2, run("compile", spec.toString(), "--out", out.toString(), "--package", "example.fuzz"));
    }
    @Test void cliFailures() throws Exception {
        Path spec = spec(); String input = spec.toString(), out = temp.resolve("out").toString();
        assertEquals(2, run());
        assertEquals(2, run("compile", input, "--out", out, "--out", out));
        assertEquals(2, run("compile", input, "--wat", out, "--package", "example"));
        assertEquals(2, run("compile", input, "--out", out + "/../escape", "--package", "example"));
        assertEquals(2, run("compile", input, "--out", out, "--package", "../escape"));
        assertEquals(2, run("compile", temp.resolve("missing.jzl").toString(), "--out", out, "--package", "example"));
        Files.writeString(spec, VALID.replace("ArrayHash", "../Escape"));
        assertEquals(2, run("compile", input, "--out", out, "--package", "example"));
        assertFalse(Files.exists(Path.of(out)));
        Files.writeString(spec, " ".repeat(65537));
        assertEquals(2, run("compile", input, "--out", out, "--package", "example"));
        Files.write(spec, new byte[]{(byte) 0xff});
        assertEquals(2, run("compile", input, "--out", out, "--package", "example"));
    }
    @Test void symlinksAndFilesRejected() throws Exception {
        Path spec = spec(); Path real = Files.createDirectory(temp.resolve("real"));
        Path link = Files.createSymbolicLink(temp.resolve("link"), real);
        assertEquals(2, run("compile", spec.toString(), "--out", link.toString(), "--package", "example"));
        Path out = Files.createDirectory(temp.resolve("out"));
        Files.createSymbolicLink(out.resolve("example"), real);
        assertEquals(2, run("compile", spec.toString(), "--out", out.toString(), "--package", "example"));
        assertEquals(2, run("compile", spec.toString(), "--out", spec.toString(), "--package", "example"));
    }
}
