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

import java.util.concurrent.RejectedExecutionException;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

/** Damage poison is also its own reusable scheduled callback: no per-poison timer task. */
public final class DamagePoison extends PoisonEffect implements Runnable {
    private final L1Character attacker;
    private final L1Character target;
    private final int damageSpan;
    private final int damage;
    private final boolean tomahawk;
    private boolean firstTick = true;
    private boolean cured;

    private DamagePoison(L1Character attacker, L1Character target,
                         int damageSpan, int damage, boolean tomahawk) {
        this.attacker = attacker;
        this.target = target;
        this.damageSpan = damageSpan;
        this.damage = damage;
        this.tomahawk = tomahawk;
    }

    public static boolean doInfection(L1Character attacker, L1Character target,
                                      int damageSpan, int damage, boolean tomahawk, int time) {
        if (target == null) return false;
        synchronized (target) {
            if (!isValidTarget(target)) return false;
            final DamagePoison poison = new DamagePoison(attacker, target, damageSpan, damage, tomahawk);
            target.setPoison(poison);
            poison.apply(time);
            if (target instanceof L1PcInstance || target instanceof L1MonsterInstance) {
                try {
                    // Preserve the original immediate first tick and later interval.
                    ThreadPool.getInstance().execute(poison);
                } catch (RejectedExecutionException rejected) {
                    poison.cure();
                    return false;
                }
            }
            return target.getPoison() == poison;
        }
    }

    private void apply(int time) {
        if (tomahawk) {
            target.setSkillEffect(L1SkillId.STATUS_TOMAHAWK, time);
        } else {
            if (target instanceof L1PcInstance pc)
                SC_PacketBox.send(pc, SC_PacketBox.POSION_ICON, pc, 1, 30);
            target.setSkillEffect(L1SkillId.STATUS_POISON, time);
            target.setPoisonEffect(1);
        }
    }

    @Override
    public void run() {
        synchronized (target) {
            if (cured || target.getPoison() != this) return;

            // Skill changes are dispatched to the character owner. The first
            // immediate tick must not mistake an enqueued status for expiry.
            if (!firstTick && !target.hasSkillEffect(L1SkillId.STATUS_POISON)
                    && !target.hasSkillEffect(L1SkillId.STATUS_TOMAHAWK)) {
                cure();
                return;
            }
            firstTick = false;
            if (target.hasSkillEffect(L1SkillId.ICE_LANCE)
                    || target.hasSkillEffect(L1SkillId.MOB_COCA)
                    || target.hasSkillEffect(L1SkillId.MOB_BASILL)
                    || target.hasSkillEffect(L1SkillId.EARTH_BIND)) {
                cure();
                return;
            }

            if (target instanceof L1PcInstance pc) {
                pc.receiveDamage(attacker, damage);
                if (pc.isDead()) {
                    cure();
                    return;
                }
            } else if (target instanceof L1MonsterInstance monster) {
                monster.receiveDamage(attacker, damage);
                if (monster.isDead()) {
                    // Previously only canceled the timer, leaving poison attached.
                    cure();
                    return;
                }
            } else {
                return;
            }

            // Reuse this effect as the next callback. Do not allocate another
            // RepeatTask for every infected character.
            if (!cured && target.getPoison() == this
                    && ThreadPool.getInstance().schedule(this, damageSpan) == null) cure();
        }
    }

    @Override
    public int getEffectId() {
        return 1;
    }

    @Override
    public void cure() {
        synchronized (target) {
            if (cured) return;
            cured = true;
            if (target.getPoison() != this) return;

            if (tomahawk) {
                target.killSkillEffectTimer(L1SkillId.STATUS_TOMAHAWK);
            } else {
                if (target instanceof L1PcInstance pc)
                    SC_PacketBox.send(pc, SC_PacketBox.POSION_ICON, pc, 0, 0);
                target.setPoisonEffect(0);
                target.killSkillEffectTimer(L1SkillId.STATUS_POISON);
            }
            target.setPoison(null);
        }
    }
}
