import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
public final class ParseJava21 {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("source root");
    List<Path> paths;
    try (Stream<Path> stream=Files.walk(Path.of(args[0]))) {
      paths=stream.filter(p->p.toString().endsWith(".java")).sorted().toList();
    }
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    if(compiler == null)throw new IllegalStateException("JDK required");
    int parsed=0,errors=0;
    for(int start=0;start<paths.size();start+=90){
      List<Path> chunk=paths.subList(start,Math.min(paths.size(),start+90));
      DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
      try(StandardJavaFileManager mgr=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)) {
        ArrayList<File> files=new ArrayList<>(chunk.size());
        for(Path p:chunk)files.add(p.toFile());
        Iterable<? extends JavaFileObject> units=mgr.getJavaFileObjectsFromFiles(files);
        JavacTask task=(JavacTask)compiler.getTask(null,mgr,diagnostics,List.of("--release","21","-proc:none"),null,units);
        for(var unit:task.parse())parsed++;
      }
      for (Diagnostic<? extends JavaFileObject> d:diagnostics.getDiagnostics()) {
        if(d.getKind()==Diagnostic.Kind.ERROR){
          errors++; System.err.println(d.getSource()+":"+d.getLineNumber()+":"+d.getColumnNumber()+" "+d.getMessage(Locale.ROOT));
        }
      }
    }
    System.out.println("JDK21_PARSE_TOTAL="+parsed+" ERRORS="+errors);
    if (errors>0 || parsed!=paths.size())System.exit(1);
  }
}
