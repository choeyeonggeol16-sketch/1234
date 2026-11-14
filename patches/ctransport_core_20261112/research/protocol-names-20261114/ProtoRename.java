import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.lang.model.element.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;

/** Resolve symbols before moving types; no name-only replacement of game methods. */
public final class ProtoRename {
    record Edit(int start,int end,String text){}
    static final Map<String,String> classes=new LinkedHashMap<>();
    static final Map<String,Map<String,String>> fields=new HashMap<>();
    static final Set<String> keywords=Set.of("int","class","enum","interface","default","package","private","public","protected","static","final","long","short","byte","char","float","double","boolean","void","new","return","switch","case","this","super","extends","implements","throws","throw","try","catch","finally","for","while","do","if","else","break","continue","instanceof","abstract","native","synchronized","transient","volatile","assert","const","goto","true","false","null");
    static String norm(String s){return s.replaceAll("[^A-Za-z0-9]","").toLowerCase(Locale.ROOT);}
    static String renamed(String name){
        for(var e:classes.entrySet())if(name.equals(e.getKey())||name.startsWith(e.getKey()+"."))return e.getValue()+name.substring(e.getKey().length());
        return name;
    }
    static TypeElement top(TypeElement type){while(type.getEnclosingElement()instanceof TypeElement parent)type=parent;return type;}
    static String methodName(ExecutableElement method){
        if(method.getKind()!=ElementKind.METHOD||method.getModifiers().contains(Modifier.STATIC))return null;
        String owner=renamed(((TypeElement)method.getEnclosingElement()).getQualifiedName().toString());Map<String,String> schema=fields.get(owner);if(schema==null)return null;
        String old=method.getSimpleName().toString(),suffix=null,prefix=null;
        for(String op:List.of("get","set","has","add","clear")){
            if(old.startsWith(op+"_")){prefix=op;suffix=old.substring(op.length()+1);break;}
            if(old.length()>op.length()&&old.startsWith(op)&&Character.isUpperCase(old.charAt(op.length()))){prefix=op;suffix=old.substring(op.length());break;}
        }
        if(suffix==null)return null;String field=schema.get(norm(suffix));if(field==null)return null;
        if(keywords.contains(field))field+="_";
        String result=prefix.equals("get")?field:prefix+"_"+field;
        return result.equals(old)?null:result;
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),dir=args.length>2?Path.of(args[2]):root.resolve("research/protocol-names-20261114");
        for(String line:Files.readAllLines(dir.resolve("classes.tsv"))){var a=line.split("\t");classes.put(a[0],a[1]);}
        for(String line:Files.readAllLines(dir.resolve("fields.tsv"))){var a=line.split("\t");fields.computeIfAbsent(a[0],x->new HashMap<>()).put(norm(a[1]),a[1]);}
        if(Files.exists(dir.resolve("field-aliases.tsv")))for(String line:Files.readAllLines(dir.resolve("field-aliases.tsv"))){var a=line.split("\t");fields.computeIfAbsent(a[0],x->new HashMap<>()).put(norm(a[1]),a[2]);}
        List<Path> sources;try(var walk=Files.walk(root.resolve("src"))){sources=walk.filter(p->p.toString().endsWith(".java")).sorted().toList();}
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();var diagnostics=new DiagnosticCollector<JavaFileObject>();var fm=compiler.getStandardFileManager(diagnostics,null,java.nio.charset.StandardCharsets.UTF_8);
        var task=(JavacTask)compiler.getTask(new PrintWriter(Files.newBufferedWriter(dir.resolve("resolve.log"))),fm,diagnostics,List.of("-source","21","--enable-preview","-proc:none","-encoding","UTF-8","-classpath",args[1]),null,fm.getJavaFileObjectsFromPaths(sources));
        List<CompilationUnitTree> units=new ArrayList<>();task.parse().forEach(units::add);task.analyze();
        for(var error:diagnostics.getDiagnostics())if(error.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException(error.toString());
        Trees trees=Trees.instance(task);SourcePositions pos=trees.getSourcePositions();List<String> renamedMethods=new ArrayList<>();int changed=0;
        for(CompilationUnitTree unit:units){
            Path file=Path.of(unit.getSourceFile().toUri());String source=Files.readString(file),oldPackage=unit.getPackageName()==null?"":unit.getPackageName().toString();List<Edit> edits=new ArrayList<>();
            TypeElement primary=null;for(Tree decl:unit.getTypeDecls())if(decl instanceof ClassTree){primary=(TypeElement)trees.getElement(TreePath.getPath(unit,decl));break;}
            String oldPrimary=primary==null?"":primary.getQualifiedName().toString();boolean moving=classes.containsKey(oldPrimary);
            if(moving){Tree p=unit.getPackageName();edits.add(new Edit((int)pos.getStartPosition(unit,p),(int)pos.getEndPosition(unit,p),"LIN"));}
            final String primaryName=oldPrimary;
            new TreePathScanner<Void,Void>(){
                void edit(Tree tree,String value){long a=pos.getStartPosition(unit,tree),b=pos.getEndPosition(unit,tree);if(a>=0&&b>=a)edits.add(new Edit((int)a,(int)b,value));}
                void renameEnd(Tree tree,String old,String value){int b=(int)pos.getEndPosition(unit,tree);if(b>=old.length()&&source.substring(b-old.length(),b).equals(old))edits.add(new Edit(b-old.length(),b,value));else throw new IllegalStateException("symbol position "+file+":"+old);}
                @Override public Void visitClass(ClassTree node,Void v){
                    var symbol=trees.getElement(getCurrentPath());if(symbol instanceof TypeElement type&&classes.containsKey(type.getQualifiedName().toString())){
                        String old=node.getSimpleName().toString(),target=classes.get(type.getQualifiedName().toString());String simple=target.substring(target.lastIndexOf('.')+1);
                        int begin=(int)pos.getStartPosition(unit,node);Matcher m=Pattern.compile("\\b(class|enum|interface)\\s+("+Pattern.quote(old)+")\\b").matcher(source);if(!m.find(begin))throw new IllegalStateException("class declaration "+file);edits.add(new Edit(m.start(2),m.end(2),simple));
                    }return super.visitClass(node,v);
                }
                @Override public Void visitMethod(MethodTree node,Void v){
                    Element e=trees.getElement(getCurrentPath());if(e instanceof ExecutableElement method){
                        String old=node.getName().toString(),replacement=methodName(method);
                        if(method.getKind()==ElementKind.CONSTRUCTOR&&classes.containsKey(((TypeElement)method.getEnclosingElement()).getQualifiedName().toString())){
                            old=((TypeElement)method.getEnclosingElement()).getSimpleName().toString();String f=classes.get(((TypeElement)method.getEnclosingElement()).getQualifiedName().toString());replacement=f.substring(f.lastIndexOf('.')+1);
                        }
                        long end=node.getBody()==null?pos.getEndPosition(unit,node):pos.getStartPosition(unit,node.getBody());long begin=pos.getStartPosition(unit,node);
                        if(replacement!=null&&!replacement.equals(old)&&begin>=0&&end>begin){Matcher m=Pattern.compile("\\b"+Pattern.quote(old)+"\\s*\\(").matcher(source.substring((int)begin,(int)end));int found=-1;while(m.find())found=m.start();if(found<0)throw new IllegalStateException("method declaration "+file+":"+old);edits.add(new Edit((int)begin+found,(int)begin+found+old.length(),replacement));renamedMethods.add(method.getEnclosingElement()+"\t"+old+"\t"+replacement);}
                    }return super.visitMethod(node,v);
                }
                @Override public Void visitMethodInvocation(MethodInvocationTree node,Void v){var e=trees.getElement(getCurrentPath());if(e instanceof ExecutableElement method){String replacement=methodName(method);if(replacement!=null)renameEnd(node.getMethodSelect(),method.getSimpleName().toString(),replacement);}return super.visitMethodInvocation(node,v);}
                @Override public Void visitMemberReference(MemberReferenceTree node,Void v){var e=trees.getElement(getCurrentPath());if(e instanceof ExecutableElement method){String replacement=methodName(method);if(replacement!=null)renameEnd(node,node.getName().toString(),replacement);}return super.visitMemberReference(node,v);}
                @Override public Void visitIdentifier(IdentifierTree node,Void v){
                    var e=trees.getElement(getCurrentPath());if(e instanceof TypeElement type&&!node.getName().contentEquals("this")&&!node.getName().contentEquals("super")){
                        String name=type.getQualifiedName().toString(),target=renamed(name);
                        if(!target.equals(name))edit(node,target);
                        else if(moving&&!name.isEmpty()&&!top(type).getQualifiedName().contentEquals(primaryName)&&task.getElements().getPackageOf(type).getQualifiedName().contentEquals(oldPackage))edit(node,name);
                    }return super.visitIdentifier(node,v);
                }
                @Override public Void visitMemberSelect(MemberSelectTree node,Void v){
                    var e=trees.getElement(getCurrentPath());if(e instanceof TypeElement type){String old=type.getQualifiedName().toString(),target=renamed(old);var parent=trees.getElement(new TreePath(getCurrentPath(),node.getExpression()));if(!target.equals(old)&&(parent instanceof PackageElement||parent instanceof TypeElement))edit(node,target);}
                    return super.visitMemberSelect(node,v);
                }
            }.scan(unit,null);
            // Largest replacement wins if javac exposes the same qualified type
            // through overlapping parent/child nodes. Never apply stale offsets.
            edits.sort(Comparator.comparingInt(Edit::start).thenComparing(Comparator.comparingInt(Edit::end).reversed()));List<Edit> filtered=new ArrayList<>();int until=-1;
            for(Edit e:edits){if(e.start()<until)continue;filtered.add(e);until=e.end();}
            if(!filtered.isEmpty()){
                StringBuilder out=new StringBuilder(source);for(int i=filtered.size()-1;i>=0;i--){Edit e=filtered.get(i);out.replace(e.start(),e.end(),e.text());}Files.writeString(file,out.toString());changed++;
            }
        }
        Files.write(dir.resolve("methods.tsv"),renamedMethods);System.out.println("Updated "+changed+" source files;renamed "+renamedMethods.size()+" method/constructor declarations using resolved symbols");fm.close();
    }
}
