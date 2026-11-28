package l1j.server.server.model.world.siege;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.network.core.sc.packet.SC_War;
import l1j.server.server.model.world.siege.WarFactory.WAR_TYPE;

public class War {
	public static War newInstance(L1Clan defense, WAR_TYPE warType, int warId){
		War war = new War(defense, warType, warId);
		return war;
	}
	
	public static boolean isSameWar(L1Clan c1, L1Clan c2){
		if(c1 == null || c2 == null)
			return false;
		
		War w1 = c1.getCurrentWar();
		War w2 = c2.getCurrentWar();
		if(w1 == null || w2 == null)
			return false;
		
		return w1.equals(w2);
	}
	
	public static boolean isNowWar(L1Clan clan){
		return clan != null && clan.getCurrentWar() != null;
	}
	
	public static boolean isOffenseClan(L1Clan clan){
		if(clan == null)
			return false;
		
		War war = clan.getCurrentWar();
		if(war == null)
			return false;
		return war.getOffenseClan(clan.getClanId()) != null;
	}
	
	public static boolean isCastleOffenseClan(L1Clan clan){
		if(clan == null)
			return false;
		
		War war = clan.getCurrentWar();
		if(war == null || !war.getWarType().equals(WAR_TYPE.CASTLE))
			return false;
		
		return war.getOffenseClan(clan.getClanId()) != null;
	}
	
	protected final int					_warId;
	protected final HashMap<Integer, L1Clan> _offenses;
	protected volatile L1Clan _defense;
	protected volatile int				_defenseId;
	protected final WAR_TYPE			_warType;
	protected final Lock				_warLock;
	protected volatile boolean disposed;
	protected War(L1Clan defense, WAR_TYPE warType, int id){
		this(warType, id);
		publish(defense);
	}

	protected War(WAR_TYPE warType, int id) {
		_warType = warType;
		_offenses = new HashMap<Integer, L1Clan>(8);
		_warLock = new ReentrantLock();
		_warId = id;
	}

	protected final void publish(L1Clan defense) {
		setDefenseClan(defense);
		CWorld.getInstance().addWar(this);
	}
	
	public int getId(){
		return _warId;
	}
	
	public WAR_TYPE getWarType(){
		return _warType;
	}
	
	public void updateDefense(L1Clan nextDefense){
		try{
			_warLock.lock();
			if (disposed || nextDefense == null) return;
			L1Clan previous = _defense;
			List<L1Clan> offenses = clearOffenses();
			if (previous != null) previous.clearCurrentWar(this);
			setDefenseClan(nextDefense);
			if (previous == null) return;
			if (_warType == WAR_TYPE.CASTLE) {
				broadcastWinner(nextDefense, previous);
				for (L1Clan clan : offenses) broadcastEndWar(previous, clan);
			} else {
				notifyWinner(nextDefense, previous);
				for (L1Clan clan : offenses) notifyEndWar(previous, clan);
			}
		}finally{
			_warLock.unlock();
		}
	}
	
	public boolean register(L1Clan offense) {
		return register(offense, offense == null ? null : offense.getCurrentWar());
	}

	protected final boolean register(L1Clan offense, War previous) {
		final L1Clan defense;
		_warLock.lock();
		try {
			if (disposed || offense == null || _offenses.containsKey(offense.getClanId())) return false;
			if (_warType == WAR_TYPE.CASTLE) {
				if (!offense.compareAndSetCurrentWar(previous, this)) return false;
			} else {
				offense.setCurrentWar(this);
			}
			_offenses.put(offense.getClanId(), offense);
			defense = _defense;
		} finally {
			_warLock.unlock();
		}
		if (defense != null) {
			if (_warType == WAR_TYPE.CASTLE) broadcastDeclaration(defense, offense);
			else notifyDeclaration(defense, offense);
		}
		return true;
	}
	
	public void dispose(){
		try{
			_warLock.lock();
			if (disposed) return;
			disposed = true;
			CWorld.getInstance().removeWar(this);
			if (_defense != null) _defense.clearCurrentWar(this);
			setDefenseClan(null);
			clearOffenses();
		}finally{
			_warLock.unlock();
		}
	}
	
	public void broadcastDeclaration(L1Clan defense, L1Clan offense){
		broadcast(1, offense, defense);
	}
	
	public void broadcastSurrender(L1Clan winner, L1Clan loser){
		broadcast(2, loser, winner);
	}
	
	public void broadcastEndWar(L1Clan defense, L1Clan offense){
		broadcast(3, defense, offense);
	}
	
	public void broadcastWinner(L1Clan winner, L1Clan loser){
		broadcast(4, winner, loser);
	}
	
	public void broadcast(int type, L1Clan c1, L1Clan c2){
		SC_War.broadcastAll(type, c1.getClanName(), c2.getClanName());
	}
	
	public void notifyDeclaration(L1Clan defense, L1Clan offense){
		notify(1, offense, defense);
	}
	
	public void notifySurrender(L1Clan winner, L1Clan loser){
		notify(2, loser, winner);
	}
	
	public void notifyEndWar(L1Clan defense, L1Clan offense){
		notify(3, defense, offense);
	}
	
	public void notifyWinner(L1Clan winner, L1Clan loser){
		notify(4, winner, loser);
	}
	
	public void notify(int type, L1Clan c1, L1Clan c2){
		SC_War.broadcastClan(c1, 1, c1.getClanName(), c2.getClanName());
		SC_War.broadcastClan(c2, 1, c1.getClanName(), c2.getClanName());
	}
	
	public void notifyEnemies(L1PcInstance pc){
		int clanId = pc.getClanid();
		if (clanId <= 0) return;
		final L1Clan defense;
		final List<L1Clan> offenses;
		_warLock.lock();
		try {
			defense = _defense;
			if (defense == null || _offenses.isEmpty()) return;
			offenses = List.copyOf(_offenses.values());
		} finally {
			_warLock.unlock();
		}
		String clanName = pc.getClanname();
		if (defense.getClanId() != clanId) SC_War.send(pc, 8, clanName, defense.getClanName());
		for (L1Clan enemy : offenses) {
			if (enemy.getClanId() != clanId) SC_War.send(pc, 8, clanName, enemy.getClanName());
		}
	}

	/** Caller holds the war lock. */
	protected List<L1Clan> clearOffenses() {
		List<L1Clan> offenses = List.copyOf(_offenses.values());
		for (L1Clan clan : offenses) clan.clearCurrentWar(this);
		_offenses.clear();
		return offenses;
	}
	
	public L1Clan getOffenseClan(int id){
		L1Clan c = null;
		try{
			_warLock.lock();
			c = _offenses.get(id);
		}finally{
			_warLock.unlock();
		}
		return c;
	}
	
	public void removeOffenseClan(int id){
		try{
			_warLock.lock();
			L1Clan clan = _offenses.remove(id);
			if(clan != null)
				clan.clearCurrentWar(this);
		}finally{
			_warLock.unlock();
		}
	}
	
	public void removeOffenseClan(L1Clan c){
		removeOffenseClan(c.getClanId());
	}
	
	public L1Clan getDefenseClan(){
		return _defense;
	}
	
	private void setDefenseClan(L1Clan clan) {
		_defense = clan;
		if (clan != null) {
			_defenseId = clan.getClanId();
			clan.setCurrentWar(this);
		} else {
			_defenseId = 0; 
		}
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(256);
		sb.append("[War] Id : ").append(getId()).append("\n");
		_warLock.lock();
		try {
			sb.append("\nDefense : ").append(_defense == null ? "" : _defense.getClanName());
			for (L1Clan clan : _offenses.values()) sb.append("\nOffense : ").append(clan.getClanName());
		} finally {
			_warLock.unlock();
		}
		return sb.toString();
	}
	
	public boolean equals(War war){
		return getId() == war.getId();
	}
}
