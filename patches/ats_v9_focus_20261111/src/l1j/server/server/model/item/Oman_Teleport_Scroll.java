package l1j.server.server.model.item;

import l1j.server.server.model.L1Location;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.CommonUtil;

public class Oman_Teleport_Scroll {
	
	public static Oman_Teleport_Scroll getInstance() {
		return LazyHolder.instance;
	}
	private static class LazyHolder {
		private static final Oman_Teleport_Scroll instance = new Oman_Teleport_Scroll();
	}
	
	public boolean use(L1PcInstance pc, int itemId, ItemInstance useitem) {
        // ¹ö±× ¹æÁö 
		if(!ScrollCheck(itemId)) {
			return false;
		}    
		if(teleport(pc, itemId, useitem)) {
			return true;
		}
		return false;
	}
	
	private boolean teleport(L1PcInstance pc, int itemId, ItemInstance useitem) {
		L1Location newLocation = pc.getLocation().randomLocation(100, true);
		int newX = newLocation.getX();
		int newY = newLocation.getY();
		short mapId = (short) newLocation.getMapId();
		int ·£´ý =  CommonUtil.random(3);
		switch (itemId) {
		case ItemId.¿À¸¸_Å¾_10Ãþ_ÁÖ¹®¼­:
			if(pc.getMapId() == 110) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {	
				pc.teleport(32753 + ·£´ý , 32747 + ·£´ý, (short) 110, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.¿À¸¸_Å¾_20Ãþ_ÁÖ¹®¼­:
			if(pc.getMapId() == 120) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32753 + ·£´ý , 32747 + ·£´ý, (short) 120, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.¿À¸¸_Å¾_30Ãþ_ÁÖ¹®¼­:
			if(pc.getMapId() == 130) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32753 + ·£´ý , 32747 + ·£´ý, (short) 130, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.¿À¸¸_Å¾_40Ãþ_ÁÖ¹®¼­:
			if(pc.getMapId() == 140) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32753 + ·£´ý , 32747 + ·£´ý, (short) 140, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.¿À¸¸_Å¾_50Ãþ_ÁÖ¹®¼­:
			if(pc.getMapId() == 150) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32753 + ·£´ý , 32747 + ·£´ý, (short) 150, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.¿À¸¸_Å¾_60Ãþ_ÁÖ¹®¼­:
			if(pc.getMapId() == 160) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32684 + ·£´ý , 32814 + ·£´ý, (short) 160, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.¿À¸¸_Å¾_70Ãþ_ÁÖ¹®¼­:
			if(pc.getMapId() == 170) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32634 + ·£´ý , 32787 + ·£´ý, (short) 170, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.¿À¸¸_Å¾_80Ãþ_ÁÖ¹®¼­:
			if(pc.getMapId() == 180) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32634 + ·£´ý , 32787 + ·£´ý, (short) 180, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.¿À¸¸_Å¾_90Ãþ_ÁÖ¹®¼­:
			if(pc.getMapId() == 190) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32634 + ·£´ý , 32787 + ·£´ý, (short) 190, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.¿À¸¸_Å¾_Á¤»ó_ÁÖ¹®¼­:
			if(pc.getMapId() == 200) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32800 + ·£´ý , 32963 + ·£´ý, (short) 200, pc.getHeading(), 169, true, false);
			}
			break;	
		}	
		return true;		
	}
    
	private boolean ScrollCheck(int itemId) {
		return (itemId == ItemId.¿À¸¸_Å¾_10Ãþ_ÁÖ¹®¼­ || itemId == ItemId.¿À¸¸_Å¾_20Ãþ_ÁÖ¹®¼­ || itemId == ItemId.¿À¸¸_Å¾_30Ãþ_ÁÖ¹®¼­ ||
				itemId == ItemId.¿À¸¸_Å¾_40Ãþ_ÁÖ¹®¼­ || itemId == ItemId.¿À¸¸_Å¾_50Ãþ_ÁÖ¹®¼­ || itemId == ItemId.¿À¸¸_Å¾_60Ãþ_ÁÖ¹®¼­ ||
				itemId == ItemId.¿À¸¸_Å¾_70Ãþ_ÁÖ¹®¼­ || itemId == ItemId.¿À¸¸_Å¾_80Ãþ_ÁÖ¹®¼­ || itemId == ItemId.¿À¸¸_Å¾_90Ãþ_ÁÖ¹®¼­ ||
				itemId == ItemId.¿À¸¸_Å¾_Á¤»ó_ÁÖ¹®¼­);
		
	}

}
