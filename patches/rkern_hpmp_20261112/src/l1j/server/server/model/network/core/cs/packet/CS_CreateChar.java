package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.model.network.core.sc.packet.SC_CharCreateStatus;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.BadNamesList;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Beginner;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.templates.L1Skills;


public final class CS_CreateChar {

	private static final Logger _log = Logger.getLogger(CS_CreateChar.class.getName());

	private CS_CreateChar() {
	}

	public static void handle(
			CIOClient client,
			String name,
			int type,
			int sex,
			int baseStr,
			int baseDex,
			int baseCon,
			int baseWis,
			int baseCha,
			int baseInt) {

		if (client == null) return;

		L1PcInstance pc = new L1PcInstance();
		
		// 버그/어뷰징 방지 (이름 길이 제한)
		if (name != null && name.length() > 20) {
			_log.warning("패킷공격 ID 감지: " + name + " / IP: " + client.getIp());
			client.kick();
			return;
		}

		if (name == null || name.isEmpty()) {
			SC_CharCreateStatus.send(client, SC_CharCreateStatus.REASON_INVALID_NAME);
			return;
		}

		Calendar cal = Calendar.getInstance();
		int hour = cal.get(Calendar.HOUR);
		int minute = cal.get(Calendar.MINUTE);
		String ampm = (cal.get(Calendar.AM_PM) == 0) ? "오전" : "오후";


		if (BadNamesList.getInstance().isBadName(name) || isInvalidName(name)) {
			SC_CharCreateStatus.send(client, SC_CharCreateStatus.REASON_INVALID_NAME);
			return;
		}

		if (CharacterTable.getInstance().isContainNameList(name) || L1World.getInstance().getPlayer(name) != null) {
			_log.fine("charname: " + name + " already exists. creation failed.");
			SC_CharCreateStatus.send(client, SC_CharCreateStatus.REASON_ALREADY_EXSISTS);
			return;
		}

		if (client.getAccount() == null || type < 0 || type > 7 || (sex != 0 && sex != 1)) {
			SC_CharCreateStatus.send(client, SC_CharCreateStatus.REASON_WRONG_AMOUNT);
			return;
		}

		// 자모음 및 부적절한 단어 포함 여부 검사
		if (containsForbiddenChars(name)) {
			SC_CharCreateStatus.send(client, SC_CharCreateStatus.REASON_INVALID_NAME);
			return;
		}

		pc.setName(name);
		pc.setType(type);
		pc.set_sex(sex);
		pc.getAbility().setBaseStr((byte) baseStr);
		pc.getAbility().setBaseDex((byte) baseDex);
		pc.getAbility().setBaseCon((byte) baseCon);
		pc.getAbility().setBaseWis((byte) baseWis);
		pc.getAbility().setBaseCha((byte) baseCha);
		pc.getAbility().setBaseInt((byte) baseInt);

		int statusAmount = pc.getAbility().getAmount();

		if (baseStr < 3 || baseDex < 3 || baseCon < 3 || baseWis < 3 || baseCha < 3 || baseInt < 3
                || pc.getAbility().getBaseStr() > 20 || pc.getAbility().getBaseDex() > 20 || pc.getAbility().getBaseCon() > 20
				|| pc.getAbility().getBaseWis() > 20 || pc.getAbility().getBaseCha() > 20
				|| pc.getAbility().getBaseInt() > 20 || statusAmount != 75) {
			SC_CharCreateStatus.send(client, SC_CharCreateStatus.REASON_WRONG_AMOUNT);
			return;
		}

		if (pc.getType() == 6 && Config.cantblack == 1) {
			SC_CharCreateStatus.send(client, SC_CharCreateStatus.REASON_WRONG_AMOUNT);
			return;
		}

        try {
            initNewChar(client, pc);
            persistNewCharacter(client, pc);
        } catch (Exception failure) {
            _log.log(java.util.logging.Level.WARNING, "Character creation not admitted", failure);
            SC_CharCreateStatus.send(client, SC_CharCreateStatus.REASON_WRONG_AMOUNT);
        }
    }

    private static void persistNewCharacter(CIOClient client, L1PcInstance pc) {
        final l1j.server.server.Account account = client.getAccount();
        final var socket = client.getConnection();
        final var state = client.getConnectionState();
        final String accountName = pc.getAccountName();
        final int accountKey = l1j.server.server.storage.CacheD.accountOrderKey(accountName);
        final int creationKey = l1j.server.server.storage.CacheD.accountOrderKey("$character-create");
        final L1World world = L1World.getInstance();
        final int skillId = pc.isWizard() ? 4 : pc.isElf() ? 131 : 0;
        final L1Skills skill = skillId == 0 ? null : SkillsTable.getInstance().getTemplate(skillId);
        // Creation is metadata, with no live PC yet. Retain both account and
        // global creation lanes through owner publication, even if the client
        // disconnects after COMMIT. A rejected publication must quarantine.
        try (l1j.server.server.storage.CacheD.WarehouseMove move =
                l1j.server.server.storage.CacheD.beginEconomyMove(accountKey, creationKey)) {
            move.onError(failure -> creationFailed(client, account, failure));
            move.stageWarehouseSql(con -> {
                    l1j.server.server.storage.cached.CTransaction.requireTransactionalTables(con,
                            "accounts", "characters", "character_items", "character_skills", "character_teleport");
                    try (java.sql.PreparedStatement lock = con.prepareStatement("SELECT login FROM accounts WHERE login=? FOR UPDATE")) {
                        lock.setString(1, accountName);
                        try (java.sql.ResultSet rows = lock.executeQuery()) {
                            if (!rows.next()) throw new java.sql.SQLException("Creation account missing");
                        }
                    }
                    try (java.sql.PreparedStatement count = con.prepareStatement("SELECT COUNT(*) FROM characters WHERE account_name=?")) {
                        count.setString(1, accountName);
                        try (java.sql.ResultSet rows = count.executeQuery()) {
                            if (!rows.next() || rows.getInt(1) >= 8) throw new java.sql.SQLException("Character slot limit");
                        }
                    }
                    try (java.sql.PreparedStatement name = con.prepareStatement("SELECT objid FROM characters WHERE char_name=? FOR UPDATE")) {
                        name.setString(1, pc.getName());
                        try (java.sql.ResultSet rows = name.executeQuery()) {
                            if (rows.next()) throw new java.sql.SQLException("Character name already exists", "23000");
                        }
                    }
                    new l1j.server.server.storage.mysql.MySqlCharacterStorage().createCharacter(con, pc);
                    Beginner.getInstance().GiveItem(con, pc);
                    Beginner.getInstance().writeBookmark(con, pc);
                    if (skill != null) {
                        try (java.sql.PreparedStatement ps = con.prepareStatement("INSERT INTO character_skills "
                                + "(char_obj_id,skill_id,skill_name,is_active,activetimeleft) VALUES (?,?,?,0,0)")) {
                            ps.setInt(1, pc.getId()); ps.setInt(2, skillId); ps.setString(3, skill.getName());
                            if (ps.executeUpdate() != 1) throw new java.sql.SQLException("Initial skill insert failed");
                        }
                    }
            });
            move.commit(() -> l1j.server.server.model.network.thread.CIOThreadCallback.getInstance()
                    .completeWorldMutation(world, () -> {
                CharacterTable.getInstance().registerCreatedCharacter(pc);
                synchronized (client) {
                    if (client.isConnected() && client.getConnection() == socket && client.getAccount() == account
                            && client.getActiveChar() == null && client.getConnectionState() == state) {
                        SC_CharCreateStatus.send(client, SC_CharCreateStatus.REASON_OK);
                        l1j.server.server.model.network.core.sc.packet.SC_NewCharPacket.send(client, pc);
                    }
                }
                _log.info("Character creation committed and published: " + pc.getName());
            }, failure -> {
                move.finishOwnerApply(failure);
                if (failure != null) {
                    _log.log(java.util.logging.Level.SEVERE, "Creation publication requires reconciliation", failure);
                    l1j.server.server.model.network.thread.CIOThreadCallback.getInstance()
                            .completeSession(client, () -> { if (client.getAccount() == account) client.close(); });
                }
            }));
        }
    }

    private static void creationFailed(CIOClient client, l1j.server.server.Account account, Throwable failure) {
        boolean unsafe = false;
        String sqlState = null;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof l1j.server.server.storage.CacheD.CommittedReplayException
                    || cause instanceof l1j.server.server.storage.cached.CTransaction.OutcomeUnknownException) unsafe = true;
            if (cause instanceof java.sql.SQLException && sqlState == null)
                sqlState = ((java.sql.SQLException) cause).getSQLState();
        }
        final boolean unresolved = unsafe;
        final String state = sqlState;
        l1j.server.server.model.network.thread.CIOThreadCallback.getInstance().completeSession(client, () -> {
            if (client.getAccount() != account || client.getActiveChar() != null) return;
            if (unresolved) client.close();
            else SC_CharCreateStatus.send(client, "23000".equals(state)
                    ? SC_CharCreateStatus.REASON_ALREADY_EXSISTS : SC_CharCreateStatus.REASON_WRONG_AMOUNT);
        });
        _log.log(unresolved ? java.util.logging.Level.SEVERE : java.util.logging.Level.WARNING,
                unresolved ? "Creation receipt/publication unresolved" : "Creation SQL rolled back", failure);
    }

	private static boolean containsForbiddenChars(String name) {
		String forbidden = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎㅛㅕㅑㅐِهㅗㅓㅏㅣㅠㅜㅡㅒㅖㅢㅟㅝㅞㅙㅚㅘ씹좃좆ㅤ";
		for (int i = 0; i < name.length(); i++) {
			if (forbidden.indexOf(name.charAt(i)) != -1) {
				return true;
			}
		}
		return false;
	}

	public static final int[] MALE_LIST = new int[] { 0, 61, 138, 734, 2786, 6658, 6671, 12490 };
	public static final int[] FEMALE_LIST = new int[] { 1, 48, 37, 1186, 2796, 6661, 6650, 12494 };
	public static final int[][] START_LOC_X = new int[][] { { 33430, 33431, 33432, 33429, 33428 } };
	public static final int[][] START_LOC_Y = new int[][] { { 32799, 32800, 32801, 32798, 32807 } };
	public static final short[] MAPID_LIST = new short[] { 4, 4, 4, 4, 4, 4, 4, 4 };

	private static void initNewChar(CIOClient client, L1PcInstance pc) throws IOException, Exception {
		short init_hp = 0, init_mp = 0;
		final int NewHi = 0; // 숨계
		int startPosType = NewHi; 
		int startPos = ThreadLocalRandom.current().nextInt(5);

		pc.setId(IdFactory.getInstance().nextId());

		if (pc.get_sex() == 0) {
			pc.setClassId(MALE_LIST[pc.getType()]);
		} else {
			pc.setClassId(FEMALE_LIST[pc.getType()]);
		}

        init_hp = l1j.server.server.utils.CalcStat.initialHp(pc.getType());
        init_mp = l1j.server.server.utils.CalcStat.initialMp(pc.getType(), pc.getAbility().getBaseWis());

		if (Config.캐릭터시작점) {
			pc.setX(START_LOC_X[startPosType][startPos]);
			pc.setY(START_LOC_Y[startPosType][startPos]);
			pc.setMap(MAPID_LIST[pc.getType()]);
		} else {
			int randIdx = ThreadLocalRandom.current().nextInt(10) + 1;
			int offset = ThreadLocalRandom.current().nextInt(3) - 3;
			
			int[][] baseCoords = {
				{33445, 32794}, {33426, 32817}, {33426, 32802}, {33441, 32818}, {33438, 32813},
				{33430, 32819}, {33426, 32808}, {33438, 32806}, {33443, 32805}, {33433, 32798}
			};
			
			int coordIdx = Math.min(randIdx - 1, baseCoords.length - 1);
			pc.setX(baseCoords[coordIdx][0] + offset);
			pc.setY(baseCoords[coordIdx][1] + offset);
			pc.setMap((short) 4);
		}

		pc.setHeading(0);
		pc.setLawful(0);
		pc.addBaseMaxHp(init_hp);
		pc.setCurrentHp(init_hp);
		pc.addBaseMaxMp(init_mp);
		pc.setCurrentMp(init_mp);
		pc.resetBaseAc();
		pc.setTitle(Config.GAME_SERVER_NAME);
		pc.setClanid(0);
		pc.setClanRank(0);
		pc.set_food(39);
		pc.setAccessLevel((short) 0);
		pc.setGm(false);
		pc.setMonitor(false);
		pc.setGmInvis(false);
		pc.setExp(0);
        pc.setLevel(1);
		pc.setHighLevel(1);
		pc.setStatus(0);
		pc.setClanname("");
		pc.setClanMemberNotes("");
		pc.setBonusStats(0);
		pc.setElixirStats(0);
		pc.resetBaseMr();
		pc.setElfAttr(0);
		pc.set_PKcount(0);
		pc.setExpRes(0);
		pc.setPartnerId(0);
		pc.setOnlineStatus(0);
		pc.setHomeTownId(0);
		pc.setContribution(0);
		pc.setBanned(false);
		pc.setKarma(0);
		pc.setReturnStat(0);
		pc.setMark_count(60);

		Calendar local_c = Calendar.getInstance();
		SimpleDateFormat local_sdf = new SimpleDateFormat("yyyyMMdd");
		local_c.setTimeInMillis(System.currentTimeMillis());
		pc.setBirthDay(Integer.parseInt(local_sdf.format(local_c.getTime())));

        pc.setAccountName(client.getAccountName());
    }

	private static boolean isAlphaNumeric(String s) {
		if (s == null) return false;
		for (char c : s.toCharArray()) {
			if (!Character.isLetterOrDigit(c)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isInvalidName(String name) {
		int numOfNameBytes = 0;
		try {
			numOfNameBytes = name.getBytes("EUC-KR").length;
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return false;
		}

		if (isAlphaNumeric(name)) {
			return false;
		}

		if (5 < (numOfNameBytes - name.length()) || 12 < numOfNameBytes) {
			return false;
		}

		if (BadNamesList.getInstance().isBadName(name)) {
			return false;
		}
		return true;
	}

}
