package l1j.server.server.model.network.core.connection;

import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
// Only the game-owner dependency is stubbed. CIOServer/Socket/Endpoint,
// PssWindowTransport, FFM calls and the Windows DLL are production code.
public class CUserSocket {
 public boolean transportNativePacket(long sequence, java.nio.ByteBuffer packet, int length) { throw new AssertionError("unexpected native packet"); }
 public int transportNetworkDeadline() { return 5000; }
 public boolean isClientConnected(){return false;} public boolean supportsConstBuffer(){return false;}
 public int pendingSendBytes(){return 0;} public CAsyncSendConstBuffer.Source createConstBuffer(byte[] wire){return null;}
 public boolean ownsConstBuffer(CAsyncSendConstBuffer.Source source){return false;} public void enqueueConstBuffer(CAsyncSendConstBuffer.Source source,int limit){}
 public void close() {}
 public void onTransportClosing() {}
 public void onNativeCloseStatus(int operation,int error) {}
 public void onTransportTerminated() {}
 public void transportReadCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
 public void transportWriteCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
 public void transportGatherCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
}
