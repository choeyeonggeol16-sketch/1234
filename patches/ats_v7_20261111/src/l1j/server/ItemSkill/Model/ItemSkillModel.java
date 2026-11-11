package l1j.server.ItemSkill.Model;

import java.util.Random;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.serverpackets.ServerBasePacket;
import l1j.server.server.utils.CommonUtil;

public abstract class ItemSkillModel {
	protected static Random _rnd = new Random(System.nanoTime());
	public int itemId;           // 아이템 아이디
	public int d_prob;           // 기본 확률
	public int e_prob;           // 인챈트 확률
	public int min_dmg;          // 최소
	public int max_dmg;          // 최대
	public int in_dmg;           // 고유 데미지
	public int eff_id;           // 이팩트 번호
	public int attr_type;        // 속성 타입
	public boolean is_cri;       // 크리티컬 유무
	public boolean is_magic;     // 마법 유무
	public boolean is_erase;     // 이레이즈 삭제 유무
	public boolean is_counter;   // 카운터 매직 삭제 유무
	public int skill_range;      // 광역 마법검 스킬 거리 
	public int effectType;       // 이팩트 종류 타입
	
	public abstract double get(L1Character paramL1Character1, L1Character paramL1Character2, ItemInstance paramL1ItemInstance, double paramDouble);

    /**
     * 마법검 발동 확률만 계산한다.
     * @param c
     * @param t_item
     * @return
     */
	protected boolean isProbability(L1Character c, ItemInstance t_item) {
		int 확률 = 0;
		if(e_prob <= 0) {       
			확률 += d_prob;
		} else if(e_prob > 0) {
			확률 += (int) (t_item.getEnchantLevel() * e_prob) + d_prob;
		} 	
		if(CommonUtil.randomRange(1, 100) <= 확률) {
			return true;
		}
		return false;
	}
	
	/**
	 * 모두에게 패킷을 보냄 
	 * @param c
	 * @param pck
	 */
	protected void broadcast(L1Character c, ServerBasePacket pck) {
		c.sendPackets(pck, false);
		c.broadcastPacket(pck);
	}
	/**
	 * 공격 체크 
	 * @return
	 */
	public boolean isAttack() {
		return true;
	}
}
