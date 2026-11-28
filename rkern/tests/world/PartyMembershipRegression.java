package l1j.server.server.model;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import l1j.server.Config;
import l1j.server.server.Controller.AuraController;
import l1j.server.server.model.Instance.L1PcInstance;

public final class PartyMembershipRegression {
    public static void main(String[] args) throws Exception {
        WorldShapeRegression.installMapDefinitions();
        Config.MAX_PT = 3;
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                var first = player(10);
                var second = player(11);
                var target = player(12);
                var start = new CountDownLatch(1);
                var left = workers.submit(() -> { start.await(); return CParty.acceptInvite(first, target, 1); });
                var right = workers.submit(() -> { start.await(); return CParty.acceptInvite(second, target, 0); });
                start.countDown();
                var a = left.get(5, TimeUnit.SECONDS);
                var b = right.get(5, TimeUnit.SECONDS);
                check((a == null) != (b == null), "two invitations cannot add the same player");
                var winner = a == null ? b : a;
                check(winner.getList().size() == 2 && target.getParty() == winner, "matching roster and player");
                check((a == null ? first : second).getParty() == null, "losing invitation cannot create a solo party");
                var leader = winner.getLeader();
                var readOnly = winner.getList();
                winner.getMembers()[0] = null;
                check(winner.getList() == readOnly && winner.isMember(leader), "array is private and read-only roster is reused");
                winner.passLeader(target, target);
                check(winner.getLeader() == leader, "obsolete leader cannot transfer");
                var third = player(13);
                check(winner.tryAddMember(third) && !winner.tryAddMember(player(14)), "normal party quota");
                winner.passLeader(leader, third);
                check(winner.getLeader() == third && readOnly.size() == 2, "immutable old roster and leader change");
                winner.leaveMember(third);
                check(winner.getList().isEmpty() && target.getParty() == null && leader.getParty() == null,
                        "dissolution clears all member references");
            }
        }
        verifyCrownSkillReference();
        System.out.println("PASS party invitations, quota, roster, leader transfer, dissolution and class-change aura cleanup");
    }

    private static void verifyCrownSkillReference() {
        var crown = new L1PcInstance();
        crown.setId(30);
        crown.setName("fixtureCrown");
        crown.setType(0);
        var aura = AuraController.getInstance();

        var partyWithoutSkill = CParty.acceptInvite(crown, player(31), 0);
        check(partyWithoutSkill != null && !aura.getMember(crown), "unknown skill does not track a crown");
        partyWithoutSkill.leaveMember(crown);

        crown.AcquireSkill(121);
        var partyWithSkill = CParty.acceptInvite(crown, player(32), 0);
        check(partyWithSkill != null && aura.getMember(crown), "permanent skill tracks a crown");
        crown.setType(1);
        crown.setClassId(L1PcInstance.CLASSID_KNIGHT_MALE);
        crown.DeleteAllAcquireSkills();
        partyWithSkill.leaveMember(crown);
        check(!aura.getMember(crown), "class change followed by party leave retires the old crown tracker");
    }

    private static L1PcInstance player(int id) {
        var pc = new L1PcInstance() {
            @Override public boolean isCrown() { return false; }
        };
        pc.setId(id);
        pc.setName("fixture" + id);
        return pc;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
