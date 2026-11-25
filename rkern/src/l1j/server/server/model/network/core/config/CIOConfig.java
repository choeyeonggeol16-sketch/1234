package l1j.server.server.model.network.core.config;

import java.net.SocketAddress;
import java.nio.file.Path;

import l1j.server.server.utils.ConfigReader;

/**
 * 네트워크 공용 설정.
 *
 * Java Selector와 기존 CIO의 공용 설정. 두 설정 파일의 로드 시점을 유지한다.
 */
public final class CIOConfig {

    public static final int HEADER_SIZE = 2;

    public static final int MAX_WIRE_PACKET_SIZE = 0xFFFF;

    /**
     * 현재 단일 receive frame 파이프라인의 최대 프레임 공간.
     * 16-bit packet length 전체를 받을 수 있도록 기본 64 KiB를 유지한다.
     */
    public static int READ_BUFFER_SIZE;

    public SocketAddress address;

    public long shutdownWaitTime;
    public boolean useNagle;
    public boolean dropPackets;
    public int dropPacketThreshold;
    public int threadPriority;

    public static int PENDING_CLIENTS_QUEUE_SIZE;
    public static int PACKET_QUEUE_SIZE;

    public static int MAX_DRAIN_SIZE;

    public static int NETWORK_CLIENT_PERMISSION;
    public static int NETWORK_ADDRESS2ACCOUNT;
    public static int PACKET_LIMIT_PER_SEC;

    /** 한 receive completion에서 처리할 최대 프레임 수. */
    public static int MAX_PACKETS_PER_COMPLETION;

    public boolean usePreLoginTimeout;

    // ------------------------------------------------------------------
    // PacketExecutor
    // ------------------------------------------------------------------

    /** shard당 RingBuffer 슬롯 수. 반드시 2의 거듭제곱. */
    public int packetExecutorRingSize;

    /** PacketExecutor shard 개수 상한. */
    public int packetExecutorMaxShards;

    /** 0이면 CPU 수 기준 자동 결정, 1 이상이면 고정 shard 수. */
    public int packetExecutorShardCount;

    /** RingBuffer 포화 drop 로그 출력 간격. */
    public long packetExecutorDropLogInterval;


    public CIOConfig(SocketAddress socketAddress) {
        address = socketAddress;

        final ConfigReader networkConfig =
                new ConfigReader("config/Network.ini");

        shutdownWaitTime =
                networkConfig.getInt("ShutdownWaitTime", 5) * 1000L;

        threadPriority =
                networkConfig.getInt("ThreadPriority", Thread.NORM_PRIORITY);

        dropPackets =
                networkConfig.getBoolean("DropPackets", true);

        dropPacketThreshold =
                networkConfig.getInt("DropPacketThreshold", 2000);

        MAX_PACKETS_PER_COMPLETION =
                networkConfig.getInt("MaxPacketsPerCompletion", 32);

        READ_BUFFER_SIZE =
                networkConfig.getInt("ReadBufferSize", 65536);

        PENDING_CLIENTS_QUEUE_SIZE =
                networkConfig.getInt("PendingClientsQueueSize", 8192);

        PACKET_QUEUE_SIZE =
                networkConfig.getInt("PacketQueueSize", 2048);

        MAX_DRAIN_SIZE =
                networkConfig.getInt("MaxDrainSize", 16);

        NETWORK_CLIENT_PERMISSION =
                networkConfig.getInt("NetworkClientPermission", 2);

        NETWORK_ADDRESS2ACCOUNT =
                networkConfig.getInt("NetworkAddress2Account", 2);

        PACKET_LIMIT_PER_SEC =
                networkConfig.getInt("PacketLimitPerSec", 200);

        usePreLoginTimeout =
                networkConfig.getBoolean("UsePreLoginTimeout", true);

        packetExecutorRingSize =
                networkConfig.getInt("DisruptorRingSize", 16384);

        packetExecutorMaxShards =
                networkConfig.getInt("DisruptorMaxShards", 16);

        packetExecutorShardCount =
                networkConfig.getInt("DisruptorShardCount", 0);

        packetExecutorDropLogInterval =
                networkConfig.getInt("DisruptorDropLogInterval", 1024);


    }

    public static int loginTimeoutMs() { return TransportSettings.LOGIN_TIMEOUT_MS; }
    public static int macLimit() { return TransportSettings.MAC_LIMIT; }

    private static final class TransportSettings {
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
    }
}
