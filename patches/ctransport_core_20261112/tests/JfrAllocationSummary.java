import java.nio.file.*;
import java.util.*;
import jdk.jfr.consumer.*;

/** Offline reader for recorded measurements;never runs the server. */
public final class JfrAllocationSummary {
    public static void main(String[] args)throws Exception {
        for(String arg:args){
            Map<String,Long> allocations=new HashMap<>(),sites=new HashMap<>(),execution=new HashMap<>();
            long gcCount=0,gcNanos=0,gcMax=0;
            try(RecordingFile input=new RecordingFile(Path.of(arg))){
                while(input.hasMoreEvents()){
                    RecordedEvent event=input.readEvent();String kind=event.getEventType().getName();RecordedStackTrace stack=event.getStackTrace();
                    if(kind.equals("jdk.ObjectAllocationSample")){
                        long bytes=event.getLong("weight");allocations.merge(event.getClass("objectClass").getName(),bytes,Long::sum);
                        if(stack!=null)for(RecordedFrame frame:stack.getFrames()){
                            String type=frame.getMethod().getType().getName();
                            if(type.startsWith("l1j.")){sites.merge(type+"."+frame.getMethod().getName()+":"+frame.getLineNumber(),bytes,Long::sum);break;}
                        }
                    }
                    if(kind.equals("jdk.ExecutionSample")||kind.equals("jdk.NativeMethodSample")){
                        if(stack!=null&&!stack.getFrames().isEmpty()){
                            RecordedFrame frame=stack.getFrames().get(0);execution.merge(frame.getMethod().getType().getName()+"."+frame.getMethod().getName(),1L,Long::sum);
                        }
                    }
                    if(kind.equals("jdk.GarbageCollection")){long n=event.getDuration().toNanos();gcCount++;gcNanos+=n;gcMax=Math.max(gcMax,n);}
                }
            }
            System.out.println(arg+" GC count="+gcCount+" total_ms="+gcNanos/1e6+" max_ms="+gcMax/1e6);
            for(Map<String,Long> map:List.of(allocations,sites,execution)){
                System.out.println("TOP");map.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(10).forEach(e->System.out.println(e.getValue()+" "+e.getKey()));
            }
        }
    }
}
