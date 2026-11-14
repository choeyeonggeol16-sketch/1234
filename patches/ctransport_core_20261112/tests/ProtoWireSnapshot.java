import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import sun.misc.Unsafe;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/** Compare serializer bytes without starting a world or DB; never call handlers. */
public final class ProtoWireSnapshot {
    static final Unsafe U;
    static{try{Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);U=(Unsafe)f.get(null);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static boolean protocol(Class<?> c){return c.getName().startsWith("LIN.")||c.getName().startsWith("l1j.server.server.packets.Protocol.Packet.");}
    static Object sample(Class<?> type,int depth,int mode)throws Exception {
        if(type==boolean.class||type==Boolean.class)return mode!=0;
        if(type==byte.class||type==Byte.class)return (byte)(mode==2?-1:7);
        if(type==short.class||type==Short.class)return (short)(mode==2?-1:129);
        if(type==int.class||type==Integer.class)return mode==2?-1:129;
        if(type==long.class||type==Long.class)return mode==2?-1L:16385L;
        if(type==double.class||type==Double.class)return mode==2?-1.5:1.25;
        if(type==float.class||type==Float.class)return 1.25f;
        if(type==String.class)return "프로토";
        if(type==byte[].class)return new byte[]{0,1,127,(byte)128,(byte)255};
        if(type==int[].class)return new int[]{1,127,16384};
        if(type==long[].class)return new long[]{1,128,16385};
        if(type.isEnum())return type.getEnumConstants().length==0?null:type.getEnumConstants()[0];
        if(List.class.isAssignableFrom(type))return new ArrayList<>();
        if(type.isArray())return Array.newInstance(type.getComponentType(),0);
        if(protocol(type)&&depth<4&&!type.isInterface()&&!Modifier.isAbstract(type.getModifiers()))return object(type,depth+1,mode);
        return null;
    }
    static Object object(Class<?> type,int depth,int mode)throws Exception {
        Object value=U.allocateInstance(type);if(mode==0)return value;
        for(Class<?> c=type;protocol(c);c=c.getSuperclass())for(Field field:c.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers())||field.isSynthetic())continue;field.setAccessible(true);Object data=sample(field.getType(),depth,mode);
            if(List.class.isAssignableFrom(field.getType())&&field.getGenericType() instanceof ParameterizedType p){
                var list=new ArrayList<>();Type item=p.getActualTypeArguments()[0];if(item instanceof Class<?> t){Object child=sample(t,depth,mode);if(child!=null)list.add(child);}data=list;
            }
            if((field.getName().toLowerCase().contains("bit")||field.getName().toLowerCase().contains("mask"))&&field.getType()==long.class)data=-1L;
            if((field.getName().toLowerCase().contains("bit")||field.getName().toLowerCase().contains("mask"))&&field.getType()==int.class)data=-1;
            if(data!=null)field.set(value,data);
        }return value;
    }
    static void collect(Class<?> c,List<Class<?>> all){all.add(c);for(Class<?> child:c.getDeclaredClasses())if(!child.isSynthetic())collect(child,all);}
    public static void main(String[] args)throws Exception {
        boolean after=args[0].equals("after");Map<String,String> expected=new LinkedHashMap<>();Path golden=Path.of(args[2]);
        if(after)for(String line:Files.readAllLines(golden)){String[] p=line.split("\t",2);expected.put(p[0],p[1]);}
        List<String[]> aliases=new ArrayList<>();if(after)for(String alias:Files.readAllLines(Path.of(args[1]).getParent().resolve("nested/classes.tsv")))aliases.add(alias.split("\t"));
        List<String> output=new ArrayList<>();int covered=0;
        for(String line:Files.readAllLines(Path.of(args[1]))){String[] pair=line.split("\t");String name=after?pair[1]:pair[0];List<Class<?>> types=new ArrayList<>();
            // LIN is the descriptor package; following dots denote nested messages.
            if(after)name="LIN."+name.substring(4).replace('.','$');
            collect(Class.forName(name),types);
            for(Class<?> c:types)for(int mode=0;mode<3;mode++){
                String logicalName=c.getName();
                if(after)for(String[] parts:aliases){
                    String from="LIN."+parts[1].substring(4).replace('.','$'),to="LIN."+parts[0].substring(4).replace('.','$');
                    if(logicalName.equals(from)||logicalName.startsWith(from+"$")){logicalName=to+logicalName.substring(from.length());break;}
                }
                String suffix=logicalName.substring(name.length()),key=pair[0]+suffix+":"+mode;
                if(after&&!expected.containsKey(key))continue;
                try{
                    byte[] bytes;Object o=object(c,0,mode);
                    if(o instanceof ProtoPacket packet)bytes=packet.encode();
                    else{Method method;try{method=c.getMethod("saveBody",ProtoWriter.class);}catch(NoSuchMethodException missing){method=c.getMethod("writeTo",ProtoWriter.class);}ProtoWriter writer=new ProtoWriter();method.invoke(o,writer);bytes=writer.bodyBytes();}
                    String encoded=HexFormat.of().formatHex(bytes);
                    if(after&&!encoded.equals(expected.get(key)))throw new AssertionError("wire changed "+key+"\nold="+expected.get(key)+"\nnew="+encoded);
                    output.add(key+"\t"+encoded);covered++;
                }catch(AssertionError error){throw error;}
                catch(Throwable failure){if(after)throw new AssertionError("previously encodable case failed "+key,failure);}
            }
        }
        if(after&&covered!=expected.size())throw new AssertionError("missing wire cases "+covered+"/"+expected.size());
        if(!after)Files.write(golden,output);System.out.println("PASS "+covered+" serializer states "+(after?"byte-identical":"captured before renaming"));
    }
}
