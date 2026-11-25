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
        System.out.println("PASS Maker definition, quota, duplicate decay, deadline, retry, wake, retirement and position rules");
    }
}
