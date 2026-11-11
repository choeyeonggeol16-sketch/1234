package l1j.server.server.model.item.armor;

import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;


public class EquippedArmor {

	private L1PcInstance pc;
	public EquippedArmor(L1PcInstance temp) {
		this.pc = temp;
	}
	/**
	 * 갑옷
	 */
	private ItemInstance armor;
	public ItemInstance getArmor() {
		return armor;
	}
	public void setArmor(ItemInstance item) {
		this.armor = item;
	}

	private boolean 반역자_방패;

	public boolean isTraitorshield() {
		return 반역자_방패;
	}
	public void setTraitorshield(boolean b) {
		this.반역자_방패 = b;

	}
	private int 반역자_방패_인챈트;
	public int getTraitorShieldEn() {
		return 반역자_방패_인챈트;
	}
	public void setTraitorShieldEn(int i) {  
		this.반역자_방패_인챈트 = i;
	}

	private boolean 파푸리온_갑옷;

	public boolean isPapurionArmor() {
		return 파푸리온_갑옷;
	}
	public void setPapurionArmor(boolean b) {
		this.파푸리온_갑옷 = b;

	}
	private int 파푸리온_갑옷_인챈트;
	public int getPapurionArmorEn() {
		return 파푸리온_갑옷_인챈트;
	}
	public void setPapurionArmorEn(int i) {  
		this.파푸리온_갑옷_인챈트 = i;
	}
	private boolean 린드비오르_갑옷;

	public boolean isLindviorArmor() {
		return 린드비오르_갑옷;
	}
	public void setLindviorArmor(boolean b) {
		this.린드비오르_갑옷 = b;

	}
	private int 린드비오르_갑옷_인챈트;
	public int getLindviorArmorEn() {
		return 린드비오르_갑옷_인챈트;
	}
	public void setLindviorArmorEn(int i) {  
		this.린드비오르_갑옷_인챈트 = i;
	}
	////////////////// 발라카스 갑옷 원거리 ///////////////
	private boolean 발라카스_원거리;

	public boolean isValakasArmorRegend() {
		return 발라카스_원거리;
	}
	public void setValakasArmorRegend(boolean b) {
		this.발라카스_원거리 = b;

	}
	private int 발라카스_원거리_인챈트;
	public int getValakasArmorRegendEn() {
		return 발라카스_원거리_인챈트;
	}
	public void setValakasArmorRegendEn(int i) {  
		this.발라카스_원거리_인챈트 = i;
	}

	/////////////// 발라카스 갑옷 근거리///////////////
	private boolean 발라카스_근거리;

	public boolean isValakasArmorMelee() {
		return 발라카스_근거리;
	}
	public void setValakasArmorMelee(boolean b) {
		this.발라카스_근거리 = b;

	}
	private int 발라카스_근거리_인챈트;
	public int getValakasArmorMeleeEn() {
		return 발라카스_근거리_인챈트;
	}
	public void setValakasArmorMeleeEn(int i) {  
		this.발라카스_근거리_인챈트 = i;
	}
	/////////////// 발라카스 갑옷 마법/////////////////
	private boolean 발라카스_마법;

	public boolean isValakasArmorMagic() {
		return 발라카스_마법;
	}
	public void setValakasArmorMagic(boolean b) {
		this.발라카스_마법 = b;
	}
	private int 발라카스_마법_인챈트;
	public int getValakasArmorMagicEn() {
		return 발라카스_마법_인챈트;
	}
	public void setValakasArmorMagicEn(int i) {  
		this.발라카스_마법_인챈트 = i;
	}
	//////////////////룸티스 검은빛 귀걸이 ///////////////
	private boolean 룸티스_검은빛_귀걸이;

	public boolean isRoomtisblackArmor() {
		return 룸티스_검은빛_귀걸이;
	}
	public void setRoomtisblackArmor(boolean b) {
		this.룸티스_검은빛_귀걸이 = b;
	}
	private int 룸티스_검은빛_귀걸이_인챈트;
	public int getRoomtisblackEn() {
		return 룸티스_검은빛_귀걸이_인챈트;
	}
	public void setRoomtisblackEn(int i) {  
		this.룸티스_검은빛_귀걸이_인챈트 = i;
	}
	//////////////////스냅퍼 체력 반지 ///////////////
	private boolean 스냅퍼_체력_반지;

	public boolean isSnapperHealthRing() {
		return 스냅퍼_체력_반지;
	}
	public void setSnapperHealthRing(boolean b) {
		this.스냅퍼_체력_반지 = b;
	}
	private int 스냅퍼_체력_반지_인챈트;
	public int getSnapperHealthRingEn() {
		return 스냅퍼_체력_반지_인챈트;
	}
	public void setSnapperHealthRingEn(int i) {  
		this.스냅퍼_체력_반지_인챈트 = i;
	}
	//////////////////축복 스냅퍼 체력 반지 ///////////////
	private boolean 축복_스냅퍼_체력_반지;

	public boolean isBlessSnapperHealthRing() {
		return 축복_스냅퍼_체력_반지;
	}
	public void setBlessSnapperHealthRing(boolean b) {
		this.축복_스냅퍼_체력_반지 = b;
	}
	private int 축복_스냅퍼_체력_반지_인챈트;
	public int getBlessSnapperHealthRingEn() {
		return 축복_스냅퍼_체력_반지_인챈트;
	}
	public void setBlessSnapperHealthRingEn(int i) {  
		this.축복_스냅퍼_체력_반지_인챈트 = i;
	}
	//////////////////룸티스 붉은빛 귀걸이 ///////////////
	private boolean 룸티스_붉은빛_귀걸이;

	public boolean isRoomtisRedArmor() {
		return 룸티스_붉은빛_귀걸이;
	}
	public void setRoomtisRedArmor(boolean b) {
		this.룸티스_붉은빛_귀걸이 = b;
	}
	private int 룸티스_붉은빛_귀걸이_인챈트;
	public int getRoomtisRedEn() {
		return 룸티스_붉은빛_귀걸이_인챈트;
	}
	public void setRoomtisRedEn(int i) {  
		this.룸티스_붉은빛_귀걸이_인챈트 = i;
	}
	//////////////////축복 룸티스 붉은빛 귀걸이 ///////////////
	private boolean 축복_룸티스_붉은빛_귀걸이;

	public boolean isBlessRoomtisRedArmor() {
		return 축복_룸티스_붉은빛_귀걸이;
	}
	public void setBlessRoomtisRedArmor(boolean b) {
		this.축복_룸티스_붉은빛_귀걸이 = b;
	}
	private int 축복_룸티스_붉은빛_귀걸이_인챈트;
	public int getBlessRoomtisRedEn() {
		return 축복_룸티스_붉은빛_귀걸이_인챈트;
	}
	public void setBlessRoomtisRedEn(int i) {  
		this.축복_룸티스_붉은빛_귀걸이_인챈트 = i;
	}
	////////////////// 축복 룸티스 검은빛 귀걸이 ///////////////
	private boolean 축복_룸티스_검은빛_귀걸이;

	public boolean isBlessRoomtisBlackArmor() {
		return 축복_룸티스_검은빛_귀걸이;
	}
	public void setBlessRoomtisBlackArmor(boolean b) {
		this.축복_룸티스_검은빛_귀걸이 = b;
	}
	private int 축복_룸티스_검은빛_귀걸이_인챈트;
	public int getBlessRoomtisBlackEn() {
		return 축복_룸티스_검은빛_귀걸이_인챈트;
	}
	public void setBlessRoomtisBlackEn(int i) {  
		this.축복_룸티스_검은빛_귀걸이_인챈트 = i;
	}
	//////////////// 마법의 투구 신속 //////////////////////
	private boolean 마법_투구_신속;

	public boolean isMagicHelmFast() {
		return 마법_투구_신속;
	}
	public void setMagicHelmFast(boolean b) {
		this.마법_투구_신속 = b;
	}
	////////////////마법의 투구 치유//////////////////////
	private boolean 마법_투구_치유;

	public boolean isMagicHelmCure() {
		return 마법_투구_치유;
	}
	public void setMagicHelmCure(boolean b) {
		this.마법_투구_치유 = b;
	}
	////////////////마법의 투구 힘//////////////////////
	private boolean 마법_투구_힘;

	public boolean isMagicHelmStr() {
		return 마법_투구_힘;
	}
	public void setMagicHelmStr(boolean b) {
		this.마법_투구_힘 = b;
	}
	//////////////// 날은 바람 투구 //////////////////////
	private boolean 낡은_바람_투구;

	public boolean isOldWindHelm() {
		return 낡은_바람_투구;
	}
	public void setOldWindHelm(boolean b) {
		this.낡은_바람_투구 = b;
	}
	////////////////바람 투구 //////////////////////
	private boolean 바람_투구;

	public boolean isWindHelm() {
		return 바람_투구;
	}
	public void setWindHelm(boolean b) {
		this.바람_투구 = b;
	}
	//////////////// 독 내성 갑옷  //////////////////////
	private boolean 독_내성_갑옷;

	public boolean isPoisonRegistArmor() {
		return 독_내성_갑옷;
	}
	public void setPoisonRegistArmor(boolean b) {
		this.독_내성_갑옷 = b;
	}
	////////////////수중 호흡 갑옷  //////////////////////
	private boolean 수중_호흡_갑옷;

	public boolean isUnderwaterBreathArmor() {
		return 수중_호흡_갑옷;
	}
	public void setUnderwaterBreathArmor(boolean b) {
		this.수중_호흡_갑옷 = b;
	}
	//////////////// 변신 조종 반지  //////////////////////
	private boolean 변신_조종_반지;

	public boolean isPolyControlRing() {
		return 변신_조종_반지;
	}
	public void setPolyControlRing(boolean b) {
		this.변신_조종_반지 = b;
	}
	////////////////소환 조종 반지  //////////////////////
	private boolean 소환_조종_반지;
	public boolean isSummonControlRing() {
		return 소환_조종_반지;
	}
	public void setSummonControlRing(boolean b) {
		this.소환_조종_반지 = b;
	}
	////////////////신성한 요정족 방패  //////////////////////
	private boolean 신성한_요정족_방패;
	public boolean isHolyFairyShield() {
		return 신성한_요정족_방패;
	}
	public void setHolyFairyShield(boolean b) {
		this.신성한_요정족_방패 = b;
	}
	private int 신성한_요정족_방패_인챈트;
	public int getHolyFairyShieldEn() {
		return 신성한_요정족_방패_인챈트;
	}
	public void setHolyFairyShieldEn(int i) {  
		this.신성한_요정족_방패_인챈트 = i;
	}
	////////////////신성한 요정족 판금 갑옷  //////////////////////
	private boolean 신성한_요정족_판금_갑옷;
	public boolean isHolyFairyRlateArmor() {
		return 신성한_요정족_판금_갑옷;
	}
	public void setHolyFairyRlateArmor(boolean b) {
		this.신성한_요정족_판금_갑옷 = b;
	}
	private int 신성한_요정족_판금_갑옷_인챈트;
	public int getHolyFairyRlateArmorEn() {
		return 신성한_요정족_판금_갑옷_인챈트;
	}
	public void setHolyFairyRlateArmorEn(int i) {  
		this.신성한_요정족_판금_갑옷_인챈트 = i;
	}
}
