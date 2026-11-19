import java.nio.file.*;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;
public class ParseJavaAll {
 public static void main(String[] args) throws Exception {
  Context c=new Context();JavacFileManager.preRegister(c);Options opts=Options.instance(c);opts.put("-source","21");
  var factory=ParserFactory.instance(c);var log=Log.instance(c);
  int n=0,errs=0;
  try(var paths=Files.walk(Path.of(args[0]))) {
   for(var it=paths.filter(p->p.toString().endsWith(".java")).iterator();it.hasNext();){
    var p=it.next();n++;int start=log.nerrors;
    var parser=factory.newParser(Files.readString(p),false,false,false);
    parser.parseCompilationUnit();
    if(log.nerrors!=start){System.out.println("PARSE ERROR "+p+" "+(log.nerrors-start));errs+=log.nerrors-start;}
   }
  }
  System.out.println("Parsed "+n+" Java, errors="+errs); if(errs!=0) System.exit(1);
 }
}
