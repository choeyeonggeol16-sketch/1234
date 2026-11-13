package l1j.server.server.model.network.core;
import java.nio.file.Path;
/** Mandatory native transport. There is no alternate backend or selector. */
final class CIOConfig {
    static final Path DLL = Path.of("CTransport.dll");
    static final int ABI = 0x00090000;
    static final String DLL_SHA256 = "c7fd6352053c99370ef5db59447c7a4b7f3a606cea2c9e7864ad0634d71cebe7";
    // MAC enforcement is off until a verified server-side provider binds identity.
    static final int MAC_LIMIT = bounded("ctransport.macLimit",0,0,100000);
    static final int PERF_INTERVAL_MS = bounded("ctransport.perf.intervalMs",1000,100,60000);
    private static int bounded(String key,int fallback,int min,int max){
        int value=Integer.parseInt(System.getProperty(key,Integer.toString(fallback)));
        if(value<min||value>max)throw new IllegalArgumentException(key+" outside "+min+".."+max);
        return value;
    }
    private CIOConfig() {}
}
