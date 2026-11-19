package l1j.server.MJ3SEx;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import l1j.server.MJ3SEx.Loader.SpriteInformationLoader;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;

/** 
 * SpriteInformation
 **/
public class SpriteInformation {
    public static SpriteInformation create(ResultSet rs) throws SQLException{
        SpriteInformation info  = new SpriteInformation();
        info.sprId              = rs.getInt("spr_id");
        info.width              = rs.getInt("width");
        info.height             = rs.getInt("height");
        info.numOfAction        = rs.getInt("action_count");
        info.initialize();
        return info;
    }
    
    private int sprId;
    private int width;
    private int height;
    private int numOfAction;
    
    private static final int MAX_ACTIONS = 256;
    private static final ActionInformation[] NO_ACTIONS = new ActionInformation[0];
    // Shared sprite metadata is read by many actors. Publish complete tables;
    // retain direct O(1) lookup without 256 references for every sprite.
    private volatile ActionInformation[] actions;
    
    private SpriteInformation(){}
    
    private void initialize(){
        actions = NO_ACTIONS;
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
    
    public synchronized void put(ActionInformation aInfo){
        int actId = aInfo.getActionId();
        if (actId >= 0 && actId < MAX_ACTIONS) {
            ActionInformation[] current = actions;
            ActionInformation[] next = Arrays.copyOf(current,
                    Math.max(current.length, actId + 1));
            next[actId] = aInfo;
            actions = next;
        }
    }
    
    public ActionInformation get(int actId){
        ActionInformation[] current = actions;
        if (actId < 0 || actId >= current.length) {
            return null;
        }
        return current[actId];
    }

    private ActionInformation getOrCreateBasicAction(EActionCodes actionCode){
        ActionInformation aInfo = get(actionCode.toInt());
        if (aInfo == null) {
            synchronized (this) {
                aInfo = get(actionCode.toInt());
                if (aInfo == null) {
                    aInfo = ActionInformation.fromBasicAction(actionCode);
                    put(aInfo);
                }
            }
        }
        return aInfo;
    }
    
    public void registerUserActions(int actId, Double[] rates){
        ActionInformation aInfo = get(actId);
        if (aInfo == null) {
            aInfo = getOrCreateBasicAction(EActionCodes.fromInt(actId));
        }
        aInfo.setUserFrames(rates);
    }
    
    public void dispose(){
        actions = null;
    }
    
    public double getInterval(L1Character c, int actionCode){
        return getInterval(c, EActionCodes.fromInt(actionCode));
    }
    
    public double getInterval(L1Character c, EActionCodes actionCode){
        ActionInformation aInfo = getOrCreateBasicAction(actionCode);
        
        return actionCode.decoration(c, aInfo.getFramePerSecond().doubleValue());       
    }
    
    public double getInterval(L1PcInstance pc, int actionCode){
        return getInterval(pc, EActionCodes.fromInt(actionCode));
    }
    
    public double getInterval(L1PcInstance pc, EActionCodes actionCode){
        if(sprId == 12015 || sprId == 5641 || sprId == 11685|| sprId == 14491){
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
        
        ActionInformation aInfo = getOrCreateBasicAction(actionCode);
        return actionCode.decorationForPc(pc, aInfo.getFramePerSecond(SpriteInformationLoader.levelToIndex(pc.getLevel(), pc.getCurrentSpriteId())).doubleValue());       
    }
}
