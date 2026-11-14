package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_Trade;


import l1j.server.server.model.network.core.sc.packet.SC_Resurrection;
import java.util.Collection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import l1j.server.MJKDASystem.MJKDA;
import l1j.server.MJKDASystem.MJKDALoader;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.Random;

import l1j.server.Config;
import l1j.server.IndunSystem.ClanDungeon.AzmodanSystem;
import l1j.server.MJInstanceSystem.MJInstanceEnums.InstStatus;
import l1j.server.MJInstanceSystem.MJLFC.Creator.MJLFCCreator;
import l1j.server.MJSurveySystem.MJSurveySystemLoader;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJTemplate.Lineage2D.MJPoint;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.MJWarSystem.MJWar;
import l1j.server.MJWarSystem.MJWarFactory;
import l1j.server.server.Account;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.BossMonsterSpawnList;
import l1j.server.server.datatables.BuddyTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.model.L1Buddy;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1ChatParty;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.L1DeathMatch;
import l1j.server.server.model.L1HauntedHouse;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1Racing;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.item.Card.L1CardCollection;
import l1j.server.server.model.item.Card.L1SkillTree;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import LIN.SC_WORLD_PUT_OBJECT_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ClanAttention;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Boss;
import l1j.server.server.templates.L1House;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Pet;

import l1j.server.server.model.network.core.sc.packet.SC_CharAmount;
public final class CS_Attr {

	public static final int MSGCODE_622_BOSS 				= 1;
	public static final int MSGCODE_622_LFC					= 2;
	public static final int MSGCODE_622_KDINIT				= 3;
	public static final int MSGCODE_622_Name				= 6;
	public static final int MSGCODE_622_Card				= 7;
	public static final int MSGCODE_622_Card2				= 8;
	public static final int MSGCODE_622_Card3				= 9;
	public static final int MSGCODE_622_Tree				= 10;
	public static final int MSGCODE_622_Lawful				= 11;

	public static final int MSGCODE_NO 			= 0;
	public static final int MSGCODE_YES 			= 1;


	private CS_Attr() {
	}

    private static boolean unavailable(L1PcInstance pc) {
        return pc == null || pc.isItemTransferBusy() || pc.isTradeResolving() || pc.getTradeID() != 0;
    }
    private static void confirmationFailed(L1PcInstance pc, RuntimeException failure) {
        java.util.logging.Logger.getLogger(CS_Attr.class.getName()).log(
                java.util.logging.Level.WARNING, "Confirmation economy preparation failed", failure);
        SC_SystemMessage.send(pc, "처리를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
    /** Cost and persisted K/D image are committed together. Positive PVP events
     * that arrive while SQL is pending remain as deltas after the reset.
     */
    public static void resetKillDeath(L1PcInstance pc, L1ItemInstance item,
            boolean resetKill, boolean resetDeath) {
        if (unavailable(pc) || item == null || (!resetKill && !resetDeath)) return;
        final MJKDA kda = pc.getKDA();
        if (kda == null || kda.isBot || pc.getInventory().getItem(item.getId()) != item) return;
        final MJKDALoader loader = MJKDALoader.getInstance();
        try {
            synchronized (loader) {
                // Queue the current authoritative counters before reserving the
                // same existing global lane; no JDBC or commit wait here.
                final int beforeKill, beforeDeath;
                synchronized (kda) {
                    loader.store();
                    beforeKill = kda.kill; beforeDeath = kda.death;
                }
                if (beforeKill < 0 || beforeDeath < 0
                        || ((!resetKill || beforeKill == 0) && (!resetDeath || beforeDeath == 0))) return;
                try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, MJKDALoader.orderKey())) {
                    if (!tx.debit(item, 1)) return;
                    tx.requireTables("tb_kda");
                    tx.sql(con -> resetKillDeathRow(con, pc.getId(), beforeKill, beforeDeath, resetKill, resetDeath));
                    tx.commit(() -> {
                        synchronized (kda) {
                            if (pc.getKDA() != kda || kda.kill < beforeKill || kda.death < beforeDeath)
                                throw new IllegalStateException("K/D reset memory preimage changed");
                            if (resetKill) kda.kill -= beforeKill;
                            if (resetDeath) kda.death -= beforeDeath;
                        }
                        loader.storeCommittedReset(kda);
                        SC_SystemMessage.send(pc, "킬/데스 초기화가 완료되었습니다.");
                    });
                }
            }
        } catch (RuntimeException failure) { confirmationFailed(pc, failure); }
    }
    public static void resetKillDeathRow(Connection con, int owner, int beforeKill, int beforeDeath,
            boolean resetKill, boolean resetDeath) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE tb_kda SET killing=?, death=? WHERE objid=? AND killing=? AND death=?")) {
            ps.setInt(1, resetKill ? 0 : beforeKill); ps.setInt(2, resetDeath ? 0 : beforeDeath);
            ps.setInt(3, owner); ps.setInt(4, beforeKill); ps.setInt(5, beforeDeath);
            if (ps.executeUpdate() != 1) throw new SQLException("K/D reset before-image changed: " + owner);
        }
    }
    private static void lockLawful(L1PcInstance pc) {
        if (unavailable(pc) || pc.getLawfulStop() != 0 || pc.hasSkillEffect(L1SkillId.라우풀고정)) return;
        final int owner = pc.getId();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                CacheD.accountOrderKey("lawful-lock:" + owner))) {
            if (!tx.debit(L1ItemId.ADENA, 10000000)) {
                SC_SystemMessage.send(pc, "아데나가 부족합니다. [비용 :1,000만 아데나]"); return;
            }
            tx.requireTables("characters", "character_buff");
            tx.sql(con -> insertLawfulLock(con, owner));
            tx.commit(() -> {
                if (pc.getLawfulStop() != 0 || pc.hasSkillEffect(L1SkillId.라우풀고정))
                    throw new IllegalStateException("Lawful lock memory preimage changed");
                pc.setLawfulStop(1);
                pc.setSkillEffect(L1SkillId.라우풀고정, -1);
                SC_SystemMessage.send(pc, "캐릭터의 성향이 PVP와는 관계 없도록 설정되었습니다.");
            });
        } catch (RuntimeException failure) { confirmationFailed(pc, failure); }
    }
    public static void insertLawfulLock(Connection con, int owner) throws SQLException {
        // The character row anchors absence locks even on schemas without a
        // unique (owner,skill) key. Do not use an unqualified upsert.
        try (PreparedStatement ps = con.prepareStatement("SELECT objid FROM characters WHERE objid=? FOR UPDATE")) {
            ps.setInt(1, owner);
            try (ResultSet rows = ps.executeQuery()) { if (!rows.next()) throw new SQLException("Character missing"); }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT skill_id FROM character_buff WHERE char_obj_id=? AND skill_id=? FOR UPDATE")) {
            ps.setInt(1, owner); ps.setInt(2, L1SkillId.라우풀고정);
            try (ResultSet rows = ps.executeQuery()) { if (rows.next()) throw new SQLException("Lawful lock already persisted"); }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO character_buff(char_obj_id,skill_id,remaining_time,poly_id) VALUES(?,?,0,0)")) {
            ps.setInt(1, owner); ps.setInt(2, L1SkillId.라우풀고정);
            if (ps.executeUpdate() != 1) throw new SQLException("Lawful lock insert failed");
        }
    }
    private static void recoverExperience(L1PcInstance pc, boolean scroll) {
        if (unavailable(pc) || Config.STANDBY_SERVER || pc.getExpRes() != 1) return;
        final int before = pc.getExp(), level = pc.getLevel(), owner = pc.getId();
        final int delta = (int) (ExpTable.getNeedExpNextLevel(level) * (scroll ? 0.05 : 0.025));
        final int target = (int) Math.min(ExpTable.MAX_EXP, (long) before + Math.max(0, delta));
        if (target == before) return;
        if (ExpTable.getLevelByExp(target) != level) {
            SC_SystemMessage.send(pc, "레벨이 변경되는 경험치 복구는 현재 지원하지 않습니다. 비용은 차감되지 않았습니다.");
            return;
        }
        long cost = (long) level * level * (level < 45 ? 50 : 150);
        if (pc.getLawful() >= 0) cost = (long) (cost * 0.7);
        if (cost <= 0 || cost > Integer.MAX_VALUE) return;
        try {
            // Serialize the current character state before the economy receipt;
            // construct the immutable save directly, without pc.save's other I/O.
            CacheD.getInstance().saveCharacter(new CharacterSnapshot(pc));
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                    CacheD.accountOrderKey("experience-recovery:" + owner))) {
                if (!tx.debit(scroll ? 3000049 : L1ItemId.ADENA, scroll ? 1 : (int) cost)) {
                    SC_ServerMessage.send(pc, 189); return;
                }
                tx.requireTables("characters");
                tx.sql(con -> recoverExperienceRow(con, owner, level, before, target));
                tx.commit(() -> {
                    if (pc.getExp() != before || pc.getExpRes() != 1 || pc.getLevel() != level)
                        throw new IllegalStateException("Experience recovery memory preimage changed");
                    pc.setExp(target); pc.setExpRes(0);
                    // Same-level packet/buff refresh only. The old levelUp path
                    // has extra random stats/quest/item mutations outside receipt.
                    pc.onChangeExp();
                });
            }
        } catch (RuntimeException failure) { confirmationFailed(pc, failure); }
    }
    public static void recoverExperienceRow(Connection con, int owner, int level, int before, int target)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE characters SET Exp=?, ExpRes=0 WHERE objid=? AND level=? AND Exp=? AND ExpRes=1")) {
            ps.setInt(1, target); ps.setInt(2, owner); ps.setInt(3, level); ps.setInt(4, before);
            if (ps.executeUpdate() != 1) throw new SQLException("Experience recovery before-image changed: " + owner);
        }
    }

	public static void handle(
			CIOClient client,
			int attrcode,
			int msgIdx,
			int c,
			String name) {

		if (client == null) return;

		final L1PcInstance pc = client.getActiveChar();
		if (pc == null) return;

		if (attrcode == 622) {
			switch(msgIdx){
			case MSGCODE_622_KDINIT:
                L1ItemInstance resetItem = pc.getKillDeathInitializeItem();
                pc.setKillDeathInitializeItem(null);
                if (c == MSGCODE_YES) resetKillDeath(pc, resetItem, true, true);
                return;
            case MSGCODE_622_Lawful:
                if (c == MSGCODE_YES) lockLawful(pc);
                return;
			case MSGCODE_622_Name:
				if (c == MSGCODE_YES) {
					L1ItemInstance item = pc.getNameInstance();
					if (item != null) {
						int[] MALE_LIST = new int[] { 0, 61, 138, 734, 2786, 6658, 6671, 12490 };
						int[] FEMALE_LIST = new int[] { 1, 48, 37, 1186, 2796, 6661, 6650, 12494 };
						if (pc.get_sex() == 0) {
							pc.set_sex(1);
							pc.setClassId(FEMALE_LIST[pc.getType()]);
						} else {
							pc.set_sex(0);
							pc.setClassId(MALE_LIST[pc.getType()]);
						}
						pc.setCurrentSprite(pc.getClassId());
						pc.sendShape(pc.getClassId());
						pc.getInventory().removeItem(item, 1);
					}
					ThreadPool.getInstance().scheduleRequired(new Runnable(){
						@Override
						public void run(){
							CIOClient clnt = pc.getNetConnection();
							CS_CharSelect.restartProcess(pc);
                            CS_CommonClick.sendCharAmountAndPacks(clnt);							
						}
					}, 500L);
				}
				pc.setNameInstance(null);
				return;
			case MSGCODE_622_Card2:{
				if (c == MSGCODE_YES) {
					L1ItemInstance itemc3 = pc.getCollecItem();
					if (itemc3 == null) return;
					int	itemId3 = itemc3.getItem().getItemId();
					String itemname3 = itemc3.getItem().getNameId();
					if(itemId3 == 875640306){pc.getAccount().setInfo_ds_poly(pc.getAccount().getInfo_ds_poly()+1);}
					else if(itemId3 == 875640307){pc.getAccount().setInfo_dru_poly(pc.getAccount().getInfo_dru_poly()+1);}
					else if(itemId3 == 875640308){pc.getAccount().setInfo_ga_poly(pc.getAccount().getInfo_ga_poly()+1);}
					else if(itemId3 == 875640309){pc.getAccount().setInfo_ger_poly(pc.getAccount().getInfo_ger_poly()+1);}
					else if(itemId3 == 875640310){pc.getAccount().setInfo_dev_poly(pc.getAccount().getInfo_dev_poly()+1);}
					else if(itemId3 == 875640311){pc.getAccount().setInfo_pen_poly(pc.getAccount().getInfo_pen_poly()+1);}
					else if(itemId3 == 875640312){pc.getAccount().setInfo_atun_poly(pc.getAccount().getInfo_atun_poly()+1);}
					else if(itemId3 == 875640313){pc.getAccount().setInfo_moon_poly(pc.getAccount().getInfo_moon_poly()+1);}
					else if(itemId3 == 875640314){pc.getAccount().setInfo_mar_poly(pc.getAccount().getInfo_mar_poly()+1);}
					else{SC_ChatPacket.sendDirect(pc, pc, "\\aG올바르지 않은 접근입니다.");return;}
					pc.getInventory().removeItem(itemc3, 1);
					pc.getAccount().setInfo_col_l(pc.getAccount().getInfo_col_l() +1);
					pc.getAccount().updateLegend();
					L1CardCollection.Join(pc);
					SC_ChatPacket.sendDirect(pc, pc, "\\aG"+itemname3 +"(이)가 변신 컬렉션에 등록되었습니다.");
				}
				pc.setCollecItem(null);
				return;
			}
			case MSGCODE_622_Card3:{
				if (c == MSGCODE_YES) {
					L1ItemInstance itemc4 = pc.getCollecItem();
					if (itemc4 == null) return;
					int	itemId4 = itemc4.getItem().getItemId();
					String itemname4 = itemc4.getItem().getNameId();
					if(itemId4 == 875640296){pc.getAccount().setInfo_class_poly(pc.getAccount().getInfo_class_poly()+1);}
					else if(itemId4 == 875640297){pc.getAccount().setInfo_ki_poly(pc.getAccount().getInfo_ki_poly()+1);}
					else if(itemId4 == 875640298){pc.getAccount().setInfo_el_poly(pc.getAccount().getInfo_el_poly()+1);}
					else if(itemId4 == 875640299){pc.getAccount().setInfo_wi_poly(pc.getAccount().getInfo_wi_poly()+1);}
					else if(itemId4 == 875640300){pc.getAccount().setInfo_da_poly(pc.getAccount().getInfo_da_poly()+1);}
					else if(itemId4 == 875640301){pc.getAccount().setInfo_wa_poly(pc.getAccount().getInfo_wa_poly()+1);}
					else if(itemId4 == 875640302){pc.getAccount().setInfo_il_poly(pc.getAccount().getInfo_il_poly()+1);}
					else if(itemId4 == 875640303){pc.getAccount().setInfo_drag_poly(pc.getAccount().getInfo_drag_poly()+1);}
					else if(itemId4 == 875640304){pc.getAccount().setInfo_dsn_poly(pc.getAccount().getInfo_dsn_poly()+1);}
					else if(itemId4 == 875640305){pc.getAccount().setInfo_bapo_poly(pc.getAccount().getInfo_bapo_poly()+1);}
					else{SC_ChatPacket.sendDirect(pc, pc, "\\aG올바르지 않은 접근입니다.");return;}

					pc.getInventory().removeItem(itemc4, 1);
					pc.getAccount().setInfo_col_m(pc.getAccount().getInfo_col_m() +1);
					pc.getAccount().updateMyth();
					L1CardCollection.Join(pc);
					SC_ChatPacket.sendDirect(pc, pc, "\\aG"+itemname4 +"(이)가 변신 컬렉션에 등록되었습니다.");
				}
				pc.setCollecItem(null);
				return;
			}
			case MSGCODE_622_Card:{
				if (c == MSGCODE_YES && c != 0) {
					L1ItemInstance itemc = pc.getCollecItem();
					if (itemc == null) { pc.setCollecItem(null); return; }
					int	itemId2 = itemc.getItem().getItemId();
					String itemname = itemc.getItem().getNameId();				
					if(itemId2 == 875640315){pc.getAccount().setInfo_heaven_poly(pc.getAccount().getInfo_heaven_poly()+1);}
					else if(itemId2 == 875640316){pc.getAccount().setInfo_val_poly(pc.getAccount().getInfo_val_poly()+1);}
					else if(itemId2 == 875640317){pc.getAccount().setInfo_larry_poly(pc.getAccount().getInfo_larry_poly()+1);}
					else if(itemId2 == 875640318){pc.getAccount().setInfo_gunter_poly(pc.getAccount().getInfo_gunter_poly()+1);}
					else if(itemId2 == 875640319){pc.getAccount().setInfo_depor_poly(pc.getAccount().getInfo_depor_poly()+1);}
					else if(itemId2 == 875640320){pc.getAccount().setInfo_deathred_poly(pc.getAccount().getInfo_deathred_poly()+1);}
					else if(itemId2 == 875640321){pc.getAccount().setInfo_ecil_poly(pc.getAccount().getInfo_ecil_poly()+1);}
					else if(itemId2 == 875640322){pc.getAccount().setInfo_assm_poly(pc.getAccount().getInfo_assm_poly()+1);}
					else if(itemId2 == 875640323){pc.getAccount().setInfo_kerep_poly(pc.getAccount().getInfo_kerep_poly()+1);}
					else if(itemId2 == 875640324){pc.getAccount().setInfo_jowo_poly(pc.getAccount().getInfo_jowo_poly()+1);}
					else if(itemId2 == 875640325){pc.getAccount().setInfo_unik_poly(pc.getAccount().getInfo_unik_poly()+1);}
					else if(itemId2 == 875640326){pc.getAccount().setInfo_unim_poly(pc.getAccount().getInfo_unim_poly()+1);}
					else if(itemId2 == 875640327){pc.getAccount().setInfo_unir_poly(pc.getAccount().getInfo_unir_poly()+1);}
					else if(itemId2 == 875640328){pc.getAccount().setInfo_yozil_poly(pc.getAccount().getInfo_yozil_poly()+1);}
					else if(itemId2 == 875640329){pc.getAccount().setInfo_owen_poly(pc.getAccount().getInfo_owen_poly()+1);}
					else if(itemId2 == 875640330){pc.getAccount().setInfo_helv_poly(pc.getAccount().getInfo_helv_poly()+1);}
					else{
						SC_ChatPacket.sendDirect(pc, pc, "\\aG올바르지 않은 접근입니다.");return;
					}
					pc.getInventory().removeItem(itemc, 1);
					pc.getAccount().setInfo_col_h(pc.getAccount().getInfo_col_h() +1);
					pc.getAccount().updateHero();
					L1CardCollection.Join(pc);
					SC_ChatPacket.sendDirect(pc, pc, "\\aG"+itemname +"(이)가 변신 컬렉션에 등록되었습니다.");
				} else {
					pc.setCollecItem(null);
					return;
				}
				pc.setCollecItem(null);
				return;
			}
			case MSGCODE_622_Tree:{
				if (c == MSGCODE_YES) {
					int codenum = 0;
					L1ItemInstance itemsc = pc.getCollecItem();
					if (itemsc == null) { pc.setCollecItem(null); return; }
					int	tree = itemsc.getItem().getItemId();
					String treename = itemsc.getItem().getNameId();
					if(tree == 875640331 && pc.getAccount().getTree_iceq() != 0){SC_ChatPacket.sendDirect(pc, pc, "\\aG이미 추가된 수호성 스킬입니다.");return;}
					if(tree == 875640332 && pc.getAccount().getTree_fireq() != 0){SC_ChatPacket.sendDirect(pc, pc, "\\aG이미 추가된 수호성 스킬입니다.");return;}
					if(tree == 875640333 && pc.getAccount().getTree_final() != 0){SC_ChatPacket.sendDirect(pc, pc, "\\aG이미 추가된 수호성 스킬입니다.");return;}
					if(tree == 875640334 && pc.getAccount().getTree_ices() != 0){SC_ChatPacket.sendDirect(pc, pc, "\\aG이미 추가된 수호성 스킬입니다.");return;}
					if(tree == 875640335 && pc.getAccount().getTree_rain() != 0){SC_ChatPacket.sendDirect(pc, pc, "\\aG이미 추가된 수호성 스킬입니다.");return;}
					if(tree == 875640336 && pc.getAccount().getTree_cri() != 0){SC_ChatPacket.sendDirect(pc, pc, "\\aG이미 추가된 수호성 스킬입니다.");return;}
					if(tree == 875640337 && pc.getAccount().getTree_crim1() != 0){SC_ChatPacket.sendDirect(pc, pc, "\\aG이미 추가된 수호성 스킬입니다.");return;}
					if(tree == 875640338 && pc.getAccount().getTree_crim2() != 0){SC_ChatPacket.sendDirect(pc, pc, "\\aG이미 추가된 수호성 스킬입니다.");return;}
					if(tree == 875640339 && pc.getAccount().getTree_ecil1() != 0){SC_ChatPacket.sendDirect(pc, pc, "\\aG이미 추가된 수호성 스킬입니다.");return;}
					if(tree == 875640340 && pc.getAccount().getTree_ecil2() != 0){SC_ChatPacket.sendDirect(pc, pc, "\\aG이미 추가된 수호성 스킬입니다.");return;}
					if(tree == 875640341 && pc.getAccount().getTree_ecil3() != 0){SC_ChatPacket.sendDirect(pc, pc, "\\aG이미 추가된 수호성 스킬입니다.");return;}

					if(tree == 875640331 && pc.getAccount().getTree_iceq() == 0){pc.getAccount().setTree_iceq(1); codenum = 1;}
					if(tree == 875640332 && pc.getAccount().getTree_fireq() == 0){pc.getAccount().setTree_fireq(1);codenum = 1;}
					if(tree == 875640333 && pc.getAccount().getTree_final() == 0){pc.getAccount().setTree_final(1);codenum = 1;}
					if(tree == 875640334 && pc.getAccount().getTree_ices() == 0){pc.getAccount().setTree_ices(1);codenum = 1;}
					if(tree == 875640335 && pc.getAccount().getTree_rain() == 0){pc.getAccount().setTree_rain(1);codenum = 1;}
					if(tree == 875640336 && pc.getAccount().getTree_cri() == 0){pc.getAccount().setTree_cri(1);codenum = 2;}
					if(tree == 875640337 && pc.getAccount().getTree_crim1() == 0){pc.getAccount().setTree_crim1(1);codenum = 2;}
					if(tree == 875640338 && pc.getAccount().getTree_crim2() == 0){pc.getAccount().setTree_crim2(1);codenum = 2;}
					if(tree == 875640339 && pc.getAccount().getTree_ecil1() == 0){pc.getAccount().setTree_ecil1(1);codenum = 3;}
					if(tree == 875640340 && pc.getAccount().getTree_ecil2() == 0){pc.getAccount().setTree_ecil2(1);codenum = 3;}
					if(tree == 875640341 && pc.getAccount().getTree_ecil3() == 0){pc.getAccount().setTree_ecil3(1);codenum = 3;}

					pc.getInventory().removeItem(itemsc, 1);
					pc.getAccount().updateTree();
					if(codenum == 1){L1SkillTree.Info(pc);}
					else if(codenum == 2){L1SkillTree.Info2(pc);}
					else{L1SkillTree.Info3(pc);}
					SC_ChatPacket.sendDirect(pc, pc, "\\aG"+treename +"(이)가 수호성에 등록되었습니다.");
				}
				pc.setCollecItem(null);
				return;
			}
			case MSGCODE_622_LFC:{
				if(c == MSGCODE_NO){
					MJLFCCreator.setInstStatus(pc, InstStatus.INST_USERSTATUS_NONE);
				}else if(c == MSGCODE_YES){
					if(pc.getInstStatus() == InstStatus.INST_USERSTATUS_LFCREADY)
						MJLFCCreator.setInstStatus(pc, InstStatus.INST_USERSTATUS_LFCINREADY);
				}
				return;
			}
			case MSGCODE_622_BOSS: {
				if (c == MSGCODE_NO) {
					if (pc.getLevel() < Config.YN_pclevel) {
						SC_SystemMessage.send(pc, "레벨 " + Config.YN_pclevel + " 부터 이용가능 합니다.");
						return;
					}
					SC_SystemMessage.send(pc, "보스이동 참가를 거절 하셨습니다.");
					pc.setBossYN(0);
				} else if (c == MSGCODE_YES) {
					if (pc.getLevel() < Config.YN_pclevel) {
						SC_SystemMessage.send(pc, "레벨 "+Config.YN_pclevel+" 부터 이용가능 합니다.");
						return;
					}

					L1Boss boss = BossMonsterSpawnList.find(pc.getBossYN());
					if (boss == null)
						SC_SystemMessage.send(pc, "선택 딜레이가 지나 이용이 불가능 합니다(10초)");
					else {
						L1Map m	= L1WorldMap.getInstance().getMap((short)boss.getMap());
						int x = boss.getX();
						int y = boss.getY();
						int cx = 0;
						int cy = 0;
						int current_try = 0;
						int limit_try = 100;
						do{
							cx = x + (MJRnd.isBoolean() ? MJRnd.next(5) : -MJRnd.next(5));
							cy = y + (MJRnd.isBoolean() ? MJRnd.next(5) : -MJRnd.next(5));
						}while(++current_try < limit_try && !MJPoint.isValidPosition(m, cx, cy));
						if(current_try >= limit_try){
							cx = x;
							cy = y;
						}
						pc.start_teleport(cx, cy, (short) boss.getMap(), pc.getHeading(), 169, true, false);
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "해당 보스구역으로 이동 되었습니다.");
					}
				}
				break;
			}
			}

			if(MJSurveySystemLoader.getInstance().isSurvey(msgIdx)){
				MJSurveySystemLoader.getInstance().submitSurvey(pc, msgIdx, c == 1);
				return;
			}
		}

		switch (attrcode) {
        case 6008:
            if (msgIdx == 48) {
                l1j.server.server.packets.Protocol.Packet.GludioDungeonPackets.inviteResponse(pc,c==MSGCODE_YES);
            }
            return;
		case 180:
			boolean ring = false;
			if (name != null && name.startsWith("maple")) {
				name = name.replace("maple ", "");
			}

			if (name != null && name.equalsIgnoreCase("ranking class polymorph")) {
				if (pc.getRankLevel() < 3)
					return;
				switch (pc.getType()) {
				case 0: name = pc.get_sex() == 0 ? "rangking prince male" : "rangking prince female"; break;
				case 1: name = pc.get_sex() == 0 ? "rangking knight male" : "rangking knight female"; break;
				case 2: name = pc.get_sex() == 0 ? "rangking elf male" : "rangking elf female"; break;
				case 3: name = pc.get_sex() == 0 ? "rangking wizard male" : "rangking wizard female"; break;
				case 4: name = pc.get_sex() == 0 ? "rangking darkelf male" : "rangking darkelf female"; break;
				case 5: name = pc.get_sex() == 0 ? "rangking dragonknight male" : "rangking dragonknight female"; break;
				case 6: name = pc.get_sex() == 0 ? "rangking illusionist male" : "rangking illusionist female"; break;
				case 7: name = pc.get_sex() == 0 ? "rangking warrior male" : "rangking warrior female"; break;
				}
			}

			if (name != null && name.length() > 0) {
				L1PolyMorph poly = PolyTable.getInstance().getTemplate(name);
				if (poly != null || name.equals("")) {
					if (name.equals("")) {
						int spriteId = pc.getCurrentSpriteId();
						if (spriteId == 6034 || spriteId == 6035) {
						} else {
							if (pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
								pc.removeSkillEffect(L1SkillId.POLY_RING_MASTER);
							}
							pc.removeSkillEffect(L1SkillId.SHAPE_CHANGE);
						}
					} else if (poly.getMinLevel() <= pc.getLevel() || pc.isGm()) {
						if (pc.isPolyRingMaster()) {
							ring = true;
						}
						L1PolyMorph.doPoly(pc, poly.getPolyId(), 7200, L1PolyMorph.MORPH_BY_ITEMMAGIC, ring);
					}
				}
			}
			break;
		case 97:
			L1PcInstance joinPc = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			if (joinPc != null) {
				if (c == 0) {
					SC_ServerMessage.send(joinPc, 96, pc.getName());
				} else if (c == 1) {
					try {
						L1ClanJoin.getInstance().ClanJoin(pc.getClan(), joinPc);
					} catch (Exception e) {
					}
				}
			}
			break;
		case 3348:
			if (c == 1) {
				L1PcInstance GazePc = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
				pc.setTempID(0);
				if(GazePc == null) return;
				L1Clan targetClan = L1World.getInstance().findClan(GazePc.getClanname());
				if (targetClan == null) return;

				L1Clan pcClan = L1World.getInstance().findClan(pc.getClanname());
				if (pcClan == null) return;

				targetClan.addGazelist(pcClan.getClanName());
				pcClan.addGazelist(targetClan.getClanName());

				for (L1PcInstance member : pcClan.getOnlineClanMember()) {
					SC_ClanAttention.sendMent(member, true, targetClan.getClanName());
					SC_ClanAttention.sendList(member, pcClan.getGazeSize(), pcClan.getGazeList());
				}

				for (L1PcInstance member : targetClan.getOnlineClanMember()) {
					SC_ClanAttention.sendMent(member, true, pcClan.getClanName());
					SC_ClanAttention.sendList(member, targetClan.getGazeSize(), targetClan.getGazeList());
				}
			}
			break;
		case 217:
		case 221:
		case 222:
			L1PcInstance enemyLeader = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			if (enemyLeader == null) return;
			pc.setTempID(0);
			String clanName = pc.getClanname();
			String enemyClanName = enemyLeader.getClanname();
			if (c == 0) {
				if (attrcode == 217) {
					SC_ServerMessage.send(enemyLeader, 236, clanName);
				} else if (attrcode == 221 || attrcode == 222) {
					SC_ServerMessage.send(enemyLeader, 237, clanName);
				}
			} else if (c == 1) {
				L1Clan defense = L1World.getInstance().findClan(clanName);
				L1Clan offense = L1World.getInstance().findClan(enemyClanName);
				if(defense == null || offense == null) return;

				if (attrcode == 217) {
					MJWar war = MJWarFactory.createNormalWar(defense);
					war.register(offense);
				} else{
					MJWar war = offense.getCurrentWar();
					if(war == null) return;
					war.notifySurrender(defense, offense);
					war.dispose();
				}
			}
			break;
		case 4703:
            // The housekeeper has already disabled opening/entering this dungeon
            // during renewal. A forged confirmation must not bypass that policy
            // or debit clan funds (including a cancellation packet).
            SC_SystemMessage.send(pc, "리뉴얼 공사로인해 이용 불가능합니다.");
            break;
		case 252:
			L1PcInstance trading_partner = (L1PcInstance) L1World.getInstance().findObject(pc.getTradeID());
			L1Npc npc = NpcTable.getInstance().getTemplate(400064);
			L1Npc npc3 = NpcTable.getInstance().getTemplate(300027);
			if (trading_partner != null) {
				if (c == 0) {
					SC_ServerMessage.send(trading_partner, 253, pc.getName());
					pc.setTradeID(0);
					trading_partner.setTradeID(0);
				} else if (c == 1) {
					SC_Trade.send(pc, trading_partner.getName());
					SC_Trade.send(trading_partner, pc.getName());
				}
			} else {
				if (c == 0) {
					pc.setTradeID(0);
				} else if (c == 1) {
					if (pc.getX() == 33507 && pc.getY() == 32851 && pc.getMapId() == 4) {
						SC_Trade.send(pc, npc.get_name());
					} else if (pc.getX() == 33515 && pc.getY() == 32851 && pc.getMapId() == 4) {
						SC_Trade.send(pc, npc3.get_name());
					}
				}
			}
			break;
		case 321:
			L1PcInstance resusepc1 = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			if (resusepc1 != null) {
				if (c == 1) {
					SC_SkillSound.send(pc, pc.getId(), '\346');
					SC_SkillSound.broadcast(pc, pc.getId(), '\346');
					pc.resurrect(pc.getMaxHp() / 2);
					pc.setCurrentHp(pc.getMaxHp() / 2);
					TileOccupancy.getInstance().tryOccupyNow(pc.getMapId(), pc.getX(), pc.getY(), pc.getId());
					SC_Resurrection.send(pc, pc, resusepc1, 0);
					SC_Resurrection.broadcast(pc, pc, resusepc1, 0);
					SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
					SC_CharVisualUpdate.broadcastPacked(pc, SC_CharVisualUpdate.pack(pc));
				}
			}
			break;
		case 322:
			L1PcInstance resusepc2 = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			if (resusepc2 != null) {
				if (c == 1) {
					SC_SkillSound.send(pc, pc.getId(), '\346');
					SC_SkillSound.broadcast(pc, pc.getId(), '\346');
					pc.resurrect(pc.getMaxHp());
					pc.setCurrentHp(pc.getMaxHp());
					TileOccupancy.getInstance().tryOccupyNow(pc.getMapId(), pc.getX(), pc.getY(), pc.getId());
					SC_Resurrection.send(pc, pc, resusepc2, 0);
					SC_Resurrection.broadcast(pc, pc, resusepc2, 0);
					SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
					SC_CharVisualUpdate.broadcastPacked(pc, SC_CharVisualUpdate.pack(pc));
					if (pc.getExpRes() == 1 && pc.isGres() && pc.isGresValid()) {
						pc.resExp();
						pc.setExpRes(0);
						pc.setGres(false);
					}
				}
			}
			break;
		case 325:
			int len = name.length();
			if(len > 6 || len < 1){
				pc.sendPackets("펫 이름 길이를 확인해주세요.");
				pc.setTempID(0);
				break;
			}
			L1PetInstance pet = (L1PetInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			renamePet(pet, name);
			break;
		case 512:
			int houseId = pc.getTempID();
			pc.setTempID(0);
			if (name == null || name.isEmpty() || name.length() > 16) {
				SC_ServerMessage.send(pc, 513);
			} else {
				L1Clan ownClan = pc.getClan();
                if (!pc.isCrown() || ownClan == null || ownClan.getLeaderId() != pc.getId()
                        || ownClan.getHouseId() != houseId) {
                    SC_SystemMessage.send(pc, "아지트 이름 변경 권한이 없습니다.");
                } else {
                    l1j.server.server.model.L1AuctionTransaction.renameHouse(pc, houseId, name);
                }
			}
			break;
		case 622:
			BuddyTable buddyTable = BuddyTable.getInstance();
			L1Buddy buddyList = buddyTable.getBuddyTable(pc.getId());
			L1PcInstance target2 = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			String name2 = pc.getName();
			if (c == 0) {
				if (target2 != null) {
					SC_SystemMessage.send(target2, pc.getName() + "님이 친구 요청을 거절하였습니다.");
				} else if (pc.isSpecialBuff()) {
					pc.setSpecialBuff(false);
				}
			} else if (c == 1) {
				if (target2 != null) {
					buddyList.add(pc.getId(), name2);
					buddyTable.addBuddy(target2.getId(), pc.getId(), name2);
					SC_SystemMessage.send(target2, pc.getName() + "님이 친구 등록 되었습니다.");
					SC_SystemMessage.send(pc, target2.getName() + "님에게 친구 등록이 되었습니다.");
				} else if (pc.getRaidGame()) {
					pc.setRaidGame(false);
				} 
			}
			break;
		case 630:
			L1PcInstance fightPc = (L1PcInstance) L1World.getInstance().findObject(pc.getFightId());
			if (fightPc != null) {
				if (c == 0) {
					pc.setFightId(0);
					fightPc.setFightId(0);
					SC_ServerMessage.send(fightPc, 631, pc.getName());
				} else if (c == 1) {
					SC_PacketBox.send(fightPc, SC_PacketBox.MSG_DUEL, fightPc.getFightId(), fightPc.getId());
					SC_PacketBox.send(pc, SC_PacketBox.MSG_DUEL, pc.getFightId(), pc.getId());
				}
			}
			break;
		case 653:
			if (c == 1) {
				pc.setPartnerId(0);
				pc.save();
			}
			break;
		case 654:
			L1PcInstance partner = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			if (partner != null) {
				if (c == 0) {
					SC_ServerMessage.send(partner, 656, pc.getName());
				} else if (c == 1) {
					pc.setPartnerId(partner.getId());
					pc.save();
					SC_ServerMessage.send(pc, 790);
					SC_ServerMessage.send(pc, 655, partner.getName());

					partner.setPartnerId(pc.getId());
					partner.save();
					SC_ServerMessage.send(partner, 790);
					SC_ServerMessage.send(partner, 655, pc.getName());
				}
			}
			break;
		case 729:
			if (c == 1) {
				callClan(pc);
			}
			break;
		case 738:
            if (c == MSGCODE_YES) recoverExperience(pc, false);
            break;
        case 2551:
            if (c == MSGCODE_YES) recoverExperience(pc, true);
            break;
		case 951:
			L1PcInstance chatPc = (L1PcInstance) L1World.getInstance().findObject(pc.getPartyID());
			if (chatPc != null) {
				if (c == 0) {
					SC_ServerMessage.send(chatPc, 423, pc.getName());
					pc.setPartyID(0);
				} else if (c == 1) {
					if (chatPc.isInChatParty()) {
						if (chatPc.getChatParty().isVacancy() || chatPc.isGm()) {
							chatPc.getChatParty().addMember(pc);
						} else {
							SC_ServerMessage.send(chatPc, 417);
						}
					} else {
						L1ChatParty chatParty = new L1ChatParty();
						chatParty.addMember(chatPc);
						chatParty.addMember(pc);
					}
				}
			}
			break;
		case 953:
		case 954:
			
		    final int inviterId = pc.getPartyID();
		    if (inviterId <= 0) {
		        // 초대 ID가 이미 비었거나, 유효하지 않음
		        break;
		    }

		    final L1Object obj = L1World.getInstance().findObject(inviterId);
		    if (!(obj instanceof L1PcInstance)) {
		        // 초대자가 없거나 PC가 아님(안전 처리)
		        pc.setPartyID(0);
		        break;
		    }

		    final L1PcInstance inviter = (L1PcInstance) obj;
		    
		    // 거절
		    if (c == 0) {
		        SC_ServerMessage.send(inviter, 423, pc.getName()); // %0가 초대를 거부했습니다.
		        pc.setPartyID(0);
		        break;
		    }	    
		    // 수락(1이 아닌 값은 무시)
		    if (c != 1) {
		        break;
		    }
		    // 공통 정책: 해당 맵에서는 파티 금지
		    if (inviter.getMapId() == 5554 || pc.getMapId() == 5554) {
		        SC_SystemMessage.send(inviter, "해당 맵에서는 파티 기능을 사용 할 수 없습니다.");
		        pc.setPartyID(0);
		        break;
		    }
		    
			if(inviter.getAutoHunt()) {
				SC_SystemMessage.send(inviter, "현재 자동사냥중이므로 파티를 받을 수 없습니다.");
				return;
			}

		    // 배틀존에서는 파티 불가
			if (inviter.getMapId() == 5153 || inviter.getMapId() == 5001 ||
					pc.getMapId() == 5153 || pc.getMapId() == 5001) {
				SC_ServerMessage.send(inviter, 423, pc.getName()); // 거부로 처리
				pc.setPartyID(0);
				break;
			}

		    // 초대자 파티 상태에 따라 합류/생성
		    if (inviter.isInParty()) {
		        if (inviter.getParty().isVacancy() || inviter.isGm()) {
		            inviter.getParty().addMember(pc);
		        } else {
		            SC_ServerMessage.send(inviter, 417); // 파티 인원 꽉참
		        }
		    } else {
		        final L1Party party = new L1Party();
		        party.addMember(inviter);
		        party.addMember(pc);
		    }

		    // 초대 처리 끝났으면 PartyID 정리(중복 수락/재사용 방지)
		    pc.setPartyID(0);
			break;
		case 1256:
			switch (c) {
			case 0:
				if (L1Racing.getInstance().contains(0, pc) && pc.getMapId() != 5143) {
					L1Racing.getInstance().remove(0, pc);
				} else if (L1HauntedHouse.getInstance().isMember(pc) && pc.getMapId() != 5140) {
					L1HauntedHouse.getInstance().removeMember(pc);
				}
				break;
			case 1:
				if (L1Racing.getInstance().contains(0, pc) && pc.getMapId() != 5143) {
					if (L1Racing.getInstance().getGameStatus() == L1Racing.STATUS_NONE
							|| L1Racing.getInstance().getGameStatus() == L1Racing.STATUS_READY) {
						Random random = new Random(System.nanoTime());
						int locx = 32767 + random.nextInt(2);
						int locy = 32848 + random.nextInt(2);
						
						L1SkillUse.cast(
								pc,
								L1SkillId.CANCELLATION,
								pc.getId(),
								pc.getX(),
								pc.getY(),
								null,
								0,
								L1SkillUse.TYPE_LOGIN);
						
						pc.start_teleport(locx, locy, 5143, 5, 169, true, false);
					}
				} else if (L1HauntedHouse.getInstance().isMember(pc) && pc.getMapId() != 5140) {
					if (L1HauntedHouse.getInstance().getHauntedHouseStatus() == L1HauntedHouse.STATUS_NONE
							|| L1HauntedHouse.getInstance().getHauntedHouseStatus() == L1HauntedHouse.STATUS_READY) {
						if (pc.isInParty()) {
							pc.getParty().leaveMember(pc);
						}
						L1SkillUse.cast(
								pc,
								L1SkillId.CANCELLATION,
								pc.getId(),
								pc.getX(),
								pc.getY(),
								null,
								0,
								L1SkillUse.TYPE_LOGIN);
						
						L1PolyMorph.doPoly(pc, 6284, 600, L1PolyMorph.MORPH_BY_LOGIN, false);
						pc.start_teleport(32722, 32830, 5140, 2, 169, true, false);
					}
				}
				break;
			}
			break;
		case 1268:
			switch (c) {
			case 0:
				L1DeathMatch.getInstance().removeMember(pc);
				break;
			case 1:
				if (pc.isInParty()) {
					pc.getParty().leaveMember(pc);
				}
				L1DeathMatch.getInstance().addPlayer();
				pc.start_teleport(32658, 32899, 5153, 2, 169, true, false);
				break;
			}
			break;
		case 2923:
			if (c == 0) {
				SC_ChatPacket.sendDirect(pc, pc, "드래곤 포탈 입장이 취소되었습니다.");
			} else if (c == 1) {
				if (pc.DragonPortalLoc[0] != 0) {
					Collection<L1PcInstance> templist = L1World.getInstance().getAllPlayers();
					L1PcInstance[] list = templist.toArray(new L1PcInstance[templist.size()]);
					int count = 0;
					for (L1PcInstance player : list) {
						if (player == null) continue;
						if (player.getMapId() == pc.DragonPortalLoc[2]) {
							count += 1;
						}
					}
					if (count >= 32) {
						SC_ServerMessage.send(pc, 1536);
						return;
					}
					pc.start_teleport(pc.DragonPortalLoc[0], pc.DragonPortalLoc[1], pc.DragonPortalLoc[2], 5, 169, true, false);
				}
			}
			pc.DragonPortalLoc[0] = 0;
			pc.DragonPortalLoc[1] = 0;
			pc.DragonPortalLoc[2] = 0;
			break;
		case 479:
			if(c == 1) {
				try{
					pc.onStat(name);
				}catch(Exception e){
					System.out.println(String.format("%s님이 스텟오버를 시도했습니다.", pc.getName()));
				}
			}
			break;
		default:
			break;
		}
	}


	private static void renamePet(L1PetInstance pet, String name) {
		if (pet == null || name == null) {
			throw new NullPointerException();
		}

		int petItemObjId = pet.getItemObjId();
		L1Pet petTemplate = PetTable.getInstance().getTemplate(petItemObjId);
		if (petTemplate == null) {
			throw new NullPointerException();
		}

		L1PcInstance pc = (L1PcInstance) pet.getMaster();
		if (PetTable.isNameExists(name)) {
			SC_ServerMessage.send(pc, 327);
			return;
		}
		L1Npc l1npc = NpcTable.getInstance().getTemplate(pet.getNpcId());
		if (!(pet.getName().equalsIgnoreCase(l1npc.get_name()))) {
			L1ItemInstance removedItem = pc.getInventory().findItemId(410016);
			if(removedItem == null){
				SC_ServerMessage.send(pc, 326);
				return;
			}else{
				pc.getInventory().removeItem(removedItem, 1);
			}
		}

		pet.setName(name);
		petTemplate.set_name(name);
		PetTable.getInstance().storePet(petTemplate);
		L1ItemInstance item = pc.getInventory().getItem(pet.getItemObjId());
		pc.getInventory().updateItem(item);

		SC_RemoveObject.send(pc.getNetConnection(), pet);
		SC_RemoveObject.broadcast(pc, pet.getId());

		byte[] data = LIN.SC_WORLD_PUT_OBJECT_NOTI.newInstance(pet).encode();
		pc.broadcastPacket(data);
	}

	private static void callClan(L1PcInstance pc) {
		L1PcInstance callClanPc = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
		pc.setTempID(0);
		if (callClanPc == null) {
			return;
		}
		if (!pc.getMap().isEscapable() && !pc.isGm()) {
			SC_ServerMessage.send(pc, 647);
			pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, false);
			return;
		}
		if (pc.getId() != callClanPc.getCallClanId()) {
			return;
		}

		boolean isInWarArea = false;
		int castleId = L1CastleLocation.getCastleIdByArea(callClanPc);
		if (castleId != 0) {
			isInWarArea = true;
			if (MJCastleWarBusiness.getInstance().isNowWar(castleId)) {
				isInWarArea = false;
			}
		}
		short mapId = callClanPc.getMapId();
		if (mapId != 0 && mapId != 4 && mapId != 304 || isInWarArea) {
			pc.sendPackets("군주님께서 현재 소환 할 수없는 곳에 계셔서 마법이 실패 하였습니다.");
			callClanPc.sendPackets("군주님의 위치에서는 혈원을 소환 할 수 없습니다.");
			return;
		}

		L1Map map = callClanPc.getMap();
		int callCalnX = callClanPc.getX();
		int callCalnY = callClanPc.getY();
		int locX = 0;
		int locY = 0;
		int heading = 0;
		switch (callClanPc.getCallClanHeading()) {
		case 0: locX = callCalnX; locY = callCalnY - 1; heading = 4; break;
		case 1: locX = callCalnX + 1; locY = callCalnY - 1; heading = 5; break;
		case 2: locX = callCalnX + 1; locY = callCalnY; heading = 6; break;
		case 3: locX = callCalnX + 1; locY = callCalnY + 1; heading = 7; break;
		case 4: locX = callCalnX; locY = callCalnY + 1; heading = 0; break;
		case 5: locX = callCalnX - 1; locY = callCalnY + 1; heading = 1; break;
		case 6: locX = callCalnX - 1; locY = callCalnY; heading = 2; break;
		case 7: locX = callCalnX - 1; locY = callCalnY - 1; heading = 3; break;
		default: break;
		}

		boolean isExistCharacter = false;
        L1Character cha = null;
        for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(callClanPc, 1)) {
            if (object instanceof L1Character) {
                cha = (L1Character) object;
                if (cha.getX() == locX && cha.getY() == locY && cha.getMapId() == mapId) {
                    isExistCharacter = true;
                    break;
                }
            }
        }

		if (locX == 0 && locY == 0 || !map.isPassable(locX, locY) || isExistCharacter) {
			SC_ServerMessage.send(pc, 627);
			return;
		}
		pc.start_teleport(locX, locY, mapId, heading, 2235, true, false);
	}

}
