package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import l1j.server.Config;
import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.MJINNSystem.MJINNRoom;
import l1j.server.Stadium.StadiumManager;
import l1j.server.server.model.Getback;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1ItemBookMark;
import l1j.server.server.utils.CommonUtil;

import static l1j.server.server.model.item.function.TravelUseTables.*;
final class AccountPositionRecoveryItems {
    private AccountPositionRecoveryItems() {}
    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        int[] loc = null;
        switch (itemId) {
		case 210104:
			if (pc.isstop() || pc.hasSkillEffect(L1SkillId.DESPERADO) || pc.hasSkillEffect(L1SkillId.STATUS_FREEZE)) {
				SC_SystemMessage.send(pc, "사용할 수 없는 상태입니다.");
				return;
			}
			if (pc.getMapId() >= 1708 && pc.getMapId() <= 1710) {
				SC_SystemMessage.send(pc, "여기서는 사용할 수 없습니다.");
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약) || pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_SystemMessage.send(pc, "사용할 수 없는 상태입니다.");
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.버닝샷)) {
				SC_SystemMessage.send(pc, "\\fY버닝샷중에는 이동 및 귀환이 불가합니다.");
				return;
			}

            final String recoveryAccount = client.getAccountName();
            try (l1j.server.server.model.L1InventoryTransaction tx =
                    User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey(recoveryAccount))) {
                if (!tx.debit(l1iteminstance, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    // Never consume a recovery ticket merely because another offline
                    // character on the account was updated: the active character
                    // itself must have an eligible, locked row in this transaction.
                    try (PreparedStatement check = con.prepareStatement(
                            "SELECT objid FROM characters WHERE account_name=? AND objid=? "
                            + "AND MapID NOT IN (38,5001,99,997,5166,39,34,701,2000,1708) FOR UPDATE")) {
                        check.setString(1, recoveryAccount);
                        check.setInt(2, pc.getId());
                        try (ResultSet rows = check.executeQuery()) {
                            if (!rows.next()) throw new SQLException("Recovery character row unavailable");
                        }
                    }
                    try (PreparedStatement ps = con.prepareStatement("UPDATE characters SET LocX=33432,LocY=32807,MapID=4 "
                            + "WHERE account_name=? AND (OnlineStatus=0 OR objid=?) "
                            + "AND MapID NOT IN (38,5001,99,997,5166,39,34,701,2000,1708)")) {
                        ps.setString(1, recoveryAccount);
                        ps.setInt(2, pc.getId());
                        ps.executeUpdate();
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc,
                        "좌표 복구가 확정되지 않았습니다. 아이템을 확인해 주세요."));
                CDB.RequestUseItem(pc, tx, () -> {
                    // Logout's final snapshot must contain the durable recovery
                    // position; never let the old location overwrite this SQL.
                    pc.setX(33432);pc.setY(32807);pc.setMap((short)4);
                    SC_SystemMessage.send(pc,"좌표 복구를 저장했습니다. 다시 접속해 주세요.");
                    client.kick();
                });
            } catch (RuntimeException failure) {
                SC_SystemMessage.send(pc,"좌표 복구를 저장하지 못했습니다. 아이템과 잔액을 확인해 주세요.");
            }

			break;


        }
    }
}
