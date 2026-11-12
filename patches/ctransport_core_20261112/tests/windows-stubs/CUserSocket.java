package l1j.server.server.model.network.core;
// Only the game-owner dependency is stubbed. CIOServer/Socket/Endpoint,
// PssWindowTransport, FFM calls and the Windows DLL are production code.
class CUserSocket {
 boolean transportNativePacket(long sequence, java.nio.ByteBuffer packet, int length) { throw new AssertionError("unexpected native packet"); }
 int transportNetworkDeadline() { return 5000; }
 void close() {}
 void onTransportClosing() {}
 void onTransportTerminated() {}
 void transportReadCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
 void transportWriteCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
 void transportGatherCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
}
