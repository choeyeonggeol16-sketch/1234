package l1j.server.ActionCodes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;

public class SpriteInformation {
	public static SpriteInformation create(ResultSet rs) throws SQLException{
		SpriteInformation info 	= new SpriteInformation();
		info.sprId 				= rs.getInt("spr_id");
		info.width 				= rs.getInt("width");
		info.height 			= rs.getInt("height");
		info.numOfAction 		= rs.getInt("action_count");
		info.initialize();
		return info;
	}
	
	private int sprId;
	private int width;
	private int height;
	private int numOfAction;
	private HashMap<Integer, ActionInformation> actions;
	private SpriteInformation(){}
	
	private void initialize(){
		actions = new HashMap<Integer, ActionInformation>(numOfAction);
	}
	
	public int getSpriteId(){
		return sprId;
	}
	
	public int getWidth(){
		return width;
	}
	
	public int getHeight(){
		return height;
	}
	
	public int getNumOfAction(){
		return numOfAction;
	}
	
	public void put(ActionInformation aInfo){
		actions.put(aInfo.getActionId(), aInfo);
	}
	
	public ActionInformation get(int actId){
		return actions.get(actId);
	}
	
	public void registerUserActions(int actId, Double[] rates){
		ActionInformation aInfo = get(actId);
		if(aInfo == null){
			aInfo = ActionInformation.fromBasicAction(actId);
			put(aInfo);
		}
		aInfo.setUserFrames(rates);
	}
	
	public void dispose(){
		if(actions != null){
			actions.clear();
			actions = null;
		}
	}
	
	public double getInterval(L1Character c, int actionCode){
		return getInterval(c, EActionCodes.fromInt(actionCode));
	}
	
	public double getInterval(L1Character c, EActionCodes actionCode) {
		if (sprId == 3479 || sprId == 3497 || sprId == 3498 || sprId == 3499 || sprId == 3500 || sprId == 3479
				|| sprId == 3501 || sprId == 3502 || sprId == 3503 || sprId == 3504 || sprId == 3480 || sprId == 3505
				|| sprId == 3506 || sprId == 3507 || sprId == 3508 || sprId == 3481 || sprId == 3509 || sprId == 3510
				|| sprId == 3511 || sprId == 3512) {
			if(actionCode.is_walk()){
				actionCode = EActionCodes.walk;
			}else if(actionCode.is_attack()){
				actionCode = EActionCodes.attack;
			}else if(actionCode.is_damage()){
				actionCode = EActionCodes.damage;
			}else if(actionCode.is_breath()){
				actionCode = EActionCodes.breath;
			}else if(actionCode.is_alt_attack()){
				actionCode = EActionCodes.alt_attack;
			}
		}
		ActionInformation aInfo = get(actionCode.toInt());
		if(aInfo == null){
			aInfo = ActionInformation.fromBasicAction(actionCode);
			put(aInfo);
		}
		return actionCode.decoration(c, aInfo.getFramePerSecond().doubleValue());		
	}
	
	public double getInterval(L1PcInstance pc, int actionCode){
		return getInterval(pc, EActionCodes.fromInt(actionCode));
	}
	
	public double getInterval(L1PcInstance pc, EActionCodes actionCode){
		if(sprId == 12015 || sprId == 5641 || sprId == 11685){
			if(actionCode.is_walk()){
				actionCode = EActionCodes.walk;
			}else if(actionCode.is_attack()){
				actionCode = EActionCodes.attack;
			}else if(actionCode.is_damage()){
				actionCode = EActionCodes.damage;
			}else if(actionCode.is_breath()){
				actionCode = EActionCodes.breath;
			}
		}

		ActionInformation aInfo = get(actionCode.toInt());
		if(aInfo == null){
			aInfo = ActionInformation.fromBasicAction(actionCode);
			put(aInfo);
		}
		return actionCode.decorationForPc(pc, aInfo.getFramePerSecond().doubleValue());		
		//return actionCode.decorationForPc(pc, aInfo.getFramePerSecond(SpriteInformationLoader.levelToIndex(pc.getLevel(), pc.getCurrentSpriteId())).doubleValue());		
	}
}
