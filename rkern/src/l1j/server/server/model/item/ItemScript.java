package l1j.server.server.model.item;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** Finds editable UTF-8 item definitions at server startup. */
public final class ItemScript {
    private ItemScript() {}

    @FunctionalInterface
    public interface Parser<T> {
        T parse(String source, BufferedReader reader) throws IOException;
    }

    @FunctionalInterface
    public interface RowReader {
        void read(String[] fields);
    }

    public static <T> T load(String filename, String property, Parser<T> parser) {
        final Path path = Path.of("script", filename);
        final String override = System.getProperty(property);
        if (override != null) {
            if (override.isBlank()) throw new IllegalStateException("Empty script path: " + property);
            final T configured = readLocal(Path.of(override), parser);
            if (configured == null) throw new IllegalStateException("Missing item script " + override);
            return configured;
        }

        final T external = readLocal(path, parser);
        if (external != null) return external;
        final String resource = "/script/" + filename;
        try (InputStream bundled = ItemScript.class.getResourceAsStream(resource)) {
            if (bundled != null) {
                final var decoder = StandardCharsets.UTF_8.newDecoder();
                return parser.parse(resource, new BufferedReader(new InputStreamReader(bundled, decoder)));
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read item script " + resource, failure);
        }

        final T checkout = readLocal(Path.of("src").resolve(path), parser);
        if (checkout != null) return checkout;
        final T repository = readLocal(Path.of("rkern", "src").resolve(path), parser);
        if (repository != null) return repository;
        throw new IllegalStateException("Missing item script " + path + " (or -D" + property + ")");
    }

    private static <T> T readLocal(Path path, Parser<T> parser) {
        final String source = path.toAbsolutePath().normalize().toString();
        final BufferedReader reader;
        try {
            reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) {
            return null;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot open item script " + source, failure);
        }
        try (reader) {
            return parser.parse(source, reader);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read item script " + source, failure);
        }
    }

    public static void readRows(String source, BufferedReader reader, RowReader parse) throws IOException {
        int number = 0;
        String raw;
        while ((raw = reader.readLine()) != null) {
            number++;
            if (number == 1 && raw.startsWith("\uFEFF")) raw = raw.substring(1);
            if (raw.isBlank() || raw.stripLeading().startsWith("#")) continue;
            final String[] fields = raw.split("\t", -1);
            for (int i = 0; i < fields.length; i++) fields[i] = fields[i].strip();
            try {
                parse.read(fields);
            } catch (IllegalArgumentException | IllegalStateException invalid) {
                throw new IllegalStateException(source + ":" + number + " " + invalid.getMessage(), invalid);
            }
        }
    }

    public static void requireFields(String[] fields, int count) {
        if (fields.length != count) {
            throw new IllegalArgumentException(fields[0] + " requires " + (count - 1) + " fields");
        }
    }
}
