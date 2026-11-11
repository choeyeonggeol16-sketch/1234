package l1j.server.server.clientpackets;

import l1j.server.Templates.L1House;
import l1j.server.server.ActionCodes;
import l1j.server.server.GameClient;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.DoorInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class C_Door extends ClientBasePacket {

	private static final String C_DOOR = "[C] C_Door";

	public C_Door(byte abyte0[], GameClient client) throws Exception {
		super(abyte0);
		int locX = readH();// 문짝 클릭
		int locY = readH();// 문짝 클릭
		int objectId = readD();

		L1PcInstance pc = client.getActiveChar();
		if ( pc == null)return;

		//L1DoorInstance door = (L1DoorInstance)L1World.getInstance().findObject(objectId);
		DoorInstance door = null;
		L1Object obj = L1World.getInstance().findObject(objectId);
		if(obj instanceof DoorInstance) door = (DoorInstance) obj;
		if (door == null) {
			return;
		}

		if( L1World.getInstance().findObject(objectId) == null ||
				!(L1World.getInstance().findObject(objectId) instanceof DoorInstance) ) {
			return;
		}
		
		if (locX > pc.getX() + 1 || locX < pc.getX() - 1 || locY > pc.getY() + 1 || locY < pc.getY() - 1) {
			return;
		}


		if(door.getNpcId() >= 5147 && door.getNpcId() <= 5151){
			return;		
		}
		
		if (door != null && !isExistKeeper(pc, door.getKeeperId())) {
			if(door.getDoorId() == 113){
				if(pc.getInventory().checkItem(40163)){
					pc.getInventory().consumeItem(40163, 1);
				}else{
					return;
				}
			}
			if (door.getDoorId() == 125) {
				if (pc.getInventory().checkItem(40313)) {
					pc.getInventory().consumeItem(40313, 1);
				} else {
					return;
				}
			}

			if (door.getDoorId() >= 5000 && door.getDoorId() <= 5009) {
				if (pc.getInventory().checkItem(49035)) {
					pc.getInventory().consumeItem(49035, 1);
				} else {
					return;
				}
			}

			if (door.getDoorId() >= 900151 && door.getDoorId() <= 900154) {// 하딘 문
				return;
			}

			if (door.getOpenStatus() == ActionCodes.ACTION_Open) {
				if (door.getDoorId() >= 5000 && door.getDoorId() <= 5009) {
					return;
				}
				door.close();
			} else if (door.getOpenStatus() == ActionCodes.ACTION_Close) {
				door.open();
			}
		}
	}

	private boolean isExistKeeper(L1PcInstance pc, int keeperId) {
		if (keeperId == 0) {
			return false;
		}

		L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
		if (clan != null) {
			int houseId = clan.getHouseId();
			if (houseId != 0) {
				L1House house = HouseTable.getInstance().getHouseTable(houseId);
				if (keeperId == house.getKeeperId()) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public String getType() {
		return C_DOOR;
	}
}
