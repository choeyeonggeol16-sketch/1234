package l1j.server.server.utils;

import java.io.IOException;

public class ItemPresentOutStream extends BinaryOutputStream {
	public ItemPresentOutStream() {
		super();
	}

	// TODO 작은몹 대미지
	public void writeDMG(int SmallDmg, int LargeDmg) {
		writeC(1);
		writeC(SmallDmg);
		writeC(LargeDmg);
	}

	// TODO 큰몹 대미지
	public void writeLargeDMG(int DMG) {
		writeC(2);
		writeC(DMG);
	}
	
	// 인챈트 레벨
	public void writeEnchantLevel(int EnchantLevel) {
		writeC(2);
		writeC(EnchantLevel);
	}
	
	// TODO 손상도
	public void writeDurability(int Durability) {
		writeC(3);
		writeC(Durability);
	}

	// TODO 양손무기
	public void writeTwoHand() {
		writeC(4);
	}

	// TODO 무기 명중
	public void writeMeleeHit(int WeaponHIT) {
		writeC(5);
		writeC(WeaponHIT);
	}

	// TODO 추가 대미지
	public void writeMeleeDmg(int AddDMG) {
		writeC(6);
		writeC(AddDMG);
	}

	// TODO 추가 대미지
	public void writeShortAddDMG(int AddDMG) {
		writeC(47);
		writeC(AddDMG);
	}

	// TODO 사용 가능 클래스
	public void writeClass(int Class) {
		writeC(7);
		writeC(Class);
	}

	// TODO STR
	public void writeaStr(int STR_Bu) {
		writeC(8);
		writeC(STR_Bu);
	}

	// TODO DEX
	public void writeaDex(int DEX_Bu) {
		writeC(9);
		writeC(DEX_Bu);
	}

	// TODO CON
	public void writeaCon(int ON_Bu) {
		writeC(10);
		writeC(ON_Bu);
	}

	// TODO WIS
	public void writeaWis(int WIS_Bu) {
		writeC(11);
		writeC(WIS_Bu);
	}

	// TODO INT
	public void writeaInt(int INT_Bu) {
		writeC(12);
		writeC(INT_Bu);
	}

	// TODO CHA
	public void writeaCha(int CHA_Bu) {
		writeC(13);
		writeC(CHA_Bu);
	}

	// TODO 최대 HP
	public void writeAddMaxHP(int AddMaxHP) {
		writeC(14);
		writeH(AddMaxHP);
	}

	// TODO MR
	public void writeAddMR(int AddMR) {
		writeC(15);
		writeH(AddMR);
	}

	// TODO MP 흡수
	public void writeMpDrain() {
		writeC(16);
	}

	// TODO SP
	public void writeAddSP(int AddSP) {
		writeC(17);
		writeC(AddSP);
	}

	// TODO 헤이스트 효과
	public void writeHaste() {
		writeC(18);
	}

	// TODO AC (다른표기안나올 가능성큼)
	public void writeaAcUP(int AcUP) {
		writeC(19);
		writeC(AcUP);
	}

	// TODO 행운
	public void writeAddLuck(int AddLuck) {
		writeC(20);
		writeC(AddLuck);
	}

	// TODO 영양
	public void writeFoodVolume(int FoodVolume) {
		writeC(21);
		writeH(FoodVolume);
	}

	// TODO 밝기
	public void writeLightRange(int LightRange) {
		writeC(22);
		writeH(LightRange);
	}

	// TODO 재질
	public void writeMaterial(int Material, int Weight) {
		writeC(Material);
		writeD(Weight);
	}

	// TODO 종류 ??
	public void writeType(int type) {
		writeC(25);
		writeC(type);
	}

	// TODO 레벨
	public void writeLevel(int Level) {
		writeC(26);
		writeD(Level);
	}

	// TODO 불 속성 저항
	public void writeRegistFire(int Fire) {
		writeC(27);
		writeC(Fire);
	}

	// TODO 물 속성 저항
	public void writeRegistWater(int Water) {
		writeC(28);
		writeC(Water);
	}

	// TODO 바람 속성 저항
	public void writeRegistWind(int Wind) {
		writeC(29);
		writeC(Wind);
	}

	// TODO 땅 속성 저항
	public void writeRegistEarth(int Earth) {
		writeC(30);
		writeC(Earth);
	}

	// TODO 최대 HP
	public void writeHP(int HP) {
		writeC(31);
		writeC(HP);
	}

	// TODO 최대 MP
	public void writeMaxMP(int MaxMP) {
		writeC(32);
		writeC(MaxMP);
	}

	// TODO 수면내성 (안씀)
	public void writeRegistSleep(int Sleep) {
		writeC(33);
		writeC(Sleep);
	}

	// TODO HP 흡수
	public void writeHpDrain() {
		writeC(34);
	}

	// TODO 원거리 대미지
	public void writeRagendDmg(int LongDMG) {
		writeC(35);
		writeC(LongDMG);
	}

	// TODO 원거리 명중
	public void writeRagendHit(int IongHIT) {
		writeC(24);
		writeC(IongHIT);
	}

	// TODO 경험치 보너스
	public void writeAddEXP(int AddEXP) {
		writeC(36);
		writeC(AddEXP);
	}

	// TODO HP 회복
	public void writeAddHPPrecovery(int AddHPrecovery) {
		writeC(37);
		writeC(AddHPrecovery);
	}

	// TODO MP 회복
	public void writeAddMPPrecovery(int AddMPPrecovery) {
		writeC(38);
		writeC(AddMPPrecovery);
	}

	// TODO 스턴적중
	public void writeAddStunHit(int AddStunHit) {
		writeC(39);
		writeS(String.format("스턴 적중 +%d", AddStunHit));
	}

	// TODO 마법 적중
	public void writeMagicHIT(int MagicHIT) {
		writeC(40);
		writeC(MagicHIT);
	}

	// TODO 경험치 (안씀) 1 = 0.01
	public void writeExp(int Exp) {
		writeC(41);
		writeC(Exp);
	}
	//
	//	// TODO 근거리 대미지
	//	public void writeShortDMG(int DMG) {
	//		writeC(47);
	//		writeC(DMG);
	//	}

	//	// TODO 근거리 명중
	//	public void writeMeleeHit1(int HIT) {
	//		writeC(48);
	//		writeC(HIT);
	//	}

	// TODO 마법 치명타
	public void writeMagicCritical(int MagicCritical) {
		writeC(50);
		writeH(MagicCritical);
	}

	// TODO 추가 방어력
	public void writeAddAc(int Ac) {
		writeC(56);
		writeC(Ac);
	}

	// TODO 초(시간)
	public void writeaSecond(int Second) {
		writeC(58);
		writeC(Second);
	}

	// TODO PVP추가 대미지
	public void writePVPAddDMG(int PVPAddDMG) {
		writeC(39);
		writeS(String.format("PVP 추가대미지 +%d", PVPAddDMG));
	}

	// TODO PVP 대미지감소
	public void writePVPAddDMGdown(int PVPAddDMGdown) {
		writeC(60);
		writeC(PVPAddDMGdown);
	}

	// TODO 자동 삭제
	public void writeAutoDelete(int Delete) {
		writeC(61);
		writeD(Delete);
	}

	// TODO 대미지 감소
	public void writeDMGdown(int DMGdown) {
		writeC(63);
		writeC(DMGdown);
	}

	// TODO 대미지 감소 확률
	public void writeDMGdownprobability(int DMGdownprobability, int dmgdown) {
		writeC(64);
		writeC(DMGdownprobability);
		writeC(dmgdown);
	}

	// TODO 물약회복량
	public void writePotionrecovery(int Potionrecovery, int Percent) {
		writeC(65);
		writeC(Potionrecovery);
		writeC(Percent);
	}

	// TODO 소지 무게 증가(확률)
	public void writeAddWeightPer(int WeightPer) {
		writeC(65);
		writeC(WeightPer);
	}

	// TODO 세트 아이템
	public void writeSetItem() {
		writeC(69);
	}

	// TODO 세트 아이템 옵션
	public void writeSetItemOption(int Option) {
		writeC(71);
		writeH(Option);
	}

	// TODO 마법 발동
	public void writeMagic(String Magic) {
		writeC(73);
		writeS(Magic);
	}

	// TODO 마법 발동2
	public void writeMagic2(String Magic) {
		writeC(74);
		writeS(Magic);
	}

	// TODO 성향
	public void writeLawful(int Lawful) {
		writeC(75);
		writeC(Lawful);
	}

	// TODO 단계
	public void writeStep(int Step) {
		writeC(77);
		writeC(Step);
	}

	// TODO 속성
	public void writeAttr(int Attr) {
		writeC(78);
		writeC(Attr);
	}

	// TODO 사용 레벨
	public void writeUseLevel(int UseLevel) {
		writeC(39);
		writeS(String.format("최소레벨 : %d", UseLevel));
	}

	// TODO HP절대회복(32초)
	public void writeaHPUP(int HPUP) {
		writeC(87);
		writeC(HPUP);
	}

	// TODO MP절대회복(64초)
	public void writeaMPUP(int MPUP) {
		writeC(88);
		writeC(MPUP);
	}

	// TODO 확률 마법 회피
	public void writeMagicDodge(int MagicDodge) {
		writeC(89);
		writeD(MagicDodge);
	}

	// TODO 소지 무게 증가(+숫자)
	public void writeAddWeight(int AddWeight) {
		writeC(90);
		writeH(AddWeight);
	}

	// TODO 관통 효과
	public void writePenetrate(int Penetrate) {
		writeC(94);
		writeC(Penetrate);
	}

	// TODO 추가 대미지 확률
	public void writeAddDmgPer(int Dmg, int Dmgper) {
		writeC(95);
		writeC(Dmg);
		writeC(Dmgper);
	}

	// TODO 회복 악화 방어 (공포)
	public void writeHealDefence(int Defence) {
		writeC(96);
		writeC(Defence);
	}

	// TODO 대미지 리덕션 무시
	public void writeReductiondown(int Reductiondown) {
		writeC(97);
		writeC(Reductiondown);
	}

	// TODO 원거리 치명타
	public void writeLongCritical(int longCritical) {
		writeC(99);
		writeC(longCritical);
	}

	// TODO 근거리 치명타
	public void writeShortCritical(int ShortCritical) {
		writeC(100);
		writeC(ShortCritical);
	}

	// TODO 포우 슬레이어 대미지
	public void writeaFouslayer(int Fouslayer) {
		writeC(101);
		writeC(Fouslayer);
	}

	// TODO 타이탄 계열 발동 구간 3%
	public void writeaTitan(int Titan) {
		writeC(102);
		writeC(Titan);
	}

	// TODO 확률적 근거리 대미지
	public void writeaPercentDmg(int Dmg) {
		writeC(103);
		writeC(Dmg);
	}

	// TODO 대미지 표기
	public void writeWeaponDmg(int SmallDmg, int LargeDmg) {
		writeC(107);
		writeC(SmallDmg);
		writeC(LargeDmg);
	}

	// TODO 무기 속성 대미지
	public void writeAttrDmg(int AttrDmg) {
		writeC(109);
		writeC(AttrDmg);
	}

	// TODO 레벨 제한
	public void writeLimitLevel(int MinLevel, int MaxLevel) {
		writeC(39);
		writeS(String.format("최소 레벨 : %d", MinLevel));
		writeC(39);
		writeS(String.format("레벨 제한 : %d", MaxLevel));
	}

	// TODO 제한 시간
	public void writeLimitTime(int Time) {
		writeC(112);
		writeD(Time);
	}

	// TODO 축복 소모 효율 (다른표기안나올 가능성큼)
	public void writeaBlesssomo(int Blesssomo) {
		writeC(116);
		writeH(Blesssomo);
	}

	// TODO 기술내성
	public void writeability_resis(int ability_resis) {
		writeC(117);
		writeC(ability_resis);
	}

	// TODO 정령내성
	public void writeaspirit_resis(int spirit_resis) {
		writeC(118);
		writeC(spirit_resis);
	}

	// TODO 용언내성
	public void writeadragonS_resis(int dragonS_resis) {
		writeC(119);
		writeC(dragonS_resis);
	}

	// TODO 공포내성
	public void writeafear_resis(int fear_resis) {
		writeC(120);
		writeC(fear_resis);
	}

	// TODO 모든내성
	public void writeaAll_resis(int All_resis) {
		writeC(121);
		writeC(All_resis);
	}

	// TODO 기술적중
	public void writeability_pierce(int ability_pierce) {
		writeC(122);
		writeC(ability_pierce);
	}

	// TODO 정령적중
	public void writeaspirit_pierce(int spirit_pierce) {
		writeC(123);
		writeC(spirit_pierce);
	}

	// TODO 용언적중
	public void writeadragonS_pierce(int dragonS_pierce) {
		writeC(124);
		writeC(dragonS_pierce);
	}

	// TODO 공포적중
	public void writeafear_pierce(int fear_pierce) {
		writeC(125);
		writeC(fear_pierce);
	}

	// TODO 모든적중
	public void writeaAll_pierce(int All_pierc) {
		writeC(126);
		writeC(All_pierc);
	}

	// TODO 스턴내성
	public void writeAddStun(int AddStun) {
		writeH(0x521);
		writeC(AddStun);
	}

	public void writeString(String message, double d) throws IOException {
		writeC(39);
		writeS(String.format("\\fI%s: \\aA%s 배", message, d));
	}
	
	public void writeStringto(String message) throws IOException {
		writeC(39);
		writeS(String.format("마법 발동:", message));
	}
	public void writeOption(String message, int d) throws IOException {
		writeC(39);
		writeS(String.format("\\fI%s: \\aA%s", message, d));
	}

}
