package l1j.server.ActionCodes.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.stream.Stream;

import l1j.server.L1DatabaseFactory;
import l1j.server.ActionCodes.ActionInformation;
import l1j.server.ActionCodes.SpriteInformation;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class SpriteInformationLoader {
	private static SpriteInformationLoader _instance;
	public static SpriteInformationLoader getInstance(){
		if(_instance == null)
			_instance = new SpriteInformationLoader();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	public static void reload(){
		SpriteInformationLoader tmp = _instance;
		_instance = new SpriteInformationLoader().loadSpriteInformation();
		if(tmp != null) {
			tmp.clear();
			tmp = null;
		}
	}
	
	private HashMap<Integer, SpriteInformation> _sprInfos;
	private HashMap<Integer, SpellDelayInfo>				_spellInfos;
	private SpriteInformationLoader(){}
	
	private HashMap<Integer, SpellDelayInfo> loadSpellDelayInformation() {
		Connection 			con		= null;
		PreparedStatement 	pstm 		= null;
		ResultSet 			rs				= null;
		HashMap<Integer, SpellDelayInfo>	sps= null;
		try{
			sps = new HashMap<Integer, SpellDelayInfo>(320);
			con			= L1DatabaseFactory.getInstance().getConnection();
			pstm 		= con.prepareStatement("select * from Magic_Delay");
			rs			= pstm.executeQuery();
			while(rs.next()) {
				SpellDelayInfo si = new SpellDelayInfo();
				si.setSkillId(rs.getInt("skill_id"));
				si.setDelay(rs.getLong("delay"));
				si.setType(rs.getBoolean("fix_delay"));
				sps.put(si.getSkillId(), si);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
		return sps;
	}
	
	public class SpellDelayInfo {
		private int _skillid;
		private long _delay;
		private boolean _type;
		
		public int getSkillId(){
			return _skillid;
		}
		
		public void setSkillId(int id){
			_skillid = id;
		}
		
		public long getDelay(){
			return _delay;
		}
		
		public void setDelay(long id){
			_delay = id;
		}
		
		public boolean getType(){
			return _type;
		}
		
		public void setType(boolean id){
			_type = id;
		}

	}
	
	public void reloadSpellDelayInformation(){
		HashMap<Integer, SpellDelayInfo>	tmp = _spellInfos;
		_spellInfos = loadSpellDelayInformation();
		if(tmp != null) {
			tmp.clear();
			tmp = null;
		}
	}
	
	public SpriteInformationLoader loadSpriteInformation(){
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs		= null;
		try{
			con			= L1DatabaseFactory.getInstance().getConnection();
			pstm 		= con.prepareStatement("select spr_id, width, height, action_count from spr_info");
			rs			= pstm.executeQuery();
			_sprInfos	= new HashMap<Integer, SpriteInformation>(SQLUtil.calcRows(rs));
			while(rs.next()){
				SpriteInformation sInfo = loadActionInformation(SpriteInformation.create(rs));
				_sprInfos.put(sInfo.getSpriteId(), sInfo);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
			_spellInfos = loadSpellDelayInformation();
		}
		return this;
	}
	
	private SpriteInformation loadActionInformation(SpriteInformation sInfo){
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs		= null;
		try{
			con			= L1DatabaseFactory.getInstance().getConnection();
			pstm 		= con.prepareStatement("select act_id, framecount, framerate from spr_action where spr_id=?");
			pstm.setInt(1, sInfo.getSpriteId());
			rs			= pstm.executeQuery();
			while(rs.next())
				sInfo.put(ActionInformation.create(rs));
			
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
		return sInfo;
	}
	
	public SpriteInformation get(int n) {
		SpriteInformation sInfo = _sprInfos.get(n);
		if (sInfo == null) {
			try {
				throw new Exception(String.format("not found sprite id : %d", n));
			} catch (Exception e) {
				e.printStackTrace();
			}
			return _sprInfos.get(1120);
		}
		return sInfo;
	}

	public void clear(){
		if(_spellInfos != null) {
			_spellInfos.clear();
			_spellInfos = null;
		}

		Stream<SpriteInformation> stream = createValuesStream();
		if(stream == null) {
			return;
		}
		stream.forEach((SpriteInformation sInfo) -> { sInfo.dispose(); });
		_sprInfos.clear();
		_sprInfos = null;
	}

	public long getUseSpellInterval(L1PcInstance pc, int skillId) {
		SpellDelayInfo spell = _spellInfos.get(skillId);
		if(spell == null) {
			return 0L;
		}
		long l = spell.getDelay();
		
		if (spell.getType()) {
			return l;
		}
		return l;
	}

	public Stream<SpriteInformation> createValuesStream(){
		return _sprInfos == null ? null : _sprInfos.size() > 1024 ? _sprInfos.values().parallelStream() : _sprInfos.values().stream();
	}

	public Stream<Integer> createKeysStream(){
		return _sprInfos == null ? null : _sprInfos.size() > 1024 ? _sprInfos.keySet().parallelStream() : _sprInfos.keySet().stream();
	}
}
