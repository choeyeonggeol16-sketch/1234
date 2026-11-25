package l1j.server.server.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;

public class Beginner {

	private static final Logger _log = Logger.getLogger(Beginner.class.getName());

	private static volatile Beginner _instance;

	public static Beginner getInstance() {
		if (_instance == null) {
			synchronized (Beginner.class) {
				if (_instance == null) {
					_instance = new Beginner();
				}
			}
		}
		return _instance;
	}

	private Beginner() {}
	
	public static void reload() {
		_instance = new Beginner();
	}

	/**
	 * 접속 중인 캐릭터에게 초보자 아이템 지급 (인벤토리 즉시 지급)
	 */
	public int GiveItemToActivePc(L1PcInstance pc) {
		if (pc == null) return 0;

		String sql = "SELECT * FROM beginner WHERE activate IN (?, ?)";
		String jobCode = getJobActivationCode(pc);

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setString(1, "A");
			pstm.setString(2, jobCode);

			try (ResultSet rs = pstm.executeQuery()) {
				while (rs.next()) {
					int itemId = rs.getInt("item_id");
					int count = rs.getInt("count");
					int enchant = rs.getInt("enchantlvl");

					if (count <= 0) continue;

					L1Item temp = ItemTable.getInstance().getTemplate(itemId);
					if (temp == null) continue;

					if (temp.isStackable()) {
						// 겹쳐지는 아이템 (물약, 아데나 등) 처리
						L1ItemInstance item = ItemTable.getInstance().createItem(itemId);
						if (item != null) {
							item.setCount(count);
							item.setEnchantLevel(enchant);
							if (pc.getInventory().checkAddItem(item, count) == CInventory.OK) {
								pc.getInventory().storeItem(item);
								SC_ServerMessage.send(pc, 403, item.getLogName() + "(ID:" + itemId + ")");
							}
						}
					} else {
						// 안 겹쳐지는 장비 아이템 처리
						for (int i = 0; i < count; i++) {
							L1ItemInstance item = ItemTable.getInstance().createItem(itemId);
							if (item != null) {
								item.setEnchantLevel(enchant);
								if (pc.getInventory().checkAddItem(item, 1) == CInventory.OK) {
									pc.getInventory().storeItem(item);
									SC_ServerMessage.send(pc, 403, item.getLogName() + "(ID:" + itemId + ")");
								} else {
									break;
								}
							}
						}
					}
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "GiveItemToActivePc error", e);
		}
		return 0;
	}

	/**
	 * 캐릭터 북마크(기억 장소) 자동 등록
	 */
	public void writeBookmark(L1PcInstance pc) {
		if (pc == null) return;

		String selectSql = "SELECT * FROM beginner_teleport";
		String insertSql = "INSERT INTO character_teleport SET id = ?, char_id = ?, name = ?, locx = ?, locy = ?, mapid = ?, speed = ?, num = ?";

		try (Connection c = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement p = c.prepareStatement(selectSql);
			 PreparedStatement p1 = c.prepareStatement(insertSql);
			 ResultSet r = p.executeQuery()) {

			while (r.next()) {
				p1.setInt(1, IdFactory.getInstance().nextId());
				p1.setInt(2, pc.getId());
				p1.setString(3, r.getString("name"));
				p1.setInt(4, r.getInt("locx")); 
				p1.setInt(5, r.getInt("locy"));
				p1.setShort(6, r.getShort("mapid"));
				p1.setInt(7, -1);
				p1.setInt(8, 0);
				p1.execute();
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "북마크의 추가로 에러가 발생했습니다.", e);
		}
	}

    /** Same connection as character creation; a failure rolls back the entire character. */
    public void writeBookmark(Connection c, L1PcInstance pc) throws SQLException {
		if (pc == null) return;

		String selectSql = "SELECT * FROM beginner_teleport";
		String insertSql = "INSERT INTO character_teleport SET id = ?, char_id = ?, name = ?, locx = ?, locy = ?, mapid = ?, speed = ?, num = ?";

		try (PreparedStatement p = c.prepareStatement(selectSql);
			 PreparedStatement p1 = c.prepareStatement(insertSql);
			 ResultSet r = p.executeQuery()) {

			while (r.next()) {
				p1.setInt(1, IdFactory.getInstance().nextId());
				p1.setInt(2, pc.getId());
				p1.setString(3, r.getString("name"));
				p1.setInt(4, r.getInt("locx")); 
				p1.setInt(5, r.getInt("locy"));
				p1.setShort(6, r.getShort("mapid"));
				p1.setInt(7, -1);
				p1.setInt(8, 0);
				p1.execute();
			}
		} catch (SQLException e) {
            throw e;
		}
	}
	
	/**
	 * DB 캐릭터 인벤토리(character_items)에 직접 초보자 아이템 적재 (오프라인 지급용 등)
	 */
	public int GiveItem(L1PcInstance pc) {
		if (pc == null) return 0;

		String selectSql = "SELECT * FROM beginner WHERE activate IN (?, ?)";
		String insertSql = "INSERT INTO character_items SET id=?, item_id=?, char_id=?, item_name=?, count=?, is_equipped=?, enchantlvl=?, is_id=?, durability=?, charge_count=?, remaining_time=?, last_used=?, bless=?, attr_enchantlvl=?, special_enchant = 0";
		String jobCode = getJobActivationCode(pc);

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm1 = con.prepareStatement(selectSql);
			 PreparedStatement pstm2 = con.prepareStatement(insertSql)) {

			pstm1.setString(1, "A");
			pstm1.setString(2, jobCode);

			try (ResultSet rs = pstm1.executeQuery()) {
				while (rs.next()) {
					int count = rs.getInt("count");
					int equipitem = rs.getInt("item_id");
					if (count <= 0) continue;

					pstm2.setInt(1, IdFactory.getInstance().nextId());
					pstm2.setInt(2, equipitem);
					pstm2.setInt(3, pc.getId());
					pstm2.setString(4, rs.getString("item_name"));
					pstm2.setInt(5, count);
					
					// 수련자 장비류 자동 착용 판정
					if ((equipitem >= 22300 && equipitem <= 22311) || 
						equipitem == 48 || equipitem == 120 || equipitem == 147 || 
						equipitem == 156 || equipitem == 175) {
						pstm2.setInt(6, 1);
					} else {
						pstm2.setInt(6, 0);
					}
					
					pstm2.setInt(7, rs.getInt("enchantlvl"));
					pstm2.setInt(8, 1);
					pstm2.setInt(9, 0);
					pstm2.setInt(10, rs.getInt("charge_count"));
					pstm2.setInt(11, 0);
					pstm2.setTimestamp(12, null);
					pstm2.setInt(13, rs.getInt("bless"));
					pstm2.setInt(14, 0);
					pstm2.addBatch(); // PreparedStatement 배치 처리 적용으로 DB 성능 극대화
				}
				pstm2.executeBatch(); // 일괄 실행
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "GiveItem error", e);
		}
		return 0;
	}

    /** Same connection as character creation; a failure rolls back the entire character. */
    public int GiveItem(Connection con, L1PcInstance pc) throws SQLException {
		if (pc == null) return 0;

		String selectSql = "SELECT * FROM beginner WHERE activate IN (?, ?)";
		String insertSql = "INSERT INTO character_items SET id=?, item_id=?, char_id=?, item_name=?, count=?, is_equipped=?, enchantlvl=?, is_id=?, durability=?, charge_count=?, remaining_time=?, last_used=?, bless=?, attr_enchantlvl=?, special_enchant = 0";
		String jobCode = getJobActivationCode(pc);

		try (PreparedStatement pstm1 = con.prepareStatement(selectSql);
			 PreparedStatement pstm2 = con.prepareStatement(insertSql)) {

			pstm1.setString(1, "A");
			pstm1.setString(2, jobCode);

			try (ResultSet rs = pstm1.executeQuery()) {
				while (rs.next()) {
					int count = rs.getInt("count");
					int equipitem = rs.getInt("item_id");
					if (count <= 0) continue;

					pstm2.setInt(1, IdFactory.getInstance().nextId());
					pstm2.setInt(2, equipitem);
					pstm2.setInt(3, pc.getId());
					pstm2.setString(4, rs.getString("item_name"));
					pstm2.setInt(5, count);
					
					// 수련자 장비류 자동 착용 판정
					if ((equipitem >= 22300 && equipitem <= 22311) || 
						equipitem == 48 || equipitem == 120 || equipitem == 147 || 
						equipitem == 156 || equipitem == 175) {
						pstm2.setInt(6, 1);
					} else {
						pstm2.setInt(6, 0);
					}
					
					pstm2.setInt(7, rs.getInt("enchantlvl"));
					pstm2.setInt(8, 1);
					pstm2.setInt(9, 0);
					pstm2.setInt(10, rs.getInt("charge_count"));
					pstm2.setInt(11, 0);
					pstm2.setTimestamp(12, null);
					pstm2.setInt(13, rs.getInt("bless"));
					pstm2.setInt(14, 0);
					pstm2.addBatch(); // PreparedStatement 배치 처리 적용으로 DB 성능 극대화
				}
				pstm2.executeBatch(); // 일괄 실행
			}
		} catch (SQLException e) {
			throw e;
		}
		return 0;
	}

	/**
	 * 캐릭터 클래스별 활성화 코드 추출 헬퍼 메서드
	 */
	private String getJobActivationCode(L1PcInstance pc) {
		if (pc.isCrown()) return "P";
		if (pc.isKnight()) return "K";
		if (pc.isElf()) return "E";
		if (pc.isWizard()) return "W";
		if (pc.isDarkelf()) return "D";
		if (pc.isDragonknight()) return "T";
		if (pc.isBlackwizard()) return "B";
		if (pc.isWarrior()) return "J";
		return "A";
	}
}