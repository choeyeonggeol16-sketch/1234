package l1j.server.Template.SqlHelper;

import l1j.server.Template.SqlHelper.Handler.Handler;

public interface Executor {
	public int execute(String query, Handler handler);
}
