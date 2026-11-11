package l1j.server.Template.SqlHelper.Handler;

import java.sql.PreparedStatement;

public interface Handler {
	public void handle(PreparedStatement pstm) throws Exception;
}
