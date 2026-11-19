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
package l1j.server.server.templates;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SpecialStats;

public class L1Weapon extends L1Item {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	//private int _regist_calcPcDefense = 0;//무기테이블
	//private int _regist_PVPweaponTotalDamage = 0;//무기테이블
	public L1Weapon() {
	}

	private int _doubleDmgChance;
	@Override
	public int getDoubleDmgChance() {
		return _doubleDmgChance;
	}

	public void setDoubleDmgChance(int i) {
		_doubleDmgChance = i;
	}

	private int _magicDmgModifier = 0;

	@Override
	public int getMagicDmgModifier() {
		return _magicDmgModifier;
	}

	public void setMagicDmgModifier(int i) {
		_magicDmgModifier = i;
	}

	private int _canbedmg = 0;

	@Override
	public int get_canbedmg() {
		return _canbedmg;
	}

	public void set_canbedmg(int i) {
		_canbedmg = i;
	}

	@Override
	public boolean isTwohandedWeapon() {
		int weapon_type = getType();
		
		boolean bool = (weapon_type == 3 || weapon_type == 4
				|| weapon_type == 5 || weapon_type == 11
				|| weapon_type == 12 || weapon_type == 15
				|| weapon_type == 16 || weapon_type == 18);

		return bool;
	}
	
	
	@Override
	public void equipmentItem(L1PcInstance pc, boolean isEquipped) {
	    _specialStats.equipTo(pc, isEquipped ? 1 : -1);
	}
	
	@Override
	public int getMagicHitup(){
		return 0;
	}
	
	@Override
	public void setMagicHitup(int i){
	}
}
