package l1j.server.server.model.network.core.sc.packet;

import java.nio.charset.Charset;

import l1j.server.Config;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_NewChat {

	private static final Charset MS949 = Charset.forName("MS949");
	private static final byte[] EMPTY_BYTES = new byte[0];

    private SC_NewChat() {}

    public static final class ChatData {
        private final int type, chatType, pcObjId, pcX, pcY, rankLevel;
        private final byte[] textBytes, targetBytes, senderBytes;

        private ChatData(L1PcInstance sender, int type, int chatType, String text, String target) {
            this.type = type;
            this.chatType = chatType;
            textBytes = text == null ? EMPTY_BYTES : text.getBytes(MS949);
            targetBytes = type == 3 && chatType == 1 && target != null
                    ? target.getBytes(MS949) : EMPTY_BYTES;
            senderBytes = type == 4 && sender != null
                    ? resolveSenderNameBytes(sender, chatType) : EMPTY_BYTES;
            pcObjId = type == 4 && sender != null ? sender.getId() : 0;
            pcX = type == 4 && sender != null ? sender.getX() : 0;
            pcY = type == 4 && sender != null ? sender.getY() : 0;
            rankLevel = type == 4 && sender != null ? sender.getRankLevel() : 0;
        }

        public boolean send(L1PcInstance receiver) {
            return sendFrame(receiver, type, chatType, textBytes, targetBytes,
                    senderBytes, pcObjId, pcX, pcY, rankLevel);
        }
    }

    // One data snapshot per broadcast, not one packet per recipient.
    public static ChatData prepare(L1PcInstance sender, int type, int chatType,
                                   String text, String target) {
        return new ChatData(sender, type, chatType, text, target);
    }

    public static boolean send(L1PcInstance receiver, L1PcInstance sender, int type,
                               int chatType, String text, String target) {
        final byte[] textBytes = text == null ? EMPTY_BYTES : text.getBytes(MS949);
        final byte[] targetBytes = type == 3 && chatType == 1 && target != null
                ? target.getBytes(MS949) : EMPTY_BYTES;
        final byte[] senderBytes = type == 4 && sender != null
                ? resolveSenderNameBytes(sender, chatType) : EMPTY_BYTES;
        return sendFrame(receiver, type, chatType, textBytes, targetBytes, senderBytes,
                type == 4 && sender != null ? sender.getId() : 0,
                type == 4 && sender != null ? sender.getX() : 0,
                type == 4 && sender != null ? sender.getY() : 0,
                type == 4 && sender != null ? sender.getRankLevel() : 0);
    }

	private static byte[] resolveSenderNameBytes(L1PcInstance pc, int chatType) {
		String nameStr;

		if (pc.isGm() && chatType == 3) {
			nameStr = "******";
//		} else if (pc.is_shift_battle()) {
//			String serverDescription = pc.get_server_description();
//			MJThebeCharacterInfo cInfo = pc.get_thebe_info();
//			if (MJCommons.isNullOrEmpty(serverDescription) || cInfo == null) {
//				nameStr = "미지인";
//			} else {
//				nameStr = cInfo.to_name_pair();
//			}
		} else if (pc.getAge() != 0 && chatType == 4) {
			nameStr = pc.getName() + "(" + pc.getAge() + ")";
		} else if (pc.isGm()) {
			nameStr = "\\f3GM) \\aA" + pc.getName();
		} else if (Config.클랜이름) {
			String clanName = pc.getClanname();
			boolean hasClan = pc.getClanid() != 0;
			boolean hasCastle = hasClan && pc.getClan() != null && pc.getClan().getCastleId() != 0;

			if (chatType == 3) {
				if (hasClan) {
					nameStr = pc.getName() + (hasCastle ? "\\fH[" : "\\f9[") + clanName + "]\\fM";
				} else {
					nameStr = pc.getName();
				}
			} else if (chatType == 12) {
				if (hasClan) {
					nameStr = pc.getName() + (hasCastle ? "\\fH[" : "\\f9[") + clanName + "]\\aR";
				} else {
					nameStr = pc.getName();
				}
			} else if (chatType == 0) {
				if (hasClan) {
					nameStr = pc.getName() + (hasCastle ? "\\fH[" : "\\f9[") + clanName + "]\\aA";
				} else {
					nameStr = pc.getName();
				}
			} else {
				nameStr = pc.getName();
			}
		} else {
			nameStr = pc.getName();
		}

		return nameStr != null ? nameStr.getBytes(MS949) : EMPTY_BYTES;
	}

    private static boolean sendFrame(L1PcInstance receiver, int type, int chatType,
                                     byte[] textBytes, byte[] targetNameBytes,
                                     byte[] senderNameBytes, int pcObjId, int pcX,
                                     int pcY, int rankLevel) {
        if (receiver == null) return false;
        final CUserSocket socket = receiver.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket;
        if (connection == null || !connection.isClientConnected()) return false;
        final long needed = 64L + textBytes.length + targetNameBytes.length + senderNameBytes.length;
        if (needed > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) needed);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writePayload(frame.buffer(), type, chatType, textBytes, targetNameBytes,
                    senderNameBytes, pcObjId, pcX, pcY, rankLevel);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static void broadcastAll(L1PcInstance sender, int type, int chatType,
                                    String text, String targetName) {
        final ChatData data=prepare(sender,type,chatType,text,targetName);
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenAll(b -> writeNativeChat(b,data));
    }

	private static void writePayload(CPacketWriteBuffer buffer, int type, int chatType, byte[] textBytes, byte[] targetNameBytes, byte[] senderNameBytes, int pcObjId, int pcX, int pcY, int rankLevel) {
		buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);

		switch (type) {
			case 3:
				buffer.writeC(0x03);
				break;
			case 4:
				buffer.writeC(0x04);
				break;
		}

		buffer.writeC(0x02);
		buffer.writeC(0x08);
		buffer.writeC(0x00);
		buffer.writeC(0x10);
		buffer.writeC(chatType);

		buffer.writeC(0x1a);
		buffer.writeC(textBytes.length);
		buffer.writeByte(textBytes);

		switch (type) {
			case 3:
				buffer.writeC(0x22);
				if (chatType == 0) {
					buffer.writeC(0x00);
					buffer.writeC(0x28);
					buffer.writeC(0x00);
					buffer.writeC(0x30);
					buffer.writeC(0x18);
					buffer.writeC(0x38);
					buffer.writeC(0x00);
				} else if (chatType == 1) {
					buffer.writeC(targetNameBytes.length);
					buffer.writeByte(targetNameBytes);
					buffer.writeC(0x28);
					buffer.writeC(0x00);
					buffer.writeC(0x30);
					buffer.writeC(0x00);
					buffer.writeH(0);
				}
				break;
			case 4:
				buffer.writeC(0x2a);
				buffer.writeC(senderNameBytes.length);
				buffer.writeByte(senderNameBytes);

				if (chatType == 0) {
					buffer.writeC(0x38);
					buffer.writeK(pcObjId);
					buffer.writeC(0x40);
					buffer.writeK(pcX);
					buffer.writeC(0x48);
					buffer.writeK(pcY);
				}
				if (rankLevel != 0) {
					buffer.writeC(0x50);
					buffer.writeC(rankLevel);
				}
				buffer.writeH(0);
				break;
		}
	}

    /** Preserves the historical writeK(int) (not generic protobuf varint).
     * In particular it uses signed int division and Java's 5-byte path.
     */
    private static void putK(java.nio.ByteBuffer b,int value) {
        final int v=value/128;
        if(v==0)b.put((byte)value);
        else if(v<=127)b.put((byte)((value&0x7f)+128)).put((byte)v);
        else if(v<=16383)b.put((byte)((value&0x7f)+128))
                .put((byte)((v&0x7f)+128)).put((byte)(v/128));
        else if(v<=2097151)b.put((byte)((value&0x7f)+128))
                .put((byte)((v&0x7f)+128)).put((byte)(((v/128)&0x7f)+128))
                .put((byte)(v/16384));
        else b.put((byte)((value&0x7f)+128))
                .put((byte)((v&0x7f)+128)).put((byte)(((v/128)&0x7f)+128))
                .put((byte)(((v/16384)&0x7f)+128)).put((byte)(v/2097152));
    }

    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(ChatData d) {
        if(d==null)return null;
        final long cap=2L+64L+d.textBytes.length+d.targetBytes.length+d.senderBytes.length;
        if(cap>0xFFFFL)return null;
        return b -> writeNativeChat(b,d);
    }



    private static void writeNativeChat(java.nio.ByteBuffer b,ChatData d) {
        b.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
        if(d.type==3)b.put((byte)0x03);
        else if(d.type==4)b.put((byte)0x04);
        b.put((byte)0x02).put((byte)0x08).put((byte)0x00)
         .put((byte)0x10).put((byte)d.chatType);
        b.put((byte)0x1a).put((byte)d.textBytes.length).put(d.textBytes);
        if(d.type==3) {
            b.put((byte)0x22);
            if(d.chatType==0)
                b.put((byte)0x00).put((byte)0x28).put((byte)0x00)
                 .put((byte)0x30).put((byte)0x18).put((byte)0x38).put((byte)0x00);
            else if(d.chatType==1)
                b.put((byte)d.targetBytes.length).put(d.targetBytes)
                 .put((byte)0x28).put((byte)0x00).put((byte)0x30)
                 .put((byte)0x00).putShort((short)0);
        } else if(d.type==4) {
            b.put((byte)0x2a).put((byte)d.senderBytes.length).put(d.senderBytes);
            if(d.chatType==0) {
                b.put((byte)0x38);putK(b,d.pcObjId);
                b.put((byte)0x40);putK(b,d.pcX);
                b.put((byte)0x48);putK(b,d.pcY);
            }
            if(d.rankLevel!=0)b.put((byte)0x50).put((byte)d.rankLevel);
            b.putShort((short)0);
        }
        if(b.position()>16384)throw new java.nio.BufferOverflowException();
    }
}
