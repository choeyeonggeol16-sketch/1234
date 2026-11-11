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
        // 버그 방지 
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
		int 랜덤 =  CommonUtil.random(3);
		switch (itemId) {
		case ItemId.오만_탑_10층_주문서:
			if(pc.getMapId() == 110) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {	
				pc.teleport(32753 + 랜덤 , 32747 + 랜덤, (short) 110, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.오만_탑_20층_주문서:
			if(pc.getMapId() == 120) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32753 + 랜덤 , 32747 + 랜덤, (short) 120, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.오만_탑_30층_주문서:
			if(pc.getMapId() == 130) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32753 + 랜덤 , 32747 + 랜덤, (short) 130, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.오만_탑_40층_주문서:
			if(pc.getMapId() == 140) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32753 + 랜덤 , 32747 + 랜덤, (short) 140, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.오만_탑_50층_주문서:
			if(pc.getMapId() == 150) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32753 + 랜덤 , 32747 + 랜덤, (short) 150, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.오만_탑_60층_주문서:
			if(pc.getMapId() == 160) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32684 + 랜덤 , 32814 + 랜덤, (short) 160, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.오만_탑_70층_주문서:
			if(pc.getMapId() == 170) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32634 + 랜덤 , 32787 + 랜덤, (short) 170, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.오만_탑_80층_주문서:
			if(pc.getMapId() == 180) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32634 + 랜덤 , 32787 + 랜덤, (short) 180, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.오만_탑_90층_주문서:
			if(pc.getMapId() == 190) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32634 + 랜덤 , 32787 + 랜덤, (short) 190, pc.getHeading(), 169, true, false);
			}
			break;
		case ItemId.오만_탑_정상_주문서:
			if(pc.getMapId() == 200) {
				pc.teleport(newX, newY, mapId, pc.getHeading(), 169, true, false);
			} else {
				pc.teleport(32800 + 랜덤 , 32963 + 랜덤, (short) 200, pc.getHeading(), 169, true, false);
			}
			break;	
		}	
		return true;		
	}
    
	private boolean ScrollCheck(int itemId) {
		return (itemId == ItemId.오만_탑_10층_주문서 || itemId == ItemId.오만_탑_20층_주문서 || itemId == ItemId.오만_탑_30층_주문서 ||
				itemId == ItemId.오만_탑_40층_주문서 || itemId == ItemId.오만_탑_50층_주문서 || itemId == ItemId.오만_탑_60층_주문서 ||
				itemId == ItemId.오만_탑_70층_주문서 || itemId == ItemId.오만_탑_80층_주문서 || itemId == ItemId.오만_탑_90층_주문서 ||
				itemId == ItemId.오만_탑_정상_주문서);
		
	}

}
