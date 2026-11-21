package l1j.server.server.datatables;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.server.shared.GameRandom;
import l1j.server.server.shared.arrangement.ArrangeParseeFactory;
import l1j.server.server.shared.arrangement.ArrangeParser;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class AlchemyProbability{
	private static AlchemyProbability _instance;
	public static AlchemyProbability getInstance(){
		if(_instance == null)
			_instance = new AlchemyProbability();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		AlchemyProbability old = _instance;
		_instance = new AlchemyProbability();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, AlchemyProbStruct> _alchemy_structs;
	private AlchemyProbability(){
		_alchemy_structs = load();
	}
	
	private HashMap<Integer, AlchemyProbStruct> load(){
		HashMap<Integer, AlchemyProbStruct> sequences = new HashMap<Integer, AlchemyProbStruct>(8);
		Selector.exec("select * from tb_alchemy_probability", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					AlchemyProbStruct struct = AlchemyProbStruct.newInstance(rs);
					sequences.put(struct.get_alchemy_id(), struct);
				}
			}
		});
		return sequences;
	}

	public Integer select_id(int alchemy_id){
		return AlchemyProbabilityBox.getInstance().nextCurrentAlchemyId(alchemy_id);	
//		return _alchemy_structs.get(alchemy_id).select_id();
	}
	
	public boolean is_winning(int alchemy_id, int amount){
		return _alchemy_structs.get(alchemy_id).is_winning(amount);
	}
	
	public void dispose(){
		if(_alchemy_structs != null){
			_alchemy_structs.clear();
			_alchemy_structs = null;
		}
	}
	
	public static class AlchemyProbStruct{
		public static AlchemyProbStruct newInstance(ResultSet rs) throws SQLException{
			return newInstance()
				.set_alchemy_id(rs.getInt("alchemy_id"))
				.set_probability_by_millions(rs.getString("success_probability_by_millions") == null ? null : (Integer[])ArrangeParser.parsing(rs.getString("success_probability_by_millions"), ",", ArrangeParseeFactory.createIntArrange()).result())
				.set_sequences((Integer[])ArrangeParser.parsing(rs.getString("sequence"), ",", ArrangeParseeFactory.createIntArrange()).result());
		}
		
		public static AlchemyProbStruct newInstance(){
			return new AlchemyProbStruct();
		}
		
		private int _alchemy_id;
		private Integer[] _probability_by_millions;
		private Integer[] _sequences;
		
		public AlchemyProbStruct set_alchemy_id(int alchemy_id){
			_alchemy_id = alchemy_id;
			return this;
		}
		
		public int get_alchemy_id(){
			return _alchemy_id;
		}
		
		public AlchemyProbStruct set_probability_by_millions(Integer[] probability_by_millions){
			_probability_by_millions = probability_by_millions;
			return this;
		}
		
		public Integer[] get_probability_by_millions(){
			return _probability_by_millions;
		}
		
		public AlchemyProbStruct set_sequences(Integer[] sequences){
			_sequences = sequences;
			return this;
		}
		
		public Integer[] get_sequences(){
			return _sequences;
		}
		
		public Integer select_id(){
			for(Integer id : _sequences){
				if(GameRandom.isBoolean())
					return id;
			
			}
			return _sequences[GameRandom.next(_sequences.length)];
		}
		
		public boolean is_winning(int amount){
			return GameRandom.isWinning(1000000, _probability_by_millions[amount - 1]);
		}
	}
}