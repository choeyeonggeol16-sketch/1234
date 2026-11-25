package l1j.server.server.model;

/** 실제 Maker 슬롯 처리의 수량, 중복 삭제, 재생성 실패 및 재시도 검증. */
public final class TestNpcMakerLifecycle {
    private static final class Maker extends CNpcMaker {
        int attempts;
        int lastObjectId;
        boolean fail;
        Maker(int amount) {
            super(null);
            setAmount(amount);
            setMinRespawnDelay(5);
            setMaxRespawnDelay(5);
        }
        @Override protected boolean doSpawnInternal(int slot, int objectId) {
            attempts++;
            lastObjectId = objectId;
            return !fail;
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void concurrentDecay() throws Exception {
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            for (int round = 0; round < 100; round++) {
                Maker maker = new Maker(8);
                maker.init();
                long death = System.nanoTime() / 1_000_000L - 6000;
                var gate = new java.util.concurrent.CountDownLatch(1);
                var jobs = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
                for (int thread = 0; thread < 8; thread++) {
                    jobs.add(pool.submit(() -> {
                        gate.await();
                        int accepted = 0;
                        for (int slot = 1; slot <= 8; slot++) {
                            if (maker.npcMakerPublishDecay(slot, 1000 + slot, death)) accepted++;
                        }
                        return accepted;
                    }));
                }
                gate.countDown();
                int accepted = 0;
                for (var job : jobs) accepted += job.get(5, java.util.concurrent.TimeUnit.SECONDS);
                check(accepted == 8, "one accepted deletion per slot");
                maker.npcMakerDrainDecayEvents();
                check(maker.npcMakerActiveCount() == 0 && maker.npcMakerPendingCount() == 8, "concurrent deletion count");
                maker.TimerExpired(CNpcMaker.TIMER_NPC_MAKER_WAKE, 0);
                check(maker.attempts == 16 && maker.npcMakerActiveCount() == 8 && maker.npcMakerPendingCount() == 0, "exactly one respawn per slot");
                maker.Retire();
            }
            for (int round = 0; round < 100; round++) {
                Maker maker = new Maker(1);
                maker.init();
                check(maker.npcMakerPublishDecay(1, 123, System.nanoTime() / 1_000_000L - 6000), "retire race seed");
                var gate = new java.util.concurrent.CountDownLatch(1);
                var timer = pool.submit(() -> {
                    gate.await();
                    maker.TimerExpired(CNpcMaker.TIMER_NPC_MAKER_WAKE, 0);
                    return null;
                });
                var retire = pool.submit(() -> {
                    gate.await();
                    maker.Retire();
                    return null;
                });
                gate.countDown();
                timer.get(5, java.util.concurrent.TimeUnit.SECONDS);
                retire.get(5, java.util.concurrent.TimeUnit.SECONDS);
                int attempts = maker.attempts;
                check(attempts == 1 || attempts == 2, "retire race bounded creation");
                check(maker.IsRetired() && maker.npcMakerActiveCount() == 0 && maker.npcMakerPendingCount() == 0, "retire race final state");
                maker.TimerExpired(CNpcMaker.TIMER_NPC_MAKER_WAKE, 0);
                check(maker.attempts == attempts, "no generation after retirement");
            }
        }
        System.out.println("PASS 100 concurrent eight-producer deletion rounds (6400 attempts), 100 timer-retirement races");
    }

    public static void main(String[] args) throws Exception {
        Maker maker = new Maker(3);
        maker.setId(77);
        maker.setLocX(33000);
        maker.setLocY(33100);
        maker.setMapId((short) 4);
        check(maker.getId() == 77 && maker.getLocX() == 33000
                && maker.getLocY() == 33100 && maker.getMapId() == 4, "definition");
        check(maker.calcRespawnDelayPublic() == 5000, "fixed delay");
        maker.init();
        check(maker.attempts == 3 && maker.npcMakerActiveCount() == 3
                && maker.npcMakerPendingCount() == 0, "initial quota");
        long now = System.nanoTime() / 1_000_000L;
        check(!maker.npcMakerPublishDecay(0, 1, now), "invalid slot");
        check(!maker.npcMakerPublishDecay(4, 1, now), "invalid slot");
        check(maker.npcMakerPublishDecay(2, 123, now), "first decay");
        check(!maker.npcMakerPublishDecay(2, 123, now), "duplicate decay");
        maker.npcMakerDrainDecayEvents();
        check(maker.npcMakerActiveCount() == 2 && maker.npcMakerPendingCount() == 1, "decay count");
        check(!maker.npcMakerPublishDecay(2, 123, now), "waiting slot decay");
        long due = maker.npcMakerNextDeadlineMs();
        check(due == now + 5000, "deadline");
        check(maker.npcMakerStep(due - 1) == due && maker.attempts == 3, "early timer");
        maker.fail = true;
        long retry = maker.npcMakerStep(due);
        check(retry > 0 && maker.attempts == 4 && maker.lastObjectId == 123
                && maker.npcMakerActiveCount() == 2 && maker.npcMakerPendingCount() == 1, "retry");
        maker.fail = false;
        check(maker.npcMakerStep(retry) == -1 && maker.attempts == 5
                && maker.lastObjectId == 123 && maker.npcMakerActiveCount() == 3
                && maker.npcMakerPendingCount() == 0, "respawn");
        check(maker.npcMakerTryQueue() && !maker.npcMakerTryQueue(), "wake coalescing");
        maker.npcMakerClearQueued();
        check(maker.npcMakerTryQueue(), "wake rearm");
        CNpcSpawnDefine definition = new CNpcMaker(null);
        definition.setAmount(7);
        check(definition.getAmount() == 7, "definition handle");
        maker.init();
        check(maker.attempts == 5, "initialization is one-shot");
        check(maker.npcMakerPublishDecay(1, 124, now), "decay before retire");
        maker.npcMakerDrainDecayEvents();
        check(maker.npcMakerPendingCount() == 1, "pending before retire");
        maker.Retire();
        maker.Retire();
        check(maker.IsRetired() && maker.npcMakerPendingCount() == 0
                && maker.npcMakerActiveCount() == 0, "retire logical slots");
        check(!maker.npcMakerPublishDecay(3, 125, now), "late decay rejected");
        check(!maker.npcMakerTryQueue(), "retired wake rejected");
        maker.TimerExpired(CNpcMaker.TIMER_NPC_MAKER_WAKE, 0);
        maker.TimerExpired(CNpcMaker.TIMER_NPC_MAKER_RESPAWN, 1);
        maker.init();
        maker.doSpawn(1, 0);
        check(maker.npcMakerStep(Long.MAX_VALUE) == -1 && maker.attempts == 5,
                "retired maker cannot generate");
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        definition.setLocX(33000);
        definition.setLocY(33100);
        long pos = definition.GenerateSpawnPos(rnd, 0, false, 0, 0);
        check((int)(pos >>> 32) == 33000 && (int)pos == 33100, "fixed position");
        definition.setRandomx(3);
        definition.setRandomy(2);
        for (int i = 0; i < 1000; i++) {
            pos = definition.GenerateSpawnPos(rnd, 0, false, 0, 0);
            check((int)(pos >>> 32) >= 32997 && (int)(pos >>> 32) <= 33003
                    && (int)pos >= 33098 && (int)pos <= 33102, "random radius");
        }
        definition.setLocX1(32990);
        definition.setLocX2(33010);
        definition.setLocY1(33090);
        definition.setLocY2(33110);
        for (int i = 0; i < 1000; i++) {
            pos = definition.GenerateSpawnPos(rnd, 0, false, 0, 0);
            check((int)(pos >>> 32) >= 32990 && (int)(pos >>> 32) < 33010
                    && (int)pos >= 33090 && (int)pos < 33110, "area bounds");
        }
        pos = definition.GenerateSpawnPos(rnd, 50, false, 0, 0);
        check((int)(pos >>> 32) == 33000 && (int)pos == 33100, "area fallback");
        concurrentDecay();
        System.out.println("PASS Maker definition, quota, duplicate decay, deadline, retry, wake, retirement and position rules");
    }
}
