package l1j.server.server.model;

import java.util.ArrayDeque;
import java.util.ArrayList;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.collection.CollectionObject;
import l1j.server.server.model.item.collection.loader.CollectionSystemLoader;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;


//캐릭터 저장 구슬 조회 neige
public class L1NeigeSaveChar {
	private static final String start = ".구슬조회 이름\n";
	public static final String invalid = "캐릭터 정보를 찾을 수 없습니다.";
	private static ArrayDeque<String> parseToStringArray(String s){
		String[] arr 	= s.split(" ");
		int size		= arr.length;
		ArrayDeque<String> argsQ = new ArrayDeque<String>(size);
		for(int i=0; i<size; i++){
			try{
				argsQ.offer(arr[i]);
			}catch(Exception e){
				break;
			}
		}
		return argsQ;
	}
	public static void commands(L1PcInstance pc, String param){
		try{
			ArrayDeque<String> argsQ = parseToStringArray(param);
			if(argsQ == null || argsQ.isEmpty())
				throw new Exception("");
			pc.setFindchar(param);
			ArrayList<String> savechar = new ArrayList<String>();
		    if ((pc == null) || (pc.isDead())) {
			      return;
			    }
		    CollectionObject obj = CollectionSystemLoader.getInstance().get(param);
		    if(obj == null){
				SC_SystemMessage.send(pc, invalid);
				pc.setFindchar(null);
				return;
			}
		    savechar.add(param);
			SC_NPCTalkReturn.send(pc, pc.getId(), "nesavechar", (String[]) savechar.toArray(new String[savechar.size()]));
		}catch(Exception e){
			SC_SystemMessage.send(pc, start);
		}
	}
	public static void CharInfo(L1PcInstance pc, String Name){
		CollectionObject obj = CollectionSystemLoader.getInstance().get(Name);
		
		if(obj == null){
			SC_SystemMessage.send(pc, invalid);
			pc.setFindchar(null);
			return;
		}
		l1j.server.server.model.network.core.sc.packet.SC_ShowCharInfo.send(pc, obj);
			
	}
	public static void CharInventory(L1PcInstance pc, String Name){
		CollectionObject obj = CollectionSystemLoader.getInstance().get(Name);
		if(obj == null){
			SC_SystemMessage.send(pc, invalid);
			pc.setFindchar(null);
			return;
		}
		if(obj.invPck != null)
		     l1j.server.server.packets.Protocol.MainServer.Proto.Proto.sendRaw(pc, obj.invPck);
		pc.setFindcharno(1);
	}
	public static void CharSkill(L1PcInstance pc, String Name){
		CollectionObject obj = CollectionSystemLoader.getInstance().get(Name);
		if(obj == null){
			SC_SystemMessage.send(pc, invalid);
			pc.setFindchar(null);
			return;
		}
		if(obj.spPck != null)
		     l1j.server.server.packets.Protocol.MainServer.Proto.Proto.sendRaw(pc, obj.spPck);
		pc.setFindcharno(1);
	}
}
