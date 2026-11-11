package l1j.server.server.model.gametime;

public class GameTime extends BaseTime {
	// 2003년 7월 3일 12:00(UTC)이 1월 1일00:00
	protected static final long BASE_TIME_IN_MILLIS_REAL = 1214913600000L;

	@Override
	protected long getBaseTimeInMil() {
		return BASE_TIME_IN_MILLIS_REAL;
	}

	@Override
	protected int makeTime(long timeMillis) {
		long t1 = timeMillis - getBaseTimeInMil();
		if (t1 < 0) {
			throw new IllegalArgumentException();
		}
		int t2 = (int) ((t1 * 6) / 1000L);
		int t3 = t2 % 3; // 시간이 3의 배수가 되도록(듯이) 조정
		return t2 - t3;
	}
}
