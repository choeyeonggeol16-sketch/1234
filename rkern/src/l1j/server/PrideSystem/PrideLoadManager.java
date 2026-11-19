package l1j.server.PrideSystem;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.MJKDASystem.MJKDA;
import l1j.server.MJTemplate.MJObjectWrapper;
import l1j.server.MJTemplate.MJPropertyReader;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;

public class PrideLoadManager {
	private static PrideLoadManager _instance;
	public static PrideLoadManager getInstance(){
		if(_instance == null)
			_instance = new PrideLoadManager();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}
	
	public static void reload(){
		PrideLoadManager old = _instance;
		_instance = new PrideLoadManager();
		if(old != null){
			old.dispose();
			old = null;
		}
	}
	
	private ConcurrentHashMap<Integer, PrideInfo> m_prides;
	private PrideLoadManager(){
		load_config();
		m_prides = load();
	}
	
	private ConcurrentHashMap<Integer, PrideInfo> load(){
		final MJObjectWrapper<ConcurrentHashMap<Integer, PrideInfo>> wrapper = new MJObjectWrapper<ConcurrentHashMap<Integer, PrideInfo>>();
		Selector.exec("select * from pride_items", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				ConcurrentHashMap<Integer, PrideInfo> prides = new ConcurrentHashMap<Integer, PrideInfo>();
				while(rs.next()){
					PrideInfo pInfo = PrideInfo.newInstance(rs);
					prides.put(pInfo.get_object_id(), pInfo);
				}
				wrapper.value = prides;
			}
		});
		return wrapper.value;
	}
	
	public void put_pride(final PrideInfo pInfo){
		m_prides.put(pInfo.get_object_id(), pInfo);
		Updator.exec("insert into pride_items set object_id=?, target_object_id=?, target_name=?", new Handler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				int idx = 0;
				pstm.setInt(++idx, pInfo.get_object_id());
				pstm.setInt(++idx, pInfo.get_target_object_id());
				pstm.setString(++idx, pInfo.get_target_name());
			}
		});	
	}
	public void remove_pride(final PrideInfo pInfo){
		m_prides.remove(pInfo.get_object_id());
		Updator.exec("delete from pride_items where object_id=?", new Handler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception{
				int idx = 0;
				pstm.setInt(++idx, pInfo.get_object_id());
			}
		});
	}
	
	
	public static boolean USE_PRIDE_SYSTEM;
	public static int PRIDE_ITEM_ID;
	public static int NEED_ADENA_COUNT;
	private void load_config(){
		MJPropertyReader reader = null;
		try{
			reader = new MJPropertyReader("./config/pride_system.properties");
			USE_PRIDE_SYSTEM = reader.readBoolean("UseSystem", "false");
			PRIDE_ITEM_ID = reader.readInt("PrideItemId", "4100205");
			NEED_ADENA_COUNT = reader.readInt("NeedAdenaCount", "1000000");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(reader != null){
				reader.dispose();
				reader = null;
			}
		}
	}
	
	public PrideInfo get_pride(int object_id){
		return m_prides.get(object_id);
	}
	
	public void dispose(){
		if(m_prides != null){
			m_prides.clear();
			m_prides = null;
		}
	}
	
	public void on_kill(L1PcInstance target){
		if(!USE_PRIDE_SYSTEM)
			return;
		
		L1ItemInstance adena_item = target.getInventory().findItemId(L1ItemId.ADENA);
		if(adena_item == null || adena_item.getCount() <= NEED_ADENA_COUNT)
			return;
		
		L1ItemInstance item = ItemTable.getInstance().createItem(PRIDE_ITEM_ID);
		PrideInfo pInfo = PrideInfo.newInstance(item.getId(), target.getId(), target.getName());
		put_pride(pInfo);
		
		L1Inventory inv = L1World.getInstance().getInventory(target.getX(), target.getY(), target.getMapId());
		inv.storeTradeItem(item);
	}
	
	public boolean use_item(L1PcInstance pc, L1ItemInstance item){
		if(!USE_PRIDE_SYSTEM)
			return false;
		
		PrideInfo pInfo = get_pride(item.getId());
		if(pInfo == null)
			return false;
		
		if(pInfo.get_target_object_id() != pc.getId()){
			pc.sendPackets(String.format("해당 아이템은 %s님만 사용할 수 있습니다.", pInfo.get_target_name()));
			return true;
		}
		MJKDA kda = pc.getKDA();
		if(kda.death <= 0){
			pc.sendPackets("복구할 데스 수가 없습니다.");
			return true;			
		}
		remove_pride(pInfo);
		pc.getInventory().removeItem(item, 1);
		pc.sendPackets(String.format("데스가 1감소 했습니다.(현재 데스 : %d)", --kda.death));
		return true;
	}
}
