package l1j.server.server.storage.sql;

import l1j.server.server.storage.sql.handler.Handler;

public interface Executor {
	public void execute(String query, Handler handler);
}
