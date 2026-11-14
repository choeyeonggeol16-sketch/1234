package l1j.server.server.packets.Protocol.Packet;

import java.nio.charset.Charset;
import java.util.Arrays;

import l1j.server.IndunSystem.Gludio.GludioInstanceDungeon;
import l1j.server.IndunSystem.Gludio.GludioInstanceDungeon.Room;
import l1j.server.IndunSystem.Gludio.GludioInstanceDungeon.Member;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.ProtoWriter;

/** Source44 ArenaCo/Indun protobuf IDs, encoded on the existing 2019 extended-proto transport. */
public final class GludioDungeonPackets {
    private static final Charset MS949 = Charset.forName("MS949");
    private static final GludioInstanceDungeon DUNGEON = GludioInstanceDungeon.getInstance();
    private GludioDungeonPackets() { }

    public static final int CS_LIST=0x08a3, SC_LIST=0x08a4;
    public static final int CS_DETAIL=0x08a5, SC_DETAIL=0x08a6;
    public static final int CS_CREATE=0x08a7, SC_CREATE=0x08a8;
    public static final int CS_CHANGE=0x08a9, SC_CHANGE=0x08aa;
    public static final int SC_STATUS=0x08ab;
    public static final int CS_ENTER=0x08ac, SC_ENTER=0x08ad;
    public static final int SC_MEMBER_ENTER=0x08ae;
    public static final int CS_READY=0x08af, SC_READY=0x08b0;
    public static final int CS_START=0x08b1, SC_START=0x08b2;
    public static final int CS_INVITE=0x08b3, SC_INVITE=0x08b4;
    public static final int CS_NOTIFY=0x08b5, SC_NOTIFY=0x08b6;
    public static final int CS_EXIT=0x08b7, SC_EXIT=0x08b8;
    public static final int SC_MEMBER_EXIT=0x08b9;
    public static final int CS_KICK=0x08ba, SC_KICK=0x08bb;
    public static final int SC_GAME_INFO=0x02dd, SC_PLAY_STATUS=0x02de;

    private static final class Build {
        String title="";
        int minLevel=80, fee=5000, max=6, kind=201, distribution=1;
        boolean boost, closed;
        String password="";
    }

    public static void handle(CIOClient client,ProtoReader in,int message) {
        L1PcInstance pc=client==null?null:client.getActiveChar();
        if(pc==null) return;
        int id=0, kind=201, page=1, kickId=0;
        boolean ready=false; String password="", inviteName="";
        Build build=null; Change change=null;
        for(int tag;(tag=in.next())!=0;) {
            int field=tag>>>3;
            if(message==CS_CREATE && field==1 && (tag&7)==2) {
                build=readBuild(in.subReader());
            } else if(message==CS_CHANGE && field==2 && (tag&7)==2) {
                if(change==null) change=new Change();
                change.title=new String(in.readBytes(),MS949);
            } else if((tag&7)==0) {
                int number=in.varint32();
                if(message==CS_LIST) { if(field==1) page=number; else if(field==2) kind=number; }
                else if(message==CS_READY) { if(field==1) id=number; else if(field==2) ready=number!=0; }
                else if(message==CS_KICK && field==2) kickId=number;
                else if(message==CS_CHANGE && field>1) {
                    if(change==null) change=new Change();
                    if(field==3) change.level=number;
                    else if(field==4) change.closed=number!=0;
                    else if(field==6) change.distribution=number;
                    else if(field==7) change.max=number;
                    else if(field==9) change.boost=number!=0;
                } else if(field==1) id=number;
            } else if((tag&7)==2) {
                if(message==CS_ENTER && field==2) password=in.readString();
                else if(message==CS_INVITE && field==3) {
                    byte[] name=in.readBytes();
                    if(name.length<=48) inviteName=new String(name,MS949);
                }
                else if(message==CS_CHANGE && field==5) {
                    if(change==null) change=new Change();
                    change.password=in.readString();
                } else in.skip(tag&7);
            } else in.skip(tag&7);
        }
        switch(message) {
        case CS_NOTIFY: notification(pc); return;
        case CS_LIST: list(pc,kind,page); return;
        case CS_DETAIL: detail(pc,id); return;
        case CS_CREATE:
            Room created=null;
            if(build!=null) created=DUNGEON.create(pc,build.kind,build.minLevel,build.boost,
                    build.closed,build.title,build.password,build.max,build.fee,build.distribution);
            reply(pc,SC_CREATE,created==null?2:1,created==null?0:created.id);
            if(created!=null) status(created);
            return;
        case CS_ENTER: {
            boolean ok=DUNGEON.join(pc,id,password);
            reply(pc,SC_ENTER,ok?1:2,id);
            if(ok) status(DUNGEON.room(id));
            return;
        }
        case CS_READY: {
            boolean ok=DUNGEON.ready(pc,id,ready);
            ProtoWriter w=Proto.begin(pc,SC_READY,80);
            if(w!=null) { try { w.int32(1,ok?1:2).int32(2,id).bool(3,ready); Proto.commit(w); }
                          finally { Proto.abort(w); } }
            if(ok) status(DUNGEON.room(id));
            return;
        }
        case CS_START:
            // An accepted request is not yet a started game: CacheD must commit
            // the key debit first. Success ACK is emitted by admit() immediately
            // before the original dungeon teleport / client transition.
            if(!DUNGEON.start(pc,id)) reply(pc,SC_START,2,id);
            return;
        case CS_EXIT: {
            Room old=DUNGEON.room(id);
            boolean ok=DUNGEON.leave(pc,id);
            reply(pc,SC_EXIT,ok?1:2,id);
            if(ok && old!=null) status(old);
            return;
        }
        case CS_CHANGE: {
            boolean ok=change!=null && DUNGEON.change(pc,id,change.title,change.level,
                    change.closed,change.password,change.max,change.distribution,change.boost);
            reply(pc,SC_CHANGE,ok?1:2,id);
            if(ok) status(DUNGEON.room(id));
            return;
        }
        case CS_INVITE: {
            boolean ok=DUNGEON.invite(pc,id,inviteName);
            ProtoWriter ack=Proto.begin(pc,SC_INVITE,80);
            if(ack!=null) {
                try {ack.int32(1,ok?1:2).int32(2,id).int32(3,pc.getId());Proto.commit(ack);}
                finally {Proto.abort(ack);}
            }
            return;
        }
        case CS_KICK: {
            boolean ok=DUNGEON.kick(pc,id,kickId);
            ProtoWriter ack=Proto.begin(pc,SC_KICK,80);
            if(ack!=null) {
                try {ack.int32(1,ok?1:2).int32(2,id).int32(3,kickId);Proto.commit(ack);}
                finally {Proto.abort(ack);}
            }
            if(ok) status(DUNGEON.room(id));
            return;
        }
        default: return;
        }
    }

    public static void inviteResponse(L1PcInstance pc,boolean accept) {
        Room r=DUNGEON.answerInvite(pc,accept);
        if(!accept) return;
        reply(pc,SC_ENTER,r==null?2:1,r==null?0:r.id);
        if(r!=null) status(r);
    }

    private static final class Change {
        String title,password;
        Integer level,distribution,max;
        Boolean closed,boost;
    }

    private static Build readBuild(ProtoReader in) {
        Build b=new Build();
        for(int tag;(tag=in.next())!=0;) {
            switch(tag) {
            case 0x0a: b.title=new String(in.readBytes(),MS949); break;
            case 0x10: b.minLevel=in.varint32(); break;
            case 0x18: b.fee=in.varint32(); break;
            case 0x20: in.varint32(); break;
            case 0x28: b.distribution=in.varint32(); break;
            case 0x30: b.closed=in.varint32()!=0; break;
            case 0x38: b.max=in.varint32(); break;
            case 0x42: b.password=in.readString(); break;
            case 0x48: b.kind=in.varint32(); break;
            case 0x68: b.boost=in.varint32()!=0; break;
            default: in.skip(tag&7); break;
            }
        }
        return b;
    }

    public static void reply(L1PcInstance pc,int msg,int result,int roomId) {
        ProtoWriter w=Proto.begin(pc,msg,80);
        if(w==null) return;
        try { w.int32(1,result); if(roomId>0) w.int32(2,roomId); Proto.commit(w); }
        finally { Proto.abort(w); }
    }

    private static void roomInfo(ProtoWriter w,Room r) {
        w.int32(1,r.id).bytes(2,r.title.getBytes(MS949)).int32(3,r.members.size())
         .int32(4,r.maximum).int32(5,r.kind==202?2:1).int32(6,r.kind)
         .bool(7,r.closed).int32(8,r.minLevel).int32(9,r.fee)
         .int32(10,r.distribution).bool(11,r.started).bool(12,false).bool(13,r.boost);
    }
    public static void notification(L1PcInstance pc) {
        ProtoWriter w=Proto.begin(pc,SC_NOTIFY,160);
        if(w==null) return;
        try {
            w.int32(1,1);
            w.msg(2).int32(1,GludioInstanceDungeon.MINOR).int32(2,DUNGEON.count(GludioInstanceDungeon.MINOR)).end();
            w.msg(2).int32(1,GludioInstanceDungeon.NORMAL).int32(2,DUNGEON.count(GludioInstanceDungeon.NORMAL)).end();
            Proto.commit(w);
        } finally { Proto.abort(w); }
    }
    public static void list(L1PcInstance pc,int kind,int page) {
        Room[] all=DUNGEON.roomList(kind);
        Arrays.sort(all,(a,b)->Integer.compare(a.id,b.id));
        int total=Math.max(1,(all.length+5)/6);
        page=Math.max(1,Math.min(total,page));
        ProtoWriter w=Proto.begin(pc,SC_LIST,8192);
        if(w==null) return;
        try {
            w.int32(1,1).int32(2,page).int32(3,total);
            int start=(page-1)*6;
            for(int i=start;i<Math.min(all.length,start+6);i++) { w.msg(4); roomInfo(w,all[i]); w.end(); }
            Proto.commit(w);
        } finally { Proto.abort(w); }
    }
    public static void detail(L1PcInstance pc,int roomId) {
        Room r=DUNGEON.room(roomId);
        ProtoWriter w=Proto.begin(pc,SC_DETAIL,180);
        if(w==null) return;
        try {
            w.int32(1,r==null?2:1);
            if(r!=null) {
                w.msg(2).int32(1,r.minLevel).int32(2,1).int32(3,r.maximum)
                 .int32(4,r.distribution).msg(5).int32(1,r.fee).int32(2,GludioInstanceDungeon.KEY_ITEM)
                 .end().int32(6,r.kind).bool(7,r.boost).end();
            }
            Proto.commit(w);
        } finally { Proto.abort(w); }
    }

    public static void status(Room r) {
        if(r==null || r.ended) return;
        for(Member target:DUNGEON.members(r)) {
            ProtoWriter w=Proto.begin(target.pc,SC_STATUS,2048);
            if(w==null) continue;
            try {
                w.int32(1,r.id).int32(2,0);
                for(Member m:DUNGEON.members(r)) { w.msg(3); writeUser(w,m,r); w.end(); }
                w.msg(4); roomInfo(w,r); w.end();
                Proto.commit(w);
            } finally { Proto.abort(w); }
        }
    }
    private static void writeUser(ProtoWriter w,Member m,Room r) {
        w.int32(1,m.charId).int32(2,0).bytes(3,m.pc.getName().getBytes(MS949))
         .int32(4,m.pc.getClassNumber()).int32(5,0).int32(6,1).int32(7,1)
         .bool(8,m.charId==r.ownerId).bool(9,m.ready).bool(10,true);
    }

    // SC_ARENA_GAME_INFO_NOTI: original GludioInDungeonEx.checkMember()
    // sends this AFTER the character has entered the dungeon map.
    public static void gameInfo(L1PcInstance pc,int seconds,int kind) {
        if(pc==null) return;
        ProtoWriter w=Proto.begin(pc,SC_GAME_INFO,120);
        if(w==null) return;
        try {
            w.int32(2,kind).int32(3,0).int32(4,seconds).int32(5,0);
            Proto.commit(w);
        } finally { Proto.abort(w); }
    }

    public static void time(L1PcInstance pc,int seconds,boolean running,int kind) {
        if(pc==null) return;
        // Original SC_ARENA_PLAY_STATUS_NOTI nested ArenaGameStatus.
        ProtoWriter w=Proto.begin(pc,SC_PLAY_STATUS,120);
        if(w!=null) {
            try { w.msg(2).int32(1,seconds).bool(2,running).end(); Proto.commit(w); }
            finally { Proto.abort(w); }
        }
    }
}
