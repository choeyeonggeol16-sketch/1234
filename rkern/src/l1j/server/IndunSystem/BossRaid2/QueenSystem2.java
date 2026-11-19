package l1j.server.IndunSystem.BossRaid2;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class QueenSystem2 {
	
	private static QueenSystem2 _instance;
	private final ArrayList<Integer> _map = new ArrayList<Integer>();
	private final Map<Integer, Queen1> _list = new ConcurrentHashMap<Integer, Queen1>();
	private final Map<Integer, Integer> _clanlist = new ConcurrentHashMap<Integer, Integer>();
	private final Map<Integer, Integer> _clanid = new ConcurrentHashMap<Integer, Integer>();

	public static QueenSystem2 getInstance() {
		if (_instance == null) {
			_instance = new QueenSystem2();
		}
		return _instance;
	}

	public QueenSystem2(){
		_map.add(2101);
	} 

	public void startQueen(L1PcInstance pc){
		if(countQueen() >= 49){
			SC_ChatPacket.sendDirect(pc, pc, "인스턴스 던전에 진입한 인원이 너무 많습니다");
			return;
		}
		int id = blankMapId();
		
		if(id != 2101)
			L1WorldMap.getInstance().cloneMap(2151, id);
		Queen1 queen = new Queen1(id);
		try(var accepted=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_SystemMessage.nativeWriter("3초 후에 클랜레이드 맵으로 이동합니다."),
                Integer.MIN_VALUE,Integer.MAX_VALUE);
            var refused=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_SystemMessage.nativeWriter("같은 혈원이 아니기에 입장에서 제외 됩니다."),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance partymember:pc.getParty().getMembers()){
                if(pc.getClanid()==partymember.getClanid()){
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(accepted,partymember);
                } else {
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(refused,partymember);
                }
            }
        }
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try(var refused=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_SystemMessage.nativeWriter("같은 혈원이 아니기에 입장에서 제외 됩니다."),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance partymember:pc.getParty().getMembers()){
                if(pc.getClanMemberId()==partymember.getClanMemberId()
                        && partymember.getClanid()==pc.getClanid()){
                    partymember.start_teleport(32728,32811,(short)id,5,169,false,false);
                } else {
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(refused,partymember);
                }
            }
        }
		_list.put(id, queen);
		queen.Start();
	} 


	public int blankMapId(){
		if(_list.size() == 0)
			return 2101;
		for(int i = 2101 ; i <= 2150; i++){
			Queen1 h = _list.get(i);
			if(h == null)
				return i;
		}  
		return 2150;
	}
	public int getAzmodanClanid(int id) {
		return _clanlist.get(id);
	}
	public int getClanid(int id) {
		return _clanid.get(id);
	}
	public Queen1 getQueen(int id){
		return _list.get(id);
	}
	

	public void removeQueen(int id){
		_list.remove(id);
		_clanid.remove(id);
	}


	public int countQueen(){
		return _list.size();
	}
	private static boolean _qeensgo = false;
	
	public static boolean getqeensgo() {
		return _qeensgo;
	}
	
	public static void setqeensgo(boolean s) {
		_qeensgo = s;
	}
}
