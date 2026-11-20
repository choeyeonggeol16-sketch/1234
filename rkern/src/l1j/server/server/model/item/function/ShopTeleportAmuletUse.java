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
import l1j.server.server.model.network.core.session.CUserSession;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1ItemBookMark;
import l1j.server.server.utils.CommonUtil;
import static l1j.server.server.model.item.function.TravelUseTables.*;

/** Dedicated item-use handler extracted from a mixed domain. */
final class ShopTeleportAmuletUse {
    private ShopTeleportAmuletUse() {}

    static void use(CUserSession client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        int[] loc = null;
        switch (itemId) {
		case 748: // 잡화상점 이동부적
			pc.start_teleport(33453, 32820, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
			break;

		case 749: // 장비상점 이동부적
			pc.start_teleport(33435, 32754, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
			break;


        }
    }
}
