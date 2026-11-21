package l1j.server.server.model.item.teleport.designated;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class DesignatedTeleportLoader{
	private static DesignatedTeleportLoader _instance;
	public static DesignatedTeleportLoader getInstance(){
		if(_instance == null)
			_instance = new DesignatedTeleportLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		DesignatedTeleportLoader old = _instance;
		_instance = new DesignatedTeleportLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, DesignatedTeleportElement> _elements;
	private DesignatedTeleportLoader(){
		_elements = load();
	}
	
	private HashMap<Integer, DesignatedTeleportElement> load(){
		HashMap<Integer, DesignatedTeleportElement> elements = new HashMap<Integer, DesignatedTeleportElement>(256);
		Selector.exec("select * from tb_designate_teleport_scroll", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){				
					DesignatedTeleportElement element = DesignatedTeleportElement.newInstance(rs);
					elements.put(element.get_item_id(), element);
				}
			}
		});
		
		return elements;
	}

	public boolean use_item(L1PcInstance pc, L1ItemInstance item){
		DesignatedTeleportElement element = _elements.get(item.getItemId());
		if(element == null)
			return false;
		
		if (pc.hasSkillEffect(L1SkillId.버닝샷)) {
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
			SC_SystemMessage.send(pc, "\\fY버닝샷중에는 이동 및 귀환이 불가합니다.");
			return false;
		}
		if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
			return false;
		}
		
		if (pc.hasSkillEffect(L1SkillId.이터디스)) {
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
			return false;
		}
		
		element.use(pc, item);
		return true;
	}
	
	public void dispose(){
		if(_elements != null){
			_elements.clear();
			_elements = null;
		}
	}
}
