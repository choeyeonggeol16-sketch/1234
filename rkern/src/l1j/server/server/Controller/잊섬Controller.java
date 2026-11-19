package l1j.server.server.Controller;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javolution.util.FastTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;

public class 잊섬Controller extends Thread {

	private static 잊섬Controller _instance;

	private boolean _잊섬Start;

	public boolean get잊섬Start() {
		return _잊섬Start;
	}

	public void set잊섬Start(boolean 잊섬) {
		_잊섬Start = 잊섬;
		_is배탑승 = _잊섬Start;
	}

	private static long sTime = 0;

	public boolean isGmOpen = false;

	public long openTime = 0;

	private static final SimpleDateFormat s = new SimpleDateFormat("HH", Locale.KOREA);

	private static final SimpleDateFormat ss = new SimpleDateFormat("MM-dd HH:mm", Locale.KOREA);

	public static 잊섬Controller getInstance() {
		if (_instance == null) {
			_instance = new 잊섬Controller();
			_instance.start();
		}
		return _instance;
	}

	private List<L1PcInstance> list = new FastTable<L1PcInstance>();

	public void add_list(L1PcInstance pc) {
		synchronized (list) {
			if (!list.contains(pc))
				list.add(pc);
		}
	}

	public void removeList(L1PcInstance pc) {
		if (!list.contains(pc))
			return;
		list.remove(pc);
	}

	public void end_pc_map() {
		try {
			Iterator<L1PcInstance> iter = list.iterator();
			L1PcInstance pc = null;

			while (iter.hasNext()) {
				pc = iter.next();
				if (pc == null || pc.isDead()) {
					continue;
				}
				if (pc.getMapId() >= 1708 && pc.getMapId() <= 1712) {// 2군대추가 L1텔레포트
					pc.start_teleport(33970, 33246, 4, 5, 169, true, false);
					iter.remove();
				} else {
					iter.remove();
				}
			}	
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		try {
			while (true) {
				Thread.sleep(1000);
				/**
				 * 잊섬이 닫힌 상태 인데 PC가 해당 맵에 존재 한다면 텔레포트
				 */
				if (list.size() > 0 && !get잊섬Start()) {
					end_pc_map();
				}

				/** 오픈 **/
				if (!isOpen() && !isGmOpen)
					continue;
				if (L1World.getInstance().getAllPlayers().size() <= 0)
					continue;

				isGmOpen = false;

				/** 오픈 메세지 **/
				L1World.getInstance().broadcastServerMessage("잠시후 잊혀진 섬 입장이 1시간 동안 가능 합니다.");

				/** 잊섬컨트롤러 시작 **/
				set잊섬Start(false);

				/** 실행 1시간 시작 **/
				// Thread.sleep(10000L); // 3800000L 테스트용
				Thread.sleep(1 * 60 * 60 * 1000); // 1시간 앞에 1이 1시간

				set잊섬Start(false);
				Thread.sleep(5000L);

				if (list.size() > 0) {
					end_pc_map();
				}
				L1World.getInstance().broadcastServerMessage("잠시후 잊혀진 섬 이용 시간이 만료 됩니다.");
				End();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 오픈 시각을 가져온다
	 *
	 * @return (Strind) 오픈 시각(MM-dd HH:mm)
	 */
	public String OpenTime() {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(sTime);
		return ss.format(c.getTime());
	}

	/**
	 * 맵이가 열려있는지 확인
	 *
	 *
	 * @return (boolean) 열려있다면 true 닫혀있다면 false
	 */

	public boolean isOpen() {
		Calendar calender = Calendar.getInstance();
		int hour, minute, secon;
		hour = calender.get(Calendar.HOUR_OF_DAY);
		minute = calender.get(Calendar.MINUTE);
		secon = calender.get(Calendar.SECOND);
		// -- 새벽 2 시 6시
		// -- 오전 11시 30분
		// -- 오후 2시 6시 10시
		if ((hour == 2 && minute == 00 && secon == 00) || (hour == 6 && minute == 00 && secon == 00)
				|| (hour == 11 && minute == 30 && secon == 00) || (hour == 14 && minute == 00 && secon == 00)
				|| (hour == 16 && minute == 00 && secon == 00) || (hour == 18 && minute == 00 && secon == 00)
				|| (hour == 20 && minute == 00 && secon == 00) || (hour == 22 && minute == 00 && secon == 00)
				|| (hour == 00 && minute == 00 && secon == 00)) {
			openTime = calender.getTime().getTime();
			return true;
		}
		return false;
	}

	private boolean _is배탑승;

	public void set배탑승(boolean a) {
		_is배탑승 = a;
	}

	public boolean is배탑승() {
		return _is배탑승;
	}

	/** 종료 **/
	private void End() {
		// delenpc(1231231);//엔피씨삭제
		set잊섬Start(false);
	}
}