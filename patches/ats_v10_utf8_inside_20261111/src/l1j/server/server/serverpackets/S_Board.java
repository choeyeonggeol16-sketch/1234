package l1j.server.server.serverpackets;

import java.util.List;

import l1j.server.Templates.L1BoardPost;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;

public class S_Board extends ServerBasePacket {
	private static final String S_BOARD = "[S] S_Board";
	private static final int TOPIC_LIMIT = 8;
	

	public S_Board(L1NpcInstance board) {
		switch (board.getNpcId()) {
		case 81177:// 서버정보게시판
			buildPacketNotice(board, 0);
			break;
		case 81131:// 운영자1
			buildPacketNotice1(board, 0);
			break;
		case 81132: // 운영자2
			buildPacketNotice2(board, 0);
			break;
		case 81133:// 운영자3
			buildPacketNotice3(board, 0);
			break; 
		case 71008:// 건의사항
			buildPacketPhone(board, 0);
			break; 
		case 200001:// 전체랭킹
			buildPacket2(board, 0);
			break;
		default:// 기본값
			buildPacket(board, 0);
			break;
		}
	}

	public S_Board(L1NpcInstance board, int number) {
		switch (board.getNpcId()) {
		case 81177:// 서버정보게시판
			buildPacketNotice(board, number);
			break;
		case 71008:// 건의사항
			buildPacketPhone(board, number);
			break;
		case 81131: // 운영자1
			buildPacketNotice1(board, number);
			break;
		case 81132:// 운영자2
			buildPacketNotice2(board, number);
			break;
		case 81133:// 운영자3
			buildPacketNotice3(board, number);
			break;
		case 200001:// 전체랭킹
			buildPacket2(board, number);
			break;
		default:// 기본값
			buildPacket(board, number);
			break;
		}
	}
	
	private void buildPacket2(L1NpcInstance board,int number) {
		int count = 0;
		String[][] db = null;
		int[] id = null;
		db = new String[9][3];
		id = new int[9];
		while (count < 9) {
			id[count] = count + 1;
			db[count][0] = "";// Ranking
			db[count][1] = "";
			count++;
		}
		//db[0][2] = "--------- 전체순위";
		db[0][2] = "--------- 전체랭킹";
		db[1][2] = "--------- 군     주";
		db[2][2] = "--------- 기     사";
		db[3][2] = "--------- 요     정";
		db[4][2] = "--------- 법     사";
		db[5][2] = "--------- 다     엘";
		db[6][2] = "--------- 개인순위"; 
		db[7][2] = "--------- "; 
		writeC(Opcodes.S_OPCODE_BOARD);
		writeC(0);
		writeD(board.getId());
		writeC(0xFF); // ?
		writeC(0xFF); // ?
		writeC(0xFF); // ?
		writeC(0x7F); // ?
		writeH(9);
		writeH(300);
		for (int i = 0; i < 8; ++i) {
			writeD(id[i]);
			writeS(db[i][0]);
			writeS(db[i][1]);
			writeS(db[i][2]);
		}
	}

	private void buildPacket(L1NpcInstance board, int number) {
		List<L1BoardPost> topics = L1BoardPost.index(number, TOPIC_LIMIT);
		writeC(Opcodes.S_OPCODE_BOARD);
		writeC(0x00); // DragonKeybbs = 1
		writeD(board.getId());		// 오브젝트 아이디
		if (number == 0) {
			writeD(0x7FFFFFFF);
		} else {
			writeD(number);
		}
		writeC(topics.size());					// 표현할 글리스트 갯수

		if (number == 0) {
			writeC(0);
			writeH(300);
		}
		for (L1BoardPost topic : topics) {
			writeD(topic.getId());
			writeS(topic.getName());
			writeS(topic.getDate());
			writeS(topic.getTitle());
		}
	}
	private void buildPacketNotice(L1NpcInstance board, int number) {
		List<L1BoardPost> topics = L1BoardPost.indexGM(number, TOPIC_LIMIT);
		writeC(Opcodes.S_OPCODE_BOARD);
		writeC(0); // DragonKeybbs = 1
		writeD(board.getId());
		if (number == 0) {
			writeD(0x7FFFFFFF);
		} else {
			writeD(number);
		}
		writeC(topics.size());
		if (number == 0) {
			writeC(0);
			writeH(300);
		}
		for (L1BoardPost topic : topics) {
			writeD(topic.getId());
			writeS(topic.getName());
			writeS(topic.getDate());
			writeS(topic.getTitle());
		}
	}
	

	private void buildPacketNotice1(L1NpcInstance board, int number) {
		List<L1BoardPost> topics = L1BoardPost.indexGM1(number, TOPIC_LIMIT);
		writeC(Opcodes.S_OPCODE_BOARD);
		writeC(0); // DragonKeybbs = 1
		writeD(board.getId());
		if (number == 0) {
			writeD(0x7FFFFFFF);
		} else {
			writeD(number);
		}
		writeC(topics.size());
		if (number == 0) {
			writeC(0);
			writeH(300);
		}
		for (L1BoardPost topic : topics) {
			writeD(topic.getId());
			writeS(topic.getName());
			writeS(topic.getDate());
			writeS(topic.getTitle());
		}
	}
	private void buildPacketNotice2(L1NpcInstance board, int number) {
		List<L1BoardPost> topics = L1BoardPost.indexGM2(number, TOPIC_LIMIT);
		writeC(Opcodes.S_OPCODE_BOARD);
		writeC(0); // DragonKeybbs = 1
		writeD(board.getId());
		if (number == 0) {
			writeD(0x7FFFFFFF);
		} else {
			writeD(number);
		}
		writeC(topics.size());
		if (number == 0) {
			writeC(0);
			writeH(300);
		}
		for (L1BoardPost topic : topics) {
			writeD(topic.getId());
			writeS(topic.getName());
			writeS(topic.getDate());
			writeS(topic.getTitle());
		}
	}
	private void buildPacketNotice3(L1NpcInstance board, int number) {
		List<L1BoardPost> topics = L1BoardPost.indexGM3(number, TOPIC_LIMIT);
		writeC(Opcodes.S_OPCODE_BOARD);
		writeC(0); // DragonKeybbs = 1
		writeD(board.getId());
		if (number == 0) {
			writeD(0x7FFFFFFF);
		} else {
			writeD(number);
		}
		writeC(topics.size());
		if (number == 0) {
			writeC(0);
			writeH(300);
		}
		for (L1BoardPost topic : topics) {
			writeD(topic.getId());
			writeS(topic.getName());
			writeS(topic.getDate());
			writeS(topic.getTitle());
		}
	}
	
	private void buildPacketPhone(L1NpcInstance board, int number) {
		List<L1BoardPost> topics = L1BoardPost.indexPhone(number, TOPIC_LIMIT);
		writeC(Opcodes.S_OPCODE_BOARD);
		writeC(0); // DragonKeybbs = 1
		writeD(board.getId());
		if (number == 0) {
			writeD(0x7FFFFFFF);
		} else {
			writeD(number);
		}
		writeC(topics.size());
		if (number == 0) {
			writeC(0);
			writeH(300);
		}
		for (L1BoardPost topic : topics) {
			writeD(topic.getId());
			writeS(topic.getName());
			writeS(topic.getDate()); 
			writeS(topic.getTitle()); 
		}
	}
	
	
	@Override
	public byte[] getContent() {
		return getBytes();
	}

	@Override
	public String getType() {
		return S_BOARD;
	}
}

