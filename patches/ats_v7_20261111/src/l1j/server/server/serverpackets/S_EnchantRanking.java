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

public class S_EnchantRanking extends ServerBasePacket {

  private static final String S_EnchantRanking = "[C] S_EnchantRanking";

	private static Logger _log = Logger.getLogger(S_EnchantRanking.class.getName());

  static String[] name;
  static String[] name1;
  static String[] castlename;
  static String[] clanname;
  static String[] leadername;
  static int[] enchantlvl;
  static int[] aden;
  static int[] armor;
  static int[] pkcount;
  static int[] priaden;
  static int[] castleid;
  static int[] hascastle;
  static int[] taxrate;
  static int[] castleaden;
 	public S_EnchantRanking(L1PcInstance pc, int number) {
    name = new String[10];
    name1 = new String[10];
    enchantlvl = new int [10];
    aden = new int [10];
    armor = new int [10];
    pkcount = new int [10];
    priaden = new int [10];
    castlename = new String [10];
    clanname = new String [10];
    leadername = new String [10];
    castleid = new int [10];
    hascastle = new int [10];
    taxrate = new int [10];
    castleaden = new int [10];
  	buildPacket(pc, number);
 	}

	public S_EnchantRanking(L1NpcInstance board) {
		buildPacket(board);
	}

	private void buildPacket(L1NpcInstance board) {
		int count = 0;
		String[][] db = null;
		int[] id = null;
		db = new String[6][3];
		id = new int[6];
		while(count < 6) {
			id[count] = count + 1;
			db[count][0] = "Ranking";
			db[count][1] = "";
			count++;
		}
		db[0][2] = "1. 아덴 랭킹";
		db[1][2] = "2. 살인 랭킹";
		db[2][2] = "3. 깃털 랭킹";
		db[3][2] = "4. 성 관련";
		db[4][2] = "5. 운영자 무기";
		db[5][2] = "6. 운영자 갑옷";

		writeC(Opcodes.S_OPCODE_BOARD);
		writeD(board.getId());
		writeC(0xFF); // ?
		writeC(0xFF); // ?
		writeC(0xFF); // ?
		writeC(0x7F); // ?
		writeH(6);
		writeH(300);
		for (int i = 0; i < 6; ++i) {
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
	  		title = "아덴 랭킹";
	  		break;
	  	case 2:
	  		title = "살인 랭킹";
	  		break;
	  	case 3:
	  		title = "깃털 랭킹";
	  		break;
	  	case 4:
	  		title = "성 관련";
	  		break;
	  	case 5:
	  		title = "운영자 무기";
	  		break;
	  	case 6:
	  		title = "운영자 갑옷";
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
		if(number == 1) { //추가부분입니다
	        writeS("\n\r" + "  1위 "+ "$ " + aden[0] + " 아데나\n\r" + "  소유자 : " + name[0] + "\n\r" +
	  	          "  2위 " + "$ " + aden[1] + " 아데나\n\r" + "  소유자 : " + name[1] + "\n\r" +
	  	          "  3위 " + "$ " + aden[2] + " 아데나\n\r" + "  소유자 : " + name[2] + "\n\r" +
	  	          "  4위 " + "$ " + aden[3] + " 아데나\n\r" + "  소유자 : " + name[3] + "\n\r" +
	  	          "  5위 " + "$ " + aden[4] + " 아데나\n\r" + "  소유자 : " + name[4] + "\n\r" +
	  	          "  6위 " + "$ " + aden[5] + " 아데나\n\r" + "  소유자 : " + name[5] + "\n\r" +
	  	          "  7위 " + "$ " + aden[6] + " 아데나\n\r" + "  소유자 : " + name[6] + "\n\r" +
	  	          "  8위 " + "$ " + aden[7] + " 아데나\n\r" + "  소유자 : " + name[7] + "\n\r" +
	  	          "  9위 " + "$ " + aden[8] + " 아데나\n\r" + "  소유자 : " + name[8] + "\n\r" +
	  	          " 10위 " + "$ " + aden[9] + " 아데나\n\r" + "  소유자 : " + name[9] + "\n\r" +
	  	                      "      ");
	  }else if(number == 2) { //추가부분입니다
	        writeS("\n\r" + "  1위 "+ pkcount[0] + " 명 살인\n\r" + "  살생자 : " + name[0] + "\n\r" +
	  	          "  2위 " + pkcount[1] + " 명 살인\n\r" + "  살생자 : " + name[1] + "\n\r" +
	  	          "  3위 " + pkcount[2] + " 명 살인\n\r" + "  살생자 : " + name[2] + "\n\r" +
	  	          "  4위 " + pkcount[3] + " 명 살인\n\r" + "  살생자 : " + name[3] + "\n\r" +
	  	          "  5위 " + pkcount[4] + " 명 살인\n\r" + "  살생자 : " + name[4] + "\n\r" +
	  	          "  6위 " + pkcount[5] + " 명 살인\n\r" + "  살생자 : " + name[5] + "\n\r" +
	  	          "  7위 " + pkcount[6] + " 명 살인\n\r" + "  살생자 : " + name[6] + "\n\r" +
	  	          "  8위 " + pkcount[7] + " 명 살인\n\r" + "  살생자 : " + name[7] + "\n\r" +
	  	          "  9위 " + pkcount[8] + " 명 살인\n\r" + "  살생자 : " + name[8] + "\n\r" +
	  	          " 10위 " + pkcount[9] + " 명 살인\n\r" + "  살생자 : " + name[9] + "\n\r" +
	                      "      ");
	  }else if(number == 3) { //추가부분입니다
	        writeS("\n\r" + "  1위 "+ priaden[0] + "개의 깃털\n\r" + "  소유자 : " + name[0] + "\n\r" +
	  	          "  2위 " + priaden[1] + "개의 깃털\n\r" + "  소유자 : " + name[1] + "\n\r" +
	  	          "  3위 " + priaden[2] + "개의 깃털\n\r" + "  소유자 : " + name[2] + "\n\r" +
	  	          "  4위 " + priaden[3] + "개의 깃털\n\r" + "  소유자 : " + name[3] + "\n\r" +
	  	          "  5위 " + priaden[4] + "개의 깃털\n\r" + "  소유자 : " + name[4] + "\n\r" +
	  	          "  6위 " + priaden[5] + "개의 깃털\n\r" + "  소유자 : " + name[5] + "\n\r" +
	  	          "  7위 " + priaden[6] + "개의 깃털\n\r" + "  소유자 : " + name[6] + "\n\r" +
	  	          "  8위 " + priaden[7] + "개의 깃털\n\r" + "  소유자 : " + name[7] + "\n\r" +
	  	          "  9위 " + priaden[8] + "개의 깃털\n\r" + "  소유자 : " + name[8] + "\n\r" +
	  	          " 10위 " + priaden[9] + "개의 깃털\n\r" + "  소유자 : " + name[9] + "\n\r" +
	  	                      "      ");
	  }else if(number == 4) { //추가부분입니다
	        writeS("\n\r" + castlename[0] + " " + clanname[0] + " 길드 \n\r" + leadername[0]+ " 성주 " + taxrate[0] + "% 세율 \n\r" + "누적 아데나 : " + castleaden[0] +"원 \n\r\n\r" +
	  	          castlename[1] + " " + clanname[1] + " 길드 \n\r" + leadername[1] + " 성주 " + taxrate[1] + "% 세율 \n\r" + "누적 아데나 : " + castleaden[1] + "원 \n\r\n\r" +
	  	          castlename[2] + " " + clanname[2] + " 길드 \n\r" + leadername[2] + " 성주 " + taxrate[2] + "% 세율 \n\r" + "누적 아데나 : " + castleaden[2] + "원 \n\r\n\r" +
	  	          castlename[3] + " " + clanname[3] + " 길드 \n\r" + leadername[3] + " 성주 " + taxrate[3] + "% 세율 \n\r" + "누적 아데나 : " + castleaden[3] + "원 \n\r\n\r" +
	  	      //    castlename[4] + " " + clanname[4] + "길드 \n\r" + leadername[4] + "성주님 " + taxrate[4] + "% 세율 \n\r" + "누적 아데나 : " + castleaden[4] + "원 \n\r" +
	  	      //    castlename[5] + " " + clanname[5] + "길드 \n\r" + leadername[5] + "성주님 " + taxrate[5] + "% 세율 \n\r" + "누적 아데나 : " + castleaden[5] + "원 \n\r" +
	  	      //    castlename[6] + " " + clanname[6] + "길드 \n\r" + leadername[6] + "성주님 " + taxrate[6] + "% 세율 \n\r" + "누적 아데나 : " + castleaden[6] + "원 \n\r" +
	  	      //    castlename[7] + " " + clanname[7] + "길드 \n\r" + leadername[7] + "성주님 " + taxrate[7] + "% 세율 \n\r" + "누적 아데나 : " + castleaden[7] + "원 \n\r" +
	  	      //    castlename[8] + " " + clanname[8] + "길드 \n\r" + leadername[8] + "성주님 " + taxrate[8] + "% 세율 \n\r" + "누적 아데나 : " + castleaden[8] + "원 \n\r" +
	  	      //    castlename[9] + " " + clanname[9] + "길드 \n\r" + leadername[9] + "성주님 " + taxrate[9] + "% 세율 \n\r" + "누적 아데나 : " + castleaden[9] + "원 \n\r" +
	  	                      "      ");
	  }else if(number == 5) { //추가부분입니다
		        writeS("\n\r" + "  1위 "+ "+" + enchantlvl[0] + " " + name[0] + "\n\r" + "  소유자 : " + name1[0] +"\n\r" +
		  	          "  2위 " + "+" + enchantlvl[1] + " " + name[1] +"\n\r" + "  소유자 : " + name1[1] + "\n\r" +
		  	          "  3위 " + "+" + enchantlvl[2]  + " " + name[2]+ "\n\r" + "  소유자 : " + name1[2] + "\n\r" +
		  	          "  4위 " + "+" + enchantlvl[3] + " " + name[3] + "\n\r" + "  소유자 : " + name1[3] + "\n\r" +
		  	          "  5위 " + "+" + enchantlvl[4] + " " + name[4] + "\n\r" + "  소유자 : " + name1[4] + "\n\r" +
		  	          "  6위 " + "+" + enchantlvl[5] + " " + name[5] + "\n\r" + "  소유자 : " + name1[5] + "\n\r" +
		  	          "  7위 " + "+" + enchantlvl[6] + " " + name[6] + "\n\r" + "  소유자 : " + name1[6] +"\n\r" +
		  	          "  8위 " + "+" + enchantlvl[7] + " " + name[7] + "\n\r" + "  소유자 : " + name1[7] +"\n\r" +
		  	          "  9위 " + "+" + enchantlvl[8] + " " + name[8] + "\n\r" + "  소유자 : " + name1[8] +"\n\r"+
		  	          " 10위 " + "+" + enchantlvl[9] + " " + name[9] + "\n\r" + "  소유자 : " + name1[9] +"\n\r" +
		  	                      "      ");
		  }else if(number == 6) { //추가부분입니다
		        writeS("\n\r" + "  1위 "+ "+" + armor[0] + " " + name[0] + "\n\r" + "  소유자 : " + name1[0] +"\n\r" +
		  	          "  2위 " + "+" + armor[1] + " " + name[1] +"\n\r" + "  소유자 : " + name1[1] + "\n\r" +
		  	          "  3위 " + "+" + armor[2]  + " " + name[2]+ "\n\r" + "  소유자 : " + name1[2] + "\n\r" +
		  	          "  4위 " + "+" + armor[3] + " " + name[3] + "\n\r" + "  소유자 : " + name1[3] + "\n\r" +
		  	          "  5위 " + "+" + armor[4] + " " + name[4] + "\n\r" + "  소유자 : " + name1[4] + "\n\r" +
		  	          "  6위 " + "+" + armor[5] + " " + name[5] + "\n\r" + "  소유자 : " + name1[5] + "\n\r" +
		  	          "  7위 " + "+" + armor[6] + " " + name[6] + "\n\r" + "  소유자 : " + name1[6] +"\n\r" +
		  	          "  8위 " + "+" + armor[7] + " " + name[7] + "\n\r" + "  소유자 : " + name1[7] +"\n\r" +
		  	          "  9위 " + "+" + armor[8] + " " + name[8] + "\n\r" + "  소유자 : " + name1[8] +"\n\r"+
		  	          " 10위 " + "+" + armor[9] + " " + name[9] + "\n\r" + "  소유자 : " + name1[9] +"\n\r" +
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
			switch(number) 	{
		    case 1:
		    	pstm = con.prepareStatement("SELECT count, characters.char_name FROM character_items, characters WHERE item_id in(select item_id from etcitem) And char_id in(select objid from characters where AccessLevel = 0) And character_items.char_id=characters.objid And item_id = 40308 order by count desc limit 10");
		        break;
		    case 2: //추가부분입니다
		        pstm = con.prepareStatement("SELECT PKcount, char_name FROM characters WHERE AccessLevel = 0 And PKcount > 0 order by PKcount desc limit 10");
		        break;		
		    case 3:
		    	pstm = con.prepareStatement("SELECT count, characters.char_name FROM character_items, characters WHERE item_id in(select item_id from etcitem) And char_id in(select objid from characters where AccessLevel = 0) And character_items.char_id=characters.objid And item_id = 41159 order by count desc limit 10");
		        break;
		    case 4:
		    	pstm = con.prepareStatement("SELECT castle.castle_id, castle.name, clan_data.clan_name, clan_data.leader_name, clan_data.hascastle, castle.tax_rate, castle.public_money from clan_data, castle where castle.castle_id=clan_data.hascastle order by castle.public_money desc limit 10");
		    	break;
		    case 5: //추가부분입니다
		        pstm = con.prepareStatement("SELECT enchantlvl, weapon.name, characters.char_name  FROM character_items, weapon, characters WHERE character_items.item_id in(select item_id from weapon) And character_items.char_id in(select objid from characters where AccessLevel = 200) And character_items.item_id=weapon.item_id And character_items.char_id=characters.objid order by character_items.enchantlvl desc limit 10");
		        break;		
		    case 6: //추가부분입니다
		        pstm = con.prepareStatement("SELECT enchantlvl, armor.name, characters.char_name  FROM character_items, armor, characters WHERE character_items.item_id in(select item_id from armor) And character_items.char_id in(select objid from characters where AccessLevel = 200) And character_items.item_id=armor.item_id And character_items.char_id=characters.objid order by character_items.enchantlvl desc limit 10");
		        break;	

				default:
					pstm = con.prepareStatement("SELECT char_name FROM characters WHERE AccessLevel = 0 order by Exp desc limit 10");
					break;
			}

			rs = pstm.executeQuery();
				
		     if(number == 1) { //추가부분입니다
			    while(rs.next()){
				     aden[i] = rs.getInt(1);
				     name[i] = rs.getString(2);
				     i++;
		    } 
		    }else if(number == 2) { //추가부분입니다
			    while(rs.next()){
			    	pkcount[i] = rs.getInt(1);
			    	name[i] = rs.getString(2);
				      i++;
		    }
		    }else if(number == 3) { //추가부분입니다
			    while(rs.next()){
				     priaden[i] = rs.getInt(1);
				     name[i] = rs.getString(2);
				     i++;
		    } 
		    }else if(number == 4) { //추가부분입니다
			    while(rs.next()){
			    	castleid[i] = rs.getInt(1);
			    	castlename[i] = rs.getString(2);
			    	clanname[i] = rs.getString(3);
			    	leadername[i] = rs.getString(4);
			    	hascastle[i] = rs.getInt(5);
			    	taxrate[i] = rs.getInt(6);
			    	castleaden[i] = rs.getInt(7); 
				     i++;
		    } 
		    }else if(number == 5) { //추가부분입니다
				    while(rs.next()){
				     enchantlvl[i] = rs.getInt(1);
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
			} else {
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
  	return getBytes();
 	}

 	public String getType() {
  	return S_EnchantRanking;
 	}

}
