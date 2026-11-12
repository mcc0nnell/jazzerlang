# JazzerLang 0.1 specification

One UTF-8 file contains exactly one declaration. ASCII space, tab, CR, and LF separate tokens; punctuation can touch neighboring tokens. There are no comments, strings as literals, escapes, Unicode identifiers, or embedded code. Keywords are case-sensitive.

```ebnf
spec       = "fuzz", identifier, "{", target, input, { input }, property, "}", EOF ;
target     = "target", qualified, ";" ;
qualified  = identifier, ".", identifier, ".", identifier, { ".", identifier } ;
input      = "input", identifier, ":", type, ";" ;
type       = ( "bytes" | "string" ), "(", "max", "=", decimal, ")"
           | "int" | "long" | "boolean" ;
property   = "property", "no_throw", ";" ;
identifier = ( ASCII_LETTER | "_" ), { ASCII_LETTER | DIGIT | "_" } ;
decimal    = DIGIT, { DIGIT } ;
```

Identifiers are 1..100 characters and exclude Java 21 keywords/literals, the single underscore, and `var`, `yield`, `record`, `sealed`, `permits`. Decimal bounds are 1..1048576, with no signs or underscores; leading zeroes are accepted as decimal if representable by Java's integer parser. There must be 1..64 inputs, with unique names. Every token must be consumed in the specified order. Bounds, unsupported properties/types, duplicate names, lexical errors, and trailing content are compilation errors. Parser errors carry one-based line:column positions; LF advances the line, and tabs/CR each count as one column.

Targets name a package-qualified class and method; there must be at least three segments. Use public static methods of accessible classes. No instance creation or reflection is generated. Target existence, accessibility, and argument compatibility are checked by downstream Java compilation, not the DSL parser. Do not infer target validity merely from successful DSL compilation. The output package must not be a dotted prefix of the target name (for example, `java` and `java.util` are rejected for `java.util.Arrays.hashCode`). This conservative restriction accounts for nested class/package ambiguity and keeps the generated class outside the target package, so javac cannot grant access to package-private or protected methods. The generated class extends no target class.

The CLI accepts at most 65536 input bytes with strict UTF-8 decoding; the compiler API accepts at most 65536 Java characters. The package must consist of dot-separated validated identifier segments and be at most 255 characters (the package segments have no separate 100-character bound). Package options are Java package names, not paths. Platform file name/path limits may impose stricter limits and cause I/O errors.

| DSL type | Java type | Provider operation |
| --- | --- | --- |
| `bytes(max=N)` | `byte[]` | `consumeBytes(N)` |
| `string(max=N)` | `java.lang.String` | `consumeString(N)` |
| `int` | `int` | `consumeInt()` |
| `long` | `long` | `consumeLong()` |
| `boolean` | `boolean` | `consumeBoolean()` |

The provider is consumed in declaration order. Arrays and strings are non-null and may be empty. String bounds use Jazzer's maximum Java string length semantics, not UTF-8 byte length. Primitive values use the full provider-supported domain. Exhausted input follows Jazzer provider behavior; maxima do not require allocation of exactly N elements.

The generated public final class is `<name>FuzzTest`; it has a public `fuzz(FuzzedDataProvider)` method annotated with Jazzer's `@FuzzTest(maxDuration = "5s")` and declaring `throws Throwable`. It invokes the target exactly once per execution, discards any result, and catches no exceptions. Jazzer/JUnit determine findings and test outcomes.

The CLI creates directories under the specified output root and writes a new file. Blank output roots, `..` components, existing symlink components, non-directory components, and existing destination files are rejected. Relative and absolute output roots are accepted. Input files must end in `.jzl`. The caller controls the output root and must keep its directory tree trusted and stable; these checks do not provide an OS sandbox or protection against concurrent directory replacement. Invalid source and package validation occur before directory creation. An I/O failure may leave newly created directories or an incomplete new file; existing files are not overwritten.
