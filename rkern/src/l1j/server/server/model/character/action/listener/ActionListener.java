package l1j.server.server.model.character.action.listener;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;

public interface ActionListener {
	public boolean eqauls_action(String action);
	public String to_action(L1PcInstance pc, L1Object target);
	public ActionListener deep_copy();
	public ActionListener deep_copy(ActionListener listener);
	public ActionListener drain(ActionListener listener);
	public boolean is_action();
	public void dispose();
}
