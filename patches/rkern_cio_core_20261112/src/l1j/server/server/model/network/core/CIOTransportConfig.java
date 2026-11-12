package l1j.server.server.model.network.core;

import java.io.IOException;
import java.nio.file.Path;

/** Explicit native transport choice. The existing transport remains default. */
final class CIOTransportConfig {
    static final String PROPERTY = "rkern.cio.transport";
    static final String RKERN_SHA256 = "85ee85466fd838379a7256f894db8f670d932cf936af1f8413d71da0ae7cc64d";
    record Choice(Path path, String sha256, boolean rkernNative) {}

    private CIOTransportConfig() {}

    static Choice selected() throws IOException {
        return select(System.getProperty(PROPERTY, "hpsocket"));
    }

    static Choice select(String configured) throws IOException {
        if ("hpsocket".equals(configured)) {
            return new Choice(Path.of("CIOWinTransport.dll"),
                    "9d8106886be0e26d14bd8eb7093714ae3b44d655ccae869ffc98148db8807ecc", false);
        }
        if ("rkern".equals(configured) || "nc".equals(configured)) {
            return new Choice(Path.of("RKernCIOTransport.dll"), RKERN_SHA256, true);
        }
        throw new IOException("Unknown " + PROPERTY + ": " + configured
                + "; expected hpsocket or rkern");
    }
}
