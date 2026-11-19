import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
public class ParseAllJava {
  public static void main(String[] args) throws Exception {
    var source = Path.of(args[0]);
    List<File> files=new ArrayList<>();
    try(var paths=Files.walk(source)) { paths.filter(p->p.toString().endsWith(".java")).forEach(p->files.add(p.toFile())); }
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diags=new DiagnosticCollector<>();
    try(var manager=compiler.getStandardFileManager(diags, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
      var units=manager.getJavaFileObjectsFromFiles(files);
      var task=(JavacTask)compiler.getTask(null,manager,diags,List.of("-proc:none","-encoding","UTF-8"),null,units);
      long count=0;
      for(var unit:task.parse()) count++;
      long errors=diags.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
      for(var d:diags.getDiagnostics()) if(d.getKind()==Diagnostic.Kind.ERROR) System.out.println(d.toString());
      System.out.println("PARSED="+count+" ERRORS="+errors);
      if (errors!=0 || count!=files.size()) System.exit(1);
    }
  }
}
