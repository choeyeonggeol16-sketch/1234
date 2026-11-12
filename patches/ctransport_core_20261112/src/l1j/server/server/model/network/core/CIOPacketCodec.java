package l1j.server.server.model.network.core;

import java.lang.foreign.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/** Verified L1 codec symbols and per-connection native ownership bridge.
 * Wire Feed/Next and the plaintext storage are owned by CTransport C++.
 */
@SuppressWarnings("preview")
final class CIOPacketCodec implements AutoCloseable {
    static final String SHA256 = "60bde93c2ba20057e7e307594edef69ced273b69f923535ab658769e09f0cea9";
    private static final class API {
        static final SymbolLookup SYMBOLS = open();
        static final MemorySegment FUNCTIONS = entries();
        static SymbolLookup open() {
            Path path=Path.of("CIOPacketCodec.dll").toAbsolutePath();
            try { CIOServer.requireKnownDll(path,SHA256);return SymbolLookup.libraryLookup(path,Arena.global()); }
            catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}
        }
        static MemorySegment entries() {
            String[] names={"CIOPC_Version","CIOPC_Create","CIOPC_Destroy","CIOPC_Feed","CIOPC_Next","CIOPC_Encrypt","CIOPC_Pending"};
            MemorySegment table=Arena.global().allocate((long)names.length*ValueLayout.ADDRESS.byteSize(),ValueLayout.ADDRESS.byteAlignment());
            for(int i=0;i<names.length;i++)table.setAtIndex(ValueLayout.ADDRESS,i,SYMBOLS.find(names[i]).orElseThrow(()->new UnsatisfiedLinkError("L1 codec entry missing")));
            return table;
        }
    }
    private final CSocket channel;
    private boolean closed;
    CIOPacketCodec(CSocket channel,long seed) {
        this.channel=channel;
        if(!channel.initializeUserSocket(API.FUNCTIONS,(int)seed))throw new IllegalStateException("Native L1 user socket/codec initialization failed");
    }
    synchronized void encrypt(ByteBuffer buffer,int offset,int length) {
        if(closed)throw new IllegalStateException("L1 codec bridge closed");
        if(!buffer.isDirect()||offset<0||length<0||offset>buffer.capacity()-length)throw new IllegalArgumentException("native SEND range");
        ByteBuffer range=buffer.duplicate();range.clear().position(offset).limit(offset+length);
        if(!channel.encryptUserData(range))throw new IllegalStateException("Native L1 encryption failed");
    }
    @Override public synchronized void close(){closed=true;}
}
