package l1j.server.Payment;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

import l1j.server.MJTemplate.MJObjectWrapper;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.server.utils.MJCommons;

public class MJPaymentCipher {
	private static final String[][] m_payment_patterns = new String[][]{
		{"G", "A", "V", "K", "U", "B", "N", "M", "J", "O", "Q", "R", "Y", "Z", "T", "P", "D", "W", "X", "S", "H", "C", "F", "I", "L", "E", },
		{"X", "R", "S", "C", "H", "V", "U", "E", "G", "Z", "F", "M", "I", "T", "A", "Y", "K", "J", "P", "N", "D", "Q", "L", "O", "W", "B",},
		{"B", "W", "I", "L", "A", "E", "J", "X", "U", "Y", "V", "H", "D", "S", "Z", "Q", "N", "P", "F", "M", "G", "R", "K", "C", "T", "O",},
		{"J", "K", "T", "Y", "R", "Z", "I", "O", "B", "N", "D", "E", "A", "W", "P", "C", "S", "Q", "G", "F", "L", "U", "X", "V", "M", "H", }
	};
	private static MJPaymentCipher m_instance;
	public static MJPaymentCipher getInstance(){
		if(m_instance == null)
			m_instance = new MJPaymentCipher();
		return m_instance;
	}

	private ArrayList<Integer> m_indices;
	private MJPaymentCipher(){
		m_indices = load();
	}
	
	private ArrayList<Integer> load(){
		final MJObjectWrapper<ArrayList<Integer>> wrapper = new MJObjectWrapper<ArrayList<Integer>>();
		Selector.exec("select * from payment_cipher", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				if(rs.next()){
					wrapper.value = MJCommons.parseToIntArray(rs.getString("indices"), "\\,");
				}
			}
		});
		if(wrapper.value == null){
			wrapper.value = new ArrayList<Integer>();
			for(int i=3; i>=0; --i)
				wrapper.value.add(i == 3 ? -1 : 0);
		}
		
		return wrapper.value;
	}
	
	private void update_index(){
		int size = m_indices.size();
		for(int i=0; i<size; ++i){
			int index = m_indices.get(i);
			if(index < 25){
				m_indices.set(i, ++index);
				return;
			}
			m_indices.set(i, 0);
		}
		m_indices.add(0);
	}
	
	private void store_index(final String s){
		Updator.exec("insert into payment_cipher set identity=?, indices=? on duplicate key update indices=?", new Handler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, 1);
				pstm.setString(2, s);
				pstm.setString(3, s);
			}
		});
	}
	
	public synchronized String next_code(){
		update_index();
		StringBuilder sb_code = new StringBuilder();
		StringBuilder sb_indices = new StringBuilder();
		int size = m_indices.size();
		for(int i=0; i<size; ++i){
			int index = m_indices.get(0);
			sb_code.append(m_payment_patterns[i % 4][index]);
			if(i != 0)
				sb_indices.append(",");
			sb_indices.append(index);
		}
		store_index(sb_indices.toString());
		return sb_code.toString();
	}

}
