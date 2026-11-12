package l1j.server.server.model.network.core;
import java.nio.file.Path;
/** Mandatory native transport. There is no alternate backend or selector. */
final class CIOConfig {
    static final Path DLL = Path.of("CTransport.dll");
    static final int ABI = 0x00060000;
    static final String DLL_SHA256 = "fbbd2fcd014153457552f57177b796ced5cdeb973579a78ef68341ebc8e6a6c7";
    private CIOConfig() {}
}
