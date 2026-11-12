package l1j.server.server.model.network.core;

import java.io.IOException;
import java.nio.file.Path;

/** Explicit native transport choice. The existing transport remains default. */
final class CIOTransportConfig {
    static final String PROPERTY = "rkern.cio.transport";
    static final String NC_SHA256 = "143620e8b7ae0c1cc3842c170b318ec5b99d69ab38f50e177a36f6a23ec2f706";
    record Choice(Path path, String sha256, boolean reconstructedNc) {}

    private CIOTransportConfig() {}

    static Choice selected() throws IOException {
        return select(System.getProperty(PROPERTY, "hpsocket"));
    }

    static Choice select(String configured) throws IOException {
        if ("hpsocket".equals(configured)) {
            return new Choice(Path.of("CIOWinTransport.dll"),
                    "9d8106886be0e26d14bd8eb7093714ae3b44d655ccae869ffc98148db8807ecc", false);
        }
        if ("nc".equals(configured)) {
            return new Choice(Path.of("CIONcTransport.dll"), NC_SHA256, true);
        }
        throw new IOException("Unknown " + PROPERTY + ": " + configured
                + "; expected hpsocket or nc");
    }
}
