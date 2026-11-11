package l1j.server.ServerMacroSystem;

import java.util.ArrayList;

import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.ServerBasePacket;

public class ServerMacroObject implements Comparable<ServerMacroObject>{
	private static final int MACRO_TYPE_SYS 	= 0;
	private static final int MACRO_TYPE_GRN 	= 2;

	
	public int							id;
	public int							curIdx;
	public ArrayList<ServerBasePacket> 	pcks;
	public long							delay;
	public long							nextMs;
	public ServerMacroObject(int i, long d, long cur){
		id 		= i;
		delay	= d;
		nextMs	= cur + d;
		pcks	= new ArrayList<ServerBasePacket>();
		curIdx	= 0;
	}
	
	public void add(String msg, String stype){
		int type = 0;
		if(stype.equalsIgnoreCase("그린"))
			type = MACRO_TYPE_GRN;  
		
		
		if(type == MACRO_TYPE_SYS)
			pcks.add(new S_SystemMessage(msg));
		else if(type == MACRO_TYPE_SYS)
			pcks.add(new S_ChatPacket(msg, 1));
		 
		else if(type == MACRO_TYPE_GRN)
			pcks.add(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, msg));   
	}
	
	public ServerBasePacket get(){
		ServerBasePacket sbp = pcks.get(curIdx);
		if(++curIdx >= pcks.size())	curIdx = 0;
		return sbp;
	}

	@Override
	public int compareTo(ServerMacroObject o) {
		if(nextMs > o.nextMs) 		return 1;
		else if(nextMs < o.nextMs)	return -1;
		if(id > o.id)				return 1;
		else if(id < o.id)			return -1;
		return 0;
	}
}
