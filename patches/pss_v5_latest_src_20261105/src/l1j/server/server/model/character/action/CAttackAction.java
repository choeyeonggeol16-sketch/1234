package l1j.server.server.model.character.action;

import l1j.server.Config;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.IndunSystem.MiniGame.L1Gambling3;
import l1j.server.MJ3SEx.EActionCodes;
import l1j.server.MJ3SEx.SpriteInformation;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ClanJoinInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti;
import l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eNotiType;
import l1j.server.server.model.network.core.sc.packet.SC_AttackStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.TYUtil;

import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
public final class CAttackAction extends CNewAction {

    CAttackAction(CCreatureController controller) {
        super(controller);
    }

    /**
     * 서버가 소유하는 다음 공격 가능 시각.
     * 클라이언트 패킷 간격은 절대 기준으로 사용하지 않는다.
     */
    private long nextAttackTimeMs;

    @Override
    public boolean OnStart() {
        AddTimer(1);
        return true;
    }

    @Override
    public boolean OnTick(long nowMs) {
        final L1PcInstance owner = controller.actor();
        if (owner == null
                || owner.getNetConnection() == null
                || !owner.getNetConnection().isConnected()) {
            return true;
        }

        final boolean isAuto = controller.isAutoAttack();

        if (owner.isInvisDelay()) {
            AddTimer(1);
            return false;
        }

        if (!isAuto) {
            final long moveDelay = nowMs - owner.getLastMoveActionMillis();
            if (moveDelay >= 0L && moveDelay < 300L) {
                AddTimer(1);
                return false;
            }
        }

        if (isAuto && controller.HasPendingAction()) {
            return true;
        }

        if (owner.isDead() || owner.isGhost() || owner.get_teleport()) {
            return true;
        }

        if (isAuto && (owner.is_non_action() || MJCommons.isStunSkill(owner))) {
            AddTimer(5);
            return false;
        }

        /*
         * 서버가 계산한 최종 공격 interval이 유일한 공속 기준이다.
         * 변신/무기/공속 물약/버프 등의 보정은 getCurrentSpriteInterval() 내부에서 끝난다.
         */
        final long intervalMs = owner.getCurrentSpriteInterval(EActionCodes.fromInt(owner.getCurrentWeapon() + 1));

        if (intervalMs <= 0L) {
            return true;
        }

        /*
         * 서버 authoritative attack deadline.
         *
         * 수동 공격:
         *   deadline 전 패킷은 실행하지 않고 종료한다.
         *   current action을 붙잡지 않으므로 MOVE/SKILL pending을 막지 않는다.
         *
         * 자동 공격:
         *   서버가 남은 시간만큼 직접 sleep 후 다시 실행한다.
         */
        final long remainMs = nextAttackTimeMs - nowMs;
        if (remainMs > 0L) {
            if (isAuto) {
                AddTimer(controller.millisToTicks(remainMs));
                return false;
            }
            return true;
        }

        final int tId = controller.i0();
        final int x = controller.i1();
        final int y = controller.i2();

        /*
         * deadline을 통과한 ATTACK 요청은 서버 interval 1회분을 소비한다.
         * 타겟이 사라졌거나 범위가 틀려도 클라이언트가 다음 공격시각을 앞당길 수 없다.
         */
        nextAttackTimeMs = nowMs + intervalMs;

        if (!isAuto && tId == 0) {
            doAttack(owner, null, true, x, y);
            return true;
        }

        final boolean attackExecuted = processAttackNow(owner, tId, x, y, isAuto);

        if (!attackExecuted) {
            return true;
        }

        if (!isAuto || tId <= 0) {
            return true;
        }

        if (controller.HasPendingAction()) {
            return true;
        }

        final L1Object targetObj = L1World.getInstance().findObject(tId);
        if (!(targetObj instanceof L1Character)) {
            return true;
        }

        final L1Character target = (L1Character) targetObj;
        if (target.isDead()
                || owner.getMapId() != target.getMapId()
                || target.isInvisble()
                || !owner.getLocation().isInScreen(target.getLocation())) {
            return true;
        }
        
        if(target instanceof L1PcInstance targetPc) {
        	// 타겟이 텔레포트중이면
        	if(targetPc.get_teleport()) {
        		 return true;
        	}
        }
        

        /* 다음 자동공격은 서버 interval이 끝난 뒤 한 번만 깨운다. */
        AddTimer(controller.millisToTicks(intervalMs));
        return false;
    }

    public boolean CheckAttackValidity(L1PcInstance owner, L1Object target) {
        return owner != null && target != null && !owner.isDead();
    }

    private boolean processAttackNow(
            L1PcInstance owner,
            int tId,
            int x,
            int y,
            boolean isAuto) {

        if (!validateAttackPreconditions(owner, tId, isAuto)) {
            return false;
        }

        final L1Object obj = L1World.getInstance().findObject(tId);

        if (obj == null || !obj.instanceOf(MJL1Type.L1TYPE_CHARACTER)) {
            if (!isAuto) {
                doAttack(owner, null, true, x, y);
            }
            return false;
        }

        if (!validateAttackTargetAndRange(owner, obj, tId, x, y, isAuto)) {
            return false;
        }

        // Only client-driven PSS uses the companion observation. Keep normal
        // manual/server-AH behavior and check sight before any hit/effect.
        var connection = owner.getNetConnection();
        var automation = connection == null ? null : connection.getAutomationSession();
        if (automation != null && automation.clientDriven() && owner.getAttackRang() > 1
                && obj instanceof L1Character target && !owner.glanceCheck(target.getX(), target.getY())) {
            l1j.server.server.model.character.pss.window.PlaySupportWindow.blockedShot(owner, tId);
            return false;
        }

        executeAttackAction(owner, obj, tId, x, y);
        return true;
    }

    private void doAttack(
            L1PcInstance owner,
            L1Character target,
            boolean once,
            int x,
            int y) {

        if (target == null || target.isDead()) {
            if (once) {
                owner.setHeading(owner.targetDirection(x, y));
                SC_AttackStatus.sendAndBroadcast(owner, 0, owner.getCurrentWeapon() + 1, true);
            }
            return;
        }

        owner.getRegenController().OnAttack(System.currentTimeMillis());
        controller.DoAttack(owner, target);
    }

    private void doFocusWave(L1PcInstance owner, int tId) {
        if (!owner.isElf()) {
            return;
        }

        if (!owner.hasSkillEffect(L1SkillId.FOCUS_WAVE)) {
            return;
        }

        final L1ItemInstance weapon = owner.getWeaponSwap();
        if (weapon == null
                || (weapon.getItem().getType1() != 20
                        && weapon.getItem().getType1() != 62)) {
            owner.removeSkillEffect(L1SkillId.FOCUS_WAVE);
            return;
        }

        if (tId == 0 || tId != owner.get_last_focusing_target()) {
            if (owner.get_focus_wave_level() > 1) {
                SC_SkillSound.send(owner, owner.getId(), 16540);

                SC_SkillSound.broadcast(owner, owner.getId(), 16540);

                SC_SkillBrave.send(owner, owner.getId(), 10, owner.getSkillEffectTimeSec(
                                        L1SkillId.FOCUS_WAVE));

                SC_SkillBrave.broadcast(owner, owner.getId(), 10, 0);
            }

            owner.set_focus_wave_level(1);
            owner.set_last_focusing_target(tId);
            return;
        }

        switch (owner.get_focus_wave_level()) {
        case 1:
            if (MJRnd.isWinning(1000000, 200000)) {
                owner.inc_focus_wave_level();

                SC_SkillSound.send(owner, owner.getId(), 16532);

                SC_SkillSound.broadcast(owner, owner.getId(), 16532);

                SC_SkillBrave.send(owner, owner.getId(), 11, owner.getSkillEffectTimeSec(
                                        L1SkillId.FOCUS_WAVE));

                SC_SkillBrave.broadcast(owner, owner.getId(), 11, 0);
            }
            break;

        case 2:
            if (MJRnd.isWinning(1000000, 200000)) {
                owner.setSkillEffect(
                        L1SkillId.포커스3단,
                        3 * 1000);

                if (owner.hasSkillEffect(L1SkillId.포커스3단)){
                	owner.inc_focus_wave_level();
                	SC_SkillSound.send(owner, owner.getId(), 16533);
                	SC_SkillSound.broadcast(owner, owner.getId(), 16533);
            		 SC_SkillBrave.send(owner, owner.getId(), 12, owner.getSkillEffectTimeSec(
                             L1SkillId.FOCUS_WAVE));
                    SC_SkillBrave.broadcast(owner, owner.getId(), 12, 0);
                }
            }
            break;

        default:
            break;
        }
    }

    private boolean validateAttackPreconditions(
            L1PcInstance owner,
            int tId,
            boolean isAuto) {

        if (!owner.hasSkillEffect(L1SkillId.비욘드)) {
            owner.killSkillEffectTimer(L1SkillId.MEDITATION);
        }

        if (owner.hasSkillEffect(L1SkillId.MOEBIUS)) {
            owner.killSkillEffectTimer(L1SkillId.MOEBIUS);

            l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eNotiType notiWire_notiType = null;
            int notiWire_spellId = 0;
            int notiWire_duration = 0;
            int notiWire_offIconId = 0;
            int notiWire_endStrId = 0;
            boolean notiWire_isGood = false;
            int notiWire_bit = 0;



            { notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
            { notiWire_bit |= 2; notiWire_spellId = L1SkillId.MOEBIUS; }
            { notiWire_bit |= 4; notiWire_duration = 0; }
            { notiWire_bit |= 32; notiWire_offIconId = 9443; }
            { notiWire_bit |= 512; notiWire_endStrId = 5551; }
            { notiWire_bit |= 1024; notiWire_isGood = true; }

            SC_SpellBuffNoti.sendFields(
                    owner, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0,
                    notiWire_endStrId, notiWire_isGood, 0, 0, 0, notiWire_bit);
            SC_ServerMessage.send(owner, 5551);
        }

        if (tId <= 0) {
            return false;
        }

        if (owner.isGmInvis()
                || owner.is_non_action()
                || owner.isDead()
                || owner.isInvisble()
                || owner.isGhost()
                || owner.get_teleport()) {
            return false;
        }

        if (owner.hasSkillEffect(L1SkillId.BLIND_HIDING)
                || MJCommons.isNonAction(owner)) {
            return false;
        }

        if (!owner.is_top_ranker()
                && owner.getInventory().getWeight100() > 82) {

            SC_ServerMessage.send(owner, 110);

            return false;
        }

        final int mid = owner.getMapId();

        if (!(mid >= 2600 && mid <= 2699)
                && owner.getInventory().checkEquipped(203003)) {

            if (!isAuto) {
                owner.sendPackets(
                        "화룡의 안식처에서만 사용이 가능합니다.");
            }

            return false;
        }

        owner.offFishing();
        return true;
    }

    private boolean validateAttackTargetAndRange(
            L1PcInstance owner,
            L1Object obj,
            int tId,
            int x,
            int y,
            boolean isAuto) {

        if (obj == null) {
            return false;
        }

        if (Config.METICE_CLAN_ATTACK_CUTTER
                && obj instanceof L1NpcInstance) {

            final L1NpcInstance npc =
                    (L1NpcInstance) obj;

            final int npcId =
                    npc.getNpcTemplate().get_npcId();

            final int[] bossIds =
                    TYUtil.GetIntTokenizeIntArray(
                            Config.BossAreaAttrBossIds);

            if (owner.getClanid()
                    == Config.신규혈맹클랜) {

                for (int bossId : bossIds) {
                    if (npcId == bossId) {
                        SC_SystemMessage.send(owner, "\\f4[!] "
                                                + Config.신규혈맹이름
                                                + "혈맹은 해당 보스사냥이 제한 됩니다.");

                        return false;
                    }
                }
            }
        }

        if (!owner.isGm()
                && obj instanceof L1PcInstance) {

            final int spr =
                    owner.getCurrentSpriteId();

            if (spr == 12015
                    || spr == 5641
                    || spr == 11685
                    || spr == 11620
                    || spr == 11621) {

                owner.sendPackets(
                        "전설/신화 변신상태에서는 PVP가 불가합니다.");

                return false;
            }
        }

        if (obj instanceof L1NpcInstance
                && ((L1NpcInstance) obj).getHiddenStatus() != 0) {
            return false;
        }

        if (obj instanceof L1ClanJoinInstance) {
            CIOThreadCallback.do_clanjoin(
                    owner,
                    (L1ClanJoinInstance) obj);

            return false;
        }

        if (!(obj instanceof L1Character)) {
            return false;
        }

        final L1Character target = (L1Character) obj;

        if (target.isDead() || owner.getMapId() != target.getMapId() || target.isInvisble()) {
            return false;
        }

        if (!owner.getLocation().isInScreen(target.getLocation())) {
            return false;
        }

        final int mid = owner.getMapId();
        final int ox = owner.getX();
        final int oy = owner.getY();

        if (target instanceof L1NpcInstance npc) {
            if (ox == 33515
                    && oy == 32851
                    && mid == 4
                    && npc.getNpcId() == 300027) {

                new L1Gambling3().dealerTrade(owner);
            }
        }
        
        final SpriteInformation sInfo = target.getCurrentSprite();

        final int range = owner.getAttackRang();

        int targetWidth = 1;
        int targetHeight = 1;

        if (sInfo != null) {
            targetWidth =
                    Math.max(
                            1,
                            sInfo.getWidth());

            targetHeight =
                    Math.max(
                            1,
                            sInfo.getHeight());
        }

        final int targetX = target.getX();
        final int targetY = target.getY();

        final int dx =
                Math.abs(
                        ox - targetX);

        final int dy =
                Math.abs(
                        oy - targetY);

        final int limitX =
                range
                        + Math.max(
                                0,
                                targetWidth - 1);

        final int limitY =
                range
                        + Math.max(
                                0,
                                targetHeight - 1);

        if (!isAuto) {
            if (Math.abs(targetX - x)
                    > range + 1
                    || Math.abs(targetY - y)
                            > range + 1) {
                return false;
            }

            return dx <= limitX
                    && dy <= limitY;
        }

        if (dx <= limitX
                && dy <= limitY) {
            return true;
        }

        if (range == 1) {
            return false;
        }

        if (range >= 2
                && dx <= limitX + 1
                && dy <= limitY + 1) {

            final long moveDelay =
                    System.currentTimeMillis()
                            - owner.getLastMoveActionMillis();

            return moveDelay < 300L;
        }

        return false;
    }

    private void executeAttackAction(
            L1PcInstance owner,
            L1Object obj,
            int tId,
            int x,
            int y) {

        doFocusWave(owner, tId);

        if (owner.hasSkillEffect(
                L1SkillId.ABSOLUTE_BARRIER)) {

            owner.removeSkillEffect(
                    L1SkillId.ABSOLUTE_BARRIER);
        }

        if (owner.hasSkillEffect(
                L1SkillId.MOEBIUS)) {

            owner.removeSkillEffect(
                    L1SkillId.MOEBIUS);
        }

        owner.delInvis();

        owner.getRegenController()
                .OnAttack(
                        System.currentTimeMillis());

        final L1Character target =
                (L1Character) obj;

        if (target.isDead()) {
            owner.setHeading(
                    owner.targetDirection(
                            x,
                            y));

            SC_AttackStatus.sendAndBroadcast(owner, 0, owner.getCurrentWeapon() + 1, true);
        } else {
            controller.DoAttack(
                    owner,
                    target);
        }
    }
}