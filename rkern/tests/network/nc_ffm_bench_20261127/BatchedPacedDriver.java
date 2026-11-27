import java.io.IOException;

/** Eight consecutive frames offered per peer turn in one contiguous TCP write. */
public final class BatchedPacedDriver {
    public static void main(String[] args) throws IOException {
        System.err.println("Fixed open-loop pacing with up to 8 frames per peer turn");
        PacedDriver.runMode(args, 8);
    }
}
