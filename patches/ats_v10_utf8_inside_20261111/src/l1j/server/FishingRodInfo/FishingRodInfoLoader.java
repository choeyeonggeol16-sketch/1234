package l1j.server.FishingRodInfo;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.Template.LORnd;
import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.ServerBasePacket;

public class FishingRodInfoLoader {
	private static FishingRodInfoLoader _instance;
	public static FishingRodInfoLoader getInstance() {
		if(_instance == null)
			_instance = new FishingRodInfoLoader();
		return _instance;
	}
	
	public static void reload() {
		if(_instance != null) {
			_instance = new FishingRodInfoLoader();
		}
	}
	
	private HashMap<Integer, ArrayList<FishingRodInfo>> _set_fishing_rod_info;
	
	private FishingRodInfoLoader() {
		Optionload();
	}
	
	private void Optionload() {
		final HashMap<Integer, ArrayList<FishingRodInfo>> present_list_map = new HashMap<Integer, ArrayList<FishingRodInfo>>();
		Selector.exec("select * from fishing_rod_info", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					try {
						FishingRodInfo Info = FishingRodInfo.newInstance(rs);
						if (Info == null)
							continue;

						ArrayList<FishingRodInfo> inputList = present_list_map.get(Info.get_fishing_rod_item());
						if (inputList == null) {
							inputList = new ArrayList<FishingRodInfo>();
							present_list_map.put(Info.get_fishing_rod_item(), inputList);
						}
						inputList.add(Info);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		});
		_set_fishing_rod_info = present_list_map;
	}
	
	public void SuccessFishing(L1PcInstance pc, int itemid) {
		ArrayList<FishingRodInfo> fishing_info_list = _set_fishing_rod_info.get(itemid);
		int max_percent = LORnd.next(1000000) + 1;
		if (fishing_info_list != null) {
			for (FishingRodInfo info : fishing_info_list) {
				if (max_percent >= info.get_probability_min() && max_percent <= info.get_probability_max()) {
					if (!check_weight(pc)) {
						return;
					}
					ItemInstance item = pc.getInventory().storeItem(info.get_fishing_item_id(), 1);
					if (item != null) {
						item.setIdentified(true);
						if (info.get_ment() == null) {
							pc.sendPackets(new S_ServerMessage(1185, info.get_fishing_item_name()));
						}
						else {
							pc.sendPackets(info.get_ment());	
						}		
						if(info.get_effect_id() > 0) {
							ServerBasePacket pck = S_SkillSound.get(pc.getId(), info.get_effect_id());
							pc.sendPackets(pck);
							pc.broadcastPacket(pck);
						}
						
						if (info.isBroadCast()) {
							L1World.getInstance().broadcastPacketToAll(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, String.format("어느 누군가가 낚시에 성공하여 %s을(를) 낚았습니다.", info.get_fishing_item_name())));
						}
						
						
					}
					return;
				}
			}
		}
	}
	
	private boolean check_weight(L1PcInstance pc) {
		if (pc.getInventory().getSize() > (180 - 16)) {
			pc.sendPackets(new S_ServerMessage(263));
			return false;
		}
		return true;
	}
}
