import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Parse only: record each declared type, excluding its member type bodies. */
public final class TypeBodies {
    public static void main(String[] args) throws Exception {
        List<Path> files;
        try (var walk = Files.walk(Path.of(args[0]))) { files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList(); }
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, fm, null, List.of("-source", "21", "--enable-preview", "-proc:none"), null, fm.getJavaFileObjectsFromPaths(files));
            var positions = Trees.instance(task).getSourcePositions(); List<String> output = new ArrayList<>();
            for (CompilationUnitTree unit : task.parse()) {
                String source = Files.readString(Path.of(unit.getSourceFile().toUri()));
                new TreeScanner<Void,String>() {
                    public Void visitClass(ClassTree node, String parent) {
                        if (node.getSimpleName().length() == 0) return null;
                        String name = parent + "." + node.getSimpleName();
                        int start = (int) positions.getStartPosition(unit, node), end = (int) positions.getEndPosition(unit, node);
                        StringBuilder body = new StringBuilder(source.substring(start, end));
                        var children = node.getMembers().stream().filter(t -> t instanceof ClassTree).toList();
                        for (int i = children.size() - 1; i >= 0; i--) {
                            Tree child = children.get(i);
                            body.delete((int) positions.getStartPosition(unit, child) - start, (int) positions.getEndPosition(unit, child) - start);
                        }
                        output.add(name + "\t" + Base64.getEncoder().encodeToString(body.toString().getBytes(StandardCharsets.UTF_8)));
                        for (Tree child : children) scan(child, name);
                        return null;
                    }
                }.scan(unit, unit.getPackageName() == null ? "" : unit.getPackageName().toString());
            }
            Files.write(Path.of(args[1]), output);
        }
    }
}
