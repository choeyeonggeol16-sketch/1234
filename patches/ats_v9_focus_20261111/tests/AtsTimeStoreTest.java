package l1j.server.AutoHuntSystem;
import java.sql.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import l1j.server.L1DatabaseFactory;
public class AtsTimeStoreTest {
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    static long balance(int id)throws Exception{
        try(Connection c=L1DatabaseFactory.getInstance().getConnection();PreparedStatement q=c.prepareStatement("SELECT remaining_millis FROM character_ats_time WHERE char_id=?")){
            q.setInt(1,id);try(ResultSet r=q.executeQuery()){check(r.next(),"row exists");return r.getLong(1);}
        }
    }
    public static void main(String[] args)throws Exception{
        try(Connection c=L1DatabaseFactory.getInstance().getConnection();Statement s=c.createStatement()){
            s.execute("CREATE TABLE character_ats_time (char_id INT PRIMARY KEY, remaining_millis BIGINT NOT NULL)");
        }
        AtsTimeStore db=new AtsTimeStore(1,25000);
        AtsBudget.Reservation r=db.reserve(10000);check(r.credit==10000&&r.balance==15000,"first grant/pre-debit");
        db.refund(7500);check(balance(1)==22500,"unused refund durable");
        AtsTimeStore relog=new AtsTimeStore(1,86400000);r=relog.reserve(10000);
        check(r.balance==12500,"relogin/config change cannot regrant time");
        AtsTimeStore zero=new AtsTimeStore(2,0);check(zero.reserve(10000).credit==0,"zero account refused");
        ExecutorService pool=Executors.newFixedThreadPool(4);AtomicLong total=new AtomicLong();
        java.util.List<Future<?>> jobs=new java.util.ArrayList<>();
        for(int i=0;i<4;i++)jobs.add(pool.submit(()->{try{
            AtsTimeStore concurrent=new AtsTimeStore(3,100000);
            for(int k=0;k<5;k++)total.addAndGet(concurrent.reserve(10000).credit);
        }catch(Exception e){throw new RuntimeException(e);}}));
        for(Future<?> job:jobs)job.get();pool.shutdown();
        check(total.get()==100000&&balance(3)==0,"concurrent reservations conserve time");
        System.out.println("PASS JDBC store (H2 MySQL mode): grant once/relogin/refund/zero/concurrent reserve conservation");
    }
}
