package l1j.server.Template.Chain.Etc;

import l1j.server.Template.Chain.MJAbstractActionChain;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class AdenaPickupChain extends MJAbstractActionChain<AdenaPickupHandler> {
	public static void setup_blessing_effect() {
		getInstance().add_handler(new BlessingEffectHandler());
	}

	private static AdenaPickupChain _instance;

	public static AdenaPickupChain getInstance() {
		if (_instance == null)
			_instance = new AdenaPickupChain();
		return _instance;
	}

	private AdenaPickupChain() {
		super();
	}

	public int pickup(L1PcInstance owner, ItemInstance item, int amount) {
		int destination_amount = amount;
		for (AdenaPickupHandler handler : m_handlers) {
			destination_amount += handler.do_pickup(owner, item, amount);
			if (destination_amount <= 0)
				destination_amount = 1;
		}
		return destination_amount;
	}

	private static class BlessingEffectHandler implements AdenaPickupHandler {
		@Override
		public int do_pickup(L1PcInstance owner, ItemInstance item, int amount) {
			return is_blessing_effect(owner, item) ? (int) (amount * (0.2)) : -((int) (amount * (0)));
		}

		private static boolean is_blessing_effect(L1PcInstance pc, ItemInstance item) {
  
			if (pc.getInventory().checkItem(32561)) { 
				int mapId = pc.getMapId(); 
				if (mapId >= 0)
					return true;
 
			}
			return false;
		}
	}

}
