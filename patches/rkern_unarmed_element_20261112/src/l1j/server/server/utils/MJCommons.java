package l1j.server.server.utils;

import static l1j.server.server.model.character.skill.L1SkillId.ABSOLUTE_BARRIER;
import static l1j.server.server.model.character.skill.L1SkillId.COUNTER_MAGIC;
import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.sc.packet.SC_OnlyEffect;
public class MJCommons {
	public static boolean isNullOrEmpty(String s){
		return s == null || s.equals("") || s.length() <= 0;
	}
	
	public static int[] parseToIntArrange(String s, String tok){
		if(s == null || s.equals(""))
			return null;
		
		String[] arr 	= s.split(tok);
		int size		= arr.length;
		int[]	result	= new int[size];
		for(int i=size - 1; i>=0; --i){
			try{
				result[i] = Integer.parseInt(arr[i]);
			}catch(Exception e){
				break;
			}
		}
		return result;
	}
	
	public static double[] parseToDoubleArrange(String s, String tok){
		if(s == null || s.equals(""))
			return null;
		
		String[] arr 	= s.split(tok);
		int size		= arr.length;
		double[]	result	= new double[size];
		for(int i=size - 1; i>=0; --i){
			try{
				result[i] = Double.parseDouble(arr[i]);
			}catch(Exception e){
				break;
			}
		}
		return result;
	}
	
	public static ArrayList<Integer> parseToIntArray(String s, String tok){
		if(s == null || s.equals(""))
			return null;
		
		String[] arr 	= s.split(tok);
		int size		= arr.length;
		ArrayList<Integer> list = new ArrayList<Integer>(size);
		for(int i=0; i<size; ++i){
			try{
				if(arr[i].startsWith("0x"))
					list.add(Integer.parseInt(arr[i].replaceFirst("^0x", ""), 16));
				else
					list.add(Integer.parseInt(arr[i]));
			}catch(Exception e){
				break;
			}
		}
		return list;
	}
	
	public static ArrayList<Integer> parseToIntArray(String s){
		return parseToIntArray(s, " ");
	}
	
	public static ArrayDeque<Integer> parseToIntQ(String s){
		return parseToIntQ(s, " ");
	}
	
	public static ArrayDeque<Integer> parseToIntQ(String s, String tok){
		if(s == null || s.equals(""))
			return null;
		
		String[] arr 	= s.split(tok);
		int size		= arr.length;
		ArrayDeque<Integer> argsQ = new ArrayDeque<Integer>(size);
		for(int i=0; i<size; i++){
			try{
				argsQ.offer(Integer.parseInt(arr[i]));
			}catch(Exception e){
				break;
			}
		}
		return argsQ;
	}
	
	public static Integer parseToFirstInt(String s){
		String[] arr = s.split(",");
		try{
			return Integer.parseInt(arr[0]);
		}catch(Exception e){
			
		}
		return 0;
	}
	protected static Random _rnd = new Random(System.nanoTime());

	public static int	MCS_HITRATE_MAX;
	public static int	MCS_HITRATE_MIN;

	public static double MCS_DEFENCE_ATTR_WEIGHT;
	
	public static double MCS_STSSPELL_STATADDER;
	public static double MCS_DYCSPELL_SPADDER;
	public static double MCS_DYCSPELL_INTADDER;
	
	private static double[] _mr_dmgDef_table;
	private static double[] _mr_ratioDef_table;
	
	public static void load(){
		String column	 = null;
		try{
			Properties settings = new Properties();
            try (InputStream is = new FileInputStream(new File("./config/mjcommons.properties"))) {
                settings.load(is);
            }
			
			column = "HitRateMax";
			MCS_HITRATE_MAX 		= Integer.parseInt(settings.getProperty(column, "90"));
			
			column = "HitRateMin";
			MCS_HITRATE_MIN 		= Integer.parseInt(settings.getProperty(column, "5"));
			
			column	= "MagicDefenceAttrWeight";
			MCS_DEFENCE_ATTR_WEIGHT = Double.parseDouble(settings.getProperty(column, "1")) / 100D;
			
			column	= "StaticSpellStatAdder";
			MCS_STSSPELL_STATADDER	= Double.parseDouble(settings.getProperty(column, "70")) / 100D;
			
			column	= "DynamicSpellAdder";
			MCS_DYCSPELL_SPADDER	= Double.parseDouble(settings.getProperty(column, "15")) / 100D;
			
			column	= "DynamicIntAdder";
			MCS_DYCSPELL_INTADDER	= Double.parseDouble(settings.getProperty(column, "150")) / 100D;
			
			loadSpellDef();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private static void loadSpellDef(){
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs		= null;
		
		try{
			con					= DatabaseFactory.getInstance().getConnection();
			pstm 				= con.prepareStatement("select * from tb_mjspell_def");
			rs					= pstm.executeQuery();
			int rows 			= SQLUtil.calcRows(rs);
			_mr_dmgDef_table	= new double[rows];
			_mr_ratioDef_table	= new double[rows];
			while(rs.next()){
				int idx = rs.getInt("id");
				_mr_dmgDef_table[idx] 	= rs.getDouble("def_dmg") / 100D;
				_mr_ratioDef_table[idx]	= rs.getDouble("def_ratio") / 100D;
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public static boolean isDistance(int x, int y, int m, int tx, int ty, int tm, int loc) {
		if (m != tm) return false;
		return loc >= getDistance(x, y, tx, ty); 
	}

	public static int getDistance(int x, int y, int tx, int ty) {
		long dx = tx - x;
		long dy = ty - y;
		return (int) Math.sqrt(dx * dx + dy * dy);
	}
	
	public static final int[][] _headings = new int[][]{
		{7, 0, 1},
		{6, 1, 2},
		{5, 4, 3},
	};
	public static final int[] _headingReverseLines = new int[]{
		3, 4, 5, 6, 7, 0, 1, 2
	};	
	public static final int[] _headingLines = new int[]{
		7, 0, 1, 2, 3, 4, 5, 6
	};
	public static final int[] _headingWideLines = new int[]{
		6, 7, 0, 1, 2, 3, 4, 5
	};
	
	public static final byte HEADING_TABLE_X[] = { 0, 1, 1, 1, 0, -1, -1, -1 };
	public static final byte HEADING_TABLE_Y[] = { -1, -1, 0, 1, 1, 1, 0, -1 };	
	
	public static int calcheading(int myx, int myy, int tx, int ty) {
		return _headings[(ty > myy ? 2 : (ty < myy ? 0 : 1))][(tx > myx ? 2 : (tx < myx ? 0 : 1))];
	}
	
	public static int getReverseHeading(int h){
		return _headingReverseLines[h];
	}
	
	public static boolean isReverseHeading(int myx, int myy, int tx, int ty, int h){
		int th = calcheading(myx, myy, tx, ty);
		for(int i=0; i<3; i++){
			if(th == _headingReverseLines[(h + i) % 8])
				return true;
		}
		return false;
	}
	
	public static int getSpaceHeadingAndCombatZone(L1Character c){
		L1Map map = c.getMap();
		for(int h=0; h<8; h++){
			int cx = c.getX() + HEADING_TABLE_X[h];
			int cy = c.getY() + HEADING_TABLE_Y[h];
			if(!map.isCombatZone(cx, cy) || !map.isPassable(cx, cy))
				continue;
			
			return h;
		}
		return -1;
	}
	
	public static boolean isPassableLine(int sx, int sy, int dx, int dy, short mid){
		if(sx == dx && sy == dy)
			return true;
		
		L1Map map = L1WorldMap.getInstance().getMap(mid);
		int cx = sx;
		int cy = sy;
		
		do{
			int h = calcheading(cx, cy, dx, dy);
			if(!map.isArrowPassable(cx, cy, h))
				return false;
			
			cx = cx + HEADING_TABLE_X[h];
			cy = cy + HEADING_TABLE_Y[h];
		}while(cx != dx || cy != dy);
		return true;
	}
	
	public static int checkObject(int x, int y, short m, int d) {
		L1Map map = L1WorldMap.getInstance().getMap(m);
		
		for(int i=0; i<3; i++){
			int h = _headingLines[(i + d) % 8];
			if(map.isPassable(x, y, h))
				return h;
		}
		return -1;
	}
	
	public static int checkPassable(int x, int y, short m){
		L1Map map = L1WorldMap.getInstance().getMap(m);
		for(int i=0; i<8; i++){
			int h = _headingLines[i];
			int cx = x + HEADING_TABLE_X[h];
			int cy = y + HEADING_TABLE_Y[h];
			if(!map.isPassable(cx, cy))
				continue;
			
			return h;
		}
		return -1;
	}
	
	public static int checkPassable(int x, int y, short m, int d){
		L1Map map = L1WorldMap.getInstance().getMap(m);
		for(int i=0; i<3; i++){
			int h = _headingLines[(i + d) % 8];
			int cx = x + HEADING_TABLE_X[h];
			int cy = y + HEADING_TABLE_Y[h];
			if(!map.isPassable(cx, cy))
				continue;
			
			return h;
		}
		return -1;
	}
	
	public static int checkAroundPassable(L1PcInstance body, int d){
		int h 		= _headingLines[d];
		if(!isVisibleObjects(body, h))
			return h;
		
		h			= _headingLines[(d + 2) % 8];
		if(!isVisibleObjects(body, h))
			return h;
		
		return -1;
	}
	
	public static int checkReverseAroundPassable(L1PcInstance body, int d){
		int h		= _headingLines[d];
		if(!isVisibleObjects(body, h))
			return h;
		
		h			= _headingLines[(d+2)%8];
		if(!isVisibleObjects(body, h))
			return h;
		return -1;
	}
	
	public static int checkAroundWidePassable(L1PcInstance body, int d){
		if(_rnd.nextBoolean()){
			int h		= _headingWideLines[(d + 1) % 8];
			if(!isVisibleObjects(body, h))
				return h;
			h			= _headingWideLines[(d + 3) % 8];
			if(!isVisibleObjects(body, h))
				return h;
			h 			= _headingWideLines[d];
			if(!isVisibleObjects(body, h))
				return h;
			h 			= _headingWideLines[(d + 4) % 8];
			if(!isVisibleObjects(body, h))
				return h;
		}else{
			int h		= _headingWideLines[(d + 3) % 8];
			if(!isVisibleObjects(body, h))
				return h;
			h			= _headingWideLines[(d + 1) % 8];
			if(!isVisibleObjects(body, h))
				return h;	
			h 			= _headingWideLines[(d + 4) % 8];
			if(!isVisibleObjects(body, h))
				return h;
			h 			= _headingWideLines[d];
			if(!isVisibleObjects(body, h))
				return h;
		}
		
		if(_rnd.nextInt(100) < 5)
			return checkReverseAroundPassable(body, d);
		return -1;
	}
	
	public static boolean isAroundDirection(L1Character owner, L1Character target){
		int oh = owner.getHeading();
		int th = calcheading(owner.getX(), owner.getY(), target.getX(), target.getY());
		return (oh == th || _headingLines[oh] == th || _headingLines[(oh + 2) % 8] == th); 
	}
	
	public static boolean isWideAroundDirection(L1Character owner, L1Character target){
		int oh = owner.getHeading();
		int th = calcheading(owner.getX(), owner.getY(), target.getX(), target.getY());
		return (oh == th || _headingWideLines[oh] == th || _headingWideLines[(oh + 1) % 8] == th || _headingWideLines[(oh + 2) % 8] == th|| _headingWideLines[(oh + 3) % 8] == th || _headingWideLines[(oh + 4) % 8] == th); 
	}
	
	public static boolean isVisibleObjects(L1PcInstance body, int h){
		L1Map map 	= L1WorldMap.getInstance().getMap(body.getMapId());
		int cx 		= body.getX() + HEADING_TABLE_X[h];
		int cy 		= body.getY() + HEADING_TABLE_Y[h];
		return !map.isPassable(cx, cy);
	}
	
	public static boolean isCounterMagic(L1Character c) {
		if (c.hasSkillEffect(COUNTER_MAGIC)) {
			c.removeSkillEffect(COUNTER_MAGIC);
			SC_OnlyEffect.send(c, c.getId(), 10702);
            SC_OnlyEffect.broadcast(c, c.getId(), 10702);
			return true;
		}
		return false;
	}

	public boolean isRock(L1PcInstance pc) {
		if (pc.hasSkillEffect(ABSOLUTE_BARRIER))
			return true;

		if (pc.hasSkillEffect(EARTH_BIND)
				|| pc.hasSkillEffect(L1SkillId.ICE_LANCE))
			return true;

		return false;
	}

	public static boolean isMagicSuccess(L1Character attacker, L1Character target, int default_ratio, int defenseMr) {
		int hit = attacker.getAbility().getMagicBonus()
				+ attacker.getAbility().getMagicLevel();
		hit += (attacker.getAbility().getTotalInt()) + default_ratio;
		int mr = defenseMr;
		double pMr = 0;

		int mridx = (mr / 10) - 1;
		if (mridx < 0)
			mridx = 0;
		else if (mridx > _mr_ratioDef_table.length - 1)
			mridx = _mr_ratioDef_table.length - 1;

		if (mr != 0)
			pMr = _mr_ratioDef_table[mridx];

		hit -= (int) (hit * pMr);
		hit += attacker.getLevel() - target.getLevel();

		if (hit >= MCS_HITRATE_MAX)
			hit = MCS_HITRATE_MAX;
		else if (hit <= MCS_HITRATE_MIN)
			hit = MCS_HITRATE_MIN;

		if ((_rnd.nextInt(100)) < hit)
			return true;
		return false;
	}

	public static double getAttrDamage(double attrDamage, int attrDefense){
		if (attrDefense == 0)
			return attrDamage;

        double defense = Math.min(100, attrDefense) * MCS_DEFENCE_ATTR_WEIGHT;
        return Math.max(0.0, attrDamage - defense * attrDamage);
	}
	
	public static int getAttrDamage(int attrDamage, int attrDefense) {
		if (attrDefense == 0)
			return attrDamage;

        double defense = Math.min(100, attrDefense) * MCS_DEFENCE_ATTR_WEIGHT;
        return (int) Math.max(0.0, attrDamage - defense * attrDamage);
	}

	public static int getMagicDamage(double mdmg, int mdefen) {
		if (mdefen == 0)
			return (int) mdmg;

		int mridx = (mdefen / 10) - 1;
		if (mridx < 0)
			mridx = 0;
		else if (mridx > _mr_dmgDef_table.length - 1)
			mridx = _mr_dmgDef_table.length - 1;

		double result = mdmg - (mdmg * _mr_dmgDef_table[mridx]);
		return (int) result;
	}

	public static int getMagicDamage(int mdmg, int mdefen) {
		return getMagicDamage((double) mdmg, mdefen);
	}

	public static boolean isMagicSuccess(L1Character attacker, L1Character target, int default_ratio) {
		return isMagicSuccess(attacker, target, default_ratio, target.getResistance().getMrAfterEraseRemove());
	}

	public static int getMagicDamage(L1Character attacker, L1Character target, int pure) {
		double dmg = pure * (attacker.getAbility().getSp() * MCS_DYCSPELL_SPADDER);
		dmg += attacker.getAbility().getMagicBonus();
		dmg += (attacker.getAbility().getTotalInt() * MCS_DYCSPELL_INTADDER);

		dmg = getMagicDamage((int) dmg, target.getResistance().getMrAfterEraseRemove());
		return (int) dmg;
	}

	public static int getMagicDamage(L1Character attacker, L1Character target, int min, int max) {
		int result = 0;
		try{
			int intel = attacker.getAbility().getTotalInt();
			int pure = _rnd.nextInt(max - min) + min;
			double dmg = pure + (intel * MCS_STSSPELL_STATADDER);
			result = getMagicDamage(dmg, target.getResistance().getMrAfterEraseRemove());
		}catch(Exception e){
			System.out.println(String.format("%s -> %s MJCommons.getMagicDamage()", attacker.getName(), target.getName()));
		}
		return result;
	}
	
	public static int calcDamage(L1Character attacker, L1PcInstance pc, int min, int max, boolean isMagic){
		double imm = pc.getImmuneReduction();
		
		if(imm != 0){
			min -= (int)((double)min * imm);
			max -= (int)((double)max * imm);
		}

		int dmg = 0;
		if(isMagic){
			dmg = getMagicDamage(attacker, pc, min, max);
			if(pc.hasSkillEffect(L1SkillId.FAFU_MAAN) || pc.hasSkillEffect(L1SkillId.LIFE_MAAN) || 
					pc.hasSkillEffect(L1SkillId.SHAPE_MAAN) || pc.hasSkillEffect(L1SkillId.BIRTH_MAAN)){
				int MaanMagicCri = _rnd.nextInt(100) + 1;
			    if (MaanMagicCri <= 35)
			    	dmg /= 2;
			}
		}else{
			dmg = _rnd.nextInt(max - min) + min;
		}
		
		dmg -= pc.getDamageReductionByArmor();

		if (pc.hasSkillEffect(L1SkillId.REDUCTION_ARMOR)) {
			int targetPcLvl = pc.getLevel();
			if (targetPcLvl < 50) 
				targetPcLvl = 50;
			dmg -= (targetPcLvl - 50) / 5 + 1;
		}

		if (pc.hasSkillEffect(L1SkillId.MAJESTY)) {
			int targetPcLvl = pc.getLevel();
			if (targetPcLvl < 50) {
				targetPcLvl = 50;
			}
			int r = (targetPcLvl - 50) / 5 + 1;
			dmg -= r;
			
		}
		if (pc.hasSkillEffect(L1SkillId.IllUSION_AVATAR))
			dmg += dmg / 5;
		
		if (pc.hasSkillEffect(L1SkillId.PATIENCE))
			dmg -= 4;
		
		if (pc.hasSkillEffect(L1SkillId.DRAGON_SKIN))
			dmg -= 3;
		return dmg;
	}
	
	
	public static boolean isInArea(L1Object obj, int x1, int y1, int x2, int y2, int leftRad, int rightRad, int mid){
		if(x1 < x2)
			return isInArea(obj, x1 - leftRad, y1 - leftRad, x2 + rightRad, y2 + rightRad, (short)mid);
		else
			return isInArea(obj, x2 - leftRad, y2 - leftRad, x1 + rightRad, y1 + rightRad, (short)mid);
	}
	
	public static boolean isInArea(L1Object obj, int x1, int y1, int x2, int y2, int rad, int mid){
		if(x1 < x2)
			return isInArea(obj, x1 - rad, y1 - rad, x2 + rad, y2 + rad, (short)mid);
		else
			return isInArea(obj, x2 - rad, y2 - rad, x1 + rad, y1 + rad, (short)mid);
	}
	
	public static boolean isInArea(L1Object obj, int x1, int y1, int x2, int y2, int mid){
		if(x1 < x2)
			return isInArea(obj, x1, y1, x2, y2, (short)mid);
		else
			return isInArea(obj, x2, y2, x1, y1, (short)mid);
	}
	
	public static boolean isInArea(L1Object obj, int x, int y, int rad, short mid){
		return isInArea(obj, x - rad, y - rad, x + rad, y + rad, mid);
	}
	
	public static boolean isInArea(L1Object obj, int left, int top, int right, int bottom, short mid){
		return (obj.getX() >= left &&
				obj.getY() >= top &&
				obj.getX() <= right &&
				obj.getY() <= bottom && 
				obj.getMapId() == mid);
	}
	
	public static <E> E getRandomListElement(ArrayList<E> list){
		return list.get(_rnd.nextInt(list.size()));
	}
	
	public static ArrayList<L1Object> createKnownPlayers(L1PcInstance body){
		return new ArrayList<L1Object>(body.getKnownPlayers());
	}
	
	public static ArrayList<L1Object> createKnownList(L1PcInstance body){
		return new ArrayList<L1Object>(body.getKnownObjects());
	}
	
	public static ArrayDeque<L1Object> createKnownQ(L1PcInstance body){
		return new ArrayDeque<L1Object>(body.getKnownObjects());
	}
	
	public static boolean isNonAction(L1Character c){ 
		return (c.hasSkillEffect(L1SkillId.SHOCK_STUN)
				|| c.hasSkillEffect(L1SkillId.BONE_BREAK)
				|| c.hasSkillEffect(L1SkillId.MOB_SHOCKSTUN_30)
				|| c.hasSkillEffect(L1SkillId.Mob_RANGESTUN_30)
				|| c.hasSkillEffect(L1SkillId.MOB_RANGESTUN_20)
				|| c.hasSkillEffect(L1SkillId.MOB_RANGESTUN_19)
				|| c.hasSkillEffect(L1SkillId.MOB_RANGESTUN_18)
				|| c.hasSkillEffect(L1SkillId.ANTA_SHOCKSTUN)
				|| c.hasSkillEffect(L1SkillId.OMAN_STUN))
				|| c.hasSkillEffect(L1SkillId.Maeno_STUN)
				|| c.hasSkillEffect(L1SkillId.EARTH_BIND) 
				|| c.hasSkillEffect(L1SkillId.ICE_LANCE) 
				|| c.hasSkillEffect(L1SkillId.MOB_BASILL)
				|| c.hasSkillEffect(L1SkillId.MOB_COCA)
				|| c.hasSkillEffect(L1SkillId.EMPIRE)
				|| c.isParalyzed() || c.isSleeped();
	}
	
	public static boolean isStunSkill(L1Character c) { 
		return (c.hasSkillEffect(L1SkillId.SHOCK_STUN)
				|| c.hasSkillEffect(L1SkillId.BONE_BREAK)
				|| c.hasSkillEffect(L1SkillId.MOB_SHOCKSTUN_30)
				|| c.hasSkillEffect(L1SkillId.Mob_RANGESTUN_30)
				|| c.hasSkillEffect(L1SkillId.MOB_RANGESTUN_20)
				|| c.hasSkillEffect(L1SkillId.MOB_RANGESTUN_19)
				|| c.hasSkillEffect(L1SkillId.MOB_RANGESTUN_18)
				|| c.hasSkillEffect(L1SkillId.ANTA_SHOCKSTUN)
				|| c.hasSkillEffect(L1SkillId.OMAN_STUN))
				|| c.hasSkillEffect(L1SkillId.Maeno_STUN)
				|| c.hasSkillEffect(L1SkillId.EMPIRE);
	}
	
	
	public static boolean isUnbeatable(L1Character body){
		return body.hasSkillEffect(L1SkillId.EARTH_BIND) ||
				body.hasSkillEffect(L1SkillId.ICE_LANCE) ||
				 body.hasSkillEffect(L1SkillId.MOB_BASILL) ||
				body.hasSkillEffect(L1SkillId.MOB_COCA);
	}
	
	public static boolean isLock(L1Character body){ 
		return body.hasSkillEffect(L1SkillId.EARTH_BIND) || 
				body.hasSkillEffect(L1SkillId.SHOCK_STUN) || 
				body.hasSkillEffect(L1SkillId.ICE_LANCE) || 
				body.hasSkillEffect(L1SkillId.CURSE_PARALYZE) ||
				body.hasSkillEffect(L1SkillId.BONE_BREAK) || 
				body.hasSkillEffect(L1SkillId.PHANTASM) || 
				body.hasSkillEffect(L1SkillId.STATUS_FREEZE) ||
				body.hasSkillEffect(L1SkillId.MOB_BASILL)||
				body.hasSkillEffect(L1SkillId.EMPIRE)||
				body.hasSkillEffect(L1SkillId.MOB_COCA);
	}
	
	public static boolean isBlessed(L1Character body){
		return body.hasSkillEffect(L1SkillId.ARMOR_BLESSING);
	}
	
	public static double calcHealing(L1Character c, double healing){
		double rate = 0D;
		if(c.hasSkillEffect(L1SkillId.DESPERADO)){
			int atlvl = c.Desperadolevel;
			int dflvl = c.getLevel();
			
			rate = 0.40D;
			if(atlvl > dflvl)
				rate += ((atlvl - dflvl) * 0.01D);
		}
		
		if(c.hasSkillEffect(L1SkillId.POLLUTE_WATER))
			rate += 0.50D;
		
		if(rate == 0D)
			return healing;
		
		double calc = healing - (healing * rate);
		double min	= healing * 0.10;
		if(calc < min) calc = min;
		else if(calc > healing) calc = healing;
		
		return calc;
	}
	
	private static final int[] _classLindBlessingMp	= {9, 8, 16, 20, 16, 9, 20, 8};			// 린드 가호 블레싱 mp 차는 량
	public static int getClassLindBlessing(int classType) throws Exception{
		return _classLindBlessingMp[classType];
	}
	
	private static final int[] _chaotic_damage = new int[]{
		1, 3, 5, 7
	};
	
	public static int getChaoticDamage(int lawful){
		if(lawful >= 0)
			return 0;
		
		return _chaotic_damage[lawful / - 10000];
	}
	
	public static boolean isLetterOrDigitString(String s, int min, int max){
		int len = s.length();
		if(len < min || len > max)
			return false;
		
		char[] chars = s.toCharArray();
		for(int i = chars.length - 1; i>=0; i--){
			if(!Character.isLetterOrDigit(chars[i]))
				return false;
		}
		return true;
	}
}
