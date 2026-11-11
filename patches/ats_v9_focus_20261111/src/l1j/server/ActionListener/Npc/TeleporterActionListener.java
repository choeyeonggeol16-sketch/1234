package l1j.server.ActionListener.Npc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import l1j.server.ActionListener.ActionListener;
import l1j.server.DTSSystem.MJPoint;
import l1j.server.Template.LORnd;
import l1j.server.Template.ArrangeHelper.MJArrangeParseeFactory;
import l1j.server.Template.ArrangeHelper.MJArrangeParser;
import l1j.server.Templates.L1Item;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

public class TeleporterActionListener extends NpcActionListener{
	public static TeleporterActionListener newInstance(NpcActionListener listener, ResultSet rs) throws SQLException{
		return newInstance(listener)
				.set_destination_name(rs.getString("destination_name"))
				.set_teleport_x(rs.getInt("teleport_x"))
				.set_teleport_y(rs.getInt("teleport_y"))
				.set_teleport_map_id(rs.getString("teleport_map_id"))
				.set_range(rs.getInt("range"));
	}
		
	public static TeleporterActionListener newInstance(NpcActionListener listener){
		return (TeleporterActionListener) newInstance().drain(listener);
	}
	
	public static TeleporterActionListener newInstance(){
		return new TeleporterActionListener();
	}
	
	private String _destination_name;
	private int _teleport_x;
	private int _teleport_y;
	private short[] _teleport_map_id;
	private int _range;
	private ArrayList<MJPoint> passables;
	protected TeleporterActionListener(){}
	
	@Override
	public ActionListener deep_copy(){
		return deep_copy(newInstance());
	}
	
	@Override
	public ActionListener drain(ActionListener listener){
		if(listener instanceof TeleporterActionListener){
			TeleporterActionListener t_listener = (TeleporterActionListener) listener;
			set_destination_name(t_listener.get_destination_name());
			set_teleport_x(t_listener.get_teleport_x());
			set_teleport_y(t_listener.get_teleport_y());
			set_teleport_map_id(t_listener.get_teleport_map_id());
			set_range(t_listener.get_range());
		}
		return super.drain(listener);
	}
	
	@Override
	public void dispose(){
		super.dispose();
		_destination_name = null;
	}
	
	public TeleporterActionListener set_destination_name(String destination_name){
		_destination_name = destination_name;
		return this;
	}
	
	public String get_destination_name(){
		return _destination_name;
	}
	
	public TeleporterActionListener set_teleport_x(int teleport_x){
		_teleport_x = teleport_x;
		return this;
	}
	
	public int get_teleport_x(){
		return _teleport_x;
	}
	
	public TeleporterActionListener set_teleport_y(int teleport_y){
		_teleport_y = teleport_y;
		return this;
	}
	
	public int get_teleport_y(){
		return _teleport_y;
	}
	
	public TeleporterActionListener set_teleport_map_id(short[] teleport_map_id){
		_teleport_map_id = teleport_map_id;
		return this;
	}
		
	public TeleporterActionListener set_teleport_map_id(String teleport_map_ids){
		Integer[] ids = (Integer[])MJArrangeParser.parsing(teleport_map_ids, ",", MJArrangeParseeFactory.createIntArrange()).result();
		
		int size = ids.length;
		_teleport_map_id = new short[size];
		for(int i= size - 1; i>=0; --i)
			_teleport_map_id[i] = ids[i].shortValue();
		return this;
	}
	
	public short[] get_teleport_map_id(){
		return _teleport_map_id;
	}
	
	public TeleporterActionListener set_range(int range){
		_range = range;
		return this;
	}
	
	public int get_range(){
		return _range;
	}
	
	@Override
	public String result_un_opened(L1PcInstance pc){
		//pc.sendPackets(String.format("%s은(는) 현재 오픈 중이지 않습니다.", get_destination_name()));
		pc.sendPackets("입장이 불가능한 상태 입니다.");
		// 입장 할 수 없습니다.
		//pc.sendPackets(1229);
		return super.result_un_opened(pc);
	}
	
	@Override
	public String result_short_level(L1PcInstance pc){
		pc.sendPackets(String.format("%s은(는) %d레벨 부터 입장이 가능합니다.", get_destination_name(), get_need_level())); 
		//입장 불가: 레벨이 맞지 않음
		//pc.sendPackets(1287);
		return super.result_short_level(pc);
	}
	
	@Override
	public String result_no_buff(L1PcInstance pc){
		pc.sendPackets("입장 조건을 충족하지 않습니다.");
		return super.result_no_buff(pc);
	}
	
	@Override
	public String result_no_pc_buff(L1PcInstance pc){
		pc.sendPackets("PC방 이용권을 사용중에만 사용 가능한 행동입니다.");
		return super.result_no_pc_buff(pc);
	}
	@Override
	public String result_Check_Item(L1PcInstance pc){
		pc.sendPackets("입장에 필요한 아이템이 없습니다.");
		return super.result_no_pc_buff(pc);
	}
	
	@Override
	public String result_no_need_wanted(L1PcInstance pc){
		pc.sendPackets("수배 상태에서만 입장이 가능한 사냥터 입니다."); 
		return super.result_no_need_wanted(pc);
	}
	
	
	
	@Override
	public String result_short_item(L1PcInstance pc){
		L1Item item = ItemTable.getInstance().getTemplate(get_need_item_id());
		int needAmount = get_need_item_amount();
		if(needAmount > 0)
//			pc.sendPackets(new S_ServerMessage(5359));
			pc.sendPackets(String.format("%s %d이(가) 부족합니다.", item.getName(), get_need_item_amount()));
		else
			pc.sendPackets(String.format("%s이(가) 없습니다.", item.getName()));
		return super.result_short_item(pc);
	}
	
	@Override
	public String result_success(L1PcInstance pc){
		MJPoint pt = null;
		if(get_teleport_map_id()[0] == 5490) {
			pt = MJPoint.newInstance();
			pt.x = get_teleport_x();
			pt.y = get_teleport_y();
			pt.mapId = get_teleport_map_id()[0];
		}else {
			makePassableMap();
			pt = passables.get(LORnd.next(passables.size()));
		}
		
		pc.teleport(pt.x, pt.y, pt.mapId, pc.getHeading(), 169, true, false);	
		return super.result_success(pc);
	}
	
	public void makePassableMap(){
		if(passables == null){
			L1Map map = L1WorldMap.getInstance().getMap(get_teleport_map_id()[0]);
			passables = new ArrayList<MJPoint>(get_range() * get_range());
			int r = get_range();
			int minX = get_teleport_x() - r;
			int maxX = get_teleport_x() + r;
			int minY = get_teleport_y() - r;
			int maxY = get_teleport_y() + r;
			for(int x = maxX; x>=minX; --x){
				for(int y = maxY; y>=minY; --y){
					if(MJPoint.isValidPosition(map, x, y)){
						MJPoint pt = MJPoint.newInstance();
						pt.x = x;
						pt.y = y;
						pt.mapId = (short)map.getId();
						passables.add(pt);
					}
				}
			}
		}
	}
	
	public boolean simple_teleport(L1PcInstance pc){
		makePassableMap();
		MJPoint pt = passables.get(LORnd.next(passables.size()));
		 
		pc.teleport(pt.x, pt.y, pt.mapId, pc.getHeading(), 169, true, false);	
		return true;
		 
	}
}
