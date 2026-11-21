package l1j.server.server.model.character.skill;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
/**
 * 토마호크 출혈.
 */
public final class TomahawkBleedService {

    private static final TomahawkBleedService INSTANCE =
            new TomahawkBleedService();

    public static TomahawkBleedService getInstance() {
        return INSTANCE;
    }

    private TomahawkBleedService() {
    }

    /**
     * 토마호크 시작/갱신.
     *
     *   ticks = 2 + nextInt(5) -> 2~6틱 랜덤
     *   totalBleed = casterLevel * 2
     *   총 데미지를 랜덤 tick 수에 균등 분배
     */
    public void start(
            final L1SkillUse su,
            final L1PcInstance caster,
            final L1Character target) {

        if (su == null || caster == null || target == null) {
            return;
        }

        final int casterLv = caster.getLevel();
        if (casterLv <= 0) {
            return;
        }

        // 2 ~ 6틱 랜덤. 6초 고정 아님.
        final int ticks = 2 + su.hdRandom().nextInt(5);

        startResolvedFromOwner(target, caster.getId(), casterLv, ticks);
    }

    /** Already on the recipient controller; no extra worker or per-tick allocation. */
    void startResolvedFromOwner(final L1Character target, final int attackerId,
            final int attackerLevel, final int ticks) {
        if (target == null || attackerLevel <= 0 || ticks < 2 || ticks > 6) return;
        final int totalBleed = Math.max(1, attackerLevel * 2);
        final int baseDamage = totalBleed / ticks;
        final int remainder = totalBleed % ticks;
        // The generation must be started by the victim's execution owner.
        final long token = target.beginTomahawkState();
        if (target instanceof L1PcInstance pc) sendIconOn(pc);
        CIOThreadCallback.getInstance().markTomahawkActive(
                target, attackerId, target.getMapId(), ticks,
                baseDamage, remainder, token);
    }

    /**
     * objectId 기반 강제 취소.
     */
    public void cancel(final int targetId) {
        if (targetId <= 0) {
            return;
        }

        final L1Object obj =
                L1World.getInstance().findObject(targetId);

        if (obj instanceof L1Character) {
            cancel((L1Character) obj);
        }
    }

    /**
     * 객체를 이미 가진 곳에서 world lookup 없이 취소.
     */
    public void cancel(final L1Character target) {
        if (target == null) {
            return;
        }

        if (!target.cancelTomahawkState()) {
            return;
        }

        if (target instanceof L1PcInstance) {
            sendIconOff((L1PcInstance) target);
        }
    }

    /**
     * CharacterActionRunner에서 1초마다 호출.
     * @return true  다음 1초 tick 필요
     *         false 종료
     */
    public boolean processWheelTick(
            final L1Character target,
            final int attackerId,
            final int mapId,
            final int tickIndex,
            final int totalTicks,
            final int baseDamage,
            final int remainder,
            final long token) {

        if (target == null) {
            return false;
        }

        // 재시전 또는 강제취소된 이전 node.
        if (!target.isTomahawkState(token)) {
            return false;
        }

        /*
         * 월드에서 사라졌거나 objectId가 다른 객체로 바뀐 경우 종료.
         * node가 target 참조를 가지고 있으므로 identity까지 확인한다.
         */
        final L1Object live =
                L1World.getInstance().findObject(target.getId());

        if (live != target) {
            finish(target, token);
            return false;
        }

        if (target.isDead() || target.getMapId() != mapId) {
            finish(target, token);
            return false;
        }

        // 앱솔/어바/아이스랜스 등 기존 중단 조건 그대로.
        if (isTomaHowk(target)) {
            finish(target, token);
            return false;
        }

        L1PcInstance attacker = null;
        if (attackerId != 0) {
            final L1Object ao =
                    L1World.getInstance().findObject(attackerId);

            if (ao instanceof L1PcInstance) {
                attacker = (L1PcInstance) ao;
            }
        }

        // attacker resolve 사이 재시전됐으면 old tick 폐기.
        if (!target.isTomahawkState(token)) {
            return false;
        }

        final int damage =
                baseDamage + (tickIndex <= remainder ? 1 : 0);

        final boolean showMotion = tickIndex >= 2;

        if (damage > 0) {
            applyBleed(attacker, target, damage, showMotion);
        }

        // 랜덤으로 뽑힌 totalTicks에 도달하면 종료.
        if (tickIndex >= totalTicks) {
            finish(target, token);
            return false;
        }

        return target.isTomahawkState(token);
    }

    private static void finish(
            final L1Character target,
            final long token) {

        if (target == null) {
            return;
        }

        /*
         * 아직 이 generation이 현재 것일 때만 종료.
         * 새 토마호크가 이미 들어왔으면 새 아이콘/상태를 건드리지 않는다.
         */
        if (!target.finishTomahawkState(token)) {
            return;
        }

        if (target instanceof L1PcInstance) {
            sendIconOff((L1PcInstance) target);
        }
    }

    private static void sendIconOn(final L1PcInstance pc) {
        SC_NewCreateItem.sendTypeCk(pc, SC_NewCreateItem.버프창, true);
        SC_ServerMessage.send(pc, 3992);
    }

    private static void sendIconOff(final L1PcInstance pc) {
        SC_NewCreateItem.sendTypeCk(pc, SC_NewCreateItem.버프창, false);
        SC_ServerMessage.send(pc, 3993);
    }

    private static void applyBleed(
            final L1PcInstance caster,
            final L1Character target,
            int dmg,
            final boolean showMotion) {

        if (target == null || dmg <= 0) {
            return;
        }

        // 기존 동작: 2틱부터 데미지 모션.
        if (showMotion) {
            final int targetId = target.getId();

            if (target instanceof L1PcInstance) {
                final L1PcInstance targetPc =
                        (L1PcInstance) target;

                SC_DoActionGFX.send(
                        targetPc.getNetConnection(),
                        targetId,
                        ActionCodes.ACTION_Damage);
            }

            SC_DoActionGFX.broadcast(
                    target,
                    ActionCodes.ACTION_Damage);
        }

        if (target instanceof L1PcInstance) {
            final L1PcInstance targetPc =
                    (L1PcInstance) target;

            dmg -= targetPc.getDamageReductionByArmor();

            if (targetPc.hasSkillEffect(L1SkillId.LUCIFER)) {
                dmg -= (int) Math.round(dmg * 0.10D);
            } else {
                final double rate =
                        targetPc.getImmuneReduction();

                if (rate > 0.0D) {
                    dmg -= (int) Math.round(dmg * rate);
                }
            }

            if (dmg < 0) {
                dmg = 0;
            }

            targetPc.receiveDamage(caster, dmg);
            return;
        }

        if (target instanceof L1NpcInstance) {
            ((L1NpcInstance) target).receiveDamage(caster, dmg);
            return;
        }

        try {
            target.receiveDamage(caster, dmg);
        } catch (Throwable ignore) {
        }
    }
	public static boolean isTomaHowk(L1Character body){
		return body.hasSkillEffect(L1SkillId.EARTH_BIND) ||
				body.hasSkillEffect(L1SkillId.ICE_LANCE) ||
				body.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER) ||
				body.hasSkillEffect(L1SkillId.MOB_BASILL)||
				body.hasSkillEffect(L1SkillId.MOB_COCA);
	}
}
