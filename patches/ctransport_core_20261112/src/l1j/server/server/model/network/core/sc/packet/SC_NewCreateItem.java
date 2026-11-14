package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_NewCreateItem {

	public static final int 버프창 = 0x6e;
	public static final int TAM_POINT = 0x01c2;
	public static final int CASTLE_WAR_TIME_END = 0x44; // 아직 미사용
	public static final int CASTLE_WAR_TIME = 0x4C;
	public static final int CLAN_JOIN_MESSAGE = 0x43;
	public static final int CLAN_JOIN_SETTING = 0x4D;
	public static final int EMOTICON = 0x40;

	/** 1020이후 새로운 패킷 **/
	public static final int 사망패널티 = 0xCF;
	public static final int 도감 = 0x30;
	public static final int 퀘스트대화 = 0xb;
	public static final int 퀘스트 = 0xd;
	public static final int 퀘스트2 = 0x9;
	public static final int 퀘스트3 = 0x3e;
	public static final int 퀘스트4 = 0x6;
	public static final int 신규패킷2 = 0x20;
	public static final int 신규패킷3 = 0xe3;
	public static final int 신규패킷4 = 0xe5;
	public static final int 신규패킷5 = 0xe7;
	public static final int 신규패킷6 = 0xe9;
	public static final int 신규패킷7 = 0xea;
	public static final int 신규패킷8 = 0x2f;
	public static final int 신규패킷10 = 0x7e;
	public static final int 신규패킷11 = 0x76;
	public static final int 신규패킷12 = 0x77;
	public static final int 신규패킷13 = 0x07;
	/** 1020이후 새로운 패킷 **/

	public static final int[] hextable = { 0x80, 0x81, 0x82, 0x83, 0x84, 0x85,
			0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0x90,
			0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0x9b,
			0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6,
			0xa7, 0xa8, 0xa9, 0xaa, 0xab, 0xac, 0xad, 0xae, 0xaf, 0xb0, 0xb1,
			0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xbb, 0xbc,
			0xbd, 0xbe, 0xbf, 0xc0, 0xc1, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7,
			0xc8, 0xc9, 0xca, 0xcb, 0xcc, 0xcd, 0xce, 0xcf, 0xd0, 0xd1, 0xd2,
			0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xdb, 0xdc, 0xdd,
			0xde, 0xdf, 0xe0, 0xe1, 0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8,
			0xe9, 0xea, 0xeb, 0xec, 0xed, 0xee, 0xef, 0xf0, 0xf1, 0xf2, 0xf3,
			0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa, 0xfb, 0xfc, 0xfd, 0xfe,
			0xff };

	public static final String CHECK = 
			"08 90 1c 10 d0 81 87 a5 05 18 80 a3 05 "
					+ "20 01 28 03 32 43 08 17 12 3f 08 02 10 d3 61 18 "
					+ "03 22 14 67 72 6f 77 74 68 20 63 72 79 73 74 61 "
					+ "6c 20 70 69 65 63 65 28 00 30 af 10 38 01 42 06 "
					+ "24 31 32 38 32 39 4a 06 17 14 00 00 00 00 50 87 "
					+ "ff ff ff ff ff ff ff ff 01 32 44 08 05 12 40 08 "
					+ "02 10 f1 6d 18 03 22 0e 70 73 79 20 73 6f 66 74 "
					+ "20 64 72 69 6e 6b 28 00 30 bc 26 38 01 42 06 24 "
					+ "31 30 39 33 37 4a 0d 15 78 00 03 02 00 00 00 3d "
					+ "e0 33 4d cb 50 97 ff ff ff ff ff ff ff ff 01 32 "
					+ "4b 08 1c 12 47 08 02 10 95 75 18 01 22 15 66 61 "
					+ "6e 74 61 73 79 20 63 72 79 73 74 61 6c 20 70 69 "
					+ "65 63 65 28 00 30 aa 10 38 01 42 0d 24 31 37 35 "
					+ "33 31 20 24 31 37 38 30 31 4a 06 17 14 00 00 00 "
					+ "00 50 87 ff ff ff ff ff ff ff ff 01 32 42 08 0a "
					+ "12 3e 08 02 10 c8 1f 18 64 22 0e 67 6d 20 70 6f "
					+ "74 69 6f 6e 20 31 34 74 68 28 33 30 c1 26 38 01 "
					+ "42 06 24 31 30 39 33 35 4a 0b 17 13 00 00 00 00 "
					+ "3d e0 33 4d cb 50 97 ff ff ff ff ff ff ff ff 01 "
					+ "32 3f 08 21 12 3b 08 02 10 ea 6b 18 01 22 10 62 "
					+ "61 67 20 6f 66 20 73 61 6e 64 20 77 6f 72 6d 28 "
					+ "00 30 95 09 38 01 42 06 24 31 34 32 39 30 4a 06 "
					+ "17 04 00 00 00 00 50 87 ff ff ff ff ff ff ff ff "
					+ "01 32 3f 08 0f 12 3b 08 02 10 80 7a 18 14 22 0b "
					+ "64 72 75 77 61 20 63 61 6e 64 79 28 33 30 8e 1a "
					+ "38 01 42 06 24 31 30 39 34 36 4a 0b 17 03 00 00 "
					+ "00 00 3d e0 33 4d cb 50 97 ff ff ff ff ff ff ff "
					+ "ff 01 32 43 08 26 12 3f 08 02 10 b8 7b 18 01 22 "
					+ "0f 65 76 20 69 76 6f 72 79 20 63 68 61 72 67 65 "
					+ "28 00 30 e0 0f 38 01 42 06 24 32 30 34 35 35 4a "
					+ "0b 17 07 00 00 00 00 3d e0 33 4d cb 50 97 ff ff "
					+ "ff ff ff ff ff ff 01 32 41 08 14 12 3d 08 02 10 "
					+ "c8 20 18 01 22 13 72 75 62 79 20 6f 66 20 64 72 "
					+ "61 67 6f 6e 20 32 30 30 39 28 33 30 8d 1d 38 01 "
					+ "42 05 24 37 39 37 31 4a 06 17 14 00 00 00 00 50 "
					+ "97 ff ff ff ff ff ff ff ff 01 32 44 08 02 12 40 "
					+ "08 02 10 f1 6d 18 03 22 0e 70 73 79 20 73 6f 66 "
					+ "74 20 64 72 69 6e 6b 28 00 30 bc 26 38 01 42 06 "
					+ "24 31 30 39 33 37 4a 0d 15 78 00 03 02 00 00 00 "
					+ "3d e0 33 4d cb 50 97 ff ff ff ff ff ff ff ff 01 "
					+ "32 40 08 19 12 3c 08 02 10 c6 7b 18 01 22 0c 69 "
					+ "63 65 20 74 65 61 72 20 62 61 67 28 00 30 be 07 "
					+ "38 01 42 06 24 32 30 34 37 39 4a 0b 17 07 00 00 "
					+ "00 00 3d e0 33 4d cb 50 97 ff ff ff ff ff ff ff "
					+ "ff 01 32 3f 08 07 12 3b 08 02 10 80 7a 18 05 22 "
					+ "0b 64 72 75 77 61 20 63 61 6e 64 79 28 33 30 8e "
					+ "1a 38 01 42 06 24 31 30 39 34 36 4a 0b 17 03 00 "
					+ "00 00 00 3d e0 33 4d cb 50 97 ff ff ff ff ff ff "
					+ "ff ff 01 32 4b 08 1e 12 47 08 02 10 95 75 18 01 "
					+ "22 15 66 61 6e 74 61 73 79 20 63 72 79 73 74 61 "
					+ "6c 20 70 69 65 63 65 28 00 30 aa 10 38 01 42 0d "
					+ "24 31 37 35 33 31 20 24 31 37 38 30 31 4a 06 17 "
					+ "14 00 00 00 00 50 87 ff ff ff ff ff ff ff ff 01 "
					+ "32 45 08 0c 12 41 08 02 10 ee 6d 18 0a 22 0f 70 "
					+ "73 79 20 73 70 69 63 79 20 72 61 6d 65 6e 28 00 "
					+ "30 be 26 38 01 42 06 24 31 30 39 33 36 4a 0d 15 "
					+ "78 00 03 02 00 00 00 3d e0 33 4d cb 50 97 ff ff "
					+ "ff ff ff ff ff ff 01 32 3e 08 23 12 3a 08 02 10 "
					+ "bd 6f 18 01 22 0f 6b 69 72 74 61 73 20 73 69 6e "
					+ "69 73 74 65 72 28 00 30 93 2c 38 01 42 06 24 31 "
					+ "35 33 38 34 4a 06 17 0e 00 00 00 00 50 87 ff ff "
					+ "ff ff ff ff ff ff 01 32 48 08 11 12 44 08 02 10 "
					+ "84 1a 18 03 22 10 62 6d 20 6d 61 67 69 63 20 73 "
					+ "63 72 6f 6c 6c 33 28 33 30 ae 2f 38 01 42 05 24 "
					+ "35 38 32 35 4a 10 17 05 00 00 00 00 11 03 18 03 "
					+ "05 03 06 03 23 03 50 97 ff ff ff ff ff ff ff ff "
					+ "01 32 3b 08 28 12 37 08 02 10 b9 77 18 01 22 0c "
					+ "66 69 72 65 20 63 72 79 73 74 61 6c 28 00 30 cc "
					+ "19 38 01 42 06 24 31 38 36 31 37 4a 06 17 15 00 "
					+ "00 00 00 50 83 ff ff ff ff ff ff ff ff 01 32 44 "
					+ "08 16 12 40 08 02 10 c6 20 18 01 22 16 64 69 61 "
					+ "6d 6f 6e 64 20 6f 66 20 64 72 61 67 6f 6e 20 32 "
					+ "30 09 28 33 30 89 1d 38 01 42 05 24 37 39 36 "
					+ "39 4a 06 17 14 00 00 00 00 50 97 ff ff ff ff ff "
					+ "ff ff ff 01 32 45 08 04 12 41 08 02 10 ee 6d 18 "
					+ "03 22 0f 70 73 79 20 73 70 69 63 79 20 72 61 6d "
					+ "65 6e 28 00 30 be 26 38 01 42 06 24 31 30 39 33 "
					+ "36 4a 0d 15 78 00 03 02 00 00 00 3d e0 33 4d cb "
					+ "50 97 ff ff ff ff ff ff ff ff 01 32 36 08 1b 12 "
					+ "32 08 02 10 d8 61 18 01 22 07 69 63 71 20 6b 65 "
					+ "79 28 00 30 d7 17 38 01 42 06 24 31 32 84 38 "
					+ "4a 06 17 0c 00 00 00 00 50 83 ff ff ff ff ff ff "
					+ "ff ff 01 32 44 08 09 12 40 08 02 10 f1 6d 18 05 "
					+ "22 0e 70 73 79 20 73 6f 66 74 20 64 72 69 6e 6b "
					+ "28 00 30 bc 26 38 01 42 06 24 31 30 39 33 37 4a "
					+ "0d 15 78 00 03 02 00 00 00 3d e0 33 4d cb 50 97 "
					+ "ff ff ff ff ff ff ff ff 01 32 3f 08 20 12 3b 08 "
					+ "02 10 eb 6b 18 01 22 10 62 61 67 20 6f 66 20 61 "
					+ "6e 74 20 71 75 65 65 6e 28 00 30 bb 0e 38 01 42 "
					+ "06 24 31 42 38 39 4a 06 17 04 00 00 00 00 50 "
					+ "87 ff ff ff ff ff ff ff ff 01 32 43 08 0e 12 3f "
					+ "08 02 10 c8 1f 18 c8 01 22 0e 67 6d 20 70 6f 74 "
					+ "69 6f 6e 20 31 34 74 68 28 33 30 c1 26 38 01 42 "
					+ "06 24 31 30 39 33 35 4a 0b 17 13 00 00 00 00 3d "
					+ "e0 33 4d cb 50 97 ff ff ff ff ff ff ff ff 01 32 "
					+ "46 08 25 12 42 08 02 10 b7 7b 18 01 22 12 65 76 "
					+ "20 73 6f 75 6c 74 6f 6d 62 20 63 68 61 72 67 65 "
					+ "28 00 30 e0 0f 38 01 42 06 24 32 30 34 35 34 4a "
					+ "0b 17 07 00 00 00 00 3d e0 33 4d cb 50 97 ff ff "
					+ "ff ff ff ff ff ff 01 32 42 08 13 12 3e 08 02 10 "
					+ "b5 7b 18 0a 22 0f 65 76 20 6f 6d 61 6e 20 74 65 "
					+ "6c 62 6f 6f 6b 28 00 30 e3 1e 38 01 42 05 24 39 "
					+ "33 38 31 4a 0b 17 05 00 00 00 00 3d e0 33 4d cb "
					+ "50 97 ff ff ff ff ff ff ff ff 01 32 3e 08 2a 12 "
					+ "3a 08 02 10 8c 6e 18 01 22 0f 70 63 20 69 76 6f "
					+ "72 79 20 65 6c 69 78 69 72 28 00 30 8e 20 38 01 "
					+ "42 06 24 32 30 34 36 32 4a 06 17 13 00 00 00 00 "
					+ "50 93 ff ff ff ff ff ff ff ff 01 32 45 08 01 12 "
					+ "41 08 02 10 ee 6d 18 03 22 0f 70 73 79 20 73 70 "
					+ "69 63 79 20 72 61 6d 65 6e 28 00 30 be 26 38 01 "
					+ "42 06 24 31 30 39 33 36 4a 0d 15 78 00 03 02 00 "
					+ "00 00 3d e0 33 4d cb 50 97 ff ff ff ff ff ff ff "
					+ "ff 01 32 40 08 18 12 3c 08 02 10 c6 7b 18 01 22 "
					+ "0c 69 63 65 20 74 65 61 72 20 62 61 67 28 00 30 "
					+ "be 07 38 01 42 06 24 32 30 34 37 39 4a 0b 17 07 "
					+ "00 00 00 00 3d e0 33 4d cb 50 97 ff ff ff ff ff "
					+ "ff ff ff 01 32 42 08 06 12 3e 08 02 10 c8 1f 18 "
					+ "32 22 0e 67 6d 20 70 6f 74 69 6f 6e 20 31 34 74 "
					+ "68 28 33 30 c1 26 38 01 42 06 24 31 30 39 33 35 "
					+ "4a 0b 17 13 00 00 00 00 3d e0 33 4d cb 50 97 ff "
					+ "ff ff ff ff ff ff ff 01 32 4b 08 1d 12 47 08 02 "
					+ "10 95 75 18 01 22 15 66 61 6e 74 61 73 79 20 63 "
					+ "72 79 73 74 61 6c 20 70 69 65 63 65 28 00 30 aa "
					+ "10 38 01 42 0d 24 31 37 35 33 31 20 24 31 37 38 "
					+ "30 31 4a 06 17 14 00 00 00 00 50 87 ff ff ff ff "
					+ "ff ff ff ff 01 32 3f 08 0b 12 3b 08 02 10 80 7a "
					+ "18 0a 22 0b 64 72 75 77 61 20 63 61 6e 64 79 28 "
					+ "33 30 8e 1a 38 01 42 06 24 31 30 39 34 36 4a 0b "
					+ "17 03 00 00 00 00 3d e0 33 4d cb 50 97 ff ff ff "
					+ "ff ff ff ff ff 01 32 3d 08 22 12 39 08 02 10 f7 "
					+ "75 18 01 22 0e 62 61 67 20 6f 66 20 61 73 74 61 "
					+ "72 6f 74 28 00 30 a9 10 38 01 42 06 24 31 37 36 "
					+ "35 38 4a 06 17 07 00 00 00 00 50 83 ff ff ff ff "
					+ "ff ff ff ff 01 32 3e 08 10 12 3a 08 02 10 96 1a "
					+ "18 03 22 10 62 6d 20 6c 61 77 66 75 6c 20 74 69 "
					+ "63 6b 65 74 28 00 30 c2 18 38 01 42 05 24 35 38 "
					+ "34 30 4a 06 17 05 00 00 00 00 50 97 ff ff ff ff "
					+ "ff ff ff ff 01 32 46 08 27 12 42 08 02 10 b9 7b "
					+ "18 01 22 0d 65 76 20 67 69 61 6e 74 20 64 6f 6c "
					+ "6c 28 00 30 d8 33 38 01 42 10 24 32 04 36 36 "
					+ "20 5b 32 35 39 32 30 30 30 5d 4a 0f 17 08 01 00 "
					+ "00 00 3d e0 33 4d cb 3f 01 24 0a 50 17 32 45 08 "
					+ "15 12 41 08 02 10 c7 20 18 01 22 17 73 61 70 70 "
					+ "68 69 72 65 20 6f 66 20 64 72 61 67 6f 6e 20 32 "
					+ "30 09 28 33 30 8f 1d 38 01 42 05 24 37 39 37 "
					+ "30 4a 06 17 14 00 00 00 00 50 97 ff ff ff ff ff "
					+ "ff ff ff 01 32 3f 08 03 12 3b 08 02 10 80 7a 18 "
					+ "03 22 0b 64 72 75 77 61 20 63 61 6e 64 79 28 33 "
					+ "30 8e 1a 38 01 42 06 24 31 30 39 34 36 4a 0b 17 "
					+ "03 00 00 00 00 3d e0 33 4d cb 50 97 ff ff ff ff "
					+ "ff ff ff ff 01 32 40 08 1a 12 3c 08 02 10 c6 7b "
					+ "18 01 22 0c 69 63 65 20 74 65 61 72 20 62 61 67 "
					+ "28 00 30 be 07 38 01 42 06 24 32 30 34 37 39 4a "
					+ "0b 17 07 00 00 00 00 3d e0 33 4d cb 50 97 ff ff "
					+ "ff ff ff ff ff ff 01 32 45 08 08 12 41 08 02 10 "
					+ "ee 6d 18 05 22 0f 70 73 79 20 73 70 69 63 79 20 "
					+ "72 61 6d 65 6e 28 00 30 be 26 38 01 42 06 24 31 "
					+ "30 39 33 36 4a 0d 15 78 00 03 02 00 00 00 3d e0 "
					+ "33 4d cb 50 97 ff ff ff ff ff ff ff ff 01 32 32 "
					+ "08 1f 12 2e 08 02 10 91 78 18 01 22 0c 6f 74 68 "
					+ "65 72 20 73 65 6c 66 20 33 28 44 30 b0 30 38 01 "
					+ "42 06 24 31 38 37 32 34 4a 06 17 03 00 00 00 00 "
					+ "50 17 32 44 08 0d 12 40 08 02 10 f1 6d 18 0a 22 "
					+ "0e 70 73 79 20 73 6f 66 74 20 64 72 69 6e 6b 28 "
					+ "00 30 bc 26 38 01 42 06 24 31 30 39 33 37 4a 0d "
					+ "15 78 00 03 02 00 00 00 3d e0 33 4d cb 50 97 ff "
					+ "ff ff ff ff ff ff ff 01 32 43 08 24 12 3f 08 02 "
					+ "10 b6 7b 18 01 22 0f 65 76 20 67 69 72 61 6e 20 "
					+ "63 68 61 72 67 65 28 00 30 e0 0f 38 01 42 06 24 "
					+ "32 30 34 35 33 4a 0b 17 07 00 00 00 00 3d e0 33 "
					+ "4d cb 50 97 ff ff ff ff ff ff ff ff 01 32 44 08 "
					+ "12 12 40 08 02 10 b4 7b 18 0a 22 10 65 76 20 6a "
					+ "6f 77 6f 6f 20 74 65 6c 62 6f 6f 6b 28 00 30 ff "
					+ "17 38 01 42 06 24 31 35 39 39 34 4a 0b 17 05 00 "
					+ "00 00 00 3d e0 33 4d cb 50 97 ff ff ff ff ff ff "
					+ "ff ff 01 32 44 08 29 12 40 08 02 10 f0 73 18 01 "
					+ "22 10 31 35 74 68 20 72 65 73 63 75 65 20 63 6f "
					+ "69 6e 28 00 30 e2 0f 38 01 42 06 24 31 30 39 33 "
					+ "38 4a 0b 17 05 00 00 00 00 3d e0 33 4d cb 50 97 "
					+ "ff ff ff ff ff ff ff ff 01 00 00";

	private SC_NewCreateItem() {}

	private static void writeTypeCk(WriteBuffer buffer, int _type, boolean _ck) {
		buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
		buffer.writeC(_type);
		switch (_type) {
		case 버프창:
			buffer.writeC(0);
			buffer.writeC(8);
			if (_ck) {
				buffer.writeC(1);
			} else {
				buffer.writeC(3);
			}
			buffer.writeC(0x10);
			buffer.writeC(0xba);
			buffer.writeC(0x04);
			buffer.writeH(0);
			break;

		case 사망패널티:
			if (_ck) {
				buffer.writeC(1);
				buffer.writeC(8);
				buffer.writeC(0x80);
				buffer.writeC(1);
				buffer.writeC(0x10);
				buffer.writeC(00);
				buffer.writeC(0x18);
				buffer.writeC(0);
				buffer.writeH(0);
			} else {
				buffer.writeC(1);
				buffer.writeC(8);
				buffer.writeC(0);
				buffer.writeC(0x10);
				buffer.writeC(0);
				buffer.writeC(0x18);
				buffer.writeC(0);
				buffer.writeH(0);
			}
			break;

		case 신규패킷2: // 0x20
			buffer.writeC(2);
			buffer.writeC(8);
			buffer.writeC(144);
			buffer.writeC(28);
			buffer.writeC(16);
			buffer.writeC(128);
			buffer.writeC(163);
			buffer.writeC(5);
			buffer.writeC(24);
			buffer.writeC(1);
			buffer.writeC(32);
			buffer.writeC(1);
			buffer.writeC(40);
			buffer.writeC(2);
			buffer.writeC(0);
			buffer.writeC(0);
			break;

		case 신규패킷3: // 0xe3
			buffer.writeC(1);
			buffer.writeC(8);
			buffer.writeC(2);
			buffer.writeC(0x00);
			buffer.writeC(0x00);
			buffer.writeC(8);
			buffer.writeC(0x00);
			buffer.writeC(0x10);
			buffer.writeC(0x00);
			buffer.writeC(0x18);
			buffer.writeC(0x00);
			buffer.writeC(0x20);
			buffer.writeC(0);
			buffer.writeC(0);
			buffer.writeC(0);
			buffer.writeC(0);
			break;

		case 신규패킷4: // 0xe5
			buffer.writeC(1);
			buffer.writeC(8);
			buffer.writeC(0x00);
			buffer.writeC(0x10);
			buffer.writeC(0x00);
			buffer.writeC(0x00);
			buffer.writeC(0x18);
			buffer.writeC(0);
			buffer.writeC(0);
			buffer.writeC(0);
			buffer.writeC(0);
			break;

		case 신규패킷5: // 0xe7
			buffer.writeC(1);
			buffer.writeC(0x0a);
			buffer.writeC(0);
			buffer.writeC(8);
			buffer.writeC(0x00);
			buffer.writeC(0x10);
			buffer.writeC(0x00);
			buffer.writeC(0x18);
			buffer.writeC(0);
			break;

		case 신규패킷6: // 0xea
			buffer.writeC(1);
			buffer.writeC(8);
			buffer.writeC(0x14);
			buffer.writeC(0x10);
			buffer.writeC(0x0a);
			buffer.writeC(0x18);
			buffer.writeC(7);
			buffer.writeC(0x20);
			buffer.writeC(0x12);
			buffer.writeC(0x28);
			buffer.writeC(0x10);
			buffer.writeC(0x30);
			buffer.writeC(9);
			buffer.writeC(0x00);
			buffer.writeC(0x00);
			break;

		case 신규패킷7: // 0xe9
			buffer.writeC(1);
			buffer.writeC(8);
			buffer.writeC(0);
			buffer.writeC(0x00);
			buffer.writeC(0x00);
			break;

		case 신규패킷8: // 0x2f
			buffer.writeC(2);
			buffer.writeC(8);
			buffer.writeC(0);
			buffer.writeC(0x10);
			buffer.writeC(0);
			buffer.writeC(0x00);
			buffer.writeC(0x00);
			break;

		case 신규패킷10: // 0x7e
			buffer.writeC(0);
			buffer.writeC(8);
			buffer.writeC(0);
			buffer.writeC(0x10);
			buffer.writeC(1);
			buffer.writeC(0x00);
			buffer.writeC(0x00);
			break;

		case 신규패킷11: // 0x77
			buffer.writeC(0);
			buffer.writeC(8);
			buffer.writeC(0x00);
			buffer.writeC(0x80);
			buffer.writeC(0x00);
			buffer.writeC(0x83);
			buffer.writeC(8);
			buffer.writeC(0x10);
			buffer.writeC(0x00);
			buffer.writeC(0x00);
			break;

		case 신규패킷12: // 0x76
			buffer.writeC(0);
			buffer.writeC(8);
			buffer.writeC(0x00);
			buffer.writeC(0x00);
			buffer.writeC(0x10);
			buffer.writeC(0x00);
			buffer.writeC(0x18);
			buffer.writeC(0);
			buffer.writeC(0x20);
			buffer.writeC(0x00);
			break;

		case 퀘스트:
			buffer.writeC(2);
			buffer.writeC(8);
			buffer.writeC(0);
			buffer.writeC(16);
			buffer.writeC(00);
			buffer.writeC(2);
			buffer.writeC(0);
			buffer.writeC(0);
			break;

		case 퀘스트2: // 0x09
			buffer.writeC(2);
			buffer.writeC(8);
			buffer.writeC(0);
			buffer.writeC(16);
			buffer.writeC(0);
			buffer.writeC(2);
			buffer.writeC(0);
			buffer.writeC(0);
			break;

		case 퀘스트3: // 0x3e
			buffer.writeC(1);
			buffer.writeC(10);
			buffer.writeC(38);
			buffer.writeC(8);
			buffer.writeC(1);
			buffer.writeC(18);
			buffer.writeC(4);
			buffer.writeC(0);
			buffer.writeC(0);
			break;

		case 퀘스트4: // 0x06
			buffer.writeC(2);
			buffer.writeC(10);
			buffer.writeC(0);
			buffer.writeC(8);
			buffer.writeC(0);
			buffer.writeC(2);
			buffer.writeC(16);
			buffer.writeC(0);
			buffer.writeC(0);
			break;

		case 퀘스트대화:
			buffer.writeC(2);
			buffer.writeC(8);
			buffer.writeC(0);
			buffer.writeC(16);
			buffer.writeC(0);
			buffer.writeC(2);
			buffer.writeC(0);
			buffer.writeC(0);
			break;

		case 신규패킷13:
			buffer.writeC(2);
			buffer.writeC(0x0a);
			buffer.writeC(0x00);
			buffer.writeC(8);
			buffer.writeC(0x00);
			buffer.writeC(2);
			buffer.writeC(0x10);
			buffer.writeC(0);
			buffer.writeC(0x00);
			buffer.writeC(0x00);
			break;
		}
	}

	private static void writeCastleWarTime(WriteBuffer buffer, int _warType, int _second, String _castle) {
		buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
		buffer.writeH(CASTLE_WAR_TIME);
		buffer.writeC(0x08);
		buffer.writeC(_warType); // 1: 수성, 2: 공성
		buffer.writeC(0x10);
		if (_second > 0) {
			buffer.writeBit(_second * 2);
			buffer.writeC(0x1a);
			buffer.writeS2(_castle);
			buffer.writeH(0);
		} else {
			buffer.writeC(0x00);
			buffer.writeH(0x00);
		}
	}

	private static void writeTypeSubtype(WriteBuffer buffer, int _type, int _subType) {
		buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
		buffer.writeC(_type);
		switch (_type) {
		case CLAN_JOIN_MESSAGE:
			buffer.writeH(0x0801);
			buffer.writeC(_subType);
			buffer.writeH(0x00);
			break;
		default:
			break;
		}
	}

	private static void writeTypeOrderSuborderCount(WriteBuffer buffer, int _type, int _order, int _subOrder, int _count) {
		buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
		buffer.writeH(_type);
		switch (_type) {
		case 버프창:
			buffer.writeC(8);
			buffer.writeC(2);
			buffer.writeC(16);
			buffer.writeC(224);
			buffer.writeC(17);
			buffer.writeC(24);
			buffer.writeBit(_order);
			buffer.writeC(32);
			buffer.writeC(8);
			buffer.writeC(40);
			buffer.writeBit(_subOrder);
			buffer.writeC(48);
			buffer.writeC(0);
			buffer.writeC(56);
			buffer.writeC(1);
			buffer.writeC(64);
			buffer.writeBit(_count);
			buffer.writeC(72);
			buffer.writeC(0);
			buffer.writeC(80);
			buffer.writeC(0);
			buffer.writeC(88);
			buffer.writeC(1);
			break;
		default:
			break;
		}
	}

	private static void writeTamPoint(WriteBuffer buffer, int _type, int _tamPoint) {
		buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
		buffer.writeH(_type);
		switch (_type) {
		case TAM_POINT:
			buffer.writeC(0x08);
			if (_tamPoint <= 0) {
				buffer.writeC(0x00);
			} else {
				byteWrite(buffer, _tamPoint);
			}
			buffer.writeH(0x00);
			break;
		}
	}

	private static void writeTypeSubtypeObjid(WriteBuffer buffer, int _type, int _subType, int _objId) {
		buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
		buffer.writeC(_type);
		switch (_type) {
		case EMOTICON:
			buffer.writeC(0x01);
			buffer.writeC(0x08);
			int temp = _objId / 128;
			if (temp > 0) {
				buffer.writeC(hextable[_objId % 128]);
				while (temp > 128) {
					buffer.writeC(hextable[temp % 128]);
					temp = temp / 128;
				}
				buffer.writeC(temp);
			} else {
				if (_objId == 0) {
					buffer.writeC(0);
				} else {
					buffer.writeC(hextable[_objId]);
					buffer.writeC(0);
				}
			}
			buffer.writeC(0x10);
			buffer.writeC(0x02);
			buffer.writeC(0x18);
			buffer.writeC(_subType);
			buffer.writeH(0);
			break;

		case CLAN_JOIN_SETTING:
			buffer.writeD(0x10010801);
			buffer.writeC(_subType); // 가입 설정
			buffer.writeC(0x18);
			buffer.writeC(_objId); // 가입 유형
			buffer.writeD(0x00001422);
			buffer.writeD(0x00);
			buffer.writeD(0x00);
			buffer.writeD(0x00);
			buffer.writeD(0x00);
			buffer.writeD(0x00);
			break;

		default:
			break;
		}
	}

	private static void byteWrite(WriteBuffer buffer, long value) {
		long temp = value / 128;
		if (temp > 0) {
			buffer.writeC(hextable[(int) (value % 128)]);
			while (temp >= 128) {
				buffer.writeC(hextable[(int) (temp % 128)]);
				temp = temp / 128;
			}
			if (temp > 0) {
				buffer.writeC((int) temp);
			}
		} else {
			if (value == 0) {
				buffer.writeC(0);
			} else {
				buffer.writeC(hextable[(int) value]);
				buffer.writeC(0);
			}
		}
	}
	
	

    public static boolean sendTypeCk(L1PcInstance pc, int type, boolean ck) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(384);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writeTypeCk(frame.buffer(), type, ck);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }


    private static CIOFrame beginFrame(CIOClient socket, int capacity) {
        if (socket == null || !socket.isConnected()) return null;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return null;
        return connection.beginDirectFrame(capacity);
    }

    private static boolean finishFrame(CIOClient socket, CIOFrame frame) {
        final CUserSocket connection = socket.getConnection();
        boolean committed = false;
        try {
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean sendSubtype(L1PcInstance pc, int type, int subType) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        final CIOFrame frame = beginFrame(socket, 64);
        if (frame == null) return false;
        try {
            writeTypeSubtype(frame.buffer(), type, subType);
        } catch (RuntimeException ex) {
            socket.getConnection().abortDirectFrame(frame);
            throw ex;
        }
        return finishFrame(socket, frame);
    }

    public static boolean sendBuffOrder(L1PcInstance pc, int type, int order, int subOrder, int count) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        final CIOFrame frame = beginFrame(socket, 128);
        if (frame == null) return false;
        try {
            writeTypeOrderSuborderCount(frame.buffer(), type, order, subOrder, count);
        } catch (RuntimeException ex) {
            socket.getConnection().abortDirectFrame(frame);
            throw ex;
        }
        return finishFrame(socket, frame);
    }

    public static boolean sendTamPoint(L1PcInstance pc, CIOClient client) {
        if (pc == null) return false;
        return sendTamPoint(client != null ? client : pc.getNetConnection());
    }

    public static boolean sendTamPoint(CIOClient socket) {
        if (socket == null) return false;
        final int value = socket.getAccount() != null ? socket.getAccount().tam_point : 0;
        final CIOFrame frame = beginFrame(socket, 64);
        if (frame == null) return false;
        try {
            writeTamPoint(frame.buffer(), TAM_POINT, value);
        } catch (RuntimeException ex) {
            socket.getConnection().abortDirectFrame(frame);
            throw ex;
        }
        return finishFrame(socket, frame);
    }

    public static boolean sendSubtypeObjid(L1PcInstance pc, int type, int subType, int objId) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        final CIOFrame frame = beginFrame(socket, 96);
        if (frame == null) return false;
        try {
            writeTypeSubtypeObjid(frame.buffer(), type, subType, objId);
        } catch (RuntimeException ex) {
            socket.getConnection().abortDirectFrame(frame);
            throw ex;
        }
        return finishFrame(socket, frame);
    }

    public static boolean sendCastleTime(L1PcInstance pc, int warType, int second, String castle) {
        if (pc == null) return false;
        final String name = castle == null ? "" : castle;
        final CIOClient socket = pc.getNetConnection();
        final long capacity = 32L + 4L * name.length();
        if (capacity > Integer.MAX_VALUE) return false;
        final CIOFrame frame = beginFrame(socket, (int)capacity);
        if (frame == null) return false;
        try {
            writeCastleWarTime(frame.buffer(), warType, second, name);
        } catch (RuntimeException ex) {
            socket.getConnection().abortDirectFrame(frame);
            throw ex;
        }
        return finishFrame(socket, frame);
    }

}
