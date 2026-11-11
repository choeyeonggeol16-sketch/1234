package l1j.server.server.model;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.utils.IntRange;


public class AC {
	private L1Character _cha;
	
	private int ac = 0; 
	private int baseAc = 0;
	
	public int getAc() { 
		return ac; 			
	}
	public void addAc(int i) { 
		setAc(baseAc + i); 
	}
	public void setAc(int i) { 
		baseAc = i;
		ac = IntRange.ensure(i, -255, 255);
		if(_cha != null) {
			if(_cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) _cha;
				pc.sendPackets(new S_OwnCharAttrDef(pc), true);
			}		
		}	
	}
}
