package l1j.server.server.model.character.combat.arena.stadium;

import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.model.Instance.L1PcInstance;

public class StadiumManager {
	private static final int[] m_index_map_mapped = new int[]{
		0, 
		13051,
		13052,
		13053,
		
	};
	private static final String[] m_index_name_mapped = new String[]{
			"없음", 
			"최후의결판(13051)",
			"아레나(13052)",
			"트랩전(13053)",
		};
	private static StadiumManager _instance;
	public static StadiumManager getInstance(){
		if(_instance == null)
			_instance =new StadiumManager();
		return _instance;
	}
	
	private ConcurrentHashMap<Integer, StadiumObject> m_stadiums;
	private StadiumManager(){
		m_stadiums = new ConcurrentHashMap<Integer, StadiumObject>();
	}
	
	public void regist(StadiumObject obj){
		m_stadiums.put(obj.get_current_play_map_id(), obj);
	}
	
	public void remove(StadiumObject obj){
		m_stadiums.remove(obj.get_current_play_map_id());
	}
	
	public boolean is_on_stadium(int map_id){
		StadiumObject obj = m_stadiums.get(map_id);
		return obj != null && obj.is_on();
	}
	
	public boolean is_in_stadium(int map_id){
		StadiumObject obj = m_stadiums.get(map_id);
		return obj != null;
	}
	
	public StadiumObject get_stadium(int map_id){
		return m_stadiums.get(map_id);
	}
	
	public void open_stadium(L1PcInstance gm, String param){
		if(param == null || param.equals("")){
			gm.sendPackets(".경기장 [경기장 번호]");
			int size = m_index_name_mapped.length;
			for(int i=1; i<size; ++i)
				gm.sendPackets(String.format("%d - %s : %s", i, m_index_name_mapped[i], is_on_stadium(m_index_map_mapped[i]) ? "경기중" : "대기중"));
			return;
		}
		
		StringTokenizer token = new StringTokenizer(param);
		int number = Integer.parseInt(token.nextToken());
		if(number <= 0 || number >= m_index_map_mapped.length){
			gm.sendPackets(String.format("%d 경기장은 존재하지 않습니다.", number));
			return;
		}
		
		int map_id = m_index_map_mapped[number];
		if(is_in_stadium(map_id)){
			gm.sendPackets(String.format("%d(%d) 경기장은 이미 진행중입니다.", number, map_id));
			return;			
		}
		StadiumObject obj = new StadiumObject(map_id, 10, 1800, m_index_name_mapped[number]);
		obj.execute();
	}
	
	
	public void quit_stadium(L1PcInstance gm, String param){
		StringTokenizer token = new StringTokenizer(param);
		int number = Integer.parseInt(token.nextToken());
		if(number <= 0 || number >= m_index_map_mapped.length){
			gm.sendPackets(String.format("%d 경기장은 존재하지 않습니다.", number));
			return;
		}
		
		int map_id = m_index_map_mapped[number];
		StadiumObject obj = get_stadium(map_id);
		if(obj == null || !obj.is_on()){
			gm.sendPackets(String.format("%d(%d) 경기장은 이미 종료되었거나 시작되지 않았습니다.", number, map_id));
			return;			
		}
		try {
			obj.do_ended();
			gm.sendPackets(String.format("%d(%d) %s경기가 종료되었습니다.", number, map_id, m_index_name_mapped[number]));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
