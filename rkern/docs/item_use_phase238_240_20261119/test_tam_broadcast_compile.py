#!/usr/bin/env python3
from pathlib import Path
import re, subprocess, tempfile
root=Path(__file__).resolve().parents[2]/'src/l1j/server/server/model'
def method(s, marker):
 a=s.index(marker); b=s.index('{',a); d=1; e=b+1
 while d: d+=(s[e]=='{')-(s[e]=='}');e+=1
 return s[a:e]
s=(root/'network/core/broadcast/CSharedBroadcast.java').read_text()
m=method(s,'public static void sendWrittenClanBestEffort').replace('l1j.server.server.model.L1Clan','L1Clan')
t=(root/'item/tam/L1Tam.java').read_text()
t=method(t,'public void tamdel(int objid)')
code='''import java.sql.*;import java.util.*;import java.util.stream.*;
class L1Clan {boolean closed; Stream<Member> createOnlineMembers(){return Stream.of(new Member(1),null,new Member(2),new Member(1),new Member(3)).onClose(()->closed=true);}}
record Player(int id){} class Member {Player player;Member(int id){player=new Player(id);}}
interface NativeWireWriter {} class CAsyncSendConstBuffer {boolean closed;} class UnavailableOptionalSource extends RuntimeException {}
class SQLUtil {static void close(Connection c){Test.closed++;}}
public class Test {static int closed,bind,delete,invalidated,attempt,fail;static boolean reject;static List<Integer> ids=new ArrayList<>();static CAsyncSendConstBuffer last;
static CAsyncSendConstBuffer sendWrittenOptionalTo(CAsyncSendConstBuffer shared, Player p, NativeWireWriter w){attempt++;if(reject&&attempt==fail)throw new UnavailableOptionalSource();ids.add(p.id());return shared==null?(last=new CAsyncSendConstBuffer()):shared;}
static void logOptionalNoticeFailure(RuntimeException e){} static void closeOptional(CAsyncSendConstBuffer b){if(b!=null)b.closed=true;}
Connection newCon(){return (Connection)java.lang.reflect.Proxy.newProxyInstance(Test.class.getClassLoader(),new Class[]{Connection.class},(o,m,a)->{if(!m.getName().equals("prepareStatement"))return null;if(!a[0].equals("DELETE FROM tam WHERE objid=? ORDER BY id ASC LIMIT 1"))throw new AssertionError();return java.lang.reflect.Proxy.newProxyInstance(Test.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,pm,pa)->{switch(pm.getName()){case "setInt":bind=(int)pa[1];return null;case "executeUpdate":if(reject)throw new SQLException();delete++;return 1;default:return null;}});});}
void invalidateCache(){invalidated++;}
'''+m+'\n'+t+'''
public static void main(String[]a){for(int i=0;i<=4;i++){attempt=0;ids.clear();last=null;reject=i!=0;fail=i;L1Clan clan=new L1Clan();sendWrittenClanBestEffort(clan,new NativeWireWriter(){});if(!clan.closed||(last!=null&&!last.closed)||ids.size()!=(i==0?4:i-1))throw new AssertionError();if(!reject&&!ids.equals(List.of(1,2,1,3)))throw new AssertionError();}Test t=new Test();reject=false;t.tamdel(123);if(bind!=123||delete!=1||invalidated!=1||closed!=1)throw new AssertionError();reject=true;t.tamdel(124);if(bind!=124||delete!=1||invalidated!=1||closed!=2)throw new AssertionError();System.out.println("TAM_BROADCAST_PASS 7 typed actual-method scenarios");}}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'Test.java';p.write_text(code)
 subprocess.run(['javac','--release','21','-encoding','UTF-8',str(p)],check=True)
 subprocess.run(['java','-cp',d,'Test'],check=True)
