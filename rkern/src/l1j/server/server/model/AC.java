package l1j.server.server.model;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;

import l1j.server.MJTemplate.MJL1Type;
import l1j.server.server.model.Instance.L1PcInstance;

import l1j.server.server.utils.IntRange;

public class AC {
	private L1Character _cha;
	
	private int ac = 0;

	private int baseAc = 0;

	public AC(L1Character cha){
		_cha = cha;
	}
	
	public int getAc() {
		return ac;
	}

	public void addAc(int i) {
		setAc(baseAc + i);
	}

	public void setAc(int i) {
		baseAc = i;
		ac = IntRange.ensure(i, -999, 999);
		if(_cha != null){
			boolean isChangedDG = false;
			boolean isChangedER = false;
			int decreaseAC = -ac - 100;
			if(decreaseAC >= 0){
				int effectedValue = ((decreaseAC / 10) + 1);
				isChangedDG = _cha.setCharacterDG(effectedValue);	
				isChangedER = _cha.setCharacterER(effectedValue);
			}else{
				isChangedDG = _cha.setCharacterDG(0);	
				isChangedER = _cha.setCharacterER(0);
			}
			if((isChangedDG || isChangedER) && _cha.instanceOf(MJL1Type.L1TYPE_PC)){
				SC_OwnCharAttrDef.send((L1PcInstance)_cha);	
			}
		}
	}
}
