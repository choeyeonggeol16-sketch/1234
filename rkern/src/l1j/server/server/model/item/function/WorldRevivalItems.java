package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1NeigeItemBox;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;

final class WorldRevivalItems {
    private WorldRevivalItems() {}
    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
case 70703:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 70703, 70702, "기억의 수정(본브레이크:래스트) (소생) 8장이 필요합니다.", "기억의 수정(본브레이크:래스트) 획득하였습니다.");
			break;
		
case 4100653:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 4100653, 68076, "고대의 주문서:무기(소생) 8장이 필요합니다.", "고대의 주문서:무기를 획득하였습니다.");
			break;
		
case 4100654:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 4100654, 68077, "고대의 주문서:방어구(소생) 8장이 필요합니다.", "고대의 주문서:방어구를 획득하였습니다.");
			break;
		
case 43321:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 43321, 900167, "머미로드의 반지 (소생) 8개가 필요합니다.", "머미로드의 반지를 획득하였습니다.");
			break;
		
case 32090:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32090, 900163, "리치의 반지 (소생) 8개가 필요합니다.", "리치의 반지를 획득하였습니다.");
			break;
		
case 32005:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32005, 210125, "전사의 인장:데스페라도 (소생) 8개가 필요합니다.", "전사의 인장:데스페라도를 획득하였습니다.");
			break;
		
case 32067:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32067, 222317, "격분의 장갑 (소생) 8개가 필요합니다.", "격분의 장갑을 획득하였습니다.");
			break;
		
case 32063:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32063, 900158, "우그누스의 가더 (소생) 8개가 필요합니다.", "우그누스의 가더를 획득하였습니다.");
			break;
		
case 32062:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32062, 900157, "쿠거의 가더 (소생) 8개가 필요합니다.", "쿠거의 가더를 획득하였습니다.");
			break;
		
case 32087:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32087, 900075, "신화변신지배반지 (소생) 8개가 필요합니다.", "변신 지배 반지 획득하였습니다.");
			break;
		
case 32001:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32001, 40222, "마법서 (디스인티그레이트) (소생) 8장이 필요합니다.", "마법서(디스인티그레이트) 획득하였습니다.");
			break;
		
case 32002:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32002, 41148, "기술서 (카운터 배리어) (소생) 8장이 필요합니다.", "기술서 (카운터 배리어) 획득하였습니다.");
			break;
		
case 32075:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32075, 20279, "라이아의 반지 (소생) 8개가 필요합니다.", "라이아의 반지를 획득하였습니다.");
			break;
		
case 32058:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32058, 900019, "실프의 티셔츠 (소생) 8개가 필요합니다.", "실프의 티셔츠를 획득하였습니다.");
			break;
		
case 32039:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32039, 22263, "반역자의 방패 (소생) 8개가 필요합니다.", "반역자의 방패를 획득하였습니다.");
			break;
		
case 32038:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32038, 22214, "시어의 심안 (소생) 8개가 필요합니다.", "시어의 심안을 획득하였습니다.");
			break;
		
case 32089:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32089, 900162, "사신의 반지 (소생) 8개가 필요합니다.", "사신의 반지를 획득하였습니다.");
			break;
		
case 32064:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32064, 900159, "나이트발드의 각반 (소생) 8개가 필요합니다.", "나이트발드의 각반을 획득하였습니다.");
			break;
		
case 32065:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32065, 900160, "아이리스의 각반 (소생) 8개가 필요합니다.", "아이리스의 각반을 획득하였습니다.");
			break;
		
case 32029:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32029, 20107, "리치의 로브 (소생) 8개가 필요합니다.", "리치의 로브를 획득하였습니다.");
			break;
		
case 32047:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32047, 20017, "머미로드의 왕관 (소생) 8개가 필요합니다.", "머미로드의 왕관을 획득하였습니다.");
			break;
		
case 32060:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32060, 900155, "아이리스의 부츠 (소생) 8개가 필요합니다.", "아이리스의 부츠를 획득하였습니다.");
			break;
		
case 32011:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32011, 4100102, "카운터 배리어:베테랑 (소생) 8개가 필요합니다.", "카운터 배리어:베테랑을 획득하였습니다.");
			break;
		
case 32061:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32061, 900156, "머미로드의 장갑 (소생) 8개가 필요합니다.", "머미로드의 장갑을 획득하였습니다.");
			break;
		
case 32048:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32048, 22360, "지휘관의 투구 (소생) 8개가 필요합니다.", "지휘관의 투구를 획득하였습니다.");
			break;
		
case 32066:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32066, 900161, "뱀파이어의 각반 (소생) 8개가 필요합니다.", "뱀파이어의 각반을 획득하였습니다.");
			break;
		
case 32104:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32104, 40346, "안타라스의 숨결 (소생) 8개가 필요합니다.", "안타라스의 숨결을 획득하였습니다.");
			break;
		
case 875640396:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 875640396, 875640381, "전설 무기 제작 비법서 (소생) 8개가 필요합니다.", "전설 무기 제작 비법서를 획득하였습니다.");
			break;
		
case 875640398:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 875640398, 875640397, "신화 무기 제작 비법서 (소생) 8개가 필요합니다.", "신화 무기 제작 비법서를 획득하였습니다.");
			break;
		
case 32105:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32105, 40354, "발라카스의 숨결 (소생) 8개가 필요합니다.", "발라카스의 숨결을 획득하였습니다.");
			break;
		
case 32106:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32106, 40362, "파푸리온의 숨결 (소생) 8개가 필요합니다.", "파푸리온의 숨결을 획득하였습니다.");
			break;
		
case 32107:
			RevivalScrollCraftingItems.UseDurableRevive(pc, 32107, 40370, "린드비오르의 숨결 (소생) 8개가 필요합니다.", "린드비오르의 숨결을 획득하였습니다.");
			break;
		
        }
    }
}
