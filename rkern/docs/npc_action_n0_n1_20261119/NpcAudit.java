import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.tree.*;
import com.sun.source.util.*;
public class NpcAudit {
 public static void main(String[] arg) throws Exception {
  var root=Path.of(arg[0]); var out=Path.of(arg[1]); Files.createDirectories(out);
  var compiler=ToolProvider.getSystemJavaCompiler(); var dc=new DiagnosticCollector<JavaFileObject>();
  try(var fm=compiler.getStandardFileManager(dc,null,java.nio.charset.StandardCharsets.UTF_8)) {
   List<Path> j; try(var s=Files.walk(root)){j=s.filter(x->x.toString().endsWith(".java")).sorted().toList();}
   var t=(JavacTask)compiler.getTask(null,fm,dc,List.of("-proc:none","--release","21"),null,fm.getJavaFileObjectsFromPaths(j));
   var trees=Trees.instance(t); var pos=trees.getSourcePositions(); int n=0;
   try(var w=Files.newBufferedWriter(out.resolve("branch_inventory.tsv"));var m=Files.newBufferedWriter(out.resolve("method_inventory.tsv"));var c=Files.newBufferedWriter(out.resolve("call_inventory.tsv"))) {
    w.write("file\tmethod\tline\tend_line\tbranch_kind\tcondition\tenclosing_branch\n");m.write("file\tmethod\tline\tend_line\tsignature\n");c.write("file\tmethod\tline\tcall\tclassification\tenclosing_branch\n");
    for(var unit:t.parse()) {n++; var file=Path.of(unit.getSourceFile().toUri()); String name=file.getFileName().toString();
     if(!Set.of("CS_NpcAction.java","CS_NpcAcion2.java","CS_NpcTalk.java","ActionControlHandler.java","NpcActionTable.java","L1NpcActionFactory.java","L1NpcMakeItemAction.java","L1NpcListedAction.java","L1NpcTeleportAction.java","ActionListenerLoader.java").contains(name)) continue;
     String source=Files.readString(file); String rel=root.relativize(file).toString();
     new TreePathScanner<Void,Void>() {
      String method="<field/init>"; Deque<String> context=new ArrayDeque<>();
      long line(Tree x, boolean end){long p=end?pos.getEndPosition(unit,x)-1:pos.getStartPosition(unit,x);return p<0?-1:unit.getLineMap().getLineNumber(p);}
      String text(Tree x){if(x==null)return ""; long a=pos.getStartPosition(unit,x),b=pos.getEndPosition(unit,x); if(a>=0&&b>=a&&b<=source.length())return source.substring((int)a,(int)b).replaceAll("[\\t\\r\\n]+"," ").trim();return x.toString().replaceAll("[\\t\\r\\n]+"," ");}
      void row(BufferedWriter dest,String... cols){try{dest.write(String.join("\t",cols));dest.newLine();}catch(IOException e){throw new UncheckedIOException(e);}}
      String ctx(){return String.join(" / ", context);}
      @Override public Void visitMethod(MethodTree x,Void v){String old=method;method=x.getName().toString();String sig=text(x); int k=sig.indexOf('{'); if(k>=0)sig=sig.substring(0,k).trim();row(m,rel,method,""+line(x,false),""+line(x,true),sig);super.visitMethod(x,v);method=old;return null;}
      @Override public Void visitIf(IfTree x,Void v){String condition=text(x.getCondition());row(w,rel,method,""+line(x,false),""+line(x,true),"if",condition,ctx());scan(x.getCondition(),v);context.addLast("if@"+line(x,false));scan(x.getThenStatement(),v);context.removeLast();if(x.getElseStatement()!=null){context.addLast("else@"+line(x,false));scan(x.getElseStatement(),v);context.removeLast();}return null;}
      @Override public Void visitSwitch(SwitchTree x,Void v){row(w,rel,method,""+line(x,false),""+line(x,true),"switch",text(x.getExpression()),ctx());context.addLast("switch@"+line(x,false));super.visitSwitch(x,v);context.removeLast();return null;}
      @Override public Void visitCase(CaseTree x,Void v){row(w,rel,method,""+line(x,false),""+line(x,true),"case",x.getExpressions().isEmpty()?"default":x.getExpressions().toString().replaceAll("[\\t\\r\\n]+"," "),ctx());context.addLast("case@"+line(x,false));super.visitCase(x,v);context.removeLast();return null;}
      @Override public Void visitMethodInvocation(MethodInvocationTree x,Void v){String sel=text(x.getMethodSelect());String kind=sel.matches(".*(consume|storeItem|removeItem|deleteItem|grant|debit|commit|set_step|stageQuest|Exchange|Receipt|\\.sql|Telepor|telepor).*" )?"mutation_candidate":sel.matches(".*(send|broadcast|onTalk|onFinalAction|execute|findListener|NpcAction).*" )?"response_or_dispatch":"other";row(c,rel,method,""+line(x,false),text(x),kind,ctx());return super.visitMethodInvocation(x,v);}
      @Override public Void visitReturn(ReturnTree x,Void v){row(w,rel,method,""+line(x,false),""+line(x,true),"return",text(x.getExpression()),ctx());return super.visitReturn(x,v);}
      @Override public Void visitCatch(CatchTree x,Void v){row(w,rel,method,""+line(x,false),""+line(x,true),"catch",text(x.getParameter()),ctx());return super.visitCatch(x,v);}
     }.scan(unit,null);
    }
   }
   long errors=dc.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();for(var d:dc.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)System.err.println(d);System.out.println("Parsed="+n+" syntax errors="+errors);if(errors!=0)System.exit(1);
  }
 }
}
