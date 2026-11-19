#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
function put() { mkdir -p "$(dirname "$TMP/$1")"; cat > "$TMP/$1"; }
put l1j/server/server/Opcodes.java <<'JAVA'
package l1j.server.server;
public class Opcodes { public static final int S_EFFECT=0x25, S_EFFECT_LOC=0xD5, S_EVENT=0x6B,S_EXTENDED_PROTOBUF=0xB4; }
JAVA
put l1j/server/server/model/L1Character.java <<'JAVA'
package l1j.server.server.model;
public class L1Character { public int getId(){return 1;} }
JAVA
put l1j/server/server/model/L1World.java <<'JAVA'
package l1j.server.server.model;
public class L1World { }
JAVA
put l1j/server/server/model/L1Location.java <<'JAVA'
package l1j.server.server.model;
public class L1Location { public int getX(){return 0;} public int getY(){return 0;} }
JAVA
put l1j/server/server/model/Instance/L1PcInstance.java <<'JAVA'
package l1j.server.server.model.Instance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
public class L1PcInstance extends l1j.server.server.model.L1Character {
 private final CIOClient net;
 public L1PcInstance(CUserSocket s){net=new CIOClient(s);}
 public CIOClient getNetConnection(){return net;}
 public int getLinMeff(){return 0;}
 public String getName(){return "test";}
 public String getClanMemberNotes(){return "";}
 public String getAccountName(){return "test";}
 public int getClanRank(){return 0;}
 public int getLevel(){return 1;}
 public int getClanMemberId(){return 1;}
 public int getType(){return 0;}
}
JAVA
put l1j/server/server/model/network/core/session/CIOClient.java <<'JAVA'
package l1j.server.server.model.network.core.session;
import l1j.server.server.model.network.core.connection.CUserSocket;
public class CIOClient {
 private final CUserSocket conn;
 public CIOClient(CUserSocket conn){this.conn=conn;}
 public boolean isConnected(){return true;}
 public CUserSocket getConnection(){return conn;}
}
JAVA
put l1j/server/server/model/network/core/connection/CUserSocket.java <<'JAVA'
package l1j.server.server.model.network.core.connection;
import java.util.Arrays;
public class CUserSocket {
 public byte[] last;
 public boolean isClientConnected(){return true;}
 public CIOFrame beginDirectFrame(int size){return new CIOFrame();}
 public boolean commitDirectFrame(CIOFrame f){last=f.buffer().toArray();return true;}
 public void abortDirectFrame(CIOFrame f){}
}
JAVA
put l1j/server/server/model/network/core/connection/CIOFrame.java <<'JAVA'
package l1j.server.server.model.network.core.connection;
import l1j.server.server.model.network.buffer.WriteBuffer;
public class CIOFrame {
 private final WriteBuffer b=new WriteBuffer();
 public WriteBuffer buffer(){return b;}
}
JAVA
put l1j/server/server/model/network/buffer/WriteBuffer.java <<'JAVA'
package l1j.server.server.model.network.buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
public class WriteBuffer {
 private final ByteBuffer b=ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN);
 public void writeC(int n){b.put((byte)n);}
 public void writeH(int n){b.putShort((short)n);}
 public void writeD(int n){b.putInt(n);}
 public void writeByte(int n){b.put((byte)n);}
 public void writeByte(byte[] n){b.put(n);}
 public void writeS(String s){byte[] bs=(s==null?"":s).getBytes(java.nio.charset.Charset.forName("MS949"));b.put(bs);b.put((byte)0);}
 public void writeS2(String s){byte[] bs=(s==null?"":s).getBytes(java.nio.charset.Charset.forName("MS949"));b.put((byte)bs.length);b.put(bs);}
 public int bitlengh(int n){if(n<0)return 10; if(n<(1<<7))return 1;if(n<(1<<14))return 2;if(n<(1<<21))return 3;if(n<(1<<28))return 4;return 5;}
 public void write7B(long n){while((n&~0x7fL)!=0){b.put((byte)((n&0x7f)|0x80));n>>>=7;}b.put((byte)n);}
 public byte[] toArray(){return Arrays.copyOf(b.array(),b.position());}
}
JAVA
put l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java <<'JAVA'
package l1j.server.server.model.network.core.broadcast;
public class CSharedBroadcast {
 @FunctionalInterface public interface NativeWireWriter {void write(java.nio.ByteBuffer b);}
 public static final int FORMAT_SKILL_SOUND=7, FORMAT_EFFECT_LOCATION=8;
 public static void writeMS949S2(java.nio.ByteBuffer b,String s){byte[] bs=(s==null?"":s).getBytes(java.nio.charset.Charset.forName("MS949"));b.put((byte)bs.length);b.put(bs);}
 public static void sendFormatted(l1j.server.server.model.L1Character a,int f,int o,int p0,int p1,int p2,int p3,boolean fast,int r,java.util.function.Predicate<l1j.server.server.model.Instance.L1PcInstance> filter){}
}
JAVA
put l1j/server/server/model/L1Clan.java <<'JAVA'
package l1j.server.server.model;
public class L1Clan {
 private long count; private int[] times;
 public L1Clan(long count,int[] times){this.count=count;this.times=times;}
 public long getBlessCount(){return count;}
 public int[] getBuffTime(){return times;}
 public String getClanName(){return "";}
 public String getLeaderName(){return "";}
 public int getEmblemId(){return 0;}
 public int getHouseId(){return 0;}
 public int getCastleId(){return 0;}
 public java.util.Date getClanBirthDay(){return null;}
 public String getAnnouncement(){return "";}
}
JAVA
put l1j/server/server/datatables/ClanTable.java <<'JAVA'
package l1j.server.server.datatables;
public class ClanTable {
 public static ClanTable getInstance(){return new ClanTable();}
 public l1j.server.server.model.L1Clan getTemplate(int id){return null;}
}
JAVA
put l1j/server/server/datatables/CharacterTable.java <<'JAVA'
package l1j.server.server.datatables;
public class CharacterTable {
 public static CharacterTable getInstance(){return new CharacterTable();}
 public l1j.server.server.model.Instance.L1PcInstance restoreCharacter(String name){return null;}
}
JAVA
SRC="$ROOT/src/l1j/server/server/model/network/core/sc/packet"
mkdir -p "$TMP/classes"
javac --release 21 -d "$TMP/classes" $(find "$TMP" -name '*.java') "$SRC/SC_SkillSound.java" "$SRC/SC_EffectLocation.java" "$SRC/SC_Pledge.java" "$(dirname "$0")/WireCompat88.java"
java -cp "$TMP/classes" WireCompat88
