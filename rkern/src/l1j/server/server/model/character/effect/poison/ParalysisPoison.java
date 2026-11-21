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
package l1j.server.server.model.character.effect.poison;

import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.MonsterParalyzeDelay;
import l1j.server.server.datatables.MonsterParalyzeDelay.MonsterParalyze;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.ControlSkillEffects;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eDurationShowType;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType;

/** Two-stage poison effect. One reusable callback handles delay and expiry. */
public final class ParalysisPoison extends PoisonEffect implements Runnable {
    private static final int PENDING = 0;
    private static final int PARALYZED = 1;
    private static final int CURED = 2;
    private static final int START_NOTICE_BITS = 1 | 2 | 4 | 8 | 16 | 32 | 64 | 128 | 512 | 1024;
    private static final int END_NOTICE_BITS = 1 | 2 | 8 | 1024;

    private final L1Character target;
    private final int delay;
    private final int duration;
    private int stage;
    private volatile int effectId = 1;

    private ParalysisPoison(L1Character target, MonsterParalyze paralyze) {
        this.target = target;
        delay = paralyze.paralyze_delay;
        duration = paralyze.paralyze_millis;
    }

    public static boolean doInfection(L1Character target, int skillId) {
        return doInfection(target, MonsterParalyzeDelay.getInstance().get_paralyze(skillId));
    }

    public static boolean doInfection(L1Character target, MonsterParalyze paralyze) {
        if (target == null || paralyze == null) return false;
        synchronized (target) {
            if (!isValidTarget(target)) return false;
            final ParalysisPoison poison = new ParalysisPoison(target, paralyze);
            // Publish ownership before even a zero-delay callback is allowed.
            target.setPoison(poison);
            poison.apply();
            return target.getPoison() == poison;
        }
    }

    private void apply() {
        sendMessageIfPlayer(target, 212);
        target.setPoisonEffect(1);
        if (target instanceof L1PcInstance pc) {
            target.setSkillEffect(L1SkillId.STATUS_POISON_PARALYZING, 0);
            sendStartNotice(pc, delay / 1000, 212);
            if (ThreadPool.getInstance().schedule(this, delay) == null) cure();
        }
    }

    @Override
    public void run() {
        synchronized (target) {
            if (stage == CURED || target.getPoison() != this) return;
            if (stage == PARALYZED) {
                cure();
                return;
            }

            stage = PARALYZED;
            effectId = 2;
            target.setPoisonEffect(2);
            if (target instanceof L1PcInstance pc) {
                // A dead player must not acquire an orphaned paralysis state.
                if (pc.isDead()) {
                    cure();
                    return;
                }
                sendStartNotice(pc, duration / 1000, 213);
                SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, true);
                target.setSkillEffect(L1SkillId.STATUS_POISON_PARALYZED, 0);
                target.setPoisonParalyzed(true);
                if (ThreadPool.getInstance().schedule(this, duration) == null) cure();
            }
        }
    }

    private static void sendStartNotice(L1PcInstance pc, int seconds, int tooltipId) {
        SC_SPELL_BUFF_NOTI.sendFields(pc, eNotiType.RESTAT, L1SkillId.CURSE_PARALYZE,
                seconds, eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC,
                5270, 5270, 10, tooltipId, 0, 0, false, 0, 0, 0, START_NOTICE_BITS);
    }

    private static void sendEndNotice(L1PcInstance pc) {
        SC_SPELL_BUFF_NOTI.sendFields(pc, eNotiType.END, L1SkillId.CURSE_PARALYZE,
                0, eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC,
                0, 0, 0, 0, 0, 0, false, 0, 0, 0, END_NOTICE_BITS);
    }

    @Override
    public int getEffectId() {
        return effectId;
    }

    @Override
    public void cure() {
        synchronized (target) {
            if (stage == CURED) return;
            stage = CURED;
            if (target.getPoison() != this) return;

            target.setPoisonParalyzed(false);
            target.killSkillEffectTimer(L1SkillId.STATUS_POISON_PARALYZING);
            target.killSkillEffectTimer(L1SkillId.STATUS_POISON_PARALYZED);
            if (target instanceof L1PcInstance pc) {
                sendEndNotice(pc);
                if (!pc.isDead() && !pc.isParalyzed()
                        && !ControlSkillEffects.hasSkillFreeze(pc, -1))
                    SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, false);
            }
            if (!target.isParalyzed() && !ControlSkillEffects.hasSkillFreeze(target, -1))
                target.setPoisonEffect(0);
            target.setPoison(null);
        }
    }
}
