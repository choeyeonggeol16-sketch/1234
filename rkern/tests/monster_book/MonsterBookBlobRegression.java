import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import l1j.server.server.model.BookQuest.UserSide.BQSCharacterCriteriaProgress;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;
import l1j.server.server.packets.Protocol.MainServer.Proto.MonsterBook.CompletedAchievement;
import l1j.server.server.packets.Protocol.MainServer.Proto.MonsterBook.CriteriaProgress;

public final class MonsterBookBlobRegression {
    public static void main(String[] args) throws Exception {
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
        concurrentSnapshots();
        System.out.println("PASS legacy monster book blob, reward flags and concurrent reset/snapshot");
    }

    private static void concurrentSnapshots() throws Exception {
        final var book = BQSCharacterData.newInstance();
        final var progress = BQSCharacterCriteriaProgress.newInstance();
        progress.set_progress(CriteriaProgress.newInstance(7));
        final var achievement = CompletedAchievement.newInstance(37);
        final var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var writes = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 25_000; i++) {
                    book.resetProgress();
                    book.add_progresses(progress);
                    book.add_achievements(achievement);
                }
                return null;
            });
            var snapshots = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 25_000; i++) {
                    var saved = BQSCharacterData.load(book.save());
                    if (saved.get_achievement(37) != null)
                        check(saved.get_progress(7) != null, "achievement without matching progress");
                }
                return null;
            });
            start.countDown();
            writes.get(10, TimeUnit.SECONDS);
            snapshots.get(10, TimeUnit.SECONDS);
        }
        check(Arrays.equals(book.resetProgress(), new int[] {7}), "reset returns removed criteria IDs");
        check(book.get_progress(7) == null && book.get_achievement(37) == null, "reset clears both indexes");
    }

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
