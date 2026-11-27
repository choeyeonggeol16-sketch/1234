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
package l1j.server.server.storage;

import l1j.server.server.model.Instance.L1PcInstance;

public interface CharacterStorage {
	public void createCharacter(L1PcInstance pc) throws Exception;

	public void deleteCharacter(String accountName, String charName) throws Exception;

	public boolean storeCharacter(L1PcInstance pc) throws Exception;

	public void updateAccountName(L1PcInstance pc) throws Exception;

	L1PcInstance loadCharacter(String charName) throws Exception;

    default L1PcInstance loadCharacter(String charName, LoginVitals vitals) throws Exception {
        return loadCharacter(charName);
    }

    /** Detached login state; some saved health or mana may require equipped items or active buffs. */
    final class LoginVitals {
        private int savedHp, savedMp, initialHp, initialMp;

        public void capture(int hp, int mp, L1PcInstance pc) {
            savedHp = hp;
            savedMp = mp;
            initialHp = pc.getCurrentHp();
            initialMp = pc.getCurrentMp();
        }

        public void restore(L1PcInstance pc) {
            int hp = Math.max(0, Math.min(savedHp, pc.getMaxHp()) - initialHp);
            int mp = Math.max(0, Math.min(savedMp, pc.getMaxMp()) - initialMp);
            savedHp = savedMp = 0;
            pc.restoreLoginVitalOverflow(hp, mp);
        }
    }
}
