package l1j.server.server.model.network.core;
public final class PoolDiagnosticsTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static long[][] javaPool(long used){return new long[][]{{4096,8,used,3,3-used,8192,0},{65536,4,0,0,0,0,0},{16384,16,0,0,0,0,0},{65536,8,0,0,0,0,0}};}
 static long[] nativePool(long used){long[] p=new long[18];p[8]=0x4354504c00010000L;p[9]=300;p[10]=used;p[11]=5;p[12]=5-used;p[13]=200;p[17]=5000000;return p;}
 public static void main(String[] args){
  CPoolDiagnostics d=new CPoolDiagnostics();long[] noOwners=new long[6];
  check(d.sample(-100_000_000_000L,javaPool(1),nativePool(1),new long[]{0,0,0,0,1,0}).contains("보유 중"),"live shared producer falsely called a leak");
  check(d.sample(-90_000_000_000L,javaPool(1),nativePool(1),noOwners).contains("확인 대기"),"initial idle suspect");
  check(d.sample(-31_000_000_000L,javaPool(1),nativePool(1),noOwners).contains("확인 대기"),"early suspect");
  check(d.sample(-29_000_000_000L,javaPool(1),nativePool(1),noOwners).contains("미회수 의심"),"stable idle not detected / negative monotonic clock");
  check(d.sample(0,javaPool(0),nativePool(0),noOwners).contains("미반납 버퍼 없음"),"recovery not cleared");
  check(d.sample(1,javaPool(0),new long[8],noOwners).contains("측정 불완전"),"old DLL prefix misreported as zero usage");
  for(int i=0;i<200;i++)d.sample((long)i*1_000_000_000,javaPool(0),nativePool(0),noOwners);
  String text=d.sample(201_000_000_000L,javaPool(0),nativePool(0),noOwners);
  check(text.lines().filter(l->l.matches("[0-9]+ / [0-9]+ / -?[0-9]+ / [0-9]+")).count()==60,"unbounded history");
  System.out.println("PASS pool diagnostic classification:valid holders,60s stable idle suspicion,negative clock,recovery,unavailable extension,60 sample bound;no forced reclaim");
 }
}
