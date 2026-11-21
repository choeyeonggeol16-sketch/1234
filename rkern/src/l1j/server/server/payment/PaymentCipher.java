package l1j.server.server.payment;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

import l1j.server.server.shared.ObjectWrapper;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.executors.Updator;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;
import l1j.server.server.storage.sql.handler.Handler;
import l1j.server.server.utils.GameUtils;

public class PaymentCipher {
	private static final String[][] m_payment_patterns = new String[][]{
		{"G", "A", "V", "K", "U", "B", "N", "M", "J", "O", "Q", "R", "Y", "Z", "T", "P", "D", "W", "X", "S", "H", "C", "F", "I", "L", "E", },
		{"X", "R", "S", "C", "H", "V", "U", "E", "G", "Z", "F", "M", "I", "T", "A", "Y", "K", "J", "P", "N", "D", "Q", "L", "O", "W", "B",},
		{"B", "W", "I", "L", "A", "E", "J", "X", "U", "Y", "V", "H", "D", "S", "Z", "Q", "N", "P", "F", "M", "G", "R", "K", "C", "T", "O",},
		{"J", "K", "T", "Y", "R", "Z", "I", "O", "B", "N", "D", "E", "A", "W", "P", "C", "S", "Q", "G", "F", "L", "U", "X", "V", "M", "H", }
	};
	private static PaymentCipher m_instance;
	public static PaymentCipher getInstance(){
		if(m_instance == null)
			m_instance = new PaymentCipher();
		return m_instance;
	}

	private ArrayList<Integer> m_indices;
	private PaymentCipher(){
		m_indices = load();
	}
	
	private ArrayList<Integer> load(){
		final ObjectWrapper<ArrayList<Integer>> wrapper = new ObjectWrapper<ArrayList<Integer>>();
		Selector.exec("select * from payment_cipher", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				if(rs.next()){
					wrapper.value = GameUtils.parseToIntArray(rs.getString("indices"), "\\,");
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
