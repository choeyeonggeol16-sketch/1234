package l1j.server.WarSystem;

import java.sql.Timestamp;
import java.util.Calendar;

import l1j.server.commons;
import l1j.server.Templates.L1Item;
import l1j.server.WarSystem.WarFactory.WAR_TYPE;
import l1j.server.configure.Config;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1WarSpawn;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.CrownInstance;
import l1j.server.server.model.Instance.DoorInstance;
import l1j.server.server.model.Instance.FieldObjectInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.serverpackets.S_CastleMaster;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_WarStartMessage;
import l1j.server.server.serverpackets.ServerBasePacket;

public class CastleWar extends War {

	private static final ServerBasePacket[] _messages = new ServerBasePacket[] {
			new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "데포로듀: 자, 모두들! 곧 시작이다! 모두 물러서지 말아라!")
	};
	private static final ServerBasePacket[] _messages4ForCastle = new ServerBasePacket[] {
			new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "데포로쥬: 이번에 공격할 성은 켄트성이다. 수호성과 함께 성을 함락하라!"),
			new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "데포로쥬: 이번에 공격할 성은 오크성이다. 수호성과 함께 성을 함락하라!"),
			new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "데포로쥬: 이번에 공격할 성은 윈다우드성이다. 수호성과 함께 성을 함락하라!"),
			new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "데포로쥬: 이번에 공격할 성은 기란성이다. 수호성과 함께 성을 함락하라!"),
			new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "데포로쥬: 이번에 공격할 성은 하이네성이다. 수호성과 함께 성을 함락하라!"),
			new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "데포로쥬: 이번에 공격할 성은 지저성이다. 수호성과 함께 성을 함락하라!"),
			new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "데포로쥬: 이번에 공격할 성은 아덴성이다. 수호성과 함께 성을 함락하라!") };

	public static CastleWar newInstance(L1Clan defense, int id, int castleId, String castleName) {
		return new CastleWar(defense, id, castleId, castleName);
	}

	private int _castleId;
	private String _castleName;
	private Calendar _nextCal;
	private Calendar _readyCal;
	private Calendar _endCal;
	private Calendar _limitCal;
	private int _taxRate;
	private int _publicMoney;
	private int _security;
	private CastleWarEState _state;

	protected CastleWar(L1Clan defense, int id, int castleId, String castleName) {
		super(defense, WAR_TYPE.CASTLE, id);

		_state = CastleWarEState.IDLE;
		_castleId = castleId;
		_castleName = castleName;
	}
	
	public Calendar get_next_cal(){
		return _nextCal;
	}

	public int getCastleId() {
		return _castleId;
	}

	public String getCastleName() {
		return _castleName;
	}

	public Calendar nextCal() {
		return _nextCal;
	}

	public int getTaxRate() {
		return _taxRate;
	}

	public void setTaxRate(int i) {
		_taxRate = i;
	}

	public int getPublicMoney() {
		return _publicMoney;
	}

	public void setPublicMoney(int i) {
		_publicMoney = i;
	}

	public int getCastleSecurity() {
		return _security;
	}

	public void setCastleSecurity(int i) {
		_security = i;
	}

	public void setState(CastleWarEState state) {
		_state = state;
	}

	public boolean isIdle() {
		return _state.equals(CastleWarEState.IDLE);
	}

	public boolean isRun() {
		return _state.equals(CastleWarEState.RUN);
	}

	public boolean isReady() {
		return _state.equals(CastleWarEState.READY);
	}

	public boolean isClosing() {
		return _state.equals(CastleWarEState.CLOSING);
	}

	public int getSpareSeconds() {
		Calendar curCal = RealTimeClock.getInstance().getRealTimeCalendar();
		int result = (int) ((_endCal.getTimeInMillis() - curCal.getTimeInMillis()) / 1000L);
		if (result > 0)
			return result;
		return 0;
	}

	public void nextCalendar(Timestamp ts) {
		Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
		cal.setTimeInMillis(ts.getTime());
		nextCalendar(cal);
	}

	public void nextCalendar(Calendar cal) {
		disposesCalendar();
		_nextCal = cal;
		_readyCal = (Calendar) _nextCal.clone();
		_readyCal.add(Calendar.MINUTE, -5);
		_endCal = (Calendar) _nextCal.clone();
		_endCal.add(Config.ALT_WAR_TIME_UNIT, Config.ALT_WAR_TIME);
		_limitCal = (Calendar) _endCal.clone();
	}

	public void updateTime(int increaseMinute) {
		Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
		Calendar tmp = (Calendar) cal.clone();
		tmp.add(Calendar.MINUTE, increaseMinute);
		if (tmp.getTimeInMillis() > _limitCal.getTimeInMillis()) {
			_endCal = (Calendar) _limitCal.clone();
			tmp.clear();
		} else {
			_endCal = tmp;
		}
	}

	@Override
	public void updateDefense(L1Clan nextDefense) {
		L1Clan oldDefense = _defense;
		_defense.setCastleId(0);
		super.updateDefense(nextDefense);
		_defense.setCastleId(_castleId);
		updateTime(20);
		CastleWarBusiness.getInstance().updateCastle(_castleId);
		final ServerBasePacket oncrown = new S_CastleMaster(_castleId, nextDefense.getLeaderId());
		final ServerBasePacket message = new S_ServerMessage(643);
		try {
			final L1Location loc = createGetBackLocation();
			for(L1PcInstance pc : L1World.getInstance().getAllPlayers()){
				if(pc == null)
					continue;
				
				L1Clan clan = pc.getClan();
				if ((clan == null || clan.getCastleId() != _castleId)) {
					if (L1CastleLocation.checkInWarArea(_castleId, pc) && !pc.isGm()) {
						L1Location nloc = loc.randomLocation(15, true);
						teleport(pc, nloc.getX(), nloc.getY(), nloc.getMapId());
					}
					offLordBuff(pc);
				} else {
					pc.sendPackets(message, false);
				}
				pc.war_zone = false;
				CastleWarBusiness.move(pc);
				pc.sendPackets(oncrown, false);
			}
			GeneralThreadPool.getInstance().schedule(new Runnable(){
				@Override
				public void run(){
					try{
						for(L1PcInstance pc : L1World.getInstance().getAllPlayers()){
							if(pc == null)
								continue;
							
							L1Clan clan = pc.getClan();
							if ((clan == null || clan.getCastleId() != _castleId)) {
								if (L1CastleLocation.checkInWarArea(_castleId, pc) && !pc.isGm()) {
									L1Location nloc = loc.randomLocation(15, true);
									pc.teleport(nloc.getX(), nloc.getY(), (short) nloc.getMapId(), pc.getHeading(), 169, true, false);
								}
							}
						}
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			}, 1000L);
		} catch (Exception e) {
			e.printStackTrace();
		}
		oncrown.clear();
		message.clear();
		deleteTower();
		spawnTower();
		spawnDoor();
		ClanTable.getInstance().updateClan(nextDefense);
		if (oldDefense != null)
			ClanTable.getInstance().updateClan(oldDefense);

		GeneralThreadPool.getInstance().execute(new Runnable() {
			@Override
			public void run() {
				// 수성혈 표기 패킷 넣기
			}
		});
	}

	@Override
	public void dispose() {
		super.dispose();
		disposesCalendar();
	}

	private void disposesCalendar() {
		if (_nextCal != null) {
			_nextCal.clear();
			_nextCal = null;
		}
		if (_readyCal != null) {
			_readyCal.clear();
			_readyCal = null;
		}
		if (_endCal != null) {
			_endCal.clear();
			_endCal = null;
		}
		if (_limitCal != null) {
			_limitCal.clear();
			_limitCal = null;
		}
	}

	public boolean isRunTime(Calendar cal) {
		return !isRun() && cal.after(_nextCal) && (cal.before(_endCal) || cal.before(_limitCal));
	}

	public boolean isReadyTime(Calendar cal) {
		return isIdle() && cal.after(_readyCal) && cal.before(_nextCal);
	}

	public boolean isClosingTime(Calendar cal) {
		return isRun() && (cal.after(_limitCal) || cal.after(_endCal));
	}

	public void ready() {
		if (!isReady()) {
			setState(CastleWarEState.READY);
			//GeneralThreadPool.getInstance().execute(new WarReadier());
		}
	}

	public void run() {
		if (!isRun()) {
			setState(CastleWarEState.RUN);
			GeneralThreadPool.getInstance().execute(new WarOpener());
		}
	}

	public void close() {
		if (!isClosing() && isRun()) {
			setState(CastleWarEState.CLOSING);
			GeneralThreadPool.getInstance().execute(new WarCloser());
		}
	}

	private void teleport(L1PcInstance pc, int x, int y, int mid) {
		if(commons.isNonAction(pc)) {
			pc.teleport(x, y, (short) mid, pc.getHeading(),169, false, false);
		}
			
		pc.teleport(x, y, (short) mid, pc.getHeading(),169, true, false);
	}

	public CastleWar proclaim(L1PcInstance pc) {
		if (!isRun())
			return null;

		L1Clan clan = null;
		clan = pc.getClan();
		if (War.isOffenseClan(clan)) {
			pc.sendPackets("전쟁: 이미 전쟁 중");
			return null;
		}

		register(clan);
		
		for (L1PcInstance member : clan.getOnlineClanMember()) {
			if (member != null && member.getNetConnection() != null){
				if (L1CastleLocation.checkInWarArea(_castleId, member)){
					// 공성 혈 패킷 넣기
					onLordBuff(member);
					final L1Location loc = createGetBackLocation();
					L1Location nloc = loc.randomLocation(15, true);
					teleport(member, nloc.getX(), nloc.getY(), nloc.getMapId());
				}
			}
		}
		return this;
	}

	public void onLordBuff(L1PcInstance pc) {
		// 주군의 버프 켜기 삽입 3.1 엔 없음 혹시 모를 추가 버프 주려면 삽입.
	}

	public void offLordBuff(L1PcInstance pc) {
		// 주군의 버프 끄기 삽입3.1 엔 없음 혹시 모를 추가 버프 주려면 삽입.
	}

	public void exit() {
		setState(CastleWarEState.IDLE);
		initializeOffenses();

		final L1Location loc = createGetBackLocation();
		L1World.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> pc != null)
				.forEach((L1PcInstance pc) -> {
					try {
						L1Clan clan = pc.getClan();
						if ((clan == null || clan.getCastleId() != _castleId)) {
							if (L1CastleLocation.checkInWarArea(_castleId, pc) && !pc.isGm()) {
								L1Location nloc = loc.randomLocation(15, true);
								teleport(pc, nloc.getX(), nloc.getY(), nloc.getMapId());
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
	}

	private void spawnTower() {
		L1WarSpawn wsp = new L1WarSpawn();
		wsp.SpawnTower(_castleId);
	}

	private void spawnDoor() {
		for (DoorInstance door : DoorSpawnTable.getInstance().getDoorList()) {
			if (L1CastleLocation.checkInWarArea(_castleId, door)) {
				door.repairGate();
			}
		}
	}

	private void spawns() {
		L1WarSpawn wsp = new L1WarSpawn();
		wsp.SpawnFlag(_castleId);
		spawnDoor();
	}

	private void deleteTower() {
		L1TowerInstance tower = null;
		for (L1Object l1object : L1World.getInstance().getObject()) {
			if (l1object instanceof L1TowerInstance) {
				tower = (L1TowerInstance) l1object;
				if (L1CastleLocation.checkInWarArea(_castleId, tower)) {
					tower.deleteMe();
				}
			}
		}
	}

	public L1Location createGetBackLocation() {
		int[] arr = L1CastleLocation.getGetBackLoc(_castleId);
		return new L1Location(arr[0], arr[1], arr[2]);
	}

	class WarReadier implements Runnable {
		@Override
		public void run() {
			int idx = -1;
			while (isReady()) {
				if (idx++ == -1) {
					L1World.getInstance().broadcastPacketToAll(_messages4ForCastle[_castleId - 1], false);
				} else if (idx >= _messages.length) {
					idx = -1;
				} else {
					L1World.getInstance().broadcastPacketToAll(_messages[idx], false);
				}
				try {
					Thread.sleep(30000L);
				} catch (Exception e) {
				}
			}
		}
	}

	class WarOpener implements Runnable {
		@Override
		public void run() {
			try {
				spawns();
				if (_security == 1)
					onSecurity();
				selectMode();
				doStartup();
				L1World.getInstance().broadcastPacketToAll(
						new S_SystemMessage(String.format("현재 진행중인 공성: \'%s\'", _castleName)), true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		private void onSecurity() {
			if (_castleId >= 1 && _castleId <= 4) {
				final int[] loc = L1CastleLocation.getGetBackLoc(_castleId);
				final int defenseId = _defenseId;
				L1World.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> {
					if (pc == null || pc.isGm() || pc.getClanid() == defenseId)
						return false;
					int mapId = pc.getMapId();
					return (mapId == 52 || mapId == 248 || mapId == 249 || mapId == 250 || mapId == 251)
							|| pc.getClanid() <= 0;
				}).forEach((L1PcInstance pc) -> {
					teleport(pc, loc[0], loc[1], loc[2]);
				});
			}
			setCastleSecurity(0);
			CastleWarBusiness.getInstance().updateCastle(_castleId);
			CharacterTable.getInstance().updateLoc(_castleId, 52, 248, 249, 250, 251);
		}

		private void selectMode() {
			L1World.getInstance().broadcastPacketToAll(new ServerBasePacket[] {
					new S_PacketBox(S_PacketBox.GREEN_MESSAGE, 6298), S_WarStartMessage.get() });
		}

		private void doStartup() {
			int[] loc = L1CastleLocation.getGetBackLoc(_castleId);
			L1World.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> pc != null)
					.forEach((L1PcInstance pc) -> {
						try {
							if (L1CastleLocation.checkInWarArea(_castleId, pc)) {
								L1Clan clan = pc.getClan();
								if (clan != null) {
									if (clan.getCastleId() == _castleId) {
										CastleWarBusiness.move(pc);
										// 공성 시작 패킷 삽입
										onLordBuff(pc);
									} else {
										teleport(pc, loc[0], loc[1], loc[2]);
									}
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
		}
	}

	class WarCloser implements Runnable {
		@Override
		public void run() {
			try {
				Calendar cal = (Calendar) _nextCal.clone();
				cal.add(Config.ALT_WAR_INTERVAL_UNIT, Config.ALT_WAR_INTERVAL);
				nextCalendar(cal);
				_taxRate = Config.ALT_RATE_OF_DUTY;
				CastleWarBusiness.getInstance().updateCastle(_castleId);

				L1World.getInstance().createVisibleObjectsStream(L1CastleLocation.getCastleByMapId(_castleId))
						.filter((L1Object obj) -> obj != null).forEach((L1Object obj) -> {
							if (obj instanceof L1PcInstance) {
								L1PcInstance pc = (L1PcInstance) obj;
								CastleWarBusiness.move(pc);
								offLordBuff(pc);
							} else if (obj instanceof FieldObjectInstance || obj instanceof CrownInstance
									|| obj instanceof L1TowerInstance) {
								L1NpcInstance npc = (L1NpcInstance) obj;
								if (L1CastleLocation.checkInWarArea(_castleId, npc))
									npc.deleteMe();
							}
						});
				notifyTheEnd();
				spawnTower();
				spawnDoor();
				rewardDefense();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				exit();
			}
		}

		private void rewardDefense() {		
			int reward_item = 40308;
			int reward_count = 10000000;
			L1Item temp = ItemTable.getInstance().getTemplate(reward_item);
			if (temp != null) {
				ServerBasePacket pck = new S_SystemMessage(String.format("공성전 승리 : %s(%d) 아이템을 받았습니다.", temp.getName(), reward_count));
				try {
					L1World.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> {
						return !(pc == null);
					}).forEach((L1PcInstance pc) -> {
						L1Clan clan = pc.getClan();
						if (clan != null && clan.getCastleId() == _castleId) {
							pc.getInventory().storeItem(reward_item, reward_count);
							pc.sendPackets(pck, false);
						}
					});
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					if (pck != null)
						pck.clear();
				}
			} else {
				ServerBasePacket pck = new S_SystemMessage("공성전에 승리 하였습니다!");
				try {
					L1World.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> {
						return !(pc == null);
					}).forEach((L1PcInstance pc) -> {
						L1Clan clan = pc.getClan();
						if (clan != null && clan.getCastleId() == _castleId) {
							pc.sendPackets(pck, false);
						}
					});
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					if (pck != null)
						pck.clear();
				}
			}
		}
	}

	public void notifyTheEnd() {
		try {
			_warLock.lock();
			for (L1Clan clan : _offenses.values()) {
				broadcastEndWar(_defense, clan);
				clan.setCurrentWar(null);
			}
			_offenses.clear();
		} finally {
			_warLock.unlock();
		}
	}
}
