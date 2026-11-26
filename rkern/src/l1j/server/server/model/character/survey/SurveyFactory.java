package l1j.server.server.model.character.survey;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.geometry.GamePoint;

public class SurveyFactory {
	private static final String _isOverEvent = "이미 종료된 이벤트입니다.";
	
	
	public static InterfaceSurvey createObjectCallSurvey(){
		return new InterfaceSurvey(){
			@Override
			public void survey(L1PcInstance pc, int num, boolean isYes) {
				if(!isYes)
					return;
				
				L1Object obj = L1World.getInstance().findObject(num);
				if(obj == null){
					SC_SystemMessage.send(pc, _isOverEvent);
					return;
				}
				
				if(obj instanceof L1Character){
					L1Character c = (L1Character)obj;
					if(c.isDead()){
						SC_SystemMessage.send(pc, _isOverEvent);
						return;						
					}
				}
				
				GamePoint pt = GamePoint.newInstance(obj.getX(), obj.getY(), 3, obj.getMapId());
				pc.start_teleport(pt.x, pt.y, pt.mapId, pc.getHeading(), 169, true, false);
			}
		};
	}
}
