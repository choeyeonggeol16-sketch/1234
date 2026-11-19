import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;

public class ParseOnly117 {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(args[0]);
  List<Path> paths;
  try(var walk=Files.walk(root)) {
    paths=walk.filter(p->p.toString().endsWith(".java")).sorted().toList();
  }
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  int errors=0, total=0;
  for(int i=0;i<paths.size();i+=96) {
    var ds=new DiagnosticCollector<JavaFileObject>();
    try(StandardJavaFileManager fm=compiler.getStandardFileManager(ds,null,StandardCharsets.UTF_8)) {
      var end=Math.min(i+96,paths.size());
      var units=fm.getJavaFileObjectsFromPaths(paths.subList(i,end));
      var task=(JavacTask)compiler.getTask(null,fm,ds,Arrays.asList("-proc:none","--release","21","-encoding","UTF-8"),null,units);
      for(var tree:task.parse()) total++;
      for(var d:ds.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){errors++;System.err.println(d);}
    }
  }
  System.out.printf("Java21 parsed=%d source_files=%d syntax_errors=%d%n",total,paths.size(),errors);
  if(errors!=0 || total!=paths.size()) System.exit(1);
 }
}
