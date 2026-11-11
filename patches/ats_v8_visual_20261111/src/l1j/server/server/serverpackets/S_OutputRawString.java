package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;

public class S_OutputRawString extends ServerBasePacket {

	private static final String _S_OUTPUT_RAW_STRING = "[S] S_OutputRawString";
	private static final String _HTMLID = "deposit";

	private byte[] _byte = null;

	public S_OutputRawString(int objId, String name, String text) {
		if (text.length() > 0) {
			buildPacket(objId, _HTMLID, name, text);
		} else {
			close(objId);
		}
	}

	public void close(int objId) {
		buildPacket(objId, null, null, null);
	}

	private void buildPacket(int objId, String html, String name, String text) {
		writeC(Opcodes.S_OPCODE_SHOWHTML);
		writeD(objId);
		writeS(html);
		writeH(0x02);
		writeH(0x02);
		writeS(name);
		writeS(text);
	}

	@Override
	public byte[] getContent() {
		if (_byte == null) {
			_byte = getBytes();
		}
		return _byte;
	}

	@Override
	public String getType() {
		return _S_OUTPUT_RAW_STRING;
	}
}
