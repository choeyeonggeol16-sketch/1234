package l1j.server.Template.Builder;

import java.io.IOException;

import l1j.server.Template.PacketHelper.S_BuilderPacket;
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
	
	public S_BuilderPacket build() throws IOException{
		if(isClose())
			throw new IOException("builder data down.");
		
		byte[] b = toArray();
		return new S_BuilderPacket(b.length + 2, b);
	}
	
	public S_BuilderPacket build(boolean isClose) throws IOException{
		if(isClose())
			throw new IOException("builder data down.");
		
		byte[] b = toArray();
		
		if(isClose)
			dispose();
		return new S_BuilderPacket(b.length + 2, b);
	}
}
