package l1j.server.server.model.network.core;
import java.nio.file.Path;
/** Mandatory native transport. There is no alternate backend or selector. */
final class CTransportConfig {
    static final Path DLL = Path.of("CTransport.dll");
    static final int ABI = 0x00040000;
    static final String DLL_SHA256 = "3a7cca0ab0eb0a4c521ccefd9252247af96342888de72e8625ee200501cff1ae";
    private CTransportConfig() {}
}
