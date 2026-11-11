package l1j.server.server.clientpackets;

import java.util.logging.Logger;

import l1j.server.Templates.L1BoardPost;
import l1j.server.configure.Config;
import l1j.server.server.GameClient;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.BoardInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.serverpackets.S_SystemMessage;

public class C_BoardWrite extends ClientBasePacket {

	private static final String C_BOARD_WRITE = "[C] C_BoardWrite";
	private static Logger _log = Logger.getLogger(C_BoardWrite.class.getName());

	public C_BoardWrite(byte decrypt[], GameClient client) {
		super(decrypt);
		int id = readD();
		String title = readS();
		String content = readS();
		L1PcInstance pc = client.getActiveChar();
		if (pc == null)return;
		L1Object tg = L1World.getInstance().findObject(id);
		if (tg == null) {
			_log.warning("Invalid NPC ID: " + id);
			System.out.println("Invalid NPC ID: " + id);
			return;
		}
		if (title.length() > 16) {
			pc.sendPackets(new S_SystemMessage("게시판 제목 글자수가 초과하였습니다."));
			return;
		}
		if (tg instanceof BoardInstance) {
			BoardInstance board = (BoardInstance) tg;
			
			switch (board.getNpcId()) {
			case 81177: // GM 서버정보게시판 board_posts_notice
				if (pc.isGm()) {
					L1BoardPost.createGM(pc.getName(), title, content);
				} else {
					pc.sendPackets(new S_SystemMessage( "해당 게시판은 운영자 전용 게시판 입니다."));
					return;
				}
				break;
				
			case 81131: // GM게시판1 board_notice1
				if (pc.getAccessLevel() == Config.GMCODE) {
					L1BoardPost.createGM1(pc.getName(), title, content);
				} else {
					pc.sendPackets(new S_SystemMessage("\\aH운영자 전용 게시판 입니다."));
					return;
				}
				break;
			case 81132: // GM게시판2 board_notice2
				if (pc.getAccessLevel() == Config.GMCODE) {
					L1BoardPost.createGM2(pc.getName(), title, content);
				} else {
					pc.sendPackets(new S_SystemMessage("\\aH운영자 전용 게시판 입니다."));
					return;
				}
				break;
			case 81133: // 패키지 GM게시판3 board_notice3
				if (pc.getAccessLevel() == Config.GMCODE) {
					L1BoardPost.createGM3(pc.getName(), title, content);
				} else {
					pc.sendPackets(new S_SystemMessage("\\aH운영자 전용 게시판 입니다."));
					return;
				}
				break;
				
				
			case 71008: // 건의 사항^건의 게시판 board_posts_fix
				if (pc.getInventory().checkItem(ItemId.아데나, 300)) {
					pc.getInventory().consumeItem(ItemId.아데나, 300);
					L1BoardPost.createPhone(pc.getName(), "-비밀글-", content);
					pc.sendPackets(new S_SystemMessage("\\fY건의등록 : 제목은 운영자도 못보니 참고하세요."));
				} else {
					pc.sendPackets(new S_SystemMessage("아데나가 부족합니다."));
				}
				break;
			case 81134:
			case 81135:
				if (pc.getAccessLevel() == 0 || pc.getAccessLevel() == Config.GMCODE) {
				pc.sendPackets(new S_SystemMessage("\\fY명령어를 이용하여 주시기 바랍니다."));
				return;
			}
				break;
			default:
				if (pc.getInventory().checkItem(ItemId.아데나, 300)) {
					pc.getInventory().consumeItem(ItemId.아데나, 300);
					L1BoardPost.create(pc.getName(), title, content);
					pc.sendPackets(new S_SystemMessage("게시글 등록이 완료되었습니다."));
				} else {
					pc.sendPackets(new S_SystemMessage("아데나가 부족합니다."));
				}
				break;
			}

		}

	}

	@Override
	public String getType() {
		return C_BOARD_WRITE;
	}
}
