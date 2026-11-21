package l1j.server.server.storage.sql.property;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface IPropertyHandler {
	public void on_load(String section, SqlPropertyReader reader, ResultSet rs) throws SQLException;
}
