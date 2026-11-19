package l1j.server.server;

import java.io.FileInputStream;
import java.util.Properties;

// [19fix-5] NPC/shop action distance gate, config-driven (default 12).
// Standalone helper so C_NPCAction does NOT need to be recompiled from (stale) src.
// Reads NpcActionRange once from ./config/ServerCodes.properties; falls back to 12 on any error.
public final class NpcActionRange {

	private static volatile int _range = -1;

	private NpcActionRange() {
	}

	public static int get() {
		int r = _range;
		if (r > 0) {
			return r;
		}
		synchronized (NpcActionRange.class) {
			if (_range > 0) {
				return _range;
			}
			int v = 12;
            try (FileInputStream fis = new FileInputStream("./config/ServerCodes.properties")) {
                Properties pr = new Properties();
                pr.load(fis);
				String s = pr.getProperty("NpcActionRange");
				if (s != null) {
					int parsed = Integer.parseInt(s.trim());
					if (parsed > 0) {
						v = parsed;
					}
				}
			} catch (Exception e) {
				v = 12;
			}
			_range = v;
			return v;
		}
	}
}
