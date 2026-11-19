package l1j.server.server.model;
import l1j.server.server.model.network.core.sc.packet.SC_ItemStatus;

import java.util.ArrayList;
import java.util.StringTokenizer;

import l1j.server.server.datatables.ArmorSetTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1ArmorSets;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconBlessOfEva;

public abstract class L1ArmorSet {
	
    public abstract void giveEffect(L1PcInstance pc);
    public abstract void cancelEffect(L1PcInstance pc);
    public abstract boolean isValid(L1PcInstance pc);
    public abstract boolean isPartOfSet(int id);
    public abstract boolean isEquippedRingOfArmorSet(L1PcInstance pc);
         
    public static ArrayList<L1ArmorSet> getAllSet() {
        return _allSet;
    }

    private static ArrayList<L1ArmorSet> _allSet = new ArrayList<L1ArmorSet>();

    static {
        L1ArmorSetImpl impl;

        for (L1ArmorSets armorSets : ArmorSetTable.getInstance().getAllList()) {
            try {

                impl = new L1ArmorSetImpl(getArray(armorSets.getSets(), ","));
                 
                if (armorSets.getPolyId() != -1) {
                    impl.addEffect(new PolymorphEffect(armorSets.getPolyId()));
                }
                if (armorSets.getId() == 128) {
                    impl.addEffect(new EvaiconEffect());
                } 
                if (armorSets.get_main_id() != 0) {
                	impl.addEffect(new ArmorSetItem(armorSets.get_main_id()));
                }
                
                impl.addEffect(new AcHpMpBonusEffect(armorSets.getAc(),
                		armorSets.getHp(), armorSets.getMp(),
                        armorSets.getHpr(), armorSets.getMpr(),
                        armorSets.getMr()));
                impl.addEffect(new StatBonusEffect(armorSets.getStr(),
                        armorSets.getDex(), armorSets.getCon(),
                        armorSets.getWis(), armorSets.getCha(),
                        armorSets.getIntl()));
                impl.addEffect(new defense(armorSets.get_defense_water(),
                        armorSets.get_defense_earth(),
                        armorSets.get_defense_wind(),
                        armorSets.get_defense_fire()));
                _allSet.add(impl);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static int[] getArray(String s, String sToken) {
        StringTokenizer st = new StringTokenizer(s, sToken);
        int size = st.countTokens();
        String temp = null;
        int[] array = new int[size];
        for (int i = 0; i < size; i++) {
            temp = st.nextToken();
            array[i] = Integer.parseInt(temp);
        }
        return array;
    }
}

interface L1ArmorSetEffect {
    public void giveEffect(L1PcInstance pc);
    public void cancelEffect(L1PcInstance pc);
}

class L1ArmorSetImpl extends L1ArmorSet {
    private final int _ids[];
    private final ArrayList<L1ArmorSetEffect> _effects;

    protected L1ArmorSetImpl(int ids[]) {
        _ids = ids;
        _effects = new ArrayList<L1ArmorSetEffect>();
    }

    public void addEffect(L1ArmorSetEffect effect) {
        _effects.add(effect);
    }

    public void removeEffect(L1ArmorSetEffect effect) {
        _effects.remove(effect);
    }

    @Override
    public void cancelEffect(L1PcInstance pc) {
        for (L1ArmorSetEffect effect : _effects) {
            effect.cancelEffect(pc);
        }
    }

    @Override
    public void giveEffect(L1PcInstance pc) {
        for (L1ArmorSetEffect effect : _effects) {
            effect.giveEffect(pc);
        }
    }

    @Override
    public final boolean isValid(L1PcInstance pc) {
        return pc.getInventory().checkEquipped(_ids);
    }

    @Override
    public boolean isPartOfSet(int id) {
        for (int i : _ids) {
            if (id == i) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean isEquippedRingOfArmorSet(L1PcInstance pc) {
        L1PcInventory pcInventory = pc.getInventory();
        L1ItemInstance armor = null;
        boolean isSetContainRing = false;

        for (int id : _ids) {
            armor = pcInventory.findItemId(id);
            if (armor.getItem().getType2() == 2
                    && armor.getItem().getType() == 9) {
                isSetContainRing = true;
                break;
            }
        }

        if (armor != null && isSetContainRing) {
            int itemId = armor.getItem().getItemId();
            if (pcInventory.getTypeEquipped(2, 9) >= 2) {
                L1ItemInstance ring[] = new L1ItemInstance[4];
                ring = pcInventory.getRingEquipped();
                if (ring != null && ring.length > 0) {
                    int count = 0;
                    for (L1ItemInstance item : ring) {
                        if (item == null)
                            continue;
                        if (item.getItemId() == itemId)
                            count++;
                    }
                    if (count >= 2)
                        return true;
                }
            }
        }
        return false;
    }
}
 

class AcHpMpBonusEffect implements L1ArmorSetEffect {
    private final int _ac;
    private final int _addHp;
    private final int _addMp;
    private final int _regenHp;
    private final int _regenMp;
    private final int _addMr;

    public AcHpMpBonusEffect(int ac, int addHp, int addMp, int regenHp,
            int regenMp, int addMr) {
        _ac = ac;
        _addHp = addHp;
        _addMp = addMp;
        _regenHp = regenHp;
        _regenMp = regenMp;
        _addMr = addMr;
    }

    @Override
    public void giveEffect(L1PcInstance pc) {
        pc.getAC().addAc(_ac);
        pc.addMaxHp(_addHp);
        pc.addMaxMp(_addMp);
        pc.addHpr(_regenHp);
        pc.addMpr(_regenMp);
        pc.getResistance().addMr(_addMr);
    }

    @Override
    public void cancelEffect(L1PcInstance pc) {
        pc.getAC().addAc(-_ac);
        pc.addMaxHp(-_addHp);
        pc.addMaxMp(-_addMp);
        pc.addHpr(-_regenHp);
        pc.addMpr(-_regenMp);
        pc.getResistance().addMr(-_addMr);
    }
}

class StatBonusEffect implements L1ArmorSetEffect {
    private final int _str;
    private final int _dex;
    private final int _con;
    private final int _wis;
    private final int _cha;
    private final int _intl;

    public StatBonusEffect(int str, int dex, int con, int wis, int cha, int intl) {
        _str = str;
        _dex = dex;
        _con = con;
        _wis = wis;
        _cha = cha;
        _intl = intl;
    }

    @Override
    public void giveEffect(L1PcInstance pc) {
        pc.getAbility().addAddedStr((byte) _str);
        pc.getAbility().addAddedDex((byte) _dex);
        pc.getAbility().addAddedCon((byte) _con);
        pc.getAbility().addAddedWis((byte) _wis);
        pc.getAbility().addAddedCha((byte) _cha);
        pc.getAbility().addAddedInt((byte) _intl);
    }

    @Override
    public void cancelEffect(L1PcInstance pc) {
        pc.getAbility().addAddedStr((byte) -_str);
        pc.getAbility().addAddedDex((byte) -_dex);
        pc.getAbility().addAddedCon((byte) -_con);
        pc.getAbility().addAddedWis((byte) -_wis);
        pc.getAbility().addAddedCha((byte) -_cha);
        pc.getAbility().addAddedInt((byte) -_intl);
    }
}

class defense implements L1ArmorSetEffect {

    private final int _defense_water;
    private final int _defense_earth;
    private final int _defense_wind;
    private final int _defense_fire;

    public defense(int defense_water, int defense_earth, int defense_wind, int defense_fire) {
        _defense_water = defense_water;
        _defense_earth = defense_earth;
        _defense_wind = defense_wind;
        _defense_fire = defense_fire;
    }

    public void giveEffect(L1PcInstance pc) {
        pc.getResistance().addWater(_defense_water);
        pc.getResistance().addEarth(_defense_earth);
        pc.getResistance().addWind(_defense_wind);
        pc.getResistance().addFire(_defense_fire);
    }

    public void cancelEffect(L1PcInstance pc) {
        pc.getResistance().addWater(-_defense_water);
        pc.getResistance().addEarth(-_defense_earth);
        pc.getResistance().addWind(-_defense_wind);
        pc.getResistance().addFire(-_defense_fire);
    }
}

class PolymorphEffect implements L1ArmorSetEffect {
    private int _gfxId;

    public PolymorphEffect(int gfxId) {
        _gfxId = gfxId;
    }

    @Override
    public void giveEffect(L1PcInstance pc) {
        if (_gfxId == 6080 || _gfxId == 6094) {
            if (pc.get_sex() == 0) {
                _gfxId = 6094;
            } else {
                _gfxId = 6080;
            }
            if (!isRemainderOfCharge(pc)) {
                return;
            }
        }
        L1PolyMorph.doPoly(pc, _gfxId, 0, L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
        //L1NeigeBugCheck.shapechange(pc);
    }

    @Override
    public void cancelEffect(L1PcInstance pc) {
        if (_gfxId == 6080) {
            if (pc.get_sex() == 0) {
                _gfxId = 6094;
            }
        }
        if (pc.getCurrentSpriteId() != _gfxId) {
            return;
        }
        L1PolyMorph.undoPoly(pc);
    }

    private boolean isRemainderOfCharge(L1PcInstance pc) {
        boolean isRemainderOfCharge = false;
        if (pc.getInventory().checkItem(20383, 1)) {
            L1ItemInstance item = pc.getInventory().findItemId(20383);
            if (item != null) {
                if (item.getChargeCount() != 0) {
                    isRemainderOfCharge = true;
                }
            }
        }
        return isRemainderOfCharge;
    }

}

class EvaiconEffect implements L1ArmorSetEffect {
    public EvaiconEffect() {
    }

    @Override
    public void giveEffect(L1PcInstance pc) {
        SC_SkillIconBlessOfEva.send(pc, pc.getId(), -1);
    }

    @Override
    public void cancelEffect(L1PcInstance pc) {
        SC_SkillIconBlessOfEva.send(pc, pc.getId(), 0);
    }
}

class ArmorSetItem implements L1ArmorSetEffect {
    private int main_itemid;
    
    public ArmorSetItem(int itemid) {
    	main_itemid = itemid;
    }
    
	@Override
	public void giveEffect(L1PcInstance pc) {
		L1ItemInstance set_item = pc.getInventory().get_set_item_eq(main_itemid);
		if (set_item != null) {
			set_item.set_main_set_armor(true);
			SC_ItemStatus.send(pc, set_item);
		}
	}

	@Override
	public void cancelEffect(L1PcInstance pc) {
		L1ItemInstance set_item = pc.getInventory().findItemId(main_itemid);
		if (set_item != null) {
			set_item.set_main_set_armor(false);
			SC_ItemStatus.send(pc, set_item);
		}
	}
}