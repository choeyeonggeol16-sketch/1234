/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.serverpackets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.configure.Config;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class S_EnchantRankings extends ServerBasePacket {

  private static final String S_EnchantRankings = "[C] S_EnchantRankings";
	private static Logger _log = Logger.getLogger(S_EnchantRankings.class.getName());

 	private byte[] _byte = null;
  	static String[] name;
  	static String[] name1;
 	static int[] armor;
    static int[] enchantlvl;
 	
	public S_EnchantRankings(L1PcInstance pc, int number) {
	
	enchantlvl = new int[10];
	name = new String[10];
    name1 = new String[10];
	armor = new int [10];

	buildPacket(pc, number);
 	}

	public S_EnchantRankings(L1NpcInstance board) {
		buildPacket(board);
	}

	private void buildPacket(L1NpcInstance board) {
		int count = 0;
		String[][] db = null;
		int[] id = null;
		db = new String[8][3];
		id = new int[8];
		while(count < 8) {
			id[count] = count + 1;
			db[count][0] = "Ranking";
			db[count][1] = "";
			count++;
		}
		db[0][2] = "1. 무기 인챈 랭킹";
		db[1][2] = "2. 투구 인챈 랭킹";
		db[2][2] = "3. 갑옷 인챈 랭킹";
		db[3][2] = "4. 신발 인챈 랭킹";
		db[4][2] = "5. 장갑 인챈 랭킹";
		db[5][2] = "6. 셔츠 인챈 랭킹";
		db[6][2] = "7. 망토 인챈 랭킹";
		db[7][2] = "8. 방패 인챈 랭킹";

		writeC(Opcodes.S_OPCODE_BOARD);
		writeD(board.getId());
		writeC(0xFF); // ?
		writeC(0xFF); // ?
		writeC(0xFF); // ?
		writeC(0x7F); // ?
		writeH(8);
		writeH(300);
		for (int i = 0; i < 8; ++i) {
			writeD(id[i]);
			writeS(db[i][0]);
			writeS(db[i][1]);
			writeS(db[i][2]);
		}
  }

 	private void buildPacket(L1PcInstance pc, int number) {
 		String date = time();
 		String type = null;
 		String title = null;

	  writeC(Opcodes.S_OPCODE_BOARDREAD);
	  writeD(number);
	  writeS("운영자");
	  switch(number) {
	  	case 1:
	  		title = "무기 인챈 랭킹";
	  		break;
	  	case 2:
	  		title = "투구 인챈 랭킹";
	  		break;
	  	case 3:
	  		title = "갑옷 인챈 랭킹";
	  		break;
	  	case 4:
	  		title = "신발 인챈 랭킹";
	  		break;
	  	case 5:
	  		title = "장갑 인챈 랭킹";
	  		break;
	  	case 6:
	  		title = "셔츠 인챈 랭킹";
	  		break;
	  	case 7:
	  		title = "망토 인챈 랭킹";
	  		break;
		case 8:
	  		title = "방패 인챈 랭킹";
	  		break;
	  }	 
writeS(title);
	  writeS(date);
	  switch(pc.getType()) {
	  	case 0:
	  		type = "군주";
	  		break;
	  	case 1:
	  		type = "기사";
	  		break;
	  	case 2:
	  		type = "요정";
	  		break;
	  	case 3:
	  		type = "마법사";
	  		break;
	  	case 4:
	  		type = "다크엘프";
	  		break;
	  }
	int p = Rank(pc, number);
	    if(number == 1) { //추가부분입니다
	        writeS("\n\r" + "  1위 "+ "+" + enchantlvl[0] + " " + name[0] + "\n\r" + "  소유자 : " + name1[0] +"\n\r" +
	  	          "  2위 " + "+" + enchantlvl[1] + " " + name[1] +"\n\r" + "  소유자 : " + name1[1] + "\n\r" +
	  	          "  3위 " + "+" + enchantlvl[2]  + " " + name[2]+ "\n\r" + "  소유자 : " + name1[2] + "\n\r" +
	  	          "  4위 " + "+" + enchantlvl[3] + " " + name[3] + "\n\r" + "  소유자 : " + name1[3] + "\n\r" +
	  	          "  5위 " + "+" + enchantlvl[4] + " " + name[4] + "\n\r" + "  소유자 : " + name1[4] + "\n\r" +
	  	          "  6위 " + "+" + enchantlvl[5] + " " + name[5] + "\n\r" + "  소유자 : " + name1[5] + "\n\r" +
	  	          "  7위 " + "+" + enchantlvl[6] + " " + name[6] + "\n\r" + "  소유자 : " + name1[6] +"\n\r" +
				  "  8위 " + "+" + enchantlvl[7] + " " + name[7] + "\n\r" + "  소유자 : " + name1[7] + "\n\r" +
	  	          "  9위 " + "+" + enchantlvl[8] + " " + name[8] + "\n\r" + "  소유자 : " + name1[8] + "\n\r" +
	  	          "  10위 " + "+" + enchantlvl[9] + " " + name[9] + "\n\r" + "  소유자 : " + name1[9] +"\n\r" +
	  	                      "      ");
	    }else if(number == 2) { //추가부분입니다
	        writeS("\n\r" + "  1위 "+ "+" + armor[0] + " " + name[0] + "\n\r" + "  소유자 : " + name1[0] +"\n\r" +
	  	          "  2위 " + "+" + armor[1] + " " + name[1] +"\n\r" + "  소유자 : " + name1[1] + "\n\r" +
	  	          "  3위 " + "+" + armor[2]  + " " + name[2]+ "\n\r" + "  소유자 : " + name1[2] + "\n\r" +
	  	          "  4위 " + "+" + armor[3] + " " + name[3] + "\n\r" + "  소유자 : " + name1[3] + "\n\r" +
	  	          "  5위 " + "+" + armor[4] + " " + name[4] + "\n\r" + "  소유자 : " + name1[4] + "\n\r" +
	  	          "  6위 " + "+" + armor[5] + " " + name[5] + "\n\r" + "  소유자 : " + name1[5] + "\n\r" +
	  	          "  7위 " + "+" + armor[6] + " " + name[6] + "\n\r" + "  소유자 : " + name1[6] +"\n\r" +
				  "  8위 " + "+" + armor[7] + " " + name[7] + "\n\r" + "  소유자 : " + name1[7] + "\n\r" +
	  	          "  9위 " + "+" + armor[8] + " " + name[8] + "\n\r" + "  소유자 : " + name1[8] + "\n\r" +
	  	          "  10위 " + "+" + armor[9] + " " + name[9] + "\n\r" + "  소유자 : " + name1[9] +"\n\r" +
	  	                      "      ");
}else if(number == 3) { //추가부분입니다
	        writeS("\n\r" + "  1위 "+ "+" + armor[0] + " " + name[0] + "\n\r" + "  소유자 : " + name1[0] +"\n\r" +
	  	          "  2위 " + "+" + armor[1] + " " + name[1] +"\n\r" + "  소유자 : " + name1[1] + "\n\r" +
	  	          "  3위 " + "+" + armor[2]  + " " + name[2]+ "\n\r" + "  소유자 : " + name1[2] + "\n\r" +
	  	          "  4위 " + "+" + armor[3] + " " + name[3] + "\n\r" + "  소유자 : " + name1[3] + "\n\r" +
	  	          "  5위 " + "+" + armor[4] + " " + name[4] + "\n\r" + "  소유자 : " + name1[4] + "\n\r" +
	  	          "  6위 " + "+" + armor[5] + " " + name[5] + "\n\r" + "  소유자 : " + name1[5] + "\n\r" +
	  	          "  7위 " + "+" + armor[6] + " " + name[6] + "\n\r" + "  소유자 : " + name1[6] +"\n\r" +
				  "  8위 " + "+" + armor[7] + " " + name[7] + "\n\r" + "  소유자 : " + name1[7] + "\n\r" +
	  	          "  9위 " + "+" + armor[8] + " " + name[8] + "\n\r" + "  소유자 : " + name1[8] + "\n\r" +
	  	          "  10위 " + "+" + armor[9] + " " + name[9] + "\n\r" + "  소유자 : " + name1[9] +"\n\r" +
	  	                      "      ");
}else if(number == 4) { //추가부분입니다
	        writeS("\n\r" + "  1위 "+ "+" + armor[0] + " " + name[0] + "\n\r" + "  소유자 : " + name1[0] +"\n\r" +
	  	          "  2위 " + "+" + armor[1] + " " + name[1] +"\n\r" + "  소유자 : " + name1[1] + "\n\r" +
	  	          "  3위 " + "+" + armor[2]  + " " + name[2]+ "\n\r" + "  소유자 : " + name1[2] + "\n\r" +
	  	          "  4위 " + "+" + armor[3] + " " + name[3] + "\n\r" + "  소유자 : " + name1[3] + "\n\r" +
	  	          "  5위 " + "+" + armor[4] + " " + name[4] + "\n\r" + "  소유자 : " + name1[4] + "\n\r" +
	  	          "  6위 " + "+" + armor[5] + " " + name[5] + "\n\r" + "  소유자 : " + name1[5] + "\n\r" +
	  	          "  7위 " + "+" + armor[6] + " " + name[6] + "\n\r" + "  소유자 : " + name1[6] +"\n\r" +
				  "  8위 " + "+" + armor[7] + " " + name[7] + "\n\r" + "  소유자 : " + name1[7] + "\n\r" +
	  	          "  9위 " + "+" + armor[8] + " " + name[8] + "\n\r" + "  소유자 : " + name1[8] + "\n\r" +
	  	          "  10위 " + "+" + armor[9] + " " + name[9] + "\n\r" + "  소유자 : " + name1[9] +"\n\r" +
	  	                      "      ");
}else if(number == 5) { //추가부분입니다
	        writeS("\n\r" + "  1위 "+ "+" + armor[0] + " " + name[0] + "\n\r" + "  소유자 : " + name1[0] +"\n\r" +
	  	          "  2위 " + "+" + armor[1] + " " + name[1] +"\n\r" + "  소유자 : " + name1[1] + "\n\r" +
	  	          "  3위 " + "+" + armor[2]  + " " + name[2]+ "\n\r" + "  소유자 : " + name1[2] + "\n\r" +
	  	          "  4위 " + "+" + armor[3] + " " + name[3] + "\n\r" + "  소유자 : " + name1[3] + "\n\r" +
	  	          "  5위 " + "+" + armor[4] + " " + name[4] + "\n\r" + "  소유자 : " + name1[4] + "\n\r" +
	  	          "  6위 " + "+" + armor[5] + " " + name[5] + "\n\r" + "  소유자 : " + name1[5] + "\n\r" +
	  	          "  7위 " + "+" + armor[6] + " " + name[6] + "\n\r" + "  소유자 : " + name1[6] +"\n\r" +
				  "  8위 " + "+" + armor[7] + " " + name[7] + "\n\r" + "  소유자 : " + name1[7] + "\n\r" +
	  	          "  9위 " + "+" + armor[8] + " " + name[8] + "\n\r" + "  소유자 : " + name1[8] + "\n\r" +
	  	          "  10위 " + "+" + armor[9] + " " + name[9] + "\n\r" + "  소유자 : " + name1[9] +"\n\r" +
	  	                      "      ");
}else if(number == 6) { //추가부분입니다
	        writeS("\n\r" + "  1위 "+ "+" + armor[0] + " " + name[0] + "\n\r" + "  소유자 : " + name1[0] +"\n\r" +
	  	          "  2위 " + "+" + armor[1] + " " + name[1] +"\n\r" + "  소유자 : " + name1[1] + "\n\r" +
	  	          "  3위 " + "+" + armor[2]  + " " + name[2]+ "\n\r" + "  소유자 : " + name1[2] + "\n\r" +
	  	          "  4위 " + "+" + armor[3] + " " + name[3] + "\n\r" + "  소유자 : " + name1[3] + "\n\r" +
	  	          "  5위 " + "+" + armor[4] + " " + name[4] + "\n\r" + "  소유자 : " + name1[4] + "\n\r" +
	  	          "  6위 " + "+" + armor[5] + " " + name[5] + "\n\r" + "  소유자 : " + name1[5] + "\n\r" +
	  	          "  7위 " + "+" + armor[6] + " " + name[6] + "\n\r" + "  소유자 : " + name1[6] +"\n\r" +
				  "  8위 " + "+" + armor[7] + " " + name[7] + "\n\r" + "  소유자 : " + name1[7] + "\n\r" +
	  	          "  9위 " + "+" + armor[8] + " " + name[8] + "\n\r" + "  소유자 : " + name1[8] + "\n\r" +
	  	          "  10위 " + "+" + armor[9] + " " + name[9] + "\n\r" + "  소유자 : " + name1[9] +"\n\r" +
	  	                      "      ");
}else if(number == 7) { //추가부분입니다
	        writeS("\n\r" + "  1위 "+ "+" + armor[0] + " " + name[0] + "\n\r" + "  소유자 : " + name1[0] +"\n\r" +
	  	          "  2위 " + "+" + armor[1] + " " + name[1] +"\n\r" + "  소유자 : " + name1[1] + "\n\r" +
	  	          "  3위 " + "+" + armor[2]  + " " + name[2]+ "\n\r" + "  소유자 : " + name1[2] + "\n\r" +
	  	          "  4위 " + "+" + armor[3] + " " + name[3] + "\n\r" + "  소유자 : " + name1[3] + "\n\r" +
	  	          "  5위 " + "+" + armor[4] + " " + name[4] + "\n\r" + "  소유자 : " + name1[4] + "\n\r" +
	  	          "  6위 " + "+" + armor[5] + " " + name[5] + "\n\r" + "  소유자 : " + name1[5] + "\n\r" +
	  	          "  7위 " + "+" + armor[6] + " " + name[6] + "\n\r" + "  소유자 : " + name1[6] +"\n\r" +
				  "  8위 " + "+" + armor[7] + " " + name[7] + "\n\r" + "  소유자 : " + name1[7] + "\n\r" +
	  	          "  9위 " + "+" + armor[8] + " " + name[8] + "\n\r" + "  소유자 : " + name1[8] + "\n\r" +
	  	          "  10위 " + "+" + armor[9] + " " + name[9] + "\n\r" + "  소유자 : " + name1[9] +"\n\r" +
	  	                      "      ");
}else if(number == 8) { //추가부분입니다
	        writeS("\n\r" + "  1위 "+ "+" + armor[0] + " " + name[0] + "\n\r" + "  소유자 : " + name1[0] +"\n\r" +
	  	          "  2위 " + "+" + armor[1] + " " + name[1] +"\n\r" + "  소유자 : " + name1[1] + "\n\r" +
	  	          "  3위 " + "+" + armor[2]  + " " + name[2]+ "\n\r" + "  소유자 : " + name1[2] + "\n\r" +
	  	          "  4위 " + "+" + armor[3] + " " + name[3] + "\n\r" + "  소유자 : " + name1[3] + "\n\r" +
	  	          "  5위 " + "+" + armor[4] + " " + name[4] + "\n\r" + "  소유자 : " + name1[4] + "\n\r" +
	  	          "  6위 " + "+" + armor[5] + " " + name[5] + "\n\r" + "  소유자 : " + name1[5] + "\n\r" +
	  	          "  7위 " + "+" + armor[6] + " " + name[6] + "\n\r" + "  소유자 : " + name1[6] +"\n\r" +
				  "  8위 " + "+" + armor[7] + " " + name[7] + "\n\r" + "  소유자 : " + name1[7] + "\n\r" +
	  	          "  9위 " + "+" + armor[8] + " " + name[8] + "\n\r" + "  소유자 : " + name1[8] + "\n\r" +
	  	          "  10위 " + "+" + armor[9] + " " + name[9] + "\n\r" + "  소유자 : " + name1[9] +"\n\r" +
	  	                      "      ");
  }
	}
	private int Rank(L1PcInstance pc, int number) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		int i = 0;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			switch(number) {
				case 1:
		        		pstm = con.prepareStatement("SELECT enchantlvl, weapon.name, characters.char_name  FROM character_items, weapon, characters WHERE character_items.item_id in(select item_id from weapon) And character_items.char_id in(select objid from characters where AccessLevel = 0) And character_items.item_id=weapon.item_id And character_items.char_id=characters.objid order by character_items.enchantlvl desc limit 10");
					break;
				case 2:
					pstm = con.prepareStatement("SELECT enchantlvl, armor.name, characters.char_name  FROM character_items, armor, characters WHERE character_items.item_id in(select item_id from armor where type = 'helm') And character_items.char_id in(select objid from characters where AccessLevel = 0) And character_items.item_id=armor.item_id And character_items.char_id=characters.objid order by character_items.enchantlvl desc limit 10");
					break;
				case 3:
					pstm = con.prepareStatement("SELECT enchantlvl, armor.name, characters.char_name  FROM character_items, armor, characters WHERE character_items.item_id in(select item_id from armor where type = 'armor') And character_items.char_id in(select objid from characters where AccessLevel = 0) And character_items.item_id=armor.item_id And character_items.char_id=characters.objid order by character_items.enchantlvl desc limit 10");
					break;
				case 4:
pstm = con.prepareStatement("SELECT enchantlvl, armor.name, characters.char_name  FROM character_items, armor, characters WHERE character_items.item_id in(select item_id from armor where type = 'boots') And character_items.char_id in(select objid from characters where AccessLevel = 0) And character_items.item_id=armor.item_id And character_items.char_id=characters.objid order by character_items.enchantlvl desc limit 10");
					break;
				case 5:
pstm = con.prepareStatement("SELECT enchantlvl, armor.name, characters.char_name  FROM character_items, armor, characters WHERE character_items.item_id in(select item_id from armor where type = 'glove') And character_items.char_id in(select objid from characters where AccessLevel = 0) And character_items.item_id=armor.item_id And character_items.char_id=characters.objid order by character_items.enchantlvl desc limit 10");
					break;
				case 6:
pstm = con.prepareStatement("SELECT enchantlvl, armor.name, characters.char_name  FROM character_items, armor, characters WHERE character_items.item_id in(select item_id from armor where type = 'T') And character_items.char_id in(select objid from characters where AccessLevel = 0) And character_items.item_id=armor.item_id And character_items.char_id=characters.objid order by character_items.enchantlvl desc limit 10");
					break;
				case 7:
pstm = con.prepareStatement("SELECT enchantlvl, armor.name, characters.char_name  FROM character_items, armor, characters WHERE character_items.item_id in(select item_id from armor where type = 'cloak') And character_items.char_id in(select objid from characters where AccessLevel = 0) And character_items.item_id=armor.item_id And character_items.char_id=characters.objid order by character_items.enchantlvl desc limit 10");
					break;
				case 8:
pstm = con.prepareStatement("SELECT enchantlvl, armor.name, characters.char_name  FROM character_items, armor, characters WHERE character_items.item_id in(select item_id from armor where type = 'shield') And character_items.char_id in(select objid from characters where AccessLevel = 0) And character_items.item_id=armor.item_id And character_items.char_id=characters.objid order by character_items.enchantlvl desc limit 10");
					break;
				
				default:
					pstm = con.prepareStatement("SELECT char_name FROM characters WHERE AccessLevel = 0 order by Exp desc limit 10");
					break;
			}
			rs = pstm.executeQuery();
			if(number == 1) { //추가부분입니다
				    while(rs.next()){
				     enchantlvl[i] = rs.getInt(1);
				    	name[i] = rs.getString(2);
				    	name1[i] = rs.getString(3);
				      i++;
				    }
			    }else if(number == 2) { //추가부분입니다
				    while(rs.next()){
				    	armor[i] = rs.getInt(1);
				    	name[i] = rs.getString(2);
				    	name1[i] = rs.getString(3);
					      i++;
					}
			    }else if(number == 3) { //추가부분입니다
				    while(rs.next()){
				    	armor[i] = rs.getInt(1);
				    	name[i] = rs.getString(2);
				    	name1[i] = rs.getString(3);
					      i++;
					}
			    }else if(number == 4) { //추가부분입니다
				    while(rs.next()){
				    	armor[i] = rs.getInt(1);
				    	name[i] = rs.getString(2);
				    	name1[i] = rs.getString(3);
					      i++;
					}
			    }else if(number == 5) { //추가부분입니다
				    while(rs.next()){
				    	armor[i] = rs.getInt(1);
				    	name[i] = rs.getString(2);
				    	name1[i] = rs.getString(3);
					      i++;
					}
			    }else if(number == 6) { //추가부분입니다
				    while(rs.next()){
				    	armor[i] = rs.getInt(1);
				    	name[i] = rs.getString(2);
				    	name1[i] = rs.getString(3);
					      i++;
					}
			    }else if(number == 7) { //추가부분입니다
				    while(rs.next()){
				    	armor[i] = rs.getInt(1);
				    	name[i] = rs.getString(2);
				    	name1[i] = rs.getString(3);
					      i++;
					}
			    }else if(number == 8) { //추가부분입니다
				    while(rs.next()){
				    	armor[i] = rs.getInt(1);
				    	name[i] = rs.getString(2);
				    	name1[i] = rs.getString(3);
					      i++;
					}
			    }else {
				while(rs.next()){
					name[i] = rs.getString(1);
					i++;
				}

				// 레코드가 없거나 5보다 작을때
				while(i < 10){
					name[i] = "없음.";
					i++;
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}

		return i;
	}
	private static String time() {
		TimeZone tz = TimeZone.getTimeZone(Config.TIME_ZONE);
		Calendar cal = Calendar.getInstance(tz);
		int year = cal.get(Calendar.YEAR) - 2000;
		String year2;
		if (year < 10) {
			year2 = "0" + year;
		} else {
			year2 = Integer.toString(year);
		}
		int Month = cal.get(Calendar.MONTH) + 1;
		String Month2 = null;
		if (Month < 10) {
			Month2 = "0" + Month;
		} else {
			Month2 = Integer.toString(Month);
		}
		int date = cal.get(Calendar.DATE);
		String date2 = null;
		if (date < 10) {
			date2 = "0" + date;
		} else {
			date2 = Integer.toString(date);
		}
		return year2 + "/" + Month2 + "/" + date2;
	}

 	@Override
 	public byte[] getContent() {
  	if (_byte == null) {
   		_byte = getBytes();
  	}
  	return _byte;
 	}

 	public String getType() {
  	return S_EnchantRankings;
 	}

}
