package l1j.server.AutoHuntSystem;
import java.util.Properties;
public final class AtsBudgetTest {
    static void check(boolean v,String m) { if(!v)throw new AssertionError(m); }
    static final class MemoryStore implements AtsBudget.Store {
        long value; int reserves,refunds; boolean fail;
        MemoryStore(long value) {this.value=value;}
        public AtsBudget.Reservation reserve(long n) throws Exception {
            if(fail)throw new Exception("DB failure");
            reserves++;long credit=Math.min(n,value);value-=credit;return new AtsBudget.Reservation(credit,value);
        }
        public void refund(long n) throws Exception {if(fail)throw new Exception("DB failure");refunds++;value+=n;}
    }
    public static void main(String[] args) throws Exception {
        MemoryStore db=new MemoryStore(86400000);AtsBudget b=new AtsBudget(db);
        check(b.start(1_000_000),"start");check(db.value==86390000,"durable pre-debit");
        check(b.remaining(2_501_000_000L)==86397500,"active consumption");
        b.stop(3_251_000_000L);check(db.value==86396750,"refund only unused; retain exact milliseconds");
        int refunds=db.refunds;b.stop(9_000_000_000L);check(db.refunds==refunds,"stop idempotent");
        b=new AtsBudget(db);check(b.start(100_000_000_000L),"restart");
        check(b.remaining(100_000_000_000L)==86396750,"offline time not billed; no new grant");
        b.stop(100_000_000_000L);
        MemoryStore small=new MemoryStore(1500);b=new AtsBudget(small);check(b.start(0),"last partial chunk");
        check(b.remaining(1_499_999_999L)==1,"subsecond boundary");
        check(b.remaining(1_500_000_000L)==0,"expiry");b.stop(2_000_000_000L);
        check(small.value==0,"no refund used time");check(!new AtsBudget(small).start(0),"exhausted refuses restart");
        MemoryStore delayed=new MemoryStore(25000);b=new AtsBudget(delayed);b.start(0);
        check(b.remaining(24_001_000_000L)==999,"delayed tick consumes all elapsed chunks");
        b.stop(24_001_000_000L);check(delayed.value==999,"delayed tick refund");
        MemoryStore longPause=new MemoryStore(86400000);b=new AtsBudget(longPause);b.start(0);
        check(b.remaining(3_600_000_000_000L)==82800000,"one-hour pause charged");
        check(longPause.reserves<=2,"long pause uses bounded transaction count");
        b.stop(3_600_000_000_000L);
        MemoryStore failure=new MemoryStore(20000);b=new AtsBudget(failure);b.start(0);failure.fail=true;
        boolean threw=false;try{b.remaining(11_000_000_000L);}catch(Exception e){threw=true;}
        check(threw,"DB failure does not grant free credit");
        Properties p=new Properties();p.setProperty("MinLevel","10");p.setProperty("MaxLevel","60");p.setProperty("AllowedMaps","0,4");
        AtsRules rules=new AtsRules(p);
        check(rules.rejection(9,0)!=null,"min level");check(rules.rejection(10,0)==null,"min inclusive");
        check(rules.rejection(60,4)==null,"max inclusive");check(rules.rejection(61,4)!=null,"max level");
        check(rules.rejection(30,99)!=null,"forbidden map");
        p.setProperty("AllowedMaps","");p.setProperty("MaxLevel","0");rules=new AtsRules(p);
        check(rules.rejection(120,99)==null,"explicit unrestricted settings");
        p.setProperty("AllowedMaps","0,,4");threw=false;try{new AtsRules(p);}catch(IllegalArgumentException e){threw=true;}
        check(threw,"malformed restriction cannot silently allow all maps");
        System.out.println("PASS ATS: active/offline/restart/expiry/refund/failure/level/map boundaries");
    }
}
