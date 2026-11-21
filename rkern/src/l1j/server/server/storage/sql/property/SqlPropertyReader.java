package l1j.server.server.storage.sql.property;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;
import l1j.server.server.utils.GameUtils;

public class SqlPropertyReader {
	public static void do_exec(String table_name, String section_name, String value_name, IPropertyHandler handler){
		newInstance(table_name, section_name, value_name, handler).execute();
	}
	
	private static SqlPropertyReader newInstance(String table_name, String section_name, String value_name, IPropertyHandler handler){
		return newInstance()
				.set_table_name(table_name)
				.set_section_name(section_name)
				.set_value_name(value_name)
				.set_handler(handler);
	}
	private static SqlPropertyReader newInstance(){
		return new SqlPropertyReader();
	}
	
	private String m_table_name;
	private String m_section_name;
	private String m_value_name;
	private IPropertyHandler m_handler;
	private SqlPropertyReader(){
	}
	public void execute(){
		Selector.exec(String.format("select %s, %s from %s", m_section_name, m_value_name, m_table_name), new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					m_handler.on_load(rs.getString(m_section_name), SqlPropertyReader.this, rs);
			}
		});
	}
	
	public SqlPropertyReader set_table_name(String table_name){
		m_table_name = table_name;
		return this;
	}
	public String get_table_name(){
		return m_table_name;
	}
	public SqlPropertyReader set_section_name(String section_name){
		m_section_name = section_name;
		return this;
	}
	public String get_section_name(){
		return m_section_name;
	}
	public SqlPropertyReader set_value_name(String value_name){
		m_value_name = value_name;
		return this;
	}
	public String get_value_section_name(){
		return m_value_name;
	}
	public SqlPropertyReader set_handler(IPropertyHandler handler){
		m_handler = handler;
		return this;
	}
	public IPropertyHandler get_handler(){
		return m_handler;
	}
	
	public String read_string(ResultSet rs) throws SQLException{
		return rs.getString(m_value_name);
	}
	
	public int read_int(ResultSet rs) throws SQLException{
		return rs.getInt(m_value_name);
	}
	
	public double read_double(ResultSet rs) throws NumberFormatException, SQLException{
		return Double.parseDouble(read_string(rs));
	}
	
	public double read_double_by_percent(ResultSet rs) throws SQLException{
		return (double)read_int(rs) * 0.01;
	}
	public boolean read_boolean(ResultSet rs) throws SQLException{
		return Boolean.parseBoolean(read_string(rs));
	}
	public int[] read_int_arrange(ResultSet rs, String tok) throws SQLException{
		return GameUtils.parseToIntArrange(read_string(rs), tok);
	}
	public ArrayList<Integer> read_int_array(ResultSet rs, String tok) throws SQLException{
		return GameUtils.parseToIntArray(read_string(rs), tok);
	}
}
