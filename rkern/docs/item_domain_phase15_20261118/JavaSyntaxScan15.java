import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
import java.io.*;
public class JavaSyntaxScan15 {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(args[0]);
  ArrayList<File> javaFiles=new ArrayList<>();
  try(var stream=Files.walk(root)) { stream.filter(p->p.toString().endsWith(".java")).forEach(p->javaFiles.add(p.toFile())); }
  DiagnosticCollector<JavaFileObject> errors=new DiagnosticCollector<>();
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  try(StandardJavaFileManager fm=compiler.getStandardFileManager(errors,Locale.ROOT,null)) {
   var units=fm.getJavaFileObjectsFromFiles(javaFiles);
   JavacTask task=(JavacTask)compiler.getTask(null,fm,errors,List.of("--release","21","-proc:none"),null,units);
   int parsed=0;
   for(var unit:task.parse()) { parsed++; }
   int count=0;
   for(var e:errors.getDiagnostics()) if(e.getKind()==Diagnostic.Kind.ERROR) {System.out.println(e);count++;}
   System.out.println("Java 21 parsed="+parsed+" syntax_errors="+count);
   if(count>0)System.exit(2);
  }
 }
}
