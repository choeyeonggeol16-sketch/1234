import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import javax.tools.*;import com.sun.source.tree.*;import com.sun.source.util.*;
public class ParseSources {
 public static void main(String[] args)throws Exception {
  var compiler=ToolProvider.getSystemJavaCompiler();var diagnostics=new DiagnosticCollector<JavaFileObject>();
  try(var paths=Files.walk(Path.of(args[0]));var fm=compiler.getStandardFileManager(diagnostics,null,StandardCharsets.UTF_8)) {
   var files=paths.filter(p->p.toString().endsWith(".java")).sorted().toList();
   var task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("-proc:none","-source","21"),null,fm.getJavaFileObjectsFromPaths(files));
   int count=0;for(var tree:task.parse())count++;
   for(var d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)System.err.println(d);
   if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR))throw new AssertionError("source syntax failure");
   if(count!=files.size())throw new AssertionError("source count");
   System.out.println("PASS UTF-8 Java 21 syntax parse of all "+count+" source files; no type linking performed");
  }
 }
}
