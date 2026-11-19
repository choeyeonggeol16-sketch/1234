package l1j.server.server.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

//import kr.poby.server.exception.SqlException;
//import kr.poby.server.utils.collections.Lists;
import l1j.server.DatabaseFactory;

public class L1QueryUtil {
	private static void setupPrepareStatement(PreparedStatement pstm,
			Object[] args) throws SQLException {
		for (int i = 0; i < args.length; i++)
			pstm.setObject(i + 1, args[i]);
	}

	// public static <T> T selectFirst(EntityFactory<T> factory, String sql,
	// Object[] args)
	// {
	// List result = selectAll(factory, sql, args);
	// return result.isEmpty() ? null : result.get(0);
	// }

	@SuppressWarnings("resource")
	public static <T> List<T> selectAll(EntityFactory<T> factory, String sql,
			Object[] args) {
		List<T> result = new ArrayList<T>();
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(sql);
			setupPrepareStatement(pstm, args);
			rs = pstm.executeQuery();
			while (rs.next()) {
				T entity = factory.fromResultSet(rs);
				if (entity == null)
					throw new NullPointerException(String.format("%s returned null.", factory.getClass().getSimpleName()));
				result.add(entity);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return result;
	}

	public static boolean execute(Connection con, String sql, Object[] args) {
		boolean bool = false;
		PreparedStatement pstm = null;
		try {
			pstm = con.prepareStatement(sql);
			setupPrepareStatement(pstm, args);
			bool = pstm.execute();
			return bool;
		} catch (SQLException e) {
			try {
				throw new SQLException(e);
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		} finally {
			SQLUtil.close(pstm);
		}
		return bool;// throw localObject;
	}

	public static boolean execute(String sql, Object[] args) {
		Connection con = null;
		boolean bool = false;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			bool = execute(con, sql, args);
			return bool;
		} catch (Exception e) {
			try {
				throw new SQLException(e);
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		} finally {
			SQLUtil.close(con);
		}
		return bool;// throw localObject;
	}

	public static abstract interface EntityFactory<T> {
		public abstract T fromResultSet(ResultSet paramResultSet)
				throws SQLException;
	}
}