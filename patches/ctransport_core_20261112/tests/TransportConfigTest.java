package l1j.server.server.model.network.core;
import java.nio.file.*;
import java.security.*;
public class TransportConfigTest {
 public static void main(String[] args) throws Exception {
  if(!CTransportConfig.DLL.toString().equals("CTransport.dll") || CTransportConfig.ABI!=0x20000)throw new AssertionError("mandatory native ABI");
  String src=Files.readString(Path.of("src/l1j/server/server/model/network/core/CTransportServer.java"));
  if(src.contains("CIOTransportConfig")||src.contains("hpsocket")||src.contains("rkern.cio.transport"))throw new AssertionError("old transport selector remains");
  byte[] bytes=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(args[0])));
  if(!java.util.HexFormat.of().formatHex(bytes).equals(CTransportConfig.DLL_SHA256))throw new AssertionError("DLL hash mismatch");
  System.out.println("PASS mandatory CTransport.dll ABI 0x20000/hash; no old backend selector");
 }
}
