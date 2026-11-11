package l1j.server.server.storage.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.MJTemplate.MJObjectWrapper;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.storage.CharacterStorage;
import l1j.server.server.storage.CacheD;

public class MySqlCharacterStorage implements CharacterStorage {

	private static final Logger _log = Logger.getLogger(MySqlCharacterStorage.class.getName());

	@Override
	public L1PcInstance loadCharacter(String charName) {
		if (!CacheD.getInstance().flushCharacter(charName, 30000L)) {
			throw new IllegalStateException("Character CacheD flush incomplete: " + charName);
		}
		L1PcInstance pc = null;
		String sql = "SELECT * FROM characters WHERE char_name = ?";
		
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, charName);
			
			try (ResultSet rs = pstm.executeQuery()) {
				if (!rs.next()) {
					return null;
				}

				pc = new L1PcInstance();
				pc.setAccountName(rs.getString("account_name"));
				pc.setId(rs.getInt("objid"));
				pc.setName(rs.getString("char_name"));
				pc.setHighLevel(rs.getInt("HighLevel"));
				pc.setExp(rs.getInt("Exp"));
				pc.addBaseMaxHp(rs.getShort("MaxHp"));
				int currentHp = rs.getInt("CurHp");
				if (currentHp < 1) {
					currentHp = 1;
				}
				pc.setDead(false);
				pc.setStatus(0);
				pc.addBaseMaxMp(rs.getShort("MaxMp"));
				int currentMp = rs.getInt("CurMp");
				pc.getAbility().setBaseStr(rs.getByte("BaseStr"));
				pc.getAbility().setStr(rs.getByte("Str"));
				pc.getAbility().setBaseCon(rs.getByte("BaseCon"));
				pc.getAbility().setCon(rs.getByte("Con"));
				pc.getAbility().setBaseDex(rs.getByte("BaseDex"));
				pc.getAbility().setDex(rs.getByte("Dex"));
				pc.getAbility().setBaseCha(rs.getByte("BaseCha"));
				pc.getAbility().setCha(rs.getByte("Cha"));
				pc.getAbility().setBaseInt(rs.getByte("BaseIntel"));
				pc.getAbility().setInt(rs.getByte("Intel"));
				pc.getAbility().setBaseWis(rs.getByte("BaseWis"));
				pc.getAbility().setWis(rs.getByte("Wis"));

				int status = rs.getInt("Status");
				pc.setCurrentWeapon(status);
				int classId = rs.getInt("Class");
				pc.setClassId(classId);
				pc.setCurrentSprite(classId);
				pc.set_sex(rs.getInt("Sex"));
				pc.setType(rs.getInt("Type"));
				int head = rs.getInt("Heading");
				if (head > 7) {
					head = 0;
				}
				pc.setHeading(head);
				pc.setX(rs.getInt("locX"));
				pc.setY(rs.getInt("locY"));
				pc.setMap(rs.getShort("MapID"));
				pc.set_food(rs.getInt("Food"));
				pc.setLawful(rs.getInt("Lawful"));
				pc.setTitle(rs.getString("Title"));
				pc.setClanid(rs.getInt("ClanID"));
				pc.setClanname(rs.getString("Clanname"));
				pc.setClanRank(rs.getInt("ClanRank"));
				pc.setClanMemberNotes(rs.getString("notes"));
				pc.setBonusStats(rs.getInt("BonusStatus"));
				pc.setElixirStats(rs.getInt("ElixirStatus"));
				pc.setElfAttr(rs.getInt("ElfAttr"));
				pc.set_PKcount(rs.getInt("PKcount"));
				pc.setExpRes(rs.getInt("ExpRes"));
				pc.setPartnerId(rs.getInt("PartnerID"));
				pc.setAccessLevel(rs.getShort("AccessLevel"));
				
				if (pc.getAccessLevel() == Config.GMCODE || pc.getAccessLevel() == Config.SUB_GMCODE) {
					pc.setGm(true);
					pc.setMonitor(false);
				} else if (pc.getAccessLevel() == 100) {
					pc.setGm(false);
					pc.setMonitor(true);
				} else {
					pc.setGm(false);
					pc.setMonitor(false);
				}
				
				pc.setOnlineStatus(rs.getInt("OnlineStatus"));
				pc.setHomeTownId(rs.getInt("HomeTownID"));
				pc.setContribution(rs.getInt("Contribution"));
				pc.setBanned(rs.getBoolean("Banned"));
				pc.setKarma(rs.getInt("Karma"));
				pc.setLastPk(rs.getTimestamp("LastPk"));
				pc.setDeleteTime(rs.getTimestamp("DeleteTime"));
				pc.setReturnStat(rs.getInt("ReturnStat"));
				pc.setSealingPW(rs.getString("sealingPW"));
				pc.setSealScrollTime(rs.getInt("sealScrollTime"));
				pc.setSealScrollCount(rs.getInt("sealScrollCount"));
				pc.setLastLoginTime(rs.getTimestamp("lastLoginTime"));
				pc.setAinState(rs.getInt("AinState"));
				pc.setSurvivalGauge(rs.getInt("SurvivalGauge"));
				pc.setMark_count(rs.getInt("Mark_Count"));
				pc.setAge(rs.getInt("Age"));
				
				/** 캐릭별 추가데미지, 추가리덕션, 확률 **/
				pc.setAddDamage(rs.getInt("AddDamage"));
				pc.setAddDamageRate(rs.getInt("AddDamageRate"));
				pc.setAddReduction(rs.getInt("AddReduction"));
				pc.setAddReductionRate(rs.getInt("AddReductionRate"));
				pc.setBug_check(rs.getInt("bug_check"));
				pc.setKir_time(rs.getInt("kir_time"));
				
				/**컬렉션**/
				pc.setBcol_y(rs.getInt("col_y"));
				pc.setBcol_m(rs.getInt("col_m"));
				pc.setBcol_l(rs.getInt("col_l"));
				pc.setBcol_h(rs.getInt("col_h"));
				pc.setLevel_q(rs.getInt("level_q"));
				
				pc.setTamTime(rs.getTimestamp("TamEndTime"));
				pc.set_SpecialSize(rs.getInt("SpecialSize"));
				pc.setSlotNumber(rs.getInt("slot_number"));
				pc.setElfAttrInitCount(rs.getInt("elf_attrinit_count"));
				pc.setLastTopBless(rs.getTimestamp("last_topBless"));
                pc.restoreHpMpGrowth(rs.getString("hp_mp_growth"));
                // Permanent CON/WIS and type are restored before clamping
                // saved current vitals against their effective maxima.
                pc.setCurrentHp(currentHp);
                pc.setCurrentMp(currentMp);
			
				try {
					pc.setFishingShopBuyTime_1(rs.getTimestamp("FishingShopBuyTime_1").getTime());
				} catch (Exception e) {
					pc.setFishingShopBuyTime_1(0);
				}
				
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "loadCharacter error for: " + charName, e);
			pc = null;
		}

		if (pc != null) {
			pc.refresh();
			pc.setMoveSpeed(0);
			pc.setBraveSpeed(0);
		}
		return pc;
	}

	@Override
	public void createCharacter(L1PcInstance pc) {
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            createCharacter(con, pc);
        } catch (SQLException failure) {
            throw new IllegalStateException("Character create failed", failure);
        }
    }

    /** Caller owns the receipt transaction; never open/commit another connection. */
    public void createCharacter(Connection con, L1PcInstance pc) throws SQLException {
        String sql = "INSERT INTO characters SET account_name=?, objid=?, char_name=?"
				+ ", level=?, HighLevel=?, Exp=?, MaxHp=?, CurHp=?, MaxMp=?, CurMp=?, Ac=?, Str=?"
				+ ", BaseStr=?, Con=?, BaseCon=?, Dex=?, BaseDex=?, Cha=?, BaseCha=?, Intel=?, BaseIntel=?"
				+ ", Wis=?, BaseWis=?, Status=?, Class=?, Sex=?, Type=?, Heading=?, LocX=?, LocY=?, MapID=?, Food=?"
				+ ", Lawful=?, Title=?, ClanID=?, Clanname=?, ClanRank=?, notes=?, BonusStatus=?, ElixirStatus=?, ElfAttr=?"
				+ ", PKcount=?, ExpRes=?, PartnerID=?, AccessLevel=?, OnlineStatus=?, HomeTownID=?, Contribution=?"
				+ ", Pay=?, Banned=?, Karma=?, LastPk=?, DeleteTime=?, ReturnStat=?, lastLoginTime=now(), AinState=?, SurvivalGauge=?, BirthDay=?"
				+ ", Mark_Count=?, Age=?, AddDamage=?, AddDamageRate=?, AddReduction=?, AddReductionRate=?, bug_check=?, kir_time=?, col_y=?, col_m=?, col_l=?, col_h=?, level_q=?"
				+ ", TamEndTime=?, SpecialSize=?, FishingShopBuyTime_1=?, slot_number=?, elf_attrinit_count=?, hp_mp_growth=?";

		try (PreparedStatement pstm = con.prepareStatement(sql)) {
			int i = 0;
			pstm.setString(++i, pc.getAccountName());
			pstm.setInt(++i, pc.getId());
			pstm.setString(++i, pc.getName());
			pstm.setInt(++i, pc.getLevel());
			pstm.setInt(++i, pc.getHighLevel());
			pstm.setInt(++i, pc.getExp());
			pstm.setInt(++i, pc.getBaseMaxHp());
			int hp = pc.getCurrentHp();
			if (hp < 1) {
				hp = 1;
			}
			pstm.setInt(++i, hp);
			pstm.setInt(++i, pc.getBaseMaxMp());
			pstm.setInt(++i, pc.getCurrentMp());
			pstm.setInt(++i, pc.getAC().getAc());
			pstm.setInt(++i, pc.getAbility().getStr());
			pstm.setInt(++i, pc.getAbility().getBaseStr());
			pstm.setInt(++i, pc.getAbility().getCon());
			pstm.setInt(++i, pc.getAbility().getBaseCon());
			pstm.setInt(++i, pc.getAbility().getDex());
			pstm.setInt(++i, pc.getAbility().getBaseDex());
			pstm.setInt(++i, pc.getAbility().getCha());
			pstm.setInt(++i, pc.getAbility().getBaseCha());
			pstm.setInt(++i, pc.getAbility().getInt());
			pstm.setInt(++i, pc.getAbility().getBaseInt());
			pstm.setInt(++i, pc.getAbility().getWis());
			pstm.setInt(++i, pc.getAbility().getBaseWis());
			pstm.setInt(++i, pc.getCurrentWeapon());
			pstm.setInt(++i, pc.getClassId());
			pstm.setInt(++i, pc.get_sex());
			pstm.setInt(++i, pc.getType());
			pstm.setInt(++i, pc.getHeading());
			pstm.setInt(++i, pc.getX());
			pstm.setInt(++i, pc.getY());
			pstm.setInt(++i, pc.getMapId());
			pstm.setInt(++i, pc.get_food());
			pstm.setInt(++i, pc.getLawful());
			pstm.setString(++i, pc.getTitle());
			pstm.setInt(++i, pc.getClanid());
			pstm.setString(++i, pc.getClanname());
			pstm.setInt(++i, pc.getClanRank());
			pstm.setString(++i, pc.getClanMemberNotes());
			pstm.setInt(++i, pc.getBonusStats());
			pstm.setInt(++i, pc.getElixirStats());
			pstm.setInt(++i, pc.getElfAttr());
			pstm.setInt(++i, pc.get_PKcount());
			pstm.setInt(++i, pc.getExpRes());
			pstm.setInt(++i, pc.getPartnerId());
			pstm.setShort(++i, pc.getAccessLevel());
			pstm.setInt(++i, pc.getOnlineStatus());
			pstm.setInt(++i, pc.getHomeTownId());
			pstm.setInt(++i, pc.getContribution());
			pstm.setInt(++i, 0);
			pstm.setBoolean(++i, pc.isBanned());
			pstm.setInt(++i, pc.getKarma());
			pstm.setTimestamp(++i, pc.getLastPk());
			pstm.setTimestamp(++i, pc.getDeleteTime());
			pstm.setInt(++i, pc.getReturnStat());
			pstm.setInt(++i, pc.getAinState());
			pstm.setInt(++i, pc.getSurvivalGauge());
			pstm.setInt(++i, pc.getBirthDay());
			pstm.setInt(++i, pc.getMark_count());
			pstm.setInt(++i, pc.getAge());
			
			pstm.setInt(++i, pc.getAddDamage());
			pstm.setInt(++i, pc.getAddDamageRate());
			pstm.setInt(++i, pc.getAddReduction());
			pstm.setInt(++i, pc.getAddReductionRate());
			pstm.setInt(++i, pc.getBug_check());
			pstm.setInt(++i, pc.getKir_time());
			
			pstm.setInt(++i, pc.getBcol_y());
			pstm.setInt(++i, pc.getBcol_m());
			pstm.setInt(++i, pc.getBcol_l());
			pstm.setInt(++i, pc.getBcol_h());
			pstm.setInt(++i, pc.getLevel_q());
			
			pstm.setTimestamp(++i, pc.getTamTime());
			pstm.setInt(++i, pc.get_SpecialSize());
			pstm.setString(++i, pc.getFishingShopBuyTime_1() == 0 ? "0000-00-00 00:00:00"
					: new Timestamp(pc.getFishingShopBuyTime_1()).toString());
			pstm.setInt(++i, pc.getSlotNumber());
			pstm.setInt(++i, pc.getElfAttrInitCount());
            pstm.setString(++i, pc.encodeHpMpGrowth());
			
			pstm.execute();
			_log.config("created char data: " + pc.getName());
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "createCharacter error for: " + pc.getName(), e);
			throw e;
		}
	}

	@Override
	public void deleteCharacter(String accountName, String charName) throws Exception {
        CacheD cache=CacheD.getInstance();
        final int characterId;
        try(Connection con=DatabaseFactory.getInstance().getConnection();PreparedStatement ps=con.prepareStatement(
                "SELECT objid FROM characters WHERE account_name=? AND char_name=?")) {
            ps.setString(1,accountName);ps.setString(2,charName);
            try(ResultSet rows=ps.executeQuery()) {
                if(!rows.next())throw new SQLException("Character missing for deletion");characterId=rows.getInt(1);
            }
        }
        if(!cache.beginCharacterDelete(charName,characterId,30000L))
            throw new IllegalStateException("Character delete barrier incomplete: "+charName);
        boolean committed=false,uncertain=false;
        try {
            l1j.server.server.storage.cached.CTransaction.persistEconomy(java.util.UUID.randomUUID().toString(),
                    characterId,CacheD.accountOrderKey(accountName),
                    new l1j.server.server.storage.cached.CTransaction.Mutation[]{con->{
                l1j.server.server.storage.cached.CTransaction.requireTransactionalTables(con,
                    "cached_transaction_receipts","characters","character_items","character_buddys","character_buff",
                    "character_config","character_quests","character_level_gifts","character_skills","character_teleport");
                try(PreparedStatement ps=con.prepareStatement("SELECT objid FROM characters WHERE account_name=? AND char_name=? FOR UPDATE")) {
                    ps.setString(1,accountName);ps.setString(2,charName);
                    try(ResultSet rows=ps.executeQuery()) {
                        if(!rows.next() || rows.getInt(1)!=characterId)throw new SQLException("Character changed during delete");
                    }
                }
                try(PreparedStatement ps=con.prepareStatement("SELECT house_id FROM board_auction "
                        +"WHERE bidder_id=? OR old_owner_id=? LIMIT 1 FOR UPDATE")) {
                    ps.setInt(1,characterId);ps.setInt(2,characterId);
                    try(ResultSet rows=ps.executeQuery()){if(rows.next())throw new SQLException("Character has an active auction");}
                }
                boolean creditTable;
                try(PreparedStatement ps=con.prepareStatement("SELECT ENGINE FROM information_schema.TABLES "
                        +"WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='character_pending_credits'")) {
                    try(ResultSet rows=ps.executeQuery()) {
                        creditTable=rows.next();
                        if(creditTable && !"InnoDB".equalsIgnoreCase(rows.getString(1)))throw new SQLException("Nontransactional credits");
                    }
                }
                if(creditTable)try(PreparedStatement ps=con.prepareStatement(
                        "SELECT credit_id FROM character_pending_credits WHERE char_id=? LIMIT 1 FOR UPDATE")) {
                    ps.setInt(1,characterId);
                    try(ResultSet rows=ps.executeQuery()){if(rows.next())throw new SQLException("Claim pending credits before deleting character");}
                }
                String[] queries = {
                        "DELETE FROM character_buddys WHERE char_id = ?",
                        "DELETE FROM character_buff WHERE char_obj_id = ?",
                        "DELETE FROM character_config WHERE object_id = ?",
                        "DELETE FROM character_items WHERE char_id = ?",
                        "DELETE FROM character_quests WHERE char_id = ?",
                        "DELETE FROM character_level_gifts WHERE char_id = ?",
                        "DELETE FROM character_skills WHERE char_obj_id = ?",
                        "DELETE FROM character_teleport WHERE char_id = ?",
                        "DELETE FROM characters WHERE objid = ?"
                    };
                    for (String sql : queries) {
                        try (PreparedStatement ps = con.prepareStatement(sql)) {
                            ps.setInt(1, characterId);
                            ps.executeUpdate();
                        }
                    }

            }});
            committed=true;
        } catch(l1j.server.server.storage.cached.CTransaction.OutcomeUnknownException failure) {
            uncertain=true;throw failure;
        } finally {
            // An absent receipt after lost COMMIT/ROLLBACK is not permission to
            // reopen a deleted character's writes. Keep its barrier quarantined.
            if(!uncertain)cache.finishCharacterDelete(charName,characterId,committed);
        }
    }

	@Override
	public void updateAccountName(L1PcInstance pc) {
		String sql = "UPDATE characters SET account_name = ? WHERE objid = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, pc.getAccountName());
			pstm.setInt(2, pc.getId());
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "updateAccountName error for char: " + pc.getName(), e);
		}
	}

    public static final class CharacterSnapshot {
        public final String name;
        public final int objectId;
        public final long sessionEpoch;
        private final int onlineStatus;
        private final int[] values = new int[65];
        private final String[] strings = new String[5];
        private final Timestamp[] times = new Timestamp[4];

        public CharacterSnapshot(L1PcInstance pc) {
            pc.lockUserState();
            try {
            this.name = pc.getName();
            this.objectId = pc.getId();
            this.sessionEpoch = pc.getCacheDSessionEpoch();
            this.onlineStatus = pc.getOnlineStatus();
            int ni = 0, si = 0, ti = 0;
            values[ni++] = pc.getLevel();
            values[ni++] = pc.getHighLevel();
            values[ni++] = pc.getExp();
            values[ni++] = pc.getBaseMaxHp();
            values[ni++] = Math.max(1, pc.getCurrentHp());
            values[ni++] = pc.getBaseMaxMp();
            values[ni++] = pc.getCurrentMp();
            values[ni++] = pc.getAC().getAc();
            values[ni++] = pc.getAbility().getStr();
            values[ni++] = pc.getAbility().getBaseStr();
            values[ni++] = pc.getAbility().getCon();
            values[ni++] = pc.getAbility().getBaseCon();
            values[ni++] = pc.getAbility().getDex();
            values[ni++] = pc.getAbility().getBaseDex();
            values[ni++] = pc.getAbility().getCha();
            values[ni++] = pc.getAbility().getBaseCha();
            values[ni++] = pc.getAbility().getInt();
            values[ni++] = pc.getAbility().getBaseInt();
            values[ni++] = pc.getAbility().getWis();
            values[ni++] = pc.getAbility().getBaseWis();
            values[ni++] = pc.getCurrentWeapon();
            values[ni++] = pc.getClassId();
            values[ni++] = pc.get_sex();
            values[ni++] = pc.getType();
            values[ni++] = pc.getHeading();
            values[ni++] = pc.getX();
            values[ni++] = pc.getY();
            values[ni++] = pc.getMapId();
            values[ni++] = pc.get_food();
            values[ni++] = pc.getLawful();
            strings[si++] = pc.getTitle();
            values[ni++] = pc.getClanid();
            strings[si++] = pc.getClanname();
            values[ni++] = pc.getClanRank();
            strings[si++] = pc.getClanMemberNotes();
            values[ni++] = pc.getBonusStats();
            values[ni++] = pc.getElixirStats();
            values[ni++] = pc.getElfAttr();
            values[ni++] = pc.get_PKcount();
            values[ni++] = pc.getExpRes();
            values[ni++] = pc.getPartnerId();
            values[ni++] = pc.getAccessLevel();
            values[ni++] = pc.getOnlineStatus();
            values[ni++] = pc.getHomeTownId();
            values[ni++] = pc.getContribution();
            values[ni++] = (pc.isBanned()) ? 1 : 0;
            values[ni++] = pc.getKarma();
            times[ti++] = copyStamp(pc.getLastPk());
            times[ti++] = copyStamp(pc.getDeleteTime());
            values[ni++] = pc.getReturnStat();
            values[ni++] = pc.getSealScrollTime();
            values[ni++] = pc.getSealScrollCount();
            values[ni++] = pc.getAinState();
            values[ni++] = pc.getSurvivalGauge();
            values[ni++] = pc.getMark_count();
            values[ni++] = pc.getAge();
            values[ni++] = pc.getAddDamage();
            values[ni++] = pc.getAddDamageRate();
            values[ni++] = pc.getAddReduction();
            values[ni++] = pc.getAddReductionRate();
            values[ni++] = pc.getBug_check();
            values[ni++] = pc.getKir_time();
            values[ni++] = pc.getBcol_y();
            values[ni++] = pc.getBcol_m();
            values[ni++] = pc.getBcol_l();
            values[ni++] = pc.getBcol_h();
            values[ni++] = pc.getLevel_q();
            times[ti++] = copyStamp(pc.getTamTime());
            values[ni++] = pc.get_SpecialSize();
            strings[si++] = pc.getFishingShopBuyTime_1() == 0 ? "0000-00-00 00:00:00" : new Timestamp(pc.getFishingShopBuyTime_1()).toString();
            values[ni++] = pc.getSlotNumber();
            values[ni++] = pc.getElfAttrInitCount();
            times[ti++] = copyStamp(pc.getLastTopBless());
            strings[si++] = pc.encodeHpMpGrowth();
            } finally { pc.unlockUserState(); }
        }

        /** Compare the exact SQL-bound values, including session ownership. */
        public boolean sameData(CharacterSnapshot other) {
            return other != null
                    && objectId == other.objectId
                    && sessionEpoch == other.sessionEpoch
                    && onlineStatus == other.onlineStatus
                    && java.util.Arrays.equals(values, other.values)
                    && java.util.Arrays.equals(strings, other.strings)
                    && java.util.Arrays.equals(times, other.times);
        }

        private static Timestamp copyStamp(Timestamp timestamp) {
            return timestamp == null ? null : (Timestamp) timestamp.clone();
        }

        public void write() throws SQLException {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(CHARACTER_STORE_SQL)) {
                int index = 0, ni = 0, si = 0, ti = 0;
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setString(++index, strings[si++]);
                ps.setInt(++index, values[ni++]);
                ps.setString(++index, strings[si++]);
                ps.setInt(++index, values[ni++]);
                ps.setString(++index, strings[si++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setShort(++index, (short) values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setBoolean(++index, values[ni++] != 0);
                ps.setInt(++index, values[ni++]);
                ps.setTimestamp(++index, times[ti++]);
                ps.setTimestamp(++index, times[ti++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, onlineStatus);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setTimestamp(++index, times[ti++]);
                ps.setInt(++index, values[ni++]);
                ps.setString(++index, strings[si++]);
                ps.setInt(++index, values[ni++]);
                ps.setInt(++index, values[ni++]);
                ps.setTimestamp(++index, times[ti++]);
                ps.setString(++index, strings[si++]);
                ps.setInt(++index, objectId);
                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement exists = con.prepareStatement(
                            "SELECT 1 FROM characters WHERE objid=? LIMIT 1")) {
                        exists.setInt(1, objectId);
                        try (ResultSet rs = exists.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("Character save target missing: " + name + " (" + objectId + ")");
                            }
                        }
                    }
                }
            }
        }
    }

    private static final String CHARACTER_STORE_SQL = "UPDATE characters SET level=?, HighLevel=?, Exp=?, MaxHp=?"
				+ ", CurHp=?, MaxMp=?, CurMp=?, Ac=?, Str=?, BaseStr=?, Con=?, BaseCon=?, Dex=?, BaseDex=?"
				+ ", Cha=?, BaseCha=?, Intel=?, BaseIntel=?, Wis=?, BaseWis=?, Status=?, Class=?, Sex=?, Type=?"
				+ ", Heading=?, LocX=?, LocY=?, MapID=?, Food=?, Lawful=?, Title=?, ClanID=?, Clanname=?, ClanRank=?"
				+ ", notes=?, BonusStatus=?, ElixirStatus=?, ElfAttr=?, PKcount=?, ExpRes=?, PartnerID=?, AccessLevel=?"
				+ ", OnlineStatus=?, HomeTownID=?, Contribution=?, Banned=?, Karma=?, LastPk=?"
				+ ", DeleteTime=?, ReturnStat=?, sealScrollTime=?, sealScrollCount=?, lastLogoutTime = IF(? = 0, NOW(), lastLogoutTime)"
				+ ", AinState=?, SurvivalGauge=?"
				+ ", Mark_Count=?, Age=?, AddDamage=?, AddDamageRate=?, AddReduction=?, AddReductionRate=?, bug_check=?, kir_time=?, col_y=?, col_m=?, col_l=?, col_h=?, level_q=?,"
				+ " TamEndTime=?, SpecialSize=?, FishingShopBuyTime_1=?, slot_number=?, elf_attrinit_count=?, last_topBless=?, hp_mp_growth=? WHERE objid=?";

    @Override
    public void storeCharacter(L1PcInstance pc) {
        CacheD.getInstance().saveCharacter(new CharacterSnapshot(pc));

    }


	public static boolean isValidUserObjectId(int objectId) {
		final MJObjectWrapper<Boolean> wrapper = new MJObjectWrapper<>();
		wrapper.value = false;
		Selector.exec("SELECT objid FROM characters WHERE objid = ?", new SelectorHandler() {
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, objectId);
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				wrapper.value = rs.next();
			}
		});
		return wrapper.value;
	}
}