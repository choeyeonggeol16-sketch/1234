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
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.Instance.L1ClanJoinInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.Utill.PcBaseAbilityCache;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import LIN.SC_SPELL_BUFF_NOTI;
import LIN.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.model.network.core.sc.packet.SC_AttackStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.TYUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.model.network.core.sc.packet.SC_AttackCritical;
import l1j.server.server.utils.CalcStat;
import l1j.server.server.model.character.Combat.CCreatureDamageReaction;
import l1j.server.server.model.character.Combat.MortalBody.MortalBodyProcEngine;
import l1j.server.server.model.character.Attack.Helper.L1AttackHelper;
import l1j.server.server.model.character.ItemSkill.Doll.MagicDollCombatProcs;
import l1j.server.server.model.character.Attack.Helper.L1DamageCalcHelper;
import l1j.server.server.model.character.Attack.Helper.L1SkillProcHelper;
import l1j.server.server.model.character.Attack.Helper.L1UndeadBonusHelper;
import l1j.server.server.model.character.Attack.Helper.L1WeaponDurabilityHelper;
import l1j.server.server.model.character.Attack.Helper.NpcStatusAttackHelper;
import l1j.server.server.model.character.Attack.Helper.TargetSizeHelper;
import l1j.server.server.model.character.Attack.Helper.L1AttackPacketHelper;
import l1j.server.server.model.character.Attack.Helper.DamageReductionHelper;
import l1j.server.server.model.character.Attack.Helper.HitEvasionModel;
import l1j.server.server.model.character.Attack.Helper.L1WeaponAttrEnchantHelper;
import l1j.server.server.model.Instance.L1ScarecrowInstance;
import l1j.server.server.model.Instance.L1GuardianInstance;
import l1j.server.server.utils.CommonUtil;
import l1j.server.DogFight.Instance.MJDogFightInstance;
import l1j.server.DogFight.Instance.MJDogFightAttackHandler;
import l1j.server.DogFight.MJDogFightSettings;
import l1j.server.MJCompanion.Instance.MJCompanionAttackHandler;
import l1j.server.MJInstanceSystem.MJInstanceEnums.InstStatus;
import l1j.server.MJRaidSystem.Loader.MJRaidLoadManager;
import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.WeaponAddDamage;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.UserProtectMonsterTable;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PeopleInstance;
import l1j.server.server.model.character.skill.helper.WeakExposeHelper;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.cs.packet.CS_ItemUse;
import l1j.server.server.model.poison.L1DamagePoison;
import l1j.server.server.model.poison.L1ParalysisPoison;
import l1j.server.server.model.poison.L1SilencePoison;
import l1j.server.server.model.network.core.sc.packet.SC_AttackMissPacket;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacketForNpc;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_UseArrowSkill;
import l1j.server.server.model.network.core.sc.packet.SC_UseAttackSkill;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.model.*;
public final class CAttackAction extends CNewAction {

    private static final Logger LOG = Logger.getLogger(CAttackAction.class.getName());

    // Defense class coefficients for the 2016 keylink MR curve (PC types 0..7).
    // Reload publishes all eight primitive values together; combat only reads
    // this volatile reference, with no file IO or allocations per attack.
    private static volatile double[] keylinkClassFactors = {
            2.30, 2.40, 2.20, 2.00, 2.20, 2.40, 2.20, 2.40
    };
    private static final Path KEYLINK_BALANCE_FILE = Path.of("./config/KeylinkBalance.properties");

    static {
        try {
            reloadKeylinkBalance();
        } catch (IOException | IllegalArgumentException ex) {
            LOG.log(Level.WARNING, "KeylinkBalance.properties not loaded; using previous/default class factors", ex);
        }
    }

    /** Reload only defender class factors, retaining the last valid snapshot on failure. */
    public static synchronized void reloadKeylinkBalance() throws IOException {
        final double[] next = new double[8];
        final boolean[] seen = new boolean[8];
        try (BufferedReader reader = Files.newBufferedReader(KEYLINK_BALANCE_FILE, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                ++lineNumber;
                final String entry = line.trim();
                if (entry.isEmpty() || entry.charAt(0) == '#' || entry.charAt(0) == '!') continue;
                final int separator = entry.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalArgumentException("KeylinkBalance line " + lineNumber + " must be Key=Value");
                }
                final String key = entry.substring(0, separator).trim();
                final int type = switch (key) {
                    case "Prince" -> 0;
                    case "Knight" -> 1;
                    case "Elf" -> 2;
                    case "Wizard" -> 3;
                    case "DarkElf" -> 4;
                    case "DragonKnight" -> 5;
                    case "Illusionist" -> 6;
                    case "Warrior" -> 7;
                    default -> throw new IllegalArgumentException(
                            "Unknown KeylinkBalance key on line " + lineNumber + ": " + key);
                };
                if (seen[type]) {
                    throw new IllegalArgumentException("Duplicate KeylinkBalance key: " + key);
                }
                final String raw = entry.substring(separator + 1).trim();
                final double value;
                try {
                    value = Double.parseDouble(raw);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid KeylinkBalance " + key + ": " + raw, ex);
                }
                if (!Double.isFinite(value) || value < 0.0 || value > 10.0) {
                    throw new IllegalArgumentException(key + " must be between 0.0 and 10.0: " + raw);
                }
                next[type] = value;
                seen[type] = true;
            }
        }
        for (int i = 0; i < seen.length; ++i) {
            if (!seen[i]) throw new IllegalArgumentException("KeylinkBalance missing class type " + i);
        }
        keylinkClassFactors = next;
    }

    private static final AtomicIntegerFieldUpdater<CAttackAction> OWNED_BUSY =
            AtomicIntegerFieldUpdater.newUpdater(CAttackAction.class, "ownedAttackBusy");
    // PC controller is constructed once on character creation. Reuse one combat
    // context per actor, never a worker-wide object for ordinary PC actions.
    private final AttackTargetData ownedAttack = new AttackTargetData(this);
    private volatile int ownedAttackBusy;

    CAttackAction(CCreatureController controller) {
        super(controller);
    }

    private AttackTargetData tryBorrowOwned(L1Character attacker, L1Character target) {
        if (!OWNED_BUSY.compareAndSet(this, 0, 1)) return null;
        try {
            ownedAttack.initialize(attacker, target);
            return ownedAttack;
        } catch (RuntimeException | Error ex) {
            ownedAttack.clear();
            OWNED_BUSY.set(this, 0);
            throw ex;
        }
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

    private boolean validateAttackPreconditions(
            L1PcInstance owner,
            int tId,
            boolean isAuto) {

        if (owner.getWeapon() == null
                && !L1PolyMorph.canAttackWithoutWeapon(owner.getCurrentSpriteId())) return false;

        if (!owner.hasSkillEffect(L1SkillId.비욘드)) {
            owner.killSkillEffectTimer(L1SkillId.MEDITATION);
        }

        if (owner.hasSkillEffect(L1SkillId.MOEBIUS)) {
            owner.killSkillEffectTimer(L1SkillId.MOEBIUS);

            LIN.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
            int notiWire_spellId = 0;
            int notiWire_duration = 0;
            int notiWire_offIconId = 0;
            int notiWire_endStrId = 0;
            boolean notiWire_isGood = false;
            int notiWire_bit = 0;



            { notiWire_bit |= 1; notiWire_notiType = LIN.SC_SPELL_BUFF_NOTI.eNotiType.END; }
            { notiWire_bit |= 2; notiWire_spellId = L1SkillId.MOEBIUS; }
            { notiWire_bit |= 4; notiWire_duration = 0; }
            { notiWire_bit |= 32; notiWire_offIconId = 9443; }
            { notiWire_bit |= 512; notiWire_endStrId = 5551; }
            { notiWire_bit |= 1024; notiWire_isGood = true; }

            LIN.SC_SPELL_BUFF_NOTI.sendFields(
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

        if (owner.hasSkillEffect(
                L1SkillId.ABSOLUTE_BARRIER)) {

            owner.removeSkillEffect(
                    L1SkillId.ABSOLUTE_BARRIER);
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

    /*
     * NC CAttackAction execution / AttackTargetData dispatch phases.
     * Full V4 reward, counter, damage, packet and commit ordering preserved.
     * Instance implementations must call this action owner, not a duplicate
     * combat dispatcher. NPC-specific rule checks remain on their instances.
     */
/**
     * NC-style centralized PC -> NPC execution for ordinary target entities.
     * Target-specific permission checks stay at the entity boundary.
     * Hit/damage formulas use src22's core rules; action and commit order stays intact.
     */
    // NPC execution and rare same-PC reentrancy have no PC-owned controller
    // context available. These use a small thread-local fallback. The usual
    // PC owner path never acquires that pool or allocates for each hit.
    private static final ThreadLocal<AttackTargetPool> FALLBACK_POOL =
            ThreadLocal.withInitial(AttackTargetPool::new);

    private static final class AttackTargetPool {
        AttackTargetData first;
        AttackTargetData second;
    }

    private static AttackTargetData borrowAttack(L1Character attacker, L1Character target) {
        if (attacker instanceof L1PcInstance pc) {
            final CCreatureController cc = pc.getCreatureController();
            if (cc != null) {
                final AttackTargetData owned = cc.attackAction().tryBorrowOwned(attacker, target);
                if (owned != null) return owned;
            }
        }
        final AttackTargetPool pool = FALLBACK_POOL.get();
        AttackTargetData attack = pool.first;
        if (attack != null) pool.first = null;
        else if ((attack = pool.second) != null) pool.second = null;
        else attack = new AttackTargetData(); // NPC/reentrant cold path only
        attack.initialize(attacker, target);
        return attack;
    }

    private static void releaseAttack(AttackTargetData attack) {
        attack.clear();
        if (attack.ownerAction != null) {
            OWNED_BUSY.set(attack.ownerAction, 0);
            return;
        }
        final AttackTargetPool pool = FALLBACK_POOL.get();
        if (pool.first == null) pool.first = attack;
        else if (pool.second == null) pool.second = attack;
    }

    public static void attackPcNpc(L1PcInstance attacker, L1NpcInstance target, boolean poison) {
        final AttackTargetData attack = borrowAttack(attacker, target);
        try {
            if (attack.calcHit()) {
                attack.calcDamage();
                if (poison && attack._isHit) {
                    attack.addPcPoisonAttack(attacker, target);
                }
            }
            attack.action();
            attack.commit();
        } finally {
            releaseAttack(attack);
        }
    }

    /** An NPC which only animates a rejected attack, with no combat roll. */
    public static void swingOnly(L1PcInstance attacker, L1NpcInstance target) {
        final AttackTargetData attack = borrowAttack(attacker, target);
        try {
            attack.action();
        } finally {
            releaseAttack(attack);
        }
    }

    /** Non-damageable NPC still performs hit logic before animating. */
    public static void hitAndSwing(L1PcInstance attacker, L1NpcInstance target) {
        final AttackTargetData attack = borrowAttack(attacker, target);
        try {
            attack.calcHit();
            attack.action();
        } finally {
            releaseAttack(attack);
        }
    }

    /** Special elf-only guardian rewards, with the original hit/reward/damage order. */
    public static void attackGuardianBarehanded(L1PcInstance attacker,
                                                L1GuardianInstance target) {
        final AttackTargetData attack = borrowAttack(attacker, target);
        try {
            if (attack.calcHit()) {
                if (!target.onGuardianBarehandedHit(attacker)) {
                    return; // original no-reward early return
                }
                attack.calcDamage();
                if (attack._isHit) attack.addPcPoisonAttack(attacker, target);
            }
            attack.action();
            attack.commit();
        } finally {
            releaseAttack(attack);
        }
    }

    /** Scarecrow reward and damage-display callbacks; exactly no HP commit. */
    public static void attackScarecrow(L1PcInstance attacker,
                                       L1ScarecrowInstance target) {
        // Borrow comes first, preserving the original weapon snapshot timing.
        final AttackTargetData attack = borrowAttack(attacker, target);
        try {
            if (attacker.isInParty() || attacker.isDead()) {
                attacker.sendPackets("파티중에는 허수아비를 공격할 수 없습니다.");
                return;
            }
            if (attack.calcHit()) {
                target.onTrainingHitBeforeDamage(attacker);
                int damage = attack.calcDamage();
                target.onTrainingHitAfterDamage(attacker, damage);
            }
            attack.action();
        } finally {
            releaseAttack(attack);
        }
    }

    /** AoE physical hit with per-cast leverage and single primary target animation. */
    public static void attackNpcPhysicalAoE(L1NpcInstance attacker, L1Character target,
                                            int leverage, int actId, int gfxId,
                                            boolean primaryTarget) {
        final AttackTargetData attack = borrowAttack(attacker, target);
        try {
            if (attack.calcHit()) {
                if (leverage > 0) {
                    attack.setLeverage(leverage);
                }
                if (actId > 0) {
                    attack.setActId(actId);
                }
                // The same victim reaction boundary also applies to physical
                // AoE, without sending a normal attack animation per secondary.
                if (CCreatureDamageReaction.onPhysicalHit(attack, attacker, target,
                        primaryTarget)) {
                    if (primaryTarget && gfxId > 0) {
                        SC_SkillSound.broadcast(attacker, attacker.getId(), gfxId);
                    }
                    return;
                }
                attack.calcDamage();
            }
            if (actId > 0) {
                attack.setActId(actId);
            }
            if (primaryTarget) {
                if (gfxId > 0) {
                    SC_SkillSound.broadcast(attacker, attacker.getId(), gfxId);
                }
                attack.action();
            }
            attack.commit();
        } finally {
            releaseAttack(attack);
        }
    }

    /** Safe-zone swing visual; no hit roll, damage or commit. */
    public static void playSafeZoneAttack(L1PcInstance attacker, L1PcInstance target) {
        final AttackTargetData attack = borrowAttack(attacker, target);
        try {
            attack.action();
        } finally {
            releaseAttack(attack);
        }
    }

    /** PC attacking another PC; V4 action/commit sequencing stays intact. */
    public static void attackPcTarget(L1PcInstance attacker, L1PcInstance target) {
        final AttackTargetData attack = borrowAttack(attacker, target);
        try {
            if (!attack.calcHit()) {
                attack.action();
                attack.commit();
                return;
            }
            if (attack._weaponType2 == 17) {
                // Keylink is a magic hit: Counter Barrier / Titan Rock / Inferno
                // must not consume a physical attack reaction.
                if (CCreatureDamageReaction.onMagicDamage(attacker, target)) {
                    attack.set_hit(false);
                    attack.action();
                    return;
                }
            } else if (CCreatureDamageReaction.onPhysicalHit(attack, attacker, target)) {
                return;
            }

            attacker.set_pet_target(target);
            attack.calcDamage();
            if (attack._isHit && attack._weaponType2 != 17)
                attack.addPcPoisonAttack(attacker, target);
            attack.action();
            attack.commit();
        } finally {
            releaseAttack(attack);
        }
    }

    /** NPC attacking a character: preserve original effects and sequence. */
    public static void attackNpcTarget(L1NpcInstance attacker, L1Character target) {
        final AttackTargetData attack = borrowAttack(attacker, target);
        try {
            if (!attack.calcHit()) {
                attack.action();
                attack.commit();
                return;
            }
            if (CCreatureDamageReaction.onPhysicalHit(attack, attacker, target)) return;
            attack.calcDamage();
            attack.action();
            attack.commit();
        } finally {
            releaseAttack(attack);
        }
    }

    /**
     * NC-inspired per-target AttackTargetData execution context.
     * All four PC/NPC hit and final damage paths, special effects, counter
     * handling, animations and HP commits live inside CAttackAction now.
     * Combat fields are reset on borrow/release; nested attacks do not share a live context.
     * Core hit and base damage rules are adapted from src22 L1Attack;
     * the surrounding V4 skill and transaction processing is retained.
     */
    public static final class AttackTargetData {

    private final CAttackAction ownerAction;

	private L1PcInstance _pc = null;

	private L1Character _target = null;

	private L1PcInstance _targetPc = null;

	private L1NpcInstance _npc = null;

	private L1NpcInstance _targetNpc = null;

	private int _targetId;

	private int _targetX;

	private int _targetY;

	private int _statusDamage = 0;

	// Shared java.util.Random uses a single CAS seed across all attacker threads.
    // ThreadLocalRandom keeps each combat thread's PRNG state independent and
    // still implements Random for the existing L1DamageCalcHelper APIs.
    private static final Random _random = ThreadLocalRandom.current();

	private int _hitRate = 0;

	private int _calcType;

	private static final int PC_PC = 1;

	private static final int PC_NPC = 2;

	private static final int NPC_PC = 3;

	private static final int NPC_NPC = 4;

	public boolean _isHit = false;

	public boolean _isCritical = false;

	private int _damage = 0;
	private int _effex = 0;

	private int _attckGrfxId = 0;

	private int _attckActId = 0;

	// 공격자가 플레이어의 경우의 무기 정보
	private L1ItemInstance weapon = null;

	// 전사 쌍수
	private L1ItemInstance Sweapon = null;// 세컨웨폰
	private int _SweaponId = 0;
	private int _SweaponType = 0;
	private int _SweaponType1 = 0;
	private int _SweaponAddHit = 0;
	private int _SweaponAddDmg = 0;
	private int _SweaponSmall = 0;
	private int _SweaponLarge = 0;
	private int _SweaponRange = 1;
	private int _SweaponBless = 1;
	private int _SweaponEnchant = 0;
	private int _SweaponMaterial = 0;
	private int _SweaponAttrEnchantLevel = 0;

	private int _weaponId = 0;

	private int _weaponType = 0;

	private int _weaponType2 = 0;

	// private int _weaponType1 = 0;

	private int _weaponAddHit = 0;

	private int _weaponAddDmg = 0;

	private int _weaponSmall = 0;

	private int _weaponLarge = 0;

	private int _weaponBless = 1;

	private int _weaponEnchant = 0;

	private int _weapon_bless_level = 0;

	private int _weaponMaterial = 0;

	private int _weaponDoubleDmgChance = 0;

	private int _weaponAttrLevel = 0; // 속성 레벨

	private int _attackType = 0;

	private L1ItemInstance _arrow = null;

	private L1ItemInstance _sting = null;

	private int _leverage = 10; // 1/10배로 표현한다.

	public void setLeverage(int i) {
		_leverage = i;
	}

	private int getLeverage() {
		return _leverage;
	}

	// Damage stat tables are owned exclusively by CalcStat (1..80 clamped).

	public void setActId(int actId) {
		_attckActId = actId;
	}

	public void setGfxId(int gfxId) {
		_attckGrfxId = gfxId;
	}

	public int getActId() {
		return _attckActId;
	}

	public int getGfxId() {
		return _attckGrfxId;
	}

    private AttackTargetData() { this(null); }

    private AttackTargetData(CAttackAction ownerAction) {
        this.ownerAction = ownerAction;
    }

    public AttackTargetData(L1Character attacker, L1Character target) {
        this(null);
        initialize(attacker, target);
    }

    private void clear() {
        _pc = null;
        _npc = null;
        _target = null;
        _targetPc = null;
        _targetNpc = null;
        weapon = null;
        Sweapon = null;
        _arrow = null;
        _sting = null;
        _targetId = _targetX = _targetY = 0;
        _statusDamage = _hitRate = _calcType = 0;
        _isHit = _isCritical = false;
        _edoryuDouble = _doubleBreak = _burning = _cycloneProc = false;
        _crash = _fury = _burningSlashProc = false;
        _damage = _effex = _attckGrfxId = _attckActId = 0;
        _SweaponId = _SweaponType = _SweaponType1 = 0;
        _SweaponAddHit = _SweaponAddDmg = 0;
        _SweaponSmall = _SweaponLarge = 0;
        _SweaponRange = _SweaponBless = 1;
        _SweaponEnchant = _SweaponMaterial = _SweaponAttrEnchantLevel = 0;
        _weaponId = _weaponType = _weaponType2 = 0;
        _weaponAddHit = _weaponAddDmg = _weaponSmall = _weaponLarge = 0;
        _weaponBless = 1;
        _weaponEnchant = _weapon_bless_level = _weaponMaterial = 0;
        _weaponDoubleDmgChance = _weaponAttrLevel = _attackType = 0;
        _leverage = 10;
    }

    private void initialize(L1Character attacker, L1Character target) {
        clear();
		if (attacker instanceof L1PcInstance) {
			_pc = (L1PcInstance) attacker;
			if (target instanceof L1PcInstance) {
				_targetPc = (L1PcInstance) target;
				_calcType = PC_PC;
			} else if (target instanceof L1NpcInstance) {
				_targetNpc = (L1NpcInstance) target;
				_calcType = PC_NPC;
			}
			// 무기 정보의 취득
            final L1ItemInstance left = _pc.getWeapon();
            final L1ItemInstance right = _pc.getSecondWeapon();
            if (L1AttackHelper.isSlayerDualAxe(_pc, left, right)) {
                final L1ItemInstance picked = _pc.getWeaponSwap();
                weapon = (picked == left || picked == right) ? picked : left;
                Sweapon = (weapon == left) ? right : left;
            } else {
                weapon = left;
                Sweapon = null;
            }
			if (Sweapon != null) {
				_SweaponId = Sweapon.getItem().getItemId();
				_SweaponType = Sweapon.getItem().getType1();

				final long weaponBonus = WeaponAddDamage.getInstance().getBonusPacked(_weaponId);
				_SweaponAddHit = Sweapon.getItem().getHitModifier() + (int) (weaponBonus >> 32);
				_SweaponAddDmg = Sweapon.getItem().getDmgModifier() + (int) weaponBonus;

				_SweaponType1 = Sweapon.getItem().getType();
				_SweaponSmall = Sweapon.getItem().getDmgSmall();
				_SweaponLarge = Sweapon.getItem().getDmgLarge();
				_SweaponBless = Sweapon.getItem().getBless();
				_SweaponEnchant = Sweapon.getEnchantLevel(); // src22: durability applied by final formula
				// 마이너스
				_SweaponMaterial = Sweapon.getItem().getMaterial();
				_SweaponAttrEnchantLevel = Sweapon.getAttrEnchantLevel();
			}
			if (weapon != null) {
				_weaponId = weapon.getItem().getItemId();
				_weaponType = weapon.getItem().getType1();
				_weaponType2 = weapon.getItem().getType();// 변경
                // DB corrections are additive to the original template stats.
                // One immutable memory lookup for both values; no SQL/new per hit.
                final long weaponBonus = WeaponAddDamage.getInstance().getBonusPacked(_weaponId);
                _weaponAddHit = weapon.getItem().getHitModifier() + (int) (weaponBonus >> 32);
                _weaponAddDmg = weapon.getItem().getDmgModifier() + (int) weaponBonus;


				_weaponSmall = weapon.getItem().getDmgSmall();
				_weaponLarge = weapon.getItem().getDmgLarge();
				_weaponBless = weapon.getItem().getBless();
                _weaponEnchant = _weaponType == 0 ? 0 : weapon.getEnchantLevel();
				_weapon_bless_level = weapon.get_bless_level();
				_weaponMaterial = weapon.getItem().getMaterial();
				if (_weaponType == 20) { // 아로의 취득
					_arrow = _pc.getInventory().getArrow();
					if (_arrow != null) {
						_weaponBless = _arrow.getItem().getBless();
						_weaponMaterial = _arrow.getItem().getMaterial();
					}
				}
				if (_weaponType == 62) { // 스팅의 취득
					_sting = _pc.getInventory().getSting();
					if (_sting != null) {
						_weaponBless = _sting.getItem().getBless();
						_weaponMaterial = _sting.getItem().getMaterial();
					}
				}
				_weaponDoubleDmgChance = weapon.getItem().getDoubleDmgChance();
				_weaponAttrLevel = weapon.getAttrEnchantLevel();
			}
            _statusDamage = (_weaponType == 20 || _weaponType == 62)
                    ? CalcStat.원거리대미지(_pc.getAbility().getTotalDex())
                    : CalcStat.근거리대미지(_pc.getAbility().getTotalStr());
		} else if (attacker instanceof L1NpcInstance) {
			_npc = (L1NpcInstance) attacker;
			if (target instanceof L1PcInstance) {
				_targetPc = (L1PcInstance) target;
				_calcType = NPC_PC;
			} else if (target instanceof L1NpcInstance) {
				_targetNpc = (L1NpcInstance) target;
				_calcType = NPC_NPC;
			}
		}
		_target = target;
		_targetId = target.getId();
		_targetX = target.getX();
		_targetY = target.getY();
	}

	/* ■■■■■■■■■■■■■■■■ 명중 판정 ■■■■■■■■■■■■■■■■ */

    // src22/L1Attack PC/NPC hit/damage rules, retained under the existing
    // NC CAttackAction ownership and per-character context. No L1Attack class.
    private boolean _edoryuDouble;
    private boolean _doubleBreak;
    private boolean _burning;
    private boolean _cycloneProc;
    private boolean _crash;
    private boolean _fury;
    private boolean _burningSlashProc;

    public boolean calcHit() {
        if (_target == null) return (_isHit = false);

        switch (_calcType) {
        case PC_PC:
        case PC_NPC:
        	return (_isHit = calcHitPcAttack());
        case NPC_PC:
        	return (_isHit = calcHitNpcToPc());
        case NPC_NPC:
        	return (_isHit = calcHitNpcToNpc());
        default:
        	return (_isHit = false);
        }
    }

    /** PC가 공격하는 케이스 (PC->PC, PC->NPC) */
    private boolean calcHitPcAttack() {
        if (_pc == null) return false;
        if (isUnarmedPcAttack()
                && !L1PolyMorph.canAttackWithoutWeapon(_pc.getCurrentSpriteId())) return false;
        
        // 1) 탄/무기 조건
        final int type1 = _weaponType;
        final int itemId = _weaponId;

        if (type1 == 20) { // 활
            if (itemId != 190 && itemId != 10000 && itemId != 202011 && _arrow == null) return false;
        } else if (type1 == 62) { // 스팅
            if (_sting == null) return false;
        }

        // 특정 무기들 무조건 miss
        if (itemId == 247 || itemId == 248 || itemId == 249) return false;

        // 2) 시야 체크
        if (!_pc.glanceCheck(_targetX, _targetY) || !_target.glanceCheck(_pc.getX(), _pc.getY())) {
            return false;
        }
        
        // Keylink uses the live weapon range resolved for the character.
        // A guaranteed hit must never bypass range, visibility or PvP rules.
        final int range = (_weaponType2 == 17)
                ? Math.max(1, _pc.getAttackRang())
                : L1AttackHelper.weaponRangeByType(type1);
        if (_pc.getLocation().getTileLineDistance(_target.getLocation()) > range + 1) return false;
        if (!_pc.getLocation().isInScreen(_target.getLocation())) return false;

        // Reject an out-of-range Burning Shot before rolling damage or procs.
        if (_pc.hasSkillEffect(L1SkillId.버닝샷)
                && (Math.abs(_pc.getX() - _targetX) > Config.버닝샷칸
                || Math.abs(_pc.getY() - _targetY) > Config.버닝샷칸)) {
            SC_SystemMessage.send(_pc, "\\f3버닝샷: 사정거리 밖이여서 공격할 수 없습니다.");
            return false;
        }
        
        // 3) 타입별 실제 명중 계산
        if (_calcType == PC_PC) {
            if (isSameCombatTeamBlocked()) return false;
            if (L1AttackHelper.isSafeZoneAttackBlocked(_pc, _target)) return false;
            if (_weaponType2 == 17) {
                return !L1AttackHelper.applyInvalidHitRule(_target)
                        && !MJCommons.isCounterMagic(_target);
            }

            final boolean rangedPc = L1AttackHelper.isRangedByType1(type1);          
            if (L1AttackHelper.shouldEvadeByMoebius(_target, rangedPc)) {
            	return false;
            }
            
            return calcPcPcHit();
        }

        if (_calcType == PC_NPC && _targetNpc != null) {     	
            if (isSpecialNpcImmune()) {
                return false;
            }
        }
        if (_weaponType2 == 17) {
            return !L1AttackHelper.applyInvalidHitRule(_target)
                    && !MJCommons.isCounterMagic(_target);
        }
        // PC_NPC
        return calcPcNpcHit();
    }

	/** 엔피시 -> 플레이어 명중 판정 **/
    private boolean calcHitNpcToPc() {
        if (_npc == null || _targetPc == null) return false;
        // 앱솔
		if (L1AttackHelper.applyInvalidHitRule(_target)) return false;
		// 펫, 서먼, 세이프티존 처리 
	    if (L1AttackHelper.blockPetSummonCompanionHitInSafeZone(_npc, _targetPc)) {
	        return false;
	    }
	    // 뫼비우스 처리
		final boolean rangedNpc = L1AttackHelper.isRangedNpc(_npc.getNpcTemplate().get_ranged());
		if (L1AttackHelper.shouldEvadeByMoebius(_target, rangedNpc)) {
		    return false; 
		}
        
        return (_npc instanceof L1CompanionInstance || _npc instanceof MJDogFightInstance)
                ? do_calc_hit_companion(_npc)
                : calcNpcPcHit();
    }

	/** 엔피시 -> 엔피시 명중 판정 **/
    private boolean calcHitNpcToNpc() {
        if (_npc == null || _targetNpc == null) return false;
    
        if (!L1AttackHelper.isAttackableNpcTarget(_targetNpc)) return false;
        
        return (_npc instanceof L1CompanionInstance || _npc instanceof MJDogFightInstance)
                ? do_calc_hit_companion(_npc)
                : calcNpcNpcHit();
    }
    
    /** 플레이어 -> 플레이어 명중 판정 **/
    private boolean calcPcPcHit() {
        if (_pc == null || _targetPc == null || _target == null) return false;

        final int wType = _weaponType;
        
        // ===== 앱솔블레이드로 앱솔 배리어 파괴 =====
        L1AttackHelper.tryBreakAbsoluteBarrier(_pc, wType, _targetPc);

        // ===== 원/근 구분 =====
        final boolean isRanged = (wType == 20 || wType == 62);

        // ===== H(_hitRate) 계산 =====
        final int wpnHit = _weaponAddHit;
        final int wpnEnchant = _weaponEnchant;
        
        final int loss = weapon != null ? weapon.get_durability() : 0;

        _hitRate = L1AttackHelper.calcPcHitH(_pc, isRanged, wpnHit, wpnEnchant, loss);
        
        // ===== AC / DG / ER  =====
        final int acDef = HitEvasionModel.defenseAc(_targetPc.getAC().getAc());
        final int dg = _targetPc.getDg();
        final int er = _targetPc.getTotalER();

        // ===== 회피 보정(마안 3종): 10% 발동 시 hitChance -0.10 =====
        final double evadeBonus = L1AttackHelper.rollMaanEvadeBonus(_targetPc);

        // ===== 최종 명중률 =====
        final double hitChance = HitEvasionModel.calcHitChance(
                _hitRate,
                acDef,
                dg,
                er,
                isRanged,
                evadeBonus
        );

        // ===== 판정 =====
        return HitEvasionModel.rollHit(hitChance);
    }

	/** 플레이어 -> 엔피시 명중 판정 **/
	private boolean calcPcNpcHit() {
		if (_pc == null || _targetNpc == null || _target == null) return false;

		final int wType = _weaponType;
		final boolean isRanged = (wType == 20 || wType == 62);

		// ===== H(_hitRate) 계산 =====
		final int wpnHit = _weaponAddHit;
		final int wpnEnchant = _weaponEnchant;
			
		 final int loss = weapon != null ? weapon.get_durability() : 0;

		_hitRate = L1AttackHelper.calcPcHitH(_pc, isRanged, wpnHit, wpnEnchant, loss);
		
		// ===== AC =====
        final int acDef = HitEvasionModel.defenseAc(_targetNpc.getAC().getAc());
	    
		// ===== 최종 명중률 =====
		final double hitChance = HitEvasionModel.calcHitChance(
				_hitRate,
				acDef,
				0,
				0,
				isRanged,
				0.0
				);
					
		// ===== 판정 =====
		return HitEvasionModel.rollHit(hitChance);
	}

	/** 엔피시 -> 플레이어 명중 판정 **/
	private boolean calcNpcPcHit() {
		if (_npc == null || _targetPc == null || _target == null) return false;

		final boolean isRanged = _npc.getNpcTemplate().get_ranged() > 5;
		
		// ===== H(_hitRate) 계산 =====
		_hitRate = L1AttackHelper.calcNpcHitH(_npc, isRanged);
		
		  // ===== AC / DG / ER  =====
        final int acDef = HitEvasionModel.defenseAc(_targetPc.getAC().getAc());
        final int dg = _targetPc.getDg();
        final int er = _targetPc.getTotalER();

        // ===== 회피 보정(마안 3종): 10% 발동 시 hitChance -0.10 =====
        final double evadeBonus = L1AttackHelper.rollMaanEvadeBonus(_targetPc);

        // ===== 최종 명중률 =====
        final double hitChance = HitEvasionModel.calcHitChance(
                _hitRate,
                acDef,
                dg,
                er,
                isRanged,
                evadeBonus
        );
        // ===== 판정 =====
        return HitEvasionModel.rollHit(hitChance);
	}

	/** 엔피시 -> 엔피시 명중 판정 **/
	private boolean calcNpcNpcHit() {
		if (_npc == null || _targetNpc == null || _target == null) return false;

		final boolean isRanged = _npc.getNpcTemplate().get_ranged() > 5;
		
		// ===== H(_hitRate) 계산 =====
		_hitRate = L1AttackHelper.calcNpcHitH(_npc, isRanged);
		
		  // ===== AC  =====
        final int acDef = HitEvasionModel.defenseAc(_targetNpc.getAC().getAc());

        // ===== 최종 명중률 =====
        final double hitChance = HitEvasionModel.calcHitChance(
                _hitRate, acDef, 0,  0, isRanged, 0.0);
        // ===== 판정 =====
        return HitEvasionModel.rollHit(hitChance);
	}

	
	public int calcDamage() {

        if (isUnarmedPcAttack()
                && !L1PolyMorph.canAttackWithoutWeapon(_pc.getCurrentSpriteId())) {
            _isHit = false;
            return (_damage = 0);
        }
		final int calcType = _calcType;
		int dmg = 0;
		try {
			switch (calcType) {
			case PC_PC:
				dmg = calcPcPcDamage();
				break;
			case PC_NPC:
				dmg = calcPcNpcDamage();
				break;
			case NPC_PC:
				dmg = (_npc instanceof L1CompanionInstance || _npc instanceof MJDogFightInstance) ? do_calc_damage_companion(_npc) : calcNpcPcDamage();
				break;
			case NPC_NPC:
				dmg = (_npc instanceof L1CompanionInstance || _npc instanceof MJDogFightInstance) ? do_calc_damage_companion(_npc) : calcNpcNpcDamage();
				break;
			default:
				dmg = 0;
				break;
			}
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Attack damage calculation failed: type=" + calcType
                    + " target=" + _targetId, e);
            // Never commit the last hit or send a success proc for a failed calculation.
            _isHit = false;
            _isCritical = false;
            _edoryuDouble = _doubleBreak = _burning = _cycloneProc = false;
            _crash = _fury = _burningSlashProc = false;
            dmg = 0;
        }
        // src22 doll proc: roll once, only after a valid successful PC physical hit.
        if (dmg > 0 && _isHit && !isUnarmedPcAttack()
                && (calcType == PC_PC || calcType == PC_NPC)) {
            final boolean melee = _weaponType2 != 17
                    && !L1AttackHelper.isRangedByType1(_weaponType);
            dmg = MagicDollCombatProcs.applyOnHit(_pc, _target, dmg, melee);
        }
		if (dmg < 0) dmg = 0;

		_damage = dmg;
		return dmg;
	}

	/** 플레이어 -> 플레이어 대미지 판정 **/
	public int calcPcPcDamage() {	
		if (_pc == null || _targetPc == null) return 0;
        if (isUnarmedPcAttack()) return rollUnarmedDamage();

        // The keylink's attack damage is magic, not the normal weapon table.
        // Apply the magic PvP reduction once; do not add physical PvP bonuses.
        if (_weaponType2 == 17) {
            int magicDamage = calcKeylinkDamage(_targetPc, true);
            magicDamage = L1AttackHelper.applyTargetPcDamageMultipliers(
                    _pc, _targetPc, true, magicDamage);
            magicDamage = DamageReductionHelper.applyAllDamageReductions(
                    _targetPc, _pc, false, true, magicDamage);
            if (magicDamage <= 0) _isHit = false;
            return Math.max(0, magicDamage);
        }

		int dmg = calcPcDamageByTable(_targetPc, true);
		
		 // PVP 변신 데미지
	    // PvP polymorph bonus is already owned by current target receiveDamage.

	    // PVP weapon bonus
	    final int pvpWpn = _pc.getResistance().getPVPweaponTotalDamage();
	    if (pvpWpn != 0) dmg += pvpWpn;

        // All attack additions have been assembled. Armor Break owns one
        // integer floor here, before fixed target DR and deferred Burning.
        dmg = L1AttackHelper.applyArmorBreak(_pc, _targetPc,
                L1AttackHelper.isRangedByType1(_weaponType), dmg);

	    // PVP defense reduction
	    final int pvpDef = _targetPc.getResistance().getcalcPcDefense();
	    if (pvpDef != 0) dmg -= pvpDef;
	    
		 // 용 갑옷 처리 
	    
	    
	    

	    // 공통 DR (확률/고정/퍼센트)
	    dmg = DamageReductionHelper.applyAllDamageReductions(
	            _targetPc, _pc,
	           false,
	           false,
	            dmg, _burning, _fury
	    );
	    if (dmg <= 0) { _isHit = false; return 0; }
	    return dmg;
	}


	/** 플레이어 -> 엔피시 대미지 판정 **/
	private int calcPcNpcDamage() {
		if (_pc == null || _targetNpc == null) return 0;
        if (isUnarmedPcAttack()) return rollUnarmedDamage();
		
        // Keylink bypasses the STR/DEX physical damage table and its procs.
        int dmg = _weaponType2 == 17
                ? calcKeylinkDamage(_targetNpc, false)
                : calcPcDamageByTable(_targetNpc, false);
		
		// 몬스터 데미지 감소
		dmg -= calcNpcDamageReduction();
        if (_weaponType2 != 17)
            dmg = L1DamageCalcHelper.applyFinalPhysicalMultipliers(dmg, _burning, _fury);
		

	    if (dmg <= 0) {
	        _isHit = false;
	    }
		return dmg;
	}

    private boolean isUnarmedPcAttack() {
        return _pc != null && weapon == null;
    }

    private int rollUnarmedDamage() {
        // Hit probability is still owned by calcHit. Confirmed bare-hand hits
        // do not enter weapon/stat/DR/proc arithmetic.
        _isCritical = _edoryuDouble = _doubleBreak = _burning = false;
        _cycloneProc = _crash = _fury = _burningSlashProc = false;
        return ThreadLocalRandom.current().nextInt(1, 3);
    }

	/** 엔피시 -> 플레이어 대미지 판정 **/
	private int calcNpcPcDamage() {
		if (_npc == null || _targetPc == null) return 0;
			
		final boolean rangedNpc = L1AttackHelper.isRangedNpc(_npc.getNpcTemplate().get_ranged());
		int dmg = rollNpcBaseDamage(rangedNpc);
		
		 // 용 갑옷 처리 
	    
	    
	    
		
		// 타겟 PC 배율 후처리
		dmg = L1AttackHelper.applyTargetPcDamageMultipliers(_npc, _targetPc, rangedNpc, dmg);
		
		// DR (원거리/근거리 반영)
		dmg = DamageReductionHelper.applyAllDamageReductions(_targetPc, _npc, rangedNpc, false, dmg);
		
		addNpcPoisonAttack(_npc, _targetPc);
		
		if (dmg <= 0) { 
			_isHit = false; 
		}
		return dmg;
	}

	/** 엔피시 -> 엔피시 대미지 판정 **/
	private int calcNpcNpcDamage() {
		if (_targetNpc == null || _npc == null) return 0;
		
		final boolean rangedNpc = L1AttackHelper.isRangedNpc(_npc.getNpcTemplate().get_ranged());
		int dmg = rollNpcBaseDamage(rangedNpc);
		dmg -= calcNpcDamageReduction();
		
		addNpcPoisonAttack(_npc, _targetNpc);
		
		if (dmg <= 0) { 
			_isHit = false; 
		}
		return dmg;
	}

	/** Shared NPC physical base roll for PC and NPC targets. */
	private int rollNpcBaseDamage(boolean rangedNpc) {
		int status;
		if (rangedNpc) status = CalcStat.원거리대미지(_npc.getAbility().getTotalDex());
		else           status = CalcStat.근거리대미지(_npc.getAbility().getTotalStr());

		int dmg = status + ThreadLocalRandom.current().nextInt(Math.max(1, _npc.getLevel() / 2)) + 1;
		dmg += _npc.getDmgup();

		if (isUndeadDamage()) {
			dmg = (int) Math.floor(dmg * 1.1);
		}
		dmg = (int) (dmg * getLeverage() / 10.0);

		if (_npc.isWeaponBreaked()) {
			dmg >>= 1;
		}
		return dmg;
	}

	/** NPC의 대미지 감소에 대한 경감 **/
	private int calcNpcDamageReduction() {
		return _targetNpc.getNpcTemplate().get_damagereduction();
	}

	/** 무기의 재질과 축복에 의한 추가 대미지 산출 **/
	private int calcUndeadMaterialBonus() {	
		if (_targetNpc == null) return 0;
		final int undeadType = _targetNpc.getNpcTemplate().get_undead();
		final boolean isNight = L1GameTimeClock.getInstance().getGameTime().isNight();
		return L1UndeadBonusHelper.calcBonus(_pc,
				_weaponMaterial,
				_weaponBless,
				weapon,     
				undeadType,
				isNight);
	}
	
	/** 무기 속성 인챈트(속성추타) 추가대미지 산출 (PC-PC / PC-NPC 공용) **/
	private int calcWeaponAttrBonus() {
	    final int attrLevel = _weaponAttrLevel;
	    if (attrLevel <= 0) return 0;

	    final int arrowItemId = (_arrow != null) ? _arrow.getItemId() : 0;
	    final double v = L1WeaponAttrEnchantHelper.calcAttrEnchantDamage(
	            attrLevel,
	            arrowItemId,
	            _targetPc,
	            _targetNpc
	    );
	    return (v > 0) ? (int) v : 0;
	}
	
	/** NPC 언데드의 야간 공격력의 변화 **/
	private boolean isUndeadDamage() {
		 return L1AttackHelper.isUndeadDamage(_npc);
	}
	
    /** PC->NPC 타격 시 무기 손상 처리 */
	private void damageNpcWeaponDurability() {
		L1WeaponDurabilityHelper.tryDamageWeaponDurability(
			        _calcType,
			        _pc,
			        _targetNpc,
			        _weaponType,
			        _weaponBless, 
			        weapon          
			    );
	}

	 /** NPC의 독공격 부가 */
	private void addNpcPoisonAttack(L1Character attacker, L1Character target) {
		 NpcStatusAttackHelper.tryApplyNpcStatusAttack(_npc, attacker, target);
	}

    /**
     * Keylink-only damage: use a single weapon-stat-driven multi-dice rule,
     * without item-ID exceptions. Keep the 2009 keylink structure and the
     * 2016 INT/SP and PC high-MR multipliers separate from normal spells.
     */
    private int calcKeylinkDamage(final L1Character target, final boolean pvp) {
        if (weapon == null || target == null) return 0;

        // Multi-dice distribution is inferred from the weapon's displayed
        // damage only. No hard-coded keylink IDs or per-weapon overrides.
        final int displayedDamage = TargetSizeHelper.isSmall(pvp, target)
                ? _weaponSmall : _weaponLarge;
        if (displayedDamage <= 0) return 0;
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        final int dice = keylinkDice(displayedDamage, rng);

        // SP already includes MagicLevel + MagicBonus: subtract both once.
        final int bonusSp = _pc.getAbility().getSp() - _pc.getAbility().getTrueSp();
        final int intSp = _pc.getAbility().getTotalInt() + bonusSp;
        final double a = Math.max(0.0,
                Math.floor((1.0 + (3.0 / 32.0) * (intSp - 12)) * 100.0) / 100.0);
        int scaled = (int) Math.floor(dice * a);

        // Keylink magic critical is 1.25x, not the spell critical's 1.5x.
        int critChance = CalcStat.마법치명타(_pc.getAbility().getTotalInt())
                + (int) (_pc.getBaseMagicCritical() * Config.CHARACTER_MAGICCRI_RATE);
        if (_pc.hasSkillEffect(L1SkillId.LIND_MAAN)
                || _pc.hasSkillEffect(L1SkillId.SHAPE_MAAN)
                || _pc.hasSkillEffect(L1SkillId.LIFE_MAAN)) {
            critChance++;
        }
        _isCritical = critChance > 0 && rng.nextInt(100) < Math.min(100, critChance);
        if (_isCritical) scaled = (int) Math.floor(scaled * 1.25);

        // The 2009/2021 keylink experiments put enchant and additional magic
        // damage inside MR, unlike the 2016 general-spell MD-after-MR equation.
        // getBaseMagicDmg() and getTotalMagicHitup() already contain
        // the cached class/level bonuses; never add them twice.
        final int magicBonus = _pc.getBaseMagicDmg()
                + weapon.getItem().getMagicDmgModifier()
                + _weaponAddDmg; // original weapon bonus + weapon_damage DB snapshot
        final int beforeMr = Math.max(0, scaled + _weaponEnchant + magicBonus);
        final int mh = _pc.getTotalMagicHitup();
        final int mrMinusMh = target.getResistance().getEffectedMrBySkill() - mh;
        final int defenderType = target instanceof L1PcInstance defender ? defender.getType() : -1;
        final double b = keylinkMrMultiplier(mrMinusMh, defenderType,
                target.getAbility().getTotalWis());

        int damage = (int) Math.floor(beforeMr * b);
        // The element enchant is applied after MR and filtered by element resist.
        damage += calcWeaponAttrBonus();
        return Math.max(0, damage);
    }

    /**
     * Item-ID-independent keylink dice approximation from weapon display damage.
     *
     * These common damage bands reproduce measured multi-dice distributions,
     * without per-item branches or allocations on the attack path. An attack
     * value alone cannot identify every historical keylink's internal dice.
     */
    private static int keylinkDice(final int displayedDamage, final ThreadLocalRandom rng) {
        if (displayedDamage <= 0) return 0;
        if (displayedDamage < 10) return rng.nextInt(1, displayedDamage + 1);
        if (displayedDamage <= 26) {
            // Display 24 -> 2d5+14; 26 -> 2d5+16.
            return (displayedDamage - 10) + rng.nextInt(1, 6) + rng.nextInt(1, 6);
        }
        if (displayedDamage <= 28) {
            // Display 28 -> 2d5+14, matching recorded damage values when
            // the hidden class level bonus is applied once. The original
            // researcher also considered +15; this choice is an approximation.
            return 14 + rng.nextInt(1, 6) + rng.nextInt(1, 6);
        }
        if (displayedDamage <= 31) {
            // Display 30 -> 2d5+17.
            return (displayedDamage / 2 + 2) + rng.nextInt(1, 6) + rng.nextInt(1, 6);
        }
        if (displayedDamage <= 34) {
            // Display 32 -> 2d8+14 (post-2017 keylink revisions).
            return (displayedDamage / 2 - 2) + rng.nextInt(1, 9) + rng.nextInt(1, 9);
        }
        if (displayedDamage <= 35) {
            // Display 35 -> 3d6+14.
            return ((displayedDamage + 1) / 2 - 4)
                    + rng.nextInt(1, 7) + rng.nextInt(1, 7) + rng.nextInt(1, 7);
        }
        if (displayedDamage <= 37) {
            // Display 36 -> 4d5+13; wider curve with the same mean.
            return (displayedDamage / 2 - 5)
                    + rng.nextInt(1, 6) + rng.nextInt(1, 6)
                    + rng.nextInt(1, 6) + rng.nextInt(1, 6);
        }
        // Display 38 -> 3d7+14.
        return (displayedDamage / 2 - 5)
                + rng.nextInt(1, 8) + rng.nextInt(1, 8) + rng.nextInt(1, 8);
    }

    private static double keylinkMrMultiplier(final int mrMinusMh,
                                               final int defenderType, final int defenderWis) {
        if (defenderType >= 0 && defenderType <= 7) {
            // The 2016 experimental curve is measured for MR-MH >= 10 only.
            // Clamp to <= 1 so MR cannot amplify magic damage, and join the
            // low-MR region continuously to avoid a damage increase at 9->10.
            final double c = keylinkClassFactors[defenderType]
                    - 0.02 * (Math.max(0, defenderWis) / 2);
            final int x = Math.max(0, mrMinusMh);
            // The 2021 sapphire observations at MR-MH=-1 and 2 have exactly
            // the same damage distribution, so keep this measured plateau.
            if (x <= 2) return 1.0;
            if (x < 10) {
                // Unmeasured 3..9: bounded, monotone connection to B(10).
                // Do not use the incompatible pre-2016 MR law at this seam.
                final double b10 = Math.max(0.0,
                        Math.min(1.0, Math.floor(c * Math.pow(10.0, -0.3) * 100.0) / 100.0));
                return Math.max(0.0,
                        Math.floor((1.0 - (1.0 - b10) * ((x - 2) / 8.0)) * 100.0) / 100.0);
            }
            final double b2016 = Math.floor(c * Math.pow(x, -0.3) * 100.0) / 100.0;
            return Math.max(0.0, Math.min(1.0, b2016));
        }
        // NPC: preserve the existing 2009 MR fallback; no PC class factor.
        final int effectiveMr = Math.max(0, mrMinusMh);
        if (effectiveMr <= 100) return 1.0 - 0.01 * (effectiveMr / 2);
        return Math.max(0.0, 0.6 - 0.01 * (effectiveMr / 10));
    }

    private int calcPcDamageByTable(final L1Character target, final boolean pvp) {
        if (_pc == null || target == null) return 0;
        
        // -------------------------
        // 0) 무기 타입/대상 크기
        // -------------------------
        final int type1 = _weaponType;           // _weaponType
        final int type2 = _weaponType2;
        final boolean ranged = (type1 == 20 || type1 == 62);

        final boolean targetIsSmall = TargetSizeHelper.isSmall(pvp, target); // PC면 무조건 small 취급
        // -------------------------
        // 1) A~J 구성 본서버 기준
        // -------------------------
        // A: 기본 공격력 random min/max
        int A_min = 0;
        int A_max = 0;

        final int base = targetIsSmall ? _weaponSmall : _weaponLarge;
        A_min = (base > 0 ? 1 : 0);
        A_max = (base > 0 ? base : 0);
        
    	final int itemId = _weaponId;
        // B: 무기 고유 추가 타격
        final int B = _weaponAddDmg;
        // C: 인챈트 수
        final int C = _weaponEnchant;
        // E: 캐릭에 대한 마법 효과
        int E = ranged ? _pc.getBowDmgup() : _pc.getDmgup();
        
        // 화살데미지 처리
        if (type1 == 20 && _arrow != null && _arrow.getItem() != null) {
        	E += _arrow.getItem().getDmgModifier();
        } else if (type1 == 62 && _sting != null && _sting.getItem() != null) {
        	E += _sting.getItem().getDmgModifier();
        }
        // 무형화살 데미지
        if(type1 == 20 && _arrow == null && (itemId == 190 || itemId == 10000 || itemId == 202011)) {
            E += 2;
        }   
        // F: STR/DEX 보너스
        final int F = _statusDamage;
        // G: 클래스/레벨 보너스 
        final int G = ranged ? _pc.getBaseBowDmgByLevelClass() : _pc.getBaseMeleeDmgByLevelClass();
        // H: 언데드 보너스 (재질 실버/미스릴/오리 등 1~20 낮 / 1~10 밤)
        final int H = calcUndeadMaterialBonus(); 
        // 무기 속성 파트
        final int weaponAttr = calcWeaponAttrBonus();             	
        // -------------------------
        // 2) 확률/발동 (더블브레이크/이도더블/버닝)
        // -------------------------		
	    final int loss = (weapon != null) ? weapon.get_durability() : 0;
		
        final boolean hasSkill = _pc.hasSkillEffect(L1SkillId.QUAKE)
        		|| _pc.hasSkillEffect(L1SkillId.ELEMENTAL_FIRE)
        		|| _pc.hasSkillEffect(L1SkillId.BURNING_SPIRIT)
        		|| _pc.hasSkillEffect(L1SkillId.BLOW_ATTACK)
        		|| _pc.hasSkillEffect(L1SkillId.CYCLONE);
        
        final int flags = L1SkillProcHelper.rollAllFlags(
                _pc, ranged, type1, itemId, _weaponDoubleDmgChance, hasSkill
        );
        
        _doubleBreak = L1SkillProcHelper.hasDoubleBreak(flags);
        _burning     = L1SkillProcHelper.hasBurning(flags);      
        _cycloneProc = L1SkillProcHelper.hasCycloneProc(flags);
  
        final boolean showCriticalEffect = L1SkillProcHelper.hasNormalCrit(flags);


        // 최종 크리 트리거
        final boolean critProc = showCriticalEffect;
        _isCritical = critProc;

        final L1SkillProcHelper.WeaponProcMode mode = L1SkillProcHelper.getMode(flags);
        
        L1DamageCalcHelper.WeaponCritMode critMode = L1SkillProcHelper.toCritMode(mode);
        
        _edoryuDouble = (critMode == L1DamageCalcHelper.WeaponCritMode.DUAL_DOUBLE);

        final boolean soulFlameMax = L1SkillProcHelper.isSoulOfFlameMax(_pc, ranged);
        final boolean forceMaxRandom = critProc || soulFlameMax;

        int dmg = L1DamageCalcHelper.calcDamage(A_min, A_max, B, C, E, F, G, H,
                loss, weaponAttr, critMode, _doubleBreak, false, forceMaxRandom, weapon != null
        		);   

        // 일루션 아바타, 큐브아바타 데미지 +10처리
        dmg = L1AttackHelper.applyAvatarPhysicalAtkPlus10(_pc, dmg);

        // 타겟 PC 배율 후처리
        dmg = L1AttackHelper.applyTargetPcStatusMultipliers(_targetPc, dmg);
        
        _crash = _fury = false;
        final int cfFlags = L1SkillProcHelper.rollCrashFuryFlags(_pc, ranged);

        if (L1SkillProcHelper.hasCrash(cfFlags)) {
        	final int crashDmg = L1SkillProcHelper.crashDamageByLevel(_pc.getLevel());
        	dmg += crashDmg;             
        	_crash = true;
        	if (L1SkillProcHelper.hasFury(cfFlags)) {
                // Fury doubles the whole physical result after fixed DR,
                // rather than adding another two Crash bonuses here.
        		_fury = true;
        	}
        }

        _burningSlashProc = false;      
        // 버닝슬래시 (근접 +7, 소모)
        final int bsAdd = L1AttackHelper.consumeBurningSlashAddDamage(_pc, ranged);
        if (bsAdd != 0) {
        	dmg += bsAdd;
        	_burningSlashProc = true;
        }
 	    // 마안 처리
        dmg += L1AttackHelper.rollMaanPhysicalBonusDamage(_pc, ranged, L1SkillId.VALA_MAAN, L1SkillId.LIFE_MAAN);
            
        // 체인소드(약점노출) 
        dmg += WeakExposeHelper.applyPowExposeOnHit(_pc, target, weapon, type2, dmg);

        
       
        return dmg;
    }
    
    
	private boolean do_calc_hit_companion(L1Character attacker) {
		int hit = MJCompanionAttackHandler.do_calculate_hit(this, attacker, _target);
		hit += MJDogFightAttackHandler.do_calculate_hit(this, attacker, _target);
		if (attacker instanceof MJDogFightInstance && _target instanceof MJDogFightInstance) {
			if (((MJDogFightInstance) attacker).getTotalPrice() > ((MJDogFightInstance) _target).getTotalPrice()) {
				hit -= MJDogFightSettings.FAKE_HIT;
			}
		}
		boolean isHit = MJRnd.isWinning(100, hit);
		set_hit_rate(hit);
		set_hit(isHit);
		MJCompanionAttackHandler.on_hit_notify(this, attacker, _target, isHit);
		MJDogFightAttackHandler.on_hit_notify(this, attacker, _target, isHit);
		return isHit;
	}
	private int do_calc_damage_companion(L1Character attacker) {
		int damage = MJCompanionAttackHandler.do_calculate_damage(this, attacker, _target);
		damage += MJDogFightAttackHandler.do_calculate_damage(this, attacker, _target);
		if (attacker instanceof MJDogFightInstance && _target instanceof MJDogFightInstance) {
			if (((MJDogFightInstance) attacker).getTotalPrice() > ((MJDogFightInstance) _target).getTotalPrice()) {
				damage -= MJDogFightSettings.FAKE_DAMAGE;
			}
		}
		if (damage <= 0)
			_isHit = false;
		return damage;
	}
	public void addPcPoisonAttack(L1Character attacker, L1Character target) {
		int chance = _random.nextInt(100) + 1;
		if ((_weaponId == 13 || _weaponId == 44 // FOD, 고대의 다크 에르프 소도
				|| (_weaponId != 0 && _pc.hasSkillEffect(L1SkillId.ENCHANT_VENOM))) // 인챈트
																			// 베놈
				&& chance <= 10) {
			L1DamagePoison.doInfection(attacker, target, 3000, 30, false, 30000);
		}
	}
    public int calcDamage(boolean inferno) {
        final int damage = calcDamage();
        if (inferno) _damage = Math.max(0, damage / 2);
        return _damage;
    }



    private boolean isSameCombatTeamBlocked() {
        return _pc != null && _targetPc != null
                && _pc.get_current_combat_id() == _targetPc.get_current_combat_team_id()
                && _pc.get_current_combat_team_id() == _targetPc.get_current_combat_team_id();
    }

    private boolean isSpecialNpcImmune() {
        if (_pc == null || _targetNpc == null || _targetNpc.getNpcTemplate() == null) return true;
        final int id = _targetNpc.getNpcTemplate().get_npcId();
        if (_targetNpc.getHiddenStatus() != L1NpcInstance.HIDDEN_STATUS_NONE) return true;
        if (id == 8500138) return true;
        if (id >= 45912 && id <= 45915 && !_pc.hasSkillEffect(L1SkillId.STATUS_HOLY_WATER)) return true;
        if (id == 45916 && !_pc.hasSkillEffect(L1SkillId.STATUS_HOLY_MITHRIL_POWDER)) return true;
        if (id == 45941 && !_pc.hasSkillEffect(L1SkillId.STATUS_HOLY_WATER_OF_EVA)) return true;
        if (id >= 46068 && id <= 46091 && _pc.getCurrentSpriteId() == 6035) return true;
        if (id >= 46092 && id <= 46106 && _pc.getCurrentSpriteId() == 6034) return true;
        final int gfx = _targetNpc.getNpcTemplate().get_gfxid();
        if (gfx == 7684 && !_pc.hasSkillEffect(L1SkillId.PAP_FIVEPEARLBUFF)) return true;
        if (gfx == 7805 && !_pc.hasSkillEffect(L1SkillId.PAP_MAGICALPEARLBUFF)) return true;
        if (gfx == 7720) return true;
        if ((id == MJRaidLoadManager.MRS_SP_VALAKAS_HPABSORB_ID
                || id == MJRaidLoadManager.MRS_SP_VALAKAS_MPABSORB_ID) && !_pc.isValakasProduct()) return true;
        return false;
    }


	public void action() {
		try {
			if (_calcType == PC_PC || _calcType == PC_NPC) {
				actionPc();
			} else if (_calcType == NPC_PC || _calcType == NPC_NPC) {
				actionNpc();
			}
        } catch (Exception e) {
            // Packet/animation failure must not silently disappear; damage commit
            // is still executed by the caller, preserving the combat ordering.
            LOG.log(Level.WARNING, "Attack animation failed: type=" + _calcType
                    + " target=" + _targetId, e);
        }
    }

    // ●●●● 플레이어의 공격 모션 송신 ●●●●
    private void actionPc() {
        // A rejected bow-only morph must not emit even a missed fist swing.
        if (isUnarmedPcAttack()
                && !L1PolyMorph.canAttackWithoutWeapon(_pc.getCurrentSpriteId())) {
            _isHit = false;
            return;
        }
        final int spriteId = _pc.getCurrentSpriteId();
        _pc.setHeading(_pc.targetDirection(_targetX, _targetY));

        if (_weaponType == 20) {
            if (!_pc.glanceCheck(_targetX, _targetY)
                    || !_target.glanceCheck(_pc.getX(), _pc.getY())) {
                SC_UseArrowSkill.sendAndBroadcast(_pc, _targetId, ActionCodes.ACTION_BowAttack,
                        _targetX, _targetY, false);
                _isHit = false;
                return;
            }
            int gfx;
            if (_arrow != null) {
                if (!_pc.noPlayerCK) _pc.getInventory().removeItem(_arrow, 1);
                gfx = getArrowGfxId(spriteId);
            } else {
                gfx = L1AttackPacketHelper.arrowGfxByWeaponIdWhenNoArrow(_weaponId);
                if (gfx < 0) {
                    SC_UseArrowSkill.sendAndBroadcast(_pc, _targetId, ActionCodes.ACTION_BowAttack,
                            _targetX, _targetY, _isHit);
                    return;
                }
            }
            if (_cycloneProc) sendProcSound(17557);
            if (_isHit && _isCritical) {
                SC_AttackCritical.send(_pc.getNetConnection(), _pc, _targetId,
                        _targetX, _targetY, _weaponType, true);
                SC_AttackCritical.broadcast(_pc, _targetId, _targetX, _targetY, _weaponType, true);
                return;
            }
            SC_UseArrowSkill.sendAndBroadcast(_pc, _targetId, gfx, _targetX, _targetY, _isHit);
            return;
        }

        if (_weaponType == 62 && _sting != null) {
            if (!_pc.noPlayerCK) _pc.getInventory().removeItem(_sting, 1);
            if (_cycloneProc) sendProcSound(17557);
            if (_isHit && _isCritical) {
                SC_AttackCritical.send(_pc.getNetConnection(), _pc, _targetId,
                        _targetX, _targetY, _weaponType, true);
                SC_AttackCritical.broadcast(_pc, _targetId, _targetX, _targetY, _weaponType, true);
                return;
            }
            SC_UseArrowSkill.sendAndBroadcast(_pc, _targetId,
                    L1AttackPacketHelper.stingGfxBySprite(spriteId), _targetX, _targetY, _isHit);
            return;
        }

        if (!_isHit) {
            if (_targetId > 0) {
                SC_AttackMissPacket.send(_pc.getNetConnection(), _pc, _targetId);
                SC_AttackMissPacket.broadcast(_pc, _targetId);
            } else {
                SC_AttackPacket.send(_pc.getNetConnection(), _pc, 0, ActionCodes.ACTION_Attack);
                SC_AttackPacket.broadcast(_pc, 0, ActionCodes.ACTION_Attack);
            }
            return;
        }

        if (_doubleBreak && _burning) sendProcSound(6532);
        else if (_burning) sendProcSound(7727);
        if (_crash) sendProcSound(12487);
        if (_fury) sendProcSound(12489);
        if (_burningSlashProc) sendProcSound(6591);

        // Keylink critical is already reflected in _damage. Always render the
        // keylink magic hit (never replace it with a physical critical swing).
        if (_weaponType2 == 17) {
            final int gfx = (_weaponId == 504) ? 6983
                    : (_weaponId == 1120 || _weaponId == 202012 || _weaponId == 7000223
                    || _weaponId == 283 || _weaponId == 123008) ? 13923 : 7049;
            CommonUtil.sendKeyLinkTo(_pc, _targetId, gfx, _damage,
                    _pc.getHeading(), _targetX, _targetY);
            return;
        }
        // Critical damage was already accounted for by src22's damage formula;
        // send only the animation here (never multiply the damage twice).
        if (_isCritical || _edoryuDouble) {
            SC_AttackCritical.send(_pc.getNetConnection(), _pc, _targetId,
                    _weaponType2 == 17 ? 91 : _weaponType, Sweapon != null);
            SC_AttackCritical.broadcast(_pc, _targetId,
                    _weaponType2 == 17 ? 91 : _weaponType, Sweapon != null);
            return;
        }
        SC_AttackPacket.send(_pc.getNetConnection(), _pc, _targetId, ActionCodes.ACTION_Attack, _attackType);
        SC_AttackPacket.broadcast(_pc, _targetId, ActionCodes.ACTION_Attack, _attackType);
    }

    private void sendProcSound(int gfxId) {
        final long packed = SC_SkillSound.pack(_targetId, gfxId);
        SC_SkillSound.sendPacked(_pc, packed);
        SC_SkillSound.broadcastFastPacked(_pc, packed);
    }

    /** 변신 스프라이트별 화살 그래픽 ID (활 + 스트링 공용) */
    private static int getArrowGfxId(int spriteId) {
        switch (spriteId) {
        case 21132: case 21136: return 21427;
        case 21233:             return 20003;
        case 19514: case 21209: case 21258: case 21405: case 21409: return 20542;
        case 20528:             return 20005;
        case 18398:             return 8904;
        case 18391:             return 8916;
        case 18394:             return 13656;
        case 21140:             return 19999;
        case 21226:             return 20001;
        case 7967:              return 7972;
        case 11402: case 8900:  return 8904;
        case 11406: case 8913:  return 8916;
        case 13631:             return 13656;
        case 13635:             return 13658;
        case 15814:             return 12243;
        case 16002: case 16074: return 16078;
        default:                return 2989;
        }
    }
    
	// ●●●● NPC의 공격 모션 송신 ●●●●
    private void actionNpc() {
    	int _npcObjectId = _npc.getId();
    	int bowActId = 0;
    	int actId = 0;
    	int heading = _npc.targetDirection(_targetX, _targetY);

    	if (_npc.getHeading() != heading) {
    		_npc.setHeading(heading);
    	}

    	boolean isLongRange =
    			_npc.getLocation()
    				.getTileLineDistance(_target.getLocation()) > 1;

    	bowActId = _npc.getNpcTemplate().getBowActId();

    	actId = (getActId() > 0)
    			? getActId()
    			: ActionCodes.ACTION_Attack;

    	if (isLongRange && bowActId > 0) {
    		SC_UseArrowSkill.broadcast(
    				_npc,
    				_targetId,
    				bowActId,
    				_targetX,
    				_targetY,
    				_isHit);
    		return;
    	}

		if (getGfxId() > 0) {
			SC_UseAttackSkill.broadcast(
					_npc,
					_target,
					_npcObjectId,
					getGfxId(),
					_targetX,
					_targetY,
					actId);
		} else {
			SC_AttackPacketForNpc.broadcast(
					_npc,
					_target,
					_npcObjectId,
					actId);
		}
    }
    
	// 나는 일 도구(화살, 스팅)가 미스였다고 나무의 궤도를 계산
	/* ■■■■■■■■■■■■■■■ 계산 결과 반영 ■■■■■■■■■■■■■■■ */
	public void commit(int damage) {
		if (damage > 0) {
			try {
				if (_calcType == PC_PC || _calcType == NPC_PC) {
					commitPc(damage);
				} else if (_calcType == PC_NPC || _calcType == NPC_NPC) {
					commitNpc(damage);
				}
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Attack HP commit failed: type=" + _calcType
                        + " target=" + _targetId, e);
                throw e;
            }
		}
		// 대미지치 및 명중율 확인용 메세지
		if (!Config.ALT_ATKMSG) {
			return;
		}
		if (_target == null)
			return;
		if (_pc != null && _pc.isGm()) {
			StringBuffer sb = new StringBuffer();

			sb.append("\\fT<데미지> [").append(damage).append(" DMG ==][HP ").append(_target.getCurrentHp()).append("]");

			SC_SystemMessage.send(_pc, sb.toString());
			sb = null;
		}
		if (_targetPc != null && _targetPc.isGm()) {
			StringBuffer sb = new StringBuffer();
			sb.append("\\fY<데미지> [").append(damage).append(" DMG ==][HP ").append(_target.getCurrentHp()).append("]");
			SC_SystemMessage.send(_targetPc, sb.toString());
			sb = null;
		}
	}

	public void commit() {
		if (_isHit) {
			try {
				if (_calcType == PC_PC || _calcType == NPC_PC) {
					commitPc(0);
				} else if (_calcType == PC_NPC || _calcType == NPC_NPC) {
					commitNpc(0);
				}
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Attack HP commit failed: type=" + _calcType
                        + " target=" + _targetId, e);
                throw e;
            }
		}

		// 대미지치 및 명중율 확인용 메세지
		if (!Config.ALT_ATKMSG) {
			return;
		}
		if (_target == null)
			return;
		if (_pc != null && _pc.isGm()) {
			StringBuffer sb = new StringBuffer();
			sb.append("\\fT[").append(_pc.getName()).append("] ==> [").append(_target.getName()).append("][== ")
					.append(_damage).append(" DMG ==][HP ").append(_target.getCurrentHp()).append("]");
			SC_SystemMessage.send(_pc, sb.toString());
			sb = null;
		}
		if (_targetPc != null && _targetPc.isGm()) {
			StringBuffer sb = new StringBuffer();
			sb.append("\\fY[").append(_target.getName()).append("] <== [")
					.append((_pc == null ? _npc.getName() : _pc.getName())).append("][== ").append(_damage)
					.append(" DMG ==][HP ").append(_target.getCurrentHp()).append("]");
			SC_SystemMessage.send(_targetPc, sb.toString());
			sb = null;
		}
	}

	// ●●●● 플레이어에 계산 결과를 반영 ●●●●
	private void commitPc(int dmg) {
		if (_calcType == PC_PC) {
			if (MJCommons.isUnbeatable(_targetPc))
				_damage = 0;
            if (isUnarmedPcAttack()) _targetPc.receiveUnarmedPhysicalDamage(_pc, _damage + dmg);
            else _targetPc.receiveDamage(_pc, _damage + dmg);
		} else if (_calcType == NPC_PC) {
			if (MJCommons.isUnbeatable(_targetPc))
				_damage = 0;
			_targetPc.receiveDamage(_npc, _damage + dmg);
		}
	}

	private void commitNpc(int dmg) {
		if (_calcType == PC_NPC) {
			if (MJCommons.isUnbeatable(_targetNpc))
				_damage = 0;
			damageNpcWeaponDurability(); // 무기를 손상시킨다.
			_targetNpc.receiveDamage(_pc, _damage + dmg);
		} else if (_calcType == NPC_NPC) {
			if (MJCommons.isUnbeatable(_targetNpc))
				_damage = 0;
			_targetNpc.receiveDamage(_npc, _damage + dmg);
		}
	}

	/* ■■■■■■■■■■■■■■■ 카운터 배리어 ■■■■■■■■■■■■■■■ */
	// ■■■■ 카운터 배리어시의 공격 모션 송신 ■■■■
	public void actionCounterBarrier() {
        actionCounterBarrier(true);
    }

    public void actionCounterBarrier(boolean animateNormalAttack) {
		if (_calcType == PC_PC) {
			if (_pc == null)
				return;
			if (_targetPc.hasSkillEffect(L1SkillId.SHOCK_STUN) || _targetPc.hasSkillEffect(L1SkillId.EMPIRE)
					|| _targetPc.hasSkillEffect(L1SkillId.EARTH_BIND)) {
				return;
			}
			_pc.setHeading(_pc.targetDirection(_targetX, _targetY)); // 방향세트
			SC_AttackMissPacket.send(_pc.getNetConnection(), _pc, _targetId);
			SC_AttackMissPacket.broadcast(_pc, _targetId);
			SC_DoActionGFX.send(_pc.getNetConnection(), _pc.getId(), ActionCodes.ACTION_Damage);
			SC_DoActionGFX.broadcast(_pc, ActionCodes.ACTION_Damage);

			if (_targetPc.isPassive(MJPassiveID.COUNTER_BARRIER_VETERAN.toInt())) {
				_targetPc.send_effect(17220);
			} else {
				_targetPc.send_effect(10710);
			}
		} else if (_calcType == NPC_PC) {
			if (_npc == null || _target == null)
				return;
            // NPC physical AoE must not resend the attack animation for every secondary target.
            if (animateNormalAttack) {
                int actId = getActId() > 0 ? getActId() : ActionCodes.ACTION_Attack;
                _npc.setHeading(_npc.targetDirection(_targetX, _targetY));
                if (getGfxId() > 0) {
                    SC_UseAttackSkill.broadcast(_npc, _target, _npc.getId(), getGfxId(), _targetX, _targetY, actId, 0);
                } else {
                    SC_AttackMissPacket.broadcast(_npc, _targetId, actId);
                }
                SC_DoActionGFX.broadcast(_npc, ActionCodes.ACTION_Damage);
            }
			if (_targetPc.isPassive(MJPassiveID.COUNTER_BARRIER_VETERAN.toInt())) {
				_targetPc.send_effect(17220);
			} else {
				_targetPc.send_effect(10710);
			}
		}
	}

	public void NpcactionCounterBarrier(L1Character attacker, L1PcInstance pc) {
		if (pc == null || _target == null)
			return;
		int actId = 0;
		pc.setHeading(pc.targetDirection(_targetX, _targetY)); // 방향세트
		if (getActId() > 0) {
			actId = getActId();
		} else {
			actId = ActionCodes.ACTION_Attack;
		}
		if (getGfxId() > 0) {
			SC_UseAttackSkill.broadcast(pc, _target, pc.getId(), getGfxId(), _targetX, _targetY, actId, 0);
		} else {
			SC_AttackMissPacket.broadcast(pc, _targetId, actId);
		}
		SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage);
		attacker.send_effect(17220);
	}

	// ■■■■ 모탈바디 발동시의 공격 모션 송신 ■■■■
	public void actionMortalBody() {
        actionMortalBody(true);
    }

    public void actionMortalBody(boolean animateNormalAttack) {
		if (_calcType == PC_PC) {
			if (_pc == null || _target == null)
				return;
			_pc.setHeading(_pc.targetDirection(_targetX, _targetY)); // 방향세트
			SC_UseAttackSkill.sendAndBroadcast(
					_pc,
					_target.getId(),
					6519,
					_targetX,
					_targetY,
					ActionCodes.ACTION_Attack,
					false);
			SC_DoActionGFX.send(_pc.getNetConnection(), _pc.getId(), ActionCodes.ACTION_Damage);
			SC_DoActionGFX.broadcast(_pc, ActionCodes.ACTION_Damage);
		} else if (_calcType == NPC_PC) {
			if (_npc == null || _target == null)
				return;
			_npc.setHeading(_npc.targetDirection(_targetX, _targetY)); // 방향세트
			SC_SkillSound.broadcastFast(_npc, _target.getId(), 6519);
			if (animateNormalAttack) SC_DoActionGFX.broadcast(_npc, ActionCodes.ACTION_Damage);
		}
	}

    // The hit's weapon snapshot determines the Titan category; ARMOR_BRAKE
    // affects Counter Barrier but must not turn a melee hit into Blitz.
    public boolean isTitanPhysicalHit() {
        return _calcType == NPC_PC || _calcType == NPC_NPC
                || ((_calcType == PC_PC || _calcType == PC_NPC)
                    && _weaponType2 != 17 && _weaponType2 != 19);
    }

    public boolean isRangedPhysicalHit() {
        if (_calcType == PC_PC || _calcType == PC_NPC)
            return _weaponType == 20 || _weaponType == 62;
        if (_calcType == NPC_PC || _calcType == NPC_NPC)
            return _npc != null && _npc.getNpcTemplate() != null
                    && L1AttackHelper.isRangedNpc(_npc.getNpcTemplate().get_ranged());
        return false;
    }

	// ■■■■ 상대의 공격에 대해서 카운터 배리어가 유효한가를 판별 ■■■■
	public boolean isShortDistance() {
		boolean isShortDistance = true;
		if (_calcType == PC_PC) {
			if (_weaponType == 20 || _weaponType == 62 || _weaponType2 == 17 || _weaponType2 == 19
					|| _pc.hasSkillEffect(L1SkillId.ARMOR_BRAKE)) {
				isShortDistance = false;
			}
        } else if (_calcType == NPC_PC) {
            isShortDistance = !isRangedPhysicalHit();
        }
		return isShortDistance;
	}

	// ■■■■ 카운터 배리어의 대미지를 반영 ■■■■
	public void commitCounterBarrier() {
		int damage = calcCounterBarrierDamage();

		if (damage == 0) {
			return;
		}
		if (_calcType == PC_PC) {
			_pc.receiveCounterBarrierDamage(_targetPc, damage);
		} else if (_calcType == NPC_PC) {
			_npc.receiveCounterBarrierDamage(_targetPc, damage);
		} else if (_calcType == PC_NPC) {
			_pc.receiveCounterBarrierDamage(_targetPc, damage);
		}
	}

	// ■■■■ 모탈바디의 대미지를 반영 ■■■■
	public void commitMortalBody() {
        if (_targetPc == null) return;
        final L1Character attacker = _pc != null ? _pc : _npc;
        if (attacker == null) return;
        final int damage = MortalBodyProcEngine.calcReflectDamage(attacker, _targetPc);
        if (damage > 0) attacker.receiveDamage(_targetPc, damage);
    }

	// ●●●● 카운터 배리어의 대미지를 산출 ●●●●
	private int calcCounterBarrierDamage() {
		double damage = 0;
		L1ItemInstance weapon = null;
		weapon = _targetPc.getWeapon();

		if (weapon != null) {
			if (weapon.getItem().getType() == 3) {
				damage = Math.round(
						(weapon.getItem().getDmgLarge() + weapon.getEnchantLevel() + weapon.getItem().getDmgModifier())
								* Config.Counter);
			}

		}

		if (weapon != null && _targetPc.hasSkillEffect(L1SkillId.카배마스터)) {

			int 회복 = (int) ((double) Math.round(
					(weapon.getItem().getDmgLarge() + weapon.getEnchantLevel() + weapon.getItem().getDmgModifier())
							* Config.카배회복량));
			int 최종회복 = _random.nextInt(30) + 회복;
			_targetPc.setCurrentHp(_targetPc.getCurrentHp() + 최종회복);
			SC_SkillSound.send(_targetPc, _targetPc.getId(), 6590);
			SC_SkillSound.broadcastFast(_targetPc, _targetPc.getId(), 6590);
			if (_targetPc.마스터) {
				SC_SystemMessage.send(_targetPc, "\\f9카운터배리어(마스터): HP \\f3" + 최종회복 + "\\f9을 회복하였습니다.");
			}

		}
		return (int) damage;
	}

	// ●●●● 전사 타이탄 대미지를 산출 ●●●●
	protected static void broadcast(L1Character c, ServerPacket pck) {
		c.sendPackets(pck, false);
		c.broadcastPacket(pck);
	}

	public void set_hit_rate(int rate) {
		_hitRate = rate;
	}

	public void set_hit(boolean hit) {
		_isHit = hit;
	}

	public void set_is_critical(boolean is_critical) {
		_isCritical = is_critical;
	}
	
	/**
	 * 요정 인페르노 이펙트 및 모션 실행
	 */
	public void actionInferno(int step) {
        actionInferno(step, true);
    }

    public void actionInferno(int step, boolean animateNormalAttack) {
	    // step 1: 17561, 2: 17563, 3: 17565, 4: 17567
        final int gfxId;
        switch (step) {
        case 1: gfxId = 17561; break;
        case 2: gfxId = 17563; break;
        case 3: gfxId = 17565; break;
        case 4: gfxId = 17567; break;
        default: return;
        }

	    if (_calcType == PC_PC || _calcType == PC_NPC) {
	        // 공격자(PC) 처리
	        _pc.setHeading(_pc.targetDirection(_targetX, _targetY));
	        
	        // 패킷 송신 통합 (나와 주변에 보냄)
	        long skillSound = SC_SkillSound.pack(_targetId, gfxId);

	        SC_AttackMissPacket.send(_pc.getNetConnection(), _pc, _targetId);
	        SC_AttackMissPacket.broadcast(_pc, _targetId);
	        
	        SC_DoActionGFX.send(
	                _pc.getNetConnection(),
	                _pc.getId(),
	                ActionCodes.ACTION_Damage);
	        SC_DoActionGFX.broadcast(
	                _pc,
	                ActionCodes.ACTION_Damage);
	        
	        SC_SkillSound.sendPacked(_pc, skillSound);
	        SC_SkillSound.broadcastFastPacked(_pc, skillSound);

	    } else if (_calcType == NPC_PC) {
	        // 공격자(NPC) 처리
            if (animateNormalAttack) {
                _npc.setHeading(_npc.targetDirection(_targetX, _targetY));
                int actId = getActId() > 0 ? getActId() : ActionCodes.ACTION_Attack;
                if (getGfxId() > 0) {
                    SC_UseAttackSkill.broadcast(_npc, _target, _npc.getId(), getGfxId(), _targetX, _targetY, actId, 0);
                } else {
                    SC_AttackMissPacket.broadcast(_npc, _targetId, actId);
                }
                SC_DoActionGFX.broadcast(_npc, ActionCodes.ACTION_Damage);
            }
	        SC_SkillSound.broadcastFast(_npc, _targetId, gfxId);
	    }
	}

	/**
	 * 요정 인페르노 대미지 적용
	 */
	public void commitInferno(int step) {
		int damage = calcInfernoDamage(step);
		
		if (damage <= 0) return;

		// 공격 타입에 따른 대상 대미지 전달
		switch (_calcType) {
		case PC_PC:  _pc.receiveDamage(_targetPc, damage); break;
		case PC_NPC: _pc.receiveDamage(_targetNpc, damage); break;
		case NPC_PC: _npc.receiveDamage(_targetPc, damage); break;
		}
	}

	/**
	 * 요정 인페르노 대미지 계산
	 */
	private int calcInfernoDamage(int step) {
	    // 공격자로부터 무기 정보를 가져옴 
		 L1PcInstance attacker = _targetPc; 
	    
	    if (attacker == null) {
	    	return 0;
	    }
	    L1ItemInstance weapon = attacker.getWeapon();
	    
	    if (weapon == null || weapon.getItem().getType() != 1) {
	        return 0;
	    }
	    int dmgSmall = weapon.getItem().getDmgSmall();
	    int addDmg = weapon.getItem().getDmgModifier();
	    int enchant = weapon.getEnchantLevel();

	    int effectiveEnchant = enchant;
	    if (enchant >= 10) {
	        effectiveEnchant += (enchant - 10) + 1;
	    }
	    return (dmgSmall + effectiveEnchant + addDmg) * step;
	}

    }
}
