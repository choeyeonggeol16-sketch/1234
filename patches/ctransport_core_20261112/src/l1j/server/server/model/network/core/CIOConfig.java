package l1j.server.server.model.network.core;
import java.nio.file.Path;
/** Mandatory native transport. There is no alternate backend or selector. */
final class CIOConfig {
    static final Path DLL = Path.of("CTransport.dll");
    static final int ABI = 0x000a0000;
    static final String DLL_SHA256 = "62604a5b1b111526da30b0d06f224eb6d5f3e4aafca0292571aae7932b3f272e";
    private static final java.util.Properties SETTINGS = loadSettings();
    static final boolean DIAGNOSTICS_ENABLED = flag("ctransport.diagnostics.enabled", true);
    static final int DIAGNOSTICS_MAX_CONNECTIONS = bounded("ctransport.diagnostics.maxConnections",10000,1,100000);
    static final int LOGIN_TIMEOUT_MS = bounded("ctransport.loginTimeoutMs",60000,0,600000);
    private static java.util.Properties loadSettings() {
        java.util.Properties result = new java.util.Properties();
        Path file = Path.of(System.getProperty("ctransport.config", "config/ctransport.properties"));
        try {
            if (java.nio.file.Files.exists(file)) {
                String text = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
                if (text.startsWith("\ufeff")) text = text.substring(1);
                result.load(new java.io.StringReader(text));
            } else if (System.getProperty("ctransport.config") != null) throw new IllegalArgumentException("CTransport config does not exist: " + file);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read CTransport config: " + file, e); }
        return result;
    }
    private static String setting(String key, String fallback) { return System.getProperty(key, SETTINGS.getProperty(key, fallback)).trim(); }
    private static boolean flag(String key, boolean fallback) {
        String value = setting(key, Boolean.toString(fallback));
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException(key + " must be true/false");
        return Boolean.parseBoolean(value);
    }
    // MAC enforcement is off until a verified server-side provider binds identity.
    static final int MAC_LIMIT = bounded("ctransport.macLimit",0,0,100000);
    static final int PERF_INTERVAL_MS = bounded("ctransport.perf.intervalMs",1000,100,60000);
    private static int bounded(String key,int fallback,int min,int max){
        int value=Integer.parseInt(setting(key,Integer.toString(fallback)));
        if(value<min||value>max)throw new IllegalArgumentException(key+" outside "+min+".."+max);
        return value;
    }
    private CIOConfig() {}
}
