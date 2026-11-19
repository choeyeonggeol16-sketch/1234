import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import javax.lang.model.element.Modifier;
import com.sun.source.tree.*;
import com.sun.source.util.*;
/** AST-based field inventory; deliberately not a heap reachability proof. */
class AuditStaticItemFields {
    public static void main(String[] args) throws Exception {
        List<Path> paths;
        try(var walk=Files.walk(Path.of(args[0]))) {paths=walk.filter(p->p.toString().endsWith(".java")).sorted().toList();}
        var compiler=ToolProvider.getSystemJavaCompiler();
        try(var fm=compiler.getStandardFileManager(null,null,null)) {
            var task=(JavacTask)compiler.getTask(null,fm,null,List.of("-proc:none","--release","21"),null,fm.getJavaFileObjectsFromPaths(paths));
            final int[] counts=new int[3];
            for(var tree:task.parse()) new TreeScanner<Void,Void>() {
                @Override public Void visitClass(ClassTree clazz,Void unused) {
                    for(var member:clazz.getMembers()) if(member instanceof VariableTree field && field.getModifiers().getFlags().contains(Modifier.STATIC)) {
                        counts[0]++;
                        if(field.getType()==null)continue;
                        String t=field.getType().toString();
                        if(t.matches(".*(Map|List|Set|Queue|Collection|ThreadLocal|Future|Timer|Executor|L1PcInstance|L1ItemInstance|CIOClient|L1Character|L1Object).*")){
                            counts[1]++;
                            if(t.matches(".*(L1PcInstance|L1ItemInstance|CIOClient|L1Character|L1Object).*"))counts[2]++;
                            System.out.println(tree.getSourceFile().getName()+" "+clazz.getSimpleName()+" "+t+" "+field.getName());
                        }
                    }
                    return super.visitClass(clazz,unused);
                }
            }.scan(tree,null);
            System.out.println("FILES="+paths.size()+" STATIC_FIELDS="+counts[0]+" CANDIDATE_FIELDS="+counts[1]+" ACTOR_OR_ITEM_TYPED="+counts[2]);
        }
    }
}
