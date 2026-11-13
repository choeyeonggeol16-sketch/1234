package l1j.server.server.model.network.core;
import java.nio.*;
/** Reproduces only the production diagnostic entry point, not game execution. */
public final class TraceIdentityGap {
 public static void main(String[] args) {
  var trace=CNetworkDiagnostics.open("diagnostic-fixture",42);
  if(trace==null)throw new AssertionError("diagnostics must be enabled");
  for(int command:new int[]{0x03e9,0x03ea}) {
   ByteBuffer decoded=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
   decoded.put((byte)0x51).putShort((short)command).put(new byte[5]).flip();
   // These are exactly executeNativePacket's diagnostic arguments.
   var work=CNetworkDiagnostics.begin(trace,decoded.get(0)&255,decoded.remaining());
   CNetworkDiagnostics.end(work);
  }
  String recent=CNetworkDiagnostics.recent(trace.id),counts=CNetworkDiagnostics.packetCounts();
  if(recent.lines().filter(l->l.contains("opcode=81")).count()!=2||!counts.contains("opcode=81 count=2"))
   throw new AssertionError("production diagnostic contract changed; re-audit gap");
  System.out.println("Input:opcode0x51/command0x03e9 and opcode0x51/command0x03ea");
  System.out.print(recent);System.out.print(counts);
  CNetworkDiagnostics.close(trace);
  System.out.println("REPRODUCED:both different L1 extended commands become opcode81;no subtype identity reaches production diagnostics");
 }
}
