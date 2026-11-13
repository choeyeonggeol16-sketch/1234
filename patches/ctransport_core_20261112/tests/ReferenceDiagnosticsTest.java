package l1j.server.server.model.network.core;
public final class ReferenceDiagnosticsTest {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  long[] report=new long[16+16*105];report[0]=0x4354524600010000L;report[2]=3;report[8]=16;report[9]=31;report[10]=105;report[11]=16;report[15]=128;
  for(int i=0;i<16;i++){int p=16+i*105;report[p]=i+1;report[p+6]=2;report[p+7]=1;report[p+8]=i%4;report[p+9]=(16L<<32)|1;report[p+12+22*3]=1;report[p+12+22*3+1]=(1L<<32)|891;}
  String text=CPoolDiagnostics.referenceReport(report);
  check(CPoolDiagnostics.referenceActive(report)==3&&text.contains("ORT_VERIFIER_RETAIN=1")&&text.contains("CTransport.cpp:891"),"reference ABI/file mapping");
  check(text.lines().filter(l->l.startsWith("#")).count()==8&&!text.contains("#1 ")&&text.contains("#16 "),"unbounded or incorrectly ordered report");
  check(text.contains("추가 참조 없음")&&text.contains("잔여 참조 의심")&&text.contains("검사 취소")&&text.contains("정리 대기"),"reference classifications");
  report[8]=17;check(CPoolDiagnostics.referenceActive(report)==0&&CPoolDiagnostics.referenceReport(report).contains("미지원"),"malformed shape accepted");
  check(CPoolDiagnostics.referenceReport(new long[0]).contains("미지원"),"old DLL handling");
  System.out.println("PASS bounded manager reference report:31 kinds,file/line mapping,4 statuses,latest8 of16,malformed/old DLL fallback");
 }
}
