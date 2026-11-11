package l1j.server.CharacterAction;

import l1j.server.CharacterAction.Attack.AttackActionHandler;

public class AttackActionHandlerFactory {
	public static AttackActionHandler create(){
		return new AttackActionHandler();
	}
}
