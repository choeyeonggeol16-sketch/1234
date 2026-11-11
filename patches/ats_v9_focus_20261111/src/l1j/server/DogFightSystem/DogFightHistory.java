package l1j.server.DogFightSystem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;

public class DogFightHistory {
	private static DogFightHistory _instance;
	public static DogFightHistory getInstance() {
		if(_instance == null)
			_instance = new DogFightHistory();
		return _instance;
	}
	public static void reload() {
		if(_instance != null) {
			_instance = new DogFightHistory();
		}
	}
	
	
	private HashMap<Integer, DogFight> _namelist;
	private DogFightHistory() {
		load();
	}
	
	private void load() {
		final HashMap<Integer, DogFight> bonus = new HashMap<Integer, DogFight>();
		Selector.exec("select * from gamble_dogfight_history", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					DogFight dInfo = DogFight.newInstance(rs);
					if(dInfo == null)
						continue;
					bonus.put(dInfo.getNum(), dInfo);
				}
			}
		});
		_namelist = bonus;
	}
	
	public DogFight getTemplate(int name) {
		return _namelist.get(Integer.valueOf(name));
	}
	
	public static class DogFight {
		static DogFight newInstance(ResultSet rs) throws SQLException {
			DogFight dInfo = newInstance();
			dInfo._num = rs.getInt("Num");
			dInfo._winCount = rs.getInt("WinCount");
			dInfo._loseCount = rs.getInt("LoseCount");
			return dInfo;
		}
		
		private static DogFight newInstance() {
			return new DogFight();
		}
		
		private int _num;
		private int _winCount;
		private int _loseCount;

		public int getNum() {
			return _num;
		}

		public void setNum(int num) {
			_num = num;
		}

		public int getWinCount() {
			return _winCount;
		}

		public void setWinCount(int winCount) {
			_winCount = winCount;
		}

		public int getLoseCount() {
			return _loseCount;
		}

		public void setLoseCount(int loseCount) {
			_loseCount = loseCount;
		}
	}
}
