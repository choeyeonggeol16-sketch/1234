package l1j.server.Template.PacketHelper;

import java.io.IOException;

import l1j.server.server.serverpackets.ServerBasePacket;

public class S_BuilderPacket extends ServerBasePacket{
	public S_BuilderPacket(int capacity, byte[] b){
		super(capacity);
		writeByte(b);
	}

	@Override
	public byte[] getContent() throws IOException {
		return getBytes();
	}
}
