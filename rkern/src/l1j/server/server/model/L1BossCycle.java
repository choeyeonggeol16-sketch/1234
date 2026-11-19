package l1j.server.server.model;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import l1j.server.server.utils.PerformanceTimer;

@XmlAccessorType(XmlAccessType.FIELD)
public class L1BossCycle {
	@XmlAttribute(name = "Name")
	private String _name;
	@XmlElement(name = "Base")
	private Base _base;

	@XmlAccessorType(XmlAccessType.FIELD)
	private static class Base {
		@XmlAttribute(name = "Date")
		private String _date;
		@XmlAttribute(name = "Time")
		private String _time;

		public String getDate() {
			return _date;
		}

		public void setDate(String date) {
			this._date = date;
		}

		public String getTime() {
			return _time;
		}

		public void setTime(String time) {
			this._time = time;
		}
	}

	@XmlElement(name = "Cycle")
	private Cycle _cycle;

	@XmlAccessorType(XmlAccessType.FIELD)
	private static class Cycle {
		@XmlAttribute(name = "Period")
		private String _period;
		@XmlAttribute(name = "Start")
		private String _start;
		@XmlAttribute(name = "End")
		private String _end;

		public String getPeriod() {
			return _period;
		}

		public void setPeriod(String period) {
			this._period = period;
		}

		public String getStart() {
			return _start;
		}

		public void setStart(String start) {
			_start = start;
		}

		public String getEnd() {
			return _end;
		}

		public void setEnd(String end) {
			_end = end;
		}
	}

	private static final Random _rnd = new Random();
	private Calendar _baseDate;
	private int _period; // 분 환산
	private int _periodDay;
	private int _periodHour;
	private int _periodMinute;

	private int _startTime; // 분 환산
	private int _endTime; // 분 환산
	private static SimpleDateFormat _sdfYmd = new SimpleDateFormat("yyyy/MM/dd");
	private static SimpleDateFormat _sdfTime = new SimpleDateFormat("HH:mm");
	private static SimpleDateFormat _sdf = new SimpleDateFormat(
			"yyyy/MM/dd HH:mm");
	private static Date _initDate = new Date();
	private static String _initTime = "0:00";
	private static final Calendar START_UP = Calendar.getInstance();

	public void init() throws Exception {
		// 기준일시의 설정
		Base base = getBase();
		// 기준이 없으면, 현재 일자의0:00기준
		if (base == null) {
			setBase(new Base());
			getBase().setDate(_sdfYmd.format(_initDate));
			getBase().setTime(_initTime);
			base = getBase();
		} else {
			try {
				_sdfYmd.parse(base.getDate());
			} catch (Exception e) {
				base.setDate(_sdfYmd.format(_initDate));
			}
			try {
				_sdfTime.parse(base.getTime());
			} catch (Exception e) {
				base.setTime(_initTime);
			}
		}
		// 기준일시를 결정
		Calendar baseCal = Calendar.getInstance();
		baseCal.setTime(_sdf.parse(base.getDate() + " " + base.getTime()));

		// 출현 주기의 초기화, 체크
		Cycle spawn = getCycle();
		if (spawn == null || spawn.getPeriod() == null) {
			throw new Exception("Cycle의 Period는 필수");
		}

		String period = spawn.getPeriod();
		_periodDay = getTimeParse(period, "d");
		_periodHour = getTimeParse(period, "h");
		_periodMinute = getTimeParse(period, "m");

		String start = spawn.getStart();
		int sDay = getTimeParse(start, "d");
		int sHour = getTimeParse(start, "h");
		int sMinute = getTimeParse(start, "m");
		String end = spawn.getEnd();
		int eDay = getTimeParse(end, "d");
		int eHour = getTimeParse(end, "h");
		int eMinute = getTimeParse(end, "m");

		// 분 환산
		_period = (_periodDay * 24 * 60) + (_periodHour * 60) + _periodMinute;
		_startTime = (sDay * 24 * 60) + (sHour * 60) + sMinute;
		_endTime = (eDay * 24 * 60) + (eHour * 60) + eMinute;
		if (_period <= 0) {
			throw new Exception("must be Period > 0");
		}
		// start 보정
		if (_startTime < 0 || _period < _startTime) { // 보정
			_startTime = 0;
		}
		// end 보정
		if (_endTime < 0 || _period < _endTime || end == null) { // 보정
			_endTime = _period;
		}
		if (_startTime > _endTime) {
			_startTime = _endTime;
		}
		// start, end의 상관 보정(최악이어도 1 분의간을 시작된다)
		// start==end라고 하는 지정에서도, 출현 시간이 다음의 주기에 입지 않게 하기 위한(해)
		if (_startTime == _endTime) {
			if (_endTime == _period) {
				_startTime--;
			} else {
				_endTime++;
			}
		}

		// 최근의 주기까지 보정(재계산할 경우에 엄밀하게 산출하므로, 여기에서는 근처까지 적당하게 보정할 뿐)
		while (!(baseCal.after(START_UP))) {
			baseCal.add(Calendar.DAY_OF_MONTH, _periodDay);
			baseCal.add(Calendar.HOUR_OF_DAY, _periodHour);
			baseCal.add(Calendar.MINUTE, _periodMinute);
		}
		_baseDate = baseCal;
	}

	/*
	 * 지정 일시를 포함한 주기(의 개시 시간)를 돌려준다
	 * ex. 주기가 2시간의 경우
	 *  target base 반환값
	 *   4:59  7:00 3:00
	 *   5:00  7:00 5:00
	 *   5:01  7:00 5:00
	 *   6:00  7:00 5:00
	 *   6:59  7:00 5:00
	 *   7:00  7:00 7:00
	 *   7:01  7:00 7:00
	 *   9:00  7:00 9:00
	 *   9:01  7:00 9:00
	 */
	private Calendar getBaseCycleOnTarget(Calendar target) {
		// 기준일시 취득
		Calendar base = (Calendar) _baseDate.clone();
		if (target.after(base)) {
			// target <= base가 될 때까지 반복한다
			while (target.after(base)) {
				base.add(Calendar.DAY_OF_MONTH, _periodDay);
				base.add(Calendar.HOUR_OF_DAY, _periodHour);
				base.add(Calendar.MINUTE, _periodMinute);
			}
		}
		if (target.before(base)) {
			while (target.before(base)) {
				base.add(Calendar.DAY_OF_MONTH, -_periodDay);
				base.add(Calendar.HOUR_OF_DAY, -_periodHour);
				base.add(Calendar.MINUTE, -_periodMinute);
			}
		}
		// 종료시간을 산출해 봐, 과거의 시각이라면 보스 시간이 지나고 있는→다음의 주기를 돌려준다.
		Calendar end = (Calendar) base.clone();
		end.add(Calendar.MINUTE, _endTime);
		if (end.before(target)) {
			base.add(Calendar.DAY_OF_MONTH, _periodDay);
			base.add(Calendar.HOUR_OF_DAY, _periodHour);
			base.add(Calendar.MINUTE, _periodMinute);
		}
		return base;
	}

	/**
	 * 지정 일시를 포함한 주기에 대해서, 출현 타이밍을 산출한다.
	 * @return 출현하는 시간
	 */
	public Calendar calcSpawnTime(Calendar now) {
		// 기준일시 취득
		Calendar base = getBaseCycleOnTarget(now);
		// 출현 기간의 계산
		base.add(Calendar.MINUTE, _startTime);
		// 출현 시간의 결정 start~end까지의 사이에 랜덤의 초
		int diff = (_endTime - _startTime) * 60;
		int random = diff > 0 ? _rnd.nextInt(diff) : 0;
		base.add(Calendar.SECOND, random);
		return base;
	}

	/**
	 * 지정 일시를 포함한 주기에 대해서, 출현 개시 시간을 산출한다.
	 * @return 주기의 출현 개시 시간
	 */
	public Calendar getSpawnStartTime(Calendar now) {
		// 기준일시 취득
		Calendar startDate = getBaseCycleOnTarget(now);
		// 출현 기간의 계산
		startDate.add(Calendar.MINUTE, _startTime);
		return startDate;
	}

	/**
	 * 지정 일시를 포함한 주기에 대해서, 출현 종료시간을 산출한다.
	 * @return 주기의 출현 종료시간
	 */
	public Calendar getSpawnEndTime(Calendar now) {
		// 기준일시 취득
		Calendar endDate = getBaseCycleOnTarget(now);
		// 출현 기간의 계산
		endDate.add(Calendar.MINUTE, _endTime);
		return endDate;
	}

	/**
	 * 지정 일시를 포함한 주기에 대해서, 다음의 주기의 출현 타이밍을 산출한다.
	 * @return 다음의 주기의 출현하는 시간
	 */
	public Calendar nextSpawnTime(Calendar now) {
		// 기준일시 취득
		Calendar next = (Calendar) now.clone();
		next.add(Calendar.DAY_OF_MONTH, _periodDay);
		next.add(Calendar.HOUR_OF_DAY, _periodHour);
		next.add(Calendar.MINUTE, _periodMinute);
		return calcSpawnTime(next);
	}

	/**
	 * 지정 일시에 대해서, 최근의 출현 개시 시간을 반환한다.
	 * @return 최근의 출현 개시 시간
	 */
	public Calendar getLatestStartTime(Calendar now) {
		// 기준일시 취득
		Calendar latestStart = getSpawnStartTime(now);
		if (!now.before(latestStart)) { // now >= latestStart
		} else {
			// now < latestStart라면 1개전이 최근의 주기
			latestStart.add(Calendar.DAY_OF_MONTH, -_periodDay);
			latestStart.add(Calendar.HOUR_OF_DAY, -_periodHour);
			latestStart.add(Calendar.MINUTE, -_periodMinute);
		}

		return latestStart;
	}

	private static int getTimeParse(String target, String search) {
		if (target == null) {
			return 0;
		}
		int n = 0;
		Matcher matcher = Pattern.compile("\\d+" + search).matcher(target);
		if (matcher.find()) {
			String match = matcher.group();
			n = Integer.parseInt(match.replace(search, ""));
		}
		return n;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "BossCycleList")
	static class L1BossCycleList {
		@XmlElement(name = "BossCycle")
		private List<L1BossCycle> bossCycles;

		public List<L1BossCycle> getBossCycles() {
			return bossCycles;
		}

		public void setBossCycles(List<L1BossCycle> bossCycles) {
			this.bossCycles = bossCycles;
		}
	}

	public static void load() {
		PerformanceTimer timer = new PerformanceTimer();
//		System.out.print("■ 보스몹스폰 데이터 .......................... ");
		try {
			// BookOrder 클래스를 바인딩 하는 문맥을 생성
			JAXBContext context = JAXBContext.newInstance(L1BossCycle.L1BossCycleList.class);

			// XML -> POJO 변환을 실시하는 안마샤라를 생성
			Unmarshaller um = context.createUnmarshaller();

			// XML -> POJO 변환
			File file = new File("./data/xml/Cycle/BossCycle.xml");
			L1BossCycleList bossList = (L1BossCycleList) um.unmarshal(file);

			for (L1BossCycle cycle : bossList.getBossCycles()) {
				cycle.init();
				_cycleMap.put(cycle.getName(), cycle);
			}

			// spawnlist_boss로부터 읽어들여 배치
		} catch (Exception e) {
			_log.log(Level.SEVERE, "BossCycle를 읽어들일 수 없었습니다", e);
			System.exit(0);
		}
//		System.out.println("■ 로딩 정상 완료 " + timer.get() + "ms");
	}

	/**
	 * 주기명과 지정 일시에 대한 출현 기간, 출현 시간을 콘솔 출력
	 * @param now 주기를 출력하는 일시
	 */
	public void showData(Calendar now) {
		System.out.println("[Type]" + getName());
		System.out.print("  [출현 기간]");
		System.out.print(_sdf.format(getSpawnStartTime(now).getTime()) + " - ");
		System.out.println(_sdf.format(getSpawnEndTime(now).getTime()));
	}

	private static HashMap<String, L1BossCycle> _cycleMap = new HashMap<String, L1BossCycle>();

	public static L1BossCycle getBossCycle(String type) {
		return _cycleMap.get(type);
	}

	public String getName() {
		return _name;
	}

	public void setName(String name) {
		this._name = name;
	}

	public Base getBase() {
		return _base;
	}

	public void setBase(Base base) {
		this._base = base;
	}

	public Cycle getCycle() {
		return _cycle;
	}

	public void setCycle(Cycle cycle) {
		this._cycle = cycle;
	}

	private static Logger _log = Logger.getLogger(L1BossCycle.class.getName());
}
