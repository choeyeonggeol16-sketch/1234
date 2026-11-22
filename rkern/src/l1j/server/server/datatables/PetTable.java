package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.L1Pet;

public class PetTable {

	private static final Logger _log = Logger.getLogger(PetTable.class.getName());
	private static volatile PetTable _instance;

	private final TIntObjectMap<L1Pet> _pets = new TIntObjectHashMap<L1Pet>(128);

	public static synchronized PetTable getInstance() {
		if (_instance == null) {
			_instance = new PetTable();
		}
		return _instance;
	}

	public static synchronized void reload() {
		PetTable oldInstance = _instance;
		_instance = new PetTable();
		if (oldInstance != null) {
            synchronized (oldInstance._pets) { oldInstance._pets.clear(); }
		}
	}

	private PetTable() {
		load();
	}

	private void load() {
		String sql = "SELECT * FROM pets";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				L1Pet pet = new L1Pet();
				int itemobjid = rs.getInt(1);
				pet.set_itemobjid(itemobjid);
				pet.set_objid(rs.getInt(2));
				pet.set_npcid(rs.getInt(3));
				pet.set_name(rs.getString(4));
				pet.set_level(rs.getInt(5));
				pet.set_hp(rs.getInt(6));
				pet.set_mp(rs.getInt(7));
				pet.set_exp(rs.getInt(8));
				pet.set_lawful(rs.getInt(9));

				_pets.put(itemobjid, pet);
			}
			_log.config("Pet 데이터 " + _pets.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "PetTable load error", e);
		}
	}

    public static L1Pet newPetRecord(L1NpcInstance pet, int objid, int itemobjid) {
        L1Pet record = new L1Pet();
        record.set_itemobjid(itemobjid);
        record.set_objid(objid);
        record.set_npcid(pet.getNpcTemplate().get_npcId());
        record.set_name(pet.getNpcTemplate().get_nameid());
        record.set_level(pet.getNpcTemplate().get_level());
        record.set_hp(pet.getMaxHp());
        record.set_mp(pet.getMaxMp());
        record.set_exp(750);
        record.set_lawful(0);
        return record;
    }

    public static void insertPet(Connection con, L1Pet record) throws SQLException {
        try (PreparedStatement insert = con.prepareStatement(
                "INSERT INTO pets(item_obj_id,objid,npcid,name,lvl,hp,mp,exp,lawful) VALUES(?,?,?,?,?,?,?,?,?)")) {
            insert.setInt(1, record.get_itemobjid());
            insert.setInt(2, record.get_objid());
            insert.setInt(3, record.get_npcid());
            insert.setString(4, record.get_name());
            insert.setInt(5, record.get_level());
            insert.setInt(6, record.get_hp());
            insert.setInt(7, record.get_mp());
            insert.setInt(8, record.get_exp());
            insert.setInt(9, record.get_lawful());
            if (insert.executeUpdate() != 1) throw new SQLException("Pet insert failed: " + record.get_itemobjid());
        }
    }

    public void storeNewPet(L1NpcInstance petInstance, int objid, int itemobjid) {
        L1Pet record = newPetRecord(petInstance, objid, itemobjid);
        registerCommittedPet(record);
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            insertPet(con, record);
        } catch (SQLException e) {
            _log.log(Level.SEVERE, "PetTable storeNewPet error", e);
        }
    }

	public void storePet(L1Pet pet) {
		String sql = "UPDATE pets SET objid=?, npcid=?, name=?, lvl=?, hp=?, mp=?, exp=?, lawful=? WHERE item_obj_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, pet.get_objid());
			pstm.setInt(2, pet.get_npcid());
			pstm.setString(3, pet.get_name());
			pstm.setInt(4, pet.get_level());
			pstm.setInt(5, pet.get_hp());
			pstm.setInt(6, pet.get_mp());
			pstm.setInt(7, pet.get_exp());
			pstm.setInt(8, pet.get_lawful());
			pstm.setInt(9, pet.get_itemobjid());
			pstm.execute();
			
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "PetTable storePet error", e);
		}
	}

    /** SQL part of the pet+collar durable receipt. */
    public static void deletePetRow(Connection con, int itemobjid) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM pets WHERE item_obj_id=?")) {
            ps.setInt(1, itemobjid);
            ps.executeUpdate();
        }
    }

    /** Only after receipt-confirmed SQL COMMIT. */
    public void forgetCommittedPet(int itemobjid) {
        synchronized (_pets) { _pets.remove(itemobjid); }
    }

	public void deletePet(int itemobjid) {
		String sql = "DELETE FROM pets WHERE item_obj_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, itemobjid);
			pstm.execute();
			
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "PetTable deletePet error", e);
		}
		synchronized (_pets) { _pets.remove(itemobjid); }
	}

	/**
	 * Pets 테이블에 이미 이름이 존재할까를 돌려준다.
	 * @param nameCaseInsensitive 조사하는 애완동물의 이름. 대문자 소문자의 차이는 무시된다.
	 * @return 이미 이름이 존재하면 true
	 */
	public static boolean isNameExists(String nameCaseInsensitive) {
		String nameLower = nameCaseInsensitive.toLowerCase();
		String sql = "SELECT item_obj_id FROM pets WHERE LOWER(name)=?";
		
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setString(1, nameLower);
			try (ResultSet rs = pstm.executeQuery()) {
				if (!rs.next()) {
					return false;
				} else if (PetTypeTable.getInstance().isNameDefault(nameLower)) {
					return false;
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "PetTable isNameExists error", e);
		}
		return true;
	}

    public static synchronized void publishPurchasedPet(L1Pet pet) {
        getInstance().registerCommittedPet(pet);
    }

    /** Publish only a pet record already durable with its amulet receipt. */
    public void registerCommittedPet(L1Pet pet) {
        if (pet == null) throw new IllegalArgumentException("pet");
        synchronized (_pets) {
            _pets.put(pet.get_itemobjid(), pet);
        }
    }

    public L1Pet getTemplate(int itemobjid) {
        synchronized (_pets) { return _pets.get(itemobjid); }
    }

	public L1Pet[] getPetTableList() {
		synchronized (_pets) { return _pets.values(new L1Pet[_pets.size()]); }
	}
}