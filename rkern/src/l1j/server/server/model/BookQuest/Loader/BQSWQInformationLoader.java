package l1j.server.server.model.BookQuest.Loader;

import java.sql.ResultSet;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.BookQuest.BQSWQInformation;

public class BQSWQInformationLoader {
	private static BQSWQInformationLoader _instance;
	public static BQSWQInformationLoader getInstance(){
		if(_instance == null)
			_instance = new BQSWQInformationLoader();
		return _instance;
	}
	
	public static void reload(){
		BQSWQInformationLoader old = _instance;
		_instance = new BQSWQInformationLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}
	
	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}
	
	private BQSWQInformation[] _wq_informations;
	private BQSWQInformationLoader(){
		_wq_informations = loadWqInformations();
	}
	
	private BQSWQInformation[] loadWqInformations(){
		BQSWQInformation[] wq_informations = new BQSWQInformation[128];
		Selector.exec("select * from tb_mbook_wq_information order by start_level desc", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				int latest_level = 127;
				while(rs.next()){
					BQSWQInformation wqInfo = BQSWQInformation.newInstance(rs);
					int start_level = wqInfo.get_start_level();
					for(int i=latest_level; i>= start_level; --i)
						wq_informations[i] = wqInfo;
					latest_level = start_level - 1;
				}
			}
		});
		return wq_informations;
	}
	
	public BQSWQInformation get(int level){
		return _wq_informations[level];
	}
	
	public void dispose(){
		if(_wq_informations != null){
			int len = _wq_informations.length;
			for(int i = len - 1; i>=0; --i)
				_wq_informations[i] = null;
			_wq_informations = null;
		}
	}
}
