package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import javolution.util.FastTable;
import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.EventTimeTemp;

public class EventTimeTable2 {

	private static EventTimeTable2 instance;
	public static EventTimeTable2 getInstance() {
		if (instance == null) {
			instance = new EventTimeTable2();
		}
		return instance;
	}

	private EventTimeTable2() {
		store_event_time();
		store_npc();
	}

	public void reload() {
		EventTimeTable2 oladInstance = instance;
		instance = new EventTimeTable2();
		oladInstance.list.clear();
		oladInstance.npc_list.clear();
		oladInstance= null;
	}

	private ConcurrentHashMap<Integer, EventTimeTemp> list = new ConcurrentHashMap<Integer, EventTimeTemp>();

	private void store_event_time() {
		try (Connection con = DatabaseFactory.getInstance().getConnection();
				PreparedStatement pst = con.prepareStatement("SELECT * FROM spawnlist_boss_hot");
				ResultSet rst = pst.executeQuery();) {
			EventTimeTemp ett = null;
			while (rst.next()) {
				String days = rst.getString("spawn_yoil");
				if (days != null && days.length() > 0) {
					if (!dayCk(days))
						continue;
				}
				ett = new EventTimeTemp();
				ett.set_type(rst.getInt("type"));
				ett.set_npcid(rst.getInt("npcid"));
				ett.set_boss_message(rst.getString("boss_msg"));
				ett.set_loc_x(rst.getInt("loc_x"));
				ett.set_loc_y(rst.getInt("loc_y"));
				ett.set_loc_map(rst.getInt("loc_map"));
				ett.set_hour(rst.getInt("hour"));
				ett.set_minute(rst.getInt("minute"));
				list.put(ett.get_type(), ett);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean dayCk(String days) {
		StringTokenizer st = new StringTokenizer(days, ",");
		Calendar calendar = Calendar.getInstance();
		int nowDay = calendar.get(Calendar.DAY_OF_WEEK); // 1일요일 2월요일 3화요일 4수요일 5목요일 6금요일 7토요일
		while (st.hasMoreTokens()) {
			int ckDay = Integer.parseInt(st.nextToken().trim());
			if (nowDay == ckDay) {
				return true;
			}
		}
		return false;
	}

	private FastTable<L1NpcInstance> npc_list = new FastTable<L1NpcInstance>();

	private void store_npc() {
		try {
			Iterator<EventTimeTemp> iter = list.values().iterator();
			EventTimeTemp ett = null;
			L1NpcInstance npc = null;

			while (iter.hasNext()) {
				ett = iter.next();
				if (ett == null) {
					continue;
				}

				npc = NpcTable.getInstance().newNpcInstance(ett.get_npcid());

				if (npc == null) {
					continue;
				}
				
				long nowTime = get_current_time();
				long j = (get_current_time() - get_spawn_hour(ett.get_hour(), ett.get_minute()));
								
				if (j < 0) {
					j *= -1;
				} else if (j > 0) {
					j = 86400;
				}
				
				long time = nowTime + j;
				npc.setId(IdFactory.getInstance().nextId());
				npc.set_boss_type(ett.get_type());
				npc.set_boss_time(time);
				npc.set_boss_hour(ett.get_hour());
				npc.set_boss_minute(ett.get_minute());
				npc.set_boss_msg(ett.get_boss_message());
				npc.setX(ett.get_loc_x());
				npc.setHomeX(ett.get_loc_x());
				npc.setY(ett.get_loc_y());
				npc.setHomeY(ett.get_loc_y());
				npc.setMap((short) ett.get_loc_map());
				npc.setHeading(5);
				npc_list.add(npc);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//-- 현재 시간 구하기 
	@SuppressWarnings("deprecation")
	public long get_current_time() {
		Date date = new Date(System.currentTimeMillis());
		date.setHours(date.getHours());
		date.setMinutes(date.getMinutes());
		date.setSeconds(date.getSeconds());
		long time = date.getTime(); 
		return (time / 1000);
	}
	//-- 스폰 시킬 '시간' 구하기
	@SuppressWarnings("deprecation")
	public long get_spawn_hour(int hour, int minute) {
		Date date = new Date(System.currentTimeMillis());
		date.setHours(hour);
		date.setMinutes(minute);
		long time = date.getTime(); 
		return (time / 1000);
	}

	public Iterator<L1NpcInstance> get_npc_iter() {
		return npc_list.iterator();
	}

}