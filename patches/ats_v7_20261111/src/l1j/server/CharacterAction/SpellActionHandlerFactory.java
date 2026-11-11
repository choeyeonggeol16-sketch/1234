package l1j.server.CharacterAction;

import java.util.HashMap;

import l1j.server.CharacterAction.Magic.BackgroundActionHandler;
import l1j.server.CharacterAction.Magic.DirectionActionHandler;
import l1j.server.CharacterAction.Magic.RunNCallClanActionHandler;
import l1j.server.CharacterAction.Magic.SkillActionHandler;
import l1j.server.CharacterAction.Magic.SummonMonsterActionHandler;
import l1j.server.CharacterAction.Magic.TeleportActionHandler;
import l1j.server.CharacterAction.Magic.TruetargetActionHandler;
import l1j.server.server.model.skill.SkillId;

public class SpellActionHandlerFactory {
	private static final HashMap<Integer, SkillActionHandler> _handlers;
	static{
		_handlers = new HashMap<Integer, SkillActionHandler>(256);
		for(int i=256; i>0; --i) {
			switch(i){
			case SkillId.런_클랜: case SkillId.콜_클랜:
				_handlers.put(i, new RunNCallClanActionHandler());
				break;
			case SkillId.트루_타겟:
				_handlers.put(i, new TruetargetActionHandler());
				break;
			case SkillId.텔레포트: case SkillId.매스_텔레포트:
				_handlers.put(i, new TeleportActionHandler());
				break;
			case SkillId.서먼_몬스터:
				_handlers.put(i, new SummonMonsterActionHandler());
				break;
			case SkillId.파이어_월: case SkillId.라이프_스트림:
				_handlers.put(i, new BackgroundActionHandler());
				break;
			default:
				_handlers.put(i, new DirectionActionHandler());
				break;
			}
		}
	}
	
	public static SkillActionHandler create(int skillId){
		SkillActionHandler default_handler = _handlers.get(skillId);
		if(default_handler == null) {
			return null;
		}
		return default_handler.copy().setSkillId(skillId);
	}
}
