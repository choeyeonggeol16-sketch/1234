package l1j.server.server.model.network.core.config;
import java.nio.file.Path;
/** Java Selector network settings. */
public final class CIOConfig {
    private static final java.util.Properties SETTINGS = loadSettings();
    public static final int LOGIN_TIMEOUT_MS = bounded("ctransport.loginTimeoutMs",60000,0,600000);
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
    // MAC enforcement is off until a verified server-side provider binds identity.
    public static final int MAC_LIMIT = bounded("ctransport.macLimit",0,0,100000);
    private static int bounded(String key,int fallback,int min,int max){
        int value=Integer.parseInt(setting(key,Integer.toString(fallback)));
        if(value<min||value>max)throw new IllegalArgumentException(key+" outside "+min+".."+max);
        return value;
    }
    private CIOConfig() {}
}
