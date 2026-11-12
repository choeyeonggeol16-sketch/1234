package l1j.server.server.model.network.core;
import java.nio.file.Path;
/** Mandatory native transport. There is no alternate backend or selector. */
final class CTransportConfig {
    static final Path DLL = Path.of("CTransport.dll");
    static final int ABI = 0x00020000;
    static final String DLL_SHA256 = "5bf718c9df9e3dbce2eee824bbb3928430a2e142ee06abb45f14ae4156e33fa6";
    private CTransportConfig() {}
}
