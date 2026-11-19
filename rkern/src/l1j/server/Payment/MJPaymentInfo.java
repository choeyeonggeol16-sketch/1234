package l1j.server.Payment;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import l1j.server.MJTemplate.MJObjectWrapper;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.server.utils.CommonUtil;

public class MJPaymentInfo {
	public static MJPaymentInfo newInstance(String name, int adena, String character_name){
		return newInstance()
				.set_issue_date(CommonUtil.getLocalTime())
				.set_issue_character_name(character_name)
				.set_code(MJPaymentCipher.getInstance().next_code())
				.set_name(name)
				.set_account_name("")
				.set_character_name("")
				.set_adena(adena)
				.set_is_use(false)
				.set_expire_date("")
				.do_update();
	}
	
	public static MJPaymentInfo newInstance(String code, String name){
		final MJObjectWrapper<MJPaymentInfo> wrapper = new MJObjectWrapper<MJPaymentInfo>();
		Selector.exec("select * from payment_info where code=? and name=?", new SelectorHandler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setString(1, code);
				pstm.setString(2, name);
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				if(rs.next())
					wrapper.value = newInstance(rs);
			}
		});
		return wrapper.value;
	}

	private static MJPaymentInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_code(rs.getString("code"))
				.set_name(rs.getString("name"))
				.set_issue_date(rs.getString("issue_date"))
				.set_issue_character_name(rs.getString("issue_character_name"))
				.set_account_name(rs.getString("account_name"))
				.set_character_name(rs.getString("character_name"))
				.set_adena(rs.getInt("adena"))
				.set_is_use(rs.getInt("is_use") != 0)
				.set_expire_date(rs.getString("expire_date"));
	}
	
	private static void do_update(final MJPaymentInfo pInfo){
		Updator.exec("insert into payment_info set code=?, name=?, issue_date=?, issue_character_name=?, account_name=?, character_name=?, adena=?, is_use=?, expire_date=? "
				+ "on duplicate key update "
				+ "issue_date=?, issue_character_name=?, account_name=?, character_name=?, adena=?, is_use=?, expire_date=?", 
				new Handler(){
					@Override
					public void handle(PreparedStatement pstm) throws Exception {
						int idx = 0;
						pstm.setString(++idx, pInfo.get_code());
						pstm.setString(++idx, pInfo.get_name());
						pstm.setString(++idx, pInfo.get_issue_date());
						pstm.setString(++idx, pInfo.get_issue_character_name());
						pstm.setString(++idx, pInfo.get_account_name());
						pstm.setString(++idx, pInfo.get_character_name());
						pstm.setInt(++idx, pInfo.get_adena());
						pstm.setInt(++idx, pInfo.get_is_use() ? 1 : 0);
						pstm.setString(++idx, pInfo.get_expire_date());
						
						pstm.setString(++idx, pInfo.get_issue_date());
						pstm.setString(++idx, pInfo.get_issue_character_name());
						pstm.setString(++idx, pInfo.get_account_name());
						pstm.setString(++idx, pInfo.get_character_name());
						pstm.setInt(++idx, pInfo.get_adena());
						pstm.setInt(++idx, pInfo.get_is_use() ? 1 : 0);
						pstm.setString(++idx, pInfo.get_expire_date());
					}
				});
	}

	private static MJPaymentInfo newInstance(){
		return new MJPaymentInfo();
	}

	private String m_code;
	private String m_name;
	private String m_issue_date;
	private String m_issue_character_name;
	private String m_account_name;
	private String m_character_name;
	private int m_adena;
	private boolean m_is_use;
	private String m_expire_date;
	private MJPaymentInfo(){}

	public MJPaymentInfo set_code(String code){
		m_code = code;
		return this;
	}
	public MJPaymentInfo set_name(String name){
		m_name = name;
		return this;
	}
	public MJPaymentInfo set_issue_date(String issue_date){
		m_issue_date = issue_date;
		return this;
	}
	public MJPaymentInfo set_issue_character_name(String issue_character_name){
		m_issue_character_name = issue_character_name;
		return this;
	}
	public MJPaymentInfo set_account_name(String account_name){
		m_account_name = account_name;
		return this;
	}
	public MJPaymentInfo set_character_name(String character_name){
		m_character_name = character_name;
		return this;
	}
	public MJPaymentInfo set_adena(int adena){
		m_adena = adena;
		return this;
	}
	public MJPaymentInfo set_is_use(boolean is_use){
		m_is_use = is_use;
		return this;
	}
	public MJPaymentInfo set_expire_date(String expire_date){
		m_expire_date = expire_date;
		return this;
	}
	public String get_code(){
		return m_code;
	}
	public String get_name(){
		return m_name;
	}
	public String get_issue_date(){
		return m_issue_date;
	}
	public String get_issue_character_name(){
		return m_issue_character_name;
	}
	public String get_account_name(){
		return m_account_name;
	}
	public String get_character_name(){
		return m_character_name;
	}
	public int get_adena(){
		return m_adena;
	}
	public boolean get_is_use(){
		return m_is_use;
	}
	public String get_expire_date(){
		return m_expire_date;
	}
	public MJPaymentInfo do_update(){
		do_update(this);
		return this;
	}
}


