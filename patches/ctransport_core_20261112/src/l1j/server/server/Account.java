package l1j.server.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;
import l1j.server.MJTemplate.MJObjectWrapper;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.tam.L1Tam;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;
import l1j.server.server.packets.Protocol.Packet.SC_RestExpInfo;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.utils.IntRange;

public class Account {
	/** 계정오브젝트아이디 **/
	private int _id;
	/** 계정명 */
	private String _name;
	/** 접속자 IP주소 */
	private String _ip;
	/** 패스워드(암호화 됨) */
	private String _password;
	/** 최근 접속일 */
	private Timestamp _lastActive;
	/** 최근 접속종료일 */
	public Timestamp _lastlogout;

	/** 엑세스 등급(GM인가?) */
	private int _accessLevel;
	/** 접속자 호스트명 */
	private String _host;
	/** 밴 유무(True == 금지) */
	private int _banned;
	/** 계정 유효 유무(True == 유효) */
	private boolean _isValid = false;
	/** 캐릭터 슬롯(태고의옥쇄) */
	private int _charslot;
	private boolean _is_changed_slot = false;
	/** 창고 비밀번호 */
	private int _GamePassword;
	/** 케릭비번 */
	private String _CharPassword;
	private byte[] _waitpacket = null;

	public int Ncoin_point;
	public int Shop_open_count;

	/** Buff_PC방 */
	public Timestamp _Buff_PC방;
	public volatile int tam_point;
	public Timestamp _lastQuit;

	private int _tam = 0;
	private int _tamStep = 0;

    private volatile int _blessOfAin;
	private int _grangKinAngerTime;
	
	/** 변카 */
	public int info_kir_poly;
	public int info_class_poly;
	public int info_ds_poly;
	public int info_dru_poly;
	public int info_ga_poly;
	public int info_ger_poly;
	public int info_dev_poly;
	public int info_pen_poly;
	public int info_atun_poly;
	public int info_moon_poly;
	public int info_mar_poly;
	public int info_dsn_poly;
	public int info_bapo_poly;
	public int info_ki_poly;
	public int info_el_poly;
	public int info_wi_poly;
	public int info_da_poly;
	public int info_wa_poly;
	public int info_il_poly;
	public int info_drag_poly;
	// 영웅
	public int info_heaven_poly;
	public int info_val_poly;
	public int info_larry_poly;
	public int info_gunter_poly;
	public int info_depor_poly;
	public int info_deathred_poly;
	public int info_ecil_poly;
	public int info_assm_poly;
	public int info_kerep_poly;
	public int info_jowo_poly;
	public int info_unik_poly;
	public int info_unim_poly;
	public int info_unir_poly;
	public int info_yozil_poly;
	public int info_owen_poly;
	public int info_helv_poly;
	// 합성
	public int info_card_a;
	public int info_card_b;
	public int info_card_c;
	public int info_card_d;
	
	// 컬렉션
	public int info_col_y;
	public int info_col_m;
	public int info_col_l;
	public int info_col_h;
	// 컬렉션 메세지
	public int info_colme_y;
	public int info_colme_m;
	public int info_colme_l;
	public int info_colme_h;
	// 수호성
	public int tree_rain;
	public int tree_iceq;
	public int tree_fireq;
	public int tree_ices;
	public int tree_final;
	public int tree_cri;
	public int tree_select;
	public int tree_select2;
	public int tree_select3;
	public int tree_crim1;
	public int tree_crim2;
	public int tree_ecil1;
	public int tree_ecil2;
	public int tree_ecil3;
	// 추가 영웅 변신
	public int hero01;
	public int hero02;
	public int hero03;
	public int hero04;
	public int hero05;
	public int hero06;
	public int hero07;
	public int hero08;
	public int hero09;
	public int hero10;
	public int hero11;
	public int hero12;
	public int hero13;
	public int hero14;
	public int hero15;
	public int hero16;
	public int hero17;
	public int hero18;
	public int hero19;
	public int hero20;
	public int hero21;
	// 추가 전설 변신
	public int legend01;
	public int legend02;
	public int legend03;
	public int legend04;
	public int legend05;
	public int legend06;
	public int legend07;
	public int legend08;
	public int legend09;
	public int legend10;
	public int legend11;
	public int legend12;
	public int legend13;
	public int legend14;
	public int legend15;
	public int legend16;
	public int legend17;
	public int legend18;
	public int legend19;
	public int legend20;
	public int legend21;
	public int legend22;
	public int legend23;
	public int legend24;
	public int legend25;
	// 추가 신화 변신
	public int myth01;
	public int myth02;
	// 추가 수호성
	public int tree_select4;
	public int tree_atun1;
	public int tree_atun2;
	public int tree_atun3;
	
	private String _phone;
	private long _btnTimeHome;
	private long _btnTimePc;
	private Timestamp _raidBuff;
	
	/** 메세지 로그용 */
	private static final Logger _log = Logger.getLogger(Account.class.getName());

	public Account() {
	}


	public static String checkIP(String name) {
		String n = null;
		String sql = "SELECT ip FROM accounts WHERE login=? LIMIT 1";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, name);
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					n = rs.getString("ip");
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "checkIP error", e);
		}
		return n;
	}

	public static Account create(final String name, final String rawPassword, final String ip, final String host) {
        if (!CacheD.getInstance().flushAccount(name, 30000L)) {
            throw new IllegalStateException("CacheD account flush timed out before create: " + name);
        }
		Calendar cal = Calendar.getInstance();
		String 오전오후 = (cal.get(Calendar.AM_PM) == 0) ? "오전" : "오후";
		
		String sql = "INSERT INTO accounts SET login=?, password=?, lastactive=?, access_level=?, ip=?, host=?, banned=?, charslot=?, gamepassword=?, last_log_out=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			Account account = new Account();
			account._name = name;
			account._password = rawPassword;
			account._ip = ip;
			account._host = host;
			account._banned = 0;
			account._lastActive = new Timestamp(System.currentTimeMillis());
			account._lastlogout = new Timestamp(System.currentTimeMillis());

			pstm.setString(1, account._name);
			pstm.setString(2, account._password);
			pstm.setTimestamp(3, account._lastActive);
			pstm.setInt(4, 0);
			pstm.setString(5, account._ip);
			pstm.setString(6, account._host);
			pstm.setInt(7, account._banned);
			pstm.setInt(8, Config.Characters_CharSlot);
			pstm.setInt(9, 0);
			pstm.setTimestamp(10, account._lastlogout);
			if (pstm.executeUpdate() != 1) {
                throw new SQLException("Account creation affected no row: " + name);
            }
			
			System.out.println("계정생성:【" + name + "】 / IP:【" + ip + "】 / 시간:【" + 오전오후 + " " + cal.get(Calendar.HOUR) + "시" + cal.get(Calendar.MINUTE) + "분】");
			return account;
		} catch (Exception e) {
            // A rejected or uncertain INSERT must not fall through to a
            // second account lookup as if registration succeeded.
            throw new IllegalStateException("Account create failed: " + name, e);
        }
	}

	public static Account load(final String name) {
        if (!CacheD.getInstance().flushAccount(name, 30000L)) {
            throw new IllegalStateException("CacheD account flush timed out before load: " + name);
        }
		Account account = null;
		String sql = "SELECT * FROM accounts WHERE login=? LIMIT 1";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setString(1, name);
			try (ResultSet rs = pstm.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				account = new Account();
				account._id = rs.getInt("id");
				account._name = rs.getString("login");
				account._password = rs.getString("password");
				account._lastActive = rs.getTimestamp("lastactive");
				account._accessLevel = rs.getInt("access_level");
				account._host = rs.getString("host");
				account._ip = rs.getString("ip");
				account._banned = rs.getInt("banned");
				account._charslot = rs.getInt("charslot");
				account._GamePassword = rs.getInt("gamepassword");
				account._phone = rs.getString("phone");
				account.tam_point = rs.getInt("Tam_Point");
				account._Buff_PC방 = rs.getTimestamp("BUFF_PCROOM_Time");
				account._CharPassword = rs.getString("CharPassword");
				account.Ncoin_point = rs.getInt("Ncoin_Point");
				account.Shop_open_count = rs.getInt("Shop_open_count");
				account.setDragonRaid(rs.getTimestamp("raid_buff"));
				account._blessOfAin = rs.getInt("bless_of_ain");
				account._grangKinAngerTime = rs.getInt("GrangKinAngerTime");
				account._lastlogout = rs.getTimestamp("last_log_out");

				account.info_kir_poly = rs.getInt("info_kir_poly");
				account.info_class_poly = rs.getInt("info_class_poly");
				account.info_ds_poly = rs.getInt("info_ds_poly");
				account.info_dru_poly = rs.getInt("info_dru_poly");
				account.info_ga_poly = rs.getInt("info_ga_poly");
				account.info_ger_poly = rs.getInt("info_ger_poly");
				account.info_dev_poly = rs.getInt("info_dev_poly");
				account.info_pen_poly = rs.getInt("info_pen_poly");
				account.info_atun_poly = rs.getInt("info_atun_poly");
				account.info_moon_poly = rs.getInt("info_moon_poly");
				account.info_mar_poly = rs.getInt("info_mar_poly");
				account.info_dsn_poly = rs.getInt("info_dsn_poly");
				account.info_bapo_poly = rs.getInt("info_bapo_poly");
				account.info_ki_poly = rs.getInt("info_ki_poly");
				account.info_el_poly = rs.getInt("info_el_poly");
				account.info_wi_poly = rs.getInt("info_wi_poly");
				account.info_da_poly = rs.getInt("info_da_poly");
				account.info_wa_poly = rs.getInt("info_wa_poly");
				account.info_il_poly = rs.getInt("info_il_poly");
				account.info_drag_poly = rs.getInt("info_drag_poly");
				
				account.info_heaven_poly = rs.getInt("info_heaven_poly");
				account.info_val_poly = rs.getInt("info_val_poly");
				account.info_larry_poly = rs.getInt("info_larry_poly");
				account.info_gunter_poly = rs.getInt("info_gunter_poly");
				account.info_depor_poly = rs.getInt("info_depor_poly");
				account.info_deathred_poly = rs.getInt("info_deathred_poly");
				account.info_ecil_poly = rs.getInt("info_ecil_poly");
				account.info_assm_poly = rs.getInt("info_assm_poly");
				account.info_kerep_poly = rs.getInt("info_kerep_poly");
				account.info_jowo_poly = rs.getInt("info_jowo_poly");
				account.info_unik_poly = rs.getInt("info_unik_poly");
				account.info_unim_poly = rs.getInt("info_unim_poly");
				account.info_unir_poly = rs.getInt("info_unir_poly");
				account.info_yozil_poly = rs.getInt("info_yozil_poly");
				account.info_owen_poly = rs.getInt("info_owen_poly");
				account.info_helv_poly = rs.getInt("info_helv_poly");
				
				account.info_card_a = rs.getInt("info_card_a");
				account.info_card_b = rs.getInt("info_card_b");
				account.info_card_c = rs.getInt("info_card_c");
				account.info_card_d = rs.getInt("info_card_d");

				account.info_col_y = rs.getInt("info_col_y");
				account.info_col_m = rs.getInt("info_col_m");
				account.info_col_l = rs.getInt("info_col_l");
				account.info_col_h = rs.getInt("info_col_h");
				account.info_colme_y = rs.getInt("info_colme_y");
				account.info_colme_m = rs.getInt("info_colme_m");
				account.info_colme_l = rs.getInt("info_colme_l");
				account.info_colme_h = rs.getInt("info_colme_h");

				account.tree_rain = rs.getInt("tree_rain");
				account.tree_iceq = rs.getInt("tree_iceq");
				account.tree_fireq = rs.getInt("tree_fireq");
				account.tree_ices = rs.getInt("tree_ices");
				account.tree_final = rs.getInt("tree_final");
				account.tree_cri = rs.getInt("tree_cri");
				account.tree_select = rs.getInt("tree_select");
				account.tree_select2 = rs.getInt("tree_select2");
				account.tree_select3 = rs.getInt("tree_select3");
				account.tree_select4 = rs.getInt("tree_select4");
				account.tree_crim1 = rs.getInt("tree_crim1");
				account.tree_crim2 = rs.getInt("tree_crim2");
				account.tree_ecil1 = rs.getInt("tree_ecil1");
				account.tree_ecil2 = rs.getInt("tree_ecil2");
				account.tree_ecil3 = rs.getInt("tree_ecil3");
				account.tree_atun1 = rs.getInt("tree_atun1");
				account.tree_atun2 = rs.getInt("tree_atun2");
				account.tree_atun3 = rs.getInt("tree_atun3");

				account.hero01 = rs.getInt("hero01");
				account.hero02 = rs.getInt("hero02");
				account.hero03 = rs.getInt("hero03");
				account.hero04 = rs.getInt("hero04");
				account.hero05 = rs.getInt("hero05");
				account.hero06 = rs.getInt("hero06");
				account.hero07 = rs.getInt("hero07");
				account.hero08 = rs.getInt("hero08");
				account.hero09 = rs.getInt("hero09");
				account.hero10 = rs.getInt("hero10");
				account.hero11 = rs.getInt("hero11");
				account.hero12 = rs.getInt("hero12");
				account.hero13 = rs.getInt("hero13");
				account.hero14 = rs.getInt("hero14");
				account.hero15 = rs.getInt("hero15");
				account.hero16 = rs.getInt("hero16");
				account.hero17 = rs.getInt("hero17");
				account.hero18 = rs.getInt("hero18");
				account.hero19 = rs.getInt("hero19");
				account.hero20 = rs.getInt("hero20");
				account.hero21 = rs.getInt("hero21");

				account.legend01 = rs.getInt("legend01");
				account.legend02 = rs.getInt("legend02");
				account.legend03 = rs.getInt("legend03");
				account.legend04 = rs.getInt("legend04");
				account.legend05 = rs.getInt("legend05");
				account.legend06 = rs.getInt("legend06");
				account.legend07 = rs.getInt("legend07");
				account.legend08 = rs.getInt("legend08");
				account.legend09 = rs.getInt("legend09");
				account.legend10 = rs.getInt("legend10");
				account.legend11 = rs.getInt("legend11");
				account.legend12 = rs.getInt("legend12");
				account.legend13 = rs.getInt("legend13");
				account.legend14 = rs.getInt("legend14");
				account.legend15 = rs.getInt("legend15");
				account.legend16 = rs.getInt("legend16");
				account.legend17 = rs.getInt("legend17");
				account.legend18 = rs.getInt("legend18");
				account.legend19 = rs.getInt("legend19");
				account.legend20 = rs.getInt("legend20");
				account.legend21 = rs.getInt("legend21");
				account.legend22 = rs.getInt("legend22");
				account.legend23 = rs.getInt("legend23");
				account.legend24 = rs.getInt("legend24");
				account.legend25 = rs.getInt("legend25");

				account.myth01 = rs.getInt("myth01");
				account.myth02 = rs.getInt("myth02");

				_log.fine("account exists");
			}
		} catch (SQLException e) {
            // Only a successful SELECT with no row means the account is absent.
            // Never interpret a DB outage or a partial row decode as a missing
            // account: auto-registration may otherwise run against stale state.
            throw new IllegalStateException("Account load failed: " + name, e);
		}
		return account;
	}

	private void executeUpdate(String sql, Object... params) {
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			for (int i = 0; i < params.length; i++) {
				pstm.setObject(i + 1, params[i]);
			}
			pstm.setString(params.length + 1, _name);
			pstm.executeUpdate();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "Account executeUpdate error [SQL: " + sql + "]", e);
		}
	}

	public static void updateLastActive(final Account account, String ip) {
		final long now = System.currentTimeMillis();
		CacheD.getInstance().saveLastActive(account.getName(), now, ip);
		account._lastActive = new Timestamp(now);
		account._ip = ip;
	}

	public int countCharacters() {
		int result = 0;
		String sql = "SELECT count(*) as cnt FROM characters WHERE account_name=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, _name);
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					result = rs.getInt("cnt");
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "countCharacters error", e);
		}
		return result;
	}

	public void updateNcoin() {
		executeUpdate("UPDATE accounts SET Ncoin_Point=? WHERE login = ?", Ncoin_point);
	}

	public static void resetShopOpenCount() {
		String sql = "UPDATE accounts SET Shop_open_count=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, 0);
			pstm.executeUpdate();
		} catch (Exception e) {
			_log.log(Level.SEVERE, "resetShopOpenCount error", e);
		}
	}

	public void updateShopOpenCount() {
		Shop_open_count++;
		executeUpdate("UPDATE accounts SET Shop_open_count=? WHERE login = ?", Shop_open_count);
	}

	public static void ban(final String account, int reason) {
		String sql = "UPDATE accounts SET banned=? WHERE login=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, reason);
			pstm.setString(2, account);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ban error", e);
		}
	}

	public boolean validatePassword(String accountName, final String rawPassword) {
		_isValid = false;
		if (_password == null || rawPassword == null) return false;
		try {
			_isValid = _password.equals(rawPassword)
					|| checkPassword(accountName, _password, rawPassword);
			return _isValid;
		} catch (Exception e) {
			_log.log(Level.SEVERE, "validatePassword error", e);
		}
		return false;
	}

	public static void updatePhone(final Account account) {
		String sql = "UPDATE accounts SET phone=? WHERE login=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, account.getphone());
			pstm.setString(2, account.getName());
			pstm.execute();
			account._phone = account.getphone();
		} catch (Exception e) {
			_log.log(Level.SEVERE, "accounts updatePhone 에러발생", e);
		}
	}

	public boolean isValid() {
		return _isValid;
	}
	
	public void setValid(boolean is_valid) {
		_isValid = is_valid;
	}

	public boolean isGameMaster() {
		return 0 < _accessLevel;
	}

	public String getName() { return _name; }
	public void setName(String name) { _name = name; }
	public String get_Password() { return _password; }
	public void setPassword(String password) { _password = password; }
	public Timestamp getBuff_PC방() { return _Buff_PC방; }
	public void setBuff_PC방(Timestamp ts) { _Buff_PC방 = ts; }
	public String getCPW() { return _CharPassword; }
	public void setCPW(String s) { _CharPassword = s; }
	public byte[] getwaitpacket() { return _waitpacket; }
	public void setwaitpacket(byte[] s) { _waitpacket = s; }
	public void setIp(String ip) { _ip = ip; }
	public String getIp() { return _ip; }
	public Timestamp getLastActive() { return _lastActive; }
	public void setLastActive(Timestamp ts) { _lastActive = ts; }
	public long getButtonTimeHome() { return _btnTimeHome; }
	public void setButtonTimeHome(long i) { _btnTimeHome = i; }
	public long getButtonTimePc() { return _btnTimePc; }
	public void setButtonTimePc(long i) { _btnTimePc = i; }
	public int getAccessLevel() { return _accessLevel; }
	public void setAccessLevel(int accessLevel) { _accessLevel = accessLevel; }
	public String getHost() { return _host; }
	public void setHost(String host) { _host = host; }
	public int getBannedCode() { return _banned; }
	public void setBannedCode(int banned) { _banned = banned; }
	public int getCharSlot() { return _charslot; }
	public void setCharSlot(int charSlot) { _charslot = charSlot; }
	public void is_changed_slot(boolean is_changed) { _is_changed_slot = is_changed; }
	public boolean is_changed_slot() { return _is_changed_slot; }
	public String getphone() { return _phone; }
	public void setphone(String s) { _phone = s; }

	public void UpdateCharPassword(String pwd) {
		executeUpdate("UPDATE accounts SET CharPassword=? WHERE login=?", pwd);
	}

	public void setCharSlot(CIOClient client, int i) {
		String sql = "UPDATE accounts SET charslot=? WHERE login = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, i);
			pstm.setString(2, client.getAccount().getName());
			pstm.execute();
			client.getAccount()._charslot = i;
		} catch (Exception e) {
			_log.log(Level.SEVERE, "setCharSlot error", e);
		}
	}

	public static boolean checkLoginIP(String ip) {
		int num = 0;
		String sql = "SELECT count(ip) as cnt FROM accounts WHERE ip=? ";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, ip);
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					num = rs.getInt("cnt");
				}
			}
			return num >= ConnectionConfig.NETWORK_ADDRESS2ACCOUNT;
		} catch (Exception e) {
			_log.log(Level.SEVERE, "checkLoginIP error", e);
		}
		return false;
	}

	public static boolean checkPassword(String accountName, String stored, String rawPassword) {
		if (stored == null || rawPassword == null) return false;
		if (stored.equals(rawPassword)) return true;
		String sql = "SELECT password(?) as pwd ";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, rawPassword);
			try (ResultSet rs = pstm.executeQuery()) {
				return rs.next() && stored.equals(rs.getString("pwd"));
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "checkPassword error", e);
		}
		return false;
	}

	public static boolean checkLoginBanIP(String ip) {
		int num = 0;
		String sql = "SELECT count(ip) as cnt FROM ban_ip WHERE ip=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, ip);
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					num = rs.getInt("cnt");
				}
			}
			return num >= 1;
		} catch (Exception e) {
			_log.log(Level.SEVERE, "checkLoginBanIP error", e);
		}
		return false;
	}

	public void update피씨방() {
		executeUpdate("UPDATE accounts SET BUFF_PCROOM_Time=? WHERE login = ?", _Buff_PC방);
	}

	public static void setGamePassword(CIOClient client, int pass) {
		String sql = "UPDATE accounts SET gamepassword=? WHERE login =?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, pass);
			pstm.setString(2, client.getAccount().getName());
			pstm.execute();
			client.getAccount()._GamePassword = pass;
		} catch (Exception e) {
			_log.log(Level.SEVERE, "setGamePassword error", e);
		}
	}

	// --- 통합 헬퍼로 간소화된 update 메서드들  ---
	public void updateKir() { executeUpdate("UPDATE accounts SET info_kir_poly=? WHERE login = ?", info_kir_poly); }
	public void updatecardpu() { executeUpdate("UPDATE accounts SET info_card_a=?, info_card_b=?, info_card_c=? WHERE login = ?", info_card_a, info_card_b, info_card_c); }
	public void updateMyth02() { executeUpdate("UPDATE accounts SET myth02=? WHERE login = ?", myth02); }
	public void updateMyth() { executeUpdate("UPDATE accounts SET info_class_poly=?, info_ki_poly=?, info_el_poly=?, info_wi_poly=?, info_da_poly=?, info_wa_poly=?, info_il_poly=?, info_drag_poly=?, info_dsn_poly=?, info_bapo_poly=?, myth01 =? WHERE login = ?", info_class_poly, info_ki_poly, info_el_poly, info_wi_poly, info_da_poly, info_wa_poly, info_il_poly, info_drag_poly, info_dsn_poly, info_bapo_poly, myth01); }
	public void updateTree() { executeUpdate("UPDATE accounts SET tree_rain=?, tree_iceq=?, tree_fireq=?, tree_ices=?, tree_final=?, tree_cri=?, tree_select=?, tree_select2=?, tree_select3=?, tree_crim1=?, tree_crim2=?, tree_ecil1=?, tree_ecil2=?, tree_ecil3=? WHERE login = ?", tree_rain, tree_iceq, tree_fireq, tree_ices, tree_final, tree_cri, tree_select, tree_select2, tree_select3, tree_crim1, tree_crim2, tree_ecil1, tree_ecil2, tree_ecil3); }
	public void updateTree2() { executeUpdate("UPDATE accounts SET tree_select4=?, tree_atun1=?, tree_atun2=?, tree_atun3=? WHERE login = ?", tree_select4, tree_atun1, tree_atun2, tree_atun3); }
	public void updateCollection() { executeUpdate("UPDATE accounts SET info_col_y=?, info_col_m=?, info_col_l=?, info_col_h=?, info_colme_y=?, info_colme_m=?, info_colme_l=?, info_colme_h=? WHERE login = ?", info_col_y, info_col_m, info_col_l, info_col_h, info_colme_y, info_colme_m, info_colme_l, info_colme_h); }
	public void updateHero2() { executeUpdate("UPDATE accounts SET hero01=?, hero02=?, hero03=?, hero04=?, hero05=?, hero06=?, hero07=?, hero08=?, hero09=?, hero10=?, hero11=?, hero12=?, hero13=?, hero14=?, hero15=?, hero16=?, hero17=?, hero18=?, hero19=?, hero20=? WHERE login = ?", hero01, hero02, hero03, hero04, hero05, hero06, hero07, hero08, hero09, hero10, hero11, hero12, hero13, hero14, hero15, hero16, hero17, hero18, hero19, hero20); }
	public void updateHero21() { executeUpdate("UPDATE accounts SET hero21=? WHERE login = ?", hero21); }
	public void updatelegend2() { executeUpdate("UPDATE accounts SET legend01=?, legend02=?, legend03=?, legend04=?, legend05=?, legend06=?, legend07=?, legend08=?, legend09=?, legend10=?, legend11=?, legend12=?, legend13=?, legend14=?, legend15=?, legend16=?, legend17=?, legend18=?, legend19=?, legend20=?, legend21=?, legend22=?, legend23=?, legend24=?, legend25=?, myth01=?, myth02=? WHERE login = ?", legend01, legend02, legend03, legend04, legend05, legend06, legend07, legend08, legend09, legend10, legend11, legend12, legend13, legend14, legend15, legend16, legend17, legend18, legend19, legend20, legend21, legend22, legend23, legend24, legend25, myth01, myth02); }
	public void updateHero() { executeUpdate("UPDATE accounts SET info_heaven_poly=?, info_val_poly=?, info_larry_poly=?, info_gunter_poly=?, info_depor_poly=?, info_deathred_poly=?, info_ecil_poly=?, info_assm_poly=?, info_kerep_poly=?, info_jowo_poly=?, info_unik_poly=?, info_unim_poly=?, info_unir_poly=?, info_yozil_poly=?, info_owen_poly=?, info_helv_poly=? WHERE login = ?", info_heaven_poly, info_val_poly, info_larry_poly, info_gunter_poly, info_depor_poly, info_deathred_poly, info_ecil_poly, info_assm_poly, info_kerep_poly, info_jowo_poly, info_unik_poly, info_unim_poly, info_unir_poly, info_yozil_poly, info_owen_poly, info_helv_poly); }
	public void updateLegend() { executeUpdate("UPDATE accounts SET info_ds_poly=?, info_dru_poly=?, info_ga_poly=?, info_ger_poly=?, info_dev_poly=?, info_pen_poly=?, info_atun_poly=?, info_moon_poly=?, info_mar_poly=? WHERE login = ?", info_ds_poly, info_dru_poly, info_ga_poly, info_ger_poly, info_dev_poly, info_pen_poly, info_atun_poly, info_moon_poly, info_mar_poly); }
	public void updateDrag() { executeUpdate("UPDATE accounts SET info_drag_poly=? WHERE login = ?", info_drag_poly); }
	public void updateClass() { executeUpdate("UPDATE accounts SET info_class_poly=? WHERE login = ?", info_class_poly); }
	public void updateDs() { executeUpdate("UPDATE accounts SET info_ds_poly=? WHERE login = ?", info_ds_poly); }
	public void updateDru() { executeUpdate("UPDATE accounts SET info_dru_poly=? WHERE login = ?", info_dru_poly); }
	public void updateGa() { executeUpdate("UPDATE accounts SET info_ga_poly=? WHERE login = ?", info_ga_poly); }
	public void updateGer() { executeUpdate("UPDATE accounts SET info_ger_poly=? WHERE login = ?", info_ger_poly); }
	public void updateDev() { executeUpdate("UPDATE accounts SET info_dev_poly=? WHERE login = ?", info_dev_poly); }
	public void updatePen() { executeUpdate("UPDATE accounts SET info_pen_poly=? WHERE login = ?", info_pen_poly); }
	public void updateAtun() { executeUpdate("UPDATE accounts SET info_atun_poly=? WHERE login = ?", info_atun_poly); }
	public void updateMoon() { executeUpdate("UPDATE accounts SET info_moon_poly=? WHERE login = ?", info_moon_poly); }
	public void updateMar() { executeUpdate("UPDATE accounts SET info_mar_poly=? WHERE login = ?", info_mar_poly); }
	public void updateDsn() { executeUpdate("UPDATE accounts SET info_dsn_poly=? WHERE login = ?", info_dsn_poly); }
	public void updateKi() { executeUpdate("UPDATE accounts SET info_ki_poly=? WHERE login = ?", info_ki_poly); }
	public void updateEl() { executeUpdate("UPDATE accounts SET info_el_poly=? WHERE login = ?", info_el_poly); }
	public void updateWi() { executeUpdate("UPDATE accounts SET info_wi_poly=? WHERE login = ?", info_wi_poly); }
	public void updateDa() { executeUpdate("UPDATE accounts SET info_da_poly=? WHERE login = ?", info_da_poly); }
	public void updateWa() { executeUpdate("UPDATE accounts SET info_wa_poly=? WHERE login = ?", info_wa_poly); }
	public void updateIl() { executeUpdate("UPDATE accounts SET info_il_poly=? WHERE login = ?", info_il_poly); }
	public void updateBapo() { executeUpdate("UPDATE accounts SET info_bapo_poly=? WHERE login = ?", info_bapo_poly); }

    private transient volatile L1Tam _tamHandler;

    public L1Tam getTamHandler() {
        L1Tam handler = _tamHandler;
        if (handler != null) return handler;
        synchronized (this) {
            handler = _tamHandler;
            if (handler == null) {
                handler = new L1Tam(this);
                _tamHandler = handler;
            }
        }
        return handler;
    }

	public void tamUpdate(final Account account) {
		getTamHandler().lessTimeUpdate();
	}

	public void updateTam() {
		getTamHandler().updateTamPoint();
	}
	
	public int getTamActiveCountCached() {
		return getTamHandler().getActiveCountAndRefreshCached();
	}

	public long getTamRemainTimeMsCached() {
		return getTamHandler().getRemainTimeMsCached();
	}
	
	public void invalidateTamCache() {
		L1Tam h = _tamHandler;
		if (h != null) h.invalidateCache();
	}

    // A receipt-backed TAM award spans a DB COMMIT and the CIO owner replay.
    // Suppress snapshot flushes in this interval: the account DB balance may
    // already include the award before its in-memory callback has run.
    private transient boolean tamAwardPending;

    public synchronized boolean tryBeginTamAward() {
        if (tamAwardPending || tam_point < 0 || tam_point > Integer.MAX_VALUE - 100000) return false;
        tamAwardPending = true;
        return true;
    }

    public synchronized void endTamAward() {
        tamAwardPending = false;
    }

    public synchronized boolean isTamAwardPending() {
        return tamAwardPending;
    }

	public int getTamPoint() { return tam_point; }
	public synchronized int setTamPoint(int tampoint) { return tam_point = tampoint; }
	public synchronized int addTamPoint(int tampoint) { return tam_point += tampoint; }
	
	public int getInfo_kir_poly() { return info_kir_poly; }
	public int setInfo_kir_poly(int kir_poly) { return info_kir_poly = kir_poly; }
	public int getInfo_drag_poly() { return info_drag_poly; }
	public int setInfo_drag_poly(int drag_poly) { return info_drag_poly = drag_poly; }
	
	// 합성
	public int getInfo_card_a() { return info_card_a; }
	public int setInfo_card_a(int card_a) { return info_card_a = card_a; }
	public int getInfo_card_b() { return info_card_b; }
	public int setInfo_card_b(int card_b) { return info_card_b = card_b; }
	public int getInfo_card_c() { return info_card_c; }
	public int setInfo_card_c(int card_c) { return info_card_c = card_c; }
	public int getInfo_card_d() { return info_card_d; }
	public int setInfo_card_d(int card_d) { return info_card_d = card_d; }
	
	// 영웅
	public int getInfo_heaven_poly() { return info_heaven_poly; }
	public int setInfo_heaven_poly(int heaven_poly) { return info_heaven_poly = heaven_poly; }
	public int getInfo_val_poly() { return info_val_poly; }
	public int setInfo_val_poly(int val_poly) { return info_val_poly = val_poly; }
	public int getInfo_larry_poly() { return info_larry_poly; }
	public int setInfo_larry_poly(int larry_poly) { return info_larry_poly = larry_poly; }
	public int getInfo_gunter_poly() { return info_gunter_poly; }
	public int setInfo_gunter_poly(int gunter_poly) { return info_gunter_poly = gunter_poly; }
	public int getInfo_depor_poly() { return info_depor_poly; }
	public int setInfo_depor_poly(int depor_poly) { return info_depor_poly = depor_poly; }
	public int getInfo_deathred_poly() { return info_deathred_poly; }
	public int setInfo_deathred_poly(int deathred_poly) { return info_deathred_poly = deathred_poly; }
	public int getInfo_ecil_poly() { return info_ecil_poly; }
	public int setInfo_ecil_poly(int ecil_poly) { return info_ecil_poly = ecil_poly; }
	public int getInfo_assm_poly() { return info_assm_poly; }
	public int setInfo_assm_poly(int assm_poly) { return info_assm_poly = assm_poly; }
	public int getInfo_kerep_poly() { return info_kerep_poly; }
	public int setInfo_kerep_poly(int kerep_poly) { return info_kerep_poly = kerep_poly; }
	public int getInfo_jowo_poly() { return info_jowo_poly; }
	public int setInfo_jowo_poly(int jowo_poly) { return info_jowo_poly = jowo_poly; }
	public int getInfo_unik_poly() { return info_unik_poly; }
	public int setInfo_unik_poly(int unik_poly) { return info_unik_poly = unik_poly; }
	public int getInfo_unim_poly() { return info_unim_poly; }
	public int setInfo_unim_poly(int unim_poly) { return info_unim_poly = unim_poly; }
	public int getInfo_unir_poly() { return info_unir_poly; }
	public int setInfo_unir_poly(int unir_poly) { return info_unir_poly = unir_poly; }
	public int getInfo_yozil_poly() { return info_yozil_poly; }
	public int setInfo_yozil_poly(int yozil_poly) { return info_yozil_poly = yozil_poly; }
	public int getInfo_owen_poly() { return info_owen_poly; }
	public int setInfo_owen_poly(int owen_poly) { return info_owen_poly = owen_poly; }
	public int getInfo_helv_poly() { return info_helv_poly; }
	public int setInfo_helv_poly(int helv_poly) { return info_helv_poly = helv_poly; }
	
	// 신규 추가 영웅 / 전설 / 신화
	public int gethero01() { return hero01; } public int sethero01(int h01) { return hero01 = h01; }
	public int gethero02() { return hero02; } public int sethero02(int h02) { return hero02 = h02; }
	public int gethero03() { return hero03; } public int sethero03(int h03) { return hero03 = h03; }
	public int gethero04() { return hero04; } public int sethero04(int h04) { return hero04 = h04; }
	public int gethero05() { return hero05; } public int sethero05(int h05) { return hero05 = h05; }
	public int gethero06() { return hero06; } public int sethero06(int h06) { return hero06 = h06; }
	public int gethero07() { return hero07; } public int sethero07(int h07) { return hero07 = h07; }
	public int gethero08() { return hero08; } public int sethero08(int h08) { return hero08 = h08; }
	public int gethero09() { return hero09; } public int sethero09(int h09) { return hero09 = h09; }
	public int gethero10() { return hero10; } public int sethero10(int h10) { return hero10 = h10; }
	public int gethero11() { return hero11; } public int sethero11(int h11) { return hero11 = h11; }
	public int gethero12() { return hero12; } public int sethero12(int h12) { return hero12 = h12; }
	public int gethero13() { return hero13; } public int sethero13(int h13) { return hero13 = h13; }
	public int gethero14() { return hero14; } public int sethero14(int h14) { return hero14 = h14; }
	public int gethero15() { return hero15; } public int sethero15(int h15) { return hero15 = h15; }
	public int gethero16() { return hero16; } public int sethero16(int h16) { return hero16 = h16; }
	public int gethero17() { return hero17; } public int sethero17(int h17) { return hero17 = h17; }
	public int gethero18() { return hero18; } public int sethero18(int h18) { return hero18 = h18; }
	public int gethero19() { return hero19; } public int sethero19(int h19) { return hero19 = h19; }
	public int gethero20() { return hero20; } public int sethero20(int h20) { return hero20 = h20; }
	public int gethero21() { return hero21; } public int sethero21(int h21) { return hero21 = h21; }
	
	public int getlegend01() { return legend01; }
	public int setlegend01(int l01) { return legend01 = l01; }
	public int getlegend02() { return legend02; } public int setlegend02(int l02) { return legend02 = l02; }
	public int getlegend03() { return legend03; } public int setlegend03(int l03) { return legend03 = l03; }
	public int getlegend04() { return legend04; } public int setlegend04(int l04) { return legend04 = l04; }
	public int getlegend05() { return legend05; } public int setlegend05(int l05) { return legend05 = l05; }
	public int getlegend06() { return legend06; } public int setlegend06(int l06) { return legend06 = l06; }
	public int getlegend07() { return legend07; } public int setlegend07(int l07) { return legend07 = l07; }
	public int getlegend08() { return legend08; } public int setlegend08(int l08) { return legend08 = l08; }
	public int getlegend09() { return legend09; } public int setlegend09(int l09) { return legend09 = l09; }
	public int getlegend10() { return legend10; } public int setlegend10(int l10) { return legend10 = l10; }
	public int getlegend11() { return legend11; } public int setlegend11(int l11) { return legend11 = l11; }
	public int getlegend12() { return legend12; } public int setlegend12(int l12) { return legend12 = l12; }
	public int getlegend13() { return legend13; } public int setlegend13(int l13) { return legend13 = l13; }
	public int getlegend14() { return legend14; } public int setlegend14(int l14) { return legend14 = l14; }
	public int getlegend15() { return legend15; } public int setlegend15(int v) { return legend15 = v; }
	public int getlegend16() { return legend16; } public int setlegend16(int v) { return legend16 = v; }
	public int getlegend17() { return legend17; } public int setlegend17(int v) { return legend17 = v; }
	public int getlegend18() { return legend18; } public int setlegend18(int v) { return legend18 = v; }
	public int getlegend19() { return legend19; } public int setlegend19(int v) { return legend19 = v; }
	public int getlegend20() { return legend20; } public int setlegend20(int v) { return legend20 = v; }
	public int getlegend21() { return legend21; } public int setlegend21(int v) { return legend21 = v; }
	public int getlegend22() { return legend22; } public int setlegend22(int v) { return legend22 = v; }
	public int getlegend23() { return legend23; } public int setlegend23(int v) { return legend23 = v; }
	public int getlegend24() { return legend24; } public int setlegend24(int v) { return legend24 = v; }
	public int getlegend25() { return legend25; } public int setlegend25(int v) { return legend25 = v; }
	
	public int getmyth01() { return myth01; } public int setmyth01(int m1) { return myth01 = m1; }
	public int getmyth02() { return myth02; } public int setmyth02(int m2) { return myth02 = m2; }
	
	// 추가 수호성
	public int getTree_select4() { return tree_select4; } public int setTree_select4(int i) { return tree_select4 = i; }
	public int getTree_atun1() { return tree_atun1; } public int setTree_atun1(int i) { return tree_atun1 = i; }
	public int getTree_atun2() { return tree_atun2; } public int setTree_atun2(int i) { return tree_atun2 = i; }
	public int getTree_atun3() { return tree_atun3; } public int setTree_atun3(int i) { return tree_atun3 = i; }
	
	// 컬렉션
	public int getInfo_col_y() { return info_col_y; } public int setInfo_col_y(int col_y) { return info_col_y = col_y; }
	public int getInfo_col_m() { return info_col_m; } public int setInfo_col_m(int col_m) { return info_col_m = col_m; }
	public int getInfo_col_l() { return info_col_l; } public int setInfo_col_l(int col_l) { return info_col_l = col_l; }
	public int getInfo_col_h() { return info_col_h; } public int setInfo_col_h(int col_h) { return info_col_h = col_h; }
	
	// 컬렉션 메세지
	public int getInfo_colme_y() { return info_colme_y; } public int setInfo_colme_y(int colme_y) { return info_colme_y = colme_y; }
	public int getInfo_colme_m() { return info_colme_m; } public int setInfo_colme_m(int colme_m) { return info_colme_m = colme_m; }
	public int getInfo_colme_l() { return info_colme_l; } public int setInfo_colme_l(int colme_l) { return info_colme_l = colme_l; }
	public int getInfo_colme_h() { return info_colme_h; } public int setInfo_colme_h(int colme_h) { return info_colme_h = colme_h; }
	
	// 수호성
	public int getTree_rain() { return tree_rain; } public int setTree_rain(int t_rain) { return tree_rain = t_rain; }
	public int getTree_iceq() { return tree_iceq; } public int setTree_iceq(int t_iceq) { return tree_iceq = t_iceq; }
	public int getTree_fireq() { return tree_fireq; } public int setTree_fireq(int t_fireq) { return tree_fireq = t_fireq; }
	public int getTree_ices() { return tree_ices; } public int setTree_ices(int t_ices) { return tree_ices = t_ices; }
	public int getTree_final() { return tree_final; } public int setTree_final(int t_final) { return tree_final = t_final; }
	public int getTree_cri() { return tree_cri; } public int setTree_cri(int t_cri) { return tree_cri = t_cri; }
	public int getTree_select() { return tree_select; } public int setTree_select(int t_select) { return tree_select = t_select; }
	public int getTree_select2() { return tree_select2; } public int setTree_select2(int t_select2) { return tree_select2 = t_select2; }
	public int getTree_select3() { return tree_select3; } public int setTree_select3(int t_select3) { return tree_select3 = t_select3; }
	public int getTree_crim1() { return tree_crim1; } public int setTree_crim1(int t_crim1) { return tree_crim1 = t_crim1; }
	public int getTree_crim2() { return tree_crim2; } public int setTree_crim2(int t_crim2) { return tree_crim2 = t_crim2; }
	public int getTree_ecil1() { return tree_ecil1; } public int setTree_ecil1(int t_ecil1) { return tree_ecil1 = t_ecil1; }
	public int getTree_ecil2() { return tree_ecil2; } public int setTree_ecil2(int t_ecil2) { return tree_ecil2 = t_ecil2; }
	public int getTree_ecil3() { return tree_ecil3; } public int setTree_ecil3(int t_ecil3) { return tree_ecil3 = t_ecil3; }
	
	public int getInfo_class_poly() { return info_class_poly; } public int setInfo_class_poly(int class_poly) { return info_class_poly = class_poly; }
	public int getInfo_ds_poly() { return info_ds_poly; } public int setInfo_ds_poly(int ds_poly) { return info_ds_poly = ds_poly; }
	public int getInfo_dru_poly() { return info_dru_poly; } public int setInfo_dru_poly(int dru_poly) { return info_dru_poly = dru_poly; }
	public int getInfo_ga_poly() { return info_ga_poly; } public int setInfo_ga_poly(int ga_poly) { return info_ga_poly = ga_poly; }
	public int getInfo_ger_poly() { return info_ger_poly; } public int setInfo_ger_poly(int ger_poly) { return info_ger_poly = ger_poly; }
	public int getInfo_dev_poly() { return info_dev_poly; } public int setInfo_dev_poly(int dev_poly) { return info_dev_poly = dev_poly; }
	public int getInfo_pen_poly() { return info_pen_poly; } public int setInfo_pen_poly(int pen_poly) { return info_pen_poly = pen_poly; }
	public int getInfo_atun_poly() { return info_atun_poly; } public int setInfo_atun_poly(int atun_poly) { return info_atun_poly = atun_poly; }
	public int getInfo_moon_poly() { return info_moon_poly; } public int setInfo_moon_poly(int moon_poly) { return info_moon_poly = moon_poly; }
	public int getInfo_mar_poly() { return info_mar_poly; } public int setInfo_mar_poly(int mar_poly) { return info_mar_poly = mar_poly; }
	public int getInfo_dsn_poly() { return info_dsn_poly; } public int setInfo_dsn_poly(int dsn_poly) { return info_dsn_poly = dsn_poly; }
	public int getInfo_bapo_poly() { return info_bapo_poly; } public int setInfo_bapo_poly(int bapo_poly) { return info_bapo_poly = bapo_poly; }
	public int getInfo_ki_poly() { return info_ki_poly; } public int setInfo_ki_poly(int ki_poly) { return info_ki_poly = ki_poly; }
	public int getInfo_el_poly() { return info_el_poly; } public int setInfo_el_poly(int el_poly) { return info_el_poly = el_poly; }
	public int getInfo_wi_poly() { return info_wi_poly; } public int setInfo_wi_poly(int wi_poly) { return info_wi_poly = wi_poly; }
	public int getInfo_da_poly() { return info_da_poly; } public int setInfo_da_poly(int da_poly) { return info_da_poly = da_poly; }
	public int getInfo_wa_poly() { return info_wa_poly; } public int setInfo_wa_poly(int wa_poly) { return info_wa_poly = wa_poly; }
	public int getInfo_il_poly() { return info_il_poly; } public int setInfo_il_poly(int il_poly) { return info_il_poly = il_poly; }

	public int getGamePassword() { return _GamePassword; }
	public void setGamePassword(int gamePassword) { _GamePassword = gamePassword; }

	public Timestamp getDragonRaid() { return _raidBuff; }
	public void setDragonRaid(Timestamp ts) { _raidBuff = ts; }

	public int getBlessOfAin() { return this._blessOfAin; }
    public synchronized void setBlessOfAin(int i) {
        replaceBlessOfAin(i);
    }

    private void replaceBlessOfAin(long value) {
        final int next = (int) Math.max(0L, Math.min(50000000L, value));
        if (next != _blessOfAin) {
            _blessOfAin = next;
            updateBlessOfAin();
        }
    }

    /** Existing internal units, not a new historical XP-per-percent constant. */
    public synchronized int recoverBlessOfAin(long units, int ceiling) {
        final int limit = Math.max(0, Math.min(50000000, ceiling));
        if (units > 0 && _blessOfAin < limit) {
            final long room = (long) limit - _blessOfAin;
            replaceBlessOfAin(units >= room ? limit : (long) _blessOfAin + units);
        }
        return _blessOfAin;
    }

    public synchronized void addBlessOfAin(int i, L1PcInstance pc) {
        replaceBlessOfAin((long) _blessOfAin + i);
        // One authoritative gauge notification, including the transition to 0.
        SC_RestExpInfo.send(pc);

        if (pc == null) return;
		
		if (pc.hasSkillEffect(L1SkillId.EXP_BUFF)) {
			int skillTime = pc.getSkillEffectTimeSec(L1SkillId.EXP_BUFF);
			if (_blessOfAin < 10000) {
				SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.EXP_BUFF + 1, 5087, true); 
			} else {
				SC_InventoryIcon.icoEnd(pc, L1SkillId.EXP_BUFF + 1);
				SC_InventoryIcon.icoNew(pc, L1SkillId.EXP_BUFF, 5087, skillTime, true);
			}
		}
	}

    public synchronized void updateBlessOfAin() {
		if (_blessOfAin < 0) _blessOfAin = 0;
		CacheD.getInstance().saveBlessOfAin(_name, _blessOfAin);
	}

	public int getGrangKinAngerTime() { return _grangKinAngerTime; }
	public void addGrangKinAngerTime(int i, L1PcInstance pc) {}


	public void setGrangKinAngerTime(int i) {}
	public void setGrangKinAngerTime(int i, L1PcInstance pc) {}
	public int getGrangKinAngerStat() { return 0; }

	public Timestamp getLastLogOut() { return _lastlogout; }
	public void setLastLogOut(Timestamp ts) { _lastlogout = ts; }
	
	public void updateLastLogOut() {
		final long now = System.currentTimeMillis();
		CacheD.getInstance().saveLastLogout(_name, now);
		_lastlogout = new Timestamp(now);
	}

	public double getGrangKinAngerExpCalc() {
//		double exp_down = 0.0;
//		if (getGrangKinAngerStat() == 1) {
//			exp_down = 0.7;
//		} else if (getGrangKinAngerStat() == 2) {
//			exp_down = 0.4;
//		} else if (getGrangKinAngerStat() >= 3) {
//			exp_down = 0.1;
//		}
		return 0;
	}

	public double getGrangKinAngerReducCalc() {
//		double dmgRate = 1.0;
//		if (getGrangKinAngerStat() == 1) {
//			dmgRate = 3.0;
//		} else if (getGrangKinAngerStat() == 2) {
//			dmgRate = 3.5;
//		} else if (getGrangKinAngerStat() >= 3) {
//			dmgRate = 4.0;
//		} else if (getGrangKinAngerStat() >= 4) {
//			dmgRate = 4.5;
//		} else if (getGrangKinAngerStat() >= 5) {
//			dmgRate = 5.0;
//		} else if (getGrangKinAngerStat() >= 6) {
//			dmgRate = 5.0;
//		}
		return 0;
	}

	public int getGrangKinAngerDmgCalc(double dmg) {
//		int calcDmg = 0;
//		if (getGrangKinAngerStat() == 1) {
//			calcDmg = (int) (dmg * 0.5);
//		} else if (getGrangKinAngerStat() == 2) {
//			calcDmg = (int) (dmg * 0.6);
//		} else if (getGrangKinAngerStat() >= 3) {
//			calcDmg = (int) (dmg * 0.7);
//		} else if (getGrangKinAngerStat() >= 4) {
//			calcDmg = (int) (dmg * 0.8);
//		} else if (getGrangKinAngerStat() >= 5) {
//			calcDmg = (int) (dmg * 0.9);
//		} else if (getGrangKinAngerStat() >= 6) {
//			calcDmg = (int) (dmg * 0.9);
//		}
		return 0;
	}
	
	public int getAccountId() { return _id; }
	public void setAccountId(int i) { _id = i; }
	
	public static Integer[] loadAccountAddress(String accountName) {
		MJObjectWrapper<Integer[]> wrapper = new MJObjectWrapper<Integer[]>();
		Selector.exec("select ip from accounts where login=?", new SelectorHandler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setString(1, accountName);
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				if(rs.next()){
					try {					
						String addressValues = rs.getString("ip");
						StringTokenizer st = new StringTokenizer(addressValues, ".");

						String ip1 = st.nextToken();
						String ip2 = st.nextToken();
						String ip3 = st.nextToken();
						Integer[] itg = new Integer[]{
								Integer.parseInt(ip1),	
								Integer.parseInt(ip2),	
								Integer.parseInt(ip3),	
						};
						wrapper.value = itg;
					} catch(Exception e) {
						_log.log(Level.SEVERE, "loadAccountAddress parsing error", e);
					}
				}
			}
		});
		return wrapper.value;
	}
}
