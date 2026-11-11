package l1j.server.ActionCodes;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.Template.Frame.FrameElement;
/** 
 * ActionInformation
 * made by mjsoft, 2017.
 **/
public class ActionInformation {
	public static ActionInformation fromBasicAction(int actId){
			return fromBasicAction(EActionCodes.fromInt(actId));
	}
	
	public static ActionInformation fromBasicAction(EActionCodes actionCode){
		return create(actionCode.toInt(), actionCode.getAmount(), 24D);
	}
	
	public static ActionInformation create(ResultSet rs) throws SQLException{
		return create(rs.getInt("act_id"), rs.getDouble("framecount"), rs.getDouble("framerate"));
	}
	
	public static ActionInformation create(int actId, double framecount, double framerate){
		
		return create(actId, FrameElement.fromRate(framecount, framerate));
	}
	
	public static ActionInformation create(int actId, FrameElement frame){
		ActionInformation info = new ActionInformation();
		info.actId = actId;
		info.frame = frame;
		return info;
	}
	
	private int 			actId;
	private FrameElement	frame;
	private Double[]		userFrames;
	private ActionInformation(){}
	
	public int getActionId(){
		return actId;
	}
	
	public boolean isUserFrames(){
		return userFrames != null;
	}
	
	public void setUserFrames(Double[] frames){
		userFrames = frames;
	}
	
	public FrameElement getFrame(){
		return frame;
	}
	
	public Double getFramePerSecond(){
		return frame.getFramePerSeocnd();
	}
	
	public Double getFramePerSecond(double rps){
		return frame.getFramePerSecond(rps);
	}
	
	public Double getFramePerSecond(int idx){
		return userFrames == null ? getFramePerSecond() : frame.getFramePerSecond(userFrames[idx].doubleValue());
	}
}
