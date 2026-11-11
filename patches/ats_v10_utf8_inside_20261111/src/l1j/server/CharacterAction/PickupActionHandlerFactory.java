package l1j.server.CharacterAction;

import l1j.server.CharacterAction.Pickup.PickupActionHandler;

public class PickupActionHandlerFactory {
	public static PickupActionHandler create(){
		return new PickupActionHandler();
	}
}
