package l1j.server.server.model;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.WarSystem.War;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.ServerBasePacket;

public class L1Clan {
	
	public static final int CLAN_RANK_PROBATION = 1;
	public static final int CLAN_RANK_PUBLIC = 2;
	public static final int CLAN_RANK_GUARDIAN = 3;
	public static final int CLAN_RANK_PRINCE = 4;

	private static final Logger _log = Logger.getLogger(L1Clan.class.getName());

	private int _clanId;
	private String _clanName;
	private int _leaderId;
	private String _leaderName;
	private int _castleId;
	private int _houseId;
	private byte[] _emblem;
	private int _alliance;
	private int _emblemId = 0;
	
	public int getEmblemId() {
		String name = _clanName;
		if (name == null) {
			return 0;
		}
		
		int id = _emblemId; 
		
		if (id != 0) {
			//-- 서버팩 폴더 안에 emblem 폴더 안에 해당 번호가 존재 하는지 체크 
			//-- 있다면 해당 엠블럼 호출값 주고, 없다면 0처리.
			File file = new File(String.format("emblem/%s", id));
			return file.exists() ? id : 0;
		} else {
			return 0;
		} 
	}

	public void setEmblemId(int emblemId) {
		this._emblemId = emblemId;
	}
	
	private final ArrayList<String> membersNameList = new ArrayList<String>();

	public void setEmblem(byte[] emblem){  _emblem = emblem;  }	
	public byte[] getEmblem(){  return _emblem;  }

	public int getClanId() {  return _clanId;  }
	public void setClanId(int clan_id) {  _clanId = clan_id;  }
	
	public String getClanName() {  return _clanName;  }
	public void setClanName(String clan_name) {  _clanName = clan_name;  }

	public int getLeaderId() {  return _leaderId;  }
	public void setLeaderId(int leader_id) {  _leaderId = leader_id;  }

	public String getLeaderName() {  return _leaderName;  }
	public void setLeaderName(String leader_name) {  _leaderName = leader_name;  }

	public int getCastleId() {  return _castleId;  }
	public void setCastleId(int hasCastle) {  _castleId = hasCastle;  }

	public int getHouseId() {  return _houseId;  }
	public void setHouseId(int hasHideout) {  _houseId = hasHideout;  }
	
	public int getAlliance() {	return _alliance;	}
	public void setAlliance(int alliance) {	_alliance = alliance;	}

	public void addMemberName(String member_name) {
		if (!membersNameList.contains(member_name)) {
			membersNameList.add(member_name);
		}
	}

	public void delMemberName(String member_name) {
		if (membersNameList.contains(member_name)) {
			membersNameList.remove(member_name);
		}
	}

	public L1PcInstance[] getOnlineClanMember() // 온라인중의 크란원만
	{
		ArrayList<L1PcInstance> onlineMembers = new ArrayList<L1PcInstance>();
		for (String name : membersNameList) {
			L1PcInstance pc = L1World.getInstance().getPlayer(name);
			if (pc != null && !onlineMembers.contains(pc) && pc.getNetConnection() != null){
				onlineMembers.add(pc);
			}
		}
		return onlineMembers.toArray(new L1PcInstance[onlineMembers.size()]);
	}

	public String getOnlineMembersFP() { // FP means "For Pledge"
		String result = "";
		for (String name : membersNameList) {
			L1PcInstance pc = L1World.getInstance().getPlayer(name);
			if (pc != null) {
				result = result + name + " ";
			}
		}
		return result;
	}
	
	public int getCurrentOnlineMemebers() {
		int cnt = 0;
		for (String name : membersNameList) {
			L1PcInstance pc = L1World.getInstance().getPlayer(name);
			if (pc != null) {
				if (pc.getOnlineStatus() > 0)
					cnt++;
			}
		}
		return cnt;
	}

	public String getAllMembersFP() {
		String result = "";
		for (String name : membersNameList) {
			result = result + name + " ";
		}
		return result;
	}

	public String getOnlineMembersFPWithRank() {
		String result = "";
		for (String name : membersNameList) {
			L1PcInstance pc = L1World.getInstance().getPlayer(name);
			if (pc != null && pc.getNetConnection() != null) {
				result = result + name + getRankString(pc) + " ";
			}
		}
		return result;
	}

	public String getAllMembersFPWithRank() {
		String result = "";
		try {
			for (String name : membersNameList) {
				L1PcInstance pc = CharacterTable.getInstance().restoreCharacter(name);
				if (pc != null) {
					result = result + name + getRankString(pc) + " ";
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		return result;
	}


	private String getRankString(L1PcInstance pc) {
		String rank = "";
		if (pc != null) {
			if (pc.getClanRank() == CLAN_RANK_PROBATION) {
				rank = "[견습]";
			} else if (pc.getClanRank() == CLAN_RANK_PUBLIC) {
				rank = "[일반]";
			} else if (pc.getClanRank() == CLAN_RANK_GUARDIAN) {
				rank = "[수호기사]";
			} else if (pc.getClanRank() == CLAN_RANK_PRINCE) {
				rank = "[혈맹군주]";
			} else {
				rank = "";
			}
		}
		return rank;
	}
	
	private War _currentWar;

	public void setCurrentWar(War war) {
		_currentWar = war;
	}

	public War getCurrentWar() {
		return _currentWar;
	}
	
	public void broadcast(ServerBasePacket pck, boolean isClear) {
		for (L1PcInstance member : getOnlineClanMember()) {
			if (member != null && member.getNetConnection() != null){
				member.sendPackets(pck, false);
			}
		}
		
		if (isClear)
			pck.clear();
	}
	
	public void outOfWarArea(int castleId) {
		final int[] loc = L1CastleLocation.getGetBackLoc(castleId);
		for (L1PcInstance member : getOnlineClanMember()) {
			if (member != null && member.getNetConnection() != null){
				if (L1CastleLocation.checkInWarArea(castleId, member))
					member.teleport(loc[0], loc[1], (short) loc[2], member.getHeading(),169, true, false); 
			}
		}
	}

	public String[] getAllMembers() {
		return membersNameList.toArray(new String[membersNameList.size()]);
	}
}

