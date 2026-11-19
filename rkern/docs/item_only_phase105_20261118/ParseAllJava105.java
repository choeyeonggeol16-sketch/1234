import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.*;
/** Java 21 syntax-only parser; no classpath type attribution. */
public class ParseAllJava105 {
  public static void main(String[] args) throws Exception {
    Path root = Path.of(args.length > 0 ? args[0] : ".");
    List<Path> paths;
    try (var walk = Files.walk(root.resolve("src"))) {
      paths = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
    }
    var compiler=ToolProvider.getSystemJavaCompiler();
    var diagnostics=new DiagnosticCollector<JavaFileObject>();
    try (var fm=compiler.getStandardFileManager(diagnostics,null,java.nio.charset.StandardCharsets.UTF_8)) {
      var units=fm.getJavaFileObjectsFromPaths(paths);
      var task=(JavacTask)compiler.getTask(null,fm,diagnostics,
          List.of("--release","21","-proc:none"),null,units);
      int parsed=0;
      for(var tree:task.parse()) parsed++;
      var errs=diagnostics.getDiagnostics().stream()
          .filter(d->d.getKind()==Diagnostic.Kind.ERROR).toList();
      System.out.println("Java21 parsed="+parsed+" source_files="+paths.size()+" syntax_errors="+errs.size());
      for(var e:errs.stream().limit(16).toList()) System.out.println(e);
      if(!errs.isEmpty()||parsed!=paths.size())System.exit(1);
    }
  }
}
