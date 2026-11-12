package l1j.server.server.model.network.core;
import java.nio.file.*;
import java.security.*;
public class TransportConfigTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception {
  var old=CIOTransportConfig.select("hpsocket");var next=CIOTransportConfig.select("rkern");
  check(old.path().toString().equals("CIOWinTransport.dll")&&!old.rkernNative(),"original engine changed");
  check(next.path().toString().equals("RKernCIOTransport.dll")&&next.rkernNative(),"RKern native selection");
  check(CIOTransportConfig.select("nc").equals(next),"legacy option did not select the renamed core");
  for(String value:new String[]{"", "RKERN", "nio2", "../other.dll"}){
   try{CIOTransportConfig.select(value);throw new AssertionError("silent fallback "+value);}catch(java.io.IOException expected){}
  }
  check(CIOTransportConfig.selected().equals(old),"default no longer uses original engine");
  System.setProperty(CIOTransportConfig.PROPERTY,"rkern");check(CIOTransportConfig.selected().equals(next),"explicit property ignored");
  System.clearProperty(CIOTransportConfig.PROPERTY);
  if(args.length>0){
   byte[] bytes=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(args[0])));
   check(java.util.HexFormat.of().formatHex(bytes).equals(next.sha256()),"pinned DLL build mismatch");
  }
  System.out.println("PASS explicit native choice, unchanged default, unknown engines rejected"+(args.length>0?", current DLL SHA256 pin":"; DLL hash check skipped (no --native-dll)"));
 }
}
