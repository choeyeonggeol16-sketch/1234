package l1j.server.server.model.network.core;

import java.nio.*;
import java.util.*;
import l1j.server.server.model.network.buffer.ReadBuffer;
import l1j.server.server.model.network.packet.CPacketHandler;
import l1j.server.server.packets.Protocol.CSProto;

/** Actual production decoder/route registry. No game client/DB is constructed. */
public final class ExtendedPacketDecoderTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void decode(int opcode,int command,String kind,boolean shortHeader)throws Exception {
  var trace=CNetworkDiagnostics.open("actual-decoder",72);check(trace!=null,"trace");
  byte[] frame=new byte[shortHeader?2:16];frame[0]=(byte)opcode;frame[1]=(byte)command;
  if(!shortHeader)frame[2]=(byte)(command>>>8);byte[] before=frame.clone();
  ReadBuffer reader=new ReadBuffer();reader.reset(ByteBuffer.wrap(frame),0,frame.length);reader.readUnsignedByte();
  var work=CNetworkDiagnostics.begin(trace,opcode,frame.length);boolean delegateReached=false;
  try {
   try{CPacketHandler.HANDLERS[opcode].execute(reader,null);}
   catch(NullPointerException expected){
    // Decode succeeds until the unchanged handler boundary checks client.
    StackTraceElement top=expected.getStackTrace()[0];
    check(top.getClassName().equals(CPacketHandler.class.getName())&&top.getMethodName().startsWith("handle"),"unexpected decoder NPE:"+top);
    delegateReached=true;
   }
   String active=CNetworkDiagnostics.workers(),recent=CNetworkDiagnostics.recent(trace.id);
   if(kind==null)check(!recent.contains(" protocol="),"short header invented an ID");
   else {
    int expected=kind.equals("CRAFT")?command&255:command;
    String identity="protocol="+kind+" command="+expected;
    check(active.contains(identity)&&recent.contains(identity),"production route missing:"+identity+"\n"+recent);
   }
   check(reader.position()==1&&Arrays.equals(frame,before),"diagnostic hook mutated cursor or packet bytes");
   check(shortHeader||delegateReached,"packet delegate not reached");
  } finally {CNetworkDiagnostics.end(work);CNetworkDiagnostics.close(trace);}
 }
 public static void main(String[] args)throws Exception {
  check(!CSProto.isRoutable(1001)&&!CSProto.isRoutable(1002)&&!CSProto.isRoutable(0x44f9),"fixture route identities changed");
  decode(81,1001,"ACTION_UI",false);decode(81,1002,"ACTION_UI",false);
  decode(81,0x44f9,"CRAFT",false);decode(137,65535,"EXTENDED",false);
  int routed=-1;for(int id=0;id<=65535;id++)if(CSProto.isRoutable(id)){routed=id;break;}
  check(routed>=0,"no production protobuf routes");decode(81,routed,"PROTOBUF",false);
  decode(81,1,null,true);decode(137,1,null,true);
  System.out.println("PASS actual production CPacketHandler+CSProto:ActionUI1001/1002,craft byte subtype,protobuf precedence route,extended unsigned65535,short headers;unchanged delegate/cursor/bytes");
 }
}
