package l1j.server.server.model;

import java.io.File;
import java.sql.Timestamp;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.Collections;
import java.util.List;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javolution.util.FastTable;

import l1j.server.Config;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.warehouse.ClanWarehouse;
import l1j.server.server.model.inventory.warehouse.WarehouseManager;
import l1j.server.server.model.world.siege.War;
import l1j.server.server.utils.IntRange;

public class L1Clan {
	static public class ClanMember {
		public volatile String name;
		public volatile int rank;
		public volatile int level;
		public volatile String notes;
		public int memberId;
		public volatile int type;
		public volatile boolean online;
		public volatile L1PcInstance player;

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
    private static final ClanMember[] EMPTY_MEMBERS = new ClanMember[0];
    private static final L1PcInstance[] EMPTY_ONLINE = new L1PcInstance[0];

    private static final class CPledgeMemberList extends AbstractList<ClanMember> implements RandomAccess {
        private final Int2ObjectOpenHashMap<ClanMember> memberById = new Int2ObjectOpenHashMap<>(4);
        private final List<ClanMember> view = Collections.unmodifiableList(this);
        private ClanMember[] entries = EMPTY_MEMBERS;
        private int count;
        private volatile ClanMember[] published = EMPTY_MEMBERS;

        // Only structural writers use the monitor. A published array is never
        // mutated, including while another shard holds an old iterator.
        private void ensureWritable(int required) {
            final int capacity = entries.length;
            if (required > capacity) {
                final int grown = Math.max(required, Math.max(4, capacity + (capacity >>> 1)));
                entries = Arrays.copyOf(entries, grown);
            } else if (published == entries) {
                entries = Arrays.copyOf(entries, capacity);
            }
        }

        ClanMember[] snapshot() {
            ClanMember[] result = published;
            if (result != null) return result;
            synchronized (this) {
                result = published;
                if (result == null) {
                    if (count == 0) entries = EMPTY_MEMBERS;
                    else if (entries.length != count) entries = Arrays.copyOf(entries, count);
                    result = entries;
                    published = result;
                }
                return result;
            }
        }

        @Override public synchronized int size() { return count; }
        @Override public ClanMember get(int index) {
            final ClanMember[] members = snapshot();
            if (index < 0 || index >= members.length) throw new IndexOutOfBoundsException(index);
            return members[index];
        }

        @Override public Iterator<ClanMember> iterator() {
            return listIterator(0);
        }

        @Override public ListIterator<ClanMember> listIterator(int index) {
            return Collections.unmodifiableList(Arrays.asList(snapshot())).listIterator(index);
        }

        @Override public Spliterator<ClanMember> spliterator() {
            return java.util.Spliterators.spliterator(snapshot(),
                    Spliterator.ORDERED | Spliterator.IMMUTABLE);
        }

        @Override public Object[] toArray() {
            final ClanMember[] members = snapshot();
            return Arrays.copyOf(members, members.length, Object[].class);
        }

        @Override public <T> T[] toArray(T[] array) {
            return Arrays.asList(snapshot()).toArray(array);
        }

        @Override public List<ClanMember> subList(int from, int to) {
            return Collections.unmodifiableList(Arrays.asList(snapshot()).subList(from, to));
        }

        synchronized ClanMember GetDbMember(int memberId) {
            return memberById.get(memberId);
        }

        synchronized int GetDbMemberCount() {
            return memberById.size();
        }

        synchronized void AddDbMember(String name, int rank, int level, String notes, int memberId,
                                      int type, boolean online, L1PcInstance player) {
            ClanMember member = memberById.get(memberId);
            if (member != null) {
                synchronized (member) {
                    member.name = name;
                    member.rank = rank;
                    member.level = level;
                    member.notes = notes;
                    member.type = type;
                    if (online && player != null) {
                        member.player = player;
                        member.online = true;
                    }
                }
                return;
            }
            member = new ClanMember(name, rank, level, notes, memberId, type,
                    online && player != null, online ? player : null);
            ensureWritable(count + 1);
            memberById.put(memberId, member);
            entries[count++] = member;
            published = null;
        }

        synchronized ClanMember RemoveDbMember(int memberId) {
            ClanMember member = memberById.get(memberId);
            if (member == null) return null;
            int index = 0;
            while (index < count && entries[index] != member) index++;
            if (index == count)
                throw new IllegalStateException("Pledge roster index mismatch: " + memberId);
            ensureWritable(count);
            final int moved = count - index - 1;
            if (moved != 0) System.arraycopy(entries, index + 1, entries, index, moved);
            entries[--count] = null;
            if (count == 0) entries = EMPTY_MEMBERS;
            memberById.remove(memberId);
            published = null;
            synchronized (member) {
                member.player = null;
                member.online = false;
            }
            return member;
        }

        synchronized ClanMember RemoveDbMember(String name) {
            for (int i = 0; i < count; i++) {
                final ClanMember member = entries[i];
                if (member.name.equals(name)) return RemoveDbMember(member.memberId);
            }
            return null;
        }

        synchronized boolean UpdateMemberMemo(int memberId, String notes) {
            ClanMember member = memberById.get(memberId);
            if (member == null) return false;
            member.notes = notes;
            return true;
        }

        synchronized void SetMemberRank(String name, int rank) {
            for (int i = 0; i < count; i++) {
                final ClanMember member = entries[i];
                if (member.name.equals(name)) {
                    member.rank = rank;
                    return;
                }
            }
        }

        synchronized void UpdateMemberSession(int clanId, L1PcInstance pc, boolean online) {
            if (pc == null) return;
            ClanMember member = memberById.get(pc.getId());
            if (member == null) return;
            synchronized (member) {
                if (online) {
                    if (pc.getClanid() != clanId
                            || L1World.getInstance().findObject(pc.getId()) != pc
                            || L1World.getInstance().getPlayer(pc.getName()) != pc) return;
                    member.player = pc;
                    member.online = true;
                } else if (member.player == pc) {
                    member.player = null;
                    member.online = false;
                }
            }
        }
    }

    private volatile int m_Dismissed;
    private static final java.util.concurrent.atomic.AtomicIntegerFieldUpdater<L1Clan> DISMISSED =
            java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater(L1Clan.class, "m_Dismissed");

    public boolean IsDismissed() { return m_Dismissed != 0; }
    boolean TryBeginDismiss() { return DISMISSED.compareAndSet(this, 0, 1); }
    void CancelDismiss() { DISMISSED.compareAndSet(this, 1, 0); }
    void CommitDismiss() { DISMISSED.set(this, 2); }

    private final CPledgeMemberList clanMemberList = new CPledgeMemberList();

    public List<ClanMember> getClanMemberList() { return clanMemberList.view; }

    public ClanMember GetDbMember(int memberId) {
        return clanMemberList.GetDbMember(memberId);
    }

    public int GetDbMemberCount() {
        return clanMemberList.GetDbMemberCount();
    }

    public void addClanMember(String name, int rank, int level, String notes, int memberId,
                              int type, int online, L1PcInstance pc) {
        clanMemberList.AddDbMember(name, rank, level, notes, memberId, type, online == 1, pc);
        ClanTable.updateOnlineUser(this);
    }

    /** DB load never enqueues a roster-count write. */
    public void addLoadedClanMember(String name, int rank, int level, String notes,
                                    int memberId, int type) {
        clanMemberList.AddDbMember(name, rank, level, notes, memberId, type, false, null);
    }

    public void addClanMember(ClanMember member) {
        if (member == null) return;
        clanMemberList.AddDbMember(member.name, member.rank, member.level, member.notes, member.memberId,
                member.type, member.online, member.player);
        ClanTable.updateOnlineUser(this);
    }

    /** Memory-only detach, shared with the committed lifecycle. */
    ClanMember RemoveDbMember(int memberId) {
        return clanMemberList.RemoveDbMember(memberId);
    }

    private void FinishMemberRemoval(ClanMember removed) {
        if (removed == null) return;
        deleteClanRetrieveUser(removed.memberId);
        ClanTable.updateOnlineUser(this);
    }

    public void removeClanMember(int memberId) {
        FinishMemberRemoval(RemoveDbMember(memberId));
    }

    /** Compatibility for legacy name-only callers. */
    public void removeClanMember(String name) {
        FinishMemberRemoval(clanMemberList.RemoveDbMember(name));
    }

    /** Updating a memo is not a membership removal or warehouse release. */
    public boolean UpdateMemberMemo(int memberId, String notes) {
        return clanMemberList.UpdateMemberMemo(memberId, notes);
    }

	/////////// 혈맹리뉴얼//////////////
    public void setClanRank(String name, int data) {
        clanMemberList.SetMemberRank(name, data);
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
        setClanRank(name, rank);
    }

    public void updateClanMemberOnline(L1PcInstance pc) {
        if (pc == null) return;
        clanMemberList.UpdateMemberSession(getClanId(), pc, pc.getOnlineStatus() == 1);
        ClanTable.updateOnlineUser(this);
    }

    /** Logout fallback must not retain a retired session or perform extra SQL. */
    public void releaseClanMemberReference(L1PcInstance pc) {
        clanMemberList.UpdateMemberSession(getClanId(), pc, false);
    }

    /** Total and online count from one ordered structural snapshot. */
    public long GetMemberCounts() {
        final ClanMember[] members = clanMemberList.snapshot();
        int online = 0;
        for (ClanMember member : members) {
            synchronized (member) {
                final L1PcInstance current = member.player;
                if (current != null && current.getOnlineStatus() == 1
                        && !current.isLogoutStarted() && current.getClanid() == getClanId()
                        && FindOnlineMember(member) == current) {
                    member.online = true;
                    online++;
                } else {
                    member.player = null;
                    member.online = false;
                }
            }
        }
        return ((long)members.length << 32) | (online & 0xffffffffL);
    }

    public int getCurrentOnlineMemebers() {
        return (int)GetMemberCounts();
    }

    public String[] getAllMembersName() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (ClanMember member : clanMemberList.snapshot()) names.add(member.name);
        return names.toArray(new String[0]);
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

    /** CPledge GetAllMember: resolve member IDs through the existing world registry.
     *  A stale ID must never deliver a pledge operation to another character. */
    private L1PcInstance FindOnlineMember(ClanMember member) {
        L1World world = L1World.getInstance();
        if (world.findObject(member.memberId) instanceof L1PcInstance pc
                && pc.getClanid() == getClanId()
                && pc.getOnlineStatus() == 1
                && !pc.isLogoutStarted()
                && world.getPlayer(pc.getName()) == pc) return pc;
        return null;
    }

    public int getOnlineMemberCount() {
        int count = 0;
        for (ClanMember member : clanMemberList.snapshot()) {
            if (FindOnlineMember(member) != null) count++;
        }
        return count;
    }

    public L1PcInstance[] getOnlineClanMember() {
        final ClanMember[] members = clanMemberList.snapshot();
        if (members.length == 0) return EMPTY_ONLINE;
        L1PcInstance[] online = null;
        int count = 0;
        for (ClanMember member : members) {
            final L1PcInstance pc = FindOnlineMember(member);
            if (pc == null) continue;
            if (online == null) online = new L1PcInstance[members.length];
            online[count++] = pc;
        }
        if (count == 0) return EMPTY_ONLINE;
        return count == online.length ? online : Arrays.copyOf(online, count);
    }

    public String getAllMembersFP() {
        StringBuilder result = new StringBuilder();
        for (ClanMember member : clanMemberList.snapshot()) result.append(member.name).append(' ');
        return result.toString();
    }

    public String getOnlineMembersFP() {
        StringBuilder result = new StringBuilder();
        for (ClanMember member : clanMemberList.snapshot()) {
            if (FindOnlineMember(member) != null) result.append(member.name).append(' ');
        }
        return result.toString();
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
        L1PcInstance selected = null;
        int oldrank = 0;
        for (ClanMember member : clanMemberList.snapshot()) {
            L1PcInstance pc = FindOnlineMember(member);
            if (pc == null) continue;
            int rank = pc.getClanRank();
            if (rank >= L1Clan.수호 && oldrank < rank) {
                oldrank = rank;
                selected = pc;
            }
        }
        return selected;
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
		return clanMemberList.stream();
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
        for (ClanMember member : clanMemberList.snapshot()) {
            L1PcInstance pc = FindOnlineMember(member);
            if (pc != null && L1CastleLocation.checkInWarArea(castleId, pc)) {
                pc.start_teleport(loc[0], loc[1], (short) loc[2], 5, 169, true, false);
            }
        }
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

    public void deleteClanRetrieveUser(L1PcInstance pc) {
        ClanWarehouse clanWarehouse = WarehouseManager.getInstance().getClanWarehouse(getClanName());
        if (clanWarehouse != null) clanWarehouse.ReleaseWarehouseUser(pc);
    }

	public void deleteClanRetrieveUser(int targetObjectId){
		ClanWarehouse clanWarehouse = WarehouseManager.getInstance().getClanWarehouse(getClanName());
		if (clanWarehouse != null) clanWarehouse.releaseWarehouseUser(targetObjectId);
	}
	
}

