package l1j.server.server.model.quest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;

public class CQuestSystem {

	public static final int QUEST_LEVEL15 = 1;
	public static final int QUEST_LEVEL30 = 2;
	public static final int QUEST_LEVEL45 = 3;
	public static final int QUEST_LEVEL50 = 4;
	public static final int QUEST_LEVEL5 = 5;
	public static final int QUEST_수련자물품 = 6;
	public static final int QUEST_LEVEL55 = 7;

	public static final int QUEST_FIRSTQUEST = 40; // ## A70 말하는 두루마리 퀘스트 추가

	public static final int QUEST_LYRA = 10;
	public static final int QUEST_OILSKINMANT = 11;

	public static final int QUEST_DOROMOND = 20;
	public static final int QUEST_RUBA = 21;
	public static final int QUEST_AREX = 22;

	public static final int QUEST_LUKEIN1 = 23;
	public static final int QUEST_TBOX1 = 24;
	public static final int QUEST_TBOX2 = 25;
	public static final int QUEST_TBOX3 = 26;
	public static final int QUEST_SIMIZZ = 27;
	public static final int QUEST_DOIL = 28;
	public static final int QUEST_RUDIAN = 29;
	public static final int QUEST_RESTA = 30;
	public static final int QUEST_CADMUS = 31;
	public static final int QUEST_KAMYLA = 32;
	public static final int QUEST_CRYSTAL = 33;
	public static final int QUEST_LIZARD = 34;
	public static final int QUEST_KEPLISHA = 35;
	public static final int QUEST_DESIRE = 36;
	public static final int QUEST_SHADOWS = 37;
	public static final int QUEST_ROI = 38;
	public static final int QUEST_MOONBOW = 39;
	public static final int QUEST_ICEQUEENRING = 41;
	public static final int QUEST_END = 255;

	// 스냅퍼 방지/귀걸이
	public static final int QUEST_SLOT76 = 60;
	public static final int QUEST_SLOT81 = 61;
	public static final int QUEST_SLOT59 = 62;
	public static final int QUEST_SAI_RUNE70 = 63;
	public static final int QUEST_SLOT_SHOULD = 64;	// 견갑
	public static final int QUEST_SLOT_BADGE = 65;	// 휘장
	public static final int QUEST_HAMO = 80;

    private static final long[] EMPTY_QUESTS = new long[0];
    private final L1PcInstance _owner;
    // Sorted packed rows: signed quest ID (high word), full int state (low word).
    // null means not loaded. The backing array and count use the same monitor.
    private long[] questRows;
    private int questCount;

    public CQuestSystem(L1PcInstance owner) {
        _owner = owner;
    }

    public final L1PcInstance get_owner() {
        return _owner;
    }

    public static int[] readSnapshot(int characterId) {
        int[] pairs = new int[16];
        int count = 0;
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT quest_id, quest_step FROM character_quests WHERE char_id=?")) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (count == pairs.length) {
                        int[] bigger = new int[pairs.length << 1];
                        System.arraycopy(pairs, 0, bigger, 0, count);
                        pairs = bigger;
                    }
                    final int questId = rs.getInt(1);
                    if (rs.wasNull())
                        throw new SQLException("NULL quest ID in login snapshot: " + characterId);
                    final int step = rs.getInt(2);
                    if (rs.wasNull())
                        throw new SQLException("NULL quest step in login snapshot: "
                                + characterId + "/" + questId);
                    pairs[count++] = questId;
                    pairs[count++] = step;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Quest DB read failed: " + characterId, e);
        }
        if (count == pairs.length) return pairs;
        int[] result = new int[count];
        System.arraycopy(pairs, 0, result, 0, count);
        return result;
    }

    public final synchronized void applySnapshot(int[] pairs) {
        if (pairs == null || (pairs.length & 1) != 0)
            throw new IllegalArgumentException("Invalid quest snapshot");
        final int count = pairs.length >>> 1;
        final long[] loaded = count == 0 ? EMPTY_QUESTS : new long[count];
        for (int i = 0; i < count; i++) {
            final int questId = pairs[i << 1];
            if (questId <= 0)
                throw new IllegalArgumentException("Invalid quest ID: " + questId);
            loaded[i] = PackQuest(questId, pairs[(i << 1) + 1]);
        }
        Arrays.sort(loaded);
        for (int i = 1; i < count; i++) {
            final int id = (int) (loaded[i] >>> 32);
            if ((int) (loaded[i - 1] >>> 32) == id)
                throw new IllegalArgumentException("Duplicate quest ID: " + id);
        }
        // Publish only after every row has been validated.
        questRows = loaded;
        questCount = count;
    }

    private static long PackQuest(int questId, int step) {
        return ((long) questId << 32) | (step & 0xffffffffL);
    }

    private int FindQuest(int questId) {
        int lo = 0, hi = questCount - 1;
        while (lo <= hi) {
            final int mid = (lo + hi) >>> 1;
            final int id = (int) (questRows[mid] >>> 32);
            if (id < questId) lo = mid + 1;
            else if (id > questId) hi = mid - 1;
            else return mid;
        }
        return ~lo;
    }

    // Cache-only mutation; callers publish after the existing durable write.
    private void SetSelectedQuest(int questId, int step) {
        final int index = FindQuest(questId);
        final long row = PackQuest(questId, step);
        if (index >= 0) {
            questRows[index] = row;
            return;
        }
        final int at = ~index;
        if (questCount == questRows.length)
            questRows = Arrays.copyOf(questRows, Math.max(4, questCount + (questCount >>> 1) + 1));
        System.arraycopy(questRows, at, questRows, at + 1, questCount - at);
        questRows[at] = row;
        questCount++;
    }

    public final synchronized int get_step(int quest_id) {
        if (questRows == null) applySnapshot(readSnapshot(_owner.getId()));
        final int index = FindQuest(quest_id);
        return index < 0 ? 0 : (int) questRows[index];
    }

    public final synchronized void set_step(int quest_id, int step) {
        // Preserve the existing synchronous durability contract. Call sites
        // which need an item + quest receipt keep their CacheD transaction.
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(
                     "insert into character_quests set char_id=?, quest_id=?, quest_step=? "
                     + "on duplicate key update quest_step=?")) {
            pstm.setInt(1, _owner.getId());
            pstm.setInt(2, quest_id);
            pstm.setInt(3, step);
            pstm.setInt(4, step);
            pstm.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("Quest step persistence failed: "
                    + _owner.getId() + "/" + quest_id, failure);
        }
        if (questRows == null) applySnapshot(readSnapshot(_owner.getId()));
        else SetSelectedQuest(quest_id, step);
    }

    /** Cache-only replay of an already committed quest row. */
    public final synchronized void applyCommittedStepIfLoaded(int questId, int step) {
        if (questRows != null) SetSelectedQuest(questId, step);
    }

    public final synchronized void add_step(int quest_id, int add) {
        set_step(quest_id, get_step(quest_id) + add);
    }

    public final void set_end(int quest_id) {
        set_step(quest_id, QUEST_END);
    }

    public final boolean isEnd(int quest_id) {
        return get_step(quest_id) == QUEST_END;
    }
}
