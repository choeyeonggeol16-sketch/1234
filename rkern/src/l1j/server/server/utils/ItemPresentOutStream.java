package l1j.server.server.utils;

public class ItemPresentOutStream extends BinaryOutputStream {
    private int[] replacedEquipmentOptions;

    public void replaceEquipmentOptions(int[] totals) {
        replacedEquipmentOptions = totals;
    }

    public void writeNumericOption(int opcode, int value, int width) {
        final int[] totals = replacedEquipmentOptions;
        if (totals != null && opcode >= 0 && opcode < totals.length && totals[opcode] != Integer.MIN_VALUE) return;
        writeC(opcode);
        switch (width) {
            case 1 -> writeC(value);
            case 2 -> writeH(value);
            case 4 -> writeD(value);
            default -> throw new IllegalArgumentException("Unsupported item option width " + width);
        }
    }


	public ItemPresentOutStream() {
		super();
	}

	public ItemPresentOutStream(int capacity) {
		super(capacity);
	}

	// =====================================================================
	// 옵션 출력 공통 방어
	// =====================================================================

	/**
	 * 1 byte 옵션.
	 * 0 이하 값은 옵션 자체를 패킷에 기록하지 않는다.
	 */
	private void writePositiveC(int opcode, int value) {
		if (value <= 0) {
			return;
		}

		writeNumericOption(opcode, value, 1);
	}

	/**
	 * 2 byte 옵션.
	 * 0 이하 값은 옵션 자체를 패킷에 기록하지 않는다.
	 */
	private void writePositiveH(int opcode, int value) {
		if (value <= 0) {
			return;
		}

		writeNumericOption(opcode, value, 2);
	}

	/**
	 * 4 byte 옵션.
	 * 0 이하 값은 옵션 자체를 패킷에 기록하지 않는다.
	 */
	private void writePositiveD(int opcode, int value) {
		if (value <= 0) {
			return;
		}

		writeNumericOption(opcode, value, 4);
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
	public void writeWeaponHIT(int WeaponHIT) {
		writePositiveC(5, WeaponHIT);
	}

	// TODO 추가 대미지
	public void writeAddDMG(int AddDMG) {
		writePositiveC(6, AddDMG);
	}

	// TODO 추가 대미지
	public void writeShortAddDMG(int AddDMG) {
		writePositiveC(47, AddDMG);
	}

	// TODO 사용 가능 클래스
	public void writeClass(int Class) {
		writeC(7);
		writeC(Class);
	}

	// TODO STR
	public void writeaSTR_Bu(int STR_Bu) {
		writePositiveC(8, STR_Bu);
	}

	// TODO DEX
	public void writeaDEX_Bu(int DEX_Bu) {
		writePositiveC(9, DEX_Bu);
	}

	// TODO CON
	public void writeaCON_Bu(int CON_Bu) {
		writePositiveC(10, CON_Bu);
	}

	// TODO WIS
	public void writeaWIS_Bu(int WIS_Bu) {
		writePositiveC(11, WIS_Bu);
	}

	// TODO INT
	public void writeaINT_Bu(int INT_Bu) {
		writePositiveC(12, INT_Bu);
	}

	// TODO CHA
	public void writeaCHA_Bu(int CHA_Bu) {
		writePositiveC(13, CHA_Bu);
	}

	// TODO 최대 HP
	public void writeAddMaxHP(int AddMaxHP) {
		writePositiveH(14, AddMaxHP);
	}

	// TODO MR
	public void writeAddMR(int AddMR) {
		writePositiveH(15, AddMR);
	}

	// TODO MP 흡수
	public void writeMpDrain() {
		writeC(16);
	}

	// TODO SP
	public void writeAddSP(int AddSP) {
		writePositiveC(17, AddSP);
	}

	// TODO 헤이스트 효과
	public void writeHaste() {
		writeC(18);
	}

	// TODO AC
	public void writeaAcUP(int AcUP) {
		writePositiveC(19, AcUP);
	}

	// TODO 행운
	public void writeAddLuck(int AddLuck) {
		writePositiveC(20, AddLuck);
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
		writeC(23);
		writeC(Material);
		writeD(Weight);
	}

	// TODO 종류
	public void writeType(int type) {
		writeC(25);
		writeC(type);
	}

	// TODO 레벨
	public void writeLevel(int Level) {
		writeC(26);
		writeC(Level);
	}

	// TODO 불 속성 저항
	public void writeRegistFire(int Fire) {
		writePositiveC(27, Fire);
	}

	// TODO 물 속성 저항
	public void writeRegistWater(int Water) {
		writePositiveC(28, Water);
	}

	// TODO 바람 속성 저항
	public void writeRegistWind(int Wind) {
		writePositiveC(29, Wind);
	}

	// TODO 땅 속성 저항
	public void writeRegistEarth(int Earth) {
		writePositiveC(30, Earth);
	}

	// TODO HP
	public void writeHP(int HP) {
		writePositiveC(31, HP);
	}

	// TODO 최대 MP
	public void writeMaxMP(int MaxMP) {
		writePositiveH(32, MaxMP);
	}

	// TODO 수면내성
	public void writeRegistSleep(int Sleep) {
		writePositiveC(33, Sleep);
	}

	// TODO HP 흡수
	public void writeHpDrain() {
		writeC(34);
	}

	// TODO 원거리 대미지
	public void writeLongDMG(int LongDMG) {
		writePositiveC(35, LongDMG);
	}

	// TODO 원거리 명중
	public void writeLongHIT(int LongHIT) {
		writePositiveC(24, LongHIT);
	}

	// TODO 경험치 보너스
	public void writeAddEXP(int AddEXP) {
		writePositiveC(36, AddEXP);
	}

	// TODO HP 회복
	public void writeAddHPPrecovery(int AddHPrecovery) {
		writePositiveC(37, AddHPrecovery);
	}

	// TODO MP 회복
	public void writeAddMPPrecovery(int AddMPPrecovery) {
		writePositiveC(38, AddMPPrecovery);
	}

	// TODO 스턴적중
	public void writeAddStunHit(int AddStunHit) {
		if (AddStunHit <= 0) {
			return;
		}

		writeC(39);
		writeS(String.format("스턴 적중 +%d", AddStunHit));
	}

	// TODO 마법 적중
	public void writeMagicHIT(int MagicHIT) {
		writePositiveC(40, MagicHIT);
	}

	// TODO 경험치
	public void writeExp(int Exp) {
		writePositiveC(41, Exp);
	}

	// TODO 근거리 대미지
	public void writeShortDMG(int DMG) {
		writePositiveC(47, DMG);
	}

	// TODO 근거리 명중
	public void writeShortHIT(int HIT) {
		writePositiveC(48, HIT);
	}

	// TODO 마법 치명타
	public void writeMagicCritical(int MagicCritical) {
		writePositiveH(50, MagicCritical);
	}

	// TODO 추가 방어력
	public void writeAddAc(int Ac) {
		writePositiveC(56, Ac);
	}

	// TODO 초(시간)
	public void writeaSecond(int Second) {
		writeC(58);
		writeC(Second);
	}

	// TODO PVP 추가 대미지
	public void writePVPAddDMG(int PVPAddDMG) {
		writePositiveC(59, PVPAddDMG);
	}

	// TODO PVP 대미지 감소
	public void writePVPAddDMGdown(int PVPAddDMGdown) {
		writePositiveC(60, PVPAddDMGdown);
	}

	// TODO 자동 삭제
	public void writeAutoDelete(int Delete) {
		writeC(61);
		writeD(Delete);
	}

	// TODO 대미지 감소
	public void writeDMGdown(int DMGdown) {
		writePositiveC(63, DMGdown);
	}

	// TODO 대미지 감소 확률
	public void writeDMGdownprobability(int DMGdownprobability, int dmgdown) {
		if (DMGdownprobability <= 0 || dmgdown <= 0) {
			return;
		}

		writeC(64);
		writeC(DMGdownprobability);
		writeC(dmgdown);
	}

	// TODO 물약 회복량
	public void writePotionrecovery(int Potionrecovery, int Percent) {
		if (Potionrecovery <= 0 && Percent <= 0) {
			return;
		}

		writeC(65);
		writeC(Math.max(0, Potionrecovery));
		writeC(Math.max(0, Percent));
	}

	/**
	 * TODO 소지 무게 증가(확률)
	 *
	 * 주의:
	 * writePotionrecovery()와 opcode 65를 같이 사용하고 있음.
	 * 프로토콜 확인 전에는 번호를 임의 변경하지 않는다.
	 */
	public void writeAddWeightPer(int WeightPer) {
		writePositiveC(65, WeightPer);
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
		if (Magic == null || Magic.isEmpty()) {
			return;
		}

		writeC(73);
		writeS(Magic);
	}

	// TODO 마법 발동2
	public void writeMagic2(String Magic) {
		if (Magic == null || Magic.isEmpty()) {
			return;
		}

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
		writeC(79);
		writeC(UseLevel);
	}

	// TODO HP 절대회복(32초)
	public void writeaHPUP(int HPUP) {
		writePositiveC(87, HPUP);
	}

	// TODO MP 절대회복(64초)
	public void writeaMPUP(int MPUP) {
		writePositiveC(88, MPUP);
	}

	// TODO 확률 마법 회피
	public void writeMagicDodge(int MagicDodge) {
		writePositiveD(89, MagicDodge);
	}

	// TODO 소지 무게 증가(+숫자)
	public void writeAddWeight(int AddWeight) {
		writePositiveH(90, AddWeight);
	}

	// TODO 관통 효과
	public void writePenetrate(int Penetrate) {
		writePositiveC(94, Penetrate);
	}

	// TODO 추가 대미지 확률
	public void writeAddDmgPer(int Dmg, int Dmgper) {
		if (Dmg <= 0 || Dmgper <= 0) {
			return;
		}

		writeC(95);
		writeC(Dmg);
		writeC(Dmgper);
	}

	// TODO 회복 악화 방어
	public void writeHealDefence(int Defence) {
		writePositiveC(96, Defence);
	}

	// TODO 대미지 리덕션 무시
	public void writeReductiondown(int Reductiondown) {
		writePositiveC(97, Reductiondown);
	}

	// TODO 원거리 치명타
	public void writeLongCritical(int longCritical) {
		writePositiveC(99, longCritical);
	}

	// TODO 근거리 치명타
	public void writeShortCritical(int ShortCritical) {
		writePositiveC(100, ShortCritical);
	}

	// TODO 포우 슬레이어 대미지
	public void writeaFouslayer(int Fouslayer) {
		writePositiveC(101, Fouslayer);
	}

	// TODO 타이탄 계열 발동 구간 3%
	public void writeaTitan(int Titan) {
		writePositiveC(102, Titan);
	}

	// TODO 확률적 근거리 대미지
	public void writeaPercentDmg(int Dmg) {
		writePositiveC(103, Dmg);
	}

	// TODO 대미지 표기
	public void writeWeaponDmg(int SmallDmg, int LargeDmg) {
		writeC(107);
		writeC(SmallDmg);
		writeC(LargeDmg);
	}

	// TODO 무기 속성 대미지
	public void writeAttrDmg(int AttrDmg) {
		writePositiveC(109, AttrDmg);
	}

	// TODO 레벨 제한
	public void writeLimitLevel(int MinLevel, int MaxLevel) {
		writeC(111);
		writeC(MinLevel);
		writeH(MaxLevel);
	}

	// TODO 제한 시간
	public void writeLimitTime(int Time) {
		writeC(112);
		writeD(Time);
	}

	// TODO 축복 소모 효율
	public void writeaBlesssomo(int Blesssomo) {
		writePositiveH(116, Blesssomo);
	}

	// TODO 기술내성
	public void writeability_resis(int ability_resis) {
		writePositiveC(117, ability_resis);
	}

	// TODO 정령내성
	public void writeaspirit_resis(int spirit_resis) {
		writePositiveC(118, spirit_resis);
	}

	// TODO 용언내성
	public void writeadragonS_resis(int dragonS_resis) {
		writePositiveC(119, dragonS_resis);
	}

	// TODO 공포내성
	public void writeafear_resis(int fear_resis) {
		writePositiveC(120, fear_resis);
	}

	// TODO 모든내성
	public void writeaAll_resis(int All_resis) {
		writePositiveC(121, All_resis);
	}

	// TODO 기술적중
	public void writeability_pierce(int ability_pierce) {
		writePositiveC(122, ability_pierce);
	}

	// TODO 정령적중
	public void writeaspirit_pierce(int spirit_pierce) {
		writePositiveC(123, spirit_pierce);
	}

	// TODO 용언적중
	public void writeadragonS_pierce(int dragonS_pierce) {
		writePositiveC(124, dragonS_pierce);
	}

	// TODO 공포적중
	public void writeafear_pierce(int fear_pierce) {
		writePositiveC(125, fear_pierce);
	}

	// TODO 모든적중
	public void writeaAll_pierce(int All_pierc) {
		writePositiveC(126, All_pierc);
	}

	// TODO 스턴내성
	public void writeAddStun(int AddStun) {
		if (AddStun <= 0) {
			return;
		}

		writeH(0x521);
		writeC(AddStun);
	}
}