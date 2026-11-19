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

import java.util.HashMap;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SpecialStats;
import l1j.server.server.utils.eKind;

public class L1Armor extends L1Item {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public L1Armor() {
	}

	private int _ac = 0;
	private int _weightReduction = 0;
	private int _HitRate = 0; // ● 근접무기 명중률
	private int _DmgRate = 0; // ● 근접무기 추타율
	private int _bowHitRate = 0; // ● 활의 명중율
	private int _BowDmgRate = 0; // ● 활의 추타율
	private int _defense_water = 0;
	private int _defense_wind = 0;
	private int _defense_fire = 0;
	private int _defense_earth = 0;
	private int _regist_calcPcDefense = 0;
	private int _regist_PVPweaponTotalDamage = 0;
	
	@Override
	public int get_ac() {	return _ac;	}
	public void set_ac(int i) {	this._ac = i;	}

	@Override
	public int getWeightReduction() {	return _weightReduction;	}
	public void setWeightReduction(int i) {	_weightReduction = i;	}
	

	@Override
	public int getHitRate() {	return _HitRate;	}
	public void setHitRate(int i) {	_HitRate = i;	}

	@Override
	public int getDmgRate() {	return _DmgRate;	}
	public void setDmgRate(int i) {	_DmgRate = i;	}

	@Override
	public int getBowHitRate() {	return _bowHitRate;	}
	public void setBowHitRate(int i) {	_bowHitRate = i;	}

	@Override
	public int getBowDmgRate() {	return _BowDmgRate;	}
	public void setBowDmgRate(int i) {	_BowDmgRate = i;	}

	@Override
	public int get_defense_water() {	return this._defense_water;	}
	public void set_defense_water(int i) {	_defense_water = i;	}

	@Override
	public int get_defense_wind() {	return this._defense_wind;	}
	public void set_defense_wind(int i) {	_defense_wind = i;	}

	@Override
	public int get_defense_fire() {	return this._defense_fire;	}
	public void set_defense_fire(int i) {	_defense_fire = i;	}

	@Override
	public int get_defense_earth() {	return this._defense_earth;	}
	public void set_defense_earth(int i) {	_defense_earth = i;	}
	
	@Override
	public int get_regist_calcPcDefense() {	return this._regist_calcPcDefense;	}
	public void set_regist_calcPcDefense(int i) {	_regist_calcPcDefense = i;	}
	
	@Override
	public int get_regist_PVPweaponTotalDamage() {	return this._regist_PVPweaponTotalDamage;	}
	public void set_regist_PVPweaponTotalDamage(int i) {	_regist_PVPweaponTotalDamage = i;	}
	
	
	@Override
	public void equipmentItem(L1PcInstance pc, boolean isEquipped) {
	    _specialStats.equipTo(pc, isEquipped ? 1 : -1);
	}
	
	private int m_magicHitup;
	@Override
	public int getMagicHitup(){
		return m_magicHitup;
	}
	
	public void setMagicHitup(int i){
		m_magicHitup = i;
	}
}
