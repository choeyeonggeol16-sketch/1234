package l1j.server.server.model;

import l1j.server.server.model.Instance.L1NpcInstance;

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
        @Override protected void bindSpawn(L1NpcInstance npc) {
            throw new AssertionError("fixture creates no world object");
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
        L1Spawn legacy = new L1Spawn(null);
        legacy.setAmount(7);
        check(legacy instanceof CNpcMaker && legacy.getAmount() == 7, "legacy handle");
        System.out.println("PASS Maker definition, quota, duplicate decay, deadline, retry, wake and legacy handle");
    }
}
