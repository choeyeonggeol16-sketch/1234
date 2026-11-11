package l1j.server.server.model.Instance;

import l1j.server.Templates.L1Npc;
import l1j.server.configure.Config;
import l1j.server.server.serverpackets.S_AuctionSystemBoard;
import l1j.server.server.serverpackets.S_Board;
import l1j.server.server.serverpackets.S_BoardRead;
import l1j.server.server.serverpackets.S_Boarditem;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_Ranking;
import l1j.server.server.serverpackets.S_SystemMessage;

public class BoardInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;

	public BoardInstance(L1Npc template) {
		super(template);
	}	

	@Override
	public void onAction(L1PcInstance player) {
		if (this.getNpcTemplate().get_npcId() == 81134) {
			player.sendPackets(new S_AuctionSystemBoard(this)); 
			player.sendPackets(new S_ChatPacket(player, String.format("%s", Config.CASHMENT), 1));
			player.sendPackets(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, Config.CASHMENT));
		} else if (this.getNpcTemplate().get_npcId() == 81135) {
			player.sendPackets(new S_Boarditem(this));
		} else {
			player.sendPackets(new S_Board(this));
		}
		
	}

	public void onAction(L1PcInstance player, int number) {
		if (this.getNpcTemplate().get_npcId() == 81134) { 
			player.sendPackets(new S_AuctionSystemBoard(this, number));

		} else if (this.getNpcTemplate().get_npcId() == 81135) {
			player.sendPackets(new S_Boarditem(this, number));
			
		} else {
			player.sendPackets(new S_Board(this, number));
		}
		
	}
	

	public void onActionRead(L1PcInstance player, int number) {
		if (this.getNpcTemplate().get_npcId() == 81134) {
			player.sendPackets(new S_AuctionSystemBoard(number));
		} else if (this.getNpcTemplate().get_npcId() == 81135) {
			player.sendPackets(new S_Boarditem(number));
		} else if (this.getNpcTemplate().get_npcId() == 200001) {// 랭킹 게시판
			player.sendPackets(new S_Ranking(player, number));
		} else {
			if (this.getNpcTemplate().get_npcId() == 71008) {
				if (!player.isGm()) {
					player.sendPackets(new S_SystemMessage("운영자만 열람할 수 있습니다."));
					return;
				}
			}
			player.sendPackets(new S_BoardRead(this, number));
		}
		
	}
}
