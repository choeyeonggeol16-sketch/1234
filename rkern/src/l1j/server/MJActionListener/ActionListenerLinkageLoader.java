package l1j.server.MJActionListener;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class ActionListenerLinkageLoader{
	private static ActionListenerLinkageLoader _instance;
	public static ActionListenerLinkageLoader getInstance(){
		if(_instance == null)
			_instance = new ActionListenerLinkageLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		ActionListenerLinkageLoader old = _instance;
		_instance = new ActionListenerLinkageLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, ActionListenerLinkage> _linkages;
	private ActionListenerLinkageLoader(){
		_linkages = load();
	}
	
	private HashMap<Integer, ActionListenerLinkage> load(){
		HashMap<Integer, ActionListenerLinkage> linkages = new HashMap<Integer, ActionListenerLinkage>(8);
		Selector.exec("select * from tb_act_listener_linkage_item", new FullSelectorHandler(){

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					ActionListenerLinkage linkage = ActionListenerLinkage.newInstance(rs);
					linkages.put(linkage.get_item_id(), linkage);
				}
			}
			
		});
		return linkages;
	}

	public boolean use_item(L1PcInstance pc, L1ItemInstance item){
		ActionListenerLinkage linkage = _linkages.get(item.getItemId());
		if(linkage == null)
			return false;
		
		linkage.use_item(pc, item);
		return true;
	}

	public void dispose(){
		if(_linkages != null){
			_linkages.clear();
			_linkages = null;
		}
	}
}
