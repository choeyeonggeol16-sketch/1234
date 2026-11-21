package l1j.server.server.shared.builder;

import java.io.IOException;

import l1j.server.server.model.network.core.sc.packet.SC_BuilderPacket;
import l1j.server.server.utils.BytesOutputStream;

public class ServerPacketBuilder extends BytesOutputStream{
	public ServerPacketBuilder(int capacity){
		super(capacity);
	}
	
	public ServerPacketBuilder addC(int i) throws IOException{
		write(i);
		return this;
	}
	
	public ServerPacketBuilder addH(int h) throws IOException{
		writeH(h);
		return this;
	}
	
	public ServerPacketBuilder addD(int d) throws IOException{
		writeD(d);
		return this;
	}
	
	public ServerPacketBuilder addS(String s) throws IOException{
		writeS(s);
		return this;
	}
	
	public ServerPacketBuilder addS2(String s) throws IOException{
		writeS2(s);
		return this;
	}
	
	public ServerPacketBuilder addBit(long l) throws IOException{
		writeBit(l);
		return this;
	}

	public ServerPacketBuilder addBytes(byte[] data, int offset, int length) throws IOException{
		write(data, offset, length);
		return this;
	}
	
	public ServerPacketBuilder addBytes(byte[] data) throws IOException{
		writeBytes(data);
		return this;
	}
	
	public SC_BuilderPacket build() throws java.io.IOException {
		if (isClose())
			throw new java.io.IOException("builder data down.");
		
		byte[] b = toArray();
		return new SC_BuilderPacket(b);
	}
	
	public SC_BuilderPacket build(boolean isClose) throws java.io.IOException {
		if (isClose())
			throw new java.io.IOException("builder data down.");
		
		byte[] b = toArray();
		
		if (isClose)
			dispose();
			
		return new SC_BuilderPacket(b);
	}
}
