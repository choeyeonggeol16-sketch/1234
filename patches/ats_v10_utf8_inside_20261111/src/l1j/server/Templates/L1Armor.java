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
package l1j.server.Templates;

public class L1Armor extends L1Item {

	private static final long serialVersionUID = 1L;

	public L1Armor() {
	}

	private int _ac = 0; // ● AC

	@Override
	public int get_ac() {
		return _ac;
	}

	public void set_ac(int i) {
		this._ac = i;
	}

	private int _damageReduction = 0; // ● 데미지 경감

	@Override
	public int getDamageReduction() {
		return _damageReduction;
	}

	public void setDamageReduction(int i) {
		_damageReduction = i;
	}

	private int _weightReduction = 0; // ● 중량 경감

	@Override
	public int getWeightReduction() {
		return _weightReduction;
	}

	public void setWeightReduction(int i) {
		_weightReduction = i;
	}
	
	private int _HitRate = 0; // ● 근접무기 명중률
	private int _DmgRate = 0; // ● 근접무기 추타율
	private int _bowHitRate = 0; // ● 활의 명중율
	private int _BowDmgRate = 0; // ● 활의 추타율

	@Override
	public int getHitRate() {	return _HitRate;	}
	public void setHitRate(int i) {	_HitRate = i;	}

	@Override
	public int getMeleeDmg() {	
		return _DmgRate;	
	} 
	public void setDmgRate(int i) {
		_DmgRate = i;	
	}

	@Override
	public int getBowHitRate() {	return _bowHitRate;	}
	public void setBowHitRate(int i) {	_bowHitRate = i;	}

	@Override
	public int getRangedDmg() {	return _BowDmgRate;	}
	public void setBowDmgRate(int i) {	_BowDmgRate = i;	}


	private int _defense_water = 0; // ● 수의 속성 방어

	public void set_defense_water(int i) {
		_defense_water = i;
	}

	@Override
	public int get_defense_water() {
		return this._defense_water;
	}

	private int _defense_wind = 0; // ● 풍의 속성 방어

	public void set_defense_wind(int i) {
		_defense_wind = i;
	}

	@Override
	public int get_defense_wind() {
		return this._defense_wind;
	}

	private int _defense_fire = 0; // ● 불의 속성 방어

	public void set_defense_fire(int i) {
		_defense_fire = i;
	}

	@Override
	public int get_defense_fire() {
		return this._defense_fire;
	}

	private int _defense_earth = 0; // ● 흙의 속성 방어

	public void set_defense_earth(int i) {
		_defense_earth = i;
	}

	@Override
	public int get_defense_earth() {
		return this._defense_earth;
	}

	private int _regist_stan = 0; // ● 스탠 내성

	public void set_regist_stan(int i) {
		_regist_stan = i;
	}

	public int get_regist_stan() {
		return this._regist_stan;
	}

	private int _regist_stone = 0; // ● 석유화학 내성

	public void set_regist_stone(int i) {
		_regist_stone = i;
	}

	public int get_regist_stone() {
		return this._regist_stone;
	}

	private int _regist_sleep = 0; // ● 수면 내성

	public void set_regist_sleep(int i) {
		_regist_sleep = i;
	}

	public int get_regist_sleep() {
		return this._regist_sleep;
	}

	private int _regist_freeze = 0; // ● 동결 내성

	public void set_regist_freeze(int i) {
		_regist_freeze = i;
	}

	public int get_regist_freeze() {
		return this._regist_freeze;
	}
	
	private int _regist_sustain = 0;	//홀드내성
	public int get_regist_sustain() {
		return this._regist_sustain;
	}

	public void set_regist_sustain(int i) {
		_regist_sustain = i;
	}


}