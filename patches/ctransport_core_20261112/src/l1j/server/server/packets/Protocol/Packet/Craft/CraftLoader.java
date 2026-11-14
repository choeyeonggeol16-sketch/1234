package l1j.server.server.packets.Protocol.Packet.Craft;

import l1j.server.server.packets.Protocol.Stream.ProtoInputStream;

public class CraftLoader {

    public static void load() {
        LIN.SC_CRAFT_LIST_ALL_ACK.Craft.clear();
        ProtoInputStream in = null;
        try {
            in = ProtoInputStream.newInstance("./data/craftinfo.dat");
            while(!in.isAtEnd()){
                int tag = in.readTag();
                switch(tag >>> 3){
                case 1: in.readEnum(); break;                              // eResult (파일에 보통 없음)
                case 2: LIN.SC_CRAFT_LIST_ALL_ACK.Craft.put(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.parse(in.readSlice())); break;  // repeated craft
                default: in.skipField(tag); break;
                }
            }
      //      System.out.println(String.format("[CraftLoader] loaded %d crafts.", Craft.all().size()));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // ProtoInputStream에 close 필요하면 추가
        }
    }
}