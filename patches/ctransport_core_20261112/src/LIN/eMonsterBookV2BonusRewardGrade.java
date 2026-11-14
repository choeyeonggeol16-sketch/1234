package LIN;




// TODO : 자동으로 생성된 PROTO 코드입니다. made by MJSoft.
public enum eMonsterBookV2BonusRewardGrade{
	BRG_REBEL(1),
	BRG_HERO(2);
	private int value;
	eMonsterBookV2BonusRewardGrade(int val){
		value = val;
	}
	public int toInt(){
		return value;
	}
	public boolean equals(LIN.eMonsterBookV2BonusRewardGrade v){
		return value == v.value;
	}
	public static LIN.eMonsterBookV2BonusRewardGrade fromInt(int i){
		switch(i){
		case 1:
			return BRG_REBEL;
		case 2:
			return BRG_HERO;
		default:
			return null;
		}
	}
}
