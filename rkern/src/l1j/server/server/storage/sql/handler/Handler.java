package l1j.server.server.storage.sql.handler;

import java.sql.PreparedStatement;

public interface Handler {
	public void handle(PreparedStatement pstm) throws Exception;
}
