/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.Controller;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Templates.L1AuctionBoard;
import l1j.server.Templates.L1House;
import l1j.server.configure.Config;
import l1j.server.server.datatables.AuctionBoardTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.storage.CharactersItemStorage;

public class AuctionTimeController implements Runnable {
	public static final int SLEEP_TIME = 60000;
	private static Logger _log = Logger.getLogger(AuctionTimeController.class.getName());

	private static AuctionTimeController _instance;

	public static AuctionTimeController getInstance() {
		if (_instance == null) {
			_instance = new AuctionTimeController();
		}
		return _instance;
	}
	@Override
	public void run() {
		try {
			checkAuctionDeadline();
		} catch (Exception e1) {
		}
	}

	public Calendar getRealTime() {
		TimeZone tz = TimeZone.getTimeZone(Config.TIME_ZONE);
		Calendar cal = Calendar.getInstance(tz);
		return cal;
	}

	private void checkAuctionDeadline() {
		AuctionBoardTable boardTable = new AuctionBoardTable();
		for (L1AuctionBoard board : boardTable.getAuctionBoardTableList()) {
			if (board.getDeadline().before(getRealTime())) {
				endAuction(board);
			}
		}
	}

	private void endAuction(L1AuctionBoard board) {
		int houseId = board.getHouseId();
		int price = board.getPrice();
		int oldOwnerId = board.getOldOwnerId();
		String bidder = board.getBidder();
		int bidderId = board.getBidderId();

		if (oldOwnerId != 0 && bidderId != 0) { // 이전의 소유자 있어·낙찰자 있어
			L1PcInstance oldOwnerPc = (L1PcInstance) L1World.getInstance().findObject(oldOwnerId);
			int payPrice = (int) (price * 0.9);
			if (oldOwnerPc != null) { // 이전의 소유자가 온라인중
				oldOwnerPc.getInventory().storeItem(ItemId.아데나, payPrice);
				oldOwnerPc.sendPackets(new S_ServerMessage(527, String.valueOf(payPrice)));
			} else { // 이전의 소유자가 오프 라인중
				ItemInstance item = ItemTable.getInstance().createItem(ItemId.아데나);
				item.setCount(payPrice);
				try {
					CharactersItemStorage storage = CharactersItemStorage.create();
					storage.storeItem(oldOwnerId, item);
				} catch (Exception e) {
					_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
			L1PcInstance bidderPc = (L1PcInstance) L1World.getInstance().findObject(bidderId);
			if (bidderPc != null) { // 낙찰자가 온라인중
				bidderPc.sendPackets(new S_ServerMessage(524, String.valueOf(price), bidder));
			}
			deleteHouseInfo(houseId);
			setHouseInfo(houseId, bidderId);
			deleteNote(houseId);
		} else if (oldOwnerId == 0 && bidderId != 0) { // 이전의 소유자 없음·낙찰자 있어
			L1PcInstance bidderPc = (L1PcInstance) L1World.getInstance().findObject(bidderId);
			if (bidderPc != null) { // 낙찰자가 온라인중
				bidderPc.sendPackets(new S_ServerMessage(524, String.valueOf(price), bidder));
			}
			setHouseInfo(houseId, bidderId);
			deleteNote(houseId);
		} else if (oldOwnerId != 0 && bidderId == 0) { // 이전의 소유자 있어·낙찰자 없음
			L1PcInstance oldOwnerPc = (L1PcInstance) L1World.getInstance().findObject(oldOwnerId);
			if (oldOwnerPc != null) { // 이전의 소유자가 온라인중
				oldOwnerPc.sendPackets(new S_ServerMessage(528));
			}
			deleteNote(houseId);
		} else if (oldOwnerId == 0 && bidderId == 0) { // 이전의 소유자 없음·낙찰자 없음
			// 마감을 5일 후로 설정해 재차 경매에 붙인다
			Calendar cal = getRealTime();
			cal.add(Calendar.HOUR, 0);
			cal.set(Calendar.MINUTE, 5);
			cal.set(Calendar.SECOND, 0);
			board.setDeadline(cal);
			AuctionBoardTable boardTable = new AuctionBoardTable();
			boardTable.updateAuctionBoard(board);
		}
	}

	/**
	 * 이전의 소유자의 아지트를 지운다
	 * 
	 * @param houseId
	 * 
	 * @return
	 */
	private void deleteHouseInfo(int houseId) {
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (clan.getHouseId() == houseId) {
				clan.setHouseId(0);
				ClanTable.getInstance().updateClan(clan);
			}
		}
	}

	/**
	 * 낙찰자의 아지트를 설정한다
	 * 
	 * @param houseId
	 *            bidderId
	 * 
	 * @return
	 */
	private void setHouseInfo(int houseId, int bidderId) {
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (clan.getLeaderId() == bidderId) {
				clan.setHouseId(houseId);
				ClanTable.getInstance().updateClan(clan);
				break;
			}
		}
	}

	/**
	 * 아지트의 경매 상태를 OFF로 설정해, 경매 게시판으로부터 지운다
	 * 
	 * @param houseId
	 * 
	 * @return
	 */
	private void deleteNote(int houseId) {
		// 아지트의 경매 상태를 OFF로 설정한다
		L1House house = HouseTable.getInstance().getHouseTable(houseId);
		house.setOnSale(false);
		Calendar cal = getRealTime();
		cal.add(Calendar.DATE, Config.HOUSE_TAX_INTERVAL);
		cal.set(Calendar.MINUTE, 0); // 분 , 초는 잘라서 버림
		cal.set(Calendar.SECOND, 0);
		house.setTaxDeadline(cal);
		HouseTable.getInstance().updateHouse(house);

		// 경매 게시판으로부터 지운다
		AuctionBoardTable boardTable = new AuctionBoardTable();
		boardTable.deleteAuctionBoard(houseId);
	}

}
