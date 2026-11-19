package l1j.server.MJPassiveSkill;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FRUIT;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;



import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
public class MJPassiveLoader{
    private static volatile MJPassiveLoader _instance;
    public static MJPassiveLoader getInstance(){
        MJPassiveLoader current = _instance;
        if (current != null) return current;
        synchronized (MJPassiveLoader.class) {
            current = _instance;
            if (current == null) _instance = current = new MJPassiveLoader();
        }
        return current;
    }

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		MJPassiveLoader old = _instance;
		_instance = new MJPassiveLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, MJPassiveInfo> m_passives;
	private MJPassiveLoader(){
		m_passives = load();
	}
	
	private HashMap<Integer, MJPassiveInfo> load(){
		HashMap<Integer, MJPassiveInfo> passives = new HashMap<Integer, MJPassiveInfo>();
		Selector.exec("select * from passive_book_mapped", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					try{
						MJPassiveInfo pInfo = MJPassiveInfo.newInstance(rs);
						passives.put(pInfo.getPassiveId(), pInfo);
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			}
		});
		return passives;
	}

	public MJPassiveInfo fromPassiveId(int passiveId){
		return m_passives.get(passiveId);
	}
	
	public MJPassiveInfo fromBookId(int bookId){
		ArrayList<MJPassiveInfo> passives = values();
		if(passives == null)
			return null;
		
		for(MJPassiveInfo pInfo : passives){
			if(pInfo.getSpellBookId() == bookId)
				return pInfo;
		}
		return null;
	}
	
	public ArrayList<MJPassiveInfo> fromClassType(int type){
		ArrayList<MJPassiveInfo> passives = values();
		if(passives == null)
			return null;
		
		ArrayList<MJPassiveInfo> result = new ArrayList<MJPassiveInfo>();
		for(MJPassiveInfo pInfo : passives){
			if(pInfo.getClassType() == type)
				result.add(pInfo);
		}
		return result;
	}
	
	public ArrayList<MJPassiveInfo> values(){
		return new ArrayList<MJPassiveInfo>(m_passives.values());
	}
	
	private static final String[] CLASSE_NAMES = new String[]{
		"군주", "기사", "요정", "마법사", "다크엘프", "용기사", "환술사", "전사"	
	};
	public boolean masterPassive(L1PcInstance pc, MJPassiveInfo pInfo){
		if(pc.getPassive(pInfo.getPassiveId()) != null){
			pc.sendPackets("이미 배운 마법입니다.");
			return false;
		}
		/*
		if(pc.getLevel() < pInfo.getUseMinLevel()){
			pc.sendPackets(String.format("%d 레벨부터 사용 가능합니다.", pInfo.getUseMinLevel()));
			return false;
		}
		*/
		
		if(pc.getClassNumber() != pInfo.getClassType()){
			pc.sendPackets(String.format("해당 아이템은 %s만 사용할 수 있습니다.", CLASSE_NAMES[pInfo.getClassType()]));
			return false;
		}
		//if ( pInfo.getSpellBookId() == 4100108  ) {
		
		
		int passiveId = pInfo.getPassiveId();
		
		
		if(passiveId == MJPassiveID.FOU_SLAYER_BRAVE.toInt()){
			SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.포우브레이브, 3890, true);
			
		}
		
		if(passiveId == MJPassiveID.DOUBLE_BREAK_DESTINY.toInt()){
			if(pc.hasSkillEffect(L1SkillId.DOUBLE_BRAKE))
				pc.removeSkillEffect(L1SkillId.DOUBLE_BRAKE);
		}

		pc.addPassive(pInfo);
		MJPassiveUserLoader.store(pc, pInfo);
		long s_skillSound = SC_SkillSound.pack(pc.getId(), 231);
		SC_SkillSound.sendPacked(pc, s_skillSound);
		SC_SkillSound.broadcastPacked(pc, s_skillSound);
		if(pInfo.getPassiveId() == MJPassiveID.DARK_HORSE.toInt()) {
			if(pc.hasSkillEffect(L1SkillId.STATUS_FRUIT)) {
				int sec = pc.getSkillEffectTimeSec(L1SkillId.STATUS_FRUIT);
				pc.removeSkillEffect(STATUS_FRUIT);
				//pc.killSkillEffectTimer(STATUS_FRUIT);
				pc.setSkillEffect(STATUS_FRUIT, sec * 1000);
				SC_SkillBrave.send(pc, pc.getId(), 3, pc.getSkillEffectTimeSec(L1SkillId.STATUS_FRUIT));
				SC_SkillBrave.broadcast(pc, pc.getId(), 3, 0);
				
			}
		}
		else if(pInfo.getPassiveId() == MJPassiveID.RESIST_ELEMENT.toInt()) {
			SC_OwnCharAttrDef.send(pc);
			SC_SPMR.send(pc);
		}
		
		return true;
	}
	
	public boolean useItem(L1PcInstance pc, L1ItemInstance item){
		MJPassiveInfo pInfo = fromBookId(item.getItemId());
		if(pInfo == null)
			return false;
		
		if(masterPassive(pc, pInfo))
			pc.getInventory().removeItem(item, 1);
		return true;
	}
	
	public void dispose(){
		if(m_passives != null){
			m_passives.clear();
			m_passives = null;
		}
	}
}
