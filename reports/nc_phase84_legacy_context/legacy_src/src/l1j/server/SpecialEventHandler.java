package l1j.server;
import l1j.server.server.model.network.core.sc.packet.SC_GMHtml;

import static l1j.server.server.model.character.skill.L1SkillId.ADDITIONAL_FIRE;
import static l1j.server.server.model.character.skill.L1SkillId.BLESS_WEAPON;
import static l1j.server.server.model.character.skill.L1SkillId.COMA_B;
import static l1j.server.server.model.character.skill.L1SkillId.DECREASE_WEIGHT;
import static l1j.server.server.model.character.skill.L1SkillId.DRAGON_SKIN;
import static l1j.server.server.model.character.skill.L1SkillId.FEATHER_BUFF_A;
import static l1j.server.server.model.character.skill.L1SkillId.God_buff;
import static l1j.server.server.model.character.skill.L1SkillId.INSIGHT;
import static l1j.server.server.model.character.skill.L1SkillId.IRON_SKIN;
import static l1j.server.server.model.character.skill.L1SkillId.NATURES_TOUCH;
import static l1j.server.server.model.character.skill.L1SkillId.PHYSICAL_ENCHANT_DEX;
import static l1j.server.server.model.character.skill.L1SkillId.PHYSICAL_ENCHANT_STR;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

enum SpecialEvent {
	BugRace, AllBuf, InfinityFight, DoNotChatEveryone, DoChatEveryone
};

// 게임 내, 전체 이벤트에 대한 처리를 담당
public class SpecialEventHandler {

	private static volatile SpecialEventHandler uniqueInstance = null;

	private SpecialEventHandler() {
	}

	public static SpecialEventHandler getInstance() {
		if (uniqueInstance == null) {
			synchronized (SpecialEventHandler.class) {
				if (uniqueInstance == null) {
					uniqueInstance = new SpecialEventHandler();
				}
			}
		}

		return uniqueInstance;
	}

	//TODO 코마의 축복 코인
	public void doGiveEventStaff() {
        final byte[] noticeWire=SC_SystemMessage.sharedWire("\\aH메티스님께서 코마의 축복 코인이 지급 하였습니다.");
        try (var broadcast=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                noticeWire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {

		for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
			if (pc.getNetConnection() != null) {
				pc.getInventory().storeItem(30104, 1);
				L1ItemInstance item = ItemTable.getInstance().createItem(30104);
				SC_GMHtml.send(pc, "보낸이:" + pc.getName() + "", "아이템:"+item.getLogName()+" 이 도착 했습니다.");
				final l1j.server.server.model.network.core.session.CIOClient connection=pc.getNetConnection();
                if(connection!=null && connection.isConnected()) {
                    broadcast.send(connection.getConnection(),pc.getId());
                }
			}
		}
	
        }
    }

	//TODO 드래곤의 토파즈
	public void doGiveEventStaff1() {
        final byte[] noticeWire=SC_SystemMessage.sharedWire("\\aH메티스님께서 드래곤의 토파즈가 지급 하였습니다.");
        try (var broadcast=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                noticeWire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {

		for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
			if (pc.getNetConnection() != null) {
				pc.getInventory().storeItem(7241, 1);
				L1ItemInstance item = ItemTable.getInstance().createItem(7241);
				SC_GMHtml.send(pc, "보낸이:" + pc.getName() + "", "아이템:"+item.getLogName()+" 이 도착 했습니다.");
				final l1j.server.server.model.network.core.session.CIOClient connection=pc.getNetConnection();
                if(connection!=null && connection.isConnected()) {
                    broadcast.send(connection.getConnection(),pc.getId());
                }
			}
		}
	
        }
    }

	//TODO 보스소환주문서
	public void doGiveEventStaff2() {
        final byte[] noticeWire=SC_SystemMessage.sharedWire("\\aG메티스님께서 소환주문서 1장이 지급 하였습니다.");
        try (var broadcast=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                noticeWire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {

		for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
			if (pc.getNetConnection() != null) {
				pc.getInventory().storeItem(3000123, 1);
				L1ItemInstance item = ItemTable.getInstance().createItem(3000123);
				SC_GMHtml.send(pc, "보낸이:"+pc.getName()+"♥", "♥"+item.getLogName()+" 1장이 도착♥　　 　(필드에서만 사용이 가능합니다)　(혈원분들과 함께 레이드 하세요)");
				final l1j.server.server.model.network.core.session.CIOClient connection=pc.getNetConnection();
                if(connection!=null && connection.isConnected()) {
                    broadcast.send(connection.getConnection(),pc.getId());
                }
			}
		}
	
        }
    }

	public void doNotChatEveryone() {
		L1World.getInstance().set_worldChatElabled(false);
		SC_SystemMessage.broadcast("잠시후 월드채팅이 비활성화 상태로 돌입 합니다.");
	}

	public void doChatEveryone() {
		L1World.getInstance().set_worldChatElabled(true);
		SC_SystemMessage.broadcast("잠시후 월드채팅이 활성화 상태로 돌입 합니다.");
	}

}
