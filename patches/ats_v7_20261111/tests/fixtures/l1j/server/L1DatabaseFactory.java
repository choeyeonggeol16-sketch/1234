package l1j.server;
import java.sql.*;
public class L1DatabaseFactory {
    private static final L1DatabaseFactory INSTANCE=new L1DatabaseFactory();
    public static L1DatabaseFactory getInstance(){return INSTANCE;}
    public Connection getAtsConnection(){return getConnection();}
    public Connection getConnection(){
        try{return DriverManager.getConnection("jdbc:h2:mem:ats;MODE=MySQL;DB_CLOSE_DELAY=-1");}
        catch(SQLException e){throw new IllegalStateException(e);}
    }
}
