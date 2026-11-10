import java.nio.charset.Charset;
import java.util.Arrays;
import javax.tools.*;
import com.sun.source.util.JavacTask;

// Syntax-only check; this does not claim a full server dependency/type build.
public class ParseJava {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, Charset.forName("MS949"))) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                Arrays.asList("-proc:none"), null, files.getJavaFileObjects(args));
            task.parse();
            for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) throw new AssertionError(d.toString());
            }
        }
        System.out.println("PASS: Java syntax parsed (3 changed files; dependency/type linking not performed)");
    }
}
