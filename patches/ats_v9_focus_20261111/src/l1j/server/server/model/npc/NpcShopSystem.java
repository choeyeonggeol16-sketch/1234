package l1j.server.server.model.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import l1j.server.Templates.L1NpcShop;
import l1j.server.server.ActionCodes;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcShopSpawnTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.serverpackets.S_DoActionShop;

public class NpcShopSystem {

	private static NpcShopSystem _instance;

	private Map<Integer, Boolean> _power = new HashMap<Integer, Boolean>();

	private int _type = -1;

	public static NpcShopSystem getInstance() {
		if (_instance == null) {
			_instance = new NpcShopSystem();
		}
		return _instance;
	}

	public NpcShopSystem() {
		_power.put(-1, false);
	}

	private static ArrayList<L1NpcInstance> _shops = new ArrayList<L1NpcInstance>(20);
	
	 

	static class NpcShopTimer implements Runnable {

		private NpcShopSystem nss;

		public NpcShopTimer(NpcShopSystem nss) {
			this.nss = nss;
		}

		public void run() {
			try {
				ArrayList<L1NpcShop> list = NpcShopSpawnTable.getInstance().getList();
				for (int i = 0; i < list.size(); i++) {
					L1NpcShop shop = list.get(i);
					if (nss._type != -1 && nss._type != shop.getType())
						continue;
					L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(shop.getNpcId());
					npc.setId(IdFactory.getInstance().nextId());
					npc.setMap(shop.getMapId());
					npc.getLocation().set(shop.getX(), shop.getY(), shop.getMapId());
					npc.getLocation().forward(5);
					npc.setHomeX(npc.getX());
					npc.setHomeY(npc.getY());
					npc.setHeading(shop.getHeading());
					npc.setName(shop.getName());
					npc.setTitle(shop.getTitle());
					L1NpcShopInstance obj = (L1NpcShopInstance) npc;
					obj.setShopName(shop.getShopName());
					L1World.getInstance().storeObject(npc);
					L1World.getInstance().addVisibleObject(npc);
					npc.getLight().turnOnOffLight();
					Thread.sleep(30);
					obj.setState(1);
					Broadcaster.broadcastPacket(npc, new S_DoActionShop(npc.getId(), ActionCodes.ACTION_Shop, shop.getShopName().getBytes()));

					_shops.add(npc);
					Thread.sleep(10);
				}
			} catch (Exception exception) {
				return;
			} finally {
			}
		}
	}

	public void npcShopStart() {
		NpcShopTimer ns = new NpcShopTimer(this);
		GeneralThreadPool.getInstance().execute(ns);
		_power.put(-1, true);
	}

	public void npcShopStart(int type) {
		NpcShopTimer ns = new NpcShopTimer(this);
		GeneralThreadPool.getInstance().execute(ns);
		_type = type;
		_power.put(type, true);
	}

	public void npcShopStop(int type) {
		_type = type;
		_power.put(type, false);
		ArrayList<L1NpcShop> list = NpcShopSpawnTable.getInstance().getList();
		if (list != null) {
			L1NpcShop[] shop = list.toArray(new L1NpcShop[list.size()]);
			for (int i = 0; i < shop.length; i++) {
				if (shop[i] == null || (shop[i].getType() != -1 && shop[i].getType() != type))
					continue;
				L1NpcInstance npc = L1World.getInstance().findNpc(shop[i].getNpcId());
				if (npc == null)
					continue;
				npc.deleteMe();
			}
		}
	}

	public void npcShopStop() {
		_type = -1;
		for (Integer type : _power.keySet()) {
			_power.put(type, false);
		}
		int size = _shops.size();
		for (int i = 0; i < size; i++) {
			L1NpcInstance npc = _shops.get(i);
			if (npc == null)
				continue;
			npc.deleteMe();
		}
		_shops.clear();
	}

	public boolean isPower() {
		// 지정상점 켜진상태에서는 단계상점 안켜지게 해야됨.
		for (Boolean is : _power.values())
			if (is) {
				return true;
			}
		return false;
	}

	public boolean isPower(int type) {
		// 전체 상점이 켜진상태에서는 지정상점 못켜지게 해야됨.
		if (_power.get(-1))
			return true;
		//
		return _power.containsKey(type) ? _power.get(type) : false;
	}
	

}
