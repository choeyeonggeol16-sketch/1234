package l1j.server.server.model.network.core;
// Only the game-owner dependency is stubbed. CIOServer/Socket/Endpoint,
// PssWindowTransport, FFM calls and the Windows DLL are production code.
class CUserSocket {
 boolean transportNativePacket(long sequence, java.nio.ByteBuffer packet, int length) { throw new AssertionError("unexpected native packet"); }
 int transportNetworkDeadline() { return 5000; }
 boolean isClientConnected(){return false;} boolean supportsConstBuffer(){return false;}
 int pendingSendBytes(){return 0;} CAsyncSendConstBuffer.Source createConstBuffer(byte[] wire){return null;}
 boolean ownsConstBuffer(CAsyncSendConstBuffer.Source source){return false;} void enqueueConstBuffer(CAsyncSendConstBuffer.Source source,int limit){}
 void close() {}
 void onTransportClosing() {}
 void onNativeCloseStatus(int operation,int error) {}
 void onTransportTerminated() {}
 void transportReadCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
 void transportWriteCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
 void transportGatherCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
}
