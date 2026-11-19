import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.tree.*;
import com.sun.source.util.*;

/** AST inventory of send-in-loop sites; candidate inventory, not a correctness proof. */
public class LoopInventory88 {
 static class Visitor extends TreeScanner<Void,Void> {
   CompilationUnitTree cu; Trees trees; int loops=0; List<String> lines;
   Visitor(CompilationUnitTree cu,Trees trees,List<String> lines){this.cu=cu;this.trees=trees;this.lines=lines;}
   String compact(String s){return s==null?"":s.replaceAll("[\\r\\n\\t]"," ").replaceAll(" +"," ").trim();}
   void gather(Tree t,String label) {
     if(t==null)return;
     final LinkedHashSet<String> found=new LinkedHashSet<>();
     new TreeScanner<Void,Void>() {
       @Override public Void visitMethodInvocation(MethodInvocationTree node,Void v) {
         String name=node.getMethodSelect().toString();
         String lower=name.toLowerCase(Locale.ROOT);
         if(lower.contains("send")||lower.contains("broadcast")||lower.contains("writepacket"))found.add(name);
         return super.visitMethodInvocation(node,v);
       }
     }.scan(t,null);
     if(found.isEmpty())return;
     var pos=trees.getSourcePositions();
     long start=pos.getStartPosition(cu,t);
     int line=start>=0?(int)cu.getLineMap().getLineNumber(start):-1;
     final String path=cu.getSourceFile().getName();
     lines.add(path+"\t"+line+"\t"+compact(label)+"\t"+String.join(",",found));
     loops++;
   }
   @Override public Void visitForLoop(ForLoopTree t,Void v){gather(t.getStatement(),t.getCondition()==null?"for":t.getCondition().toString());return super.visitForLoop(t,v);}
   @Override public Void visitEnhancedForLoop(EnhancedForLoopTree t,Void v){gather(t.getStatement(),t.getVariable().getName()+" : "+t.getExpression());return super.visitEnhancedForLoop(t,v);}
   @Override public Void visitWhileLoop(WhileLoopTree t,Void v){gather(t.getStatement(),t.getCondition().toString());return super.visitWhileLoop(t,v);}
   @Override public Void visitDoWhileLoop(DoWhileLoopTree t,Void v){gather(t.getStatement(),t.getCondition().toString());return super.visitDoWhileLoop(t,v);}
 }
 public static void main(String[] args)throws Exception{
   Path base=Paths.get(args[0]).toAbsolutePath();
   List<Path> paths;
   try(var stream=Files.walk(base)){ paths=stream.filter(p->p.toString().endsWith(".java")).sorted().toList(); }
   JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
   DiagnosticCollector<JavaFileObject> diag=new DiagnosticCollector<>();
   List<String> rows=new ArrayList<>();int parsed=0;
   for(int n=0;n<paths.size();n+=90){
     try(StandardJavaFileManager fm=compiler.getStandardFileManager(diag,null,StandardCharsets.UTF_8)){
       var sources=fm.getJavaFileObjectsFromPaths(paths.subList(n,Math.min(n+90,paths.size())));
       JavacTask task=(JavacTask)compiler.getTask(null,fm,diag,List.of("-proc:none","--enable-preview","--release","21"),null,sources);
       Trees trees=Trees.instance(task);
       for(CompilationUnitTree u:task.parse()){new Visitor(u,trees,rows).scan(u,null);parsed++;}
     }
   }
   List<String> relative=new ArrayList<>();
   for(String line:rows){String[] a=line.split("\\t",4);relative.add(a[0].replace(base.toString()+"/", "")+"\t"+a[1]+"\t"+a[2]+"\t"+a[3]);}
   Files.write(Paths.get(args[1]),relative,StandardCharsets.UTF_8);
   long errs=diag.getDiagnostics().stream().filter(x->x.getKind()==Diagnostic.Kind.ERROR).count();
   System.out.println("Java parsed="+parsed+" send-in-loops="+relative.size()+" errors="+errs);
   if(errs!=0){diag.getDiagnostics().stream().filter(x->x.getKind()==Diagnostic.Kind.ERROR).limit(20).forEach(System.err::println);System.exit(1);}
 }
}
