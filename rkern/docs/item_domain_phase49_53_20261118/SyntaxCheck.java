import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
public final class SyntaxCheck {
  public static void main(String[] args)throws Exception{
    Path root=Path.of(args[0]); List<Path> paths;
    try(var walk=Files.walk(root)) {paths=walk.filter(p->p.toString().endsWith(".java")).sorted().toList();}
    var compiler=ToolProvider.getSystemJavaCompiler();
    int errors=0;
    try(var fm=compiler.getStandardFileManager(null,Locale.ROOT,null)){
      for(int p=0;p<paths.size();p+=64){
        var batch=paths.subList(p,Math.min(p+64,paths.size()));
        var collector=new DiagnosticCollector<JavaFileObject>();
        var objects=fm.getJavaFileObjectsFromPaths(batch);
        var task=(JavacTask)compiler.getTask(null,fm,collector,List.of("-proc:none","-encoding","UTF-8","-source","21"),null,objects);
        task.parse();
        for(var d:collector.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){
          if(errors++<20)System.err.println(d.getSource().getName()+":"+d.getLineNumber()+" "+d.getMessage(Locale.ROOT));
        }
      }
    }
    System.out.println("Java21 parse: files="+paths.size()+" syntaxErrors="+errors);
    if(errors!=0)System.exit(2);
  }
}
