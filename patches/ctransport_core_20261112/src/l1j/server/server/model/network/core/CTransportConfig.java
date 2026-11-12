package l1j.server.server.model.network.core;
import java.nio.file.Path;
/** Mandatory native transport. There is no alternate backend or selector. */
final class CTransportConfig {
    static final Path DLL = Path.of("CTransport.dll");
    static final int ABI = 0x00030000;
    static final String DLL_SHA256 = "d149b3bd1835eb966ff590eecdb34890ca8798e033c43a58c498b0bee586e827";
    private CTransportConfig() {}
}
