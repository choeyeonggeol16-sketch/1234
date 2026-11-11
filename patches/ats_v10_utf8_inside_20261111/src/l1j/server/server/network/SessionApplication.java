package l1j.server.server.network;

/** Codec/open run on the channel event loop. Packet/control/closed run serially on game workers. */
public interface SessionApplication {
    
    record Framing(int prefixBytes, boolean lengthIncludesPrefix, int minBodyBytes, int maxBodyBytes) {
        public Framing {
            if ((prefixBytes != 2 && prefixBytes != 4) || minBodyBytes < 1 || maxBodyBytes < minBodyBytes) {
                throw new IllegalArgumentException("Invalid frame format");
            }
        }
        
        public static Framing lineage() { 
            return new Framing(2, true, 4, 65533); 
        }
        
        public static Framing advertisement() { 
            return new Framing(4, false, 1, 4096); 
        }
    }
    
    default Framing framing() { 
        return Framing.lineage(); 
    }
    
    default byte[] initialHandshake() { 
        return new byte[0]; 
    }
    
    default void onOpen() throws Exception { 
    }
    
    byte[] decode(byte[] body) throws Exception;
    
    byte[] encode(byte[] body) throws Exception;
    
    void onPacket(byte[] body) throws Exception;
    
    /** Queried only after the serial mailbox stops. A bounded delay preserves legacy logout policy. */
    default long closeDelayMillis() { 
        return 0; 
    }
    
    void onClosed(CloseReason reason) throws Exception;
}