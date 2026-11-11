import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.tools.*;
import com.sun.source.tree.*;
import com.sun.source.util.*;
public class MigrateSchedules {
 public static void main(String[] args) throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  Set<String> names=Set.of("schedule","scheduleAtFixedRate","scheduleAtFixedRateLong","scheduleWithFixedDelay","schedulePriorityTaskAtFixedRate");
  int[] total={0,0};
  try(var paths=Files.walk(Path.of(args[0])); var fm=compiler.getStandardFileManager(null,null,StandardCharsets.UTF_8)) {
   for(Path path:paths.filter(p->p.toString().endsWith(".java")).toList()) {
    String source=Files.readString(path);
    if(!source.contains("ThreadPool.getInstance()"))continue;
    var diagnostics=new DiagnosticCollector<JavaFileObject>();
    var task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("-proc:none"),null,fm.getJavaFileObjects(path));
    var trees=Trees.instance(task);var edits=new ArrayList<int[]>();
    for(var cu:task.parse())new TreeScanner<Void,Void>() {
     @Override public Void visitExpressionStatement(ExpressionStatementTree tree,Void unused) {
      if(tree.getExpression() instanceof MethodInvocationTree call && call.getMethodSelect() instanceof MemberSelectTree select) {
       String name=select.getIdentifier().toString(),receiver=select.getExpression().toString();
       if(names.contains(name)&&(receiver.equals("ThreadPool.getInstance()")||receiver.equals("l1j.server.server.ThreadPool.getInstance()"))) {
        int end=(int)trees.getSourcePositions().getEndPosition(cu,select);
        if(!source.substring(end-name.length(),end).equals(name))throw new AssertionError("source positions");
        edits.add(new int[]{end,end});
       }
      }
      return super.visitExpressionStatement(tree,unused);
     }
    }.scan(cu,null);
    if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR))throw new AssertionError(diagnostics.getDiagnostics().toString());
    if(!edits.isEmpty()) {
     edits.sort((a,b)->Integer.compare(b[0],a[0]));StringBuilder changed=new StringBuilder(source);
     for(var e:edits)changed.insert(e[0],"Required");
     Files.writeString(path,changed);total[0]+=edits.size();total[1]++;
     System.out.println(path+" : "+edits.size());
    }
   }
  }
  System.out.println("Migrated "+total[0]+" unchecked schedules in "+total[1]+" files; assigned/returned futures preserved.");
 }
}
