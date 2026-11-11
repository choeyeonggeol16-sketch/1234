package l1j.server.MagicDollEventMonster;

import java.sql.ResultSet;
import java.util.HashMap;
import l1j.server.Template.LORnd;
import l1j.server.Template.ArrangeHelper.MJArrangeParseeFactory;
import l1j.server.Template.ArrangeHelper.MJArrangeParser;
import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.ServerBasePacket;

public class MagicDollEventMonsterLoader {
	private static MagicDollEventMonsterLoader _instance;
	public static MagicDollEventMonsterLoader getInstance() {
		if(_instance == null)
			_instance = new MagicDollEventMonsterLoader();
		return _instance;
	}
	public static void reload() {
		if(_instance != null) {
			_instance = new MagicDollEventMonsterLoader();
		}
	}
	
	
	private HashMap<Integer, MagicDollEventMonsterInfo> _list;
	private MagicDollEventMonsterLoader() {
		load();
	}
	
	private void load() {
		final HashMap<Integer, MagicDollEventMonsterInfo> list = new HashMap<Integer, MagicDollEventMonsterInfo>(256);
		Selector.exec("select * from magicdoll_event_monster", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					MagicDollEventMonsterInfo Info = MagicDollEventMonsterInfo.newInstance(rs);
					if(Info == null)
						continue;
					list.put(Info.get_item_id(), Info);
				}
			}
		});
		_list = list;
	}
	
	public MagicDollEventMonsterInfo getList(int itemid) {
		if (_list.containsKey(itemid)) {
			return _list.get(itemid);
		}
		return null;
	}
	
	public boolean ChangeMonster(L1PcInstance pc, L1NpcInstance npc) {
		if (pc.getMagicDoll() != null) {
			MagicDollEventMonsterInfo info = getList(pc.getMagicDoll().getItemId());
			if (info != null) {
				if (npc.getNpcId() == info.get_spawn_npc())
					return false;

				String[] mapids = (String[]) MJArrangeParser.parsing(info.get_map_ids(), ",", MJArrangeParseeFactory.createStringArrange()).result();
				String[] probabilitys = (String[]) MJArrangeParser.parsing(info.get_spawn_probability(), ",", MJArrangeParseeFactory.createStringArrange()).result();
				
				if (mapids.length != probabilitys.length) {
					System.out.println(String.format("magicdoll_event_monster : 테이블 %s의 map_ids 와 spawn_probability 의 길이가 동일하지 않습니다.", info.get_item_name()));
					return false;
				}
				
				for (int i = 0; i < mapids.length; i++) {
					int mapid = Integer.parseInt(mapids[i]);
					if (pc.getMapId() == mapid) {
						if (LORnd.isWinning(1000000, Integer.parseInt(probabilitys[i]))) {
							L1NpcInstance mon = spawn(npc, info.get_spawn_npc(), info.get_delete_time());
							if (mon != null) {
								if (info.get_spawn_effect() > 0)
									npc.broadcastPacket(S_SkillSound.get(npc.getId(), info.get_spawn_effect()));

								if (info.get_spawn_ment() != null) {
									String broadcast_message = String.format(info.get_spawn_ment());
									L1World.getInstance().broadcastPacketToAll(new ServerBasePacket[]{
											new S_PacketBox(S_PacketBox.GREEN_MESSAGE, broadcast_message), new S_SystemMessage(broadcast_message),
									});
								}
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}
	
	public L1NpcInstance spawn(L1NpcInstance mon, int npcId, int deletetime) {
		try {
			mon.transform(npcId);	
			mon.getInventory().clearItems();
			DropTable.getInstance().setDrop(mon, mon.getInventory());
			mon.getInventory().shuffle();
			return mon;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
