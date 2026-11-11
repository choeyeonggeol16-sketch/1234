package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;

public class S_WarStartMessage extends ServerBasePacket {
	
	private static final String[] clanNames = new String[]{"켄트성", "오크 요새", "윈다우드성", "기란성", "하이네성", "지저성", "아덴성"};
	public static S_WarStartMessage get(){
		S_WarStartMessage p = new S_WarStartMessage();
		for(L1Clan clan : L1World.getInstance().getAllClans()) {
			if(clan.getCastleId() - 1 > 0 && clan.getCastleId() - 1 < clanNames.length) {
				clanNames[clan.getCastleId() - 1] = clan.getClanName();
			}
		}
		
		for(int i=0; i<clanNames.length; i++) {
			p.writeS(clanNames[i]);
		}
		
		return p;
	}
	
	private S_WarStartMessage(){
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(0x4E);
		writeC(0x07);
	}
	
	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
