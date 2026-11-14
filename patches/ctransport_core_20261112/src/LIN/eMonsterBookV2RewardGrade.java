package LIN;




// TODO : 자동으로 생성된 PROTO 코드입니다. made by MJSoft.
public enum eMonsterBookV2RewardGrade{
	RG_NORMAL(1),
	RG_DRAGON(2),
	RG_HIGH_DRAGON(3);
	private int value;
	eMonsterBookV2RewardGrade(int val){
		value = val;
	}
	public int toInt(){
		return value;
	}
	public boolean equals(LIN.eMonsterBookV2RewardGrade v){
		return value == v.value;
	}
	public static LIN.eMonsterBookV2RewardGrade fromInt(int i){
		switch(i){
		case 1:
			return RG_NORMAL;
		case 2:
			return RG_DRAGON;
		case 3:
			return RG_HIGH_DRAGON;
		default:
			return null;
		}
	}
}
