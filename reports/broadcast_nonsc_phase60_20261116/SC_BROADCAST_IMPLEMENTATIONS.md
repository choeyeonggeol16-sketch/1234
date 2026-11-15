
## SC_SkillSound: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_SkillSound.java (2 method candidates)

51:     public static void broadcast(L1Character anchor, int objectId, int gfxId) {
52:         broadcast0(anchor, objectId, gfxId, false);
53:     }

59:     public static byte[] sharedWire(int objectId, int gfxId) {
60:         return java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
61:                 .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_EFFECT).putInt(objectId)
62:                 .putShort((short) gfxId).putShort((short) 0).putInt(0).array();
63:     }


## SC_DoActionGFX: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_DoActionGFX.java (2 method candidates)

44:     public static void broadcast(L1Character cha, int actionId) {
45:         if (cha == null) return;
46:         final int objectId = cha.getId();
47:         final byte[] wire = java.nio.ByteBuffer.allocate(8)
48:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN).putShort((short) 8)
49:                 .put((byte) l1j.server.server.Opcodes.S_ACTION)
50:                 .putInt(objectId).put((byte) actionId).array();
51:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(cha, wire, true, -1, null);
52:     }

54:     public static void broadcast(L1PcInstance cha, int actionId) {
55:         broadcast((L1Character) cha, actionId);
56:     }


## SC_SystemMessage: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java (3 method candidates)

37:     public static void broadcast(String message) {
38:         final byte[] wire=sharedWire(message);
39:         if(wire==null)return;
40:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
41:     }

47:     public static void broadcastRecipients(
48:             Iterable<? extends L1PcInstance> recipients,String message) {
49:         final byte[] wire=sharedWire(message);
50:         if(wire!=null)
51:             l1j.server.server.model.network.core.broadcast.CSharedBroadcast
52:                     .sendRecipients(recipients,wire);
53:     }

70:     public static byte[] sharedWire(String message) {
71:         final byte[] text=(message==null ? "" : message)
72:                 .getBytes(java.nio.charset.Charset.forName("MS949"));
73:         final int size=5+text.length;
74:         if(size>0xFFFF)return null;
75:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
76:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
77:         b.putShort((short)size).put((byte)Opcodes.S_MESSAGE)
78:          .put((byte)0x09).put(text).put((byte)0);
79:         return b.array();
80:     }


## SC_SkillBrave: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_SkillBrave.java (2 method candidates)

17:         private static byte[] sharedWire(int objectId, int mode, int time) {
18:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(10)
19:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
20:         b.putShort((short) 10);
21:         b.put((byte)(Opcodes.S_EMOTION));
22:         b.putInt((int)(objectId));
23:         b.put((byte)(mode));
24:         b.putShort((short)(time));
25:         return b.array();
26:     }

51:     public static void broadcast(L1Character anchor, int objectId, int mode, int time) {
52:         if (anchor == null) return;
53:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, mode, time), false, -1, null);
54:     }


## SC_EffectLocation: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_EffectLocation.java (2 method candidates)

18:     public static byte[] sharedWire(int x, int y, int gfxId) {
19:         return java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
20:                 .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_EFFECT_LOC)
21:                 .putShort((short) x).putShort((short) y)
22:                 .putShort((short) gfxId).put((byte) 0).array();
23:     }

58:     public static void broadcast(L1Character anchor, int x, int y, int gfxId) {
59:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(x, y, gfxId), false, -1, null);
60:     }


## SC_AttackPacket: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_AttackPacket.java (4 method candidates)

103: 	public static void broadcast(
104: 			L1Character cha,
105: 			int objId,
106: 			int type) {
107: 
108: 		if (cha == null) {
109: 			return;
110: 		}
111: 
112: 		broadcast0(
113: 				cha,
114: 				MODE_1,
115: 				objId,
116: 				type,
117: 				0,
118: 				0);
119: 	}

121: 	public static void broadcast(
122: 			L1Character cha,
123: 			int objId,
124: 			int type,
125: 			int attackType) {
126: 
127: 		if (cha == null) {
128: 			return;
129: 		}
130: 
131: 		broadcast0(
132: 				cha,
133: 				MODE_2,
134: 				objId,
135: 				type,
136: 				attackType,
137: 				0);
138: 	}

140: 	public static void broadcast(
141: 			L1Character cha,
142: 			int objId,
143: 			int type,
144: 			int attackType,
145: 			int gfx) {
146: 
147: 		if (cha == null) {
148: 			return;
149: 		}
150: 
151: 		broadcast0(
152: 				cha,
153: 				MODE_3,
154: 				objId,
155: 				type,
156: 				attackType,
157: 				gfx);
158: 	}

166: 	    private static byte[] sharedWire(int mode, int chaId, int objId, int type,
167:             int heading, int attackType, int gfx) {
168:         final int size=mode==MODE_3?26:20;
169:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
170:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
171:         b.putShort((short)size);
172:         b.put((byte)Opcodes.S_ATTACK).put((byte)type);
173:         switch (mode) {
174:             case MODE_1:
175:                 b.putInt(objId).putInt(chaId).put((byte)0x4e)
176:                  .put((byte)0).put((byte)heading).putInt(0).put((byte)0);
177:                 break;
178:             case MODE_2:
179:                 b.putInt(chaId).putInt(objId).putShort((short)2)
180:                  .put((byte)heading).putShort((short)0).putShort((short)0)
181:                  .put((byte)attackType);
182:                 break;
183:             case MODE_3:
184:                 b.putInt(chaId).putInt(objId).put((byte)1).put((byte)0)
185:                  .put((byte)heading).putShort((short)0).putShort((short)0)
186:                  .put((byte)attackType).putShort((short)gfx)
187:                  .putShort((short)0).putShort((short)0);
188:                 break;
189:             default:
190:                 throw new IllegalArgumentException("Unsupported attack mode "+mode);
191:         }
192:         return b.array();
193:     }


## SC_SkillHaste: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_SkillHaste.java (2 method candidates)

17:         private static byte[] sharedWire(int objectId, int mode, int time) {
18:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(10)
19:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
20:         b.putShort((short) 10);
21:         b.put((byte)(Opcodes.S_SPEED));
22:         b.putInt((int)(objectId));
23:         b.put((byte)(mode));
24:         b.putShort((short)(time));
25:         return b.array();
26:     }

51:     public static void broadcast(L1Character anchor, int objectId, int mode, int time) {
52:         if (anchor == null) return;
53:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, mode, time), false, -1, null);
54:     }


## SC_UseAttackSkill: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_UseAttackSkill.java (9 method candidates)

157: 	public static void broadcast(
158: 			L1Character cha,
159: 			int targetObj,
160: 			int spellGfx,
161: 			int x,
162: 			int y,
163: 			int actionId) {
164: 
165: 		broadcast(
166: 				cha,
167: 				targetObj,
168: 				spellGfx,
169: 				x,
170: 				y,
171: 				actionId,
172: 				6,
173: 				true);
174: 	}

176: 	public static void broadcast(
177: 			L1Character cha,
178: 			int targetObj,
179: 			int spellGfx,
180: 			int x,
181: 			int y,
182: 			int actionId,
183: 			boolean motion) {
184: 
185: 		broadcast(
186: 				cha,
187: 				targetObj,
188: 				spellGfx,
189: 				x,
190: 				y,
191: 				actionId,
192: 				0,
193: 				motion);
194: 	}

196: 	public static void broadcast(
197: 			L1Character cha,
198: 			int targetObj,
199: 			int spellGfx,
200: 			int x,
201: 			int y,
202: 			int actionId,
203: 			int isHit) {
204: 
205: 		broadcast(
206: 				cha,
207: 				targetObj,
208: 				spellGfx,
209: 				x,
210: 				y,
211: 				actionId,
212: 				isHit,
213: 				true);
214: 	}

216: 	public static void broadcast(
217: 			L1Character cha,
218: 			int targetObj,
219: 			int spellGfx,
220: 			int x,
221: 			int y,
222: 			int actionId,
223: 			int isHit,
224: 			boolean withCastMotion) {
225: 
226: 		if (cha == null) {
227: 			return;
228: 		}
229: 
230: 		final int fixedActionId =
231: 				fixActionId(
232: 						cha,
233: 						actionId);
234: 
235: 		final int myX = cha.getX();
236: 		final int myY = cha.getY();
237: 
238: 		final boolean invalidXY =
239: 				(x == 0 && y == 0)
240: 						|| (x == myX && y == myY);
241: 
242: 		final int oldHeading =
243: 				cha.getHeading();
244: 
245: 		final int heading =
246: 				invalidXY
247: 						? oldHeading
248: 						: calcHeading(
249: 								myX,
250: 								myY,
251: 								x,
252: 								y);
253: 
254: 		cha.setHeading(heading);
255: 
256: 		final int seq =
257: 				SEQ.incrementAndGet();
258: 
259: 		final int chaId =
260: 				withCastMotion
261: 						? cha.getId()
262: 						: 0;
263: 
264:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(cha, sharedWire(fixedActionId,chaId,targetObj,isHit,heading,seq,spellGfx,myX,myY,x,y), true, -1, null);
265: }

399: 	public static void broadcast(
400: 			L1Character anchor,
401: 			L1Character source,
402: 			int targetObj,
403: 			int spellGfx,
404: 			int x,
405: 			int y,
406: 			int actionId) {
407: 
408: 		broadcast(
409: 				anchor,
410: 				source,
411: 				targetObj,
412: 				spellGfx,
413: 				x,
414: 				y,
415: 				actionId,
416: 				6,
417: 				true);
418: 	}

420: 	public static void broadcast(
421: 			L1Character anchor,
422: 			L1Character source,
423: 			int targetObj,
424: 			int spellGfx,
425: 			int x,
426: 			int y,
427: 			int actionId,
428: 			boolean motion) {
429: 
430: 		broadcast(
431: 				anchor,
432: 				source,
433: 				targetObj,
434: 				spellGfx,
435: 				x,
436: 				y,
437: 				actionId,
438: 				0,
439: 				motion);
440: 	}

442: 	public static void broadcast(
443: 			L1Character anchor,
444: 			L1Character source,
445: 			int targetObj,
446: 			int spellGfx,
447: 			int x,
448: 			int y,
449: 			int actionId,
450: 			int isHit) {
451: 
452: 		broadcast(
453: 				anchor,
454: 				source,
455: 				targetObj,
456: 				spellGfx,
457: 				x,
458: 				y,
459: 				actionId,
460: 				isHit,
461: 				true);
462: 	}

464: 	public static void broadcast(
465: 			L1Character anchor,
466: 			L1Character source,
467: 			int targetObj,
468: 			int spellGfx,
469: 			int x,
470: 			int y,
471: 			int actionId,
472: 			int isHit,
473: 			boolean withCastMotion) {
474: 
475: 		if (anchor == null || source == null) {
476: 			return;
477: 		}
478: 
479: 		final int fixedActionId =
480: 				fixActionId(source, actionId);
481: 
482: 		final int myX = source.getX();
483: 		final int myY = source.getY();
484: 
485: 		final boolean invalidXY =
486: 				(x == 0 && y == 0)
487: 						|| (x == myX && y == myY);
488: 
489: 		final int heading =
490: 				invalidXY
491: 						? source.getHeading()
492: 						: calcHeading(myX, myY, x, y);
493: 
494: 		source.setHeading(heading);
495: 
496: 		final int seq = SEQ.incrementAndGet();
497: 
498: 		final int chaId =
499: 				withCastMotion
500: 						? source.getId()
501: 						: 0;
502: 
503: 		broadcastPrepared(
504: 				anchor,
505: 				fixedActionId,
506: 				chaId,
507: 				targetObj,
508: 				isHit,
509: 				heading,
510: 				seq,
511: 				spellGfx,
512: 				myX,
513: 				myY,
514: 				x,
515: 				y);
516: 	}

900:     private static byte[] sharedWire(int actionId,int chaId,int targetObjId,
901:             int isHit,int heading,int seq,int spellGfx,int myX,int myY,
902:             int targetX,int targetY) {
903:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(WIRE_SIZE)
904:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
905:         b.putShort((short)WIRE_SIZE).put((byte)Opcodes.S_ATTACK).put((byte)actionId)
906:          .putInt(chaId).putInt(targetObjId).put((byte)isHit).put((byte)0)
907:          .put((byte)heading).putInt(seq).putShort((short)spellGfx).put((byte)6)
908:          .putShort((short)myX).putShort((short)myY)
909:          .putShort((short)targetX).putShort((short)targetY)
910:          .put((byte)0).put((byte)0).put((byte)0).put((byte)0).put((byte)0);
911:         return b.array();
912:     }


## SC_ChangeShape: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_ChangeShape.java (2 method candidates)

14:     private static byte[] sharedWire(int objectId, int polyId, int weaponState) {
15:         // writeS("abcd") is MS949 ASCII bytes followed by a NUL terminator.
16:         return java.nio.ByteBuffer.allocate(18).order(java.nio.ByteOrder.LITTLE_ENDIAN)
17:                 .putShort((short) 18).put((byte) Opcodes.S_POLYMORPH)
18:                 .putInt(objectId).putShort((short) polyId)
19:                 .put((byte) weaponState).put((byte) 0xFF).put((byte) 0xFF)
20:                 .put((byte) 0).put((byte) 'a').put((byte) 'b')
21:                 .put((byte) 'c').put((byte) 'd').put((byte) 0).array();
22:     }

50:     public static void broadcast(L1Character anchor, int objectId, int polyId, int weaponState) {
51:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, polyId, weaponState), false, -1, null);
52:     }


## SC_Door: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_Door.java (2 method candidates)

15:         private static byte[] sharedWire(int x, int y, int direction, int passable) {
16:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(9)
17:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
18:         b.putShort((short) 9);
19:         b.put((byte)(Opcodes.S_CHANGE_ATTR));
20:         b.putShort((short)(x));
21:         b.putShort((short)(y));
22:         b.put((byte)(direction));
23:         b.put((byte)(passable));
24:         return b.array();
25:     }

51:     public static void broadcast(L1Character anchor, int x, int y, int direction, int passable) {
52:         if (anchor == null) return;
53:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(x, y, direction, passable), false, -1, null);
54:     }


## SC_CharTitle: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_CharTitle.java (2 method candidates)

16:         private static byte[] sharedWire(int objectId, String title) {
17:         final byte[] body = (title == null ? "" : title)
18:                 .getBytes(java.nio.charset.Charset.forName("MS949"));
19:         final int size = 8 + body.length;
20:         if (size > 0xFFFF) return null;
21:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
22:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
23:         b.putShort((short)size).put((byte)Opcodes.S_TITLE)
24:          .putInt(objectId).put(body).put((byte)0);
25:         return b.array();
26:     }

51:     public static void broadcast(L1Character anchor, int objectId, String title) {
52:         if (anchor == null) return;
53:         final byte[] wire=sharedWire(objectId, title);
54:         if(wire==null)return;
55:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, false, -1, null);
56:     }


## SC_RemoveObject: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_RemoveObject.java (2 method candidates)

75: 	public static void broadcast(L1Object obj) {
76: 		if (obj == null) {
77: 			return;
78: 		}
79: 
80: 		broadcast(
81: 				obj,
82: 				obj.getId());
83: 	}

85: 	public static void broadcast(
86: 			L1Object anchor,
87: 			int objectId) {
88: 
89: 		if (anchor == null) {
90: 			return;
91: 		}
92: 
93:         byte[] wire = java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
94:                 .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_REMOVE_OBJECT).putInt(objectId).putShort((short) 0).array();
95:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, true, -1, null);
96:     }


## SC_AttackMissPacket: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_AttackMissPacket.java (3 method candidates)

84: 	public static void broadcast(
85: 			L1Character attacker,
86: 			int targetId) {
87: 
88: 		broadcast(
89: 				attacker,
90: 				targetId,
91: 				1);
92: 	}

94: 	public static void broadcast(L1Character attacker, int targetId, int actId) {
95:         if (attacker == null) return;
96:         final int attackerId = attacker.getId();
97:         final int heading = attacker.getHeading();
98:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(attacker, sharedWire(attackerId, targetId, actId, heading), true, -1, null);
99:     }

101: 	    private static byte[] sharedWire(int attackerId, int targetId, int actId, int heading) {
102:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(20)
103:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
104:         b.putShort((short) 20);
105:         b.put((byte) Opcodes.S_ATTACK);
106:         b.put((byte) actId);
107:         b.putInt(attackerId);
108:         b.putInt(targetId);
109:         b.put((byte) 0);
110:         b.put((byte) 0);
111:         b.put((byte) heading);
112:         b.putInt(0);
113:         b.put((byte) 0);
114:         return b.array();
115:     }


## SC_ChangeHeading: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_ChangeHeading.java (2 method candidates)

46:     public static void broadcast(L1Character anchor, int objectId, int heading) { broadcast0(anchor, objectId, heading, false); }

49:     public static void broadcast(L1Character object) {
50:         if (object != null) broadcast(object, object.getId(), object.getHeading());
51:     }


## SC_OnlyEffect: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_OnlyEffect.java (2 method candidates)

16:     private static byte[] sharedWire(int objectId, int effectId) {
17:         return java.nio.ByteBuffer.allocate(9).order(java.nio.ByteOrder.LITTLE_ENDIAN)
18:                 .putShort((short) 9).put((byte) Opcodes.S_EFFECT)
19:                 .putInt(objectId).putShort((short) effectId).array();
20:     }

49:     public static void broadcast(L1Character anchor, int objectId, int effectId) {
50:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, effectId), false, -1, null);
51:     }


## SC_DoActionShop: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_DoActionShop.java (4 method candidates)

58:     public static void broadcast(L1Character anchor, int id, int action, byte[] message) {
59:         if(anchor==null)return;
60:         final byte[] wire=sharedWire(id,action,message);
61:         if(wire==null)return;
62:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, false, -1, null);
63:     }

64:     public static void broadcast(L1Character anchor, int id, int action, String message) {
65:         if(anchor==null)return;
66:         final byte[] wire=sharedWire(id,action,message);
67:         if(wire==null)return;
68:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, false, -1, null);
69:     }

71:     private static byte[] sharedWire(int id,int action,byte[] message) {
72:         final int size=8+(message==null?0:message.length);
73:         if(size>0xFFFF)return null;
74:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
75:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
76:         b.putShort((short)size).put((byte)Opcodes.S_ACTION)
77:          .putInt(id).put((byte)action);
78:         if(message!=null)b.put(message);
79:         return b.array();
80:     }

81:     private static byte[] sharedWire(int id,int action,String message) {
82:         final byte[] data=(message==null?"":message)
83:                 .getBytes(java.nio.charset.Charset.forName("MS949"));
84:         final int size=9+data.length;
85:         if(size>0xFFFF)return null;
86:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
87:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
88:         b.putShort((short)size).put((byte)Opcodes.S_ACTION)
89:          .putInt(id).put((byte)action).put(data).put((byte)0);
90:         return b.array();
91:     }


## SC_Invis: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_Invis.java (2 method candidates)

19:         private static byte[] sharedWire(int objectId, int type) {
20:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8)
21:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
22:         b.putShort((short) 8);
23:         b.put((byte)(Opcodes.S_INVISIBLE));
24:         b.putInt((int)(objectId));
25:         b.put((byte)(type));
26:         return b.array();
27:     }

47:     public static void broadcast(L1Character anchor, int objectId, int type) {
48:         if (anchor == null) return;
49:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, type), false, -1, null);
50:     }


## SC_AttackCritical: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_AttackCritical.java (6 method candidates)

55: 	public static void broadcast(
56: 			L1PcInstance pc,
57: 			int targetId,
58: 			int type,
59: 			boolean doubleWeapon) {
60: 
61: 		if (pc == null) {
62: 			return;
63: 		}
64: 
65: 		broadcastMode1(
66: 				pc,
67: 				pc.getId(),
68: 				targetId,
69: 				pc.getHeading(),
70: 				resolveMode1Gfx(
71: 						type,
72: 						doubleWeapon));
73: 	}

75: 	public static void broadcast(
76: 			L1Character anchor,
77: 			L1PcInstance pc,
78: 			int targetId,
79: 			int type,
80: 			boolean doubleWeapon) {
81: 
82: 		if (anchor == null || pc == null) {
83: 			return;
84: 		}
85: 
86: 		broadcastMode1(
87: 				anchor,
88: 				pc.getId(),
89: 				targetId,
90: 				pc.getHeading(),
91: 				resolveMode1Gfx(
92: 						type,
93: 						doubleWeapon));
94: 	}

171: 	public static void broadcast(
172: 			L1Character cha,
173: 			int targetId,
174: 			int x,
175: 			int y,
176: 			int type,
177: 			boolean isHit) {
178: 
179: 		if (cha == null) {
180: 			return;
181: 		}
182: 
183: 		broadcast(
184: 				cha,
185: 				cha,
186: 				targetId,
187: 				x,
188: 				y,
189: 				type,
190: 				isHit);
191: 	}

193: 	public static void broadcast(
194: 			L1Character anchor,
195: 			L1Character cha,
196: 			int targetId,
197: 			int x,
198: 			int y,
199: 			int type,
200: 			boolean isHit) {
201: 
202: 		if (anchor == null || cha == null) {
203: 			return;
204: 		}
205: 
206: 		final int spriteId =
207: 				cha.getCurrentSpriteId();
208: 
209: 		final int seq =
210: 				SEQUENTIAL_NUMBER.incrementAndGet();
211: 
212: 		broadcastMode2(
213: 				anchor,
214: 				cha.getId(),
215: 				targetId,
216: 				cha.getHeading(),
217: 				resolveAid(spriteId),
218: 				isHit,
219: 				seq,
220: 				resolveMode2Gfx(
221: 						spriteId,
222: 						type),
223: 				x,
224: 				y,
225: 				cha.getX(),
226: 				cha.getY());
227: 	}

324: 	    private static byte[] sharedWireMode1(int attackerId, int targetId, int heading, int gfxId) {
325:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(26)
326:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
327:         b.putShort((short) 26);
328:         b.put((byte)(Opcodes.S_ATTACK));
329:         b.put((byte)(1));
330:         b.putInt((int)(attackerId));
331:         b.putInt((int)(targetId));
332:         b.putShort((short)(0x01));
333:         b.put((byte)(heading));
334:         b.putShort((short)(0x0000));
335:         b.putShort((short)(0x0000));
336:         b.put((byte)(2));
337:         b.putInt((int)(gfxId));
338:         b.putShort((short)(0));
339:         return b.array();
340:     }

400: 	    private static byte[] sharedWireMode2(int attackerId, int targetId, int heading, int aid, boolean isHit, int seq, int gfxId, int targetX, int targetY, int chaX, int chaY) {
401:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(34)
402:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
403:         b.putShort((short) 34);
404:         b.put((byte)(Opcodes.S_ATTACK));
405:         b.put((byte)(aid));
406:         b.putInt((int)(attackerId));
407:         b.putInt((int)(targetId));
408:         b.put((byte)(isHit ? 6 : 0));
409:         b.put((byte)(0x00));
410:         b.put((byte)(heading));
411:         b.putInt((int)(seq));
412:         b.putShort((short)(gfxId));
413:         b.put((byte)(0));
414:         b.putShort((short)(targetX));
415:         b.putShort((short)(targetY));
416:         b.putShort((short)(chaX));
417:         b.putShort((short)(chaY));
418:         b.put((byte)(0));
419:         b.put((byte)(0));
420:         b.put((byte)(0));
421:         b.put((byte)(0));
422:         return b.array();
423:     }


## SC_Sound: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_Sound.java (2 method candidates)

17:     private static byte[] sharedWire(int sound) {
18:         return java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
19:                 .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_SOUND_EFFECT)
20:                 .put((byte) 0).putShort((short) sound).array();
21:     }

46:     public static void broadcast(L1Character anchor, int sound) {
47:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(sound), false, -1, null);
48:     }


## SC_DropItem: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_DropItem.java (3 method candidates)

91: 	public static void broadcast(L1ItemInstance item) {
92: 		broadcast(item, false);
93: 	}

101: 	public static int broadcast(
102: 			L1ItemInstance item,
103: 			boolean addKnownObject) {
104: 
105: 		if (item == null) {
106: 			return 0;
107: 		}
108: 
109: 		final int x = item.getX();
110: 		final int y = item.getY();
111: 		final int objectId = item.getId();
112: 
113: 		final int groundGfxId;
114: 		final int lightRange;
115: 		final String itemName;
116: 
117: 		if (item.getItem() != null) {
118: 			groundGfxId = item.getItem().getGroundGfxId();
119: 			lightRange = item.isNowLighting()
120: 					? item.getItem().getLightRange()
121: 					: 0;
122: 			itemName = parseItemName(item);
123: 		} else {
124: 			groundGfxId = 0;
125: 			lightRange = 0;
126: 			itemName = "";
127: 		}
128: 
129: 		final int count = (int) item.getCount();
130: 		int viewers = 0;
131: 
132: 		
133:         final byte[] frozen=sharedWire(x,y,objectId,groundGfxId,lightRange,count,itemName);
134:         if(frozen==null)return viewers;
135:         try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
136:                 frozen,item.getId(),Integer.MAX_VALUE)) {
137: {
138: 		    final L1World __cioWorld0 = L1World.getInstance();
139: 		    final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(item);
140: 		    try {
141: 		        for (L1PcInstance pc : __cioVisible0) {
142:         
143:         			if (pc == null) {
144:         				continue;
145:         			}
146:         
147:         			final CIOClient client = pc.getNetConnection();
148:         			if (client == null || !client.isConnected()) {
149:         				continue;
150:         			}
151:         
152:         			final boolean delivered=shared.send(client.getConnection(),pc.getId());
153:                     if (delivered) {
154:         
155:         				if (addKnownObject) {
156:         					pc.addKnownObject(item);
157:         				}
158:         
159:         				viewers++;
160:         			}
161:         		}
162: 		    } finally {
163: 		        __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
164: 		    }
165: 		}
166: 
167: 		
168:         }
169:         return viewers;
170: }

414:     private static byte[] sharedWire(int x,int y,int objectId,int groundGfxId,
415:             int lightRange,int count,String itemName) {
416:         final String text=itemName==null?"":itemName;
417:         final long max=(long)FIXED_WIRE_BYTES+((long)text.length()<<1);
418:         if(max>ConnectionConfig.MAX_WIRE_PACKET_SIZE)return null;
419:         final java.nio.ByteBuffer heap=java.nio.ByteBuffer.allocate((int)max)
420:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
421:         final WriteBuffer b=new WriteBuffer();
422:         b.armExternal(heap,0,(int)max);
423:         b.beginDirect();
424:         b.position(ConnectionConfig.HEADER_SIZE);
425:         try {
426:             b.writeC(Opcodes.S_PUT_OBJECT);
427: 			b.writeH(x);
428: 			b.writeH(y);
429: 			b.writeD(objectId);
430: 			b.writeH(groundGfxId);
431: 			b.writeC(0);
432: 			b.writeC(0);
433: 			b.writeC(lightRange);
434: 			b.writeC(0);
435: 			b.writeD(count);
436: 			b.writeC(0);
437: 			b.writeC(0);
438: 
439: 			b.writeS(text);
440: 			b.writeC(0);
441: 			b.writeD(0);
442: 			b.writeD(0);
443: 			b.writeC(255);
444: 			b.writeC(0);
445: 			b.writeC(0);
446: 			b.writeC(0);
447: 			b.writeH(65535);
448: 			b.writeD(0);
449: 			b.writeC(8);
450: 			b.writeC(0);
451:             final int len=b.position();
452:             if(len>16384)return null;
453:             heap.putShort(0,(short)len);
454:             return java.util.Arrays.copyOf(heap.array(),len);
455:         } catch(Throwable ignored) {
456:             return null;
457:         } finally {
458:             b.releaseResources();
459:         }
460:     }


## SC_IconMessage: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_IconMessage.java (5 method candidates)

24:     public static byte[] sharedGmWire(String message) {
25:         final byte[] wire=sharedWire(message,0,GM_COLOR,28,60,false);
26:         if(wire==null)throw new IllegalStateException("macro icon frame overflow");
27:         return wire;
28:     }

42:     public static byte[] sharedIndexWire(int index,MJSimpleRgb color,int surface,int duration){
43:         return sharedWire("",index,color,surface,duration,true);
44:     }

50:     public static void broadcast(String message, MJSimpleRgb color, int surface, int duration) {
51:         final byte[] wire=sharedWire(message, 0, color, surface, duration, false);
52:         if(wire==null)return;
53:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
54:     }

56:     public static void broadcast(int index, MJSimpleRgb color, int surface, int duration) {
57:         final byte[] wire=sharedWire("", index, color, surface, duration, true);
58:         if(wire==null)return;
59:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
60:     }

121:     private static byte[] sharedWire(String msg,int index,MJSimpleRgb color,
122:             int surface,int duration,boolean indexMode) {
123:         final String text=msg==null?"":msg;
124:         final byte[] rgb=color==null?null:color.get_bytes();
125:         final byte[] bytes=indexMode?null:text.getBytes(java.nio.charset.Charset.forName("MS949"));
126:         // Source's writeS2() rejects encoded lengths above 255. Fall back
127:         // to unchanged direct handler, which preserves that validation.
128:         if(!indexMode && bytes.length>255)return null;
129:         final long reserve=64L+(indexMode?0L:((long)text.length()<<2))
130:                            +(rgb==null?0:rgb.length)+2;
131:         if(reserve>0xFFFF)return null;
132:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate((int)reserve)
133:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
134:         b.position(2);
135:         b.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
136:         b.putShort((short)(indexMode?INDEX_MESSAGE:TEXT_MESSAGE));
137:         if(indexMode){
138:             b.put((byte)0x08);putVar(b,surface*2);
139:             b.put((byte)0x10);putVar(b,index*2);
140:             b.put((byte)0x1a);
141:             if(rgb!=null)b.put(rgb);
142:             b.put((byte)0x20);putVar(b,duration);
143:         } else {
144:             putVar(b,0x08);putVar(b,surface*2);
145:             putVar(b,0x12);
146:             b.put((byte)bytes.length).put(bytes);
147:             putVar(b,0x1a);
148:             if(rgb!=null)b.put(rgb);
149:             putVar(b,0x20);putVar(b,duration);
150:         }
151:         b.putShort((short)0);
152:         final int size=b.position();
153:         b.putShort(0,(short)size);
154:         return java.util.Arrays.copyOf(b.array(),size);
155:     }


## SC_Liquor: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_Liquor.java (2 method candidates)

17:         private static byte[] sharedWire(int objectId, int type) {
18:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8)
19:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
20:         b.putShort((short) 8);
21:         b.put((byte)(Opcodes.S_DRUNKEN));
22:         b.putInt((int)(objectId));
23:         b.put((byte)(type));
24:         return b.array();
25:     }

49:     public static void broadcast(L1Character anchor, int objectId, int type) {
50:         if (anchor == null) return;
51:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, type), false, -1, null);
52:     }


## SC_MoveChar: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_MoveChar.java (2 method candidates)

97:     public static void broadcast(L1Character cha, int x, int y, int heading) {
98:         if (cha == null) return;
99:         int objectId = cha.getId();
100:         byte[] wire = broadcastWire(objectId, x, y, heading);
101:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(cha, wire, true, -1, null);
102:     }

103:     public static void broadcast(L1Character cha) {
104:         if (cha == null) return;
105:         int heading = cha.getHeading();
106:         broadcast(cha, cha.getX() + HEADING_TABLE_X[heading], cha.getY() + HEADING_TABLE_Y[heading], heading);
107:     }


## SC_Poison: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_Poison.java (2 method candidates)

17:         private static byte[] sharedWire(int objectId, int type) {
18:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(9)
19:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
20:         b.putShort((short) 9);
21:         b.put((byte)(Opcodes.S_POISON));
22:         b.putInt((int)(objectId));
23:         b.put((byte)(type == 1 ? 1 : 0));
24:         b.put((byte)(type == 2 ? 1 : 0));
25:         return b.array();
26:     }

52:     public static void broadcast(L1Character anchor, int objectId, int type) {
53:         if (anchor == null) return; 
54:         if(type < 0 || type > 2)return;
55:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, type), false, -1, null);
56:     }


## SC_Resurrection: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_Resurrection.java (2 method candidates)

16:         private static byte[] sharedWire(int targetId, int type, int userId, int targetClassId) {
17:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(16)
18:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
19:         b.putShort((short) 16);
20:         b.put((byte)(Opcodes.S_RESURRECT));
21:         b.putInt((int)(targetId));
22:         b.put((byte)(type));
23:         b.putInt((int)(userId));
24:         b.putInt((int)(targetClassId));
25:         return b.array();
26:     }

58:     public static void broadcast(L1Character anchor, L1PcInstance target, L1PcInstance user, int type) {
59:         if (anchor == null) return;
60:         final int targetId = target != null ? target.getId() : 0;
61:         final int userId = user != null ? user.getId() : 0;
62:         final int targetClassId = target != null ? target.getClassId() : 0;
63:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(targetId, type, userId, targetClassId), false, -1, null);
64:     }


## SC_AttackPacketForNpc: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_AttackPacketForNpc.java (3 method candidates)

21:     private static byte[] sharedWire(int type, int npcObjectId, int chaId, int heading) {
22:         return java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
23:                 .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_ATTACK)
24:                 .put((byte) type).putInt(npcObjectId).putInt(chaId)
25:                 .putShort((short) 1).put((byte) heading)
26:                 .putShort((short) 0).putShort((short) 0).put((byte) 0).array();
27:     }

49: 	public static void broadcast(
50: 			L1Character cha,
51: 			int npcObjectId,
52: 			int type) {
53: 
54: 		if (cha == null) {
55: 			return;
56: 		}
57: 
58: 		broadcast(
59: 				cha,
60: 				cha,
61: 				npcObjectId,
62: 				type);
63: 	}

65: 	public static void broadcast(
66: 			L1Character anchor,
67: 			L1Character cha,
68: 			int npcObjectId,
69: 			int type) {
70:         if (anchor == null) return;
71:         final int chaId = cha != null ? cha.getId() : 0;
72:         final int heading = cha != null ? cha.getHeading() : 0;
73:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(type, npcObjectId, chaId, heading), true, -1, null);
74:     }


## SC_AttackStatus: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_AttackStatus.java (2 method candidates)

23:         private static byte[] sharedWire(int attackerId, int heading, int receiverId, int type) {
24:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(19)
25:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
26:         b.putShort((short) 19);
27:         b.put((byte)(Opcodes.S_ATTACK));
28:         b.put((byte)(type));
29:         b.putInt((int)(attackerId));
30:         b.putInt((int)(receiverId));
31:         b.put((byte)(1));
32:         b.put((byte)(0));
33:         b.put((byte)(heading));
34:         b.putInt((int)(0));
35:         return b.array();
36:     }

61:     public static void broadcast(L1Character anchor, int attackerId, int heading, int receiverId, int type) {
62:         if (anchor == null) return;
63:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(attackerId, heading, receiverId, type), false, -1, null);
64:     }


## SC_ChangeName: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_ChangeName.java (2 method candidates)

16:         private static byte[] sharedWire(int objectId, String name) {
17:         final byte[] body = (name == null ? "" : name)
18:                 .getBytes(java.nio.charset.Charset.forName("MS949"));
19:         final int size = 8 + body.length;
20:         if (size > 0xFFFF) return null;
21:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
22:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
23:         b.putShort((short)size).put((byte)Opcodes.S_CHANGE_DESC)
24:          .putInt(objectId).put(body).put((byte)0);
25:         return b.array();
26:     }

51:     public static void broadcast(L1Character anchor, int objectId, String name) {
52:         if (anchor == null) return;
53:         final byte[] wire=sharedWire(objectId, name);
54:         if(wire==null)return;
55:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, false, -1, null);
56:     }


## SC_CharSpeedUpdate: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_CharSpeedUpdate.java (2 method candidates)

16:         private static byte[] sharedWire(int objectId, int speed, int level) {
17:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(10)
18:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
19:         b.putShort((short) 10);
20:         b.put((byte)(Opcodes.S_CHANGE_LEVEL));
21:         b.putInt((int)(objectId));
22:         b.put((byte)(speed));
23:         b.put((byte)(level));
24:         b.put((byte)(0));
25:         return b.array();
26:     }

52:     public static void broadcast(L1Character anchor, int objectId, int speed, int level) {
53:         if (anchor == null) return;
54:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, speed, level), false, -1, null);
55:     }


## SC_ExplosionNoti: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_ExplosionNoti.java (2 method candidates)

39:     public static void broadcast(L1Character anchor, int objectId, long mills) {
40:         if(anchor==null)return;
41:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId,mills), false, -1, null);
42:     }

52:     private static byte[] sharedWire(int objectId,long mills) {
53:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(29)
54:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
55:         b.position(2);
56:         b.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)0x0332);
57:         b.put((byte)0x08);writeVar(b,objectId);
58:         b.put((byte)0x10);writeVar(b,mills);
59:         b.putShort((short)0);
60:         final int size=b.position();
61:         b.putShort(0,(short)size);
62:         return java.util.Arrays.copyOf(b.array(),size);
63:     }


## SC_Fishing: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_Fishing.java (2 method candidates)

16:         private static byte[] sharedWire(int objectId, int motionNum, int x, int y) {
17:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(18)
18:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
19:         b.putShort((short) 18);
20:         b.put((byte)(Opcodes.S_ACTION));
21:         b.putInt((int)(objectId));
22:         b.put((byte)(motionNum));
23:         b.putShort((short)(x));
24:         b.putShort((short)(y));
25:         b.putInt((int)(0));
26:         b.putShort((short)(0));
27:         return b.array();
28:     }

55:     public static void broadcast(L1Character anchor, int objectId, int motionNum, int x, int y) {
56:         if (anchor == null) return;
57:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, motionNum, x, y), false, -1, null);
58:     }


## SC_HPMeter: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_HPMeter.java (2 method candidates)

15:         private static byte[] sharedWire(int objId, int hpRatio, int mpRatio) {
16:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(11)
17:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
18:         b.putShort((short) 11);
19:         b.put((byte)(Opcodes.S_HIT_RATIO));
20:         b.putInt((int)(objId));
21:         b.put((byte)(hpRatio));
22:         b.put((byte)(mpRatio));
23:         b.putShort((short)(0));
24:         return b.array();
25:     }

66:     public static void broadcast(L1Character anchor, L1Character cha) {
67:         if (anchor == null) return;
68:         int objectId = 0, hpRatio = 0, mpRatio = 0;
69:         if (cha != null) {
70:             objectId = cha.getId();
71:             try {
72:                 int maxHp = cha.getMaxHp();
73:                 if (maxHp > 0) hpRatio = (int) ((100.0 / maxHp) * cha.getCurrentHp());
74:                 int maxMp = cha.getMaxMp();
75:                 if (maxMp > 0) mpRatio = (int) ((100.0 / maxMp) * cha.getCurrentMp());
76:             } catch (Exception ignored) {}
77:         }
78:         final int frozenId = objectId, frozenHp = hpRatio, frozenMp = mpRatio;
79:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(frozenId, frozenHp, frozenMp), false, -1, null);
80:     }


## SC_Lawful: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_Lawful.java (2 method candidates)

17:         private static byte[] sharedWire(int objectId, int lawful) {
18:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(13)
19:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
20:         b.putShort((short) 13);
21:         b.put((byte)(Opcodes.S_CHANGE_ALIGNMENT));
22:         b.putInt((int)(objectId));
23:         b.putShort((short)(lawful));
24:         b.putInt((int)(0));
25:         return b.array();
26:     }

51:     public static void broadcast(L1Character anchor, int objectId, int lawful) {
52:         if (anchor == null) return;
53:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, lawful), false, -1, null);
54:     }


## SC_Light: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_Light.java (2 method candidates)

24:     private static byte[] sharedWire(int objectId, int type) {
25:         return java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
26:                 .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_CHANGE_LIGHT)
27:                 .putInt(objectId).put((byte) type).array();
28:     }

79:     public static void broadcast(
80:             L1Character cha,
81:             int type) {
82:         if (cha == null) return;
83:         final int objectId = cha.getId();
84:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(cha, sharedWire(objectId, type), true, -1, null);
85:     }


## SC_MessageYN: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_MessageYN.java (3 method candidates)

54:     public static void broadcast(int type, String message) {
55:         final byte[] wire=sharedWire(type, message);
56:         if(wire==null)return;
57:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
58:     }

64:     public static byte[] sharedIndexedWire(int idx,int type,String message) {
65:         final byte[] text=(message==null?"":message)
66:                 .getBytes(java.nio.charset.Charset.forName("MS949"));
67:         final int size=12+text.length;
68:         if(size>0xffff)return null;
69:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
70:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
71:         b.putShort((short)size).put((byte)Opcodes.S_ASK).putShort((short)0)
72:          .putInt(idx).putShort((short)type).put(text).put((byte)0);
73:         return b.array();
74:     }

76:     public static byte[] sharedWire(int type, String message) {
77:         final byte[] text=(message==null ? "" : message)
78:                 .getBytes(java.nio.charset.Charset.forName("MS949"));
79:         final int size=12+text.length;
80:         if(size>0xFFFF)return null;
81:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
82:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
83:         b.putShort((short)size).put((byte)Opcodes.S_ASK).putShort((short)0)
84:          .putInt(0).putShort((short)type).put(text).put((byte)0);
85:         return b.array();
86:     }


## SC_NpcShopAction: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_NpcShopAction.java (2 method candidates)

16:         private static byte[] sharedWire(int objectId, int action, String shopName) {
17:         final String text=shopName==null ? "" : shopName;
18:         final long calculated=10L+((long)text.length()<<1);
19:         if (calculated > 0xFFFF) return null;
20:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate((int)calculated)
21:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
22:         b.putShort((short)calculated).put((byte)Opcodes.S_ACTION)
23:          .putInt(objectId).put((byte)action);
24:         for(int i=0;i<text.length();i++) b.putChar(text.charAt(i));
25:         b.putShort((short)0);
26:         return b.array();
27:     }

52:     public static void broadcast(L1Character anchor, int objectId, int action, String shopName) {
53:         if (anchor == null) return;
54:         final byte[] wire=sharedWire(objectId, action, shopName);
55:         if(wire==null)return;
56:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, false, -1, null);
57:     }


## SC_PinkName: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_PinkName.java (2 method candidates)

17:         private static byte[] sharedWire(int objectId, int time) {
18:         final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8)
19:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
20:         b.putShort((short) 8);
21:         b.put((byte)(Opcodes.S_CRIMINAL));
22:         b.putInt((int)(objectId));
23:         b.put((byte)(time));
24:         return b.array();
25:     }

49:     public static void broadcast(L1Character anchor, int objectId, int time) {
50:         if (anchor == null) return;
51:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, time), false, -1, null);
52:     }


## SC_UseArrowSkill: source implementations 1

### src/l1j/server/server/model/network/core/sc/packet/SC_UseArrowSkill.java (3 method candidates)

133: 	public static void broadcast(
134: 			L1Character cha,
135: 			int targetObj,
136: 			int spellGfx,
137: 			int x,
138: 			int y,
139: 			boolean isHit) {
140: 
141: 		if (cha == null) {
142: 			return;
143: 		}
144: 
145: 		final int attackerId = cha.getId();
146: 		final int ox = cha.getX();
147: 		final int oy = cha.getY();
148: 		final int oh = cha.getHeading();
149: 
150: 		final int actId =
151: 				resolveActIdBySprite(
152: 						cha.getCurrentSpriteId());
153: 
154: 		final int seq = nextSeq();
155: 
156:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(cha, sharedWire(attackerId,ox,oy,oh,actId,targetObj,spellGfx,x,y,isHit,seq), true, -1, null);
157: }

159: 	public static void broadcast(
160: 			L1Character anchor,
161: 			int attackerId,
162: 			int ox,
163: 			int oy,
164: 			int oh,
165: 			int actId,
166: 			int targetId,
167: 			int spellGfx,
168: 			int tx,
169: 			int ty,
170: 			boolean isHit) {
171: 
172: 		if (anchor == null) {
173: 			return;
174: 		}
175: 
176: 		final int seq = nextSeq();
177: 
178:         l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(attackerId,ox,oy,oh,actId,targetId,spellGfx,tx,ty,isHit,seq), true, -1, null);
179: }

308:     private static byte[] sharedWire(int attackerId,int ox,int oy,int oh,int actId,
309:             int targetId,int spellGfx,int tx,int ty,boolean isHit,int seq) {
310:         final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(WIRE_SIZE)
311:                 .order(java.nio.ByteOrder.LITTLE_ENDIAN);
312:         b.putShort((short)WIRE_SIZE).put((byte)Opcodes.S_ATTACK).put((byte)actId)
313:          .putInt(attackerId).putInt(targetId).put((byte)(isHit?6:0))
314:          .put((byte)0).put((byte)oh).putInt(seq).putShort((short)spellGfx)
315:          .put((byte)0).putShort((short)ox).putShort((short)oy)
316:          .putShort((short)tx).putShort((short)ty)
317:          .put((byte)0).put((byte)0).put((byte)0).put((byte)0).put((byte)0);
318:         return b.array();
319:     }
