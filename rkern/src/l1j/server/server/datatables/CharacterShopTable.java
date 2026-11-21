package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastTable;

import l1j.server.Base64;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Getback;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionShop;
import l1j.server.server.model.shop.dynamic.DynamicShopItem;
import l1j.server.server.model.world.siege.CastleWarBusiness;

public class CharacterShopTable {

	private static final Logger _log = Logger.getLogger(CharacterShopTable.class.getName());
	private static CharacterShopTable _instance;

	public static CharacterShopTable getInstance() {
		if (_instance == null) {
			_instance = new CharacterShopTable();
		}
		return _instance;
	}

	private CharacterShopTable() {
		load();
	}

	private L1PcInstance load2(String charName, byte[] shopTitle) {
		L1PcInstance pc = null;
		try {
			pc = L1PcInstance.load(charName);
			if (pc == null) {
				return null;
			}
			
			int currentHpAtLoad = pc.getCurrentHp();
			int currentMpAtLoad = pc.getCurrentMp();

			pc.setOnlineStatus(1);
			CharacterTable.updateOnlineStatus(pc);
			L1World.getInstance().storeObject(pc);
			items(pc);
			
			L1Map map = L1WorldMap.getInstance().getMap(pc.getMapId());
			int tile = map.getTile(pc.getX(), pc.getY());
			if (Config.GET_BACK || !map.isInMap(pc.getX(), pc.getY())
					|| tile == 0 || tile == 4 || tile == 12) {
				int[] loc = Getback.GetBack_Location(pc, true);
				pc.setX(loc[0]);
				pc.setY(loc[1]);
				pc.setMap((short) loc[2]);
			}
			
			L1World.getInstance().addVisibleObject(pc);
			pc.beginGameTimeCarrier();
			pc.sendVisualEffectAtLogin();

			pc.getLight().turnOnOffLight();
			if (pc.getCurrentHp() > 0) {
				pc.setDead(false);
				pc.setStatus(0);
			} else {
				pc.setDead(true);
				pc.setStatus(ActionCodes.ACTION_Die);
			}
			
			CastleWarBusiness.getInstance().viewNowCastleWarState(pc);
			if (pc.getClanid() != 0) {
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null) {
					if (!(pc.getClanid() == clan.getClanId() &&
							pc.getClanname().toLowerCase().equals(clan.getClanName().toLowerCase()))) {
						pc.setClanid(0);
						pc.setClanname("");
						pc.setClanRank(0);
						pc.save();
					}
				}
			}
			
			if (currentHpAtLoad > pc.getCurrentHp()) {
				pc.setCurrentHp(currentHpAtLoad);
			}
			if (currentMpAtLoad > pc.getCurrentMp()) {
				pc.setCurrentMp(currentMpAtLoad);
			}
			pc.setNetConnection(null);
			pc.save();

			byte[] text = shopTitle;
			pc.setShopChat(text);
			pc.setPrivateShop(true);
			SC_DoActionShop.broadcast(pc, pc.getId(), ActionCodes.ACTION_Shop, text);

		} catch (Exception e) {
			_log.log(Level.SEVERE, "CharacterShopTable load2 error for char: " + charName, e);
		}
		return pc;
	}

	private void items(L1PcInstance pc) {
		CharacterTable.getInstance().restoreInventory(pc);
	}

	/** 2016.11.24 server 앱센터 시세 **/
	private void load() {
		FastTable<String> _charlist = new FastTable<String>();
		String sql = "SELECT * FROM character_shop_store";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			System.out.print("CharacterShopTable Loading...");
			int i = 0;
			
			while (rs.next()) {
				String name = rs.getString("char_Name");
				if (!_charlist.contains(name)) {
					String titleStr = rs.getString("shop_Title");
					byte[] decodedTitle = titleStr != null ? Base64.decode(titleStr) : new byte[0];
					
					L1PcInstance pc = load2(name, decodedTitle);
					_charlist.add(name);
					
					if (pc == null)
						continue;
					
					ShopListLoad(pc);
					i += 1;
				}
			}
			deleteShop();
			System.out.println("OK! Count: " + i);

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "CharacterShopTable load error", e);
		} finally {
			_charlist.clear();
		}
	}

	/** 2016.11.24 server 앱센터 시세 **/
	private void ShopListLoad(L1PcInstance pc) {
		String sql = "SELECT * FROM character_shop WHERE objid = ?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, pc.getId());
			
			try (ResultSet rs = pstm.executeQuery()) {
				while (rs.next()) {
					DynamicShopItem ditem = DynamicShopItem.create(rs);
					if (ditem != null) {
						if (ditem.isPurchase)
							pc.addPurchasings(ditem);
						else
							pc.addSellings(ditem);
					}
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "CharacterShopTable ShopListLoad error for pc: " + pc.getName(), e);
		}
	}

	public void deleteShop() {
		String sql = "DELETE FROM character_shop";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "CharacterShopTable deleteShop error", e);
		}
	}
}