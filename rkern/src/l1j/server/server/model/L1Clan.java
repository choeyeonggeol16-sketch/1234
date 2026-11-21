//package l1j.server.server.model;
//
//import java.sql.Timestamp;
//import java.util.ArrayList;
//import java.util.concurrent.CopyOnWriteArrayList;
//import java.util.logging.Logger;
//import java.util.stream.Stream;
//
//import javolution.util.FastTable;
//import l1j.server.Config;
//import l1j.server.server.shared.MJProto.IO.ProtoOutputStream;
//import l1j.server.server.model.world.siege.War;
//import l1j.server.server.datatables.ClanTable;
//import l1j.server.server.model.Instance.L1PcInstance;
//import l1j.server.server.model.Warehouse.ClanWarehouse;
//import l1j.server.server.model.Warehouse.WarehouseManager;
//import l1j.server.server.serverpackets.ServerBasePacket;
//import l1j.server.server.utils.IntRange;
//
//public class L1Clan {
//	static public class ClanMember {
//		public String name;
//		public int rank;
//		public int level;
//		public String notes;
//		public int memberId;
//		public int type;
//		public boolean online;
//		public L1PcInstance player;
//
//		public ClanMember(String name, int rank, int level, String notes, int memberId, int type, boolean online,
//				L1PcInstance pc) {
//			this.name = name;
//			this.rank = rank;
//			this.level = level;
//			this.notes = notes;
//			this.memberId = memberId;
//			this.type = type;
//			this.online = online;
//			this.player = pc;
//		}
//	}
//
//	public static final int CLAN_RANK_LEAGUE_PUBLIC = 2;
//	public static final int CLAN_RANK_LEAGUE_PRINCE = 4;
//	public static final int CLAN_RANK_LEAGUE_PROBATION = 5;
//	public static final int CLAN_RANK_LEAGUE_GUARDIAN = 6;
//
//	public static final int 부군주 = 3;
//	public static final int 수련 = 7;
//	public static final int 일반 = 8;
//	public static final int 수호 = 9;
//	public static final int 군주 = 10;
//	public static final int 정예 = 13;
//	private int _WarPoint;
//
//	@SuppressWarnings("unused")
//	private static final Logger _log = Logger.getLogger(L1Clan.class.getName());
//
//	private int _clanId;
//
//	private String _clanName;
//
//	private int _leaderId;
//
//	private String _leaderName;
//
//	private int _castleId;
//
//	private int _inCastleId = -1;
//
//	private int _houseId;
//
//	private int _alliance;
//
//	private Timestamp _clanBirthday;
//
//	private int _maxuser;
//	private int _curuser;
//
//	private int _emblemId = 0;
//
//	private int _emblemStatus = 0;
//
//	// private int _clan_exp; // 클랜경험치
//
//	// 혈맹가입 설정
//	private int _join_setting;
//	private int _join_type;
//
//	// public int getClanExp() { return _clan_exp; } // 클랜경험치
//	// public synchronized void setClanExp(int clanexp) { _clan_exp = clanexp; }
//	// // 클랜경험치
//	// public synchronized void addClanExp(int clanexp) { _clan_exp += clanexp;
//	// } // 클랜경험치
//
//	public String getAnnouncement() {
//		if (_clanName.equalsIgnoreCase(Config.신규혈맹이름))
//			_announcement = ClanTable.CLAN_TUTORIAL_ANN;
//		return _announcement;
//	}
//
//	public void setAnnouncement(String announcement) {
//		this._announcement = announcement;
//	}
//
//	private String _announcement;
//
//	public int getEmblemId() {
//		if (_clanName.equalsIgnoreCase(Config.신규혈맹이름))
//			_emblemId = ClanTable.CLAN_TUTORIAL_EMB;
//		return _emblemId;
//	}
//
//	public void setEmblemId(int emblemId) {
//		this._emblemId = emblemId;
//	}
//
//	public int getEmblemStatus() {
//		if (_clanName.equalsIgnoreCase(Config.신규혈맹이름))
//			_emblemStatus = 1;
//		return _emblemStatus;
//	}
//
//	public void setEmblemStatus(int emblemStatus) {
//		this._emblemStatus = emblemStatus;
//	}
//
//	/** 혈맹자동가입 */
//	private boolean _bot;
//	private int _bot_style;
//	private int _bot_level;
//	/** 혈맹자동가입 */
//	private CopyOnWriteArrayList<ClanMember> clanMemberList = new CopyOnWriteArrayList<ClanMember>();
//
//	public CopyOnWriteArrayList<ClanMember> getClanMemberList() {
//		return clanMemberList;
//	}
//
//	public void addClanMember(String name, int rank, int level, String notes, int memberid, int type, int online,
//			L1PcInstance pc) {
//		clanMemberList
//				.add(new ClanMember(name, rank, level, notes, memberid, type, online == 1, online == 1 ? pc : null));
//		// System.out.println(clanMemberList.size());
//		ClanTable.updateOnlineUser(this);
//
//		/*
//		 * if(pc != null && pc.getAI() != null && pc.getAI().getBotType() ==
//		 * MJBotType.HUNT){ int id = getLeaderId(); L1Object o =
//		 * L1World.getInstance().findObject(id); if(o != null && o instanceof
//		 * L1Character){ L1Character c = (L1Character)o;
//		 * if(c.getAI().getWarCastle() != -1)
//		 * pc.getAI().setWarCastle(c.getAI().getWarCastle()); } }
//		 */
//	}
//
//	public void addClanMember(ClanMember cm) {
//		clanMemberList.add(cm);
//		ClanTable.updateOnlineUser(this);
//	}
//
//	public void removeClanMember(String name) {
//		for (int i = 0; i < clanMemberList.size(); i++) {
//			if (clanMemberList.get(i).name.equals(name)) {
//				deleteClanRetrieveUser(clanMemberList.get(i).memberId);
//				clanMemberList.remove(i);
//				break;
//			}
//		}
//		ClanTable.updateOnlineUser(this);
//	}
//
//	/////////// 혈맹리뉴얼//////////////
//	public void setClanRank(String name, int data) {
//		for (int i = 0; i < clanMemberList.size(); i++) {
//			if (clanMemberList.get(i).name.equals(name)) {
//				clanMemberList.get(i).rank = data;
//				break;
//			}
//		}
//	}
//
//	/////////// 혈맹리뉴얼//////////////
//	public int getOnlineMaxUser() {
//		return _maxuser;
//	}
//
//	public void setOnlineMaxUser(int i) {
//		_maxuser = i;
//	}
//
//	public int getCurrentUser() {
//		return _curuser;
//	}
//
//	public void setCurrentUser(int i) {
//		_curuser = i;
//	}
//
//	// 실시간 변경
//	public void UpdataClanMember(String name, int rank) {
//		for (int i = 0; i < clanMemberList.size(); i++) {
//			if (clanMemberList.get(i).name.equals(name)) {
//				clanMemberList.get(i).rank = rank;
//				break;
//			}
//		}
//	}
//
//	public void updateClanMemberOnline(L1PcInstance pc) {
//		for (ClanMember clan : clanMemberList) {
//			if (clan.memberId != pc.getId())
//				continue;
//
//			clan.online = pc.getOnlineStatus() == 1;
//			clan.player = pc;
//			break;
//		}
//		ClanTable.updateOnlineUser(this);
//	}
//
//	public int getCurrentOnlineMemebers() {
//		int cnt = 0;
//		for (ClanMember clan : clanMemberList) {
//			if (clan.player == null)
//				clan.online = false;
//			else
//				clan.online = clan.player.getOnlineStatus() == 1;
//			if (clan.online)
//				cnt++;
//		}
//		return cnt;
//	}
//
//	public String[] getAllMembersName() {
//		ArrayList<String> members = new ArrayList<String>();
//		ClanMember member;
//		for (int i = 0; i < clanMemberList.size(); i++) {
//			member = clanMemberList.get(i);
//			if (!members.contains(member.name)) {
//				members.add(member.name);
//			}
//		}
//		return members.toArray(new String[members.size()]);
//	}
//
//	public Timestamp getClanBirthDay() {
//		return _clanBirthday;
//	}
//
//	public void setClanBirthDay(Timestamp t) {
//		_clanBirthday = t;
//	}
//
//	public int getClanId() {
//		return _clanId;
//	}
//
//	public void setClanId(int clan_id) {
//		_clanId = clan_id;
//	}
//
//	public String getClanName() {
//		return _clanName;
//	}
//
//	public void setClanName(String clan_name) {
//		_clanName = clan_name;
//	}
//
//	public int getLeaderId() {
//		return _leaderId;
//	}
//
//	public void setLeaderId(int leader_id) {
//		_leaderId = leader_id;
//	}
//
//	public String getLeaderName() {
//		return _leaderName;
//	}
//
//	public void setLeaderName(String leader_name) {
//		_leaderName = leader_name;
//	}
//
//	public int getCastleId() {
//		return _castleId;
//	}
//
//	public void setCastleId(int hasCastle) {
//		_castleId = hasCastle;
//	}
//
//	public int getInCastleId() {
//		return _inCastleId;
//	}
//
//	public void setInCastleId(int i) {
//		_inCastleId = i;
//	}
//
//	public int getHouseId() {
//		return _houseId;
//	}
//
//	public void setHouseId(int hasHideout) {
//		_houseId = hasHideout;
//	}
//
//	public int getAlliance() {
//		return _alliance;
//	}
//
//	public void setAlliance(int alliance) {
//		_alliance = alliance;
//	}
//
//	// 온라인중의 혈원수
//	public int getOnlineMemberCount() {
//		int count = 0;
//		for (int i = 0; i < clanMemberList.size(); i++) {
//			if (L1World.getInstance().getPlayer(clanMemberList.get(i).name) != null) {
//				count++;
//			}
//		}
//		return count;
//	}
//
//	public L1PcInstance[] getOnlineClanMember() {
//		ArrayList<L1PcInstance> onlineMembers = new ArrayList<L1PcInstance>(clanMemberList.size());
//		L1PcInstance pc = null;
//		for (int i = 0; i < clanMemberList.size(); i++) {
//			pc = L1World.getInstance().getPlayer(clanMemberList.get(i).name);
//			if (pc != null && !onlineMembers.contains(pc)) {
//				onlineMembers.add(pc);
//			}
//		}
//		return onlineMembers.toArray(new L1PcInstance[onlineMembers.size()]);
//	}
//
//	// 전체 혈원 네임 리스트
//	public String getAllMembersFP() {
//		String result = "";
//		String rank = "";
//		for (int i = 0; i < clanMemberList.size(); i++) {
//			result = result + clanMemberList.get(i).name + rank + " ";
//		}
//		return result;
//	}
//
//	// 온라인중의 혈원 네임 리스트
//	public String getOnlineMembersFP() {
//		String result = "";
//		String rank = "";
//		L1PcInstance pc = null;
//		for (int i = 0; i < clanMemberList.size(); i++) {
//			pc = L1World.getInstance().getPlayer(clanMemberList.get(i).name);
//			if (pc != null) {
//				result = result + clanMemberList.get(i).name + rank + " ";
//			}
//		}
//		return result;
//	}
//
//	private int _underDungeon = 0;
//	private int _rankTime;
//	private Timestamp _rankDate;
//	private int _underMapid = 0;
//
//	public int getUnderDungeon() {
//		return _underDungeon;
//	}
//
//	public void setUnderDungeon(int i) {
//		_underDungeon = i;
//	}
//
//	public int getRankTime() {
//		return _rankTime;
//	}
//
//	public void setRankTime(int i) {
//		_rankTime = i;
//	}
//
//	public Timestamp getRankDate() {
//		return _rankDate;
//	}
//
//	public void setRankDate(Timestamp t) {
//		_rankDate = t;
//	}
//
//	public int getUnderMapid() {
//		return _underMapid;
//	}
//
//	public void setUnderMapid(int i) {
//		_underMapid = i;
//	}
//
//	/** 혈맹자동가입 */
//	public boolean isBot() {
//		return _bot;
//	}
//
//	public void setBot(boolean _bot) {
//		this._bot = _bot;
//	}
//
//	public int getBotStyle() {
//		return _bot_style;
//	}
//
//	public void setBotStyle(int _bot_style) {
//		this._bot_style = _bot_style;
//	}
//
//	public int getBotLevel() {
//		return _bot_level;
//	}
//
//	public void setBotLevel(int _bot_level) {
//		this._bot_level = _bot_level;
//	}
//
//	/** 혈맹자동가입 */
//	// 문장주시 목록
//	private FastTable<String> GazeList = new FastTable<String>();
//
//	// 문장주시 추가
//	public void addGazelist(String name) {
//		if (GazeList.contains(name)) {
//			return;
//		}
//		GazeList.add(name);
//	}
//
//	// 문장주시 삭제
//	public void removeGazelist(String name) {
//		if (!GazeList.contains(name)) {
//			return;
//		}
//		GazeList.remove(name);
//	}
//
//	// 문장주시 사이즈
//	public int getGazeSize() {
//		return GazeList.size();
//	}
//
//	// 주시 리스트 반환
//	public FastTable<String> getGazeList() {
//		return GazeList;
//	}
//
//	public L1PcInstance getonline간부() {
//		L1PcInstance pc = null;
//		L1PcInstance no1pc = null;
//		int oldrank = 0;
//		for (int i = 0; i < clanMemberList.size(); i++) {
//			if (clanMemberList.get(i) == null)
//				continue;
//			if (!clanMemberList.get(i).online || clanMemberList.get(i).player == null)
//				continue;
//			pc = clanMemberList.get(i).player;
//			if (pc.getClanRank() >= L1Clan.수호) {
//				if (oldrank < pc.getClanRank()) {
//					oldrank = pc.getClanRank();
//					no1pc = pc;
//				}
//			}
//		}
//		return no1pc;
//	}
//
//	public int getJoinSetting() {
//		return _join_setting;
//	}
//
//	public void setJoinSetting(int i) {
//		_join_setting = i;
//	}
//
//	public int getJoinType() {
//		return _join_type;
//	}
//
//	public void setJoinType(int i) {
//		_join_type = i;
//	}
//
//	/** 혈맹버프 포인트 **/
//	private int _bless = 0;
//	private int _blesscount = 0;
//	private int _attack = 0;
//	private int _defence = 0;
//	private int _pvpattack = 0;
//	private int _pvpdefence = 0;
//	public int[] getBuffTime = new int[] { _attack, _defence, _pvpattack, _pvpdefence };
//
//	public int[] getBuffTime() {
//		return getBuffTime;
//	}
//
//	public void setBuffTime(int i, int j) {
//		getBuffTime[i] = IntRange.ensure(j, 0, 172800);
//	}
//
//	public void setBuffTime(int a, int b, int c, int d) {
//		getBuffTime = new int[] { a, b, c, d };
//	}
//
//	public int getBlessCount() {
//		return _blesscount;
//	}
//
//	public void setBlessCount(int i) {
//		_blesscount = IntRange.ensure(i, 0, 400000000);
//	}
//
//	public void addBlessCount(int i) {
//		_blesscount += i;
//		if (_blesscount > 400000000)
//			_blesscount = 400000000;
//		else if (_blesscount < 0)
//			_blesscount = 0;
//	}
//
//	public int getBless() {
//		return _bless;
//	}
//
//	public void setBless(int i) {
//		_bless = i;
//	}
//
//	/** 2016.11.25 server 앱센터 혈맹 **/
//	private String _joinPassword;
//
//	public String getJoinPassword() {
//		return _joinPassword;
//	}
//
//	public void setJoinPassword(String s) {
//		_joinPassword = s;
//	}
//
//	/** 2016.11.25 server 앱센터 혈맹 **/
//
//	private int _warPoint;
//
//	public int getWarPoint() {
//		return _warPoint;
//	}
//
//	public void setWarPoint(int i) {
//		_warPoint = i;
//	}
//
//	public void incWarPoint() {
//		_warPoint++;
//		ClanTable.updateWarPoint(this);
//	}
//
//	public boolean decWarPoint() {
//		if (_warPoint <= 0)
//			return false;
//		_warPoint--;
//		ClanTable.updateWarPoint(this);
//		return true;
//	}
//
//	public Stream<ClanMember> createMembersStream() {
//		return clanMemberList.size() > 100 ? clanMemberList.parallelStream() : clanMemberList.stream();
//	}
//
//	public Stream<ClanMember> createOnlineMembers() {
//		return createMembersStream().filter((ClanMember m) -> m.online && m.player != null);
//	}
//
//	public void broadcast(ServerBasePacket pck) {
//		broadcast(pck, true);
//	}
//
//	public void broadcast(ServerBasePacket pck, boolean isClear) {
//		Stream<ClanMember> stream = createOnlineMembers();
//		if (stream != null) {
//			stream.forEach((ClanMember m) -> {
//				if (m.player != null)
//					m.player.sendPackets(pck, false);
//			});
//		}
//		if (isClear)
//			pck.clear();
//	}
//
//	public void broadcast(ProtoOutputStream output) {
//		broadcast(output, true);
//	}
//
//	public void broadcast(ProtoOutputStream output, boolean isClear) {
//		Stream<ClanMember> stream = createOnlineMembers();
//		if (stream != null) {
//			stream.forEach((ClanMember m) -> {
//				if (m.player != null)
//					m.player.sendPackets(output, false);
//			});
//		}
//		if (isClear)
//			output.dispose();
//	}
//
//	private War _currentWar;
//
//	public void setCurrentWar(War war) {
//		_currentWar = war;
//	}
//
//	public War getCurrentWar() {
//		return _currentWar;
//	}
//
//	private boolean _isRedKnight = false;
//
//	public boolean isRedKnight() {
//		return _isRedKnight;
//	}
//
//	public void setRedKnight(boolean b) {
//		_isRedKnight = b;
//	}
//
//	public void outOfWarArea(int castleId) {
//		final int[] loc = L1CastleLocation.getGetBackLoc(castleId);
//		createMembersStream().filter((ClanMember member) -> {
//			return member.player != null && L1CastleLocation.checkInWarArea(castleId, member.player);
//		}).forEach((ClanMember member) -> {
//			member.player.start_teleport(loc[0], loc[1], (short) loc[2], 5, 169, true, false);
//		});
//	}
//
//	//TODO 혈맹 버프 리뉴얼 2017-11-12
//	private int _BuffFirst = 0;
//	private int _BuffSecond = 0;
//	private int _BuffThird = 0;
//	private int _EinhasadBlessBuff = 0;
//
//	public int getBuffFirst() {
//		return _BuffFirst;
//	}
//
//	public void setBuffFirst(int i) {
//		_BuffFirst = i;
//	}
//
//	public int getBuffSecond() {
//		return _BuffSecond;
//	}
//
//	public void setBuffSecond(int i) {
//		_BuffSecond = i;
//	}
//
//	public int getBuffThird() {
//		return _BuffThird;
//	}
//
//	public void setBuffThird(int i) {
//		_BuffThird = i;
//	}
//	
//	public int getEinhasadBlessBuff() {
//		return _EinhasadBlessBuff;
//	}
//
//	public void setEinhasadBlessBuff(int i) {
//		_EinhasadBlessBuff = i;
//	}
//
//	public void deleteClanRetrieveUser(int targetObjectId){
//		ClanWarehouse clanWarehouse = WarehouseManager.getInstance().getClanWarehouse(getClanName());
//		if(clanWarehouse != null && clanWarehouse.getWarehouseUsingChar() == targetObjectId)
//			clanWarehouse.setWarehouseUsingChar(0, 0);	
//	}
//	
//}
package l1j.server.server.model;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javolution.util.FastTable;

import l1j.server.Config;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.ClanWarehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.world.siege.War;
import l1j.server.server.utils.IntRange;

public class L1Clan {
	static public class ClanMember {
		public String name;
		public int rank;
		public int level;
		public String notes;
		public int memberId;
		public int type;
		public boolean online;
		public L1PcInstance player;

		public ClanMember(String name, int rank, int level, String notes, int memberId, int type, boolean online,
				L1PcInstance pc) {
			this.name = name;
			this.rank = rank;
			this.level = level;
			this.notes = notes;
			this.memberId = memberId;
			this.type = type;
			this.online = online;
			this.player = pc;
		}
	}

	public static final int CLAN_RANK_LEAGUE_PUBLIC = 2;
	public static final int CLAN_RANK_LEAGUE_PRINCE = 4;
	public static final int CLAN_RANK_LEAGUE_PROBATION = 5;
	public static final int CLAN_RANK_LEAGUE_GUARDIAN = 6;

	public static final int 부군주 = 3;
	public static final int 수련 = 7;
	public static final int 일반 = 8;
	public static final int 수호 = 9;
	public static final int 군주 = 10;
	public static final int 정예 = 13;
	private int _WarPoint;

	@SuppressWarnings("unused")
	private static final Logger _log = Logger.getLogger(L1Clan.class.getName());

	private int _clanId;

	private String _clanName;

	private int _leaderId;

	private String _leaderName;

	private int _castleId;

	private int _inCastleId = -1;

	private int _houseId;

	private int _alliance;

	private Timestamp _clanBirthday;

	private int _maxuser;
	private int _curuser;

	private int _emblemId = 0;

	private int _emblemStatus = 0;

	// private int _clan_exp; // 클랜경험치

	// 혈맹가입 설정
	private int _join_setting;
	private int _join_type;

	// public int getClanExp() { return _clan_exp; } // 클랜경험치
	// public synchronized void setClanExp(int clanexp) { _clan_exp = clanexp; }
	// // 클랜경험치
	// public synchronized void addClanExp(int clanexp) { _clan_exp += clanexp;
	// } // 클랜경험치

	public String getAnnouncement() {
		if (_clanName.equalsIgnoreCase(Config.신규혈맹이름))
			_announcement = ClanTable.CLAN_TUTORIAL_ANN;
		return _announcement;
	}

	public void setAnnouncement(String announcement) {
		this._announcement = announcement;
	}

	private String _announcement;

	public int getEmblemId() {
		String name = _clanName;
		if (name == null) {
			return 0;
		}
		
		int id = _emblemId;
		if (name.equalsIgnoreCase(Config.신규혈맹이름)) {
			id = ClanTable.CLAN_TUTORIAL_EMB;
		}
		
		if (id != 0) {
			//-- 서버팩 폴더 안에 emblem 폴더 안에 해당 번호가 존재 하는지 체크 
			//-- 있다면 해당 엠블럼 호출값 주고, 없다면 0처리.
			File file = new File(String.format("emblem/%s", id));
			return file.exists() ? id : 0;
		} else {
			return 0;
		}
		/*
		if (_clanName.equalsIgnoreCase(Config.신규혈맹이름)) {
			_emblemId = ClanTable.CLAN_TUTORIAL_EMB;
		}
		return _emblemId;
		*/
	}

	public void setEmblemId(int emblemId) {
		this._emblemId = emblemId;
	}

	public int getEmblemStatus() {
		if (_clanName.equalsIgnoreCase(Config.신규혈맹이름))
			_emblemStatus = 1;
		return _emblemStatus;
	}

	public void setEmblemStatus(int emblemStatus) {
		this._emblemStatus = emblemStatus;
	}

	/** 혈맹자동가입 */
	private boolean _bot;
	private int _bot_style;
	private int _bot_level;
	/** 혈맹자동가입 */
	private CopyOnWriteArrayList<ClanMember> clanMemberList = new CopyOnWriteArrayList<ClanMember>();

	public CopyOnWriteArrayList<ClanMember> getClanMemberList() {
		return clanMemberList;
	}

	public void addClanMember(String name, int rank, int level, String notes, int memberid, int type, int online,
			L1PcInstance pc) {
		clanMemberList
				.add(new ClanMember(name, rank, level, notes, memberid, type, online == 1, online == 1 ? pc : null));
		// System.out.println(clanMemberList.size());
		ClanTable.updateOnlineUser(this);

		/*
		 * if(pc != null && pc.getAI() != null && pc.getAI().getBotType() ==
		 * MJBotType.HUNT){ int id = getLeaderId(); L1Object o =
		 * L1World.getInstance().findObject(id); if(o != null && o instanceof
		 * L1Character){ L1Character c = (L1Character)o;
		 * if(c.getAI().getWarCastle() != -1)
		 * pc.getAI().setWarCastle(c.getAI().getWarCastle()); } }
		 */
	}
    /** Only for a not-yet-published clan during a full DB roster load.
     * Updating clan_data for every row read would turn a read-only reload
     * into N asynchronous writes, including when the reload later fails.
     */
    public void addLoadedClanMember(String name, int rank, int level, String notes,
                                    int memberId, int type) {
        clanMemberList.add(new ClanMember(name, rank, level, notes, memberId, type, false, null));
    }


	public void addClanMember(ClanMember cm) {
		clanMemberList.add(cm);
		ClanTable.updateOnlineUser(this);
	}

	public void removeClanMember(String name) {
		for (int i = 0; i < clanMemberList.size(); i++) {
			if (clanMemberList.get(i).name.equals(name)) {
				deleteClanRetrieveUser(clanMemberList.get(i).memberId);
				clanMemberList.remove(i);
				break;
			}
		}
		ClanTable.updateOnlineUser(this);
	}

	/////////// 혈맹리뉴얼//////////////
	public void setClanRank(String name, int data) {
		for (int i = 0; i < clanMemberList.size(); i++) {
			if (clanMemberList.get(i).name.equals(name)) {
				clanMemberList.get(i).rank = data;
				break;
			}
		}
	}

	/////////// 혈맹리뉴얼//////////////
	public int getOnlineMaxUser() {
		return _maxuser;
	}

	public void setOnlineMaxUser(int i) {
		_maxuser = i;
	}

	public int getCurrentUser() {
		return _curuser;
	}

	public void setCurrentUser(int i) {
		_curuser = i;
	}

	// 실시간 변경
	public void UpdataClanMember(String name, int rank) {
		for (int i = 0; i < clanMemberList.size(); i++) {
			if (clanMemberList.get(i).name.equals(name)) {
				clanMemberList.get(i).rank = rank;
				break;
			}
		}
	}

	public void updateClanMemberOnline(L1PcInstance pc) {
		for (ClanMember clan : clanMemberList) {
			if (clan.memberId != pc.getId())
				continue;

			clan.online = pc.getOnlineStatus() == 1;
			clan.player = pc;
			break;
		}
		ClanTable.updateOnlineUser(this);
	}

	public int getCurrentOnlineMemebers() {
		int cnt = 0;
		for (ClanMember clan : clanMemberList) {
			if (clan.player == null)
				clan.online = false;
			else
				clan.online = clan.player.getOnlineStatus() == 1;
			if (clan.online)
				cnt++;
		}
		return cnt;
	}

	public String[] getAllMembersName() {
		ArrayList<String> members = new ArrayList<String>();
		ClanMember member;
		for (int i = 0; i < clanMemberList.size(); i++) {
			member = clanMemberList.get(i);
			if (!members.contains(member.name)) {
				members.add(member.name);
			}
		}
		return members.toArray(new String[members.size()]);
	}

	public Timestamp getClanBirthDay() {
		return _clanBirthday;
	}

	public void setClanBirthDay(Timestamp t) {
		_clanBirthday = t;
	}

	public int getClanId() {
		return _clanId;
	}

	public void setClanId(int clan_id) {
		_clanId = clan_id;
	}

	public String getClanName() {
		return _clanName;
	}

	public void setClanName(String clan_name) {
		_clanName = clan_name;
	}

	public int getLeaderId() {
		return _leaderId;
	}

	public void setLeaderId(int leader_id) {
		_leaderId = leader_id;
	}

	public String getLeaderName() {
		return _leaderName;
	}

	public void setLeaderName(String leader_name) {
		_leaderName = leader_name;
	}

	public int getCastleId() {
		return _castleId;
	}

	public void setCastleId(int hasCastle) {
		_castleId = hasCastle;
	}

	public int getInCastleId() {
		return _inCastleId;
	}

	public void setInCastleId(int i) {
		_inCastleId = i;
	}

	public int getHouseId() {
		return _houseId;
	}

	public void setHouseId(int hasHideout) {
		_houseId = hasHideout;
	}

	public int getAlliance() {
		return _alliance;
	}

	public void setAlliance(int alliance) {
		_alliance = alliance;
	}

	// 온라인중의 혈원수
	public int getOnlineMemberCount() {
		int count = 0;
		for (int i = 0; i < clanMemberList.size(); i++) {
			if (L1World.getInstance().getPlayer(clanMemberList.get(i).name) != null) {
				count++;
			}
		}
		return count;
	}

	public L1PcInstance[] getOnlineClanMember() {
		ArrayList<L1PcInstance> onlineMembers = new ArrayList<L1PcInstance>(clanMemberList.size());
		L1PcInstance pc = null;
		for (int i = 0; i < clanMemberList.size(); i++) {
			pc = L1World.getInstance().getPlayer(clanMemberList.get(i).name);
			if (pc != null && !onlineMembers.contains(pc)) {
				onlineMembers.add(pc);
			}
		}
		return onlineMembers.toArray(new L1PcInstance[onlineMembers.size()]);
	}

	// 전체 혈원 네임 리스트
	public String getAllMembersFP() {
		String result = "";
		String rank = "";
		for (int i = 0; i < clanMemberList.size(); i++) {
			result = result + clanMemberList.get(i).name + rank + " ";
		}
		return result;
	}

	// 온라인중의 혈원 네임 리스트
	public String getOnlineMembersFP() {
		String result = "";
		String rank = "";
		L1PcInstance pc = null;
		for (int i = 0; i < clanMemberList.size(); i++) {
			pc = L1World.getInstance().getPlayer(clanMemberList.get(i).name);
			if (pc != null) {
				result = result + clanMemberList.get(i).name + rank + " ";
			}
		}
		return result;
	}

	private int _underDungeon = 0;
	private int _rankTime;
	private Timestamp _rankDate;
	private int _underMapid = 0;

	public int getUnderDungeon() {
		return _underDungeon;
	}

	public void setUnderDungeon(int i) {
		_underDungeon = i;
	}

	public int getRankTime() {
		return _rankTime;
	}

	public void setRankTime(int i) {
		_rankTime = i;
	}

	public Timestamp getRankDate() {
		return _rankDate;
	}

	public void setRankDate(Timestamp t) {
		_rankDate = t;
	}

	public int getUnderMapid() {
		return _underMapid;
	}

	public void setUnderMapid(int i) {
		_underMapid = i;
	}

	/** 혈맹자동가입 */
	public boolean isBot() {
		return _bot;
	}

	public void setBot(boolean _bot) {
		this._bot = _bot;
	}

	public int getBotStyle() {
		return _bot_style;
	}

	public void setBotStyle(int _bot_style) {
		this._bot_style = _bot_style;
	}

	public int getBotLevel() {
		return _bot_level;
	}

	public void setBotLevel(int _bot_level) {
		this._bot_level = _bot_level;
	}

	/** 혈맹자동가입 */
	// 문장주시 목록
	private FastTable<String> GazeList = new FastTable<String>();

	// 문장주시 추가
	public void addGazelist(String name) {
		if (GazeList.contains(name)) {
			return;
		}
		GazeList.add(name);
	}

	// 문장주시 삭제
	public void removeGazelist(String name) {
		if (!GazeList.contains(name)) {
			return;
		}
		GazeList.remove(name);
	}

	// 문장주시 사이즈
	public int getGazeSize() {
		return GazeList.size();
	}

	// 주시 리스트 반환
	public FastTable<String> getGazeList() {
		return GazeList;
	}

	public L1PcInstance getonline간부() {
		L1PcInstance pc = null;
		L1PcInstance no1pc = null;
		int oldrank = 0;
		for (int i = 0; i < clanMemberList.size(); i++) {
			if (clanMemberList.get(i) == null)
				continue;
			if (!clanMemberList.get(i).online || clanMemberList.get(i).player == null)
				continue;
			pc = clanMemberList.get(i).player;
			if (pc.getClanRank() >= L1Clan.수호) {
				if (oldrank < pc.getClanRank()) {
					oldrank = pc.getClanRank();
					no1pc = pc;
				}
			}
		}
		return no1pc;
	}

	public int getJoinSetting() {
		return _join_setting;
	}

	public void setJoinSetting(int i) {
		_join_setting = i;
	}

	public int getJoinType() {
		return _join_type;
	}

	public void setJoinType(int i) {
		_join_type = i;
	}

	/** 혈맹버프 포인트 **/
	private int _bless = 0;
	private int _blesscount = 0;
	private int _attack = 0;
	private int _defence = 0;
	private int _pvpattack = 0;
	private int _pvpdefence = 0;
	public int[] getBuffTime = new int[] { _attack, _defence, _pvpattack, _pvpdefence };

	public int[] getBuffTime() {
		return getBuffTime;
	}

	public void setBuffTime(int i, int j) {
		getBuffTime[i] = IntRange.ensure(j, 0, 172800);
	}

	public void setBuffTime(int a, int b, int c, int d) {
		getBuffTime = new int[] { a, b, c, d };
	}

	public int getBlessCount() {
		return _blesscount;
	}

	public void setBlessCount(int i) {
		_blesscount = IntRange.ensure(i, 0, 400000000);
	}

	public void addBlessCount(int i) {
		_blesscount += i;
		if (_blesscount > 400000000)
			_blesscount = 400000000;
		else if (_blesscount < 0)
			_blesscount = 0;
	}

	public int getBless() {
		return _bless;
	}

	public void setBless(int i) {
		_bless = i;
	}

	/** 2016.11.25 server 앱센터 혈맹 **/
	private String _joinPassword;

	public String getJoinPassword() {
		return _joinPassword;
	}

	public void setJoinPassword(String s) {
		_joinPassword = s;
	}

	/** 2016.11.25 server 앱센터 혈맹 **/

	private volatile long _warPoint;
	private volatile boolean _warPointUncertain;
	public boolean isWarPointUncertain() { return _warPointUncertain; }
	public void markWarPointUncertain() { _warPointUncertain = true; }
	private static final java.util.concurrent.atomic.AtomicLongFieldUpdater<L1Clan> WAR_POINT =
		java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater(L1Clan.class, "_warPoint");

	public int getWarPoint() {
		final long points = _warPoint;
		return points <= 0 ? 0 : points >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) points;
	}

	/** Initial DB load only. Durable point changes use addWarPointCommitted. */
	public void setWarPoint(int i) {
		_warPoint = i;
	}

	/** SQL and its receipt have already committed. Delta updates commute across CIO owners. */
	public void addWarPointCommitted(int delta) {
		WAR_POINT.getAndAdd(this, delta);
	}

	public void incWarPoint() {
		ClanTable.changeWarPoint(this, 1, null);
	}

	public boolean decWarPoint() {
		if (getWarPoint() <= 0) return false;
		return ClanTable.changeWarPoint(this, -1, null);
	}

	public Stream<ClanMember> createMembersStream() {
		return clanMemberList.size() > 100 ? clanMemberList.parallelStream() : clanMemberList.stream();
	}

	public Stream<ClanMember> createOnlineMembers() {
		return createMembersStream().filter((ClanMember m) -> m.online && m.player != null);
	}

	

	

	

	

	private War _currentWar;

	public void setCurrentWar(War war) {
		_currentWar = war;
	}

	public War getCurrentWar() {
		return _currentWar;
	}

	private boolean _isRedKnight = false;

	public boolean isRedKnight() {
		return _isRedKnight;
	}

	public void setRedKnight(boolean b) {
		_isRedKnight = b;
	}

	public void outOfWarArea(int castleId) {
		final int[] loc = L1CastleLocation.getGetBackLoc(castleId);
		createMembersStream().filter((ClanMember member) -> {
			return member.player != null && L1CastleLocation.checkInWarArea(castleId, member.player);
		}).forEach((ClanMember member) -> {
			member.player.start_teleport(loc[0], loc[1], (short) loc[2], 5, 169, true, false);
		});
	}

	//TODO 혈맹 버프 리뉴얼 2017-11-12
	private int _BuffFirst = 0;
	private int _BuffSecond = 0;
	private int _BuffThird = 0;
	private int _EinhasadBlessBuff = 0;

	public int getBuffFirst() {
		return _BuffFirst;
	}

	public void setBuffFirst(int i) {
		_BuffFirst = i;
	}

	public int getBuffSecond() {
		return _BuffSecond;
	}

	public void setBuffSecond(int i) {
		_BuffSecond = i;
	}

	public int getBuffThird() {
		return _BuffThird;
	}

	public void setBuffThird(int i) {
		_BuffThird = i;
	}
	
	public int getEinhasadBlessBuff() {
		return _EinhasadBlessBuff;
	}

	public void setEinhasadBlessBuff(int i) {
		_EinhasadBlessBuff = i;
	}

	public void deleteClanRetrieveUser(int targetObjectId){
		ClanWarehouse clanWarehouse = WarehouseManager.getInstance().getClanWarehouse(getClanName());
		if (clanWarehouse != null) clanWarehouse.releaseWarehouseUser(targetObjectId);
	}
	
}

