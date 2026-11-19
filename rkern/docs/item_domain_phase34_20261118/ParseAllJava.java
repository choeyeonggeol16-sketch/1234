import java.io.File;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;

/** Parse only: does not claim full dependency type compilation. */
public final class ParseAllJava {
  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]);
    List<File> files;
    try (var stream=Files.walk(root)) {
      files=stream.filter(p->p.toString().endsWith(".java")).map(Path::toFile).toList();
    }
    DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    try (StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,null,null)) {
      Iterable<? extends JavaFileObject> units=manager.getJavaFileObjectsFromFiles(files);
      JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,
          List.of("-proc:none","-source","21"),null,units);
      long n=0;
      for(var ignored:task.parse())n++;
      long errors=diagnostics.getDiagnostics().stream()
          .filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
      System.out.println("Java source files="+files.size()+", parsed="+n+", syntaxErrors="+errors);
      diagnostics.getDiagnostics().stream()
          .filter(d->d.getKind()==Diagnostic.Kind.ERROR).limit(10)
          .forEach(d->System.err.println(d.toString()));
      if(errors>0||n!=files.size()) System.exit(1);
    }
  }
}
