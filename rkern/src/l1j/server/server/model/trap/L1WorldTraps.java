package l1j.server.server.model.trap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.HashMap;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.task.TaskLane;
import java.util.List;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.TrapTable;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TrapInstance;
import l1j.server.server.types.Point;
import l1j.server.server.utils.SQLUtil;

// 여기 타이머를 놔두는 이유는;; 자주 콜도 안되는 애인데다가, TimerTask쓰는게 편해서...다.-_-;;; by magik
public class L1WorldTraps {
	private static Logger _log = Logger.getLogger(L1WorldTraps.class.getName());

	private List<L1TrapInstance> _allTraps = new ArrayList<L1TrapInstance>(5120);
	private List<L1TrapInstance> _allBases = new ArrayList<L1TrapInstance>(260);

	private final HashMap<L1TrapInstance, TrapSpawnTimer> _pendingRespawns = new HashMap<>();
	private volatile boolean _stopped;

	private static volatile L1WorldTraps _instance;

	private L1WorldTraps() {
		initialize();
	}

	public static L1WorldTraps getInstance() {
		L1WorldTraps instance = _instance;
		if (instance == null) {
			synchronized (L1WorldTraps.class) {
				instance = _instance;
				if (instance == null) _instance = instance = new L1WorldTraps();
			}
		}
		return instance;
	}

	private TrapsComparator _comp = new TrapsComparator();
	private void initialize() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;

		try {
			con = DatabaseFactory.getInstance().getConnection();

			pstm = con.prepareStatement("SELECT * FROM spawnlist_trap");

			rs = pstm.executeQuery();
			L1TrapInstance trap = null;
			L1TrapInstance base = null;
			L1Trap trapTemp = null;
			L1Location loc = null;
			Point rndPt = null;
			while (rs.next()) {
				int trapId = rs.getInt("trapId");
				trapTemp = TrapTable.getInstance().getTemplate(trapId);
				loc = new L1Location();
				loc.setMap(rs.getInt("mapId"));
				loc.setX(rs.getInt("locX"));
				loc.setY(rs.getInt("locY"));
				rndPt = new Point();
				rndPt.setX(rs.getInt("locRndX"));
				rndPt.setY(rs.getInt("locRndY"));
				int count = rs.getInt("count");
				int span = rs.getInt("span");

				for (int i = 0; i < count; i++) {
					trap = new L1TrapInstance(IdFactory.getInstance().nextId(), trapTemp, loc, rndPt, span);
					L1World.getInstance().addVisibleObject(trap);
					_allTraps.add(trap);
				}
				base = new L1TrapInstance(IdFactory.getInstance().nextId(), loc);
				L1World.getInstance().addVisibleObject(base);
				_allBases.add(base);
			}
			_allTraps.sort(_comp);
			_allBases.sort(_comp);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public static synchronized void reloadTraps() {
		TrapTable.reload();
		L1WorldTraps oldInstance = _instance;
		L1WorldTraps replacement = new L1WorldTraps();
		if (oldInstance != null) {
			oldInstance.stopTimer();
			removeTraps(oldInstance._allTraps);
			removeTraps(oldInstance._allBases);
		}
		_instance = replacement;
	}

	private static void removeTraps(List<L1TrapInstance> traps) {
		for (L1TrapInstance trap : traps) {
			trap.disableTrap();
			L1World.getInstance().removeVisibleObject(trap);
		}
	}

	private void stopTimer() {
		synchronized (this) {
			_stopped = true;
			for (TrapSpawnTimer task : _pendingRespawns.values()) {
				if (task.future != null) task.future.cancel();
			}
			_pendingRespawns.clear();
		}
	}

	private void disableTrap(L1TrapInstance trap) {
		synchronized (this) {
			if (_stopped) return;
			final TrapSpawnTimer task = new TrapSpawnTimer(trap);
			final CServerTimer future = TaskLane.WORLD.schedule(task, trap.getSpan());
			if (future == null) {
				_log.warning("Trap respawn schedule rejected; leaving trap enabled: " + trap.getId());
				return;
			}
			task.future = future;
			TrapSpawnTimer previous = _pendingRespawns.put(trap, task);
			if (previous != null && previous.future != null) previous.future.cancel();
			trap.disableTrap();
		}
	}

	public synchronized void resetAllTraps() {
		if (_stopped) return;
		for (TrapSpawnTimer task : _pendingRespawns.values()) {
			if (task.future != null) task.future.cancel();
		}
		_pendingRespawns.clear();
		for (L1TrapInstance trap : _allTraps) {
			trap.resetLocation();
			trap.enableTrap();
		}
	}

	public void onPlayerMoved(L1PcInstance player) {
		int idx = Collections.binarySearch(_allTraps, player, _comp);
		if(idx < 0)
			return;
		
		L1TrapInstance t = _allTraps.get(idx);
		if(t.isEnable()){
			t.onTrod(player);
			disableTrap(t);
		}
		
		/*L1Location loc = player.getLocation();
		for (L1TrapInstance trap : _allTraps) {
			if (trap.isEnable() && loc.equals(trap.getLocation())) {
				trap.onTrod(player);
				disableTrap(trap);
			}
		}*/
	}

	public void onDetection(L1PcInstance caster) {
		int idx = Collections.binarySearch(_allTraps, caster, _comp);
		if(idx < 0)
			return;
		
		L1TrapInstance 	t 	= null;
		L1Location 		loc = caster.getLocation();
		for(int i=idx; i>=0; i--){
			t = _allTraps.get(i);
			if(t.isEnable() && loc.isInScreen(t.getLocation())){
				t.onDetection(caster);
				disableTrap(t);
			}
		}
		for(int i=idx+1; i<_allTraps.size(); i++){
			t = _allTraps.get(i);
			if(t.isEnable() && loc.isInScreen(t.getLocation())){
				t.onDetection(caster);
				disableTrap(t);
			}
		}
	}

	class TrapsComparator implements Comparator<L1Object>{
		@Override
		public int compare(L1Object o1, L1Object o2) {
			if(o1.getMapId() > o2.getMapId())
				return 1;
			else if(o1.getMapId() < o2.getMapId())
				return -1;
			else if(o1.getX() > o2.getX())
				return 1;
			else if(o1.getX() < o2.getX())
				return -1;
			else if(o1.getY() > o2.getY())
				return 1;
			else if(o1.getY() < o2.getY())
				return -1;
			return 0;
		}
		
	}
	
	private class TrapSpawnTimer implements Runnable {
		private final L1TrapInstance _targetTrap;
		private CServerTimer future;

		public TrapSpawnTimer(L1TrapInstance trap) {
			_targetTrap = trap;
		}

		@Override
		public void run() {
			synchronized (L1WorldTraps.this) {
				if (_stopped || _pendingRespawns.get(_targetTrap) != this) return;
				_pendingRespawns.remove(_targetTrap);
				_targetTrap.resetLocation();
				_targetTrap.enableTrap();
			}
		}
	}
	
	// 오림 인던 관련
	public synchronized void addTrap(L1TrapInstance trap) {
		_allTraps.add(trap);
	}

	 public synchronized void removeTrap(L1TrapInstance trap) {
		 for (Iterator<L1TrapInstance> i =_allTraps.listIterator(); i.hasNext();) {
			  L1TrapInstance traps = i.next();
			  if(traps.getId() == trap.getId()){
					  i.remove();
				  break;
			  }
		 }
		List<L1TrapInstance> traps = new ArrayList<L1TrapInstance>();
		traps.add(trap);
		removeTraps(traps);
	}
}
