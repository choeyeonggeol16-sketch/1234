/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.   See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */

package l1j.server.server;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.clientpackets.C_AddBookmark;
import l1j.server.server.clientpackets.C_AddBuddy;
import l1j.server.server.clientpackets.C_Amount;
import l1j.server.server.clientpackets.C_Attack;
import l1j.server.server.clientpackets.C_Attr;
import l1j.server.server.clientpackets.C_AuthLogin;
import l1j.server.server.clientpackets.C_BanChatParty;
import l1j.server.server.clientpackets.C_BanClan;
import l1j.server.server.clientpackets.C_BanParty;
import l1j.server.server.clientpackets.C_Board;
import l1j.server.server.clientpackets.C_BoardBack;
import l1j.server.server.clientpackets.C_BoardDelete;
import l1j.server.server.clientpackets.C_BoardRead;
import l1j.server.server.clientpackets.C_BoardWrite;
import l1j.server.server.clientpackets.C_Buddy;
import l1j.server.server.clientpackets.C_CallPlayer;
import l1j.server.server.clientpackets.C_ChangeHeading;
import l1j.server.server.clientpackets.C_ChangeWarTime;
import l1j.server.server.clientpackets.C_CharcterConfig;
import l1j.server.server.clientpackets.C_Chat;
import l1j.server.server.clientpackets.C_ChatWhisper;
import l1j.server.server.clientpackets.C_CheckPK;
import l1j.server.server.clientpackets.C_CommonClick;
import l1j.server.server.clientpackets.C_CreateChar;
import l1j.server.server.clientpackets.C_CreateClan;
import l1j.server.server.clientpackets.C_CreateParty;
import l1j.server.server.clientpackets.C_DelBuddy;
import l1j.server.server.clientpackets.C_DeleteBookmark;
import l1j.server.server.clientpackets.C_DeleteChar;
import l1j.server.server.clientpackets.C_DeleteInventoryItem;
import l1j.server.server.clientpackets.C_Deposit;
import l1j.server.server.clientpackets.C_Door;
import l1j.server.server.clientpackets.C_Drawal;
import l1j.server.server.clientpackets.C_DropItem;
import l1j.server.server.clientpackets.C_Emblem;
import l1j.server.server.clientpackets.C_EnterPortal;
import l1j.server.server.clientpackets.C_Exclude;
import l1j.server.server.clientpackets.C_ExitGhost;
import l1j.server.server.clientpackets.C_ExtraCommand;
import l1j.server.server.clientpackets.C_FishClick;
import l1j.server.server.clientpackets.C_FixWeaponList;
import l1j.server.server.clientpackets.C_GiveItem;
import l1j.server.server.clientpackets.C_HireSoldier;
import l1j.server.server.clientpackets.C_InventoryWarehouse;
import l1j.server.server.clientpackets.C_ItemUSe;
import l1j.server.server.clientpackets.C_JoinClan;
import l1j.server.server.clientpackets.C_KeepALIVE;
import l1j.server.server.clientpackets.C_LeaveClan;
import l1j.server.server.clientpackets.C_LeaveParty;
import l1j.server.server.clientpackets.C_Login;
import l1j.server.server.clientpackets.C_LoginToServerOK;
import l1j.server.server.clientpackets.C_MailBox;
import l1j.server.server.clientpackets.C_MoveChar;
import l1j.server.server.clientpackets.C_NPCAction;
import l1j.server.server.clientpackets.C_NPCTalk;
import l1j.server.server.clientpackets.C_NewCharSelect;
import l1j.server.server.clientpackets.C_Party;
import l1j.server.server.clientpackets.C_PetMenu;
import l1j.server.server.clientpackets.C_PickUpItem;
import l1j.server.server.clientpackets.C_Pledge;
import l1j.server.server.clientpackets.C_Propose;
import l1j.server.server.clientpackets.C_Rank;
import l1j.server.server.clientpackets.C_Report;
import l1j.server.server.clientpackets.C_Restart;
import l1j.server.server.clientpackets.C_Result;
import l1j.server.server.clientpackets.C_ReturnStaus;
import l1j.server.server.clientpackets.C_ReturnToLogin;
import l1j.server.server.clientpackets.C_SelectList;
import l1j.server.server.clientpackets.C_SelectTarget;
import l1j.server.server.clientpackets.C_ServerVersion;
import l1j.server.server.clientpackets.C_Shop;
import l1j.server.server.clientpackets.C_ShopList;
import l1j.server.server.clientpackets.C_SkillBuy;
import l1j.server.server.clientpackets.C_SkillBuyOK;
import l1j.server.server.clientpackets.C_TaxRate;
import l1j.server.server.clientpackets.C_Teleport;
import l1j.server.server.clientpackets.C_Title;
import l1j.server.server.clientpackets.C_Trade;
import l1j.server.server.clientpackets.C_TradeAddItem;
import l1j.server.server.clientpackets.C_TradeCancel;
import l1j.server.server.clientpackets.C_TradeOK;
import l1j.server.server.clientpackets.C_UsePetItem;
import l1j.server.server.clientpackets.C_UseSkill;
import l1j.server.server.clientpackets.C_War;
import l1j.server.server.clientpackets.C_Who;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_CommonNews;
import l1j.server.server.utils.ByteArrayUtil;

public class PacketHandler {

	public PacketHandler(GameClient clientthread) {
		_client = clientthread;
	}

	public void handlePacket(byte abyte0[], L1PcInstance object) throws Exception {
		
		int i = abyte0[0] & 0xff;
	//	 System.out.println("옵코드 : "+i);
		if (i == Opcodes.C_OPCODE_EXCLUDE) {
			new C_Exclude(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CHARACTERCONFIG) {
			new C_CharcterConfig(abyte0, _client); 
		} else if (i == Opcodes.C_OPCODE_CLAN_STATUS) {
			new C_Rank(abyte0, _client); 
		} else if (i == Opcodes.C_OPCODE_DOOR) {
			new C_Door(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_TITLE) {
			new C_Title(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_BOARDDELETE) {
			new C_BoardDelete(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_PLEDGE) {
			new C_Pledge(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CHANGEHEADING) {
			new C_ChangeHeading(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_NPCACTION) {
			new C_NPCAction(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_USESKILL) {
			new C_UseSkill(abyte0, _client); 
		} else if (i == Opcodes.C_OPCODE_CLANMARKDOWNLOAD) {
			new C_Emblem(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_TRADEADDCANCEL) {
			new C_TradeCancel(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CHANGEWARTIME) {
			new C_ChangeWarTime(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_BOOKMARK) {
			new C_AddBookmark(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CREATECLAN) {
			new C_CreateClan(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CLIENTVERSION) {
			new C_ServerVersion(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_PROPOSE) {
			new C_Propose(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_SKILLBUY) {
			new C_SkillBuy(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_BOARDBACK) {
			new C_BoardBack(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_SHOP) {
			new C_Shop(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_BOARDREAD) {
			new C_BoardRead(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_TRADE) {
			new C_Trade(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_DELETECHAR) {
			new C_DeleteChar(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_KEEPALIVE) {
			new C_KeepALIVE(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_ATTR) {
			new C_Attr(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_LOGINPACKET) {
			new C_AuthLogin(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_RESULT) {
			new C_Result(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_DEPOSIT) {
			new C_Deposit(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_DRAWAL) {
			new C_Drawal(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_LOGINTOSERVEROK) {
			new C_LoginToServerOK(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_SKILLBUYOK) {
			new C_SkillBuyOK(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_TRADEADDITEM) {
			new C_TradeAddItem(abyte0, _client); 
		} else if (i == Opcodes.C_OPCODE_ADDBUDDY) {
			new C_AddBuddy(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_RETURNTOLOGIN) {
			new C_ReturnToLogin(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CHAT) {
			new C_Chat(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_TRADEADDOK) {
			new C_TradeOK(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CHECKPK) {
			new C_CheckPK(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_TAXRATE) {
			new C_TaxRate(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CHANGECHAR) {
			new C_NewCharSelect(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_BUDDYLIST) {
			new C_Buddy(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_DROPITEM) {
			new C_DropItem(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_LEAVEPARTY) {
			new C_LeaveParty(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_ATTACK || i == Opcodes.C_OPCODE_ARROWATTACK) {
			new C_Attack(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_QUITGAME) {
			// 캐릭터의 쇼트 컷이나 목록 상태가 플레이중에 변동했을 경우에
			// 쇼트 컷이나 목록 상태를 부가해 클라이언트로부터 송신되어 온다
			// 보내져 오는 타이밍은 클라이언트 종료시
		} else if (i == Opcodes.C_OPCODE_BANCLAN) {
			new C_BanClan(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_BOARD) {
			new C_Board(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_DELETEINVENTORYITEM) {
			new C_DeleteInventoryItem(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CHATWHISPER) {
			new C_ChatWhisper(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_PARTY) {
			new C_Party(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_PICKUPITEM) {
			new C_PickUpItem(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_WHO) {
			new C_Who(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_GIVEITEM) {
			new C_GiveItem(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_MOVECHAR) {
			new C_MoveChar(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_BOOKMARKDELETE) {
			new C_DeleteBookmark(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_RESTART) {
			new C_Restart(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_LEAVECLANE) {
			new C_LeaveClan(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_NPCTALK) {
			new C_NPCTalk(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_BANPARTY) {
			new C_BanParty(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_DELBUDDY) {
			new C_DelBuddy(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_WAR) {
			new C_War(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_LOGINTOSERVER) {
			new C_Login(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_PRIVATESHOPLIST) {
			new C_ShopList(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CHATGLOBAL) {
			new C_Chat(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_JOINCLAN) {
			new C_JoinClan(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_COMMONCLICK) {
			if (S_CommonNews.NoticeCount(_client.getAccountName()) > 0) {
				_client.sendPacket(new S_CommonNews(_client.getAccountName(), _client));
			} else {
				new C_CommonClick(_client);
				S_CommonNews sn = new S_CommonNews(null);
				sn.UpDate(_client.getAccountName(), "0");
			}
		} else if (i == Opcodes.C_OPCODE_NEWCHAR) {
			new C_CreateChar(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_EXTCOMMAND) {
			new C_ExtraCommand(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_BOARDWRITE) {
			new C_BoardWrite(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_USEITEM) {
			new C_ItemUSe(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CREATEPARTY) {
			new C_CreateParty(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_ENTERPORTAL) {
			new C_EnterPortal(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_AMOUNT) {
			new C_Amount(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_FIX_WEAPON_LIST) {
			new C_FixWeaponList(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_SELECTLIST) {
			new C_SelectList(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_EXIT_GHOST) {
			new C_ExitGhost(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_CALL) {
			new C_CallPlayer(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_HIRESOLDIER) {
			new C_HireSoldier(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_FISHCLICK) {
			new C_FishClick(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_BANCHATPARTY) {
			new C_BanChatParty(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_SELECTTARGET) {
			new C_SelectTarget(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_PETMENU) {
			new C_PetMenu(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_USEPETITEM) {
			new C_UsePetItem(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_REPORT) {
			new C_Report(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_LETTER) {
			new C_MailBox(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_BASERESET) { 
			new C_ReturnStaus(abyte0, _client);
		} else if (i == Opcodes.C_OPCODE_TELEPORT) { 
			new C_Teleport(abyte0, _client);
		} else if (i == 98) { 
			new C_InventoryWarehouse(abyte0, _client);

		} else {
//			String s = Integer.toHexString(abyte0[0] & 0xff);
//			System.out.println(String.format("용도 불명 옵코드 : %d (%s)", i, s));
//			System.out.println(new ByteArrayUtil(abyte0).dumpToString());
		}
//		 _log.warning((new StringBuilder()).append("작동코드").append(i).toString());
	}



	private final GameClient _client;
}