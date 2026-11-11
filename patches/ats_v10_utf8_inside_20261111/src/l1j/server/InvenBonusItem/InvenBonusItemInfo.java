package l1j.server.InvenBonusItem;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.utils.ItemPresentOutStream;

public class InvenBonusItemInfo {
	static InvenBonusItemInfo newInstance(ResultSet rs) throws SQLException {
		InvenBonusItemInfo pInfo = newInstance();
		pInfo._item_id = rs.getInt("item_id");
		pInfo._item_name = rs.getString("item_name");
		pInfo._str = rs.getInt("str");
		pInfo._dex = rs.getInt("dex");
		pInfo._con = rs.getInt("con");
		pInfo._wis = rs.getInt("wis");
		pInfo._int = rs.getInt("int");
		pInfo._all_Stat = rs.getInt("all_stat"); 
		pInfo._dmg = rs.getInt("dmg");
		pInfo._dmghit = rs.getInt("dmg_hit");
		pInfo._bowdmg = rs.getInt("bow_dmg");
		pInfo._bowhit = rs.getInt("bow_hit");  
		pInfo._reduction = rs.getInt("reduction");
		pInfo._pvp_dmg = rs.getInt("pvp_dmg");
		pInfo._pvp_reduction = rs.getInt("pvp_reduction"); 
		pInfo._ac = rs.getInt("ac");
		pInfo._mr = rs.getInt("mr");
		pInfo._sp = rs.getInt("sp");
		pInfo._weight = rs.getInt("weight");
		pInfo._max_hp = rs.getInt("max_hp");
		pInfo._max_mp = rs.getInt("max_mp");
		pInfo._resis_fire = rs.getInt("resis_fire");
		pInfo._resis_earth = rs.getInt("resis_earth");
		pInfo._resis_water = rs.getInt("resis_water");
		pInfo._resis_wind = rs.getInt("resis_wind");
		
		pInfo._regist_technology = rs.getInt("regist_technology");
		pInfo._technique_hit = rs.getInt("technique_hit");
		pInfo._regist_spirit = rs.getInt("regist_spirit");
		pInfo._spirit_hit = rs.getInt("spirit_hit");
		pInfo._magic_hit = rs.getInt("magic_hit"); 
		
		
		pInfo._exp = rs.getInt("exp");
		return pInfo;
	}
	
	private static InvenBonusItemInfo newInstance() {
		return new InvenBonusItemInfo();
	}

	private int _item_id;
	
	public int get_item_id() {
		return _item_id;
	}
	
	private String _item_name;
	
	public String get_item_name() {
		return _item_name;
	}
	
	private int _str;
	
	public int get_str() {
		return _str;
	}
	
	private int _dex;
	
	public int get_dex() {
		return _dex;
	}
	
	private int _con;
	
	public int get_con() {
		return _con;
	}
	
	private int _wis;
	
	public int get_wis() {
		return _wis;
	}
	
	private int _int;
	
	public int get_int() {
		return _int;
	}
	
	private int _all_Stat;
	
	public int get_all_Stat() {
		return _all_Stat;
	}
	 
	private int _dmg;
	
	public int get_dmg() {
		return _dmg;
	}
	
	private int _dmghit;
	
	public int get_dmghit() {
		return _dmghit;
	}
	
	private int _bowdmg;
	
	public int get_bowdmg() {
		return _bowdmg;
	}
	
	
	private int _bowhit;
	
	public int get_bowhit() {
		return _bowhit;
	}
	
	
	
	
	
	
	private int _reduction;
	
	public int get_reduction() {
		return _reduction;
	}
	
	private int _pvp_dmg;
	
	public int get_pvp_dmg() {
		return _pvp_dmg;
	}
	
	private int _pvp_reduction;
	
	public int get_pvp_reduction() {
		return _pvp_reduction;
	}
	
	private int _dg;
	
	public int get_dg() {
		return _dg;
	}
	
	private int _er;
	
	public int get_er() {
		return _er;
	}
	
	private int _ac;
	
	public int get_ac() {
		return _ac;
	}
	
	private int _mr;
	
	public int get_mr() {
		return _mr;
	}
	
	private int _sp;
	
	public int get_sp() {
		return _sp;
	}
	
	private int _weight;
	
	public int get_weight() {
		return _weight;
	}
	
	private int _max_hp;
	
	public int get_max_hp() {
		return _max_hp;
	}
	
	private int _max_mp;
	
	public int get_max_mp() {
		return _max_mp;
	}
	
	private int _resis_fire;
	
	public int get_resis_fire() {
		return _resis_fire;
	}
	
	private int _resis_earth;
	
	public int get_resis_earth() {
		return _resis_earth;
	}
	
	private int _resis_water;
	
	public int get_resis_water() {
		return _resis_water;
	}
	
	private int _resis_wind;
	
	public int get_resis_wind() {
		return _resis_wind;
	}
	 
	
	private int _regist_technology;	
	
	public int get_regist_technology() {
		return _regist_technology;
	} 
	
	private int _technique_hit;	
	
	public int get_technique_hit() {
		return _technique_hit;
	} 
	
	private int _regist_spirit;	
	
	public int get_regist_spirit() {
		return _regist_spirit;
	} 
	
	private int _spirit_hit;	
	
	public int get_spirit_hit() {
		return _spirit_hit;
	} 
	
	private int _magic_hit;	
	
	public int get_magic_hit() {
		return _magic_hit;
	} 
	
	
	
	
	
	
	
	private int _exp;
	
	public int get_exp() {
		return _exp;
	} 
	
	public static void inven_option(L1PcInstance pc, int itemid, boolean onoff) {
		InvenBonusItemInfo info = InvenBonusItemLoader.getInstance().getInvenBonusItemInfo(itemid);
		if (!onoff && pc.getInventory().countItems(itemid) <= 1)
			pc.delInvenBonusItems(itemid);
		
		if (pc.isInvenBonusItems(itemid))
			return;
		
		int type = onoff ? 1 : -1;
		if (info != null) {
			
			//str
			if (info.get_str() != 0) {
				pc.getAbility().addAddedStr(type * info.get_str());
			} 
			if (info.get_dex() != 0) {
				pc.getAbility().addAddedDex(type * info.get_dex());
			}
			if (info.get_con() != 0) {
				pc.getAbility().addAddedCon(type * info.get_con());
			}
			if (info.get_wis() != 0) {
				pc.getAbility().addAddedWis(type * info.get_wis());
			}
			if (info.get_int() != 0) {
				pc.getAbility().addAddedInt(type * info.get_int());
			}
			if (info.get_all_Stat() != 0) {
				pc.getAbility().addAddedStr(type * info.get_all_Stat());
				pc.getAbility().addAddedDex(type * info.get_all_Stat());
				pc.getAbility().addAddedInt(type * info.get_all_Stat());
				pc.getAbility().addAddedWis(type * info.get_all_Stat());
				pc.getAbility().addAddedCon(type * info.get_all_Stat());
			} 
			//dmg
			if (info.get_dmg() != 0) {
				pc.addMeleeDmg(type * info.get_dmg());
			}
			//dmg_hit
			if (info.get_dmghit() != 0) {
				pc.addMeleeHit(type * info.get_dmghit());
			}
			//bow_dmg
			if (info.get_bowdmg() != 0) {
				pc.addRangedDmg(type * info.get_bowdmg());
			}
			//bow_hit
			if (info.get_bowhit() != 0) {
				pc.addRangedHit(type * info.get_bowhit());
			}
			//reduction
			if (info.get_reduction() != 0) {
				pc.addDamageReduction(type * info.get_reduction());
			}
			//pvp_dmg
			if (info.get_pvp_dmg() != 0) {
				pc.addDmgupPvP(type * info.get_pvp_dmg());
			}
			//pvp_reduction
			if (info.get_pvp_reduction() != 0) {
				pc.addDmgDownPvP(type * info.get_pvp_reduction());
			}
			if (info.get_ac() != 0) {
				pc.getAC().addAc(type * info.get_ac());
			}
			if (info.get_mr() != 0) {
				pc.getResistance().addMr(type * info.get_mr());
			}
			if (info.get_sp() != 0) {
				pc.addSp(type * info.get_sp());
			} 		
			if (info.get_weight() != 0) {
				pc.addWeightReduction(type * info.get_weight());
			}
			if (info.get_max_hp() != 0) {
				pc.addMaxHp(type * info.get_max_hp());
			}
			if (info.get_max_mp() != 0) {
				pc.addMaxMp(type * info.get_max_mp());
			}
			if (info.get_resis_fire() != 0) {
				pc.getResistance().addFire(type * info.get_resis_fire());
			}
			if (info.get_resis_earth() != 0) {
				pc.getResistance().addEarth(type * info.get_resis_earth());
			}
			if (info.get_resis_water() != 0) {
				pc.getResistance().addWater(type * info.get_resis_water());
			}
			if (info.get_resis_wind() != 0) {
				pc.getResistance().addWind(type * info.get_resis_wind());
			}
			
			if (info.get_regist_technology() != 0) {
				pc.getResistance().addregist_technology(type * info.get_regist_technology());
			}
			
			if (info.get_technique_hit() != 0) {
				pc.getResistance().addtechnique_hit(type * info.get_technique_hit());
			}
			
			if (info.get_regist_spirit() != 0) {
				pc.getResistance().addregist_spirit(type * info.get_regist_spirit());
			}
			
			if (info.get_spirit_hit() != 0) {
				pc.getResistance().addspirit_hit(type * info.get_spirit_hit());
			}
			
			if (info.get_magic_hit() != 0) {
				pc.addMagicHit(type * info.get_magic_hit());
			}
 
			if (info.get_exp() != 0) {
				pc.addExpEmblem(type * info.get_exp());
			} 
			 
			if (onoff) {
				pc.addInvenBonusItems(itemid);
			}
			
			pc.sendPackets(new S_OwnCharStatus(pc));
	        pc.sendPackets(new S_OwnCharAttrDef(pc));
		}
	}
	
	public static byte[] getItemView(InvenBonusItemInfo info) {
		ItemPresentOutStream os = new ItemPresentOutStream();
		try {
			if (info != null) {
 
				if (info.get_str() != 0) {
					os.writeaStr(info.get_str());
				}
				if (info.get_dex() != 0) {
					os.writeaDex(info.get_dex());
				}
				if (info.get_con() != 0) {
					os.writeaCon(info.get_con());
				}
				if (info.get_wis() != 0) {
					os.writeaInt(info.get_wis());
				}
				if (info.get_int() != 0) {
					os.writeaInt(info.get_int());
				}
	
				if (info.get_all_Stat() != 0) {
					os.writeaStr(info.get_all_Stat());
					os.writeaDex(info.get_all_Stat());
					os.writeaCon(info.get_all_Stat());
					os.writeaInt(info.get_all_Stat());
					os.writeaWis(info.get_all_Stat());
				} 
				if (info.get_dmg() != 0) {
					os.writeMeleeDmg(info.get_dmg());
				}
				if (info.get_dmghit() != 0) {
					os.writeMeleeHit(info.get_dmghit());
				}		
				if (info.get_bowdmg() != 0) {
					os.writeRagendDmg(info.get_bowdmg());
				}
				if (info.get_bowhit() != 0) {
					os.writeRagendHit(info.get_bowhit());
				}		
				if (info.get_reduction() != 0) {
					os.writeC(39); 
					os.writeS(String.format("대미지 감소 +%d", info.get_reduction()));  
				}
				if (info.get_pvp_dmg() != 0) {
					os.writeC(39); 
					os.writeS(String.format("PVP 추가 대미지 +%d", info.get_pvp_dmg())); 
				}
				if (info.get_pvp_reduction() != 0) {
					os.writeC(39); 
					os.writeS(String.format("PVP 대미지 감소 +%d", info.get_pvp_reduction())); 
				}			 
				if (info.get_ac() != 0) {
					os.writeC(39); 
					os.writeS("방어 증가 " + -info.get_ac()); 
				}				
				if (info.get_mr() != 0) {
					os.writeAddMR(info.get_mr());
				}
				if (info.get_sp() != 0) {
					os.writeAddSP(info.get_sp());
				}
				if (info.get_weight() != 0) {
					os.writeC(39); 
					os.writeS(String.format("무게증가 +%d", info.get_weight()));
				}
				if (info.get_max_hp() != 0) {
					os.writeAddMaxHP(info.get_max_hp());
				}
				if (info.get_max_mp() != 0) {
					os.writeMaxMP(info.get_max_mp());
				} 
				
				if (info.get_resis_fire() != 0) {
					os.writeRegistFire(info.get_resis_fire());
				}
				if (info.get_resis_earth() != 0) {
					os.writeRegistEarth(info.get_resis_earth());
				}
				if (info.get_resis_water() != 0) {
					os.writeRegistWater(info.get_resis_water());
				}
				if (info.get_resis_wind() != 0) {
					os.writeRegistWind(info.get_resis_wind());
				}
				
				//스턴내성
				if (info.get_regist_technology() != 0) {
					os.writeC(39); 
					os.writeS(String.format("스턴내성 +%d", info.get_regist_technology())); 
				}
				
				//스턴적중
				if (info.get_technique_hit() != 0) { 
					os.writeC(39); 
					os.writeS(String.format("스턴적중 +%d", info.get_technique_hit())); 
				}
				
				//정령내성
				if (info.get_regist_spirit() != 0) {
					os.writeC(39); 
					os.writeS(String.format("정령내성 +%d", info.get_regist_spirit())); 
				}
				
				//정령적중
				if (info.get_spirit_hit() != 0) {
					os.writeC(39); 
					os.writeS(String.format("정령적중 +%d", info.get_spirit_hit()));  
				}
				
				//마법적중
				if (info.get_regist_technology() != 0) {
					os.writeC(39); 
					os.writeS(String.format("마법적중 +%d", info.get_regist_technology()));  
				}
				
				if (info.get_exp() != 0) {
					os.writeAddEXP(info.get_exp());

				}			  
				return os.getBytes();
			}
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
