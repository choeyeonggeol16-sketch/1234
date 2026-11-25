package l1j.server.server.model;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import l1j.server.server.model.network.core.sc.packet.SC_Weight;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_CHAR_STAT;

import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.utils.CalcStat;
import l1j.server.server.utils.IntRange;

public class Ability {
	private static final int LIMIT_MINUS_MIN = -128;
	private static final int LIMIT_MIN = 0;
	private static final int LIMIT_MAX = 127;

	void setSkillPrimaryStats(long value, boolean notify) {
		final long changed = owner.m_SkillPrimaryStats ^ value;
		if (changed == 0) return;
		owner.m_SkillPrimaryStats = value;
		if (!notify) return;
		if ((changed & 0x000000ff) != 0) notifyAddedStr();
		if ((changed & 0x0000ff00) != 0) notifyAddedDex();
		if ((changed & 0xff00000000L) != 0) notifyAddedCon();
		if ((changed & 0x00ff0000) != 0) notifyAddedInt();
		if ((changed & 0xff000000L) != 0) notifyAddedWis();
	}

	// Existing facade retains its owner; detach only suppresses notifications as before.
	private final L1Character owner;
	private L1Character character;
	
	public void dispose(){
		character = null;
	}
	
    Ability(L1Character owner.m_StatCha) {
        this.owner = owner.m_StatCha;
        this.character = owner.m_StatCha;
    }

	public void init() {
		owner.m_StatStr = owner.m_OriginalStatStr = owner.m_AddedStatStr = 0;
		owner.m_StatDex = owner.m_OriginalStatDex = owner.m_AddedStatDex = 0;
		owner.m_StatCon = owner.m_OriginalStatCon = owner.m_AddedStatCon = 0;
		owner.m_StatInt = owner.m_OriginalStatInt = owner.m_AddedStatInt = 0;
		owner.m_StatWis = owner.m_OriginalStatWis = owner.m_AddedStatWis = 0;
		owner.m_StatCha = owner.m_OriginalStatCha = owner.m_AddedStatCha = 0;
	}

	private byte checkRange(int i) {
		if (i == 0)
			return 0;
		return checkRange(i, 0);
	}

	private byte checkRange(int i, int base) {
		return (byte) IntRange.ensure(i, LIMIT_MIN + base, LIMIT_MAX);
	}

	public int getBaseAmount() {
		return owner.m_OriginalStatStr + owner.m_OriginalStatCon + owner.m_OriginalStatDex + owner.m_OriginalStatCha + owner.m_OriginalStatInt + owner.m_OriginalStatWis;
	}

	public int getAmount() {
		return owner.m_StatStr + owner.m_StatCon + owner.m_StatDex + owner.m_StatCha + owner.m_StatInt + owner.m_StatWis;
	}


	void setSkillSp(int value) {
		if (owner.m_AbilitySkillSp != value) owner.m_AbilitySkillSp = value;
	}

	public void addSp(int i) {
		owner.m_AbilitySp += i;
	}

	public int getSp() {
		return getTrueSp() + owner.m_AbilitySp + owner.m_AbilitySkillSp;
	}
	
	public int getTrueSp() {
		return getMagicLevel() + getMagicBonus();
	}

	public byte getStr() {
		return owner.GetStatStr();
	}

	public int getMagicLevel() {
		if (character instanceof L1PcInstance)
			return ((L1PcInstance) character).getMagicLevel();
		return character.getLevel() / 4;
	}

	public int getMagicBonus() {
		int i = getTotalInt();
		final int classType = (character instanceof L1PcInstance)
                ? ((L1PcInstance) character).getType() : 0;
        return CalcStat.마법보너스(classType, i);
		/*
		 * if (i <= 5) return -2; else if (i <= 8) return -1; else if (i <= 11)
		 * return 0; else if (i <= 14) return 1; else if (i <= 17) return 2;
		 * else if (i <= 24) return i - 15; else if (i <= 35) return 10; else if
		 * (i <= 42) return 11; else if (i <= 49) return 12; else if (i <= 50)
		 * return 13; else return i - 25;
		 */
	}
	
	public void setStr(int i) {
		owner.SetStatStr(i);
	}

	public void addStr(int i) {
		setStr(getStr() + i);
		if (character instanceof L1PcInstance) {
            L1PcInstance pc = (L1PcInstance) character;
            if (pc.getNetConnection() != null) {                    
                SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);// 현재순수스탯
                SC_Weight.send(pc);            
                SC_CHAR_STAT.sendCreate(pc, pc, 2, SC_CHAR_STAT.Stat_Str);
                SC_OwnCharStatus2.send(pc); 
            }
        }
	}

	public byte getBaseStr() {
		return owner.GetOriginalStatStr();
	}

	public void addBaseStr(int i) {
		setBaseStr(getBaseStr() + i);
	}

	public void setBaseStr(int i) {
		byte newBaseStr = checkRange(i);
		addStr(newBaseStr - owner.m_OriginalStatStr);
		owner.m_OriginalStatStr = newBaseStr;
	}

	public byte getAddedStr() {
		return owner.GetAddedStatStr();
	}

	public void addAddedStr(int i) {
		owner.AddAddedStatStr(i);
		notifyAddedStr();
	}

	private void notifyAddedStr() {
		if (!(character instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Str);
		SC_Weight.send(pc);
	}

	public byte getTotalStr() {
		return owner.GetTotalStatStr();
	}

	public byte getCon() {
		return owner.GetStatCon();
	}

	public void setCon(int i) {
		owner.SetStatCon(i);
	}

	public void addCon(int i) {
		setCon(getCon() + i);
		if (character instanceof L1PcInstance) {
            L1PcInstance pc = (L1PcInstance) character;
            if (pc.getNetConnection() != null) {       
                SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);
            	SC_Weight.send(pc);
                SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Con);
            }
        }
	}

	public byte getBaseCon() {
		return owner.GetOriginalStatCon();
	}

	public void addBaseCon(int i) {
		setBaseCon(getBaseCon() + i);
	}

	public void setBaseCon(int i) {
		byte newBaseCon = checkRange(i);
		addCon(newBaseCon - owner.m_OriginalStatCon);
		owner.m_OriginalStatCon = newBaseCon;
	}

	public byte getAddedCon() {
		return owner.GetAddedStatCon();
	}

	public void addAddedCon(int i) {
		owner.AddAddedStatCon(i);
		notifyAddedCon();
	}

	private void notifyAddedCon() {
		if (!(character instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Con);
		SC_Weight.send(pc);
	}

	public byte getTotalCon() {
		return owner.GetTotalStatCon();
	}

	public byte getDex() {
		return owner.GetStatDex();
	}

	public void setDex(int i) {
		owner.SetStatDex(i);
	}

	public void addDex(int i) {
		setDex(getDex() + i);
		if (character instanceof L1PcInstance) {
            L1PcInstance pc = (L1PcInstance) character;
            if (pc.getNetConnection() != null) {                       
                SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);// 현재순수스탯
                SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());             
                SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Dex);
                SC_OwnCharStatus2.send(pc);
                SC_OwnCharAttrDef.send(pc);
               
            }
        }
	}

	public byte getBaseDex() {
		return owner.GetOriginalStatDex();
	}

	public void addBaseDex(int i) {
		setBaseDex(getBaseDex() + i);
	}

	public void setBaseDex(int i) {
		byte newBaseDex = checkRange(i);
		addDex(newBaseDex - owner.m_OriginalStatDex);
		owner.m_OriginalStatDex = newBaseDex;
	}

	public byte getAddedDex() {
		return owner.GetAddedStatDex();
	}

	public void addAddedDex(int i) {
		owner.AddAddedStatDex(i);
		notifyAddedDex();
	}

	private void notifyAddedDex() {
		if (!(character instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Dex);
		SC_OwnCharAttrDef.send(pc);
	}

	public byte getTotalDex() {
		return owner.GetTotalStatDex();
	}

	public byte getCha() {
		return owner.GetStatCha();
	}

	public void setCha(int i) {
		owner.SetStatCha(i);
	}

	public void addCha(int i) {
		setCha(getCha() + i);
	}

	public byte getBaseCha() {
		return owner.GetOriginalStatCha();
	}

	public void addBaseCha(int i) {
		setBaseCha(getBaseCha() + i);
	}

	public void setBaseCha(int i) {
		byte newBaseCha = checkRange(i);
		addCha(newBaseCha - owner.m_OriginalStatCha);
		owner.m_OriginalStatCha = newBaseCha;
	}

	public byte getAddedCha() {
		return owner.GetAddedStatCha();
	}

	public void addAddedCha(int i) {
		owner.AddAddedStatCha(i);
	}

	public byte getTotalCha() {
		return owner.GetTotalStatCha();
	}

	public byte getInt() {
		return owner.GetStatInt();
	}

	public void setInt(int i) {
		owner.SetStatInt(i);
	}

	public void addInt(int i) {
		setInt(getInt() + i);
		 if (character instanceof L1PcInstance) {
	            L1PcInstance pc = (L1PcInstance) character;
	            if (pc.getNetConnection() != null) {
	                SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);// 현재순수스탯
	                SC_SPMR.send(pc);             
	                SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Int);
	           
	            }
	        }
	}

	public byte getBaseInt() {
		return owner.GetOriginalStatInt();
	}

	public void addBaseInt(int i) {
		setBaseInt(getBaseInt() + i);
	}

	public void setBaseInt(int i) {
		byte newBaseInt = checkRange(i);
		addInt(newBaseInt - owner.m_OriginalStatInt);
		owner.m_OriginalStatInt = newBaseInt;
	}

	public byte getAddedInt() {
		return owner.GetAddedStatInt();
	}

	public void addAddedInt(int i) {
		owner.AddAddedStatInt(i);
		notifyAddedInt();
	}

	private void notifyAddedInt() {
		if (!(character instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		SC_SPMR.send(pc);
		SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Int);
	}

	public byte getTotalInt() {
		return owner.GetTotalStatInt();
	}

	public byte getWis() {
		return owner.GetStatWis();
	}

	public void setWis(int i) {
		owner.SetStatWis(i);
	}

	public void addWis(int i) {
		setWis(getWis() + i);
		 if (character instanceof L1PcInstance) {
	            L1PcInstance pc = (L1PcInstance) character;
	            if (pc.getNetConnection() != null) {
	                pc.resetBaseMr();              
	                SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Wis);
	                SC_SPMR.send(pc);
	                SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);// 현재순수스탯
	            }
	        }	
	}

	public byte getBaseWis() {
		return owner.GetOriginalStatWis();
	}

	public void addBaseWis(int i) {
		setBaseWis(getBaseWis() + i);
	}

	public void setBaseWis(int i) {
		byte newBaseWis = checkRange(i);
		addWis(newBaseWis - owner.m_OriginalStatWis);
		owner.m_OriginalStatWis = newBaseWis;
	}

	public byte getAddedWis() {
		return owner.GetAddedStatWis();
	}

	public void addAddedWis(int i) {
		owner.AddAddedStatWis(i);
		notifyAddedWis();
	}

	private void notifyAddedWis() {
		if (!(character instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		pc.resetBaseMr();
		SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Wis);
		SC_SPMR.send(pc);
	}

	public byte getTotalWis() {
		return owner.GetTotalStatWis();
	}

	public int[] getBaseStatDiff(int[] value) {
		int[] returnValue = new int[6];
		returnValue[0] = getBaseStr() - value[0];
		returnValue[1] = getBaseDex() - value[1];
		returnValue[2] = getBaseCon() - value[2];
		returnValue[3] = getBaseWis() - value[3];
		returnValue[4] = getBaseCha() - value[4];
		returnValue[5] = getBaseInt() - value[5];

		return returnValue;
	}

	public int[] getMinStat(final int classId) {
		int[] minabllity = new int[6];
		// int minStr, minDex, minCon, minWis, minCha, minInt, remainStats;
		switch (classId) {
		case L1PcInstance.CLASSID_PRINCE:
		case L1PcInstance.CLASSID_PRINCESS:
			minabllity[0] = 13;
			minabllity[1] = 9;
			minabllity[2] = 11;
			minabllity[3] = 11;
			minabllity[4] = 13;
			minabllity[5] = 9;
			break;
		case L1PcInstance.CLASSID_KNIGHT_MALE:
		case L1PcInstance.CLASSID_KNIGHT_FEMALE:
			minabllity[0] = 16;
			minabllity[1] = 12;
			minabllity[2] = 16;
			minabllity[3] = 9;
			minabllity[4] = 10;
			minabllity[5] = 8;
			break;
		case L1PcInstance.CLASSID_WIZARD_MALE:
		case L1PcInstance.CLASSID_WIZARD_FEMALE:
			minabllity[0] = 8;
			minabllity[1] = 7;
			minabllity[2] = 12;
			minabllity[3] = 14;
			minabllity[4] = 8;
			minabllity[5] = 14;
			break;
		case L1PcInstance.CLASSID_ELF_MALE:
		case L1PcInstance.CLASSID_ELF_FEMALE:
			minabllity[0] = 10;
			minabllity[1] = 12;
			minabllity[2] = 12;
			minabllity[3] = 12;
			minabllity[4] = 9;
			minabllity[5] = 12;
			break;
		case L1PcInstance.CLASSID_DARK_ELF_MALE:
		case L1PcInstance.CLASSID_DARK_ELF_FEMALE:
			minabllity[0] = 15;
			minabllity[1] = 12;
			minabllity[2] = 12;
			minabllity[3] = 10;
			minabllity[4] = 8;
			minabllity[5] = 11;
			break;
		case L1PcInstance.CLASSID_DRAGONKNIGHT_MALE:
		case L1PcInstance.CLASSID_DRAGONKNIGHT_FEMALE:
			minabllity[0] = 13;
			minabllity[1] = 11;
			minabllity[2] = 14;
			minabllity[3] = 10;
			minabllity[4] = 8;
			minabllity[5] = 10;
			break;
		case L1PcInstance.CLASSID_BLACKWIZARD_MALE:
		case L1PcInstance.CLASSID_BLACKWIZARD_FEMALE:
			minabllity[0] = 9;
			minabllity[1] = 10;
			minabllity[2] = 12;
			minabllity[3] = 14;
			minabllity[4] = 8;
			minabllity[5] = 12;
			break;
		case L1PcInstance.CLASSID_전사_MALE:
		case L1PcInstance.CLASSID_전사_FEMALE:
			minabllity[0] = 16;
			minabllity[1] = 13;
			minabllity[2] = 16;
			minabllity[3] = 7;
			minabllity[4] = 9;
			minabllity[5] = 10;
			break;
		default:

		}
		minabllity = getBaseStatDiff(minabllity);
		/*
		 * for(int i =0 ; i<minabllity.length; i++){ System.out.println(minabllity[i]); }
		 */
		return minabllity;
	}

}
