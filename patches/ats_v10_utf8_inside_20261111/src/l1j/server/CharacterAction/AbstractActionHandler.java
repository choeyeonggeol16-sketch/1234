package l1j.server.CharacterAction;

import l1j.server.server.model.Instance.L1PcInstance;

public abstract class AbstractActionHandler {
	
	protected L1PcInstance owner;
	public abstract void handle();
	public abstract boolean validation();
	public abstract int getRegistIndex();
	public abstract long getInterval();
	
	public void register() {
		if(owner != null) {
			owner.registerActionHandler(getRegistIndex(), this);
		}
	}
	
	public void unregister() {
		if(owner != null) {
			owner.unreigsterActionHandler(getRegistIndex());
		}
	}
	
	public void dispose() {
		owner = null;
	}
}
