package l1j.server.server.model.network.core;
import java.nio.file.Path;
/** Mandatory native transport. There is no alternate backend or selector. */
final class CTransportConfig {
    static final Path DLL = Path.of("CTransport.dll");
    static final int ABI = 0x00050000;
    static final String DLL_SHA256 = "c5160c9e6d93924e6b0ec9bc52d40af93c1e03df9183a3629dd71ba9bba839f2";
    private CTransportConfig() {}
}
