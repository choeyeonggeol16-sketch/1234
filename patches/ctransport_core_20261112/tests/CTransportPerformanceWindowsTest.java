package l1j.server.server.model.network.core.connection;
import java.nio.file.*;
import java.util.*;
import static l1j.server.server.model.network.core.stability.StressMetrics.*;

/** Same production send FIFO/codec/byte verification and limits for both versions. */
public final class CTransportPerformanceWindowsTest {
    public static void main(String[] args)throws Exception {
        Map<String,Object> report=new LinkedHashMap<>();report.put("environment",environment());report.put("label",args[0]);List<Object> cases=new ArrayList<>();report.put("cases",cases);
        try(CSharedBroadcastWindowsTest.World world=new CSharedBroadcastWindowsTest.World()){
            CUserSocketStabilityWindowsTest.send(world,1024,15,"warmup");
            for(int n=0;n<3;n++)cases.add(CUserSocketStabilityWindowsTest.send(world,65536,15,"send-"+n));
            report.put("status","passed");
        }finally{write(Path.of("stability-wireperf-"+args[0]+".json"),report);}
    }
}
