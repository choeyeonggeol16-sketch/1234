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

package l1j.server.server.clientpackets;

import l1j.server.RaidSystem.Dragon_Raid_Space;
import l1j.server.server.ActionCodes;
import l1j.server.server.GameClient;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.datatables.WantedTelTable;
import l1j.server.server.model.Getback;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_CharVisualUpdate;
import l1j.server.server.serverpackets.S_MapID;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_Weather;
import l1j.server.server.serverpackets.S_WorldObject;

public class C_Restart extends ClientBasePacket {

	public C_Restart(byte abyte0[], GameClient client) throws Exception {
		super(abyte0);

		L1PcInstance pc = client.getActiveChar();

		if (pc == null) {
			return;
		}
		if (!pc.isDead()) {
			return;
		}
		if (pc.isGhost()){ // 관람모드 버그 막기.
			pc.endGhost();
		}		
		// 레이드에서 죽었을때 
		Dragon_Raid_Space.getInstance().getBackPc(pc);

		if(pc.isFishing()){ // 성장의 낚시로 인한 한번더 검출 리스시
			FishingTimeController.getInstance().endFishing(pc);
		}
		if(WantedTelTable.getInstance().isMapZoneChange(pc.getMapId())) {
			if (pc.getHuntCount() == 0) {  
				pc.setX(33436);
				pc.setY(32802);
				pc.setMap((short) 4);
			}
		}
		processRestart(pc);

	}
	public static void processRestart(L1PcInstance pc) {
		synchronized(pc) {
			try{
				// 리스타트 버튼을 누르면 귀환지로 가게된다. 
				int[] loc;
				loc = Getback.GetBack_Restart(pc);
				if (pc.getHellTime() > 0) {
					loc = new int[3];
					loc[0] = 32701;
					loc[1] = 32777;
					loc[2] = 666;
				} else {
					loc = Getback.GetBack_Location(pc, true);
				}
				pc.removeAllKnownObjects();
				pc.broadcastPacket(S_RemoveObject.get(pc));
				pc.setCurrentHp(pc.getLevel());
				pc.setFood(39);
				pc.setDead(false);
				pc.setStatus(ActionCodes.ACTION_Walk);	
				L1World.getInstance().moveVisibleObject(pc, loc[2]);
				pc.setLocation(loc[0], loc[1], (short) loc[2]);
				pc.sendPackets(new S_MapID(pc.getMap().getBaseMapId(), pc.getMap().isUnderwater()));
				pc.sendPackets(S_WorldObject.in(pc));
				pc.broadcastPacket(S_WorldObject.out(pc));
				pc.sendPackets(S_CharVisualUpdate.get(pc));
				pc.startHpMpRegeneration(); 
				pc.sendPackets(new S_Weather(L1World.getInstance().getWeather()));	
				
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
}