package l1j.server.DogFightSystem;

import l1j.server.Templates.L1Item;

public class DogFightTicket extends L1Item {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public DogFightTicket() {
	}

	private boolean _stackable;

	@Override
	public boolean isStackable() {
		return _stackable;
	}

	public void set_stackable(boolean stackable) {
		_stackable = stackable;
	}

}
