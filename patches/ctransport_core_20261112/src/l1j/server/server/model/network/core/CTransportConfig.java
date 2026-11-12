package l1j.server.server.model.network.core;
import java.nio.file.Path;
/** Mandatory native transport. There is no alternate backend or selector. */
final class CTransportConfig {
    static final Path DLL = Path.of("CTransport.dll");
    static final int ABI = 0x00030000;
    static final String DLL_SHA256 = "2e172e07dee35c94272718e01903b057f131dbbd30b22bbf04ef76f47c37f95b";
    private CTransportConfig() {}
}
