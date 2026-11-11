package l1j.server.SurveySystem;

import l1j.server.DTSSystem.MJPoint;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;

public class SurveyFactory {
	private static final S_SystemMessage _isOverEvent = new S_SystemMessage("이미 종료된 이벤트입니다.");
	
	public static InterfaceSurvey createObjectCallSurvey(){
		return new InterfaceSurvey(){
			@Override
			public void survey(L1PcInstance pc, int num, boolean isYes) {
				if(!isYes)
					return;
				
				L1Object obj = L1World.getInstance().findObject(num);
				if(obj == null){
					pc.sendPackets(_isOverEvent, false);
					return;
				}
				
				if(obj instanceof L1Character){
					L1Character c = (L1Character)obj;
					if(c.isDead()){
						pc.sendPackets(_isOverEvent, false);
						return;						
					}
				}
				
				MJPoint pt = MJPoint.newInstance(obj.getX(), obj.getY(), 3, obj.getMapId()); 
				pc.teleport(pt.x, pt.y, pt.mapId, pc.getHeading(),169, false, false);
			}
		};
	}
}
