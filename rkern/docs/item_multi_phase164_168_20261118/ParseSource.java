import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.net.URI;
import javax.tools.*;
import com.sun.source.util.JavacTask;
public final class ParseSource {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(args[0]);
  var src=new ArrayList<Path>();
  try(var paths=Files.walk(root)){paths.filter(p->p.toString().endsWith(".java")).forEach(src::add);}
  JavaCompiler jc=ToolProvider.getSystemJavaCompiler();
  DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
  try(StandardJavaFileManager fm=jc.getStandardFileManager(diagnostics,Locale.ROOT,null)) {
   var all=fm.getJavaFileObjectsFromPaths(src);
   var options=List.of("--release","21","-proc:none","-encoding","UTF-8");
   JavacTask task=(JavacTask)jc.getTask(new PrintWriter(System.out),fm,diagnostics,options,null,all);
   int parsed=0;
   for(var unit:task.parse())parsed++;
   long errors=diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
   System.out.println("JAVA21_PARSE_FILES="+parsed+" ERRORS="+errors);
   if(errors!=0){for(var d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)System.err.println(d);System.exit(1);}
  }
 }
}
