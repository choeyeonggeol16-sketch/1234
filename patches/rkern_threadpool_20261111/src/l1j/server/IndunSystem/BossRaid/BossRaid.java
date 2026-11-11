package l1j.server.IndunSystem.BossRaid;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.teleport.L1Teleport;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.Config;

public class BossRaid implements Runnable {

	private static Logger _log = Logger.getLogger(BossRaid.class.getName());
	
	private int _map;
	
	private static Random _random = new Random(System.nanoTime());

	private boolean Running = false;
	
	public boolean Start = false;
	private boolean FirstRoom = false;
	private boolean End = false;
	
	private int Time = 1800;

	public BossRaid(int id){
		_map = id;
	}
	 
	int[] _MonsterList= { 
			Config.boss1
			, Config.boss2
			, Config.boss3
			, Config.boss4
			, Config.boss5
			, Config.boss6
			, Config.boss7
			, Config.boss8
			, Config.boss9
			, Config.boss10
			, Config.boss10
			, Config.boss10
			, Config.boss10
			, Config.boss9
			, Config.boss9
			, Config.boss8
			, Config.boss8
			, Config.boss7
			, Config.boss7
			, Config.boss6
	};

	@Override
	public void run() {
		Calendar cal = Calendar.getInstance();
		int 시간 = Calendar.HOUR;
		int 분 = Calendar.MINUTE;
		/** 0 오전 , 1 오후 * */
		String 오전오후 = "오후";
		if (cal.get(Calendar.AM_PM) == 0) {
			오전오후 = "오전";
		}
		Running = true;
		FirstRoom = true;
		Time = 1800;
		while(Running){
			try {
				Check();
				if (End) {
					reset();
					break;
				} else if (FirstRoom) {
					First();
				}
			}catch(Exception e){
			}finally{
				try{
					Thread.sleep(1000);
				} catch (Exception e) {
					_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
		}
		BossRaid_Delete();
	  System.out.println(""+ 오전오후 + " " + cal.get(시간) + "시" + cal.get(분) + "분" + "   ■■■■■■ 보스 레이드 종료 " +  _map+" ■■■■■■");
	}

	public void Start(){
		Calendar cal = Calendar.getInstance();
		int 시간 = Calendar.HOUR;
		int 분 = Calendar.MINUTE;
		/** 0 오전 , 1 오후 * */
		String 오전오후 = "오후";
		if (cal.get(Calendar.AM_PM) == 0) {
			오전오후 = "오전";
		}
		ThreadPool.getInstance().scheduleRequired(this, 2000);
	  System.out.println(""+ 오전오후 + " " + cal.get(시간) + "시" + cal.get(분) + "분" + "   ■■■■■■ 보스 레이드 시작 " +  _map+" ■■■■■■");
	}

	private void Check() {
		if (Time > 0) {
			Time--;
		}
		if (Time <= 0) {
			End();
		} else if (Time <= 1790) {
			CheckPc();
		} 
		if (Time % 60 == 0) {
			int min = Time / 60;
			for (L1Object obj : L1World.getInstance().getVisibleObjects(_map).values()) {
				if (obj instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) obj;
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, min+"분 후에 마을로 강제 이동 됩니다.");
				}
			}
		}
	}
	
	private void First() {
		if (_list1.size() > 0) {
			for (int i = _list1.size()-1; i >= 0; i--) {
				L1NpcInstance npc = _list1.get(i);
				if (npc.getCurrentHp() <= 0) {
					remove(npc, 1);
				}
			}
		} else {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			L1SpawnUtil.spawn2(32801, 32797, (short) _map, _MonsterList[_random.nextInt(_MonsterList.length - 1)], 0, 0, 0);//1라운드 보스.
			FirstRoom = false;
			setBossGo(false);
		}
	}

	private void BossRaid_Delete(){
		Collection<L1Object> cklist = L1World.getInstance().getVisibleObjects(_map).values();
		for(L1Object ob : cklist){
			if(ob == null)
				continue;
			if(ob instanceof L1ItemInstance){
				L1ItemInstance obj = (L1ItemInstance)ob;
				L1Inventory groundInventory = L1World.getInstance().getInventory(obj.getX(), obj.getY(), obj.getMapId());
				groundInventory.removeItem(obj);
			}else if(ob instanceof L1NpcInstance){
				L1NpcInstance npc = (L1NpcInstance)ob;
				npc.deleteMe();
			}
		}
		BossRaidSystem.getInstance().removeBossRaid(_map);
	}
	
	private void reset() {
		Running = false;
		ListClear(1);
		ListClear(2);
		for (L1Object obj : L1World.getInstance().getVisibleObjects(_map).values()) {
			if (obj instanceof L1MonsterInstance) {
				L1MonsterInstance mon = (L1MonsterInstance) obj;
				mon.deleteMe();
			}
		}
	}
	
	private void CheckPc() {
		int check = 0;
		for (L1Object obj : L1World.getInstance().getVisibleObjects(_map).values()) {
			if (obj instanceof L1PcInstance) {
				check = 1;
			}
		}
		if(check == 0){End();}
	}

	private void End() {
		for (L1Object obj : L1World.getInstance().getVisibleObjects(_map).values()) {
			if (obj instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) obj;

				pc.start_teleport(33436, 32807, 4, 5, 169, false, false);
			}
		}

		End = true;
	}
	
	private final ArrayList<L1NpcInstance> _list0 = new ArrayList<L1NpcInstance>();
	private final ArrayList<L1NpcInstance> _list1 = new ArrayList<L1NpcInstance>();
	
	public void add(L1NpcInstance npc, int type) {
		switch (type) {
		case 0: if (npc == null || _list0.contains(npc)) { return; }
		_list0.add(npc); break;
		case 1: if (npc == null || _list1.contains(npc)) { return; }
		_list1.add(npc); break;
		}
	}
	
	private void remove(L1NpcInstance npc, int type) {
		switch (type) {
		case 0: if (npc == null || !_list0.contains(npc)) { return; }
		_list0.remove(npc); break;
		case 1: if (npc == null || !_list1.contains(npc)) { return; }
		_list1.remove(npc); break;
		}
	}

	private void ListClear(int type) {
		switch (type) {
		case 0: _list0.clear(); break;
		case 1: _list1.clear(); break;
		}
	}
	
	private static boolean _bossgo = false;
	
	public static boolean getBossGo() {
		return _bossgo;
	}
	
	public static void setBossGo(boolean s) {
		_bossgo = s;
	}
}
