package l1j.server.server.model.network.core.sc.packet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import l1j.server.Config;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class SC_NewChat {
    private SC_NewChat() {}

    public static final class ChatData {
        private final int type, chatType, pcObjId, pcX, pcY, rankLevel;
        private final byte[] textBytes, targetBytes, senderBytes;
        private final int frameLength;

        private ChatData(L1PcInstance sender, int type, int chatType, String text, String target) {
            this.type = type;
            this.chatType = chatType;
            textBytes = ProtoBroadcastFields.encode(text);
            targetBytes = type == 3 && chatType == 1
                    ? ProtoBroadcastFields.encode(target) : ProtoBroadcastFields.EMPTY;
            senderBytes = type == 4 && sender != null
                    ? resolveSenderNameBytes(sender, chatType) : ProtoBroadcastFields.EMPTY;
            pcObjId = type == 4 && sender != null ? sender.getId() : 0;
            pcX = type == 4 && sender != null ? sender.getX() : 0;
            pcY = type == 4 && sender != null ? sender.getY() : 0;
            rankLevel = type == 4 && sender != null ? sender.getRankLevel() : 0;
            frameLength = frameLength();
        }

        public boolean send(L1PcInstance receiver) {
            return sendFrame(receiver, this);
        }

        private int frameLength() {
            if (textBytes == null || targetBytes == null || senderBytes == null) return 0;
            int size = 8 + ProtoBroadcastFields.varintSize(chatType)
                    + ProtoBroadcastFields.bytesSize(textBytes);
            if (type == 3) {
                if (chatType == 0) size += 8;
                else if (chatType == 1) size += ProtoBroadcastFields.bytesSize(targetBytes) + 6;
                else return 0;
            } else if (type == 4) {
                size += ProtoBroadcastFields.bytesSize(senderBytes) + 2;
                if (chatType == 0) size += 3 + kSize(pcObjId) + kSize(pcX) + kSize(pcY);
                if (rankLevel != 0) size += 1 + ProtoBroadcastFields.varintSize(rankLevel);
            } else {
                return 0;
            }
            return size <= CIOBufferPool.MAX_WIRE_BYTES ? size : 0;
        }
    }

    // One data snapshot per broadcast, not one packet per recipient.
    public static ChatData prepare(L1PcInstance sender, int type, int chatType,
                                   String text, String target) {
        return new ChatData(sender, type, chatType, text, target);
    }

    public static boolean send(L1PcInstance receiver, L1PcInstance sender, int type,
                               int chatType, String text, String target) {
        return receiver != null && prepare(sender, type, chatType, text, target).send(receiver);
    }

	private static byte[] resolveSenderNameBytes(L1PcInstance pc, int chatType) {
		String nameStr;

		if (pc.isGm() && chatType == 3) {
			nameStr = "******";
//		} else if (pc.is_shift_battle()) {
//			String serverDescription = pc.get_server_description();
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
			final L1Clan clan = hasClan ? pc.getClan() : null;
			boolean hasCastle = clan != null && clan.getCastleId() != 0;

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

		return ProtoBroadcastFields.encode(nameStr);
	}

    private static boolean sendFrame(L1PcInstance receiver, ChatData data) {
        if (receiver == null || data.frameLength == 0) return false;
        final CUserSocket connection = receiver.getNetConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        if (!connection.isConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(data.frameLength);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writePayload(frame.buffer(), data.type, data.chatType, data.textBytes, data.targetBytes,
                    data.senderBytes, data.pcObjId, data.pcX, data.pcY, data.rankLevel);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static void broadcastAll(L1PcInstance sender, int type, int chatType,
                                    String text, String targetName) {
        CSharedBroadcast.sendWrittenAll(nativeWriter(prepare(sender, type, chatType, text, targetName)));
    }

	private static void writePayload(Assemble buffer, int type, int chatType, byte[] textBytes, byte[] targetNameBytes, byte[] senderNameBytes, int pcObjId, int pcX, int pcY, int rankLevel) {
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
		buffer.writeBit(chatType);
		ProtoBroadcastFields.writeBytes(buffer, 0x1a, textBytes);

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
					buffer.writeBit(targetNameBytes.length);
					buffer.writeByte(targetNameBytes);
					buffer.writeC(0x28);
					buffer.writeC(0x00);
					buffer.writeC(0x30);
					buffer.writeC(0x00);
					buffer.writeH(0);
				}
				break;
			case 4:
				ProtoBroadcastFields.writeBytes(buffer, 0x2a, senderNameBytes);

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
					buffer.writeBit(rankLevel);
				}
				buffer.writeH(0);
				break;
		}
	}

    /** Preserves the historical writeK(int) (not generic protobuf varint).
     * In particular it uses signed int division and Java's 5-byte path.
     */
    private static int kSize(int value) {
        final int quotient = value / 128;
        if (quotient == 0) return 1;
        if (quotient <= 127) return 2;
        if (quotient <= 16383) return 3;
        return quotient <= 2097151 ? 4 : 5;
    }

    private static void putK(ByteBuffer b,int value) {
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

    public static NativeWireWriter nativeWriter(ChatData d) {
        if (d == null || d.frameLength == 0) return null;
        return b -> writeNativeChat(b,d);
    }



    private static void writeNativeChat(ByteBuffer b,ChatData d) {
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
        if(d.type==3)b.put((byte)0x03);
        else if(d.type==4)b.put((byte)0x04);
        b.put((byte)0x02).put((byte)0x08).put((byte)0x00)
         .put((byte)0x10);
        ProtoBroadcastFields.putVarint(b, d.chatType);
        ProtoBroadcastFields.putBytes(b, 0x1a, d.textBytes);
        if(d.type==3) {
            b.put((byte)0x22);
            if(d.chatType==0)
                b.put((byte)0x00).put((byte)0x28).put((byte)0x00)
                 .put((byte)0x30).put((byte)0x18).put((byte)0x38).put((byte)0x00);
            else if(d.chatType==1) {
                ProtoBroadcastFields.putVarint(b, d.targetBytes.length);
                b.put(d.targetBytes).put((byte)0x28).put((byte)0x00).put((byte)0x30)
                 .put((byte)0x00).putShort((short)0);
            }
        } else if(d.type==4) {
            ProtoBroadcastFields.putBytes(b, 0x2a, d.senderBytes);
            if(d.chatType==0) {
                b.put((byte)0x38);putK(b,d.pcObjId);
                b.put((byte)0x40);putK(b,d.pcX);
                b.put((byte)0x48);putK(b,d.pcY);
            }
            if(d.rankLevel!=0) {
                b.put((byte)0x50);
                ProtoBroadcastFields.putVarint(b, d.rankLevel);
            }
            b.putShort((short)0);
        }
    }
}
