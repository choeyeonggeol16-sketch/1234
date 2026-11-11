package l1j.server.SetItems;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.Template.ArrangeHelper.MJArrangeParseeFactory;
import l1j.server.Template.ArrangeHelper.MJArrangeParser;
import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_HPUpdate;
import l1j.server.server.serverpackets.S_MPUpdate;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_SPMR;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.utils.ItemPresentOutStream;

public class SetItemsLoader {
	private static SetItemsLoader _instance;
	public static SetItemsLoader getInstance() {
		if(_instance == null)
			_instance = new SetItemsLoader();
		return _instance;
	}

	public static void reload() {
		if(_instance != null) {
			_instance = new SetItemsLoader();
		}
	}

	private HashMap<Integer, SetItemsInfo> _set_item_info;
	private HashMap<Integer, ArrayList<SetOptionInfo>> _set_option_info;

	private SetItemsLoader() {
		Itemsload();
		Optionload();
	}

	private void Itemsload() {
		final HashMap<Integer, SetItemsInfo> bonus = new HashMap<Integer, SetItemsInfo>(256);
		Selector.exec("select * from set_items_info", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					SetItemsInfo dInfo = SetItemsInfo.newInstance(rs);
					if(dInfo == null) {
						continue;
					}
					bonus.put(dInfo.get_id(), dInfo);
				}
			}
		});
		_set_item_info = bonus;
	}

	private void Optionload() {
		final HashMap<Integer, ArrayList<SetOptionInfo>> present_list_map = new HashMap<Integer, ArrayList<SetOptionInfo>>();
		Selector.exec("select * from set_items_option", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					try {
						SetOptionInfo Info = SetOptionInfo.newInstance(rs);
						if (Info == null) {
							continue;
						}
						ArrayList<SetOptionInfo> inputList = present_list_map.get(Info.getId());
						if (inputList == null) {
							inputList = new ArrayList<SetOptionInfo>();
							present_list_map.put(Integer.valueOf(Info.getId()), inputList);
						}
						inputList.add(Info);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		});
		_set_option_info = present_list_map;
	}

	public ArrayList<SetItemsInfo> values(){
		return new ArrayList<SetItemsInfo>(_set_item_info.values());
	}

	public SetItemsInfo getItemsInfo(int id) {
		ArrayList<SetItemsInfo> infos = values();
		if(infos == null) {
			return null;
		}
		for(SetItemsInfo item_Info : infos){
			String[] items = (String[])MJArrangeParser.parsing(item_Info.get_Items(), ",", MJArrangeParseeFactory.createStringArrange()).result();
			for (String item_id : items) {
				int itemId = Integer.parseInt(item_id);
				if (itemId == id) {
					return item_Info;
				}
			}
		}
		return null;
	}

	private void setOption(L1PcInstance pc, SetItemsInfo set_info, int i, int size, int last_size, boolean flag) {
		ArrayList<SetOptionInfo> present_info_list = _set_option_info.get(i);
		boolean poly_check = false;
		if (present_info_list != null) {
			for (SetOptionInfo present_info : present_info_list) {
				if (present_info != null) {
					if (present_info.get_equip_size() == size) {
						if (present_info.get_equip_size() == last_size) {
							poly_check = true;
						}
						EffectList(pc, parseeffectType(present_info.get_option()), present_info.get_option_value(), flag==true ? 1:0);
					}
				}
			}
			if (poly_check) {
				if (set_info.get_poly_id() > 0) {
					int poly_id = set_info.get_poly_id();
					if (set_info.get_high_poly_id() > 0) {
						if (pc.getLevel() >= set_info.get_high_poly_level()) {
							poly_id = set_info.get_high_poly_id();
							SetItemPolymorph(pc, poly_id);
						} else {
							SetItemPolymorph(pc, poly_id);
						}
					} else {
						if (set_info.get_poly_desc().equalsIgnoreCase("랭킹 변신")) {
							if (pc.isCrown()) {
								if (pc.get_sex() == 0) {
									poly_id = 13715;
								} else {
									poly_id = 13717;
								}					
							} else if (pc.isKnight()) {
								if (pc.get_sex() == 0) {
									poly_id = 15115;
								} else {
									poly_id = 13721;
								}		
							} else if (pc.isElf()) {
								if (pc.get_sex() == 0) {
									poly_id = 13723;
								} else {
									poly_id = 13725;
								}
							} else if (pc.isWizard()) {
								if (pc.get_sex() == 0) {
									poly_id = 13727;
								} else {
									poly_id = 13729;
								}							
							} else if (pc.isDarkelf()) {
								if (pc.get_sex() == 0) {
									poly_id = 13731;
								} else {
									poly_id = 13733;
								}							
							}
						}					
						SetItemPolymorph(pc, poly_id);
					}
				}
			}		
			if(set_info.get_poly_desc() == null) { // 변신 이름이 없는 경우 
				return;
			}
			if(!flag && size == last_size) { // 사이즈가 마지막 싸이즈가 아닐경우 
				L1PolyMorph.setItemsPoly(pc);
			}
		}
	}

	private void SetItemPolymorph(L1PcInstance pc, int poly_id) {
		if (poly_id == 6080 || poly_id == 6094) {
			if (pc.get_sex() == 0) {
				poly_id = 6094;
			} else {
				poly_id = 6080;
			}
		}
		L1PolyMorph.doPoly(pc, poly_id, -1,
				L1PolyMorph.MORPH_BY_LOGIN);
	}


	public void equip_set_items(L1PcInstance pc, ItemInstance item, boolean flag) {
		if (pc != null) {
			SetItemsInfo set_info = getItemsInfo(item.getItemId());
			if (set_info != null) {
				String[] option_size = (String[]) MJArrangeParser.parsing(set_info.get_option_size(), ",", MJArrangeParseeFactory.createStringArrange()).result();
				int last_size = Integer.parseInt(option_size[option_size.length - 1]);
				int set_id = set_info.get_id();
				String[] items = (String[]) MJArrangeParser.parsing(set_info.get_Items(), ",", MJArrangeParseeFactory.createStringArrange()).result();
				for (String item_id : items) {
					int itemId = Integer.parseInt(item_id);
					if (itemId == item.getItemId()) {
						if (flag) {
							ArrayList<ItemInstance> inputList = pc.getEquipSetItems().get(set_id);
							if (inputList == null) {
								inputList = new ArrayList<ItemInstance>();
								pc.getEquipSetItems().put(set_id, inputList);
							}
							inputList.add(item);
							int equip_size = pc.getEquipSetItems().get(set_id).size();
							setOption(pc, set_info, set_id, equip_size, last_size, flag);
						} else {
							int equip_size = pc.getEquipSetItems().get(set_id).size();
							setOption(pc, set_info, set_id, equip_size, last_size, flag);
							pc.getEquipSetItems().get(set_id).remove(item);
						}
					}
				}

			//	for (ItemInstance set_item : pc.getInventory().getItems()) {
			//		if (SetItemsLoader.getInstance().send_view_set_items_check(set_item)) {
			//			pc.getInventory().updateItem(set_item, L1PcInventory.COL_IS_ID);
			//		}
			//	}
			}
		}
	}

	@SuppressWarnings("resource")
	private byte[] sendOption(L1PcInstance pc, SetItemsInfo set_info, int set_id, int op_id, int last_size, int equip_size) throws IOException{
		ItemPresentOutStream os = new ItemPresentOutStream();
		ArrayList<SetOptionInfo> present_info_list = _set_option_info.get(set_id);
		int short_dmg = 0;
		int short_hit = 0;
		int long_dmg = 0;
		int long_hit = 0;
		int sp = 0;
		int str = 0;
		int dex = 0;
		int intel = 0;
		int con = 0;
		int wis = 0;
		int cha = 0;
		int ac = 0;
		int mr = 0;
		int dmg_reduc = 0;
		int weight = 0;
		int re_earth = 0;
		int re_wind = 0;
		int re_water = 0;
		int re_fire = 0;
		int max_hp = 0;
		int max_mp = 0;
		int hp_r = 0;
		int mp_r = 0;
		boolean poly_check = false;

		if (present_info_list != null) {
			boolean haste = false;
			for (SetOptionInfo present_info : present_info_list) {
				if (present_info != null) {
					if (present_info.get_equip_size() > op_id)
						continue;

					if (present_info.get_equip_size() == last_size)
						poly_check = true;

					String val = present_info.get_option();
					int val2 = present_info.get_option_value();

					if (val.equalsIgnoreCase("근거리 대미지"))
						short_dmg += val2;
					if (val.equalsIgnoreCase("근거리 명중"))
						short_hit += val2;
					if (val.equalsIgnoreCase("원거리 대미지"))
						long_dmg += val2;
					if (val.equalsIgnoreCase("원거리 명중"))
						long_hit += val2;
					if (val.equalsIgnoreCase("SP"))
						sp += val2;
					if (val.equalsIgnoreCase("STR"))
						str += val2;
					if (val.equalsIgnoreCase("DEX")) 
						dex += val2;
					if (val.equalsIgnoreCase("INT")) 
						intel += val2;
					if (val.equalsIgnoreCase("CON")) 
						con += val2;
					if (val.equalsIgnoreCase("WIS")) 
						wis += val2;
					if (val.equalsIgnoreCase("CHA")) 
						cha += val2;
					if (val.equalsIgnoreCase("AC")) 
						ac += val2;
					if (val.equalsIgnoreCase("MR")) 
						mr += val2;
					if (val.equalsIgnoreCase("대미지 감소")) 
						dmg_reduc += val2;
					if (val.equalsIgnoreCase("소지 무게 증가")) 
						weight += val2;
					if (val.equalsIgnoreCase("땅속성")) 
						re_earth += val2;
					if (val.equalsIgnoreCase("바람속성")) 
						re_wind += val2;
					if (val.equalsIgnoreCase("물속성")) 
						re_water += val2;
					if (val.equalsIgnoreCase("불속성")) 
						re_fire += val2;
					if (val.equalsIgnoreCase("최대 HP")) 
						max_hp += val2;
					if (val.equalsIgnoreCase("최대 MP")) 
						max_mp += val2;
					if (val.equalsIgnoreCase("HP 회복률")) 
						hp_r += val2;
					if (val.equalsIgnoreCase("MP 회복률")) 
						mp_r += val2;
					if (val.equalsIgnoreCase("헤이스트")) 
						haste = true;
				}
			}

			if (short_dmg > 0)
				os.write(getItemView("근거리 대미지", short_dmg));
			if (short_hit > 0)
				os.write(getItemView("근거리 명중", short_hit));
			if (long_dmg > 0)
				os.write(getItemView("원거리 대미지", long_dmg));
			if (long_hit > 0)
				os.write(getItemView("원거리 명중", long_hit));
			if (sp > 0)
				os.write(getItemView("SP", sp));
			if (str > 0)
				os.write(getItemView("STR", str));
			if (dex > 0)
				os.write(getItemView("DEX", dex));
			if (intel > 0)
				os.write(getItemView("INT", intel));
			if (con > 0)
				os.write(getItemView("CON", con));
			if (wis > 0)
				os.write(getItemView("WIS", wis));
			if (cha > 0)
				os.write(getItemView("CHA", cha));
			if (ac > 0)
				os.write(getItemView("AC", ac));
			if (mr > 0)
				os.write(getItemView("MR", mr));
			if (dmg_reduc > 0)
				os.write(getItemView("대미지 감소", dmg_reduc));
			if (weight > 0)
				os.write(getItemView("소지 무게 증가", weight));
			if (re_earth > 0)
				os.write(getItemView("땅속성", re_earth));
			if (re_wind > 0)
				os.write(getItemView("바람속성", re_wind));
			if (re_water > 0)
				os.write(getItemView("물속성", re_water));
			if (re_fire > 0)
				os.write(getItemView("불속성", re_fire));
			if (max_hp > 0)
				os.write(getItemView("최대 HP", max_hp));
			if (max_mp > 0)
				os.write(getItemView("최대 MP", max_mp));
			if (hp_r > 0)
				os.write(getItemView("HP 회복률", hp_r));
			if (mp_r > 0)
				os.write(getItemView("MP 회복률", mp_r));
			if (haste)
				os.write(getItemView("헤이스트", 0));

			if (poly_check) {
				if (set_info.get_poly_desc() != null) {
					if (set_info.get_high_poly_desc() != null) {
						if (pc.getLevel() >= set_info.get_high_poly_level()) {
							os.writeC(39);
							os.writeS(String.format("변신:%s", set_info.get_high_poly_desc()));
						} else {
							os.writeC(39);
							os.writeS(String.format("변신:%s", set_info.get_poly_desc()));
						}
					} else {
						os.writeC(39);
						os.writeS(String.format("변신:%s", set_info.get_poly_desc()));
					}
				}
			}
		} 
		
		return os.getBytes();
	}

	public boolean send_view_set_items_check(ItemInstance item) {
		SetItemsInfo set_info = getItemsInfo(item.getItemId());
		if (set_info != null) {
			String[] items = (String[]) MJArrangeParser.parsing(set_info.get_Items(), ",", MJArrangeParseeFactory.createStringArrange()).result();
			for (String item_id : items) {
				int itemId = Integer.parseInt(item_id);
				if (itemId == item.getItemId()) {
					return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings("resource")
	public byte[] send_view_set_items(L1PcInstance pc, ItemInstance item, boolean shop) throws IOException {
		ItemPresentOutStream os = new ItemPresentOutStream();
		SetItemsInfo set_info = getItemsInfo(item.getItemId());
		boolean item_check = false;
		if (set_info != null) {
			int set_id = set_info.get_id();			
			String[] items = (String[]) MJArrangeParser.parsing(set_info.get_Items(), ",", MJArrangeParseeFactory.createStringArrange()).result();
			for (String item_id : items) {
				int itemId = Integer.parseInt(item_id);
				if (itemId == item.getItemId()) {
					item_check = true;
				}
			}

			int equip_size = 0;

			if (pc.getEquipSetItems() != null) {
				if (pc.getEquipSetItems().get(set_id) != null)
					equip_size = pc.getEquipSetItems().get(set_id).size();
			}

			if (item_check) {
				String[] option_size = (String[]) MJArrangeParser.parsing(set_info.get_option_size(), ",", MJArrangeParseeFactory.createStringArrange()).result();
				int last_size = Integer.parseInt(option_size[option_size.length - 1]);
				for(int i=0; i<last_size; ++i) {
					int op_Id = Integer.parseInt(option_size[i]);
					if (shop) {
						os.writeC(39);
						os.writeS(String.format("%d세트 보너스", last_size));
						os.write(sendOption(pc, set_info, set_id, last_size, last_size, equip_size));
						return os.getBytes();
					} else {
						if (equip_size == last_size) {
							os.writeC(39);
							os.writeS(String.format("%d세트 보너스", last_size));
							os.write(sendOption(pc, set_info, set_id, last_size, last_size, equip_size));
							return os.getBytes();
						} else if (equip_size < op_Id) {
							if (equip_size >= Integer.parseInt(option_size[0])) {
								os.writeC(39);
								os.writeS(String.format("%d세트 보너스", Integer.parseInt(option_size[i - 1])));
								os.write(sendOption(pc, set_info, set_id, Integer.parseInt(option_size[i - 1]),
										last_size, equip_size));
							}
							return os.getBytes();
						}
					}
				}
			}

			return os.getBytes();
		}
		return null;
	}

	private void EffectList(L1PcInstance pc, int option_id, int bonus, int type) {		
		switch (option_id) {
		case 1:	// 근거리 대미지
			pc.addMeleeDmg(type == 1 ? bonus : -bonus);
			break;
		case 2: // 근거리 명중
			pc.addMeleeHit(type == 1 ? bonus : -bonus);
			break;
		case 3: // 원거리 대미지
			pc.addRangedDmg(type == 1 ? bonus : -bonus);
			break;
		case 4: // 원거리 명중
			pc.addRangedHit(type == 1 ? bonus : -bonus);
			break;
		case 5: // SP
			pc.addSp(type == 1 ? bonus : -bonus);
			pc.sendPackets(new S_SPMR(pc));
			break;
		case 6: // 힘
			pc.getAbility().addAddedStr(type == 1 ? bonus : -bonus);
			break;
		case 7: // 덱스
			pc.getAbility().addAddedDex(type == 1 ? bonus : -bonus);
			break;
		case 8: // 인트
			pc.getAbility().addAddedInt(type == 1 ? bonus : -bonus);
			break;
		case 9: // 콘
			pc.getAbility().addAddedCon(type == 1 ? bonus : -bonus);
			break;
		case 10: // 위즈
			pc.getAbility().addAddedWis(type == 1 ? bonus : -bonus);
			pc.resetBaseMr();
			break;
		case 11: // 카리
			pc.getAbility().addAddedCha(type == 1 ? bonus : -bonus);
			break;
		case 12: // AC
			pc.getAC().addAc(type == 1 ? bonus : -bonus);
			break;
		case 13: // MR
			pc.getResistance().addMr(type == 1 ? bonus : -bonus);
			pc.sendPackets(new S_SPMR(pc));
			break;
		case 14: // 대미지 리덕션
			pc.addDamageReduction(type == 1 ? bonus : -bonus);
			break;
		case 15: // 무게 게이지
			pc.addWeightReduction(type == 1 ? bonus : -bonus);
			break;
		case 16: // 땅속성
			pc.getResistance().addEarth(type == 1 ? bonus : -bonus);
			break;
		case 17: // 바람속성
			pc.getResistance().addWind(type == 1 ? bonus : -bonus);
			break;
		case 18: // 물속성
			pc.getResistance().addWater(type == 1 ? bonus : -bonus);
			break;
		case 19: // 불속성
			pc.getResistance().addFire(type == 1 ? bonus : -bonus);
			break;
		case 20: // 최대 HP
			pc.addMaxHp(type == 1 ? bonus : -bonus);
			pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
			break;
		case 21: // 최대 MP
			pc.addMaxMp(type == 1 ? bonus : -bonus);
			pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp()));
			break;
		case 22: // HP 회복률
			pc.addHpr(type== 1 ? bonus : -bonus);
			break;
		case 23: // MP 회복률
			pc.addMpr(type== 1 ? bonus : -bonus);
			break;
		case 24: // 헤이스트
			if (type == 1) {
				pc.addHasteItemEquipped(1);
				pc.removeHasteSkillEffect();
				if (pc.getMoveSpeed() != 1) {
					pc.setMoveSpeed(1);
					S_SkillHaste ss = S_SkillHaste.get(pc.getId(), 1, -1);
					pc.sendPackets(ss);
					pc.broadcastPacket(ss);
				}
			} else {
				pc.addHasteItemEquipped(-1);
				if (pc.getHasteItemEquipped() == 0) {
					pc.setMoveSpeed(0);
					pc.sendPackets(S_SkillHaste.get(pc.getId(), 0, 0));
				}
			}
			break;
		}

		pc.sendPackets(new S_OwnCharAttrDef(pc));
		pc.sendPackets(new S_OwnCharStatus(pc));
	}

	private int parseeffectType(String val){
		if (val.equalsIgnoreCase("근거리 대미지")) {
			return 1;
		} else if (val.equalsIgnoreCase("근거리 명중")) {
			return 2;
		} else if (val.equalsIgnoreCase("원거리 대미지")) {
			return 3;
		} else if (val.equalsIgnoreCase("원거리 명중")) {
			return 4;
		} else if (val.equalsIgnoreCase("SP")) {
			return 5;
		} else if (val.equalsIgnoreCase("STR")) {
			return 6;
		} else if (val.equalsIgnoreCase("DEX")) {
			return 7;
		} else if (val.equalsIgnoreCase("INT")) {
			return 8;
		} else if (val.equalsIgnoreCase("CON")) {
			return 9;
		} else if (val.equalsIgnoreCase("WIS")) {
			return 10;
		} else if (val.equalsIgnoreCase("CHA")) {
			return 11;
		} else if (val.equalsIgnoreCase("AC")) {
			return 12;
		} else if (val.equalsIgnoreCase("MR")) {
			return 13;
		} else if (val.equalsIgnoreCase("대미지 감소")) {
			return 14;
		} else if (val.equalsIgnoreCase("소지 무게 증가")) {
			return 15;
		} else if (val.equalsIgnoreCase("땅속성")) {
			return 16;
		} else if (val.equalsIgnoreCase("바람속성")) {
			return 17;
		} else if (val.equalsIgnoreCase("물속성")) {
			return 18;
		} else if (val.equalsIgnoreCase("불속성")) {
			return 19;
		} else if (val.equalsIgnoreCase("최대 HP")) {
			return 20;
		} else if (val.equalsIgnoreCase("최대 MP")) {
			return 21;
		} else if (val.equalsIgnoreCase("HP 회복률")) {
			return 22;
		} else if (val.equalsIgnoreCase("MP 회복률")) {
			return 23;
		} else if (val.equalsIgnoreCase("헤이스트")) {
			return 24;
		} else {
			System.out.println("세트아이템 효과 확인 바람=" + val);
			return 0;
		}
	}

	public static byte[] getItemView(String val, int val2) {
		ItemPresentOutStream os = new ItemPresentOutStream();
		try {
			if (val.equalsIgnoreCase("근거리 대미지")) {
				os.writeC(6);
				os.writeC(val2);
			} else if (val.equalsIgnoreCase("근거리 명중")) {
				os.writeC(5);
				os.writeC(val2);
			} else if (val.equalsIgnoreCase("원거리 대미지")) {
				os.writeC(6);
				os.writeC(val2);
			} else if (val.equalsIgnoreCase("원거리 명중")) {
				os.writeC(24);
				os.writeC(val2);
			} else if (val.equalsIgnoreCase("SP")) {
				os.writeAddSP(val2);
			} else if (val.equalsIgnoreCase("STR")) {
				os.writeaStr(val2);
			} else if (val.equalsIgnoreCase("DEX")) {
				os.writeaDex(val2);
			} else if (val.equalsIgnoreCase("INT")) {
				os.writeaInt(val2);
			} else if (val.equalsIgnoreCase("CON")) {
				os.writeaCon(val2);
			} else if (val.equalsIgnoreCase("WIS")) {
				os.writeaWis(val2);
			} else if (val.equalsIgnoreCase("CHA")) {
				os.writeaCha(val2);
			} else if (val.equalsIgnoreCase("AC")) {
				os.writeC(39);
				os.writeS(String.format("추가 방어 +%d", val2));
			} else if (val.equalsIgnoreCase("MR")) {
				os.writeAddMR(val2);
			} else if (val.equalsIgnoreCase("대미지 감소")) {
				os.writeC(39);
				os.writeS(String.format("대미지 감소 +%d", val2));
			} else if (val.equalsIgnoreCase("소지 무게 증가")) {
				os.writeC(39);
				os.writeS(String.format("무게 증가 +%d", val2));
			} else if (val.equalsIgnoreCase("땅속성")) {
				os.writeRegistEarth(val2);
			} else if (val.equalsIgnoreCase("바람속성")) {
				os.writeRegistWind(val2);
			} else if (val.equalsIgnoreCase("물속성")) {
				os.writeRegistWater(val2);
			} else if (val.equalsIgnoreCase("불속성")) {
				os.writeRegistFire(val2);
			} else if (val.equalsIgnoreCase("최대 HP")) {
				os.writeAddMaxHP(val2);
			} else if (val.equalsIgnoreCase("최대 MP")) {
				os.writeMaxMP(val2);
			} else if (val.equalsIgnoreCase("HP 회복률")) {
				os.writeAddHPPrecovery(val2);
			} else if (val.equalsIgnoreCase("MP 회복률")) {
				os.writeAddMPPrecovery(val2);
			} else if (val.equalsIgnoreCase("헤이스트")) {
				os.writeHaste();
			}
			return os.getBytes();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} 
		return null;
	}
}
