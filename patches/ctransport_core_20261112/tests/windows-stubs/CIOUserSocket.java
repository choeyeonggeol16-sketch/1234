package l1j.server.server.model.network.core;
// Only the game-owner dependency is stubbed. CTransportServer/Socket/Endpoint,
// PssWindowTransport, FFM calls and the Windows DLL are production code.
class CIOUserSocket {
 void onTransportClosing() {}
 void onTransportTerminated() {}
 void transportReadCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
 void transportWriteCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
 void transportGatherCompleted(int bytes) { throw new AssertionError("unexpected gameplay callback"); }
}
