package l1j.server.server.model.network.core.connection;
import l1j.server.server.model.network.buffer.WriteBuffer;
public class CIOFrame { private final WriteBuffer buffer=new WriteBuffer(8192); public WriteBuffer buffer(){return buffer;} }
