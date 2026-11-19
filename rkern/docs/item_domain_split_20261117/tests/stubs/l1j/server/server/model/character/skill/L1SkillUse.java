package l1j.server.server.model.character.skill;
import l1j.server.server.model.Instance.L1PcInstance;
public final class L1SkillUse {
 public static final int TYPE_GMBUFF=7;
 public static void cast(L1PcInstance pc,int spell,int id,int x,int y,Object other,int time,int type){
  L1PcInstance.events.append("CAST:").append(spell).append(':').append(id).append(':').append(x).append(':').append(y).append(':').append(type).append('|');}
}
