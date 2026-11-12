// SPDX-License-Identifier: Apache-2.0
package org.jazzerlang;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class Main {
    private Main() {}
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length != 6 || !args[0].equals("compile")) throw new IllegalArgumentException(
                    "usage: compile <file.jzl> --out <dir> --package <package>");
            String directory = null, packageName = null;
            for (int i = 2; i < args.length; i += 2) {
                if (args[i].equals("--out") && directory == null) directory = args[i + 1];
                else if (args[i].equals("--package") && packageName == null) packageName = args[i + 1];
                else throw new IllegalArgumentException("unknown or duplicate option: " + args[i]);
            }
            if (directory == null || packageName == null) throw new IllegalArgumentException("--out and --package required");
            Path input = Path.of(args[1]);
            if (!input.toString().endsWith(".jzl")) throw new IllegalArgumentException("input must have .jzl extension");
            byte[] bytes;
            try (var stream = Files.newInputStream(input)) {
                bytes = stream.readNBytes(Compiler.MAX_SPEC_CHARS + 1);
            }
            if (bytes.length > Compiler.MAX_SPEC_CHARS) throw new IllegalArgumentException("spec exceeds 65536 bytes");
            String source = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            Compiler.Generated generated = Compiler.compile(source, packageName);
            Path root = checkedRoot(directory);
            Path destination = root.resolve(packageName.replace('.', '/')).resolve(generated.className() + ".java");
            if (!destination.normalize().startsWith(root)) throw new IllegalArgumentException("output escapes directory");
            createDirectories(destination.getParent());
            Files.writeString(destination, generated.source(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            out.println(destination);
            return 0;
        } catch (IllegalArgumentException | IOException e) {
            err.println("jazzerlang: " + e.getMessage());
            return 2;
        }
    }

    private static Path checkedRoot(String value) {
        if (value.isBlank()) throw new IllegalArgumentException("output directory must not be blank");
        Path path = Path.of(value);
        for (Path part : path) if (part.toString().equals("..")) throw new IllegalArgumentException("output directory must not contain '..'");
        return path.toAbsolutePath().normalize();
    }

    private static void createDirectories(Path directory) throws IOException {
        Path current = directory.getRoot();
        for (Path part : directory) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("output path component is not a real directory: " + current);
            } else Files.createDirectory(current);
        }
    }
}
