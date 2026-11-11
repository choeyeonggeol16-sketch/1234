package l1j.server.DTSSystem;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class IODTSLoader {
	private static IODTSLoader _instance;
	private HashMap<Integer, IODTSElement> _elements = load();

	public static IODTSLoader getInstance() {
		if (_instance == null) {
			_instance = new IODTSLoader();
		}
		return _instance;
	}

	public static void release() {
		if (_instance != null) {
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload() {
		IODTSLoader old = _instance;
		_instance = new IODTSLoader();
		if (old != null) {
			old.dispose();
		}
	}

	private IODTSLoader() {
	}

	private HashMap<Integer, IODTSElement> load() {
		final HashMap<Integer, IODTSElement> elements = new HashMap<Integer, IODTSElement>(256);
		Selector.exec("select * from etcitem_teleport_scroll", new FullSelectorHandler() {
			public void result(ResultSet rs) throws Exception {
				while (rs.next()) {
					IODTSElement element = IODTSElement.newInstance(rs);
					elements.put(element.get_item_id(), element);
				}
			}
		});
		return elements;
	}

	public boolean use_item(L1PcInstance pc, ItemInstance item) {
		IODTSElement element = _elements.get(item.getItemId());
		if (element == null) {
			return false;
		}
		element.use(pc, item);
		return true;
	}

	public void dispose() {
		if (_elements != null) {
			_elements.clear();
			_elements = null;
		}
	}
}