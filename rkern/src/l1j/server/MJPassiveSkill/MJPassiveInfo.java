package l1j.server.MJPassiveSkill;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MJPassiveInfo {
	public static MJPassiveInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
			.setSpellBookId(rs.getInt("skill_book_id"))
			.setPassiveId(rs.getInt("passive_id"))
			.setPassiveName(rs.getString("name"))
			.setClassType(rs.getString("class_type"))
			.setUseMinLevel(rs.getInt("use_min_level"));
			
	}
	
	public static MJPassiveInfo newInstance(){
		return new MJPassiveInfo();
	}

	private int spellBookId;
	private int passiveId;
	private String passiveName;
	private int classType;
	private int useMinLevel;
	private MJPassiveInfo(){
	}

	public MJPassiveInfo setSpellBookId(int val){
		spellBookId = val;
		return this;
	}
	public int getSpellBookId(){
		return spellBookId;
	}

	public MJPassiveInfo setPassiveId(int val){
		passiveId = val;
		return this;
	}
	public int getPassiveId(){
		return passiveId;
	}

	public MJPassiveInfo setPassiveName(String val){
		passiveName = val;
		return this;
	}
	public String getPassiveName(){
		return passiveName;
	}

	public MJPassiveInfo setClassType(int val){
		classType = val;
		return this;
	}
	
	public MJPassiveInfo setClassType(String val){
		int nClassType = -1;
		switch(val){
		case "군주":
			nClassType = 0;
			break;
		case "기사":
			nClassType = 1;
			break;
		case "요정":
			nClassType = 2;
			break;
		case "법사":
			nClassType = 3;
			break;
		case "다크엘프":
			nClassType = 4;
			break;
		case "용기사":
			nClassType = 5;
			break;
		case "환술사":
			nClassType = 6;
			break;
		case "전사":
			nClassType = 7;
			break;
		default:
			throw new IllegalArgumentException(String.format("invalid enum type(classType) : %s", val));
		}
		
		return setClassType(nClassType);
	}
	
	public int getClassType(){
		return classType;
	}

	public MJPassiveInfo setUseMinLevel(int val){
		useMinLevel = val;
		return this;
	}
	public int getUseMinLevel(){
		return useMinLevel;
	}
}
