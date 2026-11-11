package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.COUNTER_MAGIC;
import java.util.Random;
import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.poison.L1DamagePoison;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import static l1j.server.server.model.item.function.ItemUseCreature.*;

/**
 * Legacy combat effects grouped by responsibility. 
 */
final class ItemUseCombatSupport {
    private static final Random _random = ItemUseCreature._random;
    
    private ItemUseCombatSupport() {
    }
    
    public static int targetDirection(L1PcInstance pc, int tx, int ty) {
        float dis_x = Math.abs(pc.getX() - tx);
        float dis_y = Math.abs(pc.getY() - ty);
        float dis = Math.max(dis_x, dis_y);
        if (dis == 0) return pc.getHeading();
        int avg_x = (int)Math.floor((dis_x / dis) + 0.59F);
        int avg_y = (int)Math.floor((dis_y / dis) + 0.59F);
        int dir_x = 0;
        int dir_y = 0;
        if (pc.getX() < tx) dir_x = 1;
        if (pc.getX() > tx) dir_x = -1;
        if (pc.getY() < ty) dir_y = 1;
        if (pc.getY() > ty) dir_y = -1;
        if (avg_x == 0) dir_x = 0;
        if (avg_y == 0) dir_y = 0;
        if (dir_x == 1 && dir_y == -1) return 1;
        if (dir_x == 1 && dir_y == 0) return 2;
        if (dir_x == 1 && dir_y == 1) return 3;
        if (dir_x == 0 && dir_y == 1) return 4;
        if (dir_x == -1 && dir_y == 1) return 5;
        if (dir_x == -1 && dir_y == 0) return 6;
        if (dir_x == -1 && dir_y == -1) return 7;
        if (dir_x == 0 && dir_y == -1) return 0;
        return pc.getHeading();
    }
    
    public static void Start(L1PcInstance pc, L1Character cha) {
        ThreadPool.getInstance().scheduleRequired(new Runnable(){
            
            @Override
            public void run() {
                if (!cha.hasSkillEffect(L1SkillId.COUNTER_MAGIC)) {
                    if (cha.hasSkillEffect(L1SkillId.리버레이션)) {
                        int 요정 = (int)((double)Config.이터제약 * Config.리버레이션절감);
                        L1DamagePoison.doInfection(pc, cha, 1000, 요정, true, 1);
                        SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage);
                    } else {
                        int B = _random.nextInt(Config.이터이차최대 - Config.이터이차최소) + Config.이터이차최소;
                        L1DamagePoison.doInfection(pc, cha, 1000, B, true, 1);
                        SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage);
                    }
                }
            }
        }, Config.이터제약);
    }
    
    public static void Start1(L1PcInstance pc, L1NpcInstance member1) {
        ThreadPool.getInstance().scheduleRequired(new Runnable(){
            
            @Override
            public void run() {
                if (!member1.hasSkillEffect(L1SkillId.COUNTER_MAGIC)) {
                    int B = _random.nextInt(Config.이터이차최대 - Config.이터이차최소) + Config.이터이차최소;
                    L1DamagePoison.doInfection(pc, member1, 1000, B, true, 1);
                }
            }
        }, Config.이터제약);
    }
    
    public static void 액션(L1PcInstance pc, L1Character cha) {
        ThreadPool.getInstance().scheduleRequired(new Runnable(){
            
            @Override
            public void run() {
                SC_DoActionGFX.broadcast(cha, ActionCodes.ACTION_Damage);
                if (cha instanceof L1PcInstance) {
                    final L1PcInstance targetPc = (L1PcInstance)cha;
                    SC_DoActionGFX.send(targetPc.getNetConnection(), targetPc.getId(), ActionCodes.ACTION_Damage);
                }
            }
        }, 500);
    }
    
    public static void 액션1(L1PcInstance pc, L1NpcInstance member1) {
        ThreadPool.getInstance().scheduleRequired(new Runnable(){
            
            @Override
            public void run() {
                SC_DoActionGFX.broadcast(member1, ActionCodes.ACTION_Damage);
            }
        }, 500);
    }
}
