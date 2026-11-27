package l1j.server.server.model;

public class L1ReturnStatTemp {
    public int targetLevel;
    public int characterType;
    public boolean levelSelectionComplete;
    public boolean awaitingElixirs;
    public boolean commitPending;

    public int allocatedLevelPoints() {
        return str + Int + wis + dex + con + cha;
    }
    public HpMpGrowthHistory hpMpGrowth = new HpMpGrowthHistory(1);
	public int basestr;
	public int baseint;
	public int basewis;
	public int basedex;
	public int basecon;
	public int basecha;
	
	public int str;
	public int Int;
	public int wis;
	public int dex;
	public int con;
	public int cha;
	
	public int level;
	
	public int baseHp;
	public int baseMp;
	
	public int upHp;
	public int upMp;
	
	public int ac;
	
	public int bonusStats;
	public int  exp;
	

	
}