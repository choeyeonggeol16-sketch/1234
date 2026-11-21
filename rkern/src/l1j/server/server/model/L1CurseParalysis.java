/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.datatables.MonsterParalyzeDelay;
import l1j.server.server.datatables.MonsterParalyzeDelay.MonsterParalyze;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.ControlSkillEffects;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eDurationShowType;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType;

/**
 * Curse-paralysis lifetime. The existing PC controller/NPC shard executes all
 * phases; the CIO wheel only delivers the two deadlines.
 */
public final class L1CurseParalysis extends L1Paralysis implements CIOThreadCallback.WheelTask {
    private static final Logger LOG = Logger.getLogger(L1CurseParalysis.class.getName());
    private static final int START_NOTICE_BITS = 1 | 2 | 4 | 8 | 16 | 32 | 64 | 128 | 512 | 1024;
    private static final int END_NOTICE_BITS = 1 | 2 | 8 | 1024;

    private final L1Character target;
    private final int delay;
    private final int duration;
    private final int sessionStamp;

    private volatile boolean cureRequested;
    private volatile boolean stopped;
    private boolean started;
    private boolean frozen;

    private L1CurseParalysis(L1Character target, MonsterParalyze rule) {
        this.target = target;
        this.delay = rule.paralyze_delay;
        this.duration = rule.paralyze_millis;
        this.sessionStamp = target instanceof L1PcInstance pc ? pc.getSessionStamp() : 0;
    }

    public static boolean curse(L1Character target, int skillId) {
        if (!(target instanceof L1PcInstance || target instanceof L1MonsterInstance)
                || target.getParalysis() != null
                || target.hasSkillEffect(L1SkillId.STATUS_CURSE_PARALYZING)
                || target.hasSkillEffect(L1SkillId.STATUS_CURSE_PARALYZED)) return false;

        final MonsterParalyze rule = MonsterParalyzeDelay.getInstance().get_paralyze(skillId);
        if (rule == null) return false;

        final L1CurseParalysis effect = new L1CurseParalysis(target, rule);
        if (!target.claimParalysis(effect)) return false;

        final CIOThreadCallback callback = CIOThreadCallback.getInstance();
        if (!callback.postCharacterEffectOnOwner(target, effect)
                && !callback.scheduleCharacterEffectTick(target, effect, 50L)) {
            effect.stopped = true;
            target.clearParalysisIfSame(effect);
            return false;
        }
        return target.getParalysis() == effect;
    }

    private boolean staleTarget() {
        if (target instanceof L1PcInstance pc) {
            return pc.getSessionStamp() != sessionStamp
                    || pc.getCreatureController() == null
                    || pc.getCreatureController().actor() != pc;
        }
        return target instanceof L1NpcInstance npc && npc.isDestroyed();
    }

    private void abandon() {
        stopped = true;
        target.clearParalysisIfSame(this);
    }

    @Override
    public void run() {
        if (isCancelled()) return;
        if (staleTarget()) {
            abandon();
            return;
        }
        final CIOThreadCallback callback = CIOThreadCallback.getInstance();
        if (!callback.postCharacterEffectOnOwner(target, this)
                && !callback.scheduleCharacterEffectTick(target, this, 50L))
            abandon();
    }

    @Override
    public void runOnController(L1PcInstance pc) {
        if (target == pc) onOwner();
    }

    @Override
    public void runOnNpc(L1NpcInstance npc) {
        if (target == npc) onOwner();
    }

    private void onOwner() {
        if (isCancelled()) return;
        if (staleTarget()) {
            abandon();
            return;
        }
        if (cureRequested) {
            finish();
            return;
        }
        try {
            if (!started) {
                // Admission and status mutation run in one creature execution order.
                if (target.hasSkillEffect(L1SkillId.STATUS_CURSE_PARALYZING)
                        || target.hasSkillEffect(L1SkillId.STATUS_CURSE_PARALYZED)) {
                    abandon();
                    return;
                }
                started = true;
                startEffect();
                if (target instanceof L1PcInstance && !arm(Math.max(1L, delay))) finish();
            } else if (!frozen) {
                if (target instanceof L1PcInstance pc) {
                    if (pc.isDead()) {
                        finish();
                        return;
                    }
                    frozen = true;
                    sendNotice(pc, duration / 1000, 213, false);
                    SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, true);
                    target.setCurseParalyzed(true);
                    if (!arm(Math.max(1L, duration))) finish();
                }
            } else {
                finish();
            }
        } catch (Throwable failure) {
            try {
                finish();
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            LOG.log(Level.WARNING, "Curse paralysis owner callback failed", failure);
        }
    }

    private void startEffect() {
        if (target instanceof L1PcInstance pc) {
            SC_ServerMessage.send(pc, 212);
        }
        target.setPoisonEffect(2);
        target.setSkillEffect(L1SkillId.STATUS_CURSE_PARALYZING, delay);
        if (target instanceof L1PcInstance pc)
            sendNotice(pc, delay / 1000, 212, false);
        // Original NPC case only sets the initial status/visual and awaits cure.
    }

    private boolean arm(long milliseconds) {
        return CIOThreadCallback.getInstance()
                .scheduleCharacterEffectTick(target, this, milliseconds);
    }

    private static void sendNotice(L1PcInstance pc, int seconds, int tooltipId, boolean end) {
        SC_SPELL_BUFF_NOTI.sendFields(pc,
                end ? eNotiType.END : eNotiType.RESTAT,
                L1SkillId.CURSE_PARALYZE, end ? 0 : seconds,
                eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC,
                end ? 0 : 5270, end ? 0 : 5270, end ? 0 : 10,
                end ? 0 : tooltipId, 0, 0, false, 0, 0, 0,
                end ? END_NOTICE_BITS : START_NOTICE_BITS);
    }

    private void finish() {
        if (stopped) return;
        stopped = true;
        try {
            if (started && target.getParalysis() == this) {
                target.setCurseParalyzed(false);
                if (!target.isParalyzed() && !ControlSkillEffects.hasSkillFreeze(target, -1))
                    target.setPoisonEffect(0);
                if (target instanceof L1PcInstance pc) {
                    sendNotice(pc, 0, 0, true);
                    if (!pc.isDead() && !pc.isParalyzed()
                            && !ControlSkillEffects.hasSkillFreeze(pc, -1))
                        SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, false);
                }
                target.removeSkillEffect(L1SkillId.STATUS_CURSE_PARALYZING);
                target.removeSkillEffect(L1SkillId.STATUS_CURSE_PARALYZED);
            }
        } finally {
            target.clearParalysisIfSame(this);
        }
    }

    @Override
    public boolean isCancelled() {
        return stopped || target.getParalysis() != this;
    }

    @Override
    public void cure() {
        if (isCancelled() || cureRequested) return;
        cureRequested = true;
        final CIOThreadCallback callback = CIOThreadCallback.getInstance();
        if (!callback.postCharacterEffectOnOwner(target, this)
                && !callback.scheduleCharacterEffectTick(target, this, 50L))
            abandon();
    }

    @Override
    public int getEffectId() {
        return 2;
    }
}
