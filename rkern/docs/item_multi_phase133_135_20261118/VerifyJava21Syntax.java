import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
import com.sun.source.tree.CompilationUnitTree;

/** Parse full production source with JDK21 grammar without requiring third-party dependencies. */
public final class VerifyJava21Syntax {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("source root required");
        final List<Path> paths;
        try (var walk = Files.walk(Path.of(args[0]))) {
            paths = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diags, null, StandardCharsets.UTF_8)) {
            var files = fm.getJavaFileObjectsFromPaths(paths);
            JavacTask task = (JavacTask) compiler.getTask(null, fm, diags,
                    List.of("-proc:none", "--release", "21"), null, files);
            int count = 0;
            for (CompilationUnitTree tree : task.parse()) count++;
            long failures = diags.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count();
            System.out.println("JAVA21_PARSED=" + count + " ERRORS=" + failures);
            for (var d : diags.getDiagnostics()) if (d.getKind() == Diagnostic.Kind.ERROR)
                System.out.println(d.getSource() + ":" + d.getLineNumber() + ":" + d.getMessage(Locale.ROOT));
            if (failures != 0 || count != paths.size()) System.exit(2);
        }
    }
}
