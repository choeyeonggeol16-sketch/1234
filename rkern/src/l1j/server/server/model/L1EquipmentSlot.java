package l1j.server.server.model;

import l1j.server.server.model.character.skill.BraveSpeedEffects;
import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;

import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.List;

import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.MJTemplate.ItemPresentator.ItemPresentatorFactory;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.MovementSpeedEffects;
import l1j.server.server.model.item.ItemEffectRegistry;
import l1j.server.server.model.item.function.ItemBonusHooks;
import l1j.server.server.model.network.core.sc.packet.SC_DelSkill;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;

import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;



import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.eKind;

import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconBlessOfEva;
public class L1EquipmentSlot {

	private L1PcInstance _owner;

	private ArrayList<L1ArmorSet> _currentArmorSet;

	private ArrayList<L1ItemInstance> _weapons;

	private ArrayList<L1ItemInstance> _armors;

	public int worldjoin_weapon_idx = 0;

	public L1EquipmentSlot(L1PcInstance owner) {
		_owner = owner;
		_weapons = new ArrayList<L1ItemInstance>();
		_armors = new ArrayList<L1ItemInstance>();
		_currentArmorSet = new ArrayList<L1ArmorSet>();
	}

	private void setWeapon(L1ItemInstance weapon) {// 무기류
		//L1Item item = weapon.getItem(); //무기테이블
		int itemId = weapon.getItem().getItemId();
		int enchant = weapon.getEnchantLevel();
		
		if (itemId == 1134 || itemId == 101134) { // 명상의 지팡이
			if (enchant > 0) {
				_owner.addMpr(enchant);
			}
		}

		if(itemId == 14061 || itemId == 14012 || itemId == 2202013 || itemId == 2202014 || itemId == 14086) {
			_owner.addDamageReductionByArmor(1);
		}
		if(itemId == 14134 || itemId == 2202012 ) {
			_owner.addDamageReductionByArmor(2);
		}
		if (itemId == 61 || itemId == 14061) {
			if (enchant > 0) {
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 8 + enchant);
				_owner.add_melee_critical_rate(1 + enchant);
			} else {
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 8);
				_owner.add_melee_critical_rate(1);
			}
		}

		if (itemId == 12 || itemId == 14012) {
			if (enchant > 0) {
				_owner.getResistance().addSpecialPierce(eKind.ALL, enchant);
			}
		}

		if (itemId == 134 || itemId == 14134) {
			if (enchant > 0) {
				_owner.getAbility().addSp(7 + enchant);
				_owner.addBaseMagicHitUp(2 + enchant);
			} else {
				_owner.getAbility().addSp(7);
				_owner.addBaseMagicHitUp(2);
			}
			SC_SPMR.send(_owner);
		}

		if (itemId == 86 || itemId == 14086) {
			if (enchant > 0) {
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5 + enchant);
			} else {
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
			}
		}
		if (itemId == 202011 || itemId == 2202011) {
			if (enchant > 0) {
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5 + enchant);
				_owner.addDamageReductionIgnore(9  + enchant);
				_owner.add_missile_critical_rate(1 + enchant);
				_owner.addDamageReductionByArmor(2);
			} else {
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				_owner.add_missile_critical_rate(1);
				_owner.addDamageReductionIgnore(9);
				_owner.addDamageReductionByArmor(2);
				
				
			}
		}

		if (itemId == 202013 || itemId == 2202013) {
			if (enchant > 0) {
				_owner.add_melee_critical_rate(1 + enchant);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 7  + enchant);
			} else {
				_owner.add_melee_critical_rate(1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 7);
			}
		}

		if (itemId == 202012 || itemId == 2202012) {
			if (enchant > 0) {
				_owner.getAbility().addSp(2 + enchant);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 7  + enchant);
				_owner.add_magic_critical_rate(1 + enchant);
			} else {
				_owner.getAbility().addSp(2);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 7);
				_owner.add_magic_critical_rate(1);
			}
			SC_SPMR.send(_owner);
		}

		if (itemId == 202014 || itemId == 2202014) {
			if (enchant > 0) {
				_owner.add_melee_critical_rate(1 + enchant);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 3 + enchant);
				
			} else {
				_owner.add_melee_critical_rate(1);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 3);
			
			}
		}
		
		/** 칠흑의 수정구 **/
		if (itemId == 118) {
			switch (enchant) {
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addCha(1);
				break;
			default:
				break;
			}
		}
		/** 축복받은 제로스의 지팡이 **/
		if (itemId == 620) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBaseMagicCritical(5);// 마법 치명타
				break;
			}
		}
		/** 살기의 키링크 **/
		if (itemId == 7000223 || itemId == 505015) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
				_owner.addBaseMagicCritical(1);// 마법 치명타
				break;
			case 7:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 1);
				_owner.addBaseMagicCritical(2);// 마법 치명타
				break;
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 2);
				_owner.addBaseMagicCritical(3);// 마법 치명타
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 3);
				_owner.addBaseMagicCritical(4);// 마법 치명타
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 4);
				_owner.addBaseMagicCritical(5);// 마법 치명타
				break;
			}
		}
		
		if (itemId == 123001) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 2);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 2);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 3);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 3);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 3);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 4);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 4);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 4);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 5);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.FEAR, 5);
					_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
					_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				}
				break;
			}
		}
		if (itemId == 123002) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				break;
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 6);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 7);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 8);
				break;
			default:
				if (enchant > 10) {
					_owner.getResistance().addSpecialPierce(eKind.ABILITY, 8);
				}
				break;
			}
		}
		if (itemId == 123003) {
			switch (enchant) {
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 1);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 2);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 3);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 3);
				}
				break;
			}
		}
		if (itemId == 123004) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 2);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 3);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 4);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				}
				break;
			}
		}
		if (itemId == 123005) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.addBaseMagicHitUp(1);
				break;
			case 9:
				_owner.addBaseMagicHitUp(2);
				break;
			case 10:
				_owner.addBaseMagicHitUp(3);
				break;
			case 11:
				_owner.addBaseMagicHitUp(4);
				break;
			default:
				if (enchant > 11) {
					_owner.addBaseMagicHitUp(4);
				}
				break;
			}
		}
		if (itemId == 123006) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 2);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 3);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 4);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 5);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.FEAR, 5);
				}
				break;
			}
		}
		if (itemId == 123007) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 6);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 7);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 8);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 8);
				}
				break;
			}
		}
		if (itemId == 123008) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				_owner.addSp(1);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				_owner.addSp(2);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				_owner.addSp(3);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				_owner.addSp(4);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
					_owner.addSp(4);
				}
				break;
			}
		}
		/** 바포메트의 지팡이 SP **/
		if (itemId == 124) {
			switch (enchant) {
			case 7:
				_owner.getAbility().addSp(1);
				SC_SPMR.send(_owner);
				break;
			case 8:
				_owner.getAbility().addSp(2);
				SC_SPMR.send(_owner);
				break;
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addSp(3);
				SC_SPMR.send(_owner);
				break;
			}
		}
		if (itemId == 541) {
			switch (enchant) {
			case 0:
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 10);
				break;
			}
		}
		if (itemId == 542) {
			switch (enchant) {
			case 0:
				_owner.addDamageReductionByArmor(2);
				break;
			}
		}
		if (itemId == 543) {
			switch (enchant) {
			case 0:
				_owner.addHitup(2);
				break;
			}
		}
		if (itemId == 544) {
			switch (enchant) {
			case 0:
				_owner.addHitup(10);
				break;
			}
		}
		if (itemId == 545) {
			switch (enchant) {
			case 0:
				_owner.addHitup(17);
				break;
			}
		}
		if (itemId == 547) {
			switch (enchant) {
			case 0:
				_owner.addHitup(11);
				break;
			}
		}
		/** 드래곤슬레이어 **/
		if (itemId == 66) {
			_owner.addDmgup(enchant * 2);
		}
		/** 나이트발드의 양손검 기술적중 **/
		if (itemId == 59 || itemId == 617 || itemId == 11121) {
			_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
		}
		/** 악몽의 장궁 정령 적중**/
		if (itemId == 622 || itemId == 1136) {
			_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 3);
		}
		/** 악마왕 도끼 **/
		if (itemId == 147) {
			_owner.addDmgRate(enchant);
		}
		/** 태풍의도끼 공포적중+1 **/
		if (itemId == 203006 || itemId == 616) {
			switch (enchant) {
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 1);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 2);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 3);
				break;
			}
		}
		/** 섬멸자의 체인소드 적중 **/
		if (itemId == 203017 || itemId == 618) {
			switch (enchant) {
			case 7:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 1);
				break;
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 2);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 3);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 4);
				break;
			}
		}
		/** 썸타는 도끼 공포적중+5 **/
		if (itemId == 7000221) 
			_owner.getResistance().addSpecialPierce(eKind.FEAR, 5);

		/** 썸타는 무기류 PVP 효과 부여 **/
		if (itemId >= 7000214 || itemId <= 7000221) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
				_owner.getResistance().addPVPweaponTotalDamage(3);
				break;
			case 8:
				_owner.getResistance().addPVPweaponTotalDamage(5);
				break;
			case 9:
				_owner.getResistance().addPVPweaponTotalDamage(7);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addPVPweaponTotalDamage(10);
				break;
			}
		}

		weapon.startEquipmentTimer(_owner);
		addWeaponToSlots(weapon);
	
		int type = weapon.getItem().getType();
		if ((type == 7 || type == 16 || type == 17) && (weapon.get_bless_level() != 0)) {
			_owner.getAbility().addSp(weapon.get_bless_level());
			SC_SPMR.send(_owner);
		}

		if (itemId == 203003) { // 데스나이트의 불검:진
			L1PolyMorph.doPoly(_owner, 12232, 0, L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
		}
	}
	
	

	private boolean canUseSecondWeaponBySlayer(final L1ItemInstance left, final L1ItemInstance right) {
	    if (_owner == null || left == null || right == null) return false;

	    // 슬레이어 패시브
	    if (!_owner.isPassive(MJPassiveID.SLAYER.toInt())) return false;

	    // type1 == 11, type == 6 (한손도끼)
	    if (left.getItem() == null || right.getItem() == null) return false;

	    return left.getItem().getType1() == 11
	        && right.getItem().getType1() == 11
	        && left.getItem().getType() == 6
	        && right.getItem().getType() == 6;
	}

	// ===== 무기 슬롯 정규화 =====
	private void normalizeWeaponSlots() {
	    try {
	        final L1ItemInstance left  = (_weapons.size() >= 1) ? _weapons.get(0) : null;
	        final L1ItemInstance right = (_weapons.size() >= 2) ? _weapons.get(1) : null;

	        _owner.setWeapon(left);
	        _owner.setSecondWeapon(right);

	        if (right != null) {
	            _owner.setCurrentWeapon(88);
	        } else if (left != null && left.getItem() != null) {
	            _owner.setCurrentWeapon(left.getItem().getType1());
	        } else {
	            _owner.setCurrentWeapon(0);
	        }
	    } catch (Exception e) {
	        _owner.setWeapon(null);
	        _owner.setSecondWeapon(null);
	        _owner.setCurrentWeapon(0);
	    }
	}

	// ===== 무기 추가(슬레이어 2번째 허용) =====
	private void addWeaponToSlots(final L1ItemInstance weapon) {
	    if (weapon == null) return;
	    if (_weapons.contains(weapon)) return;

	    if (_weapons.isEmpty()) {
	        _weapons.add(weapon);
	        normalizeWeaponSlots();
	        return;
	    }

	    if (_weapons.size() == 1) {
	        final L1ItemInstance left = _weapons.get(0);

	        // 슬레이어 + 한손도끼면 2개 허용
	        if (canUseSecondWeaponBySlayer(left, weapon)) {
	            _weapons.add(weapon);
	            normalizeWeaponSlots();
	            return;
	        }

	        // 일반 교체
	        _weapons.clear();
	        _weapons.add(weapon);
	        normalizeWeaponSlots();
	        return;
	    }

	    // 이미 2개면: 기본은 교체
	    _weapons.clear();
	    _weapons.add(weapon);
	    normalizeWeaponSlots();
	}

	// =====  무기 제거(왼쪽 벗으면 오른쪽이 왼쪽으로 당겨짐) =====
	private void removeWeaponFromSlots(final L1ItemInstance weapon) {
	    if (weapon == null) return;
	    final int idx = _weapons.indexOf(weapon);
	    if (idx < 0) return;

	    _weapons.remove(idx);

	    // 당김 동작 포함
	    normalizeWeaponSlots();
	}
	
	private int weapons_idx = 0;

	public L1ItemInstance getWeapon() {
	    return _weapons.isEmpty() ? null : _weapons.get(0);
	}

	public L1ItemInstance getSecondWeapon() {
	    return _weapons.size() >= 2 ? _weapons.get(1) : null;
	}

	public L1ItemInstance getWeaponSwap() {
	    final int n = _weapons.size();

	    if (n == 0) return null;
	    if (n == 1) return _weapons.get(0);

	    return _weapons.get(weapons_idx++ & 1);
	}

	public boolean isWeapon(L1ItemInstance weapon) {
	    return _weapons.contains(weapon);
	}

	public int getWeaponCount() {
	    return _weapons.size();
	}

	public List<L1ItemInstance> getWeapons() {
	    return new ArrayList<L1ItemInstance>(_weapons);
	}


	private static boolean isRoomteece(int itemId) {
		return itemId == 222340 || itemId == 222341 || itemId == 22229 || itemId == 22230 || itemId == 22231
				|| itemId == 222337 || itemId == 222338 || itemId == 222339
				|| itemId >= 900124 && itemId <= 900130;
	}

	private void setArmor(L1ItemInstance armor) {	
		L1Item item = armor.getItem();
		int itemlvl = armor.getEnchantLevel();
		int itemtype = armor.getItem().getType();
		int itemId = armor.getItem().getItemId();
		int itemgrade = armor.getItem().getGrade();
		int constat = armor.getItem().get_addcon();
		int wisstat = armor.getItem().get_addwis();
		
		
		if (item.getMaxUseTime() > 0) {
			_owner.set시간제(true);
		}
		
		
		
			if (itemId == 123111 || itemId == 123112 || itemId == 123113) {
			
			
			 
				 int n = _owner.getSkillEffectTimeSec(L1SkillId.할파스의신의) / 60;
			 if (n > 0) {
				 SC_InventoryIcon.icoNew(_owner, L1SkillId.할파스의신의, 3942, _owner.getSkillEffectTimeSec(L1SkillId.할파스의신의), true);
				 
			 }else {
				 SC_InventoryIcon.iconNewUnLimit(_owner, L1SkillId.할파스의신의1, 3943, true);
		 }
			 }
			if (armor.get_bless_level2() != 0) {//SGMALL
				if(armor.get_bless_level2() >= 1) {
					_owner.addMaxHp(armor.get_bless_level2());
				}
			}
			if (armor.get_bless_level() != 0) { //SGMALL

					if(armor.get_bless_level() == 1) {
						_owner.addMaxHp(30);
					} else if(armor.get_bless_level() == 2) {
						_owner.addMaxHp(20);
					} else if(armor.get_bless_level() == 3) {
						_owner.addMaxHp(10);
					} else if(armor.get_bless_level() == 4) {
						_owner.addMaxHp(30);
					} else if(armor.get_bless_level() == 5) {
						_owner.addMaxHp(40);
					} else if(armor.get_bless_level() == 6) {
						_owner.addMaxHp(50);
					} else if(armor.get_bless_level() == 7) {
						_owner.addDamageReductionByArmor(1);
					} else if(armor.get_bless_level() == 8) {
						_owner.addDamageReductionByArmor(2);
					} else if(armor.get_bless_level() == 10) {
						_owner.getResistance().addSpecialPierce(eKind.ABILITY, 1);
					} else if(armor.get_bless_level() == 11) {
						_owner.getResistance().addSpecialPierce(eKind.FEAR, 1);
					} else if(armor.get_bless_level() == 12) {
						_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 1);
					} else if(armor.get_bless_level() == 13) {
						_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 1);
					} else if(armor.get_bless_level() == 14) {
						_owner.addBaseMagicHitUp(1);
					} else if(armor.get_bless_level() == 15) {
						_owner.getResistance().addSpecialResistance(eKind.ABILITY, 1);
					} else if(armor.get_bless_level() == 16) {
						_owner.getResistance().addSpecialResistance(eKind.FEAR, 1);
					} else if(armor.get_bless_level() == 17) {
						_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 1);
					} else if(armor.get_bless_level() == 18) {
						_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 1);
					} else if(armor.get_bless_level() == 19) {
						_owner.addDmgRate(1);
					} else if(armor.get_bless_level() == 20) {
						_owner.addBowDmgRate(1);
					} else if(armor.get_bless_level() == 21) {
						_owner.addHitup(1);
					} else if(armor.get_bless_level() == 22) {
						_owner.addBowHitup(1);
					} else if(armor.get_bless_level() == 23) {
						_owner.addDamageReductionByArmor(1);
					} else if(armor.get_bless_level() == 24) {
						_owner.getAC().addAc(-1);
					} else if(armor.get_bless_level() == 25) {
						_owner.getResistance().addMr(3);
					} else if(armor.get_bless_level() == 26) {
						_owner.getAbility().addSp(1);
					}							

			}
		if (constat > 0) {
			_owner.addMaxHp((_owner.getAbility().getCon() / 2) * constat);
		}
		
		if (wisstat > 0) {
			_owner.addMaxMp((_owner.getAbility().getWis() / 2) * wisstat);
		}

		if(itemId == 900111){	
			_owner.setSkillEffect(L1SkillId.TELEPORT_RULER, -1);
		}
		
		if(itemId == 22228){
			if(itemlvl >= 9){
				_owner.addMagicDodgeProbability(7);					
		}else if(itemlvl >= 8){
				_owner.addMagicDodgeProbability(4);
			}else if(itemlvl >= 7){
				_owner.addMagicDodgeProbability(1);					
			}
		}else if(itemId == 222334){
			if(itemlvl >= 9){
				_owner.addMagicDodgeProbability(9);
		}else if(itemlvl >= 8){
				_owner.addMagicDodgeProbability(5);
			}else if(itemlvl >= 7){
				_owner.addMagicDodgeProbability(3);
			}else if(itemlvl >= 6){
				_owner.addMagicDodgeProbability(1);
			}
		}
		
		
		
		if (itemId == 808011) {
			
			switch (itemlvl) {
			case 0:
				_owner.getResistance().addMr(12);
				break;
			case 1:
				_owner.getAC().addAc(-6);
				_owner.getResistance().addMr(26);
				break;
				
			case 2:
				_owner.getAC().addAc(-6);
				_owner.addMaxMp(10);
				_owner.addMaxHp(20);
				_owner.getResistance().addMr(28);
				break;
			case 3:
				_owner.getAC().addAc(-6);
				_owner.addMaxMp(20);
				_owner.addMaxHp(40);
				_owner.getResistance().addMr(30);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 3);
				break;
			case 4:
				_owner.getAC().addAc(-6);
				_owner.addMaxMp(30);
				_owner.addMaxHp(60);
				_owner.getResistance().addMr(32);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 4);
				break;
			case 5:
				_owner.getAC().addAc(-6);
				_owner.addMaxMp(40);
				_owner.addMaxHp(80);
				_owner.getResistance().addMr(34);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				_owner.addEffectedER(4);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				break;
			
		default:
			if (itemlvl > 5 ){
				_owner.getAC().addAc(-6);
				_owner.addMaxMp(40);
				_owner.addMaxHp(80);
				_owner.getResistance().addMr(34);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				_owner.addEffectedER(4);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
			}
				break;
			}
		}
		if ( itemId == 565611) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addMr(12);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 1);
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 1);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 12);
				break;
		default:
			if (itemlvl > 5) {
				_owner.getResistance().addMr(itemlvl - 5 + 12);
				_owner.addDamageReductionByArmor(itemlvl - 5 + 1);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, (itemlvl - 5 + 1));
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, (itemlvl - 5 + 1));
				_owner.getResistance().addSpecialResistance(eKind.FEAR, (itemlvl - 5 + 1));
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, (itemlvl - 5 + 12));
				_owner.getResistance().addPVPweaponTotalDamage(itemlvl - 5);
			}
				break;
			}
		}
		
		if (itemId == 900116) {
			if (itemlvl <= 5) {
				_owner.getResistance().addPVPweaponTotalDamage(3);
				_owner.getResistance().addcalcPcDefense(1);
				_owner.getResistance().addMr(10);
				_owner.addMaxHp(100);
			} else if (itemlvl == 6) {
				_owner.getResistance().addPVPweaponTotalDamage(4);
				_owner.getResistance().addcalcPcDefense(2);
				_owner.getResistance().addMr(12);
				_owner.addMaxHp(120);
			} else if (itemlvl == 7) {
				_owner.getResistance().addPVPweaponTotalDamage(5);
				_owner.getResistance().addcalcPcDefense(3);
				_owner.getResistance().addMr(13);
				_owner.addMaxHp(130);
			} else if (itemlvl == 8) {
				_owner.getResistance().addPVPweaponTotalDamage(6);
				_owner.getResistance().addcalcPcDefense(4);
				_owner.getResistance().addMr(15);
				_owner.addMaxHp(150);
			}

			
		}
		//착용
		if (itemId == 30184) {
			_owner.addHitup(2);
		}
		if (itemId == 20272 || itemId == 222342) {
			_owner.addBowHitup(3);
		}
		if (itemId == 800110) {
			if(_owner.isCrown() || _owner.isKnight() || _owner.isDarkelf() || _owner.isDragonknight() || _owner.isWarrior() ) {
				_owner.getAC().addAc(-8);
				_owner.addHitup(2);
				_owner.addDmgRate(2);
				_owner.addDamageReductionByArmor(2);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
			} else if(_owner.isBlackwizard() || _owner.isWizard()) {
				_owner.getAC().addAc(-8);
				_owner.getAbility().addSp(2);
				_owner.addBaseMagicHitUp(2);
				_owner.addDamageReductionByArmor(2);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
			} else if(_owner.isElf() && _owner.getAbility().getBaseDex() >= 30) {
				_owner.getAC().addAc(-8);
				_owner.addBowHitup(2);
				_owner.addBowDmgRate(2);
				_owner.addDamageReductionByArmor(2);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
			} else {
				_owner.getAC().addAc(-8);
				_owner.addHitup(2);
				_owner.addDmgRate(2);
				_owner.addDamageReductionByArmor(2);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
			}

			
		}
	if(item.getItemId() == 700117){
			
			
			_owner.getAbility().addSp(2+ itemlvl);
			_owner.getResistance().addPVPweaponTotalDamage(2 + itemlvl);
			_owner.getResistance().addcalcPcDefense(2 + itemlvl);
			_owner.getResistance().addMr(3 + itemlvl);
			_owner.addBaseMagicHitUp(1 + itemlvl);
			SC_OwnCharAttrDef.send(_owner);
			SC_SPMR.send(_owner);
			if (itemlvl >= 5) {
				_owner.getResistance().addSpecialResistance(eKind.ALL, itemlvl - 4);
				_owner.getResistance().addSpecialPierce(eKind.ALL, itemlvl - 4);
			}
			
		}
		
		
		if (itemId == 20050) {

			switch (itemlvl) {
			case 5:
				_owner.addEffectedER(1);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				
				break;
			case 6:
				_owner.addEffectedER(2);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				break;
			case 7:
				_owner.addEffectedER(3);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				break;
			case 8:
				_owner.addEffectedER(4);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				break;
			case 9:
				_owner.addEffectedER(5);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				break;
			
		default:
			if (itemlvl > 9 ){
				_owner.addEffectedER(5);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
			}
				break;
			}
		}
		if (itemId == 474700) {

			switch (itemlvl) {
			case 0:
				_owner.getResistance().addMr(1);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
				break;
			case 1:
				_owner.getResistance().addMr(3);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
				break;
			case 2:
				_owner.getResistance().addMr(5);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
				break;
			case 3:
				_owner.getResistance().addMr(7);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
				break;
			case 4:
				_owner.getResistance().addMr(9);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
				break;
			case 5:
				_owner.getResistance().addMr(11);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 6);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 6);
				break;
			case 6:
				_owner.getResistance().addMr(13);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 7);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
				break;
			case 7:
				_owner.getResistance().addMr(15);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 8);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 8);
				break;
			case 8:
				_owner.getResistance().addMr(17);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 9);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
				break;
			case 9:
				_owner.getResistance().addMr(19);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 10);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 10);
				break;
			case 10:
				_owner.getResistance().addMr(21);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 10);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 10);
				break;
			
//		default:
//			if (itemlvl > 9 ){
//				_owner.getResistance().addMr(((itemlvl - 9) * 2) + 19);
//				_owner.getResistance().addSpecialResistance(eKind.FEAR, 10);
//				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 10);
//			}
//				break;
			}
		}

	
		if (itemId == 20051) {

			switch (itemlvl) {
			case 0:
				_owner.getResistance().addMr(4);

				break;
			default:
				if (itemlvl > 0) {
					_owner.getResistance().addMr(itemlvl * 2 + 4);
				}

				break;

			}
		}
		
		
		
		if (itemId == 22263) {
			int aa = itemlvl - 4;
				if (itemlvl > 4) {
						_owner.addMaxHp(aa * 20);
						_owner.getResistance().addSpecialPierce(eKind.ABILITY, itemlvl - 4);
					}
			}
		if (itemId == 22360) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getResistance().addMr(1);

				break;
			default:
				
				if (itemlvl > 4) {
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, itemlvl - 4);
					_owner.getResistance().addMr(((itemlvl - 4 ) * 2) + 1);
				}

				break;

			}
		}
		
		if (itemtype >= 8 && itemtype <= 12 && !isRoomteece(itemId)) {

		    if ((itemtype == 8 || itemtype == 12) && itemlvl >= 5) {

		        int enchantAc = itemlvl - 4;

		        if (enchantAc > 5) {
		            enchantAc = 5;
		        }

		        if (itemlvl >= 10) {
		            enchantAc = 6;
		        }

		        _owner.getAC().addAc(
		                item.get_ac()
		                - enchantAc
		                + armor.get_durability());

		    } else {

		        _owner.getAC().addAc(
		                item.get_ac()
		                + armor.get_durability());
		    }

		    SC_OwnCharAttrDef.send(_owner);

		} else if (itemtype == 30) {

		    // 휘장

		} else if (itemId == 900118
		        || itemId == 900117
		        || itemId == 900119
		        || itemId == 900120) {

		    int enchant = armor.getEnchantLevel();
		    int enchantAc = enchant >= 1 ? enchant : 0;

		    _owner.getAC().addAc(
		            item.get_ac()
		            - enchantAc
		            + armor.get_durability());

		    SC_OwnCharAttrDef.send(_owner);

		} else if (!isRoomteece(itemId)) {

		    _owner.getAC().addAc(
		            item.get_ac()
		            - armor.getEnchantLevel()
		            + armor.get_durability());

		    SC_OwnCharAttrDef.send(_owner);

		} else {

		    _owner.getAC().addAc(item.get_ac());
		}
		/** 대마법사의 모자 인챈당 mp증가 **/
		if (itemId == 202022) {
			if (itemlvl >= 1) {
				_owner.addMaxMp(itemlvl * 10);
			}
			if (itemlvl >= 1) {
				_owner.getResistance().addMr(itemlvl * 2);
			}
		}
		/** 리치 로브 인챈당 sp증가 **/
		if (itemId == 20107) {
			if (itemlvl >= 3) {
				_owner.getAbility().addSp(itemlvl - 2);
			}
		}

		_owner.addDamageReductionByArmor(item.get_damage_reduction());
		_owner.addWeightReduction(item.getWeightReduction());
		_owner.addBowHitRate(item.getBowHitRate());	
		_owner.addBowDmgRate(item.getBowDmgRate());	
		_owner.addDmgRate(item.getDmgRate());	
		_owner.addHitRate(item.getHitRate());
		_owner.getResistance().addEarth(item.get_defense_earth());
		_owner.getResistance().addWind(item.get_defense_wind());
		_owner.getResistance().addWater(item.get_defense_water());
		_owner.getResistance().addFire(item.get_defense_fire());		
		item.equipmentItem(_owner, true);
		_owner.getResistance().addcalcPcDefense(item.get_regist_calcPcDefense());
		_owner.getResistance().addPVPweaponTotalDamage(item.get_regist_PVPweaponTotalDamage());
		_owner.addBaseMagicHitUp(item.getMagicHitup());
		_armors.add(armor);

		for (L1ArmorSet armorSet : L1ArmorSet.getAllSet()) {
			if (armorSet.isPartOfSet(itemId) && armorSet.isValid(_owner)) {
				if (armor.getItem().getType2() == 2 && armor.getItem().getType() == 9) {
					if (!armorSet.isEquippedRingOfArmorSet(_owner)) {
						armorSet.giveEffect(_owner);
						_currentArmorSet.add(armorSet);
					}
				} else {
					armorSet.giveEffect(_owner);
					_currentArmorSet.add(armorSet);
				}
			}
		}
		
		if (itemId == 900022) {
			if (_owner.getMapId() >= 1708 && _owner.getMapId() <= 1712) {
				SC_SkillSound.send(_owner, _owner.getId(), 11101);
				SC_PacketBox.send(_owner, SC_PacketBox.UNLIMITED_ICON1, 484, true);
			}
		}

		if (itemId == 20077 || itemId == 20062 || itemId == 120077) {
			if (_owner.hasSkillEffect(L1SkillId.DESPERADO)) {
				return;
			}
			_owner.invisibleItem();
		}
		if (itemId == 20288 || itemId == 900111) {
		    SC_Ability.send(_owner, 1, true);		    
		}
		if (itemId == 20281) {
			SC_Ability.send(_owner, 2, true);
		}
		if (itemId == 20036) {
			SC_Ability.send(_owner, 3, true);
		}
		if (itemId == 20284) {
			SC_Ability.send(_owner, 5, true);
		}
		if (itemId == 20207) {
			SC_SkillIconBlessOfEva.send(_owner, _owner.getId(), -1);
		}

		if (itemId == 20383) {
			if (armor.getChargeCount() != 0) {
				armor.setChargeCount(armor.getChargeCount() - 1);
				_owner.getInventory().updateItem(armor, L1PcInventory.COL_CHARGE_COUNT);
			}
		}
		/*** 50레벨 엘릭서 룬 ***/
		// 민첩의 엘릭서 룬
		if (itemId == 222295) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				break;
			// 기사//전사
			case 7:
			case 1:
				_owner.addMaxHp(50);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				break;
			}
		}
		// 체력의 엘릭서 룬
		if (itemId == 222296) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				break;
			// 기사//전사
			case 7:
			case 1:
				_owner.addMaxHp(50);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				break;
			}
		}
		// 지식의 엘릭서 룬
		if (itemId == 222297) {
			switch (_owner.getType()) { 
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				break;
			// 기사//전사
			case 7:
			case 1:
				_owner.addMaxHp(50);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.addMaxMp(30);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				break;
			}
		}
		// 지혜의 엘릭서 룬
		if (itemId == 222298) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				break;
			// 기사//전사
			case 7:
			case 1:
				_owner.addMaxHp(50);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				break;
			}
		}
		// 힘의 엘릭서 룬
		if (itemId == 222299) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				break;
			// 기사//전사
			case 7:
			case 1:
				_owner.addMaxHp(50);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				break;
			}
		}
		//TODO 70레벨 엘릭서 룬
		// 민첩의 엘릭서 룬
		if (itemId == 222312) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addDmgRate(2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				break;
			}
		}
		// 체력의 엘릭서 룬
		if (itemId == 222313) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addDmgRate(2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				break;
			}
		}
		// 지식의 엘릭서 룬
		if (itemId == 222314) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addDmgRate(2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				break;
			}
		}
		// 지혜의 엘릭서 룬
		if (itemId == 222315) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addDmgRate(2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				break;
			}
		}
		// 힘의 엘릭서 룬
		if (itemId == 222316) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addDmgRate(2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				break;
			}
		}
		//TODO 80레벨 엘릭서 룬
		// 힘의 엘릭서 룬
		if (itemId == 900135) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addDmgRate(2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				break;
			}
		}
		// 민첩의 엘릭서 룬
		if (itemId == 900136) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addDmgRate(2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				break;
			}
		}
		// 체력의 엘릭서 룬
		if (itemId == 900137) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addDmgRate(2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				break;
			}
		}
		// 지식의 엘릭서 룬
		if (itemId == 900138) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addDmgRate(2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				break;
			}
		}
		// 지혜의 엘릭서 룬
		if (itemId == 900139) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addDmgRate(2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				break;
			}
		}
		// TODO 85레벨 엘릭서 룬
		// 힘의 엘릭서 룬
		if (itemId == 900140) {
			_owner.addPVPMagicDamageReduction(2);
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addHitup(2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				_owner.addBaseMagicHitUp(5);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 5);
				break;
			}
		}
		// 민첩의 엘릭서 룬
		if (itemId == 900141) {
			_owner.addPVPMagicDamageReduction(2);
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addHitup(2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				_owner.addBaseMagicHitUp(5);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 5);
				break;
			}
		}
		// 체력의 엘릭서 룬
		if (itemId == 900142) {
			_owner.addPVPMagicDamageReduction(2);
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addHitup(2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				_owner.addBaseMagicHitUp(5);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 5);
				break;
			}
		}
		// 지식의 엘릭서 룬
		if (itemId == 900143) {
			_owner.addPVPMagicDamageReduction(2);
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addHitup(2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				_owner.addBaseMagicHitUp(5);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 5);
				break;
			}
		}
		// 지혜의 엘릭서 룬
		if (itemId == 900144) {
			_owner.addPVPMagicDamageReduction(2);
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addHitup(2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 5);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				_owner.addBaseMagicHitUp(5);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 5);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 5);
				break;
			}
		}
		// TODO 90레벨 엘릭서 룬
		// 힘의 엘릭서 룬
		if (itemId == 900145) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addHitup(2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 10);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 10);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 10);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 10);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 10);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 10);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 10);
				break;
			}
		}
		// 민첩의 엘릭서 룬
		if (itemId == 900146) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addHitup(2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 10);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 10);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 10);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 10);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 10);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 10);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 10);
				break;
			}
		}
		// 체력의 엘릭서 룬
		if (itemId == 900147) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addHitup(2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 10);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 10);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 10);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 10);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 10);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 10);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 10);
				break;
			}
		}
		// 지식의 엘릭서 룬
		if (itemId == 900148) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addHitup(2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 10);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 10);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 10);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 10);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 10);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 10);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 10);
				break;
			}
		}
		// 지혜의 엘릭서 룬
		if (itemId == 900149) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(3);
				_owner.addHitup(2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 10);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(50);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, 10);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(50);
				_owner.addBowDmgRate(1);
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 10);
				break;
			// 법사
			case 3:
				_owner.addMpr(3);
				_owner.getAbility().addSp(1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(-3);
				_owner.addMaxMp(30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, 10);
				break;
			// 용기사
			case 5:
				_owner.addHitup(3);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 10);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 10);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(5);
				_owner.addMaxHp(50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, 10);
				
				break;
			}
		}
		
		/** 화령의 가더 **/
		if (itemId == 900117) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addDmgRate(1);
				break;
			case 5:
				_owner.addDmgRate(1);
				_owner.addHitup(1);
				break;
			case 6:
				_owner.addDmgRate(1);
				_owner.addHitup(2);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				break;
			case 7:
				_owner.addDmgRate(2);
				_owner.addHitup(3);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 2);
				break;
			case 8:
				_owner.addDmgRate(3);
				_owner.addHitup(4);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 3);
				break;
			case 9:
				_owner.addDmgRate(4);
				_owner.addHitup(5);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 4);
				break;
			case 10:
				_owner.addDmgRate(5);
				_owner.addHitup(6);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				break;
			default:
				break;
			}
		}
		/** 풍령의 가더 **/
		if (itemId == 900118) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addBowDmgup(1);
				break;
			case 5:
				_owner.addBowDmgup(1);
				_owner.addBowHitup(1);
				break;
			case 6:
				_owner.addBowDmgup(1);
				_owner.addBowHitup(2);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				break;
			case 7:
				_owner.addBowDmgup(2);
				_owner.addBowHitup(3);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 2);
				break;
			case 8:
				_owner.addBowDmgup(3);
				_owner.addBowHitup(4);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 3);
				break;
			case 9:
				_owner.addBowDmgup(4);
				_owner.addBowHitup(5);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 4);
				break;
			case 10:
				_owner.addBowDmgup(5);
				_owner.addBowHitup(6);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				break;
			default:
				break;
			}
		}
		/** 수령의 가더 **/
		if (itemId == 900119) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getAbility().addSp(1);
				break;
			case 5:
				_owner.getAbility().addSp(1);
				_owner.addBaseMagicHitUp(1);
				break;
			case 6:
				_owner.getAbility().addSp(1);
				_owner.addBaseMagicHitUp(2);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				break;
			case 7:
				_owner.getAbility().addSp(2);
				_owner.addBaseMagicHitUp(3);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 2);
				break;
			case 8:
				_owner.getAbility().addSp(3);
				_owner.addBaseMagicHitUp(4);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 3);
				break;
			case 9:
				_owner.getAbility().addSp(4);
				_owner.addBaseMagicHitUp(5);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 4);
				break;
			case 10:
				_owner.getAbility().addSp(5);
				_owner.addBaseMagicHitUp(6);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				break;
			default:
				break;
			}
		}
		/** 에바의 방패 **/
		if (itemId == 20235) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 2);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 3);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 4);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 6);
				break;
			case 10:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 7);
				break;
			default:
				break;
			}
		}
		/** 시어의 심안 **/
		if (itemId == 22214) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 3);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 4);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 5);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 6);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 7);
				break;
			case 10:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 8);
				break;
			default:
				break;
			}
		}
		/** 지령의 가더 **/
		if (itemId == 900120) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getResistance().addcalcPcDefense(1);
				break;
			case 5:
				_owner.getResistance().addcalcPcDefense(1);
				_owner.getResistance().addMr(2);
				break;
			case 6:
				_owner.getResistance().addcalcPcDefense(1);
				_owner.getResistance().addMr(3);
				break;
			case 7:
				_owner.getResistance().addcalcPcDefense(2);
				_owner.getResistance().addMr(4);
				break;
			case 8:
				_owner.getResistance().addcalcPcDefense(3);
				_owner.getResistance().addMr(5);
				break;
			case 9:
				_owner.getResistance().addcalcPcDefense(4);
				_owner.getResistance().addMr(6);
				break;
			case 10:
				_owner.getResistance().addcalcPcDefense(5);
				_owner.getResistance().addMr(7);
				break;
			default:
				break;
			}
		}
		
		/** 마법사의가더 **/
		if (itemId == 22255) {
			switch (itemlvl) {
			case 5:
			case 6:
				_owner.getAbility().addSp(1);
				break;
			case 7:
			case 8:
				_owner.getAbility().addSp(2);
				break;
			case 9:
			case 10:
				_owner.getAbility().addSp(3);
				break;
			default:
				break;
			}
		}
		/** 체력의 가더 **/
		if (itemId == 22256) {
			switch (itemlvl) {
			case 5:
			case 6:
				_owner.addMaxHp(25);
				break;
			case 7:
			case 8:
				_owner.addMaxHp(50);
				break;
			case 9:
			case 10:
				_owner.addMaxHp(75);
				break;
			default:
				break;
			}
		}
		// TODO 투사의 휘장
		if (itemId == 900152) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(5);
				break;
			case 1:
				_owner.addMaxHp(10);
				break;
			case 2:
				_owner.addMaxHp(15);
				break;
			case 4:
				_owner.getAC().addAc(-1);
				_owner.addMaxHp(25);
				break;
			case 5:
				_owner.getAC().addAc(-2);
				_owner.addMaxHp(30);
				_owner.addDmgRate(1);// 근거리 대미지
				break;
			case 6:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(35);
				_owner.addDmgRate(2);// 근거리 대미지
				_owner.add_melee_critical_rate(1);// 근거리 치명타
				break;
			case 7:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(40);
				_owner.addDmgRate(3);// 근거리 대미지
				_owner.add_melee_critical_rate(3);// 근거리 치명타
				break;
			case 8:
				_owner.getAC().addAc(-3);
				_owner.addDmgRate(4);// 근거리 대미지
				_owner.add_melee_critical_rate(5);// 근거리 치명타
				break;
			default:
				break;
			}
		}
		// TODO 명궁의 휘장
		if (itemId == 900153) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(5);
				break;
			case 1:
				_owner.addMaxHp(10);
				break;
			case 2:
				_owner.addMaxHp(15);
				break;
			case 4:
				_owner.getAC().addAc(-1);
				_owner.addMaxHp(25);
				break;
			case 5:
				_owner.getAC().addAc(-2);
				_owner.addMaxHp(30);
				_owner.addBowDmgup(1);//원거리 대미지
				break;
			case 6:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(35);
				_owner.addBowDmgup(2);//원거리 대미지
				_owner.add_missile_critical_rate(1);// 원거리 치명타
				break;
			case 7:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(40);
				_owner.addBowDmgup(3);//원거리 대미지
				_owner.add_missile_critical_rate(3);// 원거리 치명타
				break;
			case 8:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(50);
				_owner.addBowDmgup(4);//원거리 대미지
				_owner.add_missile_critical_rate(5);// 원거리 치명타
				break;
			default:
				break;
			}
		}
		if (itemId == 123111) {
			switch (itemlvl) {
			case 0:
				_owner.addBaseMagicCritical(5);
				_owner.addDamageReductionByArmor(7);
				break;
			case 1:
				_owner.addBaseMagicCritical(5);
				_owner.addDamageReductionByArmor(9);
				break;
			case 2:
				_owner.addBaseMagicCritical(5);
				_owner.addDamageReductionByArmor(11);
				break;
			case 3:
				_owner.addBaseMagicCritical(5);
				_owner.addDamageReductionByArmor(13);
				break;
			case 4:
				_owner.addBaseMagicCritical(5);
				_owner.addDamageReductionByArmor(15);
				break;
			case 5:
				_owner.addBaseMagicCritical(5);
				_owner.addDamageReductionByArmor(17);
				break;
			case 6:
				_owner.addBaseMagicCritical(5);
				_owner.addDamageReductionByArmor(19);
				break;
			case 7:
				_owner.addBaseMagicCritical(5);
				_owner.addDamageReductionByArmor(21);
				break;
			case 8:
				_owner.addBaseMagicCritical(5);
				_owner.addDamageReductionByArmor(23);
				break;
			case 9:
				_owner.addBaseMagicCritical(5);
				_owner.addDamageReductionByArmor(25);
				break;
			case 10:
				_owner.addBaseMagicCritical(5);
				_owner.addDamageReductionByArmor(27);
				break;
			default:
				if (itemlvl > 10) {
					_owner.addBaseMagicCritical(5);
					_owner.addDamageReductionByArmor(itemlvl * 2 + 7);
				}
				break;
			}
		}
			//할파스의 예지력
					if (itemId == 123112) {
					switch (itemlvl) {
					case 0:
						_owner.add_missile_critical_rate(5);
						_owner.addDamageReductionByArmor(7);
						break;
					case 1:
						_owner.add_missile_critical_rate(5);
						_owner.addDamageReductionByArmor(9);
						break;
					case 2:
						_owner.add_missile_critical_rate(5);
						_owner.addDamageReductionByArmor(11);
						break;
					case 3:
						_owner.add_missile_critical_rate(5);
						_owner.addDamageReductionByArmor(13);
						break;
					case 4:
						_owner.add_missile_critical_rate(5);
						_owner.addDamageReductionByArmor(15);
						break;
					case 5:
						_owner.add_missile_critical_rate(5);
						_owner.addDamageReductionByArmor(17);
						break;
					case 6:
						_owner.add_missile_critical_rate(5);
						_owner.addDamageReductionByArmor(19);
						break;
					case 7:
						_owner.add_missile_critical_rate(5);
						_owner.addDamageReductionByArmor(21);
						break;
					case 8:
						_owner.add_missile_critical_rate(5);
						_owner.addDamageReductionByArmor(23);
						break;
					case 9:
						_owner.add_missile_critical_rate(5);
						_owner.addDamageReductionByArmor(25);
						break;
					case 10:
						_owner.add_missile_critical_rate(5);
						_owner.addDamageReductionByArmor(27);
						break;
					default:
						if (itemlvl > 10) {
							_owner.add_missile_critical_rate(5);
							_owner.addDamageReductionByArmor(itemlvl * 2 + 7);
						}
						break;
					}
				}
					//할파스의 완력
					if (itemId == 123113) {
					switch (itemlvl) {
					case 0:
						_owner.add_melee_critical_rate(5);
						_owner.addDamageReductionByArmor(7);
						break;
					case 1:
						_owner.add_melee_critical_rate(5);
						_owner.addDamageReductionByArmor(9);
						break;
					case 2:
						_owner.add_melee_critical_rate(5);
						_owner.addDamageReductionByArmor(11);
						break;
					case 3:
						_owner.add_melee_critical_rate(5);
						_owner.addDamageReductionByArmor(13);
						break;
					case 4:
						_owner.add_melee_critical_rate(5);
						_owner.addDamageReductionByArmor(15);
						break;
					case 5:
						_owner.add_melee_critical_rate(5);
						_owner.addDamageReductionByArmor(17);
						break;
					case 6:
						_owner.add_melee_critical_rate(5);
						_owner.addDamageReductionByArmor(19);
						break;
					case 7:
						_owner.add_melee_critical_rate(5);
						_owner.addDamageReductionByArmor(21);
						break;
					case 8:
						_owner.add_melee_critical_rate(5);
						_owner.addDamageReductionByArmor(23);
						break;
					case 9:
						_owner.add_melee_critical_rate(5);
						_owner.addDamageReductionByArmor(25);
						break;
					case 10:
						_owner.add_melee_critical_rate(5);
						_owner.addDamageReductionByArmor(27);
						break;
					default:
						if (itemlvl > 10) {
							_owner.add_melee_critical_rate(5);
							_owner.addDamageReductionByArmor(itemlvl * 2 + 7);
						}
						break;
					}
				}
		// TODO 현자의 휘장
		if (itemId == 900154) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(5);
				break;
			case 1:
				_owner.addMaxHp(10);
				break;
			case 2:
				_owner.addMaxHp(15);
				break;
			case 4:
				_owner.getAC().addAc(-1);
				_owner.addMaxHp(25);
				break;
			case 5:
				_owner.getAC().addAc(-2);
				_owner.addMaxHp(30);
				_owner.addHitup(1);//근거리 명중
				break;
			case 6:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(35);
				_owner.addHitup(2);//근거리 명중
				_owner.addBaseMagicCritical(1);// 마법 치명타
				break;
			case 7:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(40);
				_owner.addHitup(3);//근거리 명중
				_owner.addBaseMagicCritical(2);// 마법 치명타
				break;
			case 8:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(50);
				_owner.addHitup(4);//근거리 명중
				_owner.addBaseMagicCritical(4);// 마법 치명타
				break;
			default:
				break;
			}
		}
		
		


		/** 완력의 문장 효과 **/
		if (itemId == 900049 || itemId == 900093 || itemId == 900096) {
			switch (itemlvl) {
			case 5:
				_owner.addHitup(1);
				break;
			case 6:
				_owner.addHitup(1);
				_owner.addDmgRate(1);
				break;
			case 7:
				_owner.addHitup(2);
				_owner.addDmgRate(2);
				break;
			case 8:
				_owner.addHitup(3);
				_owner.addDmgRate(3);
				break;
			case 9:
				_owner.addHitup(4);
				_owner.addDmgRate(4);
				break;
			case 10:
				_owner.addHitup(5);
				_owner.addDmgRate(5);
				break;
			default:
				break;
			}
		}
		//고대 마물 시리즈
//		if (itemId == 900015 || itemId == 900016 || itemId == 900017 || itemId == 900018) {
//			switch (itemlvl) {
//			case 0:
//			case 1:
//			case 2:
//			case 3:
//			case 4:
//			case 5:
//			case 6:
//			case 7:
//			case 8:
//			case 9:
//			case 10:
//				_owner.addDmgRate(Config.DEDMG2);
//				_owner.addBowDmgup(Config.DEDMG2);
//				break;
//		
//			default:
//				break;
//			}
//		}
		
		
		
		
		/** 민첩의 문장 효과 **/
		if (itemId == 900050 || itemId == 900094 || itemId == 900097) {
			switch (itemlvl) {
			case 5:
				_owner.addBowHitup(1);
				break;
			case 6:
				_owner.addBowHitup(1);
				_owner.addBowDmgup(1);
				break;
			case 7:
				_owner.addBowHitup(2);
				_owner.addBowDmgup(2);
				break;
			case 8:
				_owner.addBowHitup(3);
				_owner.addBowDmgup(3);
				break;
			case 9:
				_owner.addBowHitup(4);
				_owner.addBowDmgup(4);
				break;
			case 10:
				_owner.addBowHitup(5);
				_owner.addBowDmgup(5);
				break;
			default:
				break;
			}
		}
		/** 지식의 문장 효과 **/
		if (itemId == 900051 || itemId == 900095 || itemId == 900098) {
			switch (itemlvl) {
			case 5:
				_owner.addBaseMagicHitUp(1);
				break;
			case 6:
				_owner.addBaseMagicHitUp(1);
				_owner.getAbility().addSp(1);
				break;
			case 7:
				_owner.addBaseMagicHitUp(2);
				_owner.getAbility().addSp(2);
				break;
			case 8:
				_owner.addBaseMagicHitUp(3);
				_owner.getAbility().addSp(3);
				break;
			case 9:
				_owner.addBaseMagicHitUp(4);
				_owner.getAbility().addSp(4);
				break;
			case 10:
				_owner.addBaseMagicHitUp(5);
				_owner.getAbility().addSp(5);
				break;
			default:
				break;
			}
		}
		/** 성장/회복의 문장 **/
		if (itemId == 900020 || itemId == 900021 || itemId == 900049 || itemId == 900050 || itemId == 900051 || itemId >= 900093 && itemId <= 900099) {
			switch (itemlvl) {
			case 0:
				_owner.getAC().addAc(0);
				break;
			case 1:
				_owner.getAC().addAc(1);
				break;
			case 2:
				_owner.getAC().addAc(2);
				break;
			case 3:
				_owner.getAC().addAc(3);
				break;
			case 4:
				_owner.getAC().addAc(4);
				break;
			case 5:
				_owner.getAC().addAc(5);
				break;
			case 6:
				_owner.getAC().addAc(6);
				break;
			case 7:
				_owner.getAC().addAc(7);
				break;
			case 8:
				_owner.getAC().addAc(8);
				break;
			case 9:
				_owner.getAC().addAc(9);
				break;
			case 10:
				_owner.getAC().addAc(10);
				break;
			default:
				break;
			}
		}

		/** 실프의티셔츠 내성 인챈별 **/
		if (itemId == 900019) {
			switch (itemlvl) {
			case 0:
				_owner.getResistance().addMr(12);
				_owner.addDamageReductionByArmor(1);//대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 11);
				break;
			case 1:
				_owner.getResistance().addMr(12);
				_owner.addDamageReductionByArmor(1);//대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 11);
				break;
			case 2:
				_owner.getResistance().addMr(12);
				_owner.addDamageReductionByArmor(1);//대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 11);
				break;
			case 3:
				_owner.getResistance().addMr(12);
				_owner.addDamageReductionByArmor(1);//대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 11);
				break;
			case 4:
				_owner.getResistance().addMr(12);
				_owner.addDamageReductionByArmor(1);//대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 11);
				break;
			case 5:
				_owner.getResistance().addMr(12);
				_owner.addDamageReductionByArmor(1);//대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 11);
				break;
			case 6:
				_owner.getResistance().addMr(13);
				_owner.addDamageReductionByArmor(2);//대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, 2);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 11);
				break;
			case 7:
				_owner.getResistance().addMr(14);
				_owner.addDamageReductionByArmor(3);//대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, 3);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 11);
				break;
			case 8:
				_owner.getResistance().addMr(15);
				_owner.addDamageReductionByArmor(4);//대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, 4);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 11);
				break;
			case 9:
				_owner.getResistance().addMr(16);
				_owner.addDamageReductionByArmor(5);//대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 11);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addMr(17);
				_owner.addDamageReductionByArmor(6);//대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, 6);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 11);
				break;
			default:
				break;
			}
		}

		/** 체력의 각반 **/
		if (itemId == 900023) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(10);
				break;
			case 1:
				_owner.addMaxHp(15);
				break;
			case 2:
				_owner.addMaxHp(20);
				break;
			case 3:
				_owner.addMaxHp(25);
				break;
			case 4:
				_owner.addMaxHp(30);
				break;
			case 5:
				_owner.addMaxHp(35);
				break;
			case 6:
				_owner.addMaxHp(40);
				break;
			case 7:
				_owner.addMaxHp(45);
				break;
			case 8:
				_owner.addMaxHp(50);
				break;
			case 9:
				_owner.addMaxHp(55);
				break;
			default:
				break;
			}
		}
		/** 유니콘의 완력 각반 **/
		if (itemId == 900030) {
			switch (itemlvl) {
			case 9:
			case 10:
			case 11:
			case 12:
				_owner.addDmgRate(1);
				break;
			default:
				break;
			}
		}
		/** 유니콘의 민첩 각반 **/
		if (itemId == 900031) {
			switch (itemlvl) {
			case 9:
			case 10:
			case 11:
			case 12:
				_owner.addBowHitup(1);
				break;
			default:
				break;
			}
		}
		/** 유니콘의 지식 각반 **/
		if (itemId == 900032) {
			switch (itemlvl) {
			case 9:
			case 10:
			case 11:
			case 12:
				_owner.getAbility().addSp(1);
				break;
			}
		}
		/** 맘보 모자 **/
		if (itemId == 20016 || itemId == 120016 || itemId == 20112 || itemId == 120112) {
			switch (itemlvl) {
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addCha(1);
				break;
			default:
				break;
			}
		}
		/** 예언자의 견갑 **/
		if (itemId == 900080) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBaseMagicHitUp(1);// 마법 적중
				break;
			default:
				break;
			}
		}
		/** 칠흑의 망토  **/
		if (itemId == 900076) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
				_owner.getAbility().addCha(1);
				break;
			case 7:
				_owner.getAbility().addCha(2);
				break;
			case 8:
				_owner.getAbility().addCha(3);
				break;
			case 9:
				_owner.getAbility().addCha(4);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addCha(4);
				break;
			default:
				break;
			}
		}
		/** 커츠의 투사 휘장 **/
		if (itemId == 900081) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(5);
				break;	
			case 1:
				_owner.addMaxHp(10);
				break;	
			case 2:
				_owner.addMaxHp(15);
				break;	
			case 3:
				_owner.addMaxHp(20);
				break;	
			case 4:
				_owner.getAC().addAc(-1);
				_owner.addMaxHp(25);
				break;	
			case 5:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(30);
				_owner.addHitup(1);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addcalcPcDefense(1);//PVP대미지감소
				_owner.addEinhasadBlessper(5);// 축복 소모 효율
				break;
			case 6:
				_owner.getAC().addAc(-5);//AC
				_owner.addMaxHp(35);//HP
				_owner.addHitup(1);//근거리 명중
				_owner.addDamageReductionByArmor(2);//대미지감소
				_owner.getResistance().addMr(3);//MR
				_owner.addDmgRate(2);//근거리 대미지
				_owner.getResistance().addcalcPcDefense(2);//PVP대미지감소
				_owner.addEinhasadBlessper(10);// 축복 소모 효율
				_owner.add_melee_critical_rate(1);
				
				break;
			case 7:
				_owner.getAC().addAc(-6);
				_owner.addMaxHp(40);
				_owner.addDamageReductionByArmor(3);//대미지감소
				_owner.getResistance().addMr(5);//MR
				_owner.addDmgRate(3);//근거리 대미지
				_owner.addHitup(3);//근거리 명중
				_owner.getResistance().addcalcPcDefense(3);//PVP대미지감소
				_owner.addEinhasadBlessper(15);// 축복 소모 효율
				_owner.add_melee_critical_rate(3);
				break;
			case 8:
				_owner.getAC().addAc(-7);
				_owner.addMaxHp(50);
				_owner.addDamageReductionByArmor(4);//대미지감소
				_owner.getResistance().addMr(7);//MR
				_owner.addDmgRate(4);//근거리 대미지
				_owner.addHitup(5);//근거리 명중
				_owner.getResistance().addcalcPcDefense(5);//PVP대미지감소
				_owner.addEinhasadBlessper(20);// 축복 소모 효율
				_owner.add_melee_critical_rate(5);
				break;
			case 9:
				_owner.getAC().addAc(-8);
				_owner.addMaxHp(60);
				_owner.addDamageReductionByArmor(5);//대미지감소
				_owner.getResistance().addMr(9);//MR
				_owner.addDmgRate(5);//근거리 대미지
				_owner.addHitup(7);//근거리 명중
				_owner.getResistance().addcalcPcDefense(7);//PVP대미지감소
				_owner.addEinhasadBlessper(25);// 축복 소모 효율
				_owner.add_melee_critical_rate(7);
				break;
			default:
				break;
			}
		}		
		/** 커츠의 명궁 휘장 **/
		if (itemId == 900082) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(5);
				break;
			case 1:
				_owner.addMaxHp(10);
				break;
			case 2:
				_owner.addMaxHp(15);
				break;
			case 3:
				_owner.addMaxHp(20);
				break;
			case 4:
				_owner.getAC().addAc(-1);
				_owner.addMaxHp(25);
				break;
			case 5:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(30);
				_owner.addBowHitup(1);
				_owner.addDamageReductionByArmor(1);
				_owner.getResistance().addcalcPcDefense(1);//PVP대미지감소
				_owner.addEinhasadBlessper(5);// 축복 소모 효율
				break;
			case 6:
				_owner.getAC().addAc(-5);
				_owner.addMaxHp(35);
				_owner.addDamageReductionByArmor(2);
				_owner.getResistance().addMr(3);//MR
				_owner.addBowDmgup(2);
				_owner.addBowHitup(1);
				_owner.getResistance().addcalcPcDefense(2);//PVP대미지감소
				_owner.addEinhasadBlessper(10);// 축복 소모 효율
				_owner.add_missile_critical_rate(1);
				break;
			case 7:
				_owner.getAC().addAc(-6);
				_owner.addMaxHp(40);
				_owner.addDamageReductionByArmor(3);
				_owner.getResistance().addMr(5);//MR
				_owner.addBowDmgup(3);
				_owner.addBowHitup(3);
				_owner.getResistance().addcalcPcDefense(3);//PVP대미지감소
				_owner.addEinhasadBlessper(15);// 축복 소모 효율
				_owner.add_missile_critical_rate(3);
				break;
			case 8:
				_owner.getAC().addAc(-7);
				_owner.addMaxHp(50);
				_owner.addDamageReductionByArmor(4);//대미지 감소
				_owner.getResistance().addMr(7);//MR
				_owner.addBowDmgup(4);
				_owner.addBowHitup(5);
				_owner.getResistance().addcalcPcDefense(5);//PVP대미지감소
				_owner.addEinhasadBlessper(20);// 축복 소모 효율
				_owner.add_missile_critical_rate(5);
				break;
			case 9:
				_owner.getAC().addAc(-8);
				_owner.addMaxHp(60);
				_owner.addDamageReductionByArmor(5);//대미지 감소
				_owner.getResistance().addMr(9);//MR
				_owner.addBowDmgup(5);
				_owner.addBowHitup(7);
				_owner.getResistance().addcalcPcDefense(7);//PVP대미지감소
				_owner.addEinhasadBlessper(25);// 축복 소모 효율
				_owner.add_missile_critical_rate(7);
				break;
			default:
				break;
			}
		}	
		/** 커츠의 현자 휘장 **/
		if (itemId == 900083) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(5);
				break;
			case 1:
				_owner.addMaxHp(10);
				break;
			case 2:
				_owner.addMaxHp(15);
				break;
			case 3:
				_owner.addMaxHp(20);
				break;
			case 4:
				_owner.getAC().addAc(-1);
				_owner.addMaxHp(25);
				break;
			case 5:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(30);
				_owner.addDamageReductionByArmor(1);//대미지 감소
				_owner.addHitup(1);
				_owner.getResistance().addcalcPcDefense(1);//PVP대미지감소
				_owner.addEinhasadBlessper(5);// 축복 소모 효율
				break;
			case 6:
				_owner.getAC().addAc(-5);
				_owner.addMaxHp(35);
				_owner.addDamageReductionByArmor(2);//대미지 감소
				_owner.getResistance().addMr(3);//MR
				_owner.addHitup(2);//근거리 명중
				_owner.addBaseMagicCritical(1);// 마법 치명타
				_owner.getResistance().addcalcPcDefense(2);//PVP대미지감소
				_owner.addEinhasadBlessper(10);// 축복 소모 효율
				break;
			case 7:
				_owner.getAC().addAc(-6);
				_owner.addMaxHp(40);
				_owner.addDamageReductionByArmor(3);//대미지 감소
				_owner.getResistance().addMr(5);//MR
				_owner.addHitup(3);//근거리 명중
				_owner.addBaseMagicCritical(2);// 마법 치명타
				_owner.getResistance().addcalcPcDefense(3);//PVP대미지감소
				_owner.addEinhasadBlessper(15);// 축복 소모 효율
				break;
			case 8:
				_owner.getAC().addAc(-7);
				_owner.addMaxHp(50);
				_owner.addDamageReductionByArmor(4);//대미지 감소
				_owner.getResistance().addMr(7);//MR
				_owner.addHitup(4);//근거리 명중
				_owner.addBaseMagicCritical(3);// 마법 치명타
				_owner.getResistance().addcalcPcDefense(5);//PVP대미지감소
				_owner.addEinhasadBlessper(20);// 축복 소모 효율
				break;
			case 9:
				_owner.getAC().addAc(-8);
				_owner.addMaxHp(60);
				_owner.addDamageReductionByArmor(5);//대미지 감소
				_owner.getResistance().addMr(9);//MR
				_owner.addHitup(5);//근거리 명중
				_owner.addBaseMagicCritical(4);// 마법 치명타
				_owner.getResistance().addcalcPcDefense(7);//PVP대미지감소
				_owner.addEinhasadBlessper(25);// 축복 소모 효율
				break;
			default:
				break;
			}
		}	
		/** 커츠의 수호 휘장 **/
		if (itemId == 900084) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(5);
				break;	
			case 1:
				_owner.addMaxHp(10);
				break;	
			case 2:
				_owner.addMaxHp(15);
				break;	
			case 3:
				_owner.getAC().addAc(-1);
				_owner.addMaxHp(20);
				break;	
			case 4:
				_owner.getAC().addAc(-2);
				_owner.addMaxHp(25);
				break;	
			case 5:
				_owner.getAC().addAc(-3);
				_owner.addMaxHp(30);
				_owner.addDamageReductionByArmor(1);
				break;	
			case 6:
				_owner.getAC().addAc(-5);
				_owner.addMaxHp(35);
				_owner.addDamageReductionByArmor(2);
				_owner.getResistance().addMr(3);
				break;	
			case 7:
				_owner.getAC().addAc(-6);
				_owner.addMaxHp(40);
				_owner.addDamageReductionByArmor(3);
				_owner.getResistance().addMr(5);
				break;	
			case 8:
				_owner.getAC().addAc(-7);
				_owner.addMaxHp(50);
				_owner.addDamageReductionByArmor(4);
				_owner.getResistance().addMr(7);
				break;
			default:
				break;
			}
		}	
		/** 거대여왕 개미의 금빛,은빛 날개 **/
		if (itemId == 20049 || itemId == 20050 || itemId == 900056 || itemId == 900057) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 3);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 4);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 5);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 6);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 7);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 7);
				break;
			}
		}
		/** 리치의 반지 **/
		if (itemId == 900163) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
				_owner.getAbility().addSp(3);
				_owner.addBaseMagicCritical(3);// 마법 치명타
				break;
			case 7:
				_owner.getAbility().addSp(4);
				_owner.addBaseMagicCritical(3);// 마법 치명타
				break;
			case 8:
				_owner.getAbility().addSp(5);
				_owner.addBaseMagicCritical(3);// 마법 치명타
				break;
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addSp(6);
				_owner.addBaseMagicCritical(3);// 마법 치명타
				break;
			}
		}

		if (itemId == 988171) {
			switch (itemlvl) {
			case 0:
				break;
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:	
			case 7:	
				_owner.getAC().addAc(-5);
				_owner.addMaxHp(50);
				_owner.addMaxMp(20);
				_owner.getResistance().addMr(20);
				_owner.addEffectedER(2);
				_owner.getResistance().addPVPweaponTotalDamage(1);
				break;
			case 8:	
				_owner.getAC().addAc(-6);
				_owner.addMaxHp(60);
				_owner.addMaxMp(30);
				_owner.getResistance().addMr(21);
				_owner.addEffectedER(3);
				_owner.getResistance().addPVPweaponTotalDamage(2);
				break;
			case 9:		
				_owner.getAC().addAc(-7);
				_owner.addMaxHp(70);
				_owner.addMaxMp(40);
				_owner.getResistance().addMr(22);
				_owner.addEffectedER(4);
				_owner.getResistance().addPVPweaponTotalDamage(3);
				break;
			case 10:
				_owner.getAC().addAc(-8);
				_owner.addMaxHp(80);
				_owner.addMaxMp(50);
				_owner.getResistance().addMr(23);
				_owner.addEffectedER(5);
				_owner.getResistance().addPVPweaponTotalDamage(4);
				break;
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				break;

			}
		}
		/** 뱀파이어의 망토 **/
		if (itemId == 20079) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 3);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 4);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 5);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 6);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, 7);
				break;
			}
		}
		/** 아이리스의 부츠 **/
		if (itemId == 900155) {
			switch (itemlvl) {
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.ALL, 2);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.ALL, 3);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.ALL, 4);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				break;
			default:
				break;
			}
		}
		/** 머미로드의 장갑 **/
		if (itemId == 900156) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
				_owner.getAbility().addSp(1);
				break;
			case 7:
				_owner.getAbility().addSp(2);
				break;
			case 8:
				_owner.getAbility().addSp(3);
				break;
			case 9:
				_owner.getAbility().addSp(3);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addSp(3);
				break;
			default:
				break;
			}
		}
		/** 쿠거의 가더 **/
		if (itemId == 900157) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addHitup(3);
				break;
			case 5:
				_owner.addHitup(4);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				break;
			case 6:
				_owner.addHitup(5);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 2);
				break;
			case 7:
				_owner.addHitup(6);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 3);
				break;
			case 8:
				_owner.addHitup(7);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 4);
				break;
			case 9:
				_owner.addHitup(8);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addHitup(8);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				break;
			default:
				break;
			}
		}
		/** 우그누스의 가더 **/
		if (itemId == 900158) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addBowHitup(3);
				break;
			case 5:
				_owner.addBowHitup(4);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				break;
			case 6:
				_owner.addBowHitup(5);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 2);
				break;
			case 7:
				_owner.addBowHitup(6);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 3);
				break;
			case 8:
				_owner.addBowHitup(7);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 4);
				break;
			case 9:
				_owner.addBowHitup(8);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBowHitup(8);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 5);
				break;
			default:
				break;
			}
		}
		/** 나이트발드의 각반 **/
		if (itemId == 900159) {
			switch (itemlvl) {
			case 5:
				_owner.addHitup(1);
				break;
			case 6:
				_owner.addBowHitup(2);
				break;
			case 7:
				_owner.addBowHitup(3);
				break;
			case 8:
				_owner.addBowHitup(4);
				break;
			case 9:
				_owner.addBowHitup(5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBowHitup(5);
				break;
			default:
				break;
			}
		}
		/** 아이리스의 각반 **/
		if (itemId == 900160) {
			switch (itemlvl) {
			case 5:
				_owner.addBowHitup(1);
				break;
			case 6:
				_owner.addBowHitup(2);
				break;
			case 7:
				_owner.addBowHitup(3);
				break;
			case 8:
				_owner.addBowHitup(4);
				break;
			case 9:
				_owner.addBowHitup(5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBowHitup(5);
				break;
			default:
				break;
			}
		}
		/** 뱀파이어의 각반 **/
		if (itemId == 900161) {
			switch (itemlvl) {
			case 5:
				_owner.addBaseMagicHitUp(1);
				break;
			case 6:
				_owner.addBaseMagicHitUp(2);
				break;
			case 7:
				_owner.addBaseMagicHitUp(3);
				break;
			case 8:
				_owner.addBaseMagicHitUp(4);
				break;
			case 9:
				_owner.addBaseMagicHitUp(5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBaseMagicHitUp(5);
				break;
			default:
				break;
			}
		}
		/** 대마법사의 모자 **/
		if (itemId == 202022) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 2);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 3);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 4);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 5);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 6);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, 7);
				break;
			default:
				break;
			}
		}
		/** 지룡의 티셔츠 **/
		if (itemId == 900025) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addMr(4);
				break;
			case 6:
				_owner.getResistance().addMr(5);
				break;
			case 7:
				_owner.addDamageReductionByArmor(1);
				break;
			case 8:
				_owner.getResistance().addMr(8);
				_owner.addDamageReductionByArmor(1);
				break;
			case 9:
				_owner.getResistance().addMr(11);
				_owner.addDamageReductionByArmor(2);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addMr(14);
				_owner.addDamageReductionByArmor(2);
				_owner.addMaxHp(100);
				_owner.getResistance().addPVPweaponTotalDamage(1);
				_owner.getResistance().addcalcPcDefense(1);
				break;
			}
		}
		/** 화룡의 티셔츠 **/
		if (itemId == 900026) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addDmgRate(1);
				break;
			case 5:
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 8);
				break;
			case 6:
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
				break;
			case 7:
				_owner.addDmgRate(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 10);
				break;
			case 8:
				_owner.addDmgRate(1);
				_owner.addHitup(2);// 근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 12);
				break;
			case 9:
				_owner.addDmgRate(2);
				_owner.addHitup(4);// 근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 15);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addDmgRate(2);
				_owner.addHitup(6);// 근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 18);
				_owner.addMaxHp(100);
				_owner.getResistance().addPVPweaponTotalDamage(1);
				_owner.getResistance().addcalcPcDefense(1);
				break;
			default:
				break;
			}
		}
		/** 풍룡의 티셔츠 **/
		if (itemId == 900027) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addBowHitup(1);
				break;
			case 5:
				_owner.addBowHitup(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 8);
				break;
			case 6:
				_owner.addBowHitup(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
				break;
			case 7:
				_owner.addBowHitup(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 10);
				break;
			case 8:
				_owner.addBowHitup(1);
				_owner.addBowHitup(2);// 원거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 12);
				break;
			case 9:
				_owner.addBowHitup(2);
				_owner.addBowHitup(3);// 원거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 15);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBowHitup(2);
				_owner.addBowHitup(6);// 원거리 명중
				_owner.addMaxHp(100);
				_owner.getResistance().addPVPweaponTotalDamage(1);
				_owner.getResistance().addcalcPcDefense(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 18);
				break;
			default:
				break;
			}
		}
		/** 수룡의 티셔츠 **/
		if (itemId == 900028) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getAbility().addSp(1);
				break;
			case 5:
				_owner.getAbility().addSp(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 8);
				break;
			case 6:
				_owner.getAbility().addSp(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
				break;
			case 7:
				_owner.getAbility().addSp(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 10);
				break;
			case 8:
				_owner.getAbility().addSp(2);
				_owner.addBaseMagicHitUp(1);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 12);
				break;
			case 9:
				_owner.getAbility().addSp(2);
				_owner.addBaseMagicHitUp(3);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 15);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addSp(3);
				_owner.addBaseMagicHitUp(4);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 18);
				_owner.addMaxHp(100);
				_owner.getResistance().addPVPweaponTotalDamage(1);
				_owner.getResistance().addcalcPcDefense(1);
				break;
			default:
				break;
			}
		}
		/** 축복받은 지룡의 티셔츠 **/
		if (itemId == 900184) {
			switch (itemlvl) {
			case 5:
				_owner.getResistance().addMr(4);
				break;
			case 6:
				_owner.getResistance().addMr(5);
				break;
			case 7:
				_owner.getResistance().addMr(6);
				_owner.addDamageReductionByArmor(1);
				break;
			case 8:
				_owner.getResistance().addMr(8);
				_owner.addDamageReductionByArmor(1);
				break;
			case 9:
				_owner.getResistance().addMr(11);
				_owner.addDamageReductionByArmor(2);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addMr(14);
				_owner.addDamageReductionByArmor(2);
				_owner.addMaxHp(100);
				_owner.getResistance().addPVPweaponTotalDamage(1);
				_owner.getResistance().addcalcPcDefense(1);
				break;
			}
		}
		/** 축복받은 화룡의 티셔츠 **/
		if (itemId == 900185) {
			switch (itemlvl) {
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 8);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
				break;
			case 7:
				_owner.addDmgRate(1);
				_owner.addHitup(1);// 근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 10);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				break;
			case 8:
				_owner.addDmgRate(1);
				_owner.addHitup(3);// 근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 12);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 2);
				break;
			case 9:
				_owner.addDmgRate(2);
				_owner.addHitup(5);// 근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 15);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 4);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addDmgRate(2);
				_owner.addHitup(7);// 근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 18);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 6);
				_owner.addMaxHp(100);
				_owner.getResistance().addPVPweaponTotalDamage(1);
				_owner.getResistance().addcalcPcDefense(1);
				break;
			default:
				break;
			}
		}
		/** 축복받은 풍룡의 티셔츠 **/
		if (itemId == 900186) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addBowHitup(1);
				break;
			case 5:
				_owner.addBowHitup(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 8);
				break;
			case 6:
				_owner.addBowHitup(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
				break;
			case 7:
				_owner.addBowHitup(2);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 10);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				break;
			case 8:
				_owner.addBowHitup(3);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 12);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 2);
				break;
			case 9:
				_owner.addBowHitup(5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 15);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 4);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBowHitup(7);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 18);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 6);
				_owner.addMaxHp(100);
				_owner.getResistance().addPVPweaponTotalDamage(1);
				_owner.getResistance().addcalcPcDefense(1);
				break;
			default:
				break;
			}
		}
		/** 축복받은 수룡의 티셔츠 **/
		if (itemId == 900187) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getAbility().addSp(1);
				break;
			case 5:
				_owner.getAbility().addSp(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 8);
				break;
			case 6:
				_owner.getAbility().addSp(1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
				break;
			case 7:
				_owner.getAbility().addSp(2);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 10);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
				break;
			case 8:
				_owner.getAbility().addSp(2);
				_owner.addBaseMagicHitUp(2);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 12);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 2);
				break;
			case 9:
				_owner.getAbility().addSp(2);
				_owner.addBaseMagicHitUp(4);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 15);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 4);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addSp(3);
				_owner.addBaseMagicHitUp(5);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, 18);
				_owner.getResistance().addSpecialResistance(eKind.ALL, 6);
				_owner.addMaxHp(100);
				_owner.getResistance().addPVPweaponTotalDamage(1);
				_owner.getResistance().addcalcPcDefense(1);
				break;
			default:
				break;
			}
		}
		
		/** 스텟부츠 리뉴얼 **/
		if (itemId == 22359 || itemId == 222308 || itemId ==222309 || itemId ==222307) {
			switch (itemlvl) {
			case 7:
				_owner.addMaxHp(20);
				break;
			case 8:
				_owner.addMaxHp(40);
				break;
			case 9:
				_owner.addMaxHp(60);
				_owner.getResistance().addcalcPcDefense(1);
				break;
			case 10:
				_owner.addMaxHp(60);
				_owner.getResistance().addcalcPcDefense(1);
				break;
			default:
				break;
			}
		}
		/** 고대 투사의 가더 **/
		if (itemId == 22003) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addDmgRate(1);
				break;
			case 5:
			case 6:
				_owner.addDmgRate(2);
				break;
			case 7:
			case 8:
				_owner.addDmgRate(3);
				break;
			case 9:
			case 10:
				_owner.addDmgRate(4);
				break;
			default:
				break;
			}
		}
		/** 고대 명궁의 가더 **/
		if (itemId == 22000) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addBowHitup(1);
				break;
			case 5:
			case 6:
				_owner.addBowHitup(2);
				break;
			case 7:
			case 8:
				_owner.addBowHitup(3);
				break;
			case 9: case 10:
				_owner.addBowHitup(4);
				break;
			default:
				break;
			}
		}
		/** 호박 목걸이 **/
		if (itemId == 900024) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
				_owner.addBaseMagicHitUp(3);// 마법 적중
				break;
			}
		}
		/** 수령의 귀걸이 **/
		if (itemId == 900114) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
				_owner.addBaseMagicHitUp(2);
				break;
			}
		}
		/** 수호의 가더 **/
		if (itemId == 22254) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addDamageReductionByArmor(1);
				break;
			case 5:
			case 6:
				_owner.addDamageReductionByArmor(2);
				break;
			case 7:
			case 8:
				_owner.addDamageReductionByArmor(3);
				break;
			case 9:
			case 10:
				_owner.addDamageReductionByArmor(4);
				break;
			}
		}
		/** 흑기사의 면갑 대미지감소 **/
		if (itemId == 900038 || itemId == 900054) {
			switch (itemlvl) {
			case 5:
				_owner.getResistance().addMr(4);
				break;
			case 6:
				_owner.getResistance().addMr(8);
				break;
			case 7:
				_owner.getResistance().addMr(12);
				break;
			case 8:
				_owner.getResistance().addMr(16);
				break;
			case 9:
				_owner.getResistance().addMr(20);
				break;
			case 10:
				_owner.getResistance().addMr(24);
				break;
			}
		}
		/** 발라카스의 완력 **/
		if (itemId >= 22208 && itemId <= 22211) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 2);
				break;
			case 6:
				_owner.getResistance().addcalcPcDefense(3);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 3);
				break;
			case 7:
				_owner.getResistance().addcalcPcDefense(4);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 4);
				break;
			case 8:
				_owner.getResistance().addcalcPcDefense(5);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
				break;
			case 9:
				_owner.getResistance().addcalcPcDefense(6);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 6);
				break;
			case 10:
				_owner.getResistance().addcalcPcDefense(7);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 7);
				break;
			}
		}
		
		/** 신성한 엘름의 축복 **/
		if (itemId == 900035 || itemId == 900072) {
			switch (itemlvl) {
			case 5:
				_owner.getResistance().addMr(4);
				break;
			case 6:
				_owner.getResistance().addMr(8);
				break;
			case 7:
				_owner.getResistance().addMr(12);
				break;
			case 8:
				_owner.getResistance().addMr(16);
				break;
			case 9:
				_owner.getResistance().addMr(20);
				break;
			case 10:
				_owner.getResistance().addMr(24);
				break;
			default:
				break;
			}
		}
		if (itemId == 900039) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
				_owner.addHitup(2);
				break;
			default:
				break;
			}
		}
		if (itemId == 900040) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
				_owner.addBowHitup(2);
				break;
			default:
				break;
			}
		}
		if (itemId == 900041) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
				_owner.addBaseMagicHitUp(2);// 마법 적중
				break;
			default:
				break;
			}
		}
		if (itemId == 900036 || itemId == 900073) {
			switch (itemlvl) {
			case 5:
				_owner.addHitup(1);
				break;
			case 6:
				_owner.addHitup(2);
				break;
			case 7:
				_owner.addHitup(3);
				break;
			case 8:
				_owner.addHitup(4);
				break;
			case 9:
				_owner.addHitup(5);
				break;
			case 10:
				_owner.addHitup(5);
				break;
			default:
				 if (itemlvl > 10) {
					 _owner.addHitup(5);
					}
				break;
			}
		}
		if (itemId == 900037 || itemId == 900074) {
			switch (itemlvl) {
			case 5:
				_owner.addBowHitup(1);
				break;
			case 6:
				_owner.addBowHitup(2);
				break;
			case 7:
				_owner.addBowHitup(3);
				break;
			case 8:
				_owner.addBowHitup(4);
				break;
			case 9:
				_owner.addBowHitup(5);
				break;
			case 10:
				_owner.addBowHitup(5);
				break;
			default:
				 if (itemlvl > 10) {
					 _owner.addBowHitup(5);
					}
				break;
			}
		}
		/** 안타라스의 완력/예지력/인내력/마력 **/
		if (itemId >= 22196 && itemId <= 22199) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 2);
				break;
			case 6:
				_owner.addDamageReductionByArmor(3);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 3);
				break;
			case 7:
				_owner.addDamageReductionByArmor(4);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 4);
				break;
			case 8:
				_owner.addDamageReductionByArmor(5);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
				break;
			case 9:
				_owner.addDamageReductionByArmor(6);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 6);
				break;
			case 10:
				_owner.addDamageReductionByArmor(7);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 7);
				break;
			default:
				break;
			}
		}
		/** 파푸리온의 완력/예지력/인내력/마력 **/
		if (itemId >= 22200 && itemId <= 22203) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 2);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 3);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 4);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 6);
				break;
			case 10:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 7);
				break;
			default:
				break;
			}
		}
		/** 린드비오르의 완력/예지력/인내력/마력 **/
		if (itemId >= 22204 && itemId <= 22207) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 2);
				break;
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 3);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 4);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 6);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 7);
				break;
			case 10:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 8);
				break;
			default:
				if (itemlvl > 10) {
					_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 9);
				}
				break;
			}
		}
		/** 빛나는 마력의장갑 **/
		if (itemId == 20274) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 3:
			case 4:
				_owner.addWeightReduction(300);
				break;
			default:
				if (itemlvl > 4) {
					_owner.addWeightReduction(300 + ((itemlvl - 4) * 60));
					}
				break;
			}
		}
		/** 격분의 장갑 **/
		if (itemId == 222317) {
			switch (itemlvl) {
			case 7:
				
				_owner.addHitup(4);
				break;
			case 8:
			
				_owner.addHitup(5);
				break;
			case 9:
				
				_owner.addHitup(6);
				break;
			case 10:
				
				_owner.addHitup(7);
				break;
			default:
				if (itemlvl > 10) {
					
					_owner.addHitup(itemlvl - 3);
					}
				break;
			}
		}
		
		
		
		if (itemId == 22214) {
			if (itemlvl > 6) {
				_owner.addBaseMagicHitUp(itemlvl - 6);
			}
		}
		if (itemId == 20279) {

			_owner.getResistance().addcalcPcDefense(2);
			

			}
		if (itemId == 20314) {

			_owner.getResistance().addPVPweaponTotalDamage(2);
			

			}
		
		
		/** 머미로드의 왕관 **/
		if (itemId == 20017) {
			 int cc = itemlvl - 4;
			 if (itemlvl > 4) {
						_owner.addBowDmgup(itemlvl);
						_owner.getResistance().addMr(cc * 3);
					}
			}

//		if (itemId == 22200 || itemId == 22201 || itemId == 22202 || itemId == 22203) {// 파푸리온
//		//	_owner.startPapuBlessing();
//		}else if(itemId >= 22208 && itemId <= 22211 || itemId == 922208)
//		//	_owner.startValaBlessing();
		
		//TODO (장신구) 목걸이,귀걸이 인챈트 리뉴얼
		if (itemtype >= 8 && itemtype <= 12) {
			if ((itemgrade >= 0 && itemgrade <= 2) && itemtype == 8 ) { //목걸이
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(5);
					break;
				case 2:
					_owner.addMaxHp(10);
					break;
				case 3:
					_owner.addMaxHp(20);
					break;
				case 4:
					_owner.addMaxHp(30);
					break;
				case 5:
					_owner.setAccessoryHeal(3);
					_owner.addMaxHp(40);
					_owner.getResistance().addMr(1);
					break;
				case 6:
					_owner.setAccessoryHeal(5);
					_owner.addMaxHp(40);
					_owner.getResistance().addMr(3);
					break;
				case 7:
					_owner.setAccessoryHeal(7);
					_owner.addMaxHp(50);
					_owner.getResistance().addMr(5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 2);
					break;
				case 8:
					_owner.setAccessoryHeal(9);
					_owner.addMaxHp(50);
					_owner.getResistance().addMr(7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 3);
					break;
				case 9:
					_owner.setAccessoryHeal(10);
					_owner.addMaxHp(60);
					_owner.getResistance().addMr(10);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 4);
					break;
				case 10:
					_owner.setAccessoryHeal(11);
					_owner.addMaxHp(100);
					_owner.getResistance().addMr(12);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 4);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
					break;
				}
			} else if ((itemgrade >= 0 && itemgrade <= 2) && itemtype == 12) { //귀걸이
					switch (itemlvl) {
					case 1:
						_owner.addMaxHp(5);
						break;
					case 2:
						_owner.addMaxHp(10);
						break;
					case 3:
						_owner.addMaxHp(20);
						break;
					case 4:
						_owner.addMaxHp(30);
						break;
					case 5:
						_owner.setAccessoryHeal(2);
						_owner.addMaxHp(40);
						
						break;
					case 6:
						_owner.setAccessoryHeal(4);
						_owner.addMaxHp(40);
						
						break;
					case 7:
						_owner.setAccessoryHeal(6);
						_owner.addMaxHp(50);
						_owner.getResistance().addSpecialResistance(eKind.ABILITY, 2);
						break;
					case 8:
						_owner.setAccessoryHeal(8);
						_owner.addMaxHp(50);
						_owner.getResistance().addSpecialResistance(eKind.ABILITY, 3);
						break;
					case 9:
						_owner.setAccessoryHeal(9);
						_owner.addMaxHp(60);
						_owner.getResistance().addSpecialResistance(eKind.ABILITY, 4);
						break;
					case 10:
						_owner.setAccessoryHeal(10);
						_owner.addMaxHp(70);
						_owner.getResistance().addSpecialResistance(eKind.ABILITY, 4);
						_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
						_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
						break;
					}
				// TODO (장신구) 반지 인챈트 리뉴얼
			} else
				
				
				if (((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5) && (itemtype == 9 || itemtype == 11)) {
					switch (itemlvl) {
					case 1:
						_owner.addMaxHp(5);
						break;
					case 2:
						_owner.addMaxHp(10);
						break;
					case 3:
						_owner.addMaxHp(20);
						break;
					case 4:
						_owner.addMaxHp(30);
						break;
					case 5:
						_owner.addDmgup(1);
						_owner.addBowDmgup(1);
						_owner.addMaxHp(40);
						break;
					case 6:
						_owner.addDmgup(2);
						_owner.addBowDmgup(2);
						_owner.addMaxHp(40);
						_owner.getResistance().addMr(1);
						_owner.getResistance().addPVPweaponTotalDamage(1);
						break;
					case 7:
						_owner.addDmgup(3);
						_owner.addBowDmgup(3);
						_owner.addMaxHp(50);
						if (itemId != 900163) {
						_owner.getAbility().addSp(1);
						}
						_owner.getResistance().addMr(3);
						_owner.getResistance().addPVPweaponTotalDamage(2);
						break;
					case 8:
						_owner.addDmgup(4);
						_owner.addBowDmgup(4);
						_owner.addMaxHp(50);
						if (itemId != 900163) {
						_owner.getAbility().addSp(2);
						}
						_owner.getResistance().addMr(5);
						_owner.getResistance().addPVPweaponTotalDamage(3);
						break;
					case 9:
						_owner.addDmgup(5);
						_owner.addBowDmgup(5);
						_owner.addMaxHp(60);
						if (itemId != 900163) {
						_owner.getAbility().addSp(3);
						}
						_owner.getResistance().addMr(7);
						_owner.getResistance().addPVPweaponTotalDamage(5);
						break;
					case 10:
						_owner.addDmgup(6);
						_owner.addBowDmgup(6);
						_owner.addMaxHp(70);
						if (itemId != 900163) {
						_owner.getAbility().addSp(4);
						}
						_owner.getResistance().addMr(9);
						_owner.getResistance().addPVPweaponTotalDamage(7);
						_owner.getResistance().addSpecialResistance(eKind.ALL, 1);
						break;
					}
					// TODO (장신구) 벨트 인챈트 리뉴얼
				} else
				
				
				if ((itemgrade >= 0 && itemgrade <= 2) && (itemtype == 10)) {
					if (itemId == 900007 || itemId == 500012) {
						_owner.getResistance().addcalcPcDefense(2);
					}
				switch (itemlvl) {
				case 1:
					_owner.addMaxMp(5);
					break;
				case 2:
					_owner.addMaxMp(10);
					break;
				case 3:
					_owner.addMaxMp(20);
					break;
				case 4:
					_owner.addMaxMp(30);
					break;
				case 5:
					_owner.addDamageReductionByArmor(1);
					_owner.addMaxMp(40);
					break;
				case 6:
					_owner.addDamageReductionByArmor(2);
					_owner.addMaxMp(40);
					_owner.addMaxHp(20);
					_owner.getResistance().addcalcPcDefense(1);
					break;
				case 7:
					_owner.addDamageReductionByArmor(3);
					_owner.addMaxMp(50);
					_owner.addMaxHp(30);
					_owner.getResistance().addcalcPcDefense(3);
					break;
				case 8:
					_owner.addDamageReductionByArmor(4);
					_owner.addMaxMp(50);
					_owner.addMaxHp(40);
					_owner.getResistance().addcalcPcDefense(5);
					break;
				case 9:
					_owner.addDamageReductionByArmor(5);
					_owner.addMaxMp(60);
					_owner.addMaxHp(50);
					_owner.getResistance().addcalcPcDefense(7);
					break;
				case 10:
					_owner.addDamageReductionByArmor(6);
					_owner.addMaxMp(70);
					_owner.addMaxHp(100);
					_owner.getResistance().addcalcPcDefense(9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
					break;
				}
					
			} else if (itemgrade == 3 && itemId == 22224 || itemId == 22225) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(15);
					break;
				case 2:
					_owner.addMaxHp(20);
					_owner.getAC().addAc(-1);
					break;
				case 3:
					_owner.addMaxHp(25);
					_owner.getAC().addAc(-2);
					break;
				case 4:
					_owner.addMaxHp(30);
					_owner.getAC().addAc(-3);
					break;
				case 5:
					_owner.addMaxHp(35);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(1);
					break;
				case 6:
					_owner.addMaxHp(40);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(2);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
					break;
				case 7:
					_owner.addMaxHp(45);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					break;
				case 8:
					_owner.addMaxHp(50);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addPVPweaponTotalDamage(2);
					break;
				default:
					break;
				}
			} else if (itemgrade == 3 && itemId == 22227) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(15);
					break;
				case 2:
					_owner.addMaxHp(20);
					_owner.getAC().addAc(-1);
					break;
				case 3:
					_owner.addMaxHp(25);
					_owner.getAC().addAc(-2);
					break;
				case 4:
					_owner.addMaxHp(30);
					_owner.getAC().addAc(-3);
					break;
				case 5:
					_owner.addMaxHp(35);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(1);
					break;
				case 6:
					_owner.addMaxHp(40);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(2);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
					break;
				case 7:
					_owner.addMaxHp(45);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					break;
				case 8:
					_owner.addMaxHp(50);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addPVPweaponTotalDamage(2);
					break;
				default:
					break;
				}
				// 스냅퍼의 마법저항 반지 착용
			} else if (itemgrade == 3 && itemId == 22228) {
				switch (itemlvl) {
				case 0:
					_owner.getResistance().addMr(7);
					break;
				case 1:
					_owner.addMaxHp(15);
					_owner.getResistance().addMr(7);
					break;
				case 2:
					_owner.addMaxHp(20);
					_owner.getAC().addAc(-1);
					_owner.getResistance().addMr(7);
					break;
				case 3:
					_owner.addMaxHp(25);
					_owner.getAC().addAc(-2);
					_owner.getResistance().addMr(7);
					break;
				case 4:
					_owner.addMaxHp(30);
					_owner.getAC().addAc(-3);
					_owner.getResistance().addMr(7);
					break;
				case 5:
					_owner.addMaxHp(35);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(1);
					_owner.getResistance().addMr(7);
					break;
				case 6:
					_owner.addMaxHp(40);
					_owner.getAC().addAc(-4);
					_owner.addDmgup(2);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
					_owner.getResistance().addMr(7);
					break;
				case 7:
					_owner.addMaxHp(45);
					_owner.getAC().addAc(-4);
					_owner.addDmgup(3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					_owner.getResistance().addMr(7);
					break;
				case 8:
					_owner.addMaxHp(50);
					_owner.getAC().addAc(-6);
					_owner.addDmgup(5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 1);
					_owner.getResistance().addPVPweaponTotalDamage(3);
					_owner.getResistance().addMr(9);
					break;
				case 9:
					_owner.addMaxHp(60);
					_owner.getAC().addAc(-8);
					_owner.addDmgup(7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
					_owner.getResistance().addPVPweaponTotalDamage(7);
					_owner.getResistance().addMr(12);
					break;
				default:
					break;
				}
			
				//TODO 스냅퍼의 체력 반지 리뉴얼
			} else if (itemgrade == 3 && itemId == 22226) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(15);
					
					break;
				case 2:
					_owner.addMaxHp(20);
					_owner.getAC().addAc(-1);
					break;
				case 3:
					_owner.addMaxHp(25);
					_owner.getAC().addAc(-2);
					break;
				case 4:
					_owner.addMaxHp(30);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(1);
					break;
				case 5:
					_owner.addMaxHp(35);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(2);
					break;
				case 6:
					_owner.addMaxHp(40);
					_owner.getAC().addAc(-4);
					_owner.addDmgup(3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
					_owner.addDamageReductionByArmor(1);
					break;
				case 7:
					_owner.addMaxHp(45);
					_owner.getAC().addAc(-4);
					_owner.addDmgup(4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					_owner.addDamageReductionByArmor(2);
					break;
				case 8:
					_owner.addMaxHp(50);
					_owner.getAC().addAc(-6);
					_owner.addDmgup(5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR,1);
					_owner.getResistance().addPVPweaponTotalDamage(3);
					_owner.addDamageReductionByArmor(3);
					break;
				case 9:
					_owner.addMaxHp(55);
					_owner.getAC().addAc(-8);
					_owner.addDmgup(7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR,2);
					_owner.getResistance().addPVPweaponTotalDamage(7);
					_owner.addDamageReductionByArmor(5);
					break;
				default:
					break;
				}
			} else if (itemgrade == 3 && itemId == 222290) { // 지혜의 반지
				switch (itemlvl) {
				case 0:
					_owner.addMaxMp(15);
					break;
				case 1:
					_owner.addMaxHp(5);
					_owner.addMaxMp(15);
					break;
				case 2:
					_owner.addMaxHp(10);
					_owner.addMaxMp(15);
					_owner.getAC().addAc(-1);
					break;
				case 3:
					_owner.addMaxHp(15);
					_owner.addMaxMp(15);
					_owner.getAC().addAc(-2);
					break;
				case 4:
					_owner.addMaxHp(20);
					_owner.addMaxMp(15);
					_owner.getAC().addAc(-3);
					break;
				case 5:
					_owner.addMaxHp(25);
					_owner.addMaxMp(15);
					_owner.getAC().addAc(-3);
					_owner.getAbility().addSp(1);
					break;
				case 6:
					_owner.addMaxHp(30);
					_owner.addMaxMp(15);
					_owner.getAC().addAc(-4);
					_owner.getAbility().addSp(2);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
					break;
				case 7:
					_owner.addMaxHp(35);
					_owner.addMaxMp(15);
					_owner.getAC().addAc(-4);
					_owner.getAbility().addSp(3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					break;
				case 8:
					_owner.addMaxHp(40);
					_owner.addMaxMp(30);
					_owner.getAC().addAc(-6);
					_owner.getAbility().addSp(5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 1);
					_owner.getResistance().addPVPweaponTotalDamage(3);
					break;
				case 9:
					_owner.addMaxHp(50);
					_owner.addMaxMp(70);
					_owner.getAC().addAc(-8);
					_owner.getAbility().addSp(7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
					_owner.getResistance().addPVPweaponTotalDamage(7);
					break;
				default:
					break;
				}
			} else if (itemgrade == 3 && itemId == 222291) { // 용사의 반지
				switch (itemlvl) {
				case 1:
					_owner.getAC().addAc(-1);
					break;
				case 2:
					_owner.getAC().addAc(-2);
					break;
				case 3:
					_owner.addMaxHp(5);
					_owner.getAC().addAc(-3);
					break;
				case 4:
					_owner.addMaxHp(10);
					_owner.getAC().addAc(-4);
					break;
				case 5:
					_owner.addMaxHp(15);
					_owner.getAC().addAc(-4);
					_owner.addHitup(1);
					_owner.addBowHitup(1);
					_owner.addDmgup(1);
					break;
				case 6:
					_owner.addMaxHp(20);
					_owner.getAC().addAc(-4);
					_owner.addHitup(2);
					_owner.addDmgup(2);
					_owner.addBowHitup(2);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
					break;
				case 7:
					_owner.addMaxHp(25);
					_owner.getAC().addAc(-4);
					_owner.addHitup(3);
					_owner.addBowHitup(3);
					_owner.addDmgup(3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					break;
				case 8:
					_owner.addMaxHp(30);
					_owner.getAC().addAc(-6);
					_owner.addHitup(5);
					_owner.addDmgup(5);
					_owner.addBowHitup(5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 1);
					_owner.getResistance().addPVPweaponTotalDamage(3);
					break;
				case 9:
					_owner.addMaxHp(40);
					_owner.getAC().addAc(-8);
					_owner.addHitup(7);
					_owner.addDmgup(7);
					_owner.addBowHitup(7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
					_owner.getResistance().addPVPweaponTotalDamage(7);
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 체력 반지 **/
			} else if (itemgrade == 3 && itemId == 222332) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(15);
					break;
				case 2:
					_owner.addMaxHp(20);
					_owner.getAC().addAc(-1);
					break;
				case 3:
					_owner.addMaxHp(30);
					_owner.getAC().addAc(-2);
					break;
				case 4:
					_owner.addMaxHp(35);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(1);
					break;
				case 5:
					_owner.addMaxHp(40);
					_owner.getAC().addAc(-4);
					_owner.addDmgup(2);
					break;
				case 6:
					_owner.addMaxHp(45);
					_owner.getAC().addAc(-4);
					_owner.addDmgup(3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 4);
					_owner.addDamageReductionByArmor(1);
					break;
				case 7:
					_owner.addMaxHp(55);
					_owner.getAC().addAc(-5);
					_owner.addDmgup(4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					_owner.addDamageReductionByArmor(2);
					break;
				case 8:
					_owner.addMaxHp(65);
					_owner.getAC().addAc(-7);
					_owner.addDmgup(7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
					_owner.getResistance().addPVPweaponTotalDamage(5);
					_owner.addDamageReductionByArmor(5);
					break;
				case 9:
					_owner.addMaxHp(75);
					_owner.getAC().addAc(-10);
					_owner.addDmgup(9);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 4);
					_owner.getResistance().addPVPweaponTotalDamage(9);
					_owner.addDamageReductionByArmor(7);
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 회복 반지/축복받은 스냅퍼의 집중 반지 **/
			} else if (itemgrade == 3 && itemId == 222330 || itemId == 222331) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(15);
					break;
				case 2:
					_owner.addMaxHp(20);
					_owner.getAC().addAc(-1);
					break;
				case 3:
					_owner.addMaxHp(30);
					_owner.getAC().addAc(-2);
					break;
				case 4:
					_owner.addMaxHp(35);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(1);
					break;
				case 5:
					_owner.addMaxHp(40);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(2);
					break;
				case 6:
					_owner.addMaxHp(45);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
					break;
				case 7:
					_owner.addMaxHp(50);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					break;
				case 8:
					_owner.addMaxHp(50);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addPVPweaponTotalDamage(2);
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 마나 반지 **/
			} else if (itemgrade == 3 && itemId == 222333) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(15);
					break;
				case 2:
					_owner.addMaxHp(20);
					_owner.getAC().addAc(-1);
					break;
				case 3:
					_owner.addMaxHp(30);
					_owner.getAC().addAc(-2);
					break;
				case 4:
					_owner.addMaxHp(35);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(1);
					break;
				case 5:
					_owner.addMaxHp(40);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(2);
					break;
				case 6:
					_owner.addMaxHp(45);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
					break;
				case 7:
					_owner.addMaxHp(50);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					break;
				case 8:
					_owner.addMaxHp(50);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addPVPweaponTotalDamage(2);
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 마법 저항 반지 **/
			} else if (itemgrade == 3 && itemId == 222334) {
				switch (itemlvl) {
				case 0:
					_owner.getResistance().addMr(7);
					break;
				case 1:
					_owner.addMaxHp(15);
					_owner.getResistance().addMr(7);
					break;
				case 2:
					_owner.addMaxHp(20);
					_owner.getAC().addAc(-1);
					_owner.getResistance().addMr(7);
					break;
				case 3:
					_owner.addMaxHp(30);
					_owner.getAC().addAc(-2);
					_owner.getResistance().addMr(7);
					break;
				case 4:
					_owner.addMaxHp(35);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(1);
					_owner.getResistance().addMr(7);
					break;
				case 5:
					_owner.addMaxHp(40);
					_owner.getAC().addAc(-4);
					_owner.addDmgup(2);
					_owner.getResistance().addMr(7);
					break;
				case 6:
					_owner.addMaxHp(45);
					_owner.getAC().addAc(-4);
					_owner.addDmgup(3);
					_owner.getResistance().addMr(8);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
					break;
				case 7:
					_owner.addMaxHp(50);
					_owner.getAC().addAc(-5);
					_owner.addDmgup(4);
					_owner.getResistance().addMr(9);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					break;
				case 8:
					_owner.addMaxHp(50);
					_owner.getAC().addAc(-7);
					_owner.addDmgup(7);
					_owner.getResistance().addMr(12);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
					_owner.getResistance().addPVPweaponTotalDamage(5);
					break;
				case 9:
					_owner.addMaxHp(60);
					_owner.getAC().addAc(-10);
					_owner.addDmgup(9);
					_owner.getResistance().addMr(15);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 4);
					_owner.getResistance().addPVPweaponTotalDamage(9);
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 지혜 반지 **/
			} else if (itemgrade == 3 && itemId == 222335) {
				switch (itemlvl) {
				case 0:
					_owner.addMaxMp(15);
					break;
				case 1:
					_owner.addMaxHp(5);
					_owner.addMaxMp(15);
					break;
				case 2:
					_owner.addMaxHp(10);
					_owner.addMaxMp(15);
					_owner.getAC().addAc(-1);
					break;
				case 3:
					_owner.addMaxHp(20);
					_owner.addMaxMp(15);
					_owner.getAC().addAc(-2);
					break;
				case 4:
					_owner.addMaxHp(25);
					_owner.addMaxMp(15);
					_owner.getAC().addAc(-3);
					_owner.getAbility().addSp(1);
					break;
				case 5:
					_owner.addMaxHp(30);
					_owner.addMaxMp(15);
					_owner.getAC().addAc(-3);
					_owner.getAbility().addSp(2);
					break;
				case 6:
					_owner.addMaxHp(35);
					_owner.addMaxMp(15);
					_owner.getAC().addAc(-3);
					_owner.getAbility().addSp(3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
					_owner.addBaseMagicHitUp(1);// 마법 적중
					break;
				case 7:
					_owner.addMaxHp(40);
					_owner.addMaxMp(30);
					_owner.getAC().addAc(-5);
					_owner.getAbility().addSp(4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					_owner.addBaseMagicHitUp(2);// 마법 적중
					break;
				case 8:
					_owner.addMaxHp(50);
					_owner.addMaxMp(35);
					_owner.getAC().addAc(-7);
					_owner.getAbility().addSp(7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
					_owner.getResistance().addPVPweaponTotalDamage(5);
					_owner.addBaseMagicHitUp(5);// 마법 적중
					break;
				case 9:
					_owner.addMaxHp(60);
					_owner.addMaxMp(80);
					_owner.getAC().addAc(-10);
					_owner.getAbility().addSp(9);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 4);
					_owner.getResistance().addPVPweaponTotalDamage(9);
					_owner.addBaseMagicHitUp(-7);// 마법 적중
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 용사 반지 **/
			} else if (itemgrade == 3 && itemId == 222336) {
				switch (itemlvl) {
				case 1:
					_owner.getAC().addAc(-1);
					break;
				case 2:
					_owner.getAC().addAc(-2);
					break;
				case 3:
					_owner.addMaxHp(10);
					_owner.getAC().addAc(-3);
					break;
				case 4:
					_owner.addMaxHp(15);
					_owner.getAC().addAc(-4);
					_owner.addHitup(1);
					_owner.addBowHitup(1);
					_owner.addDmgup(1);
					break;
				case 5:
					_owner.addMaxHp(20);
					_owner.getAC().addAc(-4);
					_owner.addHitup(2);
					_owner.addBowHitup(2);
					_owner.addDmgup(2);
					break;
				case 6:
					_owner.addMaxHp(25);
					_owner.getAC().addAc(-4);
					_owner.addHitup(3);
					_owner.addBowHitup(3);
					_owner.addDmgup(3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 5);
					break;
				case 7:
					_owner.addMaxHp(30);
					_owner.getAC().addAc(-5);
					_owner.addHitup(4);
					_owner.addDmgup(4);
					_owner.addBowHitup(4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 7);
					_owner.getResistance().addPVPweaponTotalDamage(1);
					break;
				case 8:
					_owner.addMaxHp(30);
					_owner.getAC().addAc(-7);
					_owner.addHitup(7);
					_owner.addDmgup(7);
					_owner.addBowHitup(7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 2);
					_owner.getResistance().addPVPweaponTotalDamage(5);
					break;
				case 9:
					_owner.addMaxHp(40);
					_owner.getAC().addAc(-10);
					_owner.addHitup(9);
					_owner.addDmgup(9);
					_owner.addBowHitup(9);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, 9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, 4);
					_owner.getResistance().addPVPweaponTotalDamage(9);
					break;
				default:
					break;
				}
			} else if(itemId == 22229){
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(20);
					break;
				case 2:
					_owner.addMaxHp(30);
					break;
				case 3:
					_owner.addMaxHp(40);
					_owner.addDamageReductionByArmor(1);
					break;
				case 4:
					_owner.addMaxHp(50);
					_owner.addDamageReductionByArmor(1);
					break;
				case 5:
					_owner.addMaxHp(60);
					_owner.addDamageReductionByArmor(2);
					break;
				case 6:
					_owner.addMaxHp(70);
					_owner.addDamageReductionByArmor(3);
					_owner.getAC().addAc(-7);
					break;
				case 7:
					_owner.addBowHitup(1);
					_owner.addHitup(1);
					_owner.addMaxHp(80);
					_owner.addDamageReductionByArmor(4);
					_owner.getAC().addAc(-8);
					break;
				case 8:
					_owner.addBowHitup(3);
					_owner.addHitup(3);
					_owner.addMaxHp(90);
					_owner.addDamageReductionByArmor(7);
					_owner.getAC().addAc(-9);
					break;
				case 9:
					_owner.addBowHitup(5);
					_owner.addHitup(5);
					_owner.addMaxHp(100);
					_owner.addDamageReductionByArmor(7);
					_owner.getAC().addAc(-10);
					break;
				default:
					break;
				}
			} else if(itemId == 222337){
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(10);
					break;
				case 2:
					_owner.addMaxHp(20);
					break;
				case 3:
					_owner.addMaxHp(40);
					_owner.addDamageReductionByArmor(1);
					break;
				case 4:
					_owner.addMaxHp(50);
					_owner.addDamageReductionByArmor(2);
					break;
				case 5:
					_owner.addMaxHp(60);
					_owner.addDamageReductionByArmor(3);
					_owner.getAC().addAc(-7);
					break;
				case 6:
					_owner.addBowHitup(1);
					_owner.addHitup(1);
					_owner.addMaxHp(70);
					_owner.addDamageReductionByArmor(4);
					_owner.getAC().addAc(-8);
					break;
				case 7:
					_owner.addBowHitup(3);
					_owner.addHitup(3);
					_owner.addMaxHp(80);
					_owner.addDamageReductionByArmor(5);
					_owner.getAC().addAc(-9);
					break;
				case 8:
					_owner.addBowHitup(5);
					_owner.addHitup(5);
					_owner.addMaxHp(140);
					_owner.addDamageReductionByArmor(7);
					_owner.getAC().addAc(-10);
					break;
				case 9:
					_owner.addBowHitup(7);
					_owner.addHitup(7);
					_owner.addMaxHp(150);
					_owner.addDamageReductionByArmor(9);
					_owner.getAC().addAc(-11);
					break;
				default:
					break;
				}
			} else if(itemId == 22231){
				switch (itemlvl) {
				case 0:
					_owner.getResistance().addMr(2);
					break;
				case 1:
					_owner.addMaxMp(10);
					_owner.getResistance().addMr(5);
					break;
				case 2:
					_owner.addMaxMp(15);
					_owner.getResistance().addMr(6);
					break;
				case 3:
					_owner.addMaxMp(30);
					_owner.getResistance().addMr(7);
					_owner.getAbility().addSp(1);
					break;
				case 4:
					_owner.addMaxMp(35);
					_owner.getResistance().addMr(8);
					_owner.getAbility().addSp(1);
					break;
				case 5:
					_owner.addMaxMp(50);
					_owner.getResistance().addMr(9);
					_owner.getAbility().addSp(2);
					break;
				case 6:
					_owner.addMaxMp(55);
					_owner.getResistance().addMr(10);
					_owner.getAbility().addSp(2);
					_owner.getAC().addAc(-1);
					break;
				case 7:
					_owner.addBaseMagicHitUp(1);// 마법 적중
					_owner.addMaxMp(70);
					_owner.getResistance().addMr(12);
					_owner.getAbility().addSp(3);
					_owner.getAC().addAc(-2);
					break;
				case 8:
					_owner.addBaseMagicHitUp(5);// 마법 적중
					_owner.addMaxMp(95);
					_owner.getResistance().addMr(17);
					_owner.getAbility().addSp(5);
					_owner.addMaxHp(50);
					_owner.getAC().addAc(-3);
					break;
				case 9:
					_owner.addBaseMagicHitUp(9);// 마법 적중
					_owner.addMaxMp(125);
					_owner.addMaxHp(100);
					_owner.getResistance().addMr(20);
					_owner.getAbility().addSp(7);
					_owner.getAC().addAc(-3);
					break;
				default:
					break;
				}
			} else if(itemId == 222339){
				switch (itemlvl) {
				case 0:
					_owner.addMaxMp(10);
					
					break;
				case 1:
					_owner.addMaxMp(10);
					
					break;
				case 2:
					_owner.addMaxMp(15);
					
					break;
				case 3:
					_owner.addMaxMp(35);
					_owner.getResistance().addMr(8);
					_owner.getAbility().addSp(1);
					break;
				case 4:
					_owner.addMaxMp(50);
					_owner.getResistance().addMr(9);
					_owner.getAbility().addSp(2);
					break;
				case 5:
					_owner.addMaxMp(55);
					_owner.getResistance().addMr(10);
					_owner.getAbility().addSp(2);
					_owner.getAC().addAc(-1);
					break;
				case 6:
					_owner.addBaseMagicHitUp(1);// 마법 적중
					_owner.addMaxMp(70);
					_owner.getResistance().addMr(12);
					_owner.getAbility().addSp(3);
					_owner.getAC().addAc(-2);
					break;
				case 7:
					_owner.addBaseMagicHitUp(3);// 마법 적중
					_owner.addMaxMp(95);
					_owner.getResistance().addMr(15);
					_owner.getAbility().addSp(3);
					_owner.getAC().addAc(-3);
					break;
				case 8:
					_owner.addBaseMagicHitUp(7);// 마법 적중
					_owner.addMaxMp(125);
					_owner.addMaxHp(100);
					_owner.getResistance().addMr(20);
					_owner.getAbility().addSp(5);
					_owner.getAC().addAc(-4);
					break;
				case 9:
					_owner.addBaseMagicHitUp(12);// 마법 적중
					_owner.addMaxMp(155);
					_owner.addMaxHp(150);
					_owner.getResistance().addMr(23);
					_owner.getAbility().addSp(9);
					_owner.getAC().addAc(-4);
					break;
				default:
					break;
				}
				
				/** 검귀 ac처리부분 재수정 **/
			} else if (itemId == 222340 || itemId == 222341) {
				int ac = itemlvl;

				if (item.getBless() == 0 && itemlvl >= 3) {
					ac += 1;
				}
				_owner.getAC().addAc(-ac);
				if (itemlvl >= 4 && itemlvl <= 7) {
					if (item.getBless() != 0 ){ // 걍
						_owner.addDmgup(itemlvl - 3);
						_owner.addBowDmgup(itemlvl - 3);
					} else {
						_owner.addDmgup(itemlvl - 2);
						_owner.addBowDmgup(itemlvl - 2);
					}
				} else if (itemlvl == 8) {
					if (item.getBless() != 0 ){ // 걍
						_owner.addDmgup(7);
						_owner.addBowDmgup(7);
					} else {
						_owner.addDmgup(9);
						_owner.addBowDmgup(9);
					}
				} else if (itemlvl >= 9) {
					if (item.getBless() != 0 ){ // 걍
						_owner.addDmgup(9);
						_owner.addBowDmgup(9);
					} else {
						_owner.addDmgup(12);
						_owner.addBowDmgup(12);
					}
				}
			}
		}
		armor.startEquipmentTimer(_owner);
	}

	public ArrayList<L1ItemInstance> getArmors() {
		return _armors;
	}

	private void removeWeapon(L1ItemInstance weapon) {
	    if (weapon == null || weapon.getItem() == null) return;
	    
		int type = weapon.getItem().getType();
		int itemId = weapon.getItem().getItemId();
		int enchant = weapon.getEnchantLevel();
		if (itemId == 1134 || itemId == 101134) { // 명상의 지팡이
			
			
			if (enchant > 0) {
				_owner.addMpr(-enchant);
			}
		}
		if(itemId == 14061 || itemId == 14012 || itemId == 2202013 || itemId == 2202014 || itemId == 14086) {
			_owner.addDamageReductionByArmor(-1);
		}
		if(itemId == 14134 || itemId == 2202012 ) {
			_owner.addDamageReductionByArmor(-2);
		}
		if (itemId == 61 || itemId == 14061) {
			if (enchant > 0) {
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -(8 + enchant));
				_owner.add_melee_critical_rate(-(1 + enchant));
			} else {
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -8);
				_owner.add_melee_critical_rate(-1);
			}
		}

		if (itemId == 12 || itemId == 14012) {
			if (enchant > 0) {
				_owner.getResistance().addSpecialPierce(eKind.ALL, -enchant);
			}
		}

		if (itemId == 134|| itemId == 14134) {
			if (enchant > 0) {
				_owner.getAbility().addSp(-(7 + enchant));
				_owner.addBaseMagicHitUp(-(2 + enchant));
			} else {
				_owner.getAbility().addSp(-7);
				_owner.addBaseMagicHitUp(-2);
			}
			SC_SPMR.send(_owner);
		}

		if (itemId == 86 || itemId == 14086) {
			if (enchant > 0) {
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -(5 + enchant));
			} else {
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
			}
		}
		if (itemId == 202011 || itemId == 2202011) {
			if (enchant > 0) {
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -(5 + enchant));
				_owner.addDamageReductionIgnore(-(9  + enchant));
				_owner.add_missile_critical_rate(-(1 + enchant));
				_owner.addDamageReductionByArmor(-2);
			} else {
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				_owner.add_missile_critical_rate(-1);
				_owner.addDamageReductionIgnore(-9);
				_owner.addDamageReductionByArmor(-2);
				
				
			}
		}

		if (itemId == 202013 || itemId == 2202013) {
			if (enchant > 0) {
				_owner.add_melee_critical_rate(-(1 + enchant));
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -(7  + enchant));
			} else {
				_owner.add_melee_critical_rate(-1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -7);
			}
		}

		if (itemId == 202012 || itemId == 2202012) {
			if (enchant > 0) {
				_owner.getAbility().addSp(-(2 + enchant));
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -(7  + enchant));
				_owner.add_magic_critical_rate(-(1 + enchant));
			} else {
				_owner.getAbility().addSp(-2);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -7);
				_owner.add_magic_critical_rate(-1);
			}
			SC_SPMR.send(_owner);
		}

		if (itemId == 202014|| itemId == 2202014) {
			if (enchant > 0) {
				_owner.add_melee_critical_rate(-(1 + enchant));
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -(3 + enchant));
				
			} else {
				_owner.add_melee_critical_rate(-1);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -3);
			
			}
		}
		/** 칠흑의 수정구 **/
		if (itemId == 118) {
			switch (enchant) {
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addCha(-1);
				break;
			default:
				break;
			}
		}
		/** 축복받은 제로스의 지팡이 **/
		if (itemId == 620) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBaseMagicCritical(-5);// 마법 치명타
				break;
			}
		}
		/** 살기의 키링크 **/
		if (itemId == 7000223 || itemId == 505015) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
				_owner.addBaseMagicCritical(-1);// 마법 치명타
				break;
			case 7:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -1);
				_owner.addBaseMagicCritical(-2);// 마법 치명타
				break;
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -2);
				_owner.addBaseMagicCritical(-3);// 마법 치명타
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -3);
				_owner.addBaseMagicCritical(-4);// 마법 치명타
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -4);
				_owner.addBaseMagicCritical(-5);// 마법 치명타
				break;
			}
		}
		if (itemId == 123001) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -2);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -2);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -3);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -3);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -3);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -4);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -4);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -4);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -5);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.FEAR, -5);
					_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
					_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				}
				break;
			}
		}
		if (itemId == 123002) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				break;
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -6);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -7);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -8);
				break;
			
			default:
				if (enchant > 10) {
					_owner.getResistance().addSpecialPierce(eKind.ABILITY, -8);
				}
				break;
			}
		}
		if (itemId == 123003) {
			switch (enchant) {
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -1);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -2);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -3);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -3);
				}
				break;
			}
		}
		if (itemId == 123004) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -2);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -3);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -4);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				}
				break;
			}
		}
	
		if (itemId == 123005) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.addBaseMagicHitUp(-1);
				break;
			case 9:
				_owner.addBaseMagicHitUp(-2);
				break;
			case 10:
				_owner.addBaseMagicHitUp(-3);
				break;
			case 11:
				_owner.addBaseMagicHitUp(-4);
				break;
			default:
				if (enchant > 11) {
					_owner.addBaseMagicHitUp(-4);
				}
				break;
			}
		}
		if (itemId == 123006) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -2);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -3);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -4);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -5);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.FEAR, -5);
				}
				break;
			}
		}
		if (itemId == 123007) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -6);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -7);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -8);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -8);
				}
				break;
			}
		}
		if (itemId == 123008) {
			switch (enchant) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				_owner.addSp(-1);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				_owner.addSp(-2);
				break;
			case 10:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				_owner.addSp(-3);
				break;
			case 11:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				_owner.addSp(-4);
				break;
			default:
				if (enchant > 11) {
					_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
					_owner.addSp(-4);
				}
				break;
			}
		}
		/** 바포메트의 지팡이 SP **/
		if (itemId == 124) {
			switch (enchant) {
			case 7:
				_owner.getAbility().addSp(-1);
				SC_SPMR.send(_owner);
				break;
			case 8:
				_owner.getAbility().addSp(-2);
				SC_SPMR.send(_owner);
				break;
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addSp(-3);
				SC_SPMR.send(_owner);
				break;
			}
		}
		if (itemId == 541) {
			switch (enchant) {
			case 0:
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -10);
				break;
			}
		}
		if (itemId == 542) {
			switch (enchant) {
			case 0:
				_owner.addDamageReductionByArmor(-2);
				break;
			}
		}
		if (itemId == 543) {
			switch (enchant) {
			case 0:
				_owner.addHitup(-2);
				break;
			}
		}
		if (itemId == 544) {
			switch (enchant) {
			case 0:
				_owner.addHitup(-10);
				break;
			}
		}
		if (itemId == 545) {
			switch (enchant) {
			case 0:
				_owner.addHitup(-17);
				break;
			}
		}
		if (itemId == 547) {
			switch (enchant) {
			case 0:
				_owner.addHitup(-11);
				break;
			}
		}

		/** 드래곤슬레이어 **/
		if (itemId == 66) {
			_owner.addDmgup(-(enchant * 2));
		}
		/** 악마왕 도끼 **/
		if (itemId == 147) {
			_owner.addDmgRate(-enchant);
		}
		/** 나이트발드의 양손검 기술적중 **/
		if (itemId == 59 || itemId == 617  || itemId == 11121) {
			_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
		}
		/** 악몽의 장궁 정령 적중**/
		if (itemId == 622 || itemId == 1136) {
			_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -3);
		}
		/** 태풍의도끼 공포적중+1 **/
		if (itemId == 203006 || itemId == 616) {
			switch (enchant) {
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -1);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -2);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -3);
				break;
			}
		}
		/** 섬멸자의 체인소드 적중 **/
		if (itemId == 203017 || itemId == 618) {
			switch (enchant) {
			case 7:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -1);
				break;
			case 8:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -2);
				break;
			case 9:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -3);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -4);
				break;
			}
		}
		/** 썸타는 도끼 공포적중+5 **/
		if (itemId == 7000221) 
			_owner.getResistance().addSpecialPierce(eKind.FEAR, -5);
		
		/** 썸타는 무기류 PVP 효과 부여 **/
		if (itemId >= 7000214 || itemId <= 7000221) {
			if(enchant <= 7)
				_owner.getResistance().addPVPweaponTotalDamage(-3);
			else if(enchant == 8)
				_owner.getResistance().addPVPweaponTotalDamage(-5);
			else if(enchant == 9)
				_owner.getResistance().addPVPweaponTotalDamage(-7);
			else if(enchant >= 10)
				_owner.getResistance().addPVPweaponTotalDamage(-10);
		}
   
		if (_owner.hasSkillEffect(L1SkillId.이뮨투함블레이드)) {
			if (_owner.getType() == 0 || _owner.getType() == 1) {
				if (type == 1 || type == 3)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 2 ) {
				if (type == 4 || type == 13)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 3 ) {
				if (type == 7 || type == 16 || type == 1)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 4 ) {
				if (type == 12 || type == 11 )
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 5 ) {
				if (type == 1 || type == 3 || type == 18)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 6 ) {
				if (type == 17)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 7 ) {
				if (type == 6 || type == 15)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
		}
		
		if (_owner.hasSkillEffect(L1SkillId.INFERNO)) {
			_owner.removeSkillEffect(L1SkillId.INFERNO);
		}
		
		if (_owner.hasSkillEffect(L1SkillId.COUNTER_BARRIER)) {
			_owner.removeSkillEffect(L1SkillId.COUNTER_BARRIER);
		}
		
		if (_owner.hasSkillEffect(L1SkillId.할파스)) {
			_owner.removeSkillEffect(L1SkillId.할파스);
			SC_InventoryIcon.icoEnd(_owner, L1SkillId.할파스);
		}
		
		
		if (_weapons.size() == 1)
			_owner.setCurrentWeapon(getWeapon().getItem().getType1());
		else
			_owner.setCurrentWeapon(0);

		if (itemId == 203003) { // 데스나이트의 불검:진
			L1PolyMorph.undoPoly(_owner);
		}
		
		if ((type == 7 || type == 16 || type == 17) && (weapon.get_bless_level() != 0)) {
			_owner.getAbility().addSp(-weapon.get_bless_level());
			SC_SPMR.send(_owner);
		}
		
		weapon.stopEquipmentTimer(_owner);
	    removeWeaponFromSlots(weapon);	
	}

	private void removeArmor(L1ItemInstance armor) {
		L1Item item = armor.getItem();
		int itemId = armor.getItem().getItemId();
		int itemlvl = armor.getEnchantLevel();
		int itemtype = armor.getItem().getType();
		int itemgrade = armor.getItem().getGrade();
		int constat = armor.getItem().get_addcon();
		int wisstat = armor.getItem().get_addwis();
		
		
		if (item.getMaxUseTime() > 0) {
			_owner.set시간제(false);
		}
		
		
		if (itemId == 123111 || itemId == 123112 || itemId == 123113) {
			
			
				 SC_InventoryIcon.icoEnd(_owner, L1SkillId.할파스의신의1);
			
				 SC_InventoryIcon.icoEnd(_owner, L1SkillId.할파스의신의);
			 
		}
		
		if (constat > 0) {
			_owner.addMaxHp(-((_owner.getAbility().getCon() / 2) * constat));
		}
		
		if (wisstat > 0) {
			_owner.addMaxMp(-((_owner.getAbility().getWis() / 2) * wisstat));
		}
		
		if(itemId == 900111){
			_owner.removeSkillEffect(L1SkillId.TELEPORT_RULER); 
		}
		
	
		if (armor.get_bless_level2() != 0) {//SGMALL
			if(armor.get_bless_level2() >= 1) {
				_owner.addMaxHp(-armor.get_bless_level2());
			}
		}
		if (armor.get_bless_level() != 0) { //SGMALL

			if(armor.get_bless_level() == 1) {
				_owner.addMaxHp(-30);
			} else if(armor.get_bless_level() == 2) {
				_owner.addMaxHp(-20);
			} else if(armor.get_bless_level() == 3) {
				_owner.addMaxHp(-10);
			} else if(armor.get_bless_level() == 4) {
				_owner.addMaxHp(-30);
			} else if(armor.get_bless_level() == 5) {
				_owner.addMaxHp(-40);
			} else if(armor.get_bless_level() == 6) {
				_owner.addMaxHp(-50);
			} else if(armor.get_bless_level() == 7) {
				_owner.addDamageReductionByArmor(-1);
			} else if(armor.get_bless_level() == 8) {
				_owner.addDamageReductionByArmor(-2);
			} else if(armor.get_bless_level() == 10) {
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -1);
			} else if(armor.get_bless_level() == 11) {
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -1);
			} else if(armor.get_bless_level() == 12) {
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -1);
			} else if(armor.get_bless_level() == 13) {
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -1);
			} else if(armor.get_bless_level() == 14) {
				_owner.addBaseMagicHitUp(-1);
			} else if(armor.get_bless_level() == 15) {
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -1);
			} else if(armor.get_bless_level() == 16) {
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -1);
			} else if(armor.get_bless_level() == 17) {
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -1);
			} else if(armor.get_bless_level() == 18) {
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -1);
			} else if(armor.get_bless_level() == 19) {
				_owner.addDmgRate(-1);
			} else if(armor.get_bless_level() == 20) {
				_owner.addBowDmgRate(-1);
			} else if(armor.get_bless_level() == 21) {
				_owner.addHitup(-1);
			} else if(armor.get_bless_level() == 22) {
				_owner.addBowHitup(-1);
			} else if(armor.get_bless_level() == 23) {
				_owner.addDamageReductionByArmor(-1);
			} else if(armor.get_bless_level() == 24) {
				_owner.getAC().addAc(1);
			} else if(armor.get_bless_level() == 25) {
				_owner.getResistance().addMr(-3);
			} else if(armor.get_bless_level() == 26) {
				_owner.getAbility().addSp(-1);
			}							

	}
		
		
		if(itemId == 22228){
			if(itemlvl >= 9){
				_owner.addMagicDodgeProbability(-7);
		}else if(itemlvl >= 8){
				_owner.addMagicDodgeProbability(-4);
			}else if(itemlvl >= 7){
				_owner.addMagicDodgeProbability(-1);					
			}
		}else if(itemId == 222334){
			if(itemlvl >= 9){
				_owner.addMagicDodgeProbability(-9);
		}else if(itemlvl >= 8){
				_owner.addMagicDodgeProbability(-7);
			}else if(itemlvl >= 7){
				_owner.addMagicDodgeProbability(-3);
			}else if(itemlvl >= 6){
				_owner.addMagicDodgeProbability(-1);
			}
		}
		

		if ( itemId == 565611) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addMr(-12);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -1);
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -1);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -12);
				break;
		default:
			if (itemlvl > 5) {
				_owner.getResistance().addMr(-(itemlvl - 5 + 12));
				_owner.addDamageReductionByArmor(-(itemlvl - 5 + 1));
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, (-(itemlvl - 5 + 1)));
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, (-(itemlvl - 5 + 1)));
				_owner.getResistance().addSpecialResistance(eKind.FEAR, (-(itemlvl - 5 + 1)));
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, (-(itemlvl - 5 + 12)));
				_owner.getResistance().addPVPweaponTotalDamage(-(itemlvl - 5));
			}
				break;
			}
		}
		
		if (itemId == 900116) {
			if (itemlvl <= 5) {
				_owner.getResistance().addPVPweaponTotalDamage(-3);
				_owner.getResistance().addcalcPcDefense(-1);
				_owner.getResistance().addMr(-10);
				_owner.addMaxHp(-100);
			} else if (itemlvl == 6) {
				_owner.getResistance().addPVPweaponTotalDamage(-4);
				_owner.getResistance().addcalcPcDefense(-2);
				_owner.getResistance().addMr(-12);
				_owner.addMaxHp(-120);
			} else if (itemlvl == 7) {
				_owner.getResistance().addPVPweaponTotalDamage(-5);
				_owner.getResistance().addcalcPcDefense(-3);
				_owner.getResistance().addMr(-13);
				_owner.addMaxHp(-130);
			} else if (itemlvl == 8) {
				_owner.getResistance().addPVPweaponTotalDamage(-6);
				_owner.getResistance().addcalcPcDefense(-4);
				_owner.getResistance().addMr(-15);
				_owner.addMaxHp(-150);
			}

			
		}
		//착용 해제
		if (itemId == 30184) {
			_owner.addHitup(-2);
		}
		if (itemId == 20272 || itemId == 222342) {
			_owner.addBowHitup(-3);
		}
		if (itemId == 800110) {
			if(_owner.isCrown() || _owner.isKnight() || _owner.isDarkelf() || _owner.isDragonknight() || _owner.isWarrior() ) {
				_owner.getAC().addAc(8);
				_owner.addHitup(-2);
				_owner.addDmgRate(-2);
				_owner.addDamageReductionByArmor(-2);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
			} else if(_owner.isBlackwizard() || _owner.isWizard()) {
				_owner.getAC().addAc(8);
				_owner.getAbility().addSp(-2);
				_owner.addBaseMagicHitUp(-2);
				_owner.addDamageReductionByArmor(-2);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
			} else if(_owner.isElf() && _owner.getAbility().getBaseDex() >= 16) {
				_owner.getAC().addAc(8);
				_owner.addBowHitup(-2);
				_owner.addBowDmgRate(-2);
				_owner.addDamageReductionByArmor(-2);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
			} else {
				_owner.getAC().addAc(8);
				_owner.addHitup(-2);
				_owner.addDmgRate(-2);
				_owner.addDamageReductionByArmor(-2);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
			}

			
		}
if(item.getItemId() == 700117){
			
			
			_owner.getAbility().addSp(-(2+ itemlvl));
			_owner.getResistance().addPVPweaponTotalDamage(-(2 + itemlvl));
			_owner.getResistance().addcalcPcDefense(-(2 + itemlvl));
			_owner.getResistance().addMr(-(3 + itemlvl));
			_owner.addBaseMagicHitUp(-(1 + itemlvl));
			SC_OwnCharAttrDef.send(_owner);
			SC_SPMR.send(_owner);
			if (itemlvl >= 5) {
				_owner.getResistance().addSpecialResistance(eKind.ALL, (-(itemlvl - 4)));
				_owner.getResistance().addSpecialPierce(eKind.ALL, (-(itemlvl - 4)));
			}
			
		}
	if (itemId == 808011) {
			
			switch (itemlvl) {
			case 0:
				_owner.getResistance().addMr(-12);
				break;
			case 1:
				_owner.getAC().addAc(6);
				_owner.getResistance().addMr(-26);
				break;
				
			case 2:
				_owner.getAC().addAc(6);
				_owner.addMaxMp(-10);
				_owner.addMaxHp(-20);
				_owner.getResistance().addMr(-28);
				break;
			case 3:
				_owner.getAC().addAc(6);
				_owner.addMaxMp(-20);
				_owner.addMaxHp(-40);
				_owner.getResistance().addMr(-30);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -3);
				break;
			case 4:
				_owner.getAC().addAc(6);
				_owner.addMaxMp(-30);
				_owner.addMaxHp(-60);
				_owner.getResistance().addMr(-32);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -4);
				break;
			case 5:
				_owner.getAC().addAc(6);
				_owner.addMaxMp(-40);
				_owner.addMaxHp(-80);
				_owner.getResistance().addMr(-34);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				_owner.addEffectedER(-4);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				break;
			
		default:
			if (itemlvl > 5 ){
				_owner.getAC().addAc(-6);
				_owner.addMaxMp(-40);
				_owner.addMaxHp(-80);
				_owner.getResistance().addMr(-34);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				_owner.addEffectedER(-4);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
			}
				break;
			}
		}
		
	
		if (itemId == 20050) {

			switch (itemlvl) {
			case 5:
				_owner.addEffectedER(-1);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				
				break;
			case 6:
				_owner.addEffectedER(-2);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				break;
			case 7:
				_owner.addEffectedER(-3);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				break;
			case 8:
				_owner.addEffectedER(-4);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				break;
			case 9:
				_owner.addEffectedER(-5);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
				break;
			
		default:
			if (itemlvl > 9 ){
				_owner.addEffectedER(-5);
				SC_PacketBox.send(_owner, SC_PacketBox.ER_UpDate, _owner.getTotalER());
			}
				break;
			}
		}
		//21주년
		if (itemId == 474700) {

			switch (itemlvl) {
			case 0:
				_owner.getResistance().addMr(-1);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
				break;
			case 1:
				_owner.getResistance().addMr(-3);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
				break;
			case 2:
				_owner.getResistance().addMr(-5);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
				break;
			case 3:
				_owner.getResistance().addMr(-7);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
				break;
			case 4:
				_owner.getResistance().addMr(-9);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
				break;
			case 5:
				_owner.getResistance().addMr(-11);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -6);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -6);
				break;
			case 6:
				_owner.getResistance().addMr(-13);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -7);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
				break;
			case 7:
				_owner.getResistance().addMr(-15);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -8);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -8);
				break;
			case 8:
				_owner.getResistance().addMr(-17);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -9);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
				break;
			case 9:
				_owner.getResistance().addMr(-19);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -10);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -10);
				break;
			case 10:
				_owner.getResistance().addMr(-21);
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -10);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -10);
				break;
			
//		default:
//			if (itemlvl > 9 ){
//				_owner.getResistance().addMr(-(((itemlvl - 9) * 2) + 19));
//				_owner.getResistance().addSpecialResistance(eKind.FEAR, -10);
//				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -10);
//			}
//				break;
			}
		}

//		
		if (itemId == 20051) {

			switch (itemlvl) {
			case 0:
				_owner.getResistance().addMr(-4);

				break;
			default:
				if (itemlvl > 0) {
					_owner.getResistance().addMr(-(itemlvl * 2 + 4));
				}

				break;

			}
		}
		if (itemId == 22263) {
			int aa = itemlvl - 4;
				if (itemlvl > 4) {
						_owner.addMaxHp(-(aa * 20));
						_owner.getResistance().addSpecialPierce(eKind.ABILITY, - (itemlvl - 4));
					}
			}
		
		if (itemId == 22360) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getResistance().addMr(-1);

				break;
			default:
				
				if (itemlvl > 4) {
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -(itemlvl - 4));
					_owner.getResistance().addMr(-(((itemlvl - 4 ) * 2) + 1 ));
				}

				break;

			}
		}
			
		
		
		if (itemtype >= 8 && itemtype <= 12 && !isRoomteece(itemId)) {

		    if ((itemtype == 8 || itemtype == 12) && itemlvl >= 5) {

		        int enchantAc = itemlvl - 4;

		        if (itemlvl >= 10) {
		            enchantAc = 6;
		        } else {
		            if (enchantAc >= 5) {
		                enchantAc = 5;
		            }
		        }

		        _owner.getAC().addAc(
		                -(item.get_ac()
		                - enchantAc
		                + armor.get_durability()));

		        SC_OwnCharAttrDef.send(_owner);

		    } else {

		        _owner.getAC().addAc(
		                -(item.get_ac()
		                + armor.get_durability()));

		        SC_OwnCharAttrDef.send(_owner);
		    }

		} else if (itemtype == 30) { // 휘장

		} else if (itemId == 900118
		        || itemId == 900117
		        || itemId == 900119
		        || itemId == 900120) {

		    int enchant = armor.getEnchantLevel();
		    int enchant_ac = enchant >= 1 ? enchant : 0;

		    _owner.getAC().addAc(
		            -(item.get_ac()
		            - enchant_ac
		            + armor.get_durability()));

		    SC_OwnCharAttrDef.send(_owner);

		} else if (!isRoomteece(itemId)) {

		    _owner.getAC().addAc(
		            -(item.get_ac()
		            - armor.getEnchantLevel()
		            + armor.get_durability()));

		    SC_OwnCharAttrDef.send(_owner);

		} else {

		    _owner.getAC().addAc(-item.get_ac());
		}
		/** 대마법사의 모자 인챈당 mp증가 **/
		if (itemId == 202022) {
			if (itemlvl >= 1) {
				_owner.addMaxMp(-(itemlvl * 10));
			}
			if (itemlvl >= 1) {
				_owner.getResistance().addMr(-(itemlvl * 2));
			}
		}
		/** 리치 로브 인챈당 sp증가 **/
		if (itemId == 20107) {
			if (itemlvl >= 3) {
				_owner.getAbility().addSp(-(itemlvl - 2));
			}
		}
		
		_owner.addDamageReductionByArmor(-item.get_damage_reduction());
		_owner.addWeightReduction(-item.getWeightReduction());
		_owner.addBowHitRate(-item.getBowHitRate());	
		_owner.addBowDmgRate(-item.getBowDmgRate());	
		_owner.addDmgRate(-item.getDmgRate());	
		_owner.addHitRate(-item.getHitRate());
		_owner.getResistance().addEarth(-item.get_defense_earth());
		_owner.getResistance().addWind(-item.get_defense_wind());
		_owner.getResistance().addWater(-item.get_defense_water());
		_owner.getResistance().addFire(-item.get_defense_fire());
		item.equipmentItem(_owner, false);
		_owner.getResistance().addcalcPcDefense(-item.get_regist_calcPcDefense());
		_owner.getResistance().addPVPweaponTotalDamage(-item.get_regist_PVPweaponTotalDamage());
		_owner.addBaseMagicHitUp(-item.getMagicHitup());

		// 셋트아이템 메소드로 변경..
		removeSetItems(itemId);

		if (itemId == 900022) {
			SC_PacketBox.send(_owner, SC_PacketBox.UNLIMITED_ICON1, 484, false);
		}
		
		if (itemId == 20077 || itemId == 20062 || itemId == 120077) {
			_owner.delInvis();
		
		}
		if (itemId == 20288 || itemId == 900111) {
		    SC_Ability.send(_owner, 1, false);
		}
		if (itemId == 20281) {
			SC_Ability.send(_owner, 2, false);
		}
		/*if (itemId == 900075) {
			RAbility.send(_owner, 7, false);
			RAbility.send(_owner, 2, false);
			_owner.setPolyRingMaster(false);
		}*/
		if (itemId == 20036) {
			SC_Ability.send(_owner, 3, false);
		}
		if (itemId == 20284) {
			SC_Ability.send(_owner, 5, false);
		}
		if (itemId == 20207) {
			SC_SkillIconBlessOfEva.send(_owner, _owner.getId(), 0);
		}
//		if (itemId == 22200 || itemId == 22201 || itemId == 22202 || itemId == 22203) {// 파푸리온 갑옷
//			_owner.stopPapuBlessing();
//		}else if(itemId >= 22208 && itemId <= 22211 || itemId == 922208)
//			_owner.stopValaBlessing();

		/*** 50레벨 엘릭서 룬 ***/
		// 민첩의 엘릭서 룬
		if (itemId == 222295) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				break;
			// 기사//전사
			case 7:
			case 1:
				_owner.addMaxHp(-50);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				break;
			}
		}
		// 체력의 엘릭서 룬
		if (itemId == 222296) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				break;
			// 기사//전사
			case 7:
			case 1:
				_owner.addMaxHp(-50);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				break;
			}
		}
		// 지식의 엘릭서 룬
		if (itemId == 222297) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				break;
			// 기사//전사
			case 7:
			case 1:
				_owner.addMaxHp(-50);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.addMaxMp(-30);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				break;
			}
		}
		// 지혜의 엘릭서 룬
		if (itemId == 222298) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				break;
			// 기사//전사
			case 7:
			case 1:
				_owner.addMaxHp(-50);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				break;
			}
		}
		// 힘의 엘릭서 룬
		if (itemId == 222299) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				break;
			// 기사//전사
			case 7:
			case 1:
				_owner.addMaxHp(-50);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				break;
			}
		}
		//TODO 70레벨 엘릭서 룬
		// 민첩의 엘릭서 룬
		if (itemId == 222312) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addDmgRate(-2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				break;
			}
		}
		// 체력의 엘릭서 룬
		if (itemId == 222313) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addDmgRate(-2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				break;
			}
		}
		// 지식의 엘릭서 룬
		if (itemId == 222314) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addDmgRate(-2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				break;
			}
		}
		// 지혜의 엘릭서 룬
		if (itemId == 222315) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addDmgRate(-2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				break;
			}
		}
		// 힘의 엘릭서 룬
		if (itemId == 222316) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addDmgRate(-2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				break;
			}
		}
		// TODO 80레벨 엘릭서 룬
		// 힘의 엘릭서 룬
		if (itemId == 900135) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addDmgRate(-2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				break;
			}
		}
		// 민첩의 엘릭서 룬
		if (itemId == 900136) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addDmgRate(-2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				break;
			}
		}
		// 체력의 엘릭서 룬
		if (itemId == 900137) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addDmgRate(-2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				break;
			}
		}
		// 지식의 엘릭서 룬
		if (itemId == 900138) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addDmgRate(-2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				break;
			}
		}
		// 지혜의 엘릭서 룬
		if (itemId == 900139) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addDmgRate(-2);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				break;
			}
		}
		// TODO 85레벨 엘릭서 룬
		// 힘의 엘릭서 룬
		if (itemId == 900140) {
			_owner.addPVPMagicDamageReduction(-2);
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addHitup(-2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-5);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -5);
				break;
			}
		}
		// 민첩의 엘릭서 룬
		if (itemId == 900141) {
			_owner.addPVPMagicDamageReduction(-2);
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addHitup(-2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-5);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -5);
				break;
			}
		}
		// 체력의 엘릭서 룬
		if (itemId == 900142) {
			_owner.addPVPMagicDamageReduction(-2);
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addHitup(-2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-5);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -5);
				break;
			}
		}
		// 지식의 엘릭서 룬
		if (itemId == 900143) {
			_owner.addPVPMagicDamageReduction(-2);
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addHitup(-2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-5);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -5);
				break;
			}
		}
		// 지혜의 엘릭서 룬
		if (itemId == 900144) {
			_owner.addPVPMagicDamageReduction(-2);
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addHitup(-2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -5);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-5);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -5);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -5);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -5);
				break;
			}
		}
		// TODO 90레벨 엘릭서 룬
		// 힘의 엘릭서 룬
		if (itemId == 900145) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addHitup(-2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -10);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -10);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -10);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-10);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -10);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -10);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -10);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -10);
				
				break;
			}
		}
		// 민첩의 엘릭서 룬
		if (itemId == 900146) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addHitup(-2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -10);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -10);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -10);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-10);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -10);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -10);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -10);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -10);
				break;
			}
		}
		// 체력의 엘릭서 룬
		if (itemId == 900147) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addHitup(-2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -10);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -10);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -10);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-10);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -10);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -10);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -10);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -10);
				break;
			}
		}
		// 지식의 엘릭서 룬
		if (itemId == 900148) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addHitup(-2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -10);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -10);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -10);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-10);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -10);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -10);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -10);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -10);
				break;
			}
		}
		// 지혜의 엘릭서 룬
		if (itemId == 900149) {
			switch (_owner.getType()) {
			// 군주
			case 0:
				_owner.addDamageReductionByArmor(-3);
				_owner.addHitup(-2);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -10);
				break;
			// 기사
			case 1:
				_owner.addMaxHp(-50);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.ABILITY, -10);
				break;
			// 요정
			case 2:
				_owner.addMaxMp(-50);
				_owner.addBowDmgRate(-1);
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -10);
				break;
			// 법사
			case 3:
				_owner.addMpr(-3);
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-10);
				break;
			// 다엘
			case 4:
				_owner.getAC().addAc(3);
				_owner.addMaxMp(-30);
				_owner.getResistance().addSpecialPierce(eKind.SPIRIT, -10);
				break;
			// 용기사
			case 5:
				_owner.addHitup(-3);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -10);
				break;
			// 환술사
			case 6:
				_owner.addWeightReduction(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, -10);
				break;
			// 전사
			case 7:
				_owner.getResistance().addMr(-5);
				_owner.addMaxHp(-50);
				_owner.getResistance().addSpecialPierce(eKind.FEAR, -10);
				break;
			}
		}
		
		/** 화령의 가더 **/
		if (itemId == 900117) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addDmgRate(-1);
				break;
			case 5:
				_owner.addDmgRate(-1);
				_owner.addHitup(-1);
				break;
			case 6:
				_owner.addDmgRate(-1);
				_owner.addHitup(-2);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				break;
			case 7:
				_owner.addDmgRate(-2);
				_owner.addHitup(-3);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -2);
				break;
			case 8:
				_owner.addDmgRate(-3);
				_owner.addHitup(-4);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -3);
				break;
			case 9:
				_owner.addDmgRate(-4);
				_owner.addHitup(-5);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -4);
				break;
			case 10:
				_owner.addDmgRate(-5);
				_owner.addHitup(-6);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				break;
			default:
				break;
			}
		}
		/** 풍령의 가더 **/
		if (itemId == 900118) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addBowDmgup(-1);
				break;
			case 5:
				_owner.addBowDmgup(-1);
				_owner.addBowHitup(-1);
				break;
			case 6:
				_owner.addBowDmgup(-1);
				_owner.addBowHitup(-2);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				break;
			case 7:
				_owner.addBowDmgup(-2);
				_owner.addBowHitup(-3);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -2);
				break;
			case 8:
				_owner.addBowDmgup(-3);
				_owner.addBowHitup(-4);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -3);
				break;
			case 9:
				_owner.addBowDmgup(-4);
				_owner.addBowHitup(-5);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -4);
				break;
			case 10:
				_owner.addBowDmgup(-5);
				_owner.addBowHitup(-6);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				break;
			default:
				break;
			}
		}
		/** 수령의 가더 **/
		if (itemId == 900119) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getAbility().addSp(-1);
				break;
			case 5:
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-1);
				break;
			case 6:
				_owner.getAbility().addSp(-1);
				_owner.addBaseMagicHitUp(-2);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				break;
			case 7:
				_owner.getAbility().addSp(-2);
				_owner.addBaseMagicHitUp(-3);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -2);
				break;
			case 8:
				_owner.getAbility().addSp(-3);
				_owner.addBaseMagicHitUp(-4);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -3);
				break;
			case 9:
				_owner.getAbility().addSp(-4);
				_owner.addBaseMagicHitUp(-5);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -4);
				break;
			case 10:
				_owner.getAbility().addSp(-5);
				_owner.addBaseMagicHitUp(-6);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				break;
			default:
				break;
			}
		}
		/** 에바의 방패 **/
		if (itemId == 20235) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -2);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -3);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -4);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -5);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -6);
				break;
			case 10:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -7);
				break;
			default:
				break;
			}
		}
		/** 시어의 심안 **/
		if (itemId == 22214) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -3);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -4);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -5);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -6);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -7);
				break;
			case 10:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -8);
				break;
			default:
				break;
			}
		}
		/** 지령의 가더 **/
		if (itemId == 900120) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getResistance().addcalcPcDefense(-1);
				break;
			case 5:
				_owner.getResistance().addcalcPcDefense(-1);
				_owner.getResistance().addMr(-2);
				break;
			case 6:
				_owner.getResistance().addcalcPcDefense(-1);
				_owner.getResistance().addMr(-3);
				break;
			case 7:
				_owner.getResistance().addcalcPcDefense(-2);
				_owner.getResistance().addMr(-4);
				break;
			case 8:
				_owner.getResistance().addcalcPcDefense(-3);
				_owner.getResistance().addMr(-5);
				break;
			case 9:
				_owner.getResistance().addcalcPcDefense(-4);
				_owner.getResistance().addMr(-6);
				break;
			case 10:
				_owner.getResistance().addcalcPcDefense(-5);
				_owner.getResistance().addMr(-7);
				break;
			default:
				break;
			}
		}
		
		/** 마법사의가더 **/
		if (itemId == 22255) {
			switch (itemlvl) {
			case 5:
			case 6:
				_owner.getAbility().addSp(-1);
				break;
			case 7:
			case 8:
				_owner.getAbility().addSp(-2);
				break;
			case 9:
			case 10:
				_owner.getAbility().addSp(-3);
				break;
			default:
				break;
			}
		}
		/** 체력의 가더 **/
		if (itemId == 22256) {
			switch (itemlvl) {
			case 5:
			case 6:
				_owner.addMaxHp(-25);
				break;
			case 7:
			case 8:
				_owner.addMaxHp(-50);
				break;
			case 9:
			case 10:
				_owner.addMaxHp(-75);
				break;
			default:
				break;
			}
		}
		// TODO 투사의 휘장
		if (itemId == 900152) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(-5);
				break;
			case 1:
				_owner.addMaxHp(-10);
				break;
			case 2:
				_owner.addMaxHp(-15);
				break;
			case 4:
				_owner.getAC().addAc(1);
				_owner.addMaxHp(-25);
				break;
			case 5:
				_owner.getAC().addAc(2);
				_owner.addMaxHp(-30);
				_owner.addDmgRate(-1);// 근거리 대미지
				break;
			case 6:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-35);
				_owner.addDmgRate(-2);// 근거리 대미지
				_owner.add_melee_critical_rate(-1);// 근거리 치명타
				break;
			case 7:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-40);
				_owner.addDmgRate(-3);// 근거리 대미지
				_owner.add_melee_critical_rate(-3);// 근거리 치명타
				break;
			case 8:
				_owner.getAC().addAc(3);
				_owner.addDmgRate(-4);// 근거리 대미지
				_owner.add_melee_critical_rate(-5);// 근거리 치명타
				break;
			default:
				break;
			}
		}
		// TODO 명궁의 휘장
		if (itemId == 900153) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(-5);
				break;
			case 1:
				_owner.addMaxHp(-10);
				break;
			case 2:
				_owner.addMaxHp(-15);
				break;
			case 4:
				_owner.getAC().addAc(1);
				_owner.addMaxHp(-25);
				break;
			case 5:
				_owner.getAC().addAc(2);
				_owner.addMaxHp(-30);
				_owner.addBowDmgup(-1);//원거리 대미지
				break;
			case 6:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-35);
				_owner.addBowDmgup(-2);//원거리 대미지
				_owner.add_missile_critical_rate(-1);// 원거리 치명타
				break;
			case 7:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-40);
				_owner.addBowDmgup(-3);//원거리 대미지
				_owner.add_missile_critical_rate(-3);// 원거리 치명타
				break;
			case 8:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-50);
				_owner.addBowDmgup(-4);//원거리 대미지
				_owner.add_missile_critical_rate(-5);// 원거리 치명타
				break;
			default:
				break;
			}
		}
		if (itemId == 123111) {
			switch (itemlvl) {
			case 0:
				_owner.addBaseMagicCritical(-5);
				_owner.addDamageReductionByArmor(-7);
				break;
			case 1:
				_owner.addBaseMagicCritical(-5);
				_owner.addDamageReductionByArmor(-9);
				break;
			case 2:
				_owner.addBaseMagicCritical(-5);
				_owner.addDamageReductionByArmor(-11);
				break;
			case 3:
				_owner.addBaseMagicCritical(-5);
				_owner.addDamageReductionByArmor(-13);
				break;
			case 4:
				_owner.addBaseMagicCritical(-5);
				_owner.addDamageReductionByArmor(-15);
				break;
			case 5:
				_owner.addBaseMagicCritical(-5);
				_owner.addDamageReductionByArmor(-17);
				break;
			case 6:
				_owner.addBaseMagicCritical(-5);
				_owner.addDamageReductionByArmor(-19);
				break;
			case 7:
				_owner.addBaseMagicCritical(-5);
				_owner.addDamageReductionByArmor(-21);
				break;
			case 8:
				_owner.addBaseMagicCritical(-5);
				_owner.addDamageReductionByArmor(-23);
				break;
			case 9:
				_owner.addBaseMagicCritical(-5);
				_owner.addDamageReductionByArmor(-25);
				break;
			case 10:
				_owner.addBaseMagicCritical(-5);
				_owner.addDamageReductionByArmor(-27);
				break;
			default:
				if (itemlvl > 10) {
					_owner.addBaseMagicCritical(-5);
					_owner.addDamageReductionByArmor(-(itemlvl * 2 + 7));
				}
				break;
			}
		}
			//할파스의 예지력
					if (itemId == 123112) {
					switch (itemlvl) {
					case 0:
						_owner.add_missile_critical_rate(-5);
						_owner.addDamageReductionByArmor(-7);
						break;
					case 1:
						_owner.add_missile_critical_rate(-5);
						_owner.addDamageReductionByArmor(-9);
						break;
					case 2:
						_owner.add_missile_critical_rate(-5);
						_owner.addDamageReductionByArmor(-11);
						break;
					case 3:
						_owner.add_missile_critical_rate(-5);
						_owner.addDamageReductionByArmor(-13);
						break;
					case 4:
						_owner.add_missile_critical_rate(-5);
						_owner.addDamageReductionByArmor(-15);
						break;
					case 5:
						_owner.add_missile_critical_rate(-5);
						_owner.addDamageReductionByArmor(-17);
						break;
					case 6:
						_owner.add_missile_critical_rate(-5);
						_owner.addDamageReductionByArmor(-19);
						break;
					case 7:
						_owner.add_missile_critical_rate(-5);
						_owner.addDamageReductionByArmor(-21);
						break;
					case 8:
						_owner.add_missile_critical_rate(-5);
						_owner.addDamageReductionByArmor(-23);
						break;
					case 9:
						_owner.add_missile_critical_rate(-5);
						_owner.addDamageReductionByArmor(-25);
						break;
					case 10:
						_owner.add_missile_critical_rate(-5);
						_owner.addDamageReductionByArmor(-27);
						break;
					default:
						if (itemlvl > 10) {
							_owner.add_missile_critical_rate(-5);
							_owner.addDamageReductionByArmor(-(itemlvl * 2 + 7));
						}
						break;
					}
				}
					//할파스의 완력
					if (itemId == 123113) {
					switch (itemlvl) {
					case 0:
						_owner.add_melee_critical_rate(-5);
						_owner.addDamageReductionByArmor(-7);
						break;
					case 1:
						_owner.add_melee_critical_rate(-5);
						_owner.addDamageReductionByArmor(-9);
						break;
					case 2:
						_owner.add_melee_critical_rate(-5);
						_owner.addDamageReductionByArmor(-11);
						break;
					case 3:
						_owner.add_melee_critical_rate(-5);
						_owner.addDamageReductionByArmor(-13);
						break;
					case 4:
						_owner.add_melee_critical_rate(-5);
						_owner.addDamageReductionByArmor(-15);
						break;
					case 5:
						_owner.add_melee_critical_rate(-5);
						_owner.addDamageReductionByArmor(-17);
						break;
					case 6:
						_owner.add_melee_critical_rate(-5);
						_owner.addDamageReductionByArmor(-19);
						break;
					case 7:
						_owner.add_melee_critical_rate(-5);
						_owner.addDamageReductionByArmor(-21);
						break;
					case 8:
						_owner.add_melee_critical_rate(-5);
						_owner.addDamageReductionByArmor(-23);
						break;
					case 9:
						_owner.add_melee_critical_rate(-5);
						_owner.addDamageReductionByArmor(-25);
						break;
					case 10:
						_owner.add_melee_critical_rate(-5);
						_owner.addDamageReductionByArmor(-27);
						break;
					default:
						if (itemlvl > 10) {
							_owner.add_melee_critical_rate(-5);
							_owner.addDamageReductionByArmor(-(itemlvl * 2 + 7));
						}
						break;
					}
				}
		// TODO 현자의 휘장
		if (itemId == 900154) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(-5);
				break;
			case 1:
				_owner.addMaxHp(-10);
				break;
			case 2:
				_owner.addMaxHp(-15);
				break;
			case 4:
				_owner.getAC().addAc(1);
				_owner.addMaxHp(-25);
				break;
			case 5:
				_owner.getAC().addAc(2);
				_owner.addMaxHp(-30);
				_owner.addHitup(-1);//근거리 명중
				break;
			case 6:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-35);
				_owner.addHitup(-2);//근거리 명중
				_owner.addBaseMagicCritical(-1);// 마법 치명타
				break;
			case 7:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-40);
				_owner.addHitup(-3);//근거리 명중
				_owner.addBaseMagicCritical(-2);// 마법 치명타
				break;
			case 8:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-50);
				_owner.addHitup(-4);//근거리 명중
				_owner.addBaseMagicCritical(-4);// 마법 치명타
				break;
			default:
				break;
			}
		}
	
	

		/** 완력의 문장 효과 **/
		if (itemId == 900049 || itemId == 900093 || itemId == 900096) {
			switch (itemlvl) {
			case 5:
				_owner.addHitup(-1);
				break;
			case 6:
				_owner.addHitup(-1);
				_owner.addDmgRate(-1);
				break;
			case 7:
				_owner.addHitup(-2);
				_owner.addDmgRate(-2);
				break;
			case 8:
				_owner.addHitup(-3);
				_owner.addDmgRate(-3);
				break;
			case 9:
				_owner.addHitup(-4);
				_owner.addDmgRate(-4);
				break;
			case 10:
				_owner.addHitup(-5);
				_owner.addDmgRate(-5);
				break;
			default:
				break;
			}
		}
		
		//고대 마물 시리즈
//		if (itemId == 900015 || itemId == 900016 || itemId == 900017 || itemId == 900018) {
//			switch (itemlvl) {
//			case 0:
//			case 1:
//			case 2:
//			case 3:
//			case 4:
//			case 5:
//			case 6:
//			case 7:
//			case 8:
//			case 9:
//			case 10:
//				_owner.addDmgRate(-Config.DEDMG2);
//				_owner.addBowDmgup(-Config.DEDMG2);
//				break;
//		
//			default:
//				break;
//			}
//		}
		/** 민첩의 문장 효과 **/
		if (itemId == 900050 || itemId == 900094 || itemId == 900097) {
			switch (itemlvl) {
			case 5:
				_owner.addBowHitup(-1);
				break;
			case 6:
				_owner.addBowHitup(-1);
				_owner.addBowDmgup(-1);
				break;
			case 7:
				_owner.addBowHitup(-2);
				_owner.addBowDmgup(-2);
				break;
			case 8:
				_owner.addBowHitup(-3);
				_owner.addBowDmgup(-3);
				break;
			case 9:
				_owner.addBowHitup(-4);
				_owner.addBowDmgup(-4);
				break;
			case 10:
				_owner.addBowHitup(-5);
				_owner.addBowDmgup(-5);
				break;
			default:
				break;
			}
		}
		/** 지식의 문장 효과 **/
		if (itemId == 900051 || itemId == 900095 || itemId == 900098) {
			switch (itemlvl) {
			case 5:
				_owner.addBaseMagicHitUp(-1);
				break;
			case 6:
				_owner.addBaseMagicHitUp(-1);
				_owner.getAbility().addSp(-1);
				break;
			case 7:
				_owner.addBaseMagicHitUp(-2);
				_owner.getAbility().addSp(-2);
				break;
			case 8:
				_owner.addBaseMagicHitUp(-3);
				_owner.getAbility().addSp(-3);
				break;
			case 9:
				_owner.addBaseMagicHitUp(-4);
				_owner.getAbility().addSp(-4);
				break;
			case 10:
				_owner.addBaseMagicHitUp(-5);
				_owner.getAbility().addSp(-5);
				break;
			default:
				break;
			}
		}
		/** 성장/회복의 문장 **/
		if (itemId == 900020 || itemId == 900021 || itemId == 900049 || itemId == 900050 || itemId == 900051 || itemId >= 900093 && itemId <= 900099) {
			switch (itemlvl) {
			case 0:
				_owner.getAC().addAc(-0);
				break;
			case 1:
				_owner.getAC().addAc(-1);
				break;
			case 2:
				_owner.getAC().addAc(-2);
				break;
			case 3:
				_owner.getAC().addAc(-3);
				break;
			case 4:
				_owner.getAC().addAc(-4);
				break;
			case 5:
				_owner.getAC().addAc(-5);
				break;
			case 6:
				_owner.getAC().addAc(-6);
				break;
			case 7:
				_owner.getAC().addAc(-7);
				break;
			case 8:
				_owner.getAC().addAc(-8);
				break;
			case 9:
				_owner.getAC().addAc(-9);
				break;
			case 10:
				_owner.getAC().addAc(-10);
				break;
			default:
				break;
			}
		}

		/** 실프의티셔츠 스턴내성 인챈별 **/
		if (itemId == 900019) {
			switch (itemlvl) {
			case 0:
				_owner.getResistance().addMr(-12);
				_owner.addDamageReductionByArmor(-1);// 대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -11);
				break;
			case 1:
				_owner.getResistance().addMr(-12);
				_owner.addDamageReductionByArmor(-1);// 대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -11);
				break;
			case 2:
				_owner.getResistance().addMr(-12);
				_owner.addDamageReductionByArmor(-1);// 대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -11);
				break;
			case 3:
				_owner.getResistance().addMr(-12);
				_owner.addDamageReductionByArmor(-1);// 대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -11);
				break;
			case 4:
				_owner.getResistance().addMr(-12);
				_owner.addDamageReductionByArmor(-1);// 대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -11);
				break;
			case 5:
				_owner.getResistance().addMr(-12);
				_owner.addDamageReductionByArmor(-1);// 대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -11);
				break;
			case 6:
				_owner.getResistance().addMr(-13);
				_owner.addDamageReductionByArmor(-2);// 대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, -2);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -11);
				break;
			case 7:
				_owner.getResistance().addMr(-14);
				_owner.addDamageReductionByArmor(-3);// 대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, -3);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -11);
				break;
			case 8:
				_owner.getResistance().addMr(-15);
				_owner.addDamageReductionByArmor(-4);// 대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, -4);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -11);
				break;
			case 9:
				_owner.getResistance().addMr(-16);
				_owner.addDamageReductionByArmor(-5);// 대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -11);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addMr(-17);
				_owner.addDamageReductionByArmor(-6);// 대미지 감소
				_owner.getResistance().addSpecialResistance(eKind.ALL, -6);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -11);
				break;
			default:
				break;
			}
		}
		/** 유니콘의 완력 각반 **/
		if (itemId == 900030) {
			switch (itemlvl) {
			case 9:
			case 10:
			case 11:
			case 12:
				_owner.addDmgRate(-1);
				break;
			}
		}
		/** 유니콘의 민첩 각반 **/
		if (itemId == 900031) {
			switch (itemlvl) {
			case 9:
			case 10:
			case 11:
			case 12:
				_owner.addBowHitup(-1);
				break;
			}
		}
		/** 유니콘의 지식 각반 **/
		if (itemId == 900032) {
			switch (itemlvl) {
			case 9:
			case 10:
			case 11:
			case 12:
				_owner.getAbility().addSp(-1);
				break;
			}
		}
		/** 체력의 각반 **/
		if (itemId == 900023) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(-10);
				break;
			case 1:
				_owner.addMaxHp(-15);
				break;
			case 2:
				_owner.addMaxHp(-20);
				break;
			case 3:
				_owner.addMaxHp(-25);
				break;
			case 4:
				_owner.addMaxHp(-30);
				break;
			case 5:
				_owner.addMaxHp(-35);
				break;
			case 6:
				_owner.addMaxHp(-40);
				break;
			case 7:
				_owner.addMaxHp(-45);
				break;
			case 8:
				_owner.addMaxHp(-50);
				break;
			case 9:
				_owner.addMaxHp(-55);
				break;
			default:
				break;
			}
		}
		/** 예언자의 견갑 **/
		if (itemId == 900080) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBaseMagicHitUp(-1);// 마법 적중
				break;
			default:
				break;
			}
		}
		/** 칠흑의 망토  **/
		if (itemId == 900076) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
				_owner.getAbility().addCha(-1);
				break;
			case 7:
				_owner.getAbility().addCha(-2);
				break;
			case 8:
				_owner.getAbility().addCha(-3);
				break;
			case 9:
				_owner.getAbility().addCha(-4);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addCha(-4);
				break;
			default:
				break;
			}
		}
		/** 맘보 모자 **/
		if (itemId == 20016 || itemId == 120016 || itemId == 20112 || itemId == 120112) {
			switch (itemlvl) {
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addCha(-1);
				break;
			default:
				break;
			}
		}
		/** 커츠의 투사 휘장 **/
		if (itemId == 900081) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(-5);
				break;	
			case 1:
				_owner.addMaxHp(-10);
				break;	
			case 2:
				_owner.addMaxHp(-15);
				break;	
			case 3:
				_owner.addMaxHp(-20);
				break;	
			case 4:
				_owner.getAC().addAc(1);
				_owner.addMaxHp(-25);
				break;	
			case 5:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-30);
				_owner.addHitup(-1);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addcalcPcDefense(-1);//PVP대미지감소
				_owner.addEinhasadBlessper(-5);// 축복 소모 효율
				break;
			case 6:
				_owner.getAC().addAc(5);//AC
				_owner.addMaxHp(-35);//HP
				_owner.addHitup(-1);//근거리 명중
				_owner.addDamageReductionByArmor(-2);//대미지감소
				_owner.getResistance().addMr(-3);//MR
				_owner.addDmgRate(-2);//근거리 대미지
				_owner.getResistance().addcalcPcDefense(-2);//PVP대미지감소
				_owner.addEinhasadBlessper(-10);// 축복 소모 효율
				_owner.add_melee_critical_rate(-1);
				break;
			case 7:
				_owner.getAC().addAc(6);
				_owner.addMaxHp(-40);
				_owner.addDamageReductionByArmor(-3);//대미지감소
				_owner.getResistance().addMr(-5);//MR
				_owner.addDmgRate(-3);//근거리 대미지
				_owner.addHitup(-3);//근거리 명중
				_owner.getResistance().addcalcPcDefense(-3);//PVP대미지감소
				_owner.addEinhasadBlessper(-15);// 축복 소모 효율
				_owner.add_melee_critical_rate(-3);
				break;
			case 8:
				_owner.getAC().addAc(7);
				_owner.addMaxHp(-50);
				_owner.addDamageReductionByArmor(-4);//대미지감소
				_owner.getResistance().addMr(-7);//MR
				_owner.addDmgRate(-4);//근거리 대미지
				_owner.addHitup(-5);//근거리 명중
				_owner.add_melee_critical_rate(-5);
				_owner.getResistance().addcalcPcDefense(-5);//PVP대미지감소
				_owner.addEinhasadBlessper(-20);// 축복 소모 효율
				break;
			case 9:
				_owner.getAC().addAc(8);
				_owner.addMaxHp(-60);
				_owner.addDamageReductionByArmor(-5);//대미지감소
				_owner.getResistance().addMr(-9);//MR
				_owner.addDmgRate(-5);//근거리 대미지
				_owner.addHitup(-7);//근거리 명중
				_owner.getResistance().addcalcPcDefense(-7);//PVP대미지감소
				_owner.addEinhasadBlessper(-25);// 축복 소모 효율
				_owner.add_melee_critical_rate(-7);
				break;
			default:
				break;
			}
		}		
		/** 커츠의 명궁 휘장 **/
		if (itemId == 900082) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(-5);
				break;	
			case 1:
				_owner.addMaxHp(-10);
				break;	
			case 2:
				_owner.addMaxHp(-15);
				break;	
			case 3:
				_owner.addMaxHp(-20);
				break;	
			case 4:
				_owner.getAC().addAc(1);
				_owner.addMaxHp(-25);
				break;	
			case 5:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-30);
				_owner.addBowHitup(-1);
				_owner.addDamageReductionByArmor(-1);
				_owner.getResistance().addcalcPcDefense(-1);//PVP대미지감소
				_owner.addEinhasadBlessper(-5);// 축복 소모 효율
				break;
			case 6:
				_owner.getAC().addAc(5);
				_owner.addMaxHp(-35);
				_owner.addDamageReductionByArmor(-2);
				_owner.getResistance().addMr(-3);//MR
				_owner.addBowDmgup(-2);
				_owner.addBowHitup(-1);
				_owner.getResistance().addcalcPcDefense(-2);//PVP대미지감소
				_owner.addEinhasadBlessper(-10);// 축복 소모 효율
				_owner.add_missile_critical_rate(-1);
				break;
			case 7:
				_owner.getAC().addAc(6);
				_owner.addMaxHp(-40);
				_owner.addDamageReductionByArmor(-3);
				_owner.getResistance().addMr(-5);//MR
				_owner.addBowDmgup(-3);
				_owner.addBowHitup(-3);
				_owner.getResistance().addcalcPcDefense(-3);//PVP대미지감소
				_owner.addEinhasadBlessper(-15);// 축복 소모 효율
				_owner.add_missile_critical_rate(-3);
				
				break;
			case 8:
				_owner.getAC().addAc(7);
				_owner.addMaxHp(-50);
				_owner.addDamageReductionByArmor(-4);//대미지 감소
				_owner.getResistance().addMr(-7);//MR
				_owner.addBowDmgup(-4);
				_owner.addBowHitup(-5);
				_owner.getResistance().addcalcPcDefense(-5);//PVP대미지감소
				_owner.addEinhasadBlessper(-20);// 축복 소모 효율
				_owner.add_missile_critical_rate(-5);
				break;
			case 9:
				_owner.getAC().addAc(8);
				_owner.addMaxHp(-60);
				_owner.addDamageReductionByArmor(-5);//대미지 감소
				_owner.getResistance().addMr(-9);//MR
				_owner.addBowDmgup(-5);
				_owner.addBowHitup(-7);
				_owner.getResistance().addcalcPcDefense(-7);//PVP대미지감소
				_owner.addEinhasadBlessper(-25);// 축복 소모 효율
				_owner.add_missile_critical_rate(-7);
				break;
			default:
				break;
			}
		}	
		/** 커츠의 현자 휘장 **/
		if (itemId == 900083) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(-5);
				break;	
			case 1:
				_owner.addMaxHp(-10);
				break;	
			case 2:
				_owner.addMaxHp(-15);
				break;	
			case 3:
				_owner.addMaxHp(-20);
				break;	
			case 4:
				_owner.getAC().addAc(1);
				_owner.addMaxHp(-25);
				break;	
			case 5:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-30);
				_owner.addDamageReductionByArmor(-1);//대미지 감소
				_owner.addHitup(-1);
				_owner.getResistance().addcalcPcDefense(-1);//PVP대미지감소
				_owner.addEinhasadBlessper(-5);// 축복 소모 효율
				break;
			case 6:
				_owner.getAC().addAc(5);
				_owner.addMaxHp(-35);
				_owner.addDamageReductionByArmor(-2);//대미지 감소
				_owner.getResistance().addMr(-3);//MR
				_owner.addHitup(-2);//근거리 명중
				_owner.addBaseMagicCritical(-1);// 마법 치명타
				_owner.getResistance().addcalcPcDefense(-2);//PVP대미지감소
				_owner.addEinhasadBlessper(-10);// 축복 소모 효율
				break;
			case 7:
				_owner.getAC().addAc(6);
				_owner.addMaxHp(-40);
				_owner.addDamageReductionByArmor(-3);//대미지 감소
				_owner.getResistance().addMr(-5);//MR
				_owner.addHitup(-3);//근거리 명중
				_owner.addBaseMagicCritical(-2);// 마법 치명타
				_owner.getResistance().addcalcPcDefense(-3);//PVP대미지감소
				_owner.addEinhasadBlessper(-15);// 축복 소모 효율
				break;
			case 8:
				_owner.getAC().addAc(7);
				_owner.addMaxHp(-50);
				_owner.addDamageReductionByArmor(-4);//대미지 감소
				_owner.getResistance().addMr(-7);//MR
				_owner.addHitup(-4);//근거리 명중
				_owner.addBaseMagicCritical(-3);// 마법 치명타
				_owner.getResistance().addcalcPcDefense(-5);//PVP대미지감소
				_owner.addEinhasadBlessper(-20);// 축복 소모 효율
				break;
			case 9:
				_owner.getAC().addAc(8);
				_owner.addMaxHp(-60);
				_owner.addDamageReductionByArmor(-5);//대미지 감소
				_owner.getResistance().addMr(-9);//MR
				_owner.addHitup(-5);//근거리 명중
				_owner.addBaseMagicCritical(-4);// 마법 치명타
				_owner.getResistance().addcalcPcDefense(-7);//PVP대미지감소
				_owner.addEinhasadBlessper(-25);// 축복 소모 효율
				break;
			default:
				break;
			}
		}	
		/** 커츠의 수호 휘장 **/
		if (itemId == 900084) {
			switch (itemlvl) {
			case 0:
				_owner.addMaxHp(-5);
				break;	
			case 1:
				_owner.addMaxHp(-10);
				break;	
			case 2:
				_owner.addMaxHp(-15);
				break;	
			case 3:
				_owner.getAC().addAc(1);
				_owner.addMaxHp(-20);
				break;	
			case 4:
				_owner.getAC().addAc(2);
				_owner.addMaxHp(-25);
				break;	
			case 5:
				_owner.getAC().addAc(3);
				_owner.addMaxHp(-30);
				_owner.addDamageReductionByArmor(-1);
				break;	
			case 6:
				_owner.getAC().addAc(5);
				_owner.addMaxHp(-35);
				_owner.addDamageReductionByArmor(-2);
				_owner.getResistance().addMr(-3);
				break;	
			case 7:
				_owner.getAC().addAc(6);
				_owner.addMaxHp(-40);
				_owner.addDamageReductionByArmor(-3);
				_owner.getResistance().addMr(-5);
				break;	
			case 8:
				_owner.getAC().addAc(7);
				_owner.addMaxHp(-50);
				_owner.addDamageReductionByArmor(-4);
				_owner.getResistance().addMr(-7);
				break;
			default:
				break;
			}
		}	
		/** 대마법사의 모자 **/
		if (itemId == 202022) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -2);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -3);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -4);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -5);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -6);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -7);
				break;
			default:
				break;
			}
		}
		/** 지룡의 티셔츠 **/
		if (itemId == 900025) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addMr(-4);
				break;
			case 6:
				_owner.getResistance().addMr(-5);
				break;
			case 7:
				_owner.addDamageReductionByArmor(-1);
				break;
			case 8:
				_owner.getResistance().addMr(-8);
				_owner.addDamageReductionByArmor(-1);
				break;
			case 9:
				_owner.getResistance().addMr(-11);
				_owner.addDamageReductionByArmor(-2);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addMr(-14);
				_owner.addDamageReductionByArmor(-2);
				_owner.addMaxHp(-100);
				_owner.getResistance().addPVPweaponTotalDamage(-1);
				_owner.getResistance().addcalcPcDefense(-1);
				break;
			}
		}
		/** 화룡의 티셔츠 **/
		if (itemId == 900026) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addDmgRate(-1);
				break;
			case 5:
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -8);
				break;
			case 6:
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
				break;
			case 7:
				_owner.addDmgRate(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -10);
				break;
			case 8:
				_owner.addDmgRate(-1);
				_owner.addHitup(-2);//근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -12);
				break;
			case 9:
				_owner.addDmgRate(-2);
				_owner.addHitup(-4);//근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -15);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addDmgRate(-2);
				_owner.addHitup(-6);//근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -18);
				_owner.addMaxHp(-100);
				_owner.getResistance().addPVPweaponTotalDamage(-1);
				_owner.getResistance().addcalcPcDefense(-1);
				break;
			default:
				break;
			}
		}
		/** 풍룡의 티셔츠 **/
		if (itemId == 900027) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addBowHitup(-1);
				break;
			case 5:
				_owner.addBowHitup(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -8);
				break;
			case 6:
				_owner.addBowHitup(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
				break;
			case 7:
				_owner.addBowHitup(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -10);
				break;
			case 8:
				_owner.addBowHitup(-1);
				_owner.addBowHitup(-2);//원거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -12);
				break;
			case 9:
				_owner.addBowHitup(-2);
				_owner.addBowHitup(-3);//원거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -15);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBowHitup(-2);
				_owner.addBowHitup(-6);//원거리 명중
				_owner.addMaxHp(-100);
				_owner.getResistance().addPVPweaponTotalDamage(-1);
				_owner.getResistance().addcalcPcDefense(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -18);
				break;
			default:
				break;
			}
		}
		/** 수룡의 티셔츠 **/
		if (itemId == 900028) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getAbility().addSp(-1);
				break;
			case 5:
				_owner.getAbility().addSp(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -8);
				break;
			case 6:
				_owner.getAbility().addSp(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
				break;
			case 7:
				_owner.getAbility().addSp(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -10);
				break;
			case 8:
				_owner.getAbility().addSp(-2);
				_owner.addBaseMagicHitUp(-1);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -12);
				break;
			case 9:
				_owner.getAbility().addSp(-2);
				_owner.addBaseMagicHitUp(-3);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -15);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addSp(-3);
				_owner.addBaseMagicHitUp(-4);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -18);
				_owner.addMaxHp(-100);
				_owner.getResistance().addPVPweaponTotalDamage(-1);
				_owner.getResistance().addcalcPcDefense(-1);
				break;
			default:
				break;
			}
		}
		/** 축복받은 지룡의 티셔츠 **/
		if (itemId == 900184) {
			switch (itemlvl) {
			case 5:
				_owner.getResistance().addMr(-4);
				break;
			case 6:
				_owner.getResistance().addMr(-5);
				break;
			case 7:
				_owner.getResistance().addMr(-6);
				_owner.addDamageReductionByArmor(-1);
				break;
			case 8:
				_owner.getResistance().addMr(-8);
				_owner.addDamageReductionByArmor(-1);
				break;
			case 9:
				_owner.getResistance().addMr(-11);
				_owner.addDamageReductionByArmor(-2);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addMr(-14);
				_owner.addDamageReductionByArmor(-2);
				_owner.addMaxHp(-100);
				_owner.getResistance().addPVPweaponTotalDamage(-1);
				_owner.getResistance().addcalcPcDefense(-1);
				break;
			}
		}
		/** 축복받은 화룡의 티셔츠 **/
		if (itemId == 900185) {
			switch (itemlvl) {
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -8);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
				break;
			case 7:
				_owner.addDmgRate(-1);
				_owner.addHitup(-1);// 근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -10);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				break;
			case 8:
				_owner.addDmgRate(-1);
				_owner.addHitup(-3);// 근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -12);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -2);
				break;
			case 9:
				_owner.addDmgRate(-2);
				_owner.addHitup(-5);// 근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -15);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -4);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addDmgRate(-2);
				_owner.addHitup(-7);// 근거리 명중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -18);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -6);
				_owner.addMaxHp(-100);
				_owner.getResistance().addPVPweaponTotalDamage(-1);
				_owner.getResistance().addcalcPcDefense(-1);
				break;
			default:
				break;
			}
		}
		/** 축복받은 풍룡의 티셔츠 **/
		if (itemId == 900186) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addBowHitup(-1);
				break;
			case 5:
				_owner.addBowHitup(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -8);
				break;
			case 6:
				_owner.addBowHitup(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
				break;
			case 7:
				_owner.addBowHitup(-2);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -10);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				break;
			case 8:
				_owner.addBowHitup(-3);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -12);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -2);
				break;
			case 9:
				_owner.addBowHitup(-5);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -15);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -4);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBowHitup(-7);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -18);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -6);
				_owner.addMaxHp(-100);
				_owner.getResistance().addPVPweaponTotalDamage(-1);
				_owner.getResistance().addcalcPcDefense(-1);
				break;
			default:
				break;
			}
		}
		/** 축복받은 수룡의 티셔츠 **/
		if (itemId == 900187) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getAbility().addSp(-1);
				break;
			case 5:
				_owner.getAbility().addSp(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -8);
				break;
			case 6:
				_owner.getAbility().addSp(-1);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
				break;
			case 7:
				_owner.getAbility().addSp(-2);
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -10);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				break;
			case 8:
				_owner.getAbility().addSp(-2);
				_owner.addBaseMagicHitUp(-2);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -12);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -2);
				break;
			case 9:
				_owner.getAbility().addSp(-2);
				_owner.addBaseMagicHitUp(-4);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -15);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -4);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addSp(-3);
				_owner.addBaseMagicHitUp(-5);// 마법 적중
				_owner.getResistance().addSpecialResistance(eKind.ABILITY, -18);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -6);
				_owner.addMaxHp(-100);
				_owner.getResistance().addPVPweaponTotalDamage(-1);
				_owner.getResistance().addcalcPcDefense(-1);
				break;
			default:
				break;
			}
		}
		
		/** 스텟부츠 리뉴얼 **/
		if (itemId == 22359 || itemId == 222308 || itemId ==222309 || itemId ==222307) {
			switch (itemlvl) {
			case 7:
				_owner.addMaxHp(-20);
				break;
			case 8:
				_owner.addMaxHp(-40);
				break;
			case 9:
				_owner.addMaxHp(-60);
				_owner.getResistance().addcalcPcDefense(-1);
				break;
			case 10:
				_owner.addMaxHp(-60);
				_owner.getResistance().addcalcPcDefense(-1);
				break;
			default:
				break;
			}
		}
		/** 고대 투사의 가더 **/
		if (itemId == 22003) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addDmgRate(-1);
				break;
			case 5:
			case 6:
				_owner.addDmgRate(-2);
				break;
			case 7:
			case 8:
				_owner.addDmgRate(-3);
				break;
			case 9:
			case 10:
				_owner.addDmgRate(-4);
				break;
			default:
				break;
			}
		}

		/** 고대 명궁의 가더 **/
		if (itemId == 22000) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addBowHitup(-1);
				break;
			case 5:
			case 6:
				_owner.addBowHitup(-2);
				break;
			case 7:
			case 8:
				_owner.addBowHitup(-3);
				break;
			case 9: case 10:
				_owner.addBowHitup(-4);
				break;
			default:
				break;
			}
		}
		/** 호박 목걸이 **/
		if (itemId == 900024) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
				_owner.addBaseMagicHitUp(-3);// 마법 적중
				break;
			}
		}
		/** 수령의 귀걸이 **/
		if (itemId == 900114) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
				_owner.addBaseMagicHitUp(-2);
				break;
			}
		}
		/** 수호의 가더 **/
		if (itemId == 22254) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addDamageReductionByArmor(-1);
				break;
			case 5:
			case 6:
				_owner.addDamageReductionByArmor(-2);
				break;
			case 7:
			case 8:
				_owner.addDamageReductionByArmor(-3);
				break;
			case 9:
			case 10:
				_owner.addDamageReductionByArmor(-4);
				break;
			default:
				break;
			}
		}
		/** 거대여왕 개미의 금빛,은빛 날개 **/
		if (itemId == 20049 || itemId == 20050 || itemId == 900056 || itemId == 900057) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -3);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -4);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -5);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -6);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -7);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialResistance(eKind.SPIRIT, -7);
				break;
			}
		}
		/** 리치의 반지 **/
		if (itemId == 900163) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
				_owner.getAbility().addSp(-3);
				_owner.addBaseMagicCritical(-3);// 마법 치명타
				break;
			case 7:
				_owner.getAbility().addSp(-4);
				_owner.addBaseMagicCritical(-3);// 마법 치명타
				break;
			case 8:
				_owner.getAbility().addSp(-5);
				_owner.addBaseMagicCritical(-3);// 마법 치명타
				break;
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addSp(-6);
				_owner.addBaseMagicCritical(-3);// 마법 치명타
				break;
			}
		}
		/** 뱀파이어의 망토 **/
		if (itemId == 20079) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -3);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -4);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -5);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -6);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialResistance(eKind.FEAR, -7);
				break;
			}
		}
	
		
		if (itemId == 988171) {
			switch (itemlvl) {
			case 0:
				break;
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:	
			case 7:	
				_owner.getAC().addAc(5);
				_owner.addMaxHp(-50);
				_owner.addMaxMp(-20);
				_owner.getResistance().addMr(-20);
				_owner.addEffectedER(-2);
				_owner.getResistance().addPVPweaponTotalDamage(-1);
				break;
			case 8:	
				_owner.getAC().addAc(6);
				_owner.addMaxHp(-60);
				_owner.addMaxMp(-30);
				_owner.getResistance().addMr(-21);
				_owner.addEffectedER(-3);
				_owner.getResistance().addPVPweaponTotalDamage(-2);
				break;
			case 9:		
				_owner.getAC().addAc(7);
				_owner.addMaxHp(-70);
				_owner.addMaxMp(-40);
				_owner.getResistance().addMr(-22);
				_owner.addEffectedER(-4);
				_owner.getResistance().addPVPweaponTotalDamage(-3);
				break;
			case 10:
				_owner.getAC().addAc(8);
				_owner.addMaxHp(-80);
				_owner.addMaxMp(-50);
				_owner.getResistance().addMr(-23);
				_owner.addEffectedER(-5);
				_owner.getResistance().addPVPweaponTotalDamage(-4);
				break;
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				break;

			}
		}
		/** 아이리스의 부츠 **/
		if (itemId == 900155) {
			switch (itemlvl) {
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.ALL, -2);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.ALL, -3);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.ALL, -4);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				break;
			default:
				break;
			}
		}
		/** 머미로드의 장갑 **/
		if (itemId == 900156) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
				_owner.getAbility().addSp(-1);
				break;
			case 7:
				_owner.getAbility().addSp(-2);
				break;
			case 8:
				_owner.getAbility().addSp(-3);
				break;
			case 9:
				_owner.getAbility().addSp(-3);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.getAbility().addSp(-3);
				break;
			default:
				break;
			}
		}
		/** 쿠거의 가더 **/
		if (itemId == 900157) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addHitup(-3);
				break;
			case 5:
				_owner.addHitup(-4);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				break;
			case 6:
				_owner.addHitup(-5);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -2);
				break;
			case 7:
				_owner.addHitup(-6);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -3);
				break;
			case 8:
				_owner.addHitup(-7);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -4);
				break;
			case 9:
				_owner.addHitup(-8);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addHitup(-8);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				break;
			default:
				break;
			}
		}
		/** 우그누스의 가더 **/
		if (itemId == 900158) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.addBowHitup(-3);
				break;
			case 5:
				_owner.addBowHitup(-4);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
				break;
			case 6:
				_owner.addBowHitup(-5);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -2);
				break;
			case 7:
				_owner.addBowHitup(-6);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -3);
				break;
			case 8:
				_owner.addBowHitup(-7);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -4);
				break;
			case 9:
				_owner.addBowHitup(-8);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBowHitup(-8);
				_owner.getResistance().addSpecialResistance(eKind.ALL, -5);
				break;
			default:
				break;
			}
		}
		/** 나이트발드의 각반 **/
		if (itemId == 900159) {
			switch (itemlvl) {
			case 5:
				_owner.addHitup(-1);
				break;
			case 6:
				_owner.addBowHitup(-2);
				break;
			case 7:
				_owner.addBowHitup(-3);
				break;
			case 8:
				_owner.addBowHitup(-4);
				break;
			case 9:
				_owner.addBowHitup(-5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBowHitup(-5);
				break;
			default:
				break;
			}
		}
		/** 아이리스의 각반 **/
		if (itemId == 900160) {
			switch (itemlvl) {
			case 5:
				_owner.addBowHitup(-1);
				break;
			case 6:
				_owner.addBowHitup(-2);
				break;
			case 7:
				_owner.addBowHitup(-3);
				break;
			case 8:
				_owner.addBowHitup(-4);
				break;
			case 9:
				_owner.addBowHitup(-5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBowHitup(-5);
				break;
			default:
				break;
			}
		}
		/** 뱀파이어의 각반 **/
		if (itemId == 900161) {
			switch (itemlvl) {
			case 5:
				_owner.addBaseMagicHitUp(-1);
				break;
			case 6:
				_owner.addBaseMagicHitUp(-2);
				break;
			case 7:
				_owner.addBaseMagicHitUp(-3);
				break;
			case 8:
				_owner.addBaseMagicHitUp(-4);
				break;
			case 9:
				_owner.addBaseMagicHitUp(-5);
				break;
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				_owner.addBaseMagicHitUp(-5);
				break;
			default:
				break;
			}
		}
		/** 흑기사의 면갑 대미지감소 **/
		if (itemId == 900038 || itemId == 900054) {
			switch (itemlvl) {
			case 5:
				_owner.getResistance().addMr(-4);
				break;
			case 6:
				_owner.getResistance().addMr(-8);
				break;
			case 7:
				_owner.getResistance().addMr(-12);
				break;
			case 8:
				_owner.getResistance().addMr(-16);
				break;
			case 9:
				_owner.getResistance().addMr(-20);
				break;
			case 10:
				_owner.getResistance().addMr(-24);
				break;
			}
		}
		/** 발라카스의 완력 **/
		if (itemId >= 22208 && itemId <= 22211) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -2);
				break;
			case 6:
				_owner.getResistance().addcalcPcDefense(-3);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -3);
				break;
			case 7:
				_owner.getResistance().addcalcPcDefense(-4);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -4);
				break;
			case 8:
				_owner.getResistance().addcalcPcDefense(-5);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -5);
				break;
			case 9:
				_owner.getResistance().addcalcPcDefense(-6);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -6);
				break;
			case 10:
				_owner.getResistance().addcalcPcDefense(-7);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -7);
				break;
			}
		}
		
		/** 신성한 엘름의 축복 **/
		if (itemId == 900035 || itemId == 900072) {
			switch (itemlvl) {
			case 5:
				_owner.getResistance().addMr(-4);
				break;
			case 6:
				_owner.getResistance().addMr(-8);
				break;
			case 7:
				_owner.getResistance().addMr(-12);
				break;
			case 8:
				_owner.getResistance().addMr(-16);
				break;
			case 9:
				_owner.getResistance().addMr(-20);
				break;
			case 10:
				_owner.getResistance().addMr(-24);
				break;
			default:
				break;
			}
		}
		if (itemId == 900039) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
				_owner.addHitup(-2);
				break;
			default:
				break;
			}
		}
		if (itemId == 900040) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
				_owner.addBowHitup(-2);
				break;
			default:
				break;
			}
		}
		if (itemId == 900041) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
				_owner.addBaseMagicHitUp(-2);// 마법 적중
				break;
			default:
				break;
			}
		}
		if (itemId == 900036 || itemId == 900073) {
			switch (itemlvl) {
			case 5:
				_owner.addHitup(-1);
				break;
			case 6:
				_owner.addHitup(-2);
				break;
			case 7:
				_owner.addHitup(-3);
				break;
			case 8:
				_owner.addHitup(-4);
				break;
			case 9:
				_owner.addHitup(-5);
				break;
			case 10:
				_owner.addHitup(-5);
				break;
			default:
				 if (itemlvl > 10) {
					 _owner.addHitup(-5);
					}
				break;
			}
		}
		if (itemId == 900037 || itemId == 900074) {
			switch (itemlvl) {
			case 5:
				_owner.addBowHitup(-1);
				break;
			case 6:
				_owner.addBowHitup(-2);
				break;
			case 7:
				_owner.addBowHitup(-3);
				break;
			case 8:
				_owner.addBowHitup(-4);
				break;
			case 9:
				_owner.addBowHitup(-5);
				break;
			case 10:
				_owner.addBowHitup(-5);
				break;
			default:
				 if (itemlvl > 10) {
					 _owner.addBowHitup(-5);
					}
				break;
			}
		}
		/** 안타라스의 완력/예지력/인내력/마력 **/
		if (itemId >= 22196 && itemId <= 22199) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -2);
				break;
			case 6:
				_owner.addDamageReductionByArmor(-3);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -3);
				break;
			case 7:
				_owner.addDamageReductionByArmor(-4);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -4);
				break;
			case 8:
				_owner.addDamageReductionByArmor(-5);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -5);
				break;
			case 9:
				_owner.addDamageReductionByArmor(-6);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -6);
				break;
			case 10:
				_owner.addDamageReductionByArmor(-7);
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -7);
				break;
			default:
				break;
			}
		}
		/** 파푸리온의 완력/예지력/인내력/마력 **/
		if (itemId >= 22200 && itemId <= 22203) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -2);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -3);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -4);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -5);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -6);
				break;
			case 10:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -7);
				break;
			default:
				break;
			}
		}
		/** 린드비오르의 완력/예지력/인내력/마력 **/
		if (itemId >= 22204 && itemId <= 22207) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -2);
				break;
			case 5:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -3);
				break;
			case 6:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -4);
				break;
			case 7:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -5);
				break;
			case 8:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -6);
				break;
			case 9:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -7);
				break;
			case 10:
				_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -8);
				break;
			default:
				if (itemlvl > 10) {
					_owner.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -9);
				}
				break;
			}	
		}
		/** 빛나는 마력의장갑 **/
		if (itemId == 20274) {
			switch (itemlvl) {
			case 0:
			case 1:
			case 3:
			case 4:
				_owner.addWeightReduction(-300);
				break;
			default:
				if (itemlvl > 4) {
					_owner.addWeightReduction(-(300 + ((itemlvl - 4) * 60)));
					}
				break;
			}
		}
		/** 격분의 장갑 **/
		if (itemId == 222317) {
			switch (itemlvl) {
			case 7:
				
				_owner.addHitup(-4);
				break;
			case 8:
				
				_owner.addHitup(-5);
				break;
			case 9:
				
				_owner.addHitup(-6);
				break;
			case 10:
				
				_owner.addHitup(-7);
				break;
			default:
				if (itemlvl > 10) {
					
					_owner.addHitup(-(itemlvl - 3));
					}
				break;
			}
		}
		if (itemId == 22214) {
			if (itemlvl > 6) {
				_owner.addBaseMagicHitUp(-(itemlvl - 6));
				}
			}
		
		if (itemId == 20279) {
			
			_owner.getResistance().addcalcPcDefense(-2);
				
			}
		if (itemId == 20314) {

			_owner.getResistance().addPVPweaponTotalDamage(-2);
			

			}
		
		/** 머미로드의 왕관 **/
		if (itemId == 20017) {
		 int cc = itemlvl - 4;
		 if (itemlvl > 4) {
					_owner.addBowDmgup(-itemlvl);
					_owner.getResistance().addMr(-(cc * 3));
				}
		}
		//TODO (장신구) 목걸이,귀걸이 인챈트 리뉴얼
		if (itemtype >= 8 && itemtype <= 12) {
			if ((itemgrade >= 0 && itemgrade <= 2) && itemtype == 8 ) { //목걸이
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(-5);
					break;
				case 2:
					_owner.addMaxHp(-10);
					break;
				case 3:
					_owner.addMaxHp(-20);
					break;
				case 4:
					_owner.addMaxHp(-30);
					break;
				case 5:
					_owner.setAccessoryHeal(-3);
					_owner.addMaxHp(-40);
					_owner.getResistance().addMr(-1);
					break;
				case 6:
					_owner.setAccessoryHeal(-5);
					_owner.addMaxHp(-40);
					_owner.getResistance().addMr(-3);
					break;
				case 7:
					_owner.setAccessoryHeal(-7);
					_owner.addMaxHp(-50);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -2);
					_owner.getResistance().addMr(-5);
					break;
				case 8:
					_owner.setAccessoryHeal(-9);
					_owner.addMaxHp(-50);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -3);
					_owner.getResistance().addMr(-7);
					break;
				case 9:
					_owner.setAccessoryHeal(-10);
					_owner.addMaxHp(-60);
					_owner.getResistance().addMr(-10);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -4);
					break;
				case 10:
					_owner.setAccessoryHeal(-11);
					_owner.addMaxHp(-100);
					_owner.getResistance().addMr(-12);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -4);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
					break;
				}
			} else if ((itemgrade >= 0 && itemgrade <= 2) && itemtype == 12) { //귀걸이
					switch (itemlvl) {
					case 1:
						_owner.addMaxHp(-5);
						break;
					case 2:
						_owner.addMaxHp(-10);
						break;
					case 3:
						_owner.addMaxHp(-20);
						break;
					case 4:
						_owner.addMaxHp(-30);
						break;
					case 5:
						_owner.setAccessoryHeal(-2);
						_owner.addMaxHp(-40);
						
						break;
					case 6:
						_owner.setAccessoryHeal(-4);
						_owner.addMaxHp(-40);
						
						break;
					case 7:
						_owner.setAccessoryHeal(-6);
						_owner.addMaxHp(-50);
						_owner.getResistance().addSpecialResistance(eKind.ABILITY, -2);
						break;
					case 8:
						_owner.setAccessoryHeal(-8);
						_owner.addMaxHp(-50);
						_owner.getResistance().addSpecialResistance(eKind.ABILITY, -3);
						break;
					case 9:
						_owner.setAccessoryHeal(-9);
						_owner.addMaxHp(-60);
						_owner.getResistance().addSpecialResistance(eKind.ABILITY, -4);
						break;
					case 10:
						_owner.setAccessoryHeal(-10);
						_owner.addMaxHp(-70);
						_owner.getResistance().addSpecialResistance(eKind.ABILITY, -4);
						_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
						_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
						break;
					}
				// TODO (장신구) 반지 인챈트 리뉴얼
			} else if (((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5) && (itemtype == 9 || itemtype == 11)) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(-5);
					break;
				case 2:
					_owner.addMaxHp(-10);
					break;
				case 3:
					_owner.addMaxHp(-20);
					break;
				case 4:
					_owner.addMaxHp(-30);
					break;
				case 5:
					_owner.addDmgup(-1);
					_owner.addBowDmgup(-1);
					_owner.addMaxHp(-40);
					break;
				case 6:
					_owner.addDmgup(-2);
					_owner.addBowDmgup(-2);
					_owner.addMaxHp(-40);
					_owner.getResistance().addMr(-1);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					break;
				case 7:
					_owner.addDmgup(-3);
					_owner.addBowDmgup(-3);
					_owner.addMaxHp(-50);
					if (itemId != 900163) {
					_owner.getAbility().addSp(-1);
					}
					_owner.getResistance().addMr(-3);
					_owner.getResistance().addPVPweaponTotalDamage(-2);
					break;
				case 8:
					_owner.addDmgup(-4);
					_owner.addBowDmgup(-4);
					_owner.addMaxHp(-50);
					if (itemId != 900163) {
					_owner.getAbility().addSp(-2);
					}
					_owner.getResistance().addMr(-5);
					_owner.getResistance().addPVPweaponTotalDamage(-3);
					break;
				case 9:
					_owner.addDmgup(-5);
					_owner.addBowDmgup(-5);
					_owner.addMaxHp(-60);
					if (itemId != 900163) {
					_owner.getAbility().addSp(-3);
					}
					_owner.getResistance().addMr(-7);
					_owner.getResistance().addPVPweaponTotalDamage(-5);
					break;
				case 10:
					_owner.addDmgup(-6);
					_owner.addBowDmgup(-6);
					_owner.addMaxHp(-70);
					if (itemId != 900163) {
					_owner.getAbility().addSp(-4);
					}
					_owner.getResistance().addMr(-9);
					_owner.getResistance().addPVPweaponTotalDamage(-7);
					_owner.getResistance().addSpecialResistance(eKind.ALL, -1);
					break;
				}
				// TODO (장신구) 벨트 인챈트 리뉴얼
			} else if ((itemgrade >= 0 && itemgrade <= 2) && (itemtype == 10)) {
				if (itemId == 900007|| itemId == 500012) {
					_owner.getResistance().addcalcPcDefense(-2);
				}
				switch (itemlvl) {
				case 1:
					_owner.addMaxMp(-5);
					break;
				case 2:
					_owner.addMaxMp(-10);
					break;
				case 3:
					_owner.addMaxMp(-20);
					break;
				case 4:
					_owner.addMaxMp(-30);
					break;
				case 5:
					_owner.addDamageReductionByArmor(-1);
					_owner.addMaxMp(-40);
					break;
				case 6:
					_owner.addDamageReductionByArmor(-2);
					_owner.getResistance().addcalcPcDefense(-1);
					_owner.addMaxMp(-40);
					_owner.addMaxHp(-20);
					break;
				case 7:
					_owner.addDamageReductionByArmor(-3);
					_owner.addMaxMp(-50);
					_owner.addMaxHp(-30);
					_owner.getResistance().addcalcPcDefense(-3);
					break;
				case 8:
					_owner.addDamageReductionByArmor(-4);
					_owner.addMaxMp(-50);
					_owner.addMaxHp(-40);
					_owner.getResistance().addcalcPcDefense(-5);
					break;
				case 9:
					_owner.addDamageReductionByArmor(-5);
					_owner.addMaxMp(-60);
					_owner.addMaxHp(-50);
					_owner.getResistance().addcalcPcDefense(-7);
					break;
				case 10:
					_owner.addDamageReductionByArmor(-6);
					_owner.addMaxMp(-70);
					_owner.addMaxHp(-100);
					_owner.getResistance().addcalcPcDefense(-9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
					break;
				}
				// 스냅퍼의 반지 착용해제 부분
			} else if (itemgrade == 3 && itemId == 22224 || itemId == 22225) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(-15);
					break;
				case 2:
					_owner.addMaxHp(-20);
					_owner.getAC().addAc(1);
					break;
				case 3:
					_owner.addMaxHp(-25);
					_owner.getAC().addAc(2);
					break;
				case 4:
					_owner.addMaxHp(-30);
					_owner.getAC().addAc(3);
					break;
				case 5:
					_owner.addMaxHp(-35);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-1);
					break;
				case 6:
					_owner.addMaxHp(-40);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-2);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
					break;
				case 7:
					_owner.addMaxHp(-45);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					break;
				case 8:
					_owner.addMaxHp(-50);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addPVPweaponTotalDamage(-2);
					break;
				default:
					break;
				}
			} else if (itemgrade == 3 && itemId == 22227) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(-15);
					break;
				case 2:
					_owner.addMaxHp(-20);
					_owner.getAC().addAc(1);
					break;
				case 3:
					_owner.addMaxHp(-25);
					_owner.getAC().addAc(2);
					break;
				case 4:
					_owner.addMaxHp(-30);
					_owner.getAC().addAc(3);
					break;
				case 5:
					_owner.addMaxHp(-35);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-1);
					break;
				case 6:
					_owner.addMaxHp(-40);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-2);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
					break;
				case 7:
					_owner.addMaxHp(-45);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					break;
				case 8:
					_owner.addMaxHp(-50);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addPVPweaponTotalDamage(-2);
					break;
				default:
					break;
				}
				// 스냅퍼의 마법저항 반지 착용해제
			} else if (itemgrade == 3 && itemId == 22228) {
				switch (itemlvl) {
				case 0:
					_owner.getResistance().addMr(-7);
					break;
				case 1:
					_owner.addMaxHp(-15);
					_owner.getResistance().addMr(-7);
					break;
				case 2:
					_owner.addMaxHp(-20);
					_owner.getAC().addAc(1);
					_owner.getResistance().addMr(-7);
					break;
				case 3:
					_owner.addMaxHp(-25);
					_owner.getAC().addAc(2);
					_owner.getResistance().addMr(-7);
					break;
				case 4:
					_owner.addMaxHp(-30);
					_owner.getAC().addAc(3);
					_owner.getResistance().addMr(-7);
					break;
				case 5:
					_owner.addMaxHp(-35);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-1);
					_owner.getResistance().addMr(-7);
					break;
				case 6:
					_owner.addMaxHp(-40);
					_owner.getAC().addAc(4);
					_owner.addDmgup(-2);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
					_owner.getResistance().addMr(-7);
					break;
				case 7:
					_owner.addMaxHp(-45);
					_owner.getAC().addAc(4);
					_owner.addDmgup(-3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					_owner.getResistance().addMr(-7);
					break;
				case 8:
					_owner.addMaxHp(-50);
					_owner.getAC().addAc(6);
					_owner.addDmgup(-5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -1);
					_owner.getResistance().addPVPweaponTotalDamage(-3);
					_owner.getResistance().addMr(-9);
					break;
				case 9:
					_owner.addMaxHp(-60);
					_owner.getAC().addAc(8);
					_owner.addDmgup(-7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
					_owner.getResistance().addPVPweaponTotalDamage(-7);
					_owner.getResistance().addMr(-12);
					break;
				default:
					break;
				}
			} else if (itemgrade == 3 && itemId == 22226) {
				
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(-15);
					
					break;
				case 2:
					_owner.addMaxHp(-20);
					_owner.getAC().addAc(1);
					break;
				case 3:
					_owner.addMaxHp(-25);
					_owner.getAC().addAc(2);
					break;
				case 4:
					_owner.addMaxHp(-30);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-1);
					break;
				case 5:
					_owner.addMaxHp(-35);
					_owner.getAC().addAc(-3);
					_owner.addDmgup(-2);
					break;
				case 6:
					_owner.addMaxHp(-40);
					_owner.getAC().addAc(4);
					_owner.addDmgup(-3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
					_owner.addDamageReductionByArmor(-1);
					break;
				case 7:
					_owner.addMaxHp(-45);
					_owner.getAC().addAc(4);
					_owner.addDmgup(-4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					_owner.addDamageReductionByArmor(-2);
					break;
				case 8:
					_owner.addMaxHp(-50);
					_owner.getAC().addAc(6);
					_owner.addDmgup(-5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY,-9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR,-1);
					_owner.getResistance().addPVPweaponTotalDamage(-3);
					_owner.addDamageReductionByArmor(-3);
					break;
				case 9:
					_owner.addMaxHp(-55);
					_owner.getAC().addAc(8);
					_owner.addDmgup(-7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY,-9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR,-2);
					_owner.getResistance().addPVPweaponTotalDamage(-7);
					_owner.addDamageReductionByArmor(-5);
					break;
				default:
					break;
				}
			} else if (itemgrade == 3 && itemId == 222290) { // 지혜의 반지
				switch (itemlvl) {
				case 0 :
					_owner.addMaxMp(-15);
					break;
				case 1:
					_owner.addMaxHp(-5);
					_owner.addMaxMp(-15);
					break;
				case 2:
					_owner.addMaxHp(-10);
					_owner.addMaxMp(-15);
					_owner.getAC().addAc(1);
					break;
				case 3:
					_owner.addMaxHp(-15);
					_owner.addMaxMp(-15);
					_owner.getAC().addAc(2);
					break;
				case 4:
					_owner.addMaxHp(-20);
					_owner.addMaxMp(-15);
					_owner.getAC().addAc(3);
					break;
				case 5:
					_owner.addMaxHp(-25);
					_owner.addMaxMp(-15);
					_owner.getAC().addAc(3);
					_owner.getAbility().addSp(-1);
					break;
				case 6:
					_owner.addMaxHp(-30);
					_owner.addMaxMp(-15);
					_owner.getAC().addAc(4);
					_owner.getAbility().addSp(-2);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
					break;
				case 7:
					_owner.addMaxHp(-35);
					_owner.addMaxMp(-15);
					_owner.getAC().addAc(4);
					_owner.getAbility().addSp(-3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					break;
				case 8:
					_owner.addMaxHp(-40);
					_owner.addMaxMp(-30);
					_owner.getAC().addAc(6);
					_owner.getAbility().addSp(-5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -1);
					_owner.getResistance().addPVPweaponTotalDamage(-3);
					break;
				case 9:
					_owner.addMaxHp(-50);
					_owner.addMaxMp(-70);
					_owner.getAC().addAc(8);
					_owner.getAbility().addSp(-7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
					_owner.getResistance().addPVPweaponTotalDamage(-7);
					break;
				default:
					break;
				}
			} else if (itemgrade == 3 && itemId == 222291) { // 용사의 반지
				switch (itemlvl) {
				case 1:
					_owner.getAC().addAc(1);
					break;
				case 2:
					_owner.getAC().addAc(2);
					break;
				case 3:
					_owner.addMaxHp(-5);
					_owner.getAC().addAc(3);
					break;
				case 4:
					_owner.addMaxHp(-10);
					_owner.getAC().addAc(4);
					break;
				case 5:
					_owner.addMaxHp(-15);
					_owner.getAC().addAc(4);
					_owner.addHitup(-1);
					_owner.addBowHitup(-1);
					_owner.addDmgup(-1);
					break;
				case 6:
					_owner.addMaxHp(-20);
					_owner.getAC().addAc(4);
					_owner.addHitup(-2);
					_owner.addBowHitup(-2);
					_owner.addDmgup(-2);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
					break;
				case 7:
					_owner.addMaxHp(-25);
					_owner.getAC().addAc(4);
					_owner.addHitup(-3);
					_owner.addBowHitup(-3);
					_owner.addDmgup(-3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					break;
				case 8:
					_owner.addMaxHp(-30);
					_owner.getAC().addAc(6);
					_owner.addHitup(-5);
					_owner.addBowHitup(-5);
					_owner.addDmgup(-5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -1);
					_owner.getResistance().addPVPweaponTotalDamage(-3);
					break;
				case 9:
					_owner.addMaxHp(-40);
					_owner.getAC().addAc(8);
					_owner.addHitup(-7);
					_owner.addBowHitup(-7);
					_owner.addDmgup(-7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
					_owner.getResistance().addPVPweaponTotalDamage(-7);
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 체력 반지 **/
			} else if (itemgrade == 3 && itemId == 222332) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(-15);
					break;
				case 2:
					_owner.addMaxHp(-20);
					_owner.getAC().addAc(1);
					break;
				case 3:
					_owner.addMaxHp(-30);
					_owner.getAC().addAc(2);
					break;
				case 4:
					_owner.addMaxHp(-35);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-1);
					break;
				case 5:
					_owner.addMaxHp(-40);
					_owner.getAC().addAc(4);
					_owner.addDmgup(-2);
					break;
				case 6:
					_owner.addMaxHp(-45);
					_owner.getAC().addAc(4);
					_owner.addDmgup(-3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -4);
					_owner.addDamageReductionByArmor(-1);
					break;
				case 7:
					_owner.addMaxHp(-55);
					_owner.getAC().addAc(5);
					_owner.addDmgup(-4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					_owner.addDamageReductionByArmor(-2);
					break;
				case 8:
					_owner.addMaxHp(-65);
					_owner.getAC().addAc(7);
					_owner.addDmgup(-7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
					_owner.getResistance().addPVPweaponTotalDamage(-5);
					_owner.addDamageReductionByArmor(-5);
					break;
				case 9:
					_owner.addMaxHp(-75);
					_owner.getAC().addAc(10);
					_owner.addDmgup(-9);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -4);
					_owner.getResistance().addPVPweaponTotalDamage(-9);
					_owner.addDamageReductionByArmor(-7);
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 회복 반지/축복받은 스냅퍼의 집중 반지 **/
			} else if (itemgrade == 3 && itemId == 222330 || itemId == 222331) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(-15);
					break;
				case 2:
					_owner.addMaxHp(-20);
					_owner.getAC().addAc(1);
					break;
				case 3:
					_owner.addMaxHp(-30);
					_owner.getAC().addAc(2);
					break;
				case 4:
					_owner.addMaxHp(-35);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-1);
					break;
				case 5:
					_owner.addMaxHp(-40);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-2);
					break;
				case 6:
					_owner.addMaxHp(-45);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
					break;
				case 7:
					_owner.addMaxHp(-50);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					break;
				case 8:
					_owner.addMaxHp(-50);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addPVPweaponTotalDamage(-2);
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 마나 반지 **/
			} else if (itemgrade == 3 && itemId == 222333) {
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(-15);
					break;
				case 2:
					_owner.addMaxHp(-20);
					_owner.getAC().addAc(1);
					break;
				case 3:
					_owner.addMaxHp(-30);
					_owner.getAC().addAc(2);
					break;
				case 4:
					_owner.addMaxHp(-35);
					_owner.getAC().addAc(3);
					_owner.addDmgup(1);
					break;
				case 5:
					_owner.addMaxHp(-40);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-2);
					break;
				case 6:
					_owner.addMaxHp(-45);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
					break;
				case 7:
					_owner.addMaxHp(-50);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					break;
				case 8:
					_owner.addMaxHp(-50);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-5);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addPVPweaponTotalDamage(-2);
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 마법 저항 반지 **/
			} else if (itemgrade == 3 && itemId == 222334) {
				switch (itemlvl) {
				case 0:
					_owner.getResistance().addMr(-7);
					break;
				case 1:
					_owner.addMaxHp(-15);
					_owner.getResistance().addMr(-7);
					break;
				case 2:
					_owner.addMaxHp(-20);
					_owner.getAC().addAc(1);
					_owner.getResistance().addMr(-7);
					break;
				case 3:
					_owner.addMaxHp(-30);
					_owner.getAC().addAc(2);
					_owner.getResistance().addMr(-7);
					break;
				case 4:
					_owner.addMaxHp(-35);
					_owner.getAC().addAc(3);
					_owner.addDmgup(-1);
					_owner.getResistance().addMr(-7);
					break;
				case 5:
					_owner.addMaxHp(-40);
					_owner.getAC().addAc(4);
					_owner.addDmgup(-2);
					_owner.getResistance().addMr(-7);
					break;
				case 6:
					_owner.addMaxHp(-45);
					_owner.getAC().addAc(4);
					_owner.addDmgup(-3);
					_owner.getResistance().addMr(-8);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
					break;
				case 7:
					_owner.addMaxHp(-50);
					_owner.getAC().addAc(5);
					_owner.addDmgup(-4);
					_owner.getResistance().addMr(-9);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					break;
				case 8:
					_owner.addMaxHp(-50);
					_owner.getAC().addAc(7);
					_owner.addDmgup(-7);
					_owner.getResistance().addMr(-12);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
					_owner.getResistance().addPVPweaponTotalDamage(-5);
					break;
				case 9:
					_owner.addMaxHp(-60);
					_owner.getAC().addAc(10);
					_owner.addDmgup(-9);
					_owner.getResistance().addMr(-15);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -4);
					_owner.getResistance().addPVPweaponTotalDamage(-9);
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 지혜 반지 **/
			} else if (itemgrade == 3 && itemId == 222335) {
				switch (itemlvl) {
				case 0:
					_owner.addMaxMp(-15);
					break;
				case 1:
					_owner.addMaxHp(-5);
					_owner.addMaxMp(-15);
					break;
				case 2:
					_owner.addMaxHp(-10);
					_owner.addMaxMp(-15);
					_owner.getAC().addAc(1);
					break;
				case 3:
					_owner.addMaxHp(-20);
					_owner.addMaxMp(-15);
					_owner.getAC().addAc(2);
					break;
				case 4:
					_owner.addMaxHp(-25);
					_owner.addMaxMp(-15);
					_owner.getAC().addAc(3);
					_owner.getAbility().addSp(-1);
					break;
				case 5:
					_owner.addMaxHp(-30);
					_owner.addMaxMp(-15);
					_owner.getAC().addAc(3);
					_owner.getAbility().addSp(-2);
					break;
				case 6:
					_owner.addMaxHp(-35);
					_owner.addMaxMp(-15);
					_owner.getAC().addAc(3);
					_owner.getAbility().addSp(-3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
					_owner.addBaseMagicHitUp(-1);// 마법 적중
					break;
				case 7:
					_owner.addMaxHp(-40);
					_owner.addMaxMp(-30);
					_owner.getAC().addAc(5);
					_owner.getAbility().addSp(-4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					_owner.addBaseMagicHitUp(-2);// 마법 적중
					break;
				case 8:
					_owner.addMaxHp(-50);
					_owner.addMaxMp(-35);
					_owner.getAC().addAc(7);
					_owner.getAbility().addSp(-7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
					_owner.getResistance().addPVPweaponTotalDamage(-5);
					_owner.addBaseMagicHitUp(-5);// 마법 적중
					break;
				case 9:
					_owner.addMaxHp(-60);
					_owner.addMaxMp(-80);
					_owner.getAC().addAc(10);
					_owner.getAbility().addSp(-9);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -4);
					_owner.getResistance().addPVPweaponTotalDamage(-9);
					_owner.addBaseMagicHitUp(-7);// 마법 적중
					break;
				default:
					break;
				}
				/** 축복받은 스냅퍼의 용사 반지 **/
			} else if (itemgrade == 3 && itemId == 222336) {
				switch (itemlvl) {
				case 1:
					_owner.getAC().addAc(1);
					break;
				case 2:
					_owner.getAC().addAc(2);
					break;
				case 3:
					_owner.addMaxHp(-10);
					_owner.getAC().addAc(3);
					break;
				case 4:
					_owner.addMaxHp(-15);
					_owner.getAC().addAc(4);
					_owner.addHitup(-1);
					_owner.addBowHitup(-1);
					_owner.addDmgup(-1);
					break;
				case 5:
					_owner.addMaxHp(-20);
					_owner.getAC().addAc(4);
					_owner.addHitup(-2);
					_owner.addBowHitup(-2);
					_owner.addDmgup(-2);
					break;
				case 6:
					_owner.addMaxHp(-25);
					_owner.getAC().addAc(4);
					_owner.addHitup(-3);
					_owner.addBowHitup(-3);
					_owner.addDmgup(-3);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -5);
					break;
				case 7:
					_owner.addMaxHp(-30);
					_owner.getAC().addAc(5);
					_owner.addHitup(-4);
					_owner.addBowHitup(-4);
					_owner.addDmgup(-4);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -7);
					_owner.getResistance().addPVPweaponTotalDamage(-1);
					break;
				case 8:
					_owner.addMaxHp(-30);
					_owner.getAC().addAc(7);
					_owner.addHitup(-7);
					_owner.addDmgup(-7);
					_owner.addBowHitup(-7);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -2);
					_owner.getResistance().addPVPweaponTotalDamage(-5);
					break;
				case 9:
					_owner.addMaxHp(-40);
					_owner.getAC().addAc(10);
					_owner.addHitup(-9);
					_owner.addDmgup(-9);
					_owner.addBowHitup(-9);
					_owner.getResistance().addSpecialResistance(eKind.ABILITY, -9);
					_owner.getResistance().addSpecialResistance(eKind.FEAR, -4);
					_owner.getResistance().addPVPweaponTotalDamage(-9);
					break;
				default:
					break;
				}
			} else if(itemId == 22229){
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(-20);
					break;
				case 2:
					_owner.addMaxHp(-30);
					break;
				case 3:
					_owner.addMaxHp(-40);
					_owner.addDamageReductionByArmor(-1);
					break;
				case 4:
					_owner.addMaxHp(-50);
					_owner.addDamageReductionByArmor(-1);
					break;
				case 5:
					_owner.addMaxHp(-60);
					_owner.addDamageReductionByArmor(-2);
					break;
				case 6:
					_owner.addMaxHp(-70);
					_owner.addDamageReductionByArmor(-3);
					_owner.getAC().addAc(7);
					break;
				case 7:
					_owner.addBowHitup(-1);
					_owner.addHitup(-1);
					_owner.addMaxHp(-80);
					_owner.addDamageReductionByArmor(-4);
					_owner.getAC().addAc(8);
					break;
				case 8:
					_owner.addBowHitup(-3);
					_owner.addHitup(-3);
					_owner.addMaxHp(-90);
					_owner.addDamageReductionByArmor(-7);
					_owner.getAC().addAc(9);
					break;
				case 9:
					_owner.addBowHitup(-5);
					_owner.addHitup(-5);
					_owner.addMaxHp(-100);
					_owner.addDamageReductionByArmor(-7);
					_owner.getAC().addAc(10);
					break;
				default:
					break;
				}
			} else if(itemId == 222337){
				switch (itemlvl) {
				case 1:
					_owner.addMaxHp(-10);
					break;
				case 2:
					_owner.addMaxHp(-20);
					break;
				case 3:
					_owner.addMaxHp(-40);
					_owner.addDamageReductionByArmor(-1);
					break;
				case 4:
					_owner.addMaxHp(-50);
					_owner.addDamageReductionByArmor(-2);
					break;
				case 5:
					_owner.addMaxHp(-60);
					_owner.addDamageReductionByArmor(-3);
					_owner.getAC().addAc(7);
					break;
				case 6:
					_owner.addBowHitup(-1);
					_owner.addHitup(-1);
					_owner.addMaxHp(-70);
					_owner.addDamageReductionByArmor(-4);
					_owner.getAC().addAc(8);
					break;
				case 7:
					_owner.addBowHitup(-3);
					_owner.addHitup(-3);
					_owner.addMaxHp(-80);
					_owner.addDamageReductionByArmor(-5);
					_owner.getAC().addAc(9);
					break;
				case 8:
					_owner.addBowHitup(-5);
					_owner.addHitup(-5);
					_owner.addMaxHp(-140);
					_owner.addDamageReductionByArmor(-7);
					_owner.getAC().addAc(10);
					break;
				case 9:
					_owner.addBowHitup(-7);
					_owner.addHitup(-7);
					_owner.addMaxHp(-150);
					_owner.addDamageReductionByArmor(-9);
					_owner.getAC().addAc(11);
					break;
				default:
					break;
				}
			} else if(itemId == 22231){
				switch (itemlvl) {
				case 0:
					_owner.getResistance().addMr(-2);
					break;
				case 1:
					_owner.addMaxMp(-10);
					_owner.getResistance().addMr(-5);
					break;
				case 2:
					_owner.addMaxMp(-15);
					_owner.getResistance().addMr(-6);
					break;
				case 3:
					_owner.addMaxMp(-30);
					_owner.getResistance().addMr(-7);
					_owner.getAbility().addSp(-1);
					break;
				case 4:
					_owner.addMaxMp(-35);
					_owner.getResistance().addMr(-8);
					_owner.getAbility().addSp(-1);
					break;
				case 5:
					_owner.addMaxMp(-50);
					_owner.getResistance().addMr(-9);
					_owner.getAbility().addSp(-2);
					break;
				case 6:
					_owner.addMaxMp(-55);
					_owner.getResistance().addMr(-10);
					_owner.getAbility().addSp(-2);
					_owner.getAC().addAc(1);
					break;
				case 7:
					_owner.addBaseMagicHitUp(-1);// 마법 적중
					_owner.addMaxMp(-70);
					_owner.getResistance().addMr(-120);
					_owner.getAbility().addSp(-3);
					_owner.getAC().addAc(2);
					break;
				case 8:
					_owner.addBaseMagicHitUp(-5);// 마법 적중
					_owner.addMaxMp(-95);
					_owner.addMaxHp(-50);
					_owner.getResistance().addMr(-17);
					_owner.getAbility().addSp(-5);
					_owner.getAC().addAc(3);
					break;
				case 9:
					_owner.addBaseMagicHitUp(-9);// 마법 적중
					_owner.addMaxMp(-125);
					_owner.addMaxHp(-100);
					_owner.getResistance().addMr(-20);
					_owner.getAbility().addSp(-7);
					_owner.getAC().addAc(3);
					break;
				default:
					break;
				}
			} else if(itemId == 222339){
				switch (itemlvl) {
				case 0:
					_owner.addMaxMp(-10);
				
					break;
				case 1:
					_owner.addMaxMp(-10);
					
					break;
				case 2:
					_owner.addMaxMp(-15);
				
					break;
				case 3:
					_owner.addMaxMp(-35);
					_owner.getResistance().addMr(-8);
					_owner.getAbility().addSp(-1);
					break;
				case 4:
					_owner.addMaxMp(-50);
					_owner.getResistance().addMr(-9);
					_owner.getAbility().addSp(-2);
					break;
				case 5:
					_owner.addMaxMp(-55);
					_owner.getResistance().addMr(-10);
					_owner.getAbility().addSp(-2);
					_owner.getAC().addAc(1);
					break;
				case 6:
					_owner.addBaseMagicHitUp(-1);// 마법 적중
					_owner.addMaxMp(-70);
					_owner.getResistance().addMr(-12);
					_owner.getAbility().addSp(-3);
					_owner.getAC().addAc(2);
					break;
				case 7:
					_owner.addBaseMagicHitUp(-3);// 마법 적중
					_owner.addMaxMp(-95);
					_owner.getResistance().addMr(-15);
					_owner.getAbility().addSp(-3);
					_owner.getAC().addAc(3);
					break;
				case 8:
					_owner.addBaseMagicHitUp(-7);// 마법 적중
					_owner.addMaxMp(-125);
					_owner.addMaxHp(-100);
					_owner.getResistance().addMr(-20);
					_owner.getAbility().addSp(-5);
					_owner.getAC().addAc(4);
					break;
				case 9:
					_owner.addBaseMagicHitUp(-12);// 마법 적중
					_owner.addMaxMp(-155);
					_owner.addMaxHp(-150);
					_owner.getResistance().addMr(-23);
					_owner.getAbility().addSp(-9);
					_owner.getAC().addAc(4);
					break;
				default:
					break;
				}
				/** 검귀 ac처리부분 효과 재수정 **/
			} else if (itemId == 222340 || itemId == 222341) {
				int ac = itemlvl;

				if (item.getBless() == 0 && itemlvl >= 3) {
					ac += 1;
				}
				_owner.getAC().addAc(ac);		
				if (itemlvl >= 4 && itemlvl <= 7) {
					if (item.getBless() != 0 ){ // 걍
						_owner.addDmgup(-(itemlvl - 3));
						_owner.addBowDmgup(-(itemlvl - 3));
					} else {
						_owner.addDmgup(-(itemlvl - 2));
						_owner.addBowDmgup(-(itemlvl - 2));
					}
				} else if (itemlvl == 8) {
					if (item.getBless() != 0 ){ // 걍
						_owner.addDmgup(-7);
						_owner.addBowDmgup(-7);
					} else {
						_owner.addDmgup(-9);
						_owner.addBowDmgup(-9);
					}
				} else if (itemlvl >= 9) {
					if (item.getBless() != 0 ){ // 걍
						_owner.addDmgup(-9);
						_owner.addBowDmgup(-9);
					} else {
						_owner.addDmgup(-12);
						_owner.addBowDmgup(-12);
					}
				}
				
			}
		}
		armor.stopEquipmentTimer(_owner);
		_armors.remove(armor);
	}

	public void set(L1ItemInstance equipment) {
		L1Item item = equipment.getItem();

		if (item.getType2() == 0) {
			return;
		}
		
		_owner.addEinhasadBlessper(item.get_addeinhasadper());
		_owner.addMaxHp(item.get_addhp());
		_owner.addMaxMp(item.get_addmp());
		_owner.getAbility().addAddedStr(item.get_addstr());
		_owner.getAbility().addAddedCon(item.get_addcon());
		_owner.getAbility().addAddedDex(item.get_adddex());
		_owner.getAbility().addAddedInt(item.get_addint());
		_owner.getAbility().addAddedWis(item.get_addwis());
		_owner.add_missile_critical_rate(item.get_missile_critical_probability());
		_owner.add_melee_critical_rate(item.get_melee_critical_probability());
		
		if (item.get_addwis() != 0) {
			_owner.resetBaseMr();
		}
		_owner.getAbility().addAddedCha(item.get_addcha());

		int addMr = 0;
		addMr += equipment.getMr();
		if (item.getItemId() == 20236 && _owner.isElf()) {
			addMr += 5;
		}
		if (addMr != 0) {
			_owner.getResistance().addMr(addMr);
			SC_SPMR.send(_owner);
		}
		if (item.get_addsp() != 0) {
			_owner.getAbility().addSp(item.get_addsp());
			SC_SPMR.send(_owner);
		}
		if (item.isHasteItem()) {
			_owner.addHasteItemEquipped(1);
			_owner.removeHasteSkillEffect();
			if (_owner.getMoveSpeed() != 1) {
				_owner.setMoveSpeed(1);
				SC_SkillHaste.send(_owner, _owner.getId(), 1, -1);
				SC_SkillHaste.broadcast(_owner, _owner.getId(), 1, 0);
			}
		}
		if (item.getItemId() == 20383) {
			if (_owner.hasSkillEffect(L1SkillId.STATUS_BRAVE)) {
				BraveSpeedEffects.cancelTimer(_owner, L1SkillId.STATUS_BRAVE);
			}
		}
		_owner.getEquipSlot().setMagicHelm(equipment);

		if (item.getType2() == 1) {
			setWeapon(equipment);
		} else if (item.getType2() == 2) {
			setArmor(equipment);
			SC_SPMR.send(_owner);
		
		}
		
		ItemEffectRegistry.Def regDef = ItemEffectRegistry.get(item.getItemId());
		if (regDef != null) {
		    regDef.apply(_owner, _owner.getType(), equipment.getEnchantLevel(), 1);
		}
		
		ItemBonusHooks.onEquipChanged(_owner, equipment, true);
		ItemPresentatorFactory.do_equip(_owner, equipment);
		//_owner.sendPackets(String.format("%s을(를) 착용 하였습니다.", equipment.getLogName()));
	}

	public void remove(L1ItemInstance equipment) {
		L1Item item = equipment.getItem();
		if (item.getType2() == 0) {
			return;
		}

		if(item.getType() == 13 || item.getType() == 7){
			if(_owner.hasSkillEffect(L1SkillId.BLOW_ATTACK)){
				_owner.removeSkillEffect(L1SkillId.BLOW_ATTACK);
			}
		}
	
		_owner.addEinhasadBlessper(-item.get_addeinhasadper());
		_owner.addMaxHp(-item.get_addhp());
		_owner.addMaxMp(-item.get_addmp());
		_owner.getAbility().addAddedStr((byte) -item.get_addstr());
		_owner.getAbility().addAddedCon((byte) -item.get_addcon());
		_owner.getAbility().addAddedDex((byte) -item.get_adddex());
		_owner.getAbility().addAddedInt((byte) -item.get_addint());
		_owner.getAbility().addAddedWis((byte) -item.get_addwis());
		_owner.add_missile_critical_rate(-item.get_missile_critical_probability());
		_owner.add_melee_critical_rate(-item.get_melee_critical_probability());
		if (item.get_addwis() != 0) {
			_owner.resetBaseMr();
		}
		_owner.getAbility().addAddedCha((byte) -item.get_addcha());

		int addMr = 0;
		addMr -= equipment.getMr();
		if (item.getItemId() == 20236 && _owner.isElf()) {
			addMr -= 5;
		}
		if (addMr != 0) {
			_owner.getResistance().addMr(addMr);
			SC_SPMR.send(_owner);
		}
		if (item.get_addsp() != 0) {
			_owner.getAbility().addSp(-item.get_addsp());
			SC_SPMR.send(_owner);
		}
		if (item.isHasteItem()) {
			_owner.addHasteItemEquipped(-1);
			if (_owner.getHasteItemEquipped() == 0 && _owner.getMoveSpeed() == 1
					&& MovementSpeedEffects.activeHaste(_owner, 0) == 0) {
				_owner.setMoveSpeed(0);
				SC_SkillHaste.send(_owner, _owner.getId(), 0, 0);
				SC_SkillHaste.broadcast(_owner, _owner.getId(), 0, 0);
			}
		}
		_owner.getEquipSlot().removeMagicHelm(_owner.getId(), equipment);

		if (item.getType2() == 1) {
			removeWeapon(equipment);
			_owner.remove_elf_second_brave();
		} else if (item.getType2() == 2) {
			removeArmor(equipment);
		}
		ItemEffectRegistry.Def regDef = ItemEffectRegistry.get(item.getItemId());
		if (regDef != null) {
		    regDef.apply(_owner, _owner.getType(), equipment.getEnchantLevel(), -1);
		}
		ItemBonusHooks.onEquipChanged(_owner, equipment, false);
		ItemPresentatorFactory.do_unequip(_owner, equipment);
		//_owner.sendPackets(String.format("%s을(를) 해제 하였습니다.", equipment.getLogName()));
	}

	public void setMagicHelm(L1ItemInstance item) {
		if (item.getItemId() == 20013) {
			SC_AddSkill.send(_owner, 0, 0, 0, 2, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
					0, 0, 0, 0, 0, 0);
		}
		if (item.getItemId() == 20014) {
			SC_AddSkill.send(_owner, 1, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
					0, 0, 0, 0, 0, 0);
		}
		if (item.getItemId() == 20015) {
			SC_AddSkill.send(_owner, 0, 24, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
					0, 0, 0, 0, 0, 0, 0);
		}
		if (item.getItemId() == 20008) {
			SC_AddSkill.send(_owner, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
					0, 0, 0, 0, 0, 0);
		}
		if (item.getItemId() == 20023) {
			SC_AddSkill.send(_owner, 0, 0, 0, 0, 0, 0, 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
					0, 0, 0, 0, 0, 0, 0);
		}
	}

	public void removeMagicHelm(int objectId, L1ItemInstance item) {
		if (item.getItemId() == 20013) {
			if (!SkillsTable.getInstance().spellCheck(objectId, 26)) {
				SC_DelSkill.send(_owner, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0);
			}
			if (!SkillsTable.getInstance().spellCheck(objectId, 43)) {
				SC_DelSkill.send(_owner, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0);
			}
		}
		if (item.getItemId() == 20014) {
			if (!SkillsTable.getInstance().spellCheck(objectId, 1)) {
				SC_DelSkill.send(_owner, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0);
			}
			if (!SkillsTable.getInstance().spellCheck(objectId, 19)) {
				SC_DelSkill.send(_owner, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0);
			}
		}
		if (item.getItemId() == 20015) {
			if (!SkillsTable.getInstance().spellCheck(objectId, 12)) {
				SC_DelSkill.send(_owner, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0);
			}
			if (!SkillsTable.getInstance().spellCheck(objectId, 13)) {
				SC_DelSkill.send(_owner, 0, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0);
			}
			if (!SkillsTable.getInstance().spellCheck(objectId, 42)) {
				SC_DelSkill.send(_owner, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0);
			}
		}
		if (item.getItemId() == 20008) {
			if (!SkillsTable.getInstance().spellCheck(objectId, 43)) {
				SC_DelSkill.send(_owner, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0);
			}
		}
		if (item.getItemId() == 20023) {
			if (!SkillsTable.getInstance().spellCheck(objectId, 54)) {
				SC_DelSkill.send(_owner, 0, 0, 0, 0, 0, 0, 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0);
			}
		}
	}

	/**
	 * 셋트 아이템 해제
	 * 
	 * @param itemId
	 */
	public void removeSetItems(int itemId) {
		for (L1ArmorSet armorSet : L1ArmorSet.getAllSet()) {
			if (armorSet.isPartOfSet(itemId) && _currentArmorSet.contains(armorSet) && !armorSet.isValid(_owner)) {
				armorSet.cancelEffect(_owner);
				_currentArmorSet.remove(armorSet);
			}
		}
	}
}
