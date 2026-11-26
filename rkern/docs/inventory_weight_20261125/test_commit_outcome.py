"""Compile production outcome methods with scripted JDBC failures, no database."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / "src/l1j/server/server/storage/cached/CTransaction.java").read_text(encoding="utf-8")
def extract(start, end):
    a = source.index(start)
    return source[a:source.index(end, a)].rstrip()

methods = extract("    private static boolean claimReceipt(", "    private static final String[] TRANSACTION_TABLES")
methods += "\n" + extract("    private static void persistAtomic(", "    public static void writeCharacterMoveRow(")
harness = r'''
import java.sql.*;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.logging.*;
public class CommitAudit {
    private static final Logger LOG = Logger.getLogger("commit-audit");
    static final String ID = "00000000-0000-0000-0000-000000000001";
    static class OutcomeUnknownException extends SQLException {
        OutcomeUnknownException(String message, Throwable cause) { super(message,"08007",cause); }
    }
    interface Mutation { void apply(Connection c) throws SQLException; }
    static class Plan {
        boolean receipt, duplicate, wrongOwner, invalidEngine, closeFails, rollbackFails, commitFails, resetFails;
        int mutations, inserts, commits, rollbacks, resets;
        Connection connection() {
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)-> {
                switch(m.getName()) {
                    case "getAutoCommit": return true;
                    case "setAutoCommit": if((boolean)a[0]) { resets++; if(resetFails)throw new SQLException("reset"); } return null;
                    case "commit": commits++; if(commitFails)throw new SQLException("ACK lost"); return null;
                    case "rollback": rollbacks++; if(rollbackFails)throw new SQLException("rollback"); return null;
                    case "close": if(closeFails)throw new SQLException("close"); return null;
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });
        }
    }
    static final ArrayDeque<Plan> plans=new ArrayDeque<>();
    static Plan current;
    static class DatabaseFactory {
        static DatabaseFactory getInstance(){return new DatabaseFactory();}
        Connection getConnection(){current=plans.remove();return current.connection();}
    }
    static boolean hasReceipt(Connection c,String id,String kind,int a,int b,boolean readCurrent) throws SQLException {
        if(current.wrongOwner) throw new SQLException("wrong receipt identity");
        return current.receipt;
    }
    static void insertReceipt(Connection c,String id,String kind,int a,int b) throws SQLException {
        current.inserts++;
        if(current.receipt || current.duplicate) throw new SQLException("duplicate","23000",1062);
    }
    static void requireTransactionalTables(Connection c,String... tables) throws SQLException {
        if(current.invalidEngine) throw new SQLException("nontransactional receipt store");
    }
'''
checks = r'''
    static int tests;
    static void check(boolean ok){if(!ok)throw new AssertionError("case "+tests);}
    static void run(Plan p, Plan verify, boolean mutationFails, int expected) throws Exception {
        plans.clear();plans.add(p);if(verify!=null)plans.add(verify);
        int outcome=0;
        try {persistAtomic(ID,"ECONOMY",1,2,new Mutation[]{c->{p.mutations++;if(mutationFails)throw new SQLException("mutation");}});}
        catch(OutcomeUnknownException e){outcome=2;}
        catch(SQLException e){outcome=1;}
        check(outcome==expected);check(plans.isEmpty());tests++;
    }
    public static void main(String[] args) throws Exception {
        LOG.setLevel(Level.OFF);
        for(int flags=0;flags<8;flags++){
            Plan p=new Plan();p.receipt=true;p.rollbackFails=(flags&1)!=0;p.closeFails=(flags&2)!=0;p.resetFails=(flags&4)!=0;
            run(p,null,false,0);check(p.mutations==0&&p.inserts==1&&p.commits==0&&p.rollbacks==1&&p.resets==(p.rollbackFails?0:1));
        }
        for(int flags=0;flags<4;flags++){
            Plan p=new Plan();p.commitFails=true;Plan v=new Plan();v.receipt=(flags&1)!=0;v.closeFails=(flags&2)!=0;
            run(p,v,false,v.receipt?0:2);check(p.mutations==1&&p.inserts==1&&p.commits==1&&p.resets==0);
        }
        for(int flags=0;flags<4;flags++){
            Plan p=new Plan();p.closeFails=(flags&1)!=0;p.resetFails=(flags&2)!=0;
            run(p,null,false,0);check(p.mutations==1&&p.inserts==1&&p.commits==1&&p.resets==1);
        }
        Plan p=new Plan();run(p,null,true,1);check(p.rollbacks==1&&p.commits==0&&p.inserts==1);
        p=new Plan();p.rollbackFails=true;Plan v=new Plan();run(p,v,true,2);check(p.resets==0&&p.commits==0);
        p=new Plan();p.duplicate=true;run(p,null,false,1);check(p.mutations==0&&p.rollbacks==1);
        p=new Plan();p.receipt=true;p.wrongOwner=true;run(p,null,false,1);check(p.mutations==0&&p.rollbacks==1);
        p=new Plan();p.invalidEngine=true;run(p,null,false,1);check(p.inserts==0&&p.mutations==0&&p.rollbacks==1);
        System.out.println("PASS "+tests+" JDBC cleanup / receipt / ACK outcome cases");
    }
}
'''
with tempfile.TemporaryDirectory() as work:
    java = Path(work) / "CommitAudit.java"
    java.write_text(harness + methods + checks, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-cp", work, "CommitAudit"], check=True)
