package l1j.server.server.task.automation;

import java.util.ArrayList;

public class ServerMacroObject implements Comparable<ServerMacroObject>{
	private static final int MACRO_TYPE_SYS 	= 0;
	private static final int MACRO_TYPE_ICO 	= 1;
	private static final int MACRO_TYPE_GRN 	= 2;
	private static final int MACRO_TYPE_NTCE 	= 3;
	private static final int MACRO_TYPE_WHSP 	= 4;
	
	public int							id;
	public int							curIdx;
	public ArrayList<String> 	pcks;
    private int[] packetTypes = new int[8];
    private int selectedType;
	public long							delay;
	public long							nextMs;
	public ServerMacroObject(int i, long d, long cur){
		id 		= i;
		delay	= d;
		nextMs	= cur + d;
		pcks	= new ArrayList<String>();
		curIdx	= 0;
	}
	
	public void add(String msg, String stype){
		int type = 0;
		if(stype.equalsIgnoreCase("아이콘"))
			type = MACRO_TYPE_ICO;
		else if(stype.equalsIgnoreCase("그린"))
			type = MACRO_TYPE_GRN;
		else if(stype.equalsIgnoreCase("공지"))
			type = MACRO_TYPE_NTCE;
		else if(stype.equalsIgnoreCase("귓말"))
			type = MACRO_TYPE_WHSP;
		
		if(type == MACRO_TYPE_SYS)
            pcks.add(msg);
		else if(type == MACRO_TYPE_ICO)
			pcks.add(msg);
		else if(type == MACRO_TYPE_GRN)
            pcks.add(msg);
		else if(type == MACRO_TYPE_NTCE)
			pcks.add(msg);
		else if(type == MACRO_TYPE_WHSP)
			pcks.add(msg);
        if (pcks.size() > packetTypes.length) packetTypes = java.util.Arrays.copyOf(packetTypes, packetTypes.length << 1);
        packetTypes[pcks.size() - 1] = type;
	}
	
    public int getSelectedType() { return selectedType; }
	public String get(){
		String sbp = pcks.get(curIdx);
        selectedType = packetTypes[curIdx];
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
