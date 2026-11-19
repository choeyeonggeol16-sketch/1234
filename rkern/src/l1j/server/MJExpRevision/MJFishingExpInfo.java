package l1j.server.MJExpRevision;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.utils.IntRange;

public class MJFishingExpInfo {
	private static HashMap<Integer, ArrayList<MJFishingExpInfo>> m_fishing_exps;
	
	public static void do_load(){
		HashMap<Integer, ArrayList<MJFishingExpInfo>> fishing_exps = new HashMap<Integer, ArrayList<MJFishingExpInfo>>();
		Selector.exec("select * from fishing_exp_info", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJFishingExpInfo o = newInstance(rs);
					ArrayList<MJFishingExpInfo> list = fishing_exps.get(o.get_fishing_type().to_val());
					if(list == null){
						list = new ArrayList<MJFishingExpInfo>();
						fishing_exps.put(o.get_fishing_type().to_val(), list);
					}
					list.add(o);
				}
			}
		});
		m_fishing_exps = fishing_exps;
	}
	
	public static MJFishingExpInfo find_fishing_exp_info(MJEFishingType type, int level){
		ArrayList<MJFishingExpInfo> list = m_fishing_exps.get(type.to_val());
		if(list == null)
			return null;
		
		for(MJFishingExpInfo fInfo : list){
			if(IntRange.includes(level, fInfo.get_level_min(), fInfo.get_level_max()))
				return fInfo;
		}
		return null;
	}

	private static MJFishingExpInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_fishing_type(MJEFishingType.from_name(rs.getString("fishing_type")))
				.set_level_min(rs.getInt("level_min"))
				.set_level_max(rs.getInt("level_max"))
				.set_default_exp(rs.getDouble("default_exp"))
				.set_addition_exp(rs.getDouble("addition_exp"))
				.set_ain_ration(rs.getDouble("ain_ration"));
	}

	private static MJFishingExpInfo newInstance(){
		return new MJFishingExpInfo();
	}

	private MJEFishingType m_fishing_type;
	private int m_level_min;
	private int m_level_max;
	private double m_default_exp;
	private double m_addition_exp;
	private double m_ain_ration;
	private MJFishingExpInfo(){}

	public MJFishingExpInfo set_fishing_type(MJEFishingType fishing_type){
		m_fishing_type = fishing_type;
		return this;
	}
	public MJFishingExpInfo set_level_min(int level_min){
		m_level_min = level_min;
		return this;
	}
	public MJFishingExpInfo set_level_max(int level_max){
		m_level_max = level_max;
		return this;
	}
	public MJFishingExpInfo set_default_exp(double default_exp){
		m_default_exp = default_exp;
		return this;
	}
	public MJFishingExpInfo set_addition_exp(double addition_exp){
		m_addition_exp = addition_exp;
		return this;
	}
	public MJFishingExpInfo set_ain_ration(double ain_ration){
		m_ain_ration = ain_ration;
		return this;
	}
	public MJEFishingType get_fishing_type(){
		return m_fishing_type;
	}
	public int get_level_min(){
		return m_level_min;
	}
	public int get_level_max(){
		return m_level_max;
	}
	public double get_default_exp(){
		return m_default_exp;
	}
	public double get_addition_exp(){
		return m_addition_exp;
	}
	public double get_ain_ration(){
		return m_ain_ration;
	}

}

