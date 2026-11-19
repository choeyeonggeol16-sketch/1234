import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;

/** Java 21 syntax-only parse of every production source, not full type compilation. */
public final class ParseAll100 {
    public static void main(String[] args) throws Exception {
        final Path sourceRoot = Path.of(args.length == 0 ? "src" : args[0]);
        final List<Path> sources;
        try (var walk = Files.walk(sourceRoot)) {
            sources = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK compiler required");
        int parsed = 0, errors = 0;
        for (int first = 0; first < sources.size(); first += 96) {
            final var batch = sources.subList(first, Math.min(sources.size(), first + 96));
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
                JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics,
                        List.of("--enable-preview", "--release", "21", "-encoding", "UTF-8"), null,
                        fm.getJavaFileObjectsFromPaths(batch));
                for (var unit : task.parse()) parsed++;
            }
            for (var d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    errors++;
                    if (errors <= 30) System.err.println(d);
                }
            }
        }
        System.out.printf("Production Java 21 syntax parsed=%d, errors=%d%n", parsed, errors);
        if (parsed != sources.size() || errors != 0)
            throw new IllegalStateException("syntax parse incomplete");
    }
}
