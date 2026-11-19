package l1j.server.server.model.BookQuest;

import java.sql.ResultSet;

public class BQSWQInformation {
	public static BQSWQInformation newInstance(ResultSet rs) throws Exception{
		BQSWQInformation wqInfo = new BQSWQInformation();
		wqInfo._start_level = rs.getInt("start_level");
		wqInfo._completion_rate = rs.getDouble("completion_rate") * 0.01D;
		return wqInfo;
	}

	private int _start_level;
	private double _completion_rate;
	
	private BQSWQInformation(){}
	
	public int get_start_level(){
		return _start_level;
	}
	
	public int calc_amount(int amount){
		return calc_amount((double)amount);
	}
	
	public int calc_amount(double amount){
		return (int)(amount * _completion_rate);
	}
}
