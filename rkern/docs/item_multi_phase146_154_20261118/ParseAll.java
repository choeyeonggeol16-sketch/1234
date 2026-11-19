import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
public class ParseAll {
 public static void main(String[] args) throws Exception {
  var paths = new ArrayList<java.io.File>();
  try (var s=Files.walk(Path.of(args[0]))) {s.filter(p->p.toString().endsWith(".java")).forEach(p->paths.add(p.toFile()));}
  JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
  DiagnosticCollector<JavaFileObject> d = new DiagnosticCollector<>();
  try (var mgr=compiler.getStandardFileManager(d, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
    var files=mgr.getJavaFileObjectsFromFiles(paths);
    JavacTask task=(JavacTask)compiler.getTask(null,mgr,d,List.of("--release","21","-proc:none"),null,files);
    var nodes=task.parse();int n=0;for(var ignored:nodes)n++;
    long errors=d.getDiagnostics().stream().filter(x->x.getKind()==Diagnostic.Kind.ERROR).count();
    System.out.println("Java syntax files="+n+" errors="+errors);
    d.getDiagnostics().stream().filter(x->x.getKind()==Diagnostic.Kind.ERROR).limit(15).forEach(System.out::println);
    if(errors!=0||n!=paths.size())System.exit(1);
  }
 }
}
