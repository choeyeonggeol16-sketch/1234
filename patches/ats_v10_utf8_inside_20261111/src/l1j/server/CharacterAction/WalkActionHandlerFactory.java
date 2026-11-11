package l1j.server.CharacterAction;

import l1j.server.CharacterAction.Walk.WalkActionHandler;

public class WalkActionHandlerFactory {
	public static WalkActionHandler create() {
		return new WalkActionHandler();
	}
}
