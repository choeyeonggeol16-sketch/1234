package l1j.server.server.model;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import l1j.server.server.model.network.core.sc.packet.SC_CloseList;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import java.util.HashMap;
import java.util.Map;

import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeShape;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
//Referenced classes of package l1j.server.server.model:
//L1PcInstance

public class L1PolyMorph {
	// weapon equip bit
	private static final int DAGGER_EQUIP = 1;

	private static final int SWORD_EQUIP = 2;

	private static final int TWOHANDSWORD_EQUIP = 4;

	private static final int AXE_EQUIP = 8;

	private static final int SPEAR_EQUIP = 16;

	private static final int STAFF_EQUIP = 32;

	private static final int EDORYU_EQUIP = 64;

	private static final int CLAW_EQUIP = 128;

	private static final int BOW_EQUIP = 256;

	private static final int KIRINGKU_EQUIP = 512;

	private static final int CHAINSWORD_EQUIP = 1024;

	// armor equip bit
	private static final int HELM_EQUIP = 1;

	private static final int AMULET_EQUIP = 2;

	private static final int EARRING_EQUIP = 4;

	private static final int TSHIRT_EQUIP = 8;

	private static final int ARMOR_EQUIP = 16;

	private static final int CLOAK_EQUIP = 32;

	private static final int BELT_EQUIP = 64;

	private static final int SHIELD_EQUIP = 128;

	private static final int GARDER_EQUIP = 128;

	private static final int GLOVE_EQUIP = 256;

	private static final int RING_EQUIP = 512;

	private static final int BOOTS_EQUIP = 1024;

	// 변신의 원인을 나타내는 bit
	public static final int MORPH_BY_ITEMMAGIC = 1;

	public static final int MORPH_BY_GM = 2;

	public static final int MORPH_BY_NPC = 4; // 점성술사 케프리샤 이외의 NPC

	public static final int MORPH_BY_KEPLISHA = 8;

	public static final int MORPH_BY_LOGIN = 0;

	private static final Map<Integer, Integer> weaponFlgMap = new HashMap<Integer, Integer>();

	static {
		weaponFlgMap.put(1, SWORD_EQUIP);
		weaponFlgMap.put(2, DAGGER_EQUIP);
		weaponFlgMap.put(3, TWOHANDSWORD_EQUIP);
		weaponFlgMap.put(4, BOW_EQUIP);
		weaponFlgMap.put(5, SPEAR_EQUIP);
		weaponFlgMap.put(6, AXE_EQUIP);
		weaponFlgMap.put(7, STAFF_EQUIP);
		weaponFlgMap.put(8, BOW_EQUIP);
		weaponFlgMap.put(9, BOW_EQUIP);
		weaponFlgMap.put(10, BOW_EQUIP);
		weaponFlgMap.put(11, CLAW_EQUIP);
		weaponFlgMap.put(12, EDORYU_EQUIP);
		weaponFlgMap.put(13, BOW_EQUIP);
		weaponFlgMap.put(14, SPEAR_EQUIP);
		weaponFlgMap.put(15, AXE_EQUIP);
		weaponFlgMap.put(16, STAFF_EQUIP);
		weaponFlgMap.put(17, KIRINGKU_EQUIP);
		weaponFlgMap.put(18, CHAINSWORD_EQUIP);
	}

	private static final Map<Integer, Integer> armorFlgMap = new HashMap<Integer, Integer>();

	static {
		armorFlgMap.put(1, HELM_EQUIP);
		armorFlgMap.put(2, ARMOR_EQUIP);
		armorFlgMap.put(3, TSHIRT_EQUIP);
		armorFlgMap.put(4, CLOAK_EQUIP);
		armorFlgMap.put(5, GLOVE_EQUIP);
		armorFlgMap.put(6, BOOTS_EQUIP);
		armorFlgMap.put(7, SHIELD_EQUIP);
		armorFlgMap.put(7, GARDER_EQUIP);
		armorFlgMap.put(8, AMULET_EQUIP);
		armorFlgMap.put(9, RING_EQUIP);
		armorFlgMap.put(10, BELT_EQUIP);
		armorFlgMap.put(12, EARRING_EQUIP);
	}

	private int _id;
	private String _name;
	private int _polyId;
	private int _minLevel;
	private int _weaponEquipFlg;
	private int _armorEquipFlg;
	private boolean _canUseSkill;
	private int _causeFlg;
	// 변신 버그 방지
	private int _option;

	// 변신 버그 방지
	public L1PolyMorph(int id, String name, int polyId, int minLevel, int weaponEquipFlg, int armorEquipFlg,
			boolean canUseSkill, int causeFlg, int option) {
		_id = id;
		_name = name;
		_polyId = polyId;
		_minLevel = minLevel;
		_weaponEquipFlg = weaponEquipFlg;
		_armorEquipFlg = armorEquipFlg;
		_canUseSkill = canUseSkill;
		_causeFlg = causeFlg;
		_option = option;
	}

	public int getId() {
		return _id;
	}

	public String getName() {
		return _name;
	}

	public int getPolyId() {
		return _polyId;
	}

	public int getMinLevel() {
		return _minLevel;
	}

	public int getWeaponEquipFlg() {
		return _weaponEquipFlg;
	}

	public int getArmorEquipFlg() {
		return _armorEquipFlg;
	}

	public boolean canUseSkill() {
		return _canUseSkill;
	}

	public int getCauseFlg() {
		return _causeFlg;
	}
	
	// 변신 버그 방지
	public int getOption() {
		return _option;
	}

	public static void handleCommands(L1PcInstance pc, String s) {
		
		    if ((pc == null) || (pc.isDead())) {
		      return;
		    }
		    L1PolyMorph poly = PolyTable.getInstance().getTemplate(s);
		    if ((poly != null) || (s.equals("none"))) {
		      if (s.equals("none"))
		      {
		        pc.removeSkillEffect(67);
		        SC_CloseList.send(pc, pc.getId());
		      }
		      else if ((pc.getLevel() >= poly.getMinLevel()) || (pc.isGm()))
		      {
		        doPoly(pc, poly.getPolyId(), 7200, 1, false);
		        SC_CloseList.send(pc, pc.getId());
		      }
		      else
		      {
		        SC_ServerMessage.send(pc, 181);
		      }
		    }
		  }
		
		
	public static void doPoly(L1Character cha, int polyId, int timeSecs, int cause, boolean ring) {
		try {
			if (cha == null || cha.isDead()) {
				return;
			}
			if (cha instanceof L1PcInstance) {
				timeSecs = -1; //변신 시간
				L1PcInstance pc = (L1PcInstance) cha;
				if (pc.getMapId() == 5302 || pc.getMapId() == 5490 || pc.getMapId() == 5153) { // 낚시터,배틀존
					SC_ServerMessage.send(pc, 1170); // 이곳에서 변신할수 없습니다.
					return;
				}

				if (pc.getCurrentSpriteId() == 6034 || pc.getCurrentSpriteId() == 6035) {
					SC_ServerMessage.send(pc, 181);
					return;
				}
				if (!isMatchCause(polyId, cause)) {
					SC_ServerMessage.send(pc, 181); // \f1 그러한 monster에게는 변신할 수 없습니다.
					return;
				}

				pc.killSkillEffectTimer(L1SkillId.SHAPE_CHANGE);
				pc.setSkillEffect(L1SkillId.SHAPE_CHANGE, timeSecs * 1000);
				// 변신 버그 방지
				L1PolyMorph poly = PolyTable.getInstance().getTemplate(polyId);
				if(!pc.isGm() && pc.noPlayerCK && pc.noPlayerck2 && pc.noPlayerRobot && poly == null){
					System.out.println(String.format("폴리 테이블에서 변신 정보를 찾을 수 없습니다. 변신번호 : %d, 사용자 이름 : %s", polyId, pc.getName()));
					pc.sendPackets("비정상적인 방법으로 변신을 시도 하였습니다.");
					return;
				}
				
				if (!pc.isGm() && pc.noPlayerCK && pc.noPlayerck2 && pc.noPlayerRobot && poly.getOption() != 0) {
					if (poly.getOption() == 0)
						return;
					polyId = poly.PolyOption(cha, polyId);
				}
				
				if (polyId == 0) {
					pc.sendPackets("비정상적인 방법으로 변신을 시도 하였습니다.");
					return;
				}
			
				if (pc.getCurrentSpriteId() != polyId) {
					L1ItemInstance weapon = pc.getWeapon();
					boolean weaponTakeoff = (weapon != null && !isEquipableWeapon(polyId, weapon.getItem().getType()));
					pc.setCurrentSprite(polyId);
					SC_ChangeShape.send(pc, pc.getId(), polyId, weaponTakeoff ? 0 : 29);
					if (!pc.isGmInvis() && !pc.isInvisble() && !pc.isGhost()) {
						SC_ChangeShape.broadcast(pc, pc.getId(), polyId, 29);
					}
					pc.getInventory().takeoffEquip(polyId);
					weapon = pc.getWeapon();
					if (weapon != null) {
						final long charVisual = SC_CharVisualUpdate.pack(pc);
						SC_CharVisualUpdate.sendPacked(pc, charVisual);
						SC_CharVisualUpdate.broadcastPacked(pc, charVisual);
					}
					
					SC_PacketBox.send(pc, SC_PacketBox.공격가능거리, pc, weapon);
				}
				
				  if (pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
			        pc.killSkillEffectTimer(L1SkillId.POLY_RING_MASTER);
			        pc.addMaxHp(-200);
			        pc.getAbility().addAddedStr((byte)-1);
			        pc.getAbility().addAddedDex((byte)-1);
			        pc.getAbility().addAddedCon((byte)-1);
			        pc.getAbility().addAddedInt((byte)-1);
			        pc.getAbility().addAddedWis((byte)-1);
			        pc.getAbility().addAddedCha((byte)-1);
			        SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			        SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			        SC_OwnCharAttrDef.send(pc);
			        SC_OwnCharStatus2.send(pc);
			        SC_SPMR.send(pc);
			      }
			      if (ring) {
			        pc.setSkillEffect(L1SkillId.POLY_RING_MASTER, timeSecs * 1000);
			        pc.addMaxHp(200);
			    	pc.getAbility().addAddedStr((byte) 1);
					pc.getAbility().addAddedDex((byte) 1);
					pc.getAbility().addAddedCon((byte) 1);
					pc.getAbility().addAddedInt((byte) 1);
					pc.getAbility().addAddedWis((byte) 1);
					pc.getAbility().addAddedCha((byte) 1);
			        SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			        SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			        SC_OwnCharStatus2.send(pc);
			        SC_OwnCharAttrDef.send(pc);
			        SC_SPMR.send(pc);
			       
			      }
			      else {
			        pc.setSkillEffect(L1SkillId.SHAPE_CHANGE, timeSecs * 1000);
			      }
			      
				if(ring){
					SC_SkillIconGFX.send(pc, 35, timeSecs, true);
					
				} else if (cause != MORPH_BY_LOGIN){
					SC_SkillIconGFX.send(pc, 35, timeSecs, false);
				}
			
		} else if (cha instanceof L1MonsterInstance) {
			L1MonsterInstance mob = (L1MonsterInstance) cha;
            if (!mob.tryReplaceSkillEffect(L1SkillId.SHAPE_CHANGE, timeSecs * 1000)) {
                throw new java.util.concurrent.RejectedExecutionException("NPC polymorph admission exhausted");
            }
			if (mob.getCurrentSpriteId() != polyId) {
				mob.setCurrentSprite(polyId);
				SC_ChangeShape.broadcast(mob, mob.getId(), polyId, 29);
			}
		}
	} catch (Exception e) {
		e.printStackTrace();
	}
}

	/** 3.80 변신리스트 **/
	public static void doPolyPraivateShop(L1Character cha, int polyIndex) {
		if ((cha == null) || cha.isDead()) {
			return;
		}
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;

			int PolyList[] = { 11479, 11427, 10047, 9688, 11322, 10069, 10034, 10032 };
			if (pc.getCurrentSpriteId() != PolyList[polyIndex - 1]) {
				pc.setCurrentSprite(PolyList[polyIndex - 1]);
				L1ItemInstance weapon = pc.getWeapon();
				boolean weaponTakeoff = (weapon != null
						&& !isEquipableWeapon(PolyList[polyIndex - 1], weapon.getItem().getType()));
				if (weaponTakeoff) {
					pc.getInventory().setEquipped(weapon, false);
				}
				SC_ChangeShape.send(pc, pc.getId(), PolyList[polyIndex - 1], ActionCodes.ACTION_Shop);
				if (!pc.isGmInvis() && !pc.isInvisble()) {
					SC_ChangeShape.broadcast(pc, pc.getId(), PolyList[polyIndex - 1], ActionCodes.ACTION_Shop);
				}
			}
			SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc, ActionCodes.ACTION_Shop));
			SC_CharVisualUpdate.broadcastPacked(pc, SC_CharVisualUpdate.pack(pc, ActionCodes.ACTION_Shop));
		}
	}

	public static void undoPolyPrivateShop(L1Character cha) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			int classId = pc.getClassId();
			pc.setCurrentSprite(classId);
			if (!pc.isDead()) {
				SC_ChangeShape.send(pc, pc.getId(), classId, pc.getCurrentWeapon());
				SC_ChangeShape.broadcast(pc, pc.getId(), classId, pc.getCurrentWeapon());
				SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc, pc.getCurrentWeapon()));
				SC_CharVisualUpdate.broadcastPacked(pc, SC_CharVisualUpdate.pack(pc, pc.getCurrentWeapon()));
			}
		}
	}
	public static void undoPolyAutoClanjoin(L1Character cha){
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			int classId = pc.getClassId();
			pc.setCurrentSprite(classId);
			if (!pc.isDead()) {
				SC_ChangeShape.send(pc, pc.getId(), classId, pc.getCurrentWeapon());
				SC_ChangeShape.broadcast(pc, pc.getId(), classId, pc.getCurrentWeapon());
				SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc, pc.getCurrentWeapon()));
				SC_CharVisualUpdate.broadcastPacked(pc, SC_CharVisualUpdate.pack(pc, pc.getCurrentWeapon()));
			}
		}
	}
	public static void undoPoly(L1Character cha) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			
			if (pc.getMapId() == 5143) { // 펫레이싱
				doPoly(pc, 5065, 1000, MORPH_BY_NPC, false);
			//	L1NeigeBugCheck.shapechange(pc);
				return;
			}
			int classId = pc.getClassId();
			pc.setCurrentSprite(classId);
			pc.sendShape(classId);
			L1ItemInstance weapon = pc.getWeapon();
			// 용기사 포우
			if (pc.isDragonknight()) {
				for (L1ItemInstance items : pc.getInventory().getItems()) {
					if (items.getItem().getType() == 18) {
						if (items.getItem().getType1() == 50) {
							items.getItem().setType1(83);
							if (weapon != null) {
								pc.getInventory().setEquipped(weapon, false);
								pc.getInventory().setEquipped(weapon, true);
							}
						}
					}
				}
			}
			if (weapon != null) {
				final long charVisual = SC_CharVisualUpdate.pack(pc);
				SC_CharVisualUpdate.sendPacked(pc, charVisual);
				SC_CharVisualUpdate.broadcastPacked(pc, charVisual);
			}
			 if (pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER))
		      {
		        pc.killSkillEffectTimer(L1SkillId.POLY_RING_MASTER);
		        pc.addMaxHp(-200);
		        pc.getAbility().addAddedStr((byte)-1);
		        pc.getAbility().addAddedDex((byte)-1);
		        pc.getAbility().addAddedCon((byte)-1);
		        pc.getAbility().addAddedInt((byte)-1);
		        pc.getAbility().addAddedWis((byte)-1);
		        pc.getAbility().addAddedCha((byte)-1);
		        SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		        SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		        SC_OwnCharAttrDef.send(pc);
		        SC_OwnCharStatus2.send(pc);
		        SC_SPMR.send(pc);
		      }
			SC_PacketBox.send(pc, SC_PacketBox.공격가능거리, pc, weapon);
//			if(!pc.is무인상점()){
//			L1NeigeBugCheck.shapechange(pc);
//			}
		} else if (cha instanceof L1MonsterInstance) {
			L1MonsterInstance mob = (L1MonsterInstance) cha;
			mob.setCurrentSprite(mob.getNpcTemplate().get_gfxid());
			SC_ChangeShape.broadcast(mob, mob.getId(), mob.getCurrentSpriteId(), 29);
		}
	}

	public static void MagicBookPoly(L1PcInstance pc, String s, int time) {
		if (pc == null || pc.isDead()) {
			return;
		}
		L1PolyMorph poly = PolyTable.getInstance().getTemplate(s);
		if (poly != null) {
			doPoly(pc, poly.getPolyId(), time, MORPH_BY_ITEMMAGIC, false);
			SC_CloseList.send(pc, pc.getId());
		}
		if (pc.getMagicItemId() != 0) {
			pc.getInventory().consumeItem(pc.getMagicItemId(), 1);
			pc.setMagicItemId(0);
		}
	}

	public static boolean isEquipableWeapon(int polyId, int weaponType) {
		L1PolyMorph poly = PolyTable.getInstance().getTemplate(polyId);

		if (poly == null) {
			return true;
		}

		Integer flg = weaponFlgMap.get(weaponType);
		if (flg != null) {
			return 0 != (poly.getWeaponEquipFlg() & flg);
		}
		return true;
	}

    /** Existing polymorph weapon mask identifies bow-only forms without an ID list. */
    public static boolean canAttackWithoutWeapon(int polyId) {
        final L1PolyMorph poly = PolyTable.getInstance().getTemplate(polyId);
        if (poly == null) return true;
        final int mask = poly.getWeaponEquipFlg();
        return (mask & BOW_EQUIP) == 0 || (mask & ~BOW_EQUIP) != 0;
    }

	public static boolean isEquipableArmor(int polyId, int armorType) {
		L1PolyMorph poly = PolyTable.getInstance().getTemplate(polyId);
		if (poly == null) {
			return true;
		}

		Integer flg = armorFlgMap.get(armorType);
		if (flg != null) {
			return 0 != (poly.getArmorEquipFlg() & flg);
		}
		return true;
	}

	// 지정한 polyId가 무엇에 의해 변신해, 그것이 변신 당할까?
	public static boolean isMatchCause(int polyId, int cause) {
		L1PolyMorph poly = PolyTable.getInstance().getTemplate(polyId);

		if (poly == null) {
			return true;
		}
		if (cause == MORPH_BY_LOGIN) {
			return true;
		}
		return 0 != (poly.getCauseFlg() & cause);
	}
	
	// 변신 버그 방지
	private int PolyOption(L1Character cha, int polyId) {
		try {
			L1PolyMorph poly = PolyTable.getInstance().getTemplate(polyId);
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				if (poly.getOption() == 1) { // 일반 변신 주문서
					if (poly.getOption() != 1) {
						System.out.println(pc.getName() + " 님이 변신 버그를 시도 합니다.");
						polyId = 0;
					}
				} else if (poly.getOption() == 2) { // 변신 지배반지
					if (!pc.getInventory().checkItem(4100023) && !pc.getInventory().checkItem(4100024)
							&& !pc.getInventory().checkItem(4100025) && !pc.getInventory().checkItem(4100026)
							&& !pc.getInventory().checkItem(4100027) && !pc.getInventory().checkItem(4100028)
							&& !pc.getInventory().checkItem(4100029) && !pc.getInventory().checkItem(4100030)
							&& !pc.getInventory().checkItem(4100031) && !pc.getInventory().checkItem(4100032)) {
						if (poly.getOption() != 2 || !pc.isPolyRingMaster()) {
							System.out.println(pc.getName() + " 님이 변신 버그를 시도 합니다.");
							polyId = 0;
						}
					}
				} else if (poly.getOption() == 3) { // 이벤트 변신 반지
					if (!pc.getInventory().checkItem(4100133)) {
						System.out.println(pc.getName() + " 님이 변신 버그를 시도 합니다.");
						polyId = 0;
					}
				} else if (poly.getOption() == 4) { // 클래스 변신
					if (poly.getOption() != 4) {
						System.out.println(pc.getName() + " 님이 변신 버그를 시도 합니다.");
						polyId = 0;
					}
					if (pc.isCrown()) {
						if (pc.get_sex() == 0)
							polyId = 11408;
						else
							polyId = 11409;
					} else if (pc.isKnight()) {
						if (pc.get_sex() == 0)
							polyId = 11410;
						else
							polyId = 11411;
					} else if (pc.isElf()) {
						if (pc.get_sex() == 0)
							polyId = 11412;
						else
							polyId = 11413;
					} else if (pc.isWizard()) {
						if (pc.get_sex() == 0)
							polyId = 11414;
						else
							polyId = 11415;
					} else if (pc.isDarkelf()) {
						if (pc.get_sex() == 0)
							polyId = 11416;
						else
							polyId = 11417;
					} else if (pc.isDragonknight()) {
						if (pc.get_sex() == 0)
							polyId = 11418;
						else
							polyId = 11419;
					} else if (pc.isBlackwizard()) {
						if (pc.get_sex() == 0)
							polyId = 11420;
						else
							polyId = 11421;
					} else if (pc.isWarrior()) {
						if (pc.get_sex() == 0)
							polyId = 12613;
						else
							polyId = 12614;
					}
				} else if (poly.getOption() == 5) { // 이벤트변신
					if (!pc.getInventory().checkItem(900077)) {
						System.out.println(pc.getName() + " 님이 변신 버그를 시도 합니다.");
						polyId = 0;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return polyId;
	}
}
