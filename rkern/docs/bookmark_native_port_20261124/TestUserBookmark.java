package l1j.server.server.model.character.bookmark;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import l1j.server.server.templates.BookmarkSlot;

public final class TestUserBookmark {
    static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
    static BookmarkSlot slot(int id, int position, int speed, String name) {
        BookmarkSlot b = new BookmarkSlot();
        b.setId(id); b.setTemp_id(position); b.setSpeed_id(speed); b.setName(name);
        return b;
    }
    static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }
    public static void main(String[] args) throws Exception {
        CUserBookmark book = new CUserBookmark();
        ArrayList<BookmarkSlot> rows = new ArrayList<>();
        BookmarkSlot a = slot(100, 5, 4, "A");
        BookmarkSlot b = slot(200, 0, 0, "B");
        BookmarkSlot c = slot(300, 3, -1, "C");
        rows.add(a); rows.add(b); rows.add(c);
        book.OnLoadList(rows);
        check(book.slots().size() == 3 && book.FindWireSlot(0) == b
                && book.FindWireSlot(1) == c && book.FindWireSlot(2) == a, "sparse row order");
        check(book.shortcuts().size() == 2 && book.shortcuts().get(0) == b
                && book.shortcuts().get(1) == a, "shortcut identity independent of order");
        book.OnLoadList(rows);
        check(book.slots().size() == 3 && book.shortcuts().size() == 2, "repeated login duplicated rows");
        check(book.FindById(300) == c && book.FindSlot("b") == b, "ID/name lookup");
        check(book.FindWireSlot(-1) == null && book.FindWireSlot(3) == null, "slot bounds");
        book.RemoveCommittedSlot(b);
        check(book.FindWireSlot(0) == c && book.FindById(200) == null
                && book.shortcuts().size() == 1 && book.shortcuts().get(0) == a, "delete retires shortcut");
        BookmarkSlot d = slot(400, 8, -1, "D");
        book.AppendCommittedSlot(d);
        check(book.FindWireSlot(2) == d && d.getId() == 400 && d.getTemp_id() == 8, "row ID not wire ID");
        check(!CUserBookmark.validTitle(null) && !CUserBookmark.validTitle("")
                && !CUserBookmark.validTitle("a\0b") && CUserBookmark.validTitle("기억 장소"), "name validation");
        byte[] good = bytes(0, 2, 0, 1, 255, 1, 255, 0, 255);
        int[] expected = {2,0,1,3,1,-1};
        check(Arrays.equals(CUserBookmark.DecodeLayout(good,3,10),expected), "zero index and sparse shortcuts");
        for (int i=0; i<good.length; i++)
            check(CUserBookmark.DecodeLayout(Arrays.copyOf(good,i),3,10)==null, "truncated layout");
        check(CUserBookmark.DecodeLayout(bytes(0,0,0,2,255,255,255,255,255),3,10)==null, "duplicate order");
        check(CUserBookmark.DecodeLayout(bytes(0,0,1,10,255,255,255,255,255),3,10)==null, "out-of-range order");
        check(CUserBookmark.DecodeLayout(bytes(0,0,1,2,1,1,255,255,255),3,10)==null, "duplicate shortcut");
        check(CUserBookmark.DecodeLayout(bytes(0,0,1,2,3,255,255,255,255),3,10)==null, "invalid shortcut");
        Random random = new Random(17);
        for (int i=0;i<1000;i++) {
            int n=random.nextInt(101);
            byte[] packet=new byte[n+6]; Arrays.fill(packet,(byte)255);
            packet[0]=0;
            for (int j=0;j<n;j++) packet[1+j]=(byte)(n-j-1);
            if (n>0) packet[n+5]=0;
            int[] decoded=CUserBookmark.DecodeLayout(packet,n,100);
            check(decoded!=null && decoded.length==2*n,"valid layout count");
            if (n>0) check(decoded[n]==4,"last shortcut");
            if(n>1) {
                packet[2]=packet[1];
                check(CUserBookmark.DecodeLayout(packet,n,100)==null,"duplicate rejected");
            }
        }
        System.out.println("PASS load/identity/shortcut/slot-zero/truncation and 1000 layout rounds");
        Method layout=CUserBookmark.class.getDeclaredMethod("writeLayout",Connection.class,int.class,int[].class,int[].class,int[].class);
        layout.setAccessible(true);
        HashMap<Integer,int[]> state=new HashMap<>();
        state.put(100,new int[]{0,0}); state.put(200,new int[]{1,-1}); state.put(300,new int[]{2,4});
        ArrayList<int[]> pending=new ArrayList<>();
        int[] parameters=new int[4];
        PreparedStatement stmt=(PreparedStatement)Proxy.newProxyInstance(TestUserBookmark.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy,method,arguments)->{
            switch(method.getName()) {
                case "setInt": parameters[(int)arguments[0]-1]=(int)arguments[1]; return null;
                case "addBatch": pending.add(parameters.clone()); return null;
                case "clearBatch": pending.clear(); return null;
                case "close": return null;
                case "executeBatch":
                    int[] counts=new int[pending.size()]; int i=0;
                    for(int[] update:pending) {
                        check(update[3]==77,"owner scoped SQL");
                        if(!state.containsKey(update[2])) throw new SQLException("missing row");
                        for(var entry:state.entrySet()) {
                            if(entry.getKey()==update[2])continue;
                            if(entry.getValue()[0]==update[0]) throw new SQLException("duplicate display order");
                            if(update[1]>=0 && entry.getValue()[1]==update[1]) throw new SQLException("duplicate shortcut");
                        }
                        state.put(update[2],new int[]{update[0],update[1]}); counts[i++]=1;
                    }
                    pending.clear(); return counts;
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
        Connection con=(Connection)Proxy.newProxyInstance(TestUserBookmark.class.getClassLoader(),new Class<?>[]{Connection.class},
                (proxy,method,arguments)->{
                    if(method.getName().equals("prepareStatement")) return stmt;
                    throw new UnsupportedOperationException(method.getName());
                });
        layout.invoke(null,con,77,new int[]{100,200,300},new int[]{0,1,2},new int[]{2,0,1,4,0,-1});
        check(Arrays.equals(state.get(100),new int[]{2,4}) &&
                Arrays.equals(state.get(200),new int[]{0,0}) &&
                Arrays.equals(state.get(300),new int[]{1,-1}),"SQL swap and shortcut order");
        System.out.println("PASS actual layout SQL with unique-order and shortcut collision probe");
        check(!CUserBookmark.validTitle("x".repeat(46)) && CUserBookmark.validTitle("x".repeat(45)), "DB title limit");
        check(book.snapshotSlots().length==book.size(), "slot snapshot size");
        Method export = l1j.server.server.templates.CItemBookmark.class.getDeclaredMethod(
                "WriteSavedOrb",Connection.class,int.class,ArrayList.class);
        export.setAccessible(true);
        var row = new l1j.server.server.templates.CItemBookmark();
        row.setId(123); row.setNumId(0); row.setSpeed_id(4); row.setName("test");
        row.setLocX(33333); row.setLocY(33334); row.setMapId((short)4);
        row.setRandomX(7); row.setRandomY(8);
        ArrayList<l1j.server.server.templates.CItemBookmark> orbRows=new ArrayList<>();
        orbRows.add(row);
        Object[] bound=new Object[10];
        int[] writes={0};
        PreparedStatement insert=(PreparedStatement)Proxy.newProxyInstance(TestUserBookmark.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},(proxy,method,arguments)->{
            switch(method.getName()) {
                case "setInt": case "setString": bound[(int)arguments[0]-1]=arguments[1];return null;
                case "executeUpdate": writes[0]++;return 1;
                case "close": return null;
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
        Connection orbCon=(Connection)Proxy.newProxyInstance(TestUserBookmark.class.getClassLoader(),
                new Class<?>[]{Connection.class},(proxy,method,arguments)->{
            if(method.getName().equals("prepareStatement")) {
                check(((String)arguments[0]).contains("character_teleport_item"),"orb destination table");
                return insert;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        export.invoke(null,orbCon,999,orbRows);
        check(writes[0]==1 && Arrays.equals(bound,new Object[]{123,0,4,999,"test",33333,33334,4,7,8}),
                "orb identity, display order, shortcut and random offset preserved");
        System.out.println("PASS orb SQL binding and schema title limit");
    }
}
