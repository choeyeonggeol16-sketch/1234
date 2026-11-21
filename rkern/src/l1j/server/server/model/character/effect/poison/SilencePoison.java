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

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

/** Silence poison uses the same target-owned apply/cure lifecycle as other poison. */
public final class SilencePoison extends PoisonEffect {
    private final L1Character target;
    private boolean cured;

    private SilencePoison(L1Character target) {
        this.target = target;
    }

    public static boolean doInfection(L1Character target) {
        return doInfection(target, 0);
    }

    public static boolean doInfection(L1Character target, int time) {
        if (target == null) return false;
        synchronized (target) {
            if (!isValidTarget(target)) return false;
            final SilencePoison poison = new SilencePoison(target);
            target.setPoison(poison);
            poison.apply(time);
            return target.getPoison() == poison;
        }
    }

    private void apply(int time) {
        target.setPoisonEffect(1);
        sendMessageIfPlayer(target, 310);
        if (target instanceof L1PcInstance pc)
            SC_PacketBox.send(pc, SC_PacketBox.POSION_ICON, pc, 6, time == 0 ? -1 : time);
        // Existing L1 silence persists until cured (the icon keeps its time).
        target.setSkillEffect(L1SkillId.STATUS_POISON_SILENCE, 0);
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
            target.setPoisonEffect(0);
            sendMessageIfPlayer(target, 311);
            if (target instanceof L1PcInstance pc)
                SC_PacketBox.send(pc, SC_PacketBox.POSION_ICON, pc, 0, 0);
            target.killSkillEffectTimer(L1SkillId.STATUS_POISON_SILENCE);
            target.setPoison(null);
        }
    }
}
