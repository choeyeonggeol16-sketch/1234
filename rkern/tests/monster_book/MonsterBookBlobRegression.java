import java.util.Arrays;
import java.util.HexFormat;

import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;

public final class MonsterBookBlobRegression {
    public static void main(String[] args) {
        // Legacy body: negative uint32 object id, progress with absent level,
        // sparse IDs, duplicate achievement (last wins), and a 64-bit deck version.
        byte[] legacy = HexFormat.of().parseHex(
                "08ffffffff0f" + "1208120608e0a712107b" + "12080802120408011009"
                        + "1a0708ad0210071800" + "1a0708ad0210081801" + "288080808010");
        var original = BQSCharacterData.load(legacy);
        check(original.get_object_id() == -1, "unsigned object ID");
        check(original.get_progress(300000).getCurrentQuantity() == 123
                && !original.get_progress(300000).has_current_ahcievement_level(), "sparse progress and field presence");
        check(original.get_progress(1).get_current_ahcievement_level() == 2, "progress level");
        check(original.get_achievement(301).get_completed_time() == 8
                && original.get_achievement(301).get_get_reward(), "duplicate achievement retains latest reward");
        check(original.get_decks_version() == (1L << 32), "64-bit deck version");
        byte[] saved = original.save();
        var reloaded = BQSCharacterData.load(saved);
        check(reloaded.get_progress(300000).getCurrentQuantity() == 123
                && reloaded.get_achievement(301).get_get_reward(), "progress and reward survive the round trip");
        check(Arrays.equals(saved, reloaded.save()), "deterministic serialization");
        System.out.println("PASS legacy monster book blob, sparse IDs, field presence and reward flags");
    }

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
