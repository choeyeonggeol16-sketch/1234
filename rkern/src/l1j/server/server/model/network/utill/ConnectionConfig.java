package l1j.server.server.model.network.utill;

import java.net.SocketAddress;

import l1j.server.server.utils.ConfigReader;

/**
 * 네트워크 공용 설정.
 *
 * Transport는 NIO.2로 고정되어 있으며 PacketExecutor / Client 공용 설정만 유지한다.
 */
public class ConnectionConfig {

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


    public ConnectionConfig(SocketAddress socketAddress) {
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
}
