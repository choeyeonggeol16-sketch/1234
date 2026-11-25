package l1j.server.server.command.executor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.Warehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.SQLUtil;

public class L1ItemDelete implements L1CommandExecutor  {

	private static final Logger _log = Logger.getLogger(L1ItemDelete.class.getName());

	private L1ItemDelete() {	}

	public static L1CommandExecutor getInstance() {
		return new L1ItemDelete();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			String type = tok.nextToken();				
			WarehouseManager wareHouse = WarehouseManager.getInstance();
			if(type.equalsIgnoreCase("전체")) {				
				try {	
					int itemId = Integer.parseInt(tok.nextToken());
					ArrayList<String> accounts = loadAccount();
					String name;
					for(int i = 0; i < accounts.size(); i++) {
						name = accounts.get(i);	
						deleteWareHouse(wareHouse, name, itemId);//창고 삭제
					}

					for(L1PcInstance players : L1World.getInstance().getAllPlayers()) {
						players.getInventory().DeleteItemInInventory(itemId);
						SC_SystemMessage.send(pc, "전체유저의 템번호 ["+itemId+"]를 삭제하였습니다.");
						
					}
					Warehouse _clanWarehouse;
					L1ItemInstance clanItem;
					for(L1Clan clans : L1World.getInstance().getAllClans()) {
						_clanWarehouse = wareHouse.getClanItems(clans.getClanName());
						clanItem = _clanWarehouse.GetFirstItemByClassID(itemId);
						if(clanItem != null) {
							_clanWarehouse.deleteItem(clanItem);
						}
					}
				}catch(Exception e) {
					SC_SystemMessage.send(pc, cmdName + " [전체] [아이템 번호] 으로 입력해 주세요. ");
				}
			}else if(type.equalsIgnoreCase("케릭")){
				try {
					String name = tok.nextToken();					
					int itemId = Integer.parseInt(tok.nextToken());
					
					L1PcInstance target = L1World.getInstance().getPlayer(name);
					if(target == null) {
						SC_SystemMessage.send(pc, target + "님은 현재 접속중이 아닙니다.");
						return;
					}
					String accountName = target.getAccountName();					
					deleteWareHouse(wareHouse, accountName, itemId);
					
					loadAccountCharacter(accountName, itemId);
					
					L1PcInstance player = L1World.getInstance().getPlayer(name);
					if(player != null) {
						player.getInventory().DeleteItemInInventory(itemId);
						SC_SystemMessage.send(pc, player.getName()+"님의 템번호 ["+itemId+"]를 삭제하였습니다.");
						
					}
				}catch(Exception e) {
					SC_SystemMessage.send(pc, cmdName + " [케릭] [케릭터명] [아이템 번호] 으로 입력해 주세요");
				}
			}
		}catch(Exception e) {
			SC_SystemMessage.send(pc, "\\aD" + cmdName + " [전체] [아이템 번호] 으로 입력해 주세요. ");
			SC_SystemMessage.send(pc, "\\aD" + cmdName + "[케릭]  [케릭터명] [아이템 번호] 으로 입력해 주세요");
		}
	}

	private void deleteWareHouse(WarehouseManager wareHouse, String accountName, int itemId) {
		//계정창고 아이템 삭제..
		Warehouse _privateWarehouse = null;
		Warehouse _elfWarehouse = null;				
		Warehouse _supplementaryService = null;

		L1ItemInstance itemPrivate;
		L1ItemInstance itemElf;
		L1ItemInstance itemSupple;				

		_privateWarehouse = wareHouse.getPrivateItems(accountName);
		_elfWarehouse =  wareHouse.getElfItems(accountName);
		_supplementaryService =  wareHouse.getSupplementaryItems(accountName);					
		itemPrivate = _privateWarehouse.GetFirstItemByClassID(itemId);
		itemElf = _elfWarehouse.GetFirstItemByClassID(itemId);
		itemSupple = _supplementaryService.GetFirstItemByClassID(itemId);	
		if(itemPrivate != null) {
			_privateWarehouse.deleteItem(itemPrivate);
		}
		if(itemElf != null) {
			_elfWarehouse.deleteItem(itemElf);
		}
		if(itemSupple != null) {
			_supplementaryService.deleteItem(itemSupple);
		}
	}
	
	private ArrayList<String> loadAccountCharacter(String accountName, int itemId) {
		ArrayList<String> _list = new ArrayList<String>();
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM characters WHERE account_name = ? AND OnlineStatus = 0");
			pstm.setString(1, accountName);
			rs = pstm.executeQuery();
			while(rs.next()) {
				deleteCharacterDB(rs.getInt("objid"), itemId );
			}
		}catch(SQLException e) {

		}finally {
			SQLUtil.close(rs, pstm, con);
		}
		return _list;
	}
	
	private void deleteCharacterDB(int char_id, int itemId) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE  FROM character_items WHERE char_id = ? AND item_id = ? ");
			pstm.setInt(1, char_id);
			pstm.setInt(2, itemId);
			pstm.execute();
		}catch(SQLException e) {
			e.printStackTrace();
		}finally {
			SQLUtil.close(pstm, con);
		}
	}
	
	private ArrayList<String> loadAccount() {
		ArrayList<String> _list = new ArrayList<String>();
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM accounts");
			rs = pstm.executeQuery();
			while(rs.next()) {
				_list.add(rs.getString("login"));
			}
		}catch(SQLException e) {

		}finally {
			SQLUtil.close(rs, pstm, con);
		}
		return _list;
	}
}
