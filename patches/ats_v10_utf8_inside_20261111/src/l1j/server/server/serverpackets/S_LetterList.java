package l1j.server.server.serverpackets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class S_LetterList extends ServerBasePacket {

    private static Logger _log = Logger.getLogger(S_Letter.class.getName());

    private static final String S_LETTERLIST = "[S] S_LetterList";
    
    public static final int WRITE_TYPE_PRIVATE_MAIL = 80;
    public static final int WRITE_TYPE_BLOODPLEDGE_MAIL = 81;

    public static final int TYPE_RECEIVE = 0;
    public static final int TYPE_SEND = 1;
    
    public S_LetterList(L1PcInstance pc, int type, int count) {
        buildPacket(pc, type, count);
    }

    private void buildPacket(L1PcInstance pc, int type, int count) { // 이부분에서 문제 
        Connection con = null;
        PreparedStatement pstm = null;
        PreparedStatement pstm1 = null;

        ResultSet rs = null;
        ResultSet rs1 = null;
        int cnt = 0;
        try {
            con = L1DatabaseFactory.getInstance().getConnection();
            pstm = con.prepareStatement("SELECT * FROM letter WHERE receiver=? AND template_id = ? order by date limit ?  ");

            pstm.setString(1, pc.getName());
            pstm.setInt(2, type);
            pstm.setInt(3, count);
            rs = pstm.executeQuery();

            pstm1 = con.prepareStatement(" SELECT count(*) as cnt FROM letter WHERE receiver=? AND template_id = ? order by date limit ?  ");
            pstm1.setString(1, pc.getName());
            pstm1.setInt(2, type);
            pstm1.setInt(3, count);
            rs1 = pstm1.executeQuery();
            if (rs1.next()) {
                cnt = rs1.getInt(1);
            }
            writeC(Opcodes.S_OPCODE_LETTER);
            writeC(type);
            writeH(cnt);
            while (rs.next()) {
            	Calendar cal = timestampToCalendar(rs.getTimestamp(5));
            	int year = cal.get(Calendar.YEAR) - 2000;
				int month = cal.get(Calendar.MONTH) + 1;
				int day = cal.get(Calendar.DATE);
				
                writeD(rs.getInt(1));
                writeC(rs.getInt(9));
                
                writeC(year);
                writeC(month);
                writeC(day);
                
                writeS(rs.getString(3));
                writeSS(rs.getString(7));
            }
        } catch (SQLException e) {
            e.printStackTrace();//이로그를 없애면 안나옴
        } finally {
            SQLUtil.close(rs);
            SQLUtil.close(rs1);
            SQLUtil.close(pstm);
            SQLUtil.close(pstm1);
            SQLUtil.close(con);
        }
    }
    
	private Calendar timestampToCalendar(Timestamp ts) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(ts.getTime());
		return cal;
	}

    public S_LetterList(int writeType, int id, int type, String name, String title) {
        writeC(Opcodes.S_OPCODE_LETTER);
        writeC(writeType);
        writeD(id);
        writeC(type); // 0:수신, 1:발신
        writeS(name);
        writeSS(title);
    }

    public S_LetterList(L1PcInstance pc, int type, int id, int read) {
        Connection con = null;
        PreparedStatement pstm = null;
        ResultSet rs = null;
        try {
            con = L1DatabaseFactory.getInstance().getConnection();
            pstm = con.prepareStatement("SELECT * FROM letter WHERE item_object_id=? ");
            pstm.setInt(1, id);
            rs = pstm.executeQuery();
            writeC(Opcodes.S_OPCODE_LETTER);
            writeC(type);
            if (rs.next()) {
                writeD(rs.getInt("item_object_id"));
                writeSS(rs.getString("content"));
            }
        } catch (Exception e) {
            _log.log(Level.WARNING, "", e);
        } finally {
            SQLUtil.close(rs);
            SQLUtil.close(pstm);
            SQLUtil.close(con);
        }
    }
  
    public S_LetterList(L1PcInstance pc, int type, int id, boolean value) {
        writeC(Opcodes.S_OPCODE_LETTER);
        writeC(type); 
        writeD(id); 
        writeC(value ? 1 : 0);
    }
    
    @Override
    public byte[] getContent() {
    	return getBytes();
    }

    public String getType() {
        return S_LETTERLIST;
    }
}
