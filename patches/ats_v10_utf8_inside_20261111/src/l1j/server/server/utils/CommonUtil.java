package l1j.server.server.utils;

import java.sql.Timestamp;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_ServerMessage;

public class CommonUtil {
	/**
	 * 2011.08.05 금액표시
	 * @param number
	 * @return
	 */
	public static String numberFormat(int number) {
		try {
			NumberFormat nf = NumberFormat.getInstance();

			return nf.format(number);
		} catch (Exception e) {
			return Integer.toString(number);
		}
	}

	/**
	 * 2011.08.05 랜덤함수
	 * @param number
	 * @return
	 */
	public static int random(int number) {
		Random rnd = new Random();
		return rnd.nextInt(number);
	}

	/**
	 * 2011.08.05 랜덤함수
	 * @param lbound
	 * @param ubound
	 * @return
	 */
	public static int randomRange(int lbound, int ubound) {
		return (int) ((Math.random() * (ubound - lbound + 1)) + lbound);
	}

	/**
	 * 2011.08.30 데이터포맷
	 * @param type
	 * @return
	 */
	public static String dateFormat(String type) {
		SimpleDateFormat sdf = new SimpleDateFormat(type, Locale.KOREA);
		return sdf.format(Calendar.getInstance().getTime());
	}

	/**
	 * 2011.08.30 데이터포맷
	 * @param type
	 * @return
	 */
	public static String dateFormat(String type, Timestamp date) {
		SimpleDateFormat sdf = new SimpleDateFormat(type, Locale.KOREA);
		return sdf.format(date.getTime());
	}

	/**
	 * 2011.08.31 지정시간까지 남은 시간
	 * @param item
	 */
	public static int getRestTime(int hh) {		
		int hour = Integer.parseInt(dateFormat("HH"));
		int minute = Integer.parseInt(dateFormat("mm"));

		int time = 0;

		time = (hh - hour) * 60 - minute;	

		return time;
	}
	/**
	 * 지정된 최대치 최소치 이상 넘어가지 않도록 설정 하기위함.
	 * @param i   현재 수치
	 * @param min 최소치
	 * @param max 최대치
	 * @return
	 */
	public static int get_current(int i, int min, int max) {
		int current = i;
		if (current <= min) {
			current = min;
		} else if (current >= max) {
			current = max;
		}
		return current;
	} 
	
	/**
	 * 텔레포터 상태 이상 체크 
	 * @param pc
	 * @param item
	 * @return
	 */
	public static boolean teleporCheck(L1PcInstance pc, ItemInstance item) {
        if (pc.isParalyzed() || pc.isSleeped() || pc.hasSkillEffect(SkillId.포그_오브_슬리핑)) {
			return true;
		} else if (pc.hasSkillEffect(SkillId.쇼크_스턴) || pc.hasSkillEffect(SkillId.아이스_랜스) 
				|| pc.hasSkillEffect(SkillId.어스_바인드) || pc.hasSkillEffect(SkillId.상태_동결)) {
			return true;
		} else if (pc.isGm()) {
			return false;
		}

		return false;
	}
}