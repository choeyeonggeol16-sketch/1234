package l1j.server.server.model.network.core.cs.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.GMCommands;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.datatables.LetterTable.Letter;
import l1j.server.server.datatables.SpamTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Clan.ClanMember;
import l1j.server.server.model.L1ExcludingList;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_LetterList;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;

public final class CS_Mail {
    private static final Logger LOG=Logger.getLogger(CS_Mail.class.getName());
    private CS_Mail() {}

    public static void handle(CIOClient client,int type,int value0,int value1,
            String receiverName,String subject,Object object) {
        if (client==null) return;
        L1PcInstance pc=client.getActiveChar();
        if (pc==null || pc.isGhost()) return;
        switch(type) {
        case 0: SC_LetterList.sendListAsync(pc,0,40); return;
        case 1: SC_LetterList.sendListAsync(pc,1,80); return;
        case 2: SC_LetterList.sendListAsync(pc,2,10); return;
        case 16: case 17: case 18: readLetter(pc,type,value0); return;
        case 32: writePrivateMail(pc,receiverName,subject,object instanceof String ? (String)object : null,value0); return;
        case 33: writeBloodPledgeMail(pc,receiverName,subject,object instanceof String ? (String)object : null,value0); return;
        case 48: case 49: case 50: deleteLetterList(pc,type,1,value0,null); return;
        case 64: saveLetter(pc,value0); return;
        case 96: case 97: case 98: deleteLetterList(pc,type-48,value0,value1,object); return;
        default: return;
        }
    }

    private static boolean eligibleSender(L1PcInstance sender) {
        if (sender==null || sender.isItemTransferBusy() || sender.isTradeResolving()
                || sender.getTradeID()!=0) return false;
        long now=System.currentTimeMillis()/1000L;
        if (!sender.isGm() && sender.getPostDelay()+1>now) {
            SC_SystemMessage.send(sender,(sender.getPostDelay()+1-now)+"초 후에 보낼 수 있습니다.");
            return false;
        }
        if (sender.getLevel()<Config.To_ChatLevel) {
            SC_SystemMessage.send(sender,"레벨 "+Config.To_ChatLevel+" 이하는 편지를 보낼 수 없습니다.");
            return false;
        }
        return true;
    }

    private static boolean blocked(L1PcInstance sender,String receiver) {
        L1PcInstance target=L1World.getInstance().getPlayer(receiver);
        if (target==null) return false;
        L1ExcludingList exclude=SpamTable.getInstance().getExcludeTable(target.getId());
        if (exclude!=null && exclude.contains(0,sender.getName())) {
            SC_SystemMessage.send(sender,target.getName()+"가 당신을 차단하여 편지를 보낼 수 없습니다.");
            return true;
        }
        return false;
    }

    private static void writePrivateMail(L1PcInstance sender,String receiver,
            String subject,String content,int paper) {
        if (receiver==null || receiver.isEmpty() || subject==null || content==null
                || !eligibleSender(sender) || blocked(sender,receiver)) return;
        List<Letter> letters=new ArrayList<>(1);
        letters.add(new Letter(IdFactory.getInstance().nextId(),paper,
                new Timestamp(System.currentTimeMillis()),sender.getName(),receiver,0,subject,content));
        sendBatch(sender,letters,50);
    }

    private static void writeBloodPledgeMail(L1PcInstance sender,String receiverName,
            String subject,String content,int paper) {
        if (receiverName==null || subject==null || content==null || !eligibleSender(sender)) return;
        L1Clan clan=L1World.getInstance().findClan(receiverName);
        if (clan==null || clan.getClanMemberList()==null) return;
        List<Letter> letters=new ArrayList<>();
        HashSet<String> names=new HashSet<>();
        Timestamp date=new Timestamp(System.currentTimeMillis());
        for (ClanMember member : clan.getClanMemberList()) {
            if (member==null || member.name==null || member.name.equalsIgnoreCase(sender.getName())
                    || !names.add(member.name.toLowerCase(Locale.ROOT))) continue;
            if (blocked(sender,member.name)) return;
            letters.add(new Letter(IdFactory.getInstance().nextId(),paper,date,sender.getName(),
                    member.name,1,subject,content));
        }
        if (!letters.isEmpty()) sendBatch(sender,letters,1000);
    }

    private static void sendBatch(L1PcInstance sender,List<Letter> letters,int price) {
        // The fee and every recipient insert share ONE receipt and connection.
        // No live inventory, post delay or success notification changes here.
        try (L1InventoryTransaction tx=L1InventoryTransaction.begin(sender,LetterTable.MAIL_ORDER_KEY)) {
            if (!tx.debit(L1ItemId.ADENA,price)) {
                SC_ServerMessage.send(sender,189,"");
                return;
            }
            tx.requireTables("letter","characters");
            final List<Letter> prepared=java.util.Collections.unmodifiableList(new ArrayList<>(letters));
            tx.sql(con -> LetterTable.insertLetters(con,prepared));
            tx.onFailure(() -> SC_SystemMessage.send(sender,
                    "편지 전송을 완료하지 못했습니다. 편지함과 잔액을 확인해 주세요."));
            tx.commit(() -> {
                sender.setPostDelay(System.currentTimeMillis()/1000L+5);
                for (Letter letter : prepared) notifyWritten(letter);
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING,"Mail admission failed",failure);
            SC_SystemMessage.send(sender,"이전 처리가 끝난 뒤 다시 시도해 주세요.");
        }
    }

    private static void notifyWritten(Letter letter) {
        final L1PcInstance target=L1World.getInstance().getPlayer(letter.receiver);
        if (target==null) return;
        final CIOClient session=target.getNetConnection();
        final int stamp=target.getSessionStamp();
        CIOThreadCallback.getInstance().completeWarehouseMove(target,stamp,()->{
            if (target.getNetConnection()!=session || session==null || !session.isConnected()
                    || session.getActiveChar()!=target) return;
            SC_LetterList.sendWritten(target,letter.type==0 ? 80 : 81,letter.id,
                    SC_LetterList.TYPE_RECEIVE,letter.sender,(String)letter.subject);
            if (letter.type==0 && target.isGm() && GMCommands._sleepingMessage!=null
                    && !GMCommands._sleepingMessage.isEmpty()) {
                writePrivateMail(target,letter.sender,GMCommands._sleepingTitle,
                        GMCommands._sleepingMessage,letter.code);
            }
        },failure->{if(failure!=null)LOG.log(Level.FINE,"Mail recipient notification expired",failure);});
    }

    private static boolean current(L1PcInstance pc,CIOClient client,int stamp) {
        return client!=null && client.isConnected() && pc.getNetConnection()==client
                && client.getActiveChar()==pc && pc.getSessionStamp()==stamp;
    }

    /** Owner-qualified idempotent metadata operations, on an existing DB worker.
     * This does not change game state; only prepared packets cross back to SEND.
     */
    private static void submitMailSql(L1PcInstance pc,CTransaction.Mutation action,
            Runnable afterCommit) {
        final CIOClient session=pc.getNetConnection();
        final int stamp=pc.getSessionStamp();
        final int owner=pc.getId();
        final String receipt=UUID.randomUUID().toString();
        try {
            CacheD.getInstance().submitDbJobOrdered(LetterTable.MAIL_ORDER_KEY,()->{
                try {
                    CTransaction.persistWarehouseMove(receipt,owner,LetterTable.MAIL_ORDER_KEY,
                            new CTransaction.Mutation[]{LetterTable::assertTransactional,action});
                    CIOThreadCallback.getInstance().completeSessionReply(session,()->{
                        if (current(pc,session,stamp)) afterCommit.run();
                    });
                } catch(SQLException failure) { throw new IllegalStateException("Mail metadata transaction failed",failure); }
            });
        } catch(RuntimeException busy) {
            SC_SystemMessage.send(pc,"편지 처리가 지연되고 있습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static void deleteLetterList(L1PcInstance pc,int type,int count,int singleId,Object object) {
        if (count<=0 || count>=1000) return;
        final int[] ids;
        if (count==1) ids=new int[]{singleId};
        else if (object instanceof int[] && ((int[])object).length==count) ids=((int[])object).clone();
        else return;
        HashSet<Integer> unique=new HashSet<>();
        for(int id:ids) if(id<=0 || !unique.add(id)) return;
        final boolean[] deleted=new boolean[ids.length];
        final String receiver=pc.getName();
        submitMailSql(pc,con->{
            LetterTable.lockRecipient(con,receiver);
            try(PreparedStatement ps=con.prepareStatement("DELETE FROM letter "
                    +"WHERE item_object_id=? AND receiver=? AND template_id=?")) {
                for(int i=0;i<ids.length;i++) {
                    ps.setInt(1,ids[i]);ps.setString(2,receiver);ps.setInt(3,type-48);
                    deleted[i]=ps.executeUpdate()==1;
                }
            }
        },()->{for(int i=0;i<ids.length;i++)SC_LetterList.sendState(pc,type,ids[i],deleted[i]);});
    }

    private static void readLetter(L1PcInstance pc,int type,int id) {
        if(id<=0) return;
        final String receiver=pc.getName();
        final SC_LetterList.Data[] packet=new SC_LetterList.Data[1];
        submitMailSql(pc,con->{
            LetterTable.lockRecipient(con,receiver);
            try(PreparedStatement ps=con.prepareStatement("SELECT content FROM letter "
                    +"WHERE item_object_id=? AND receiver=? AND template_id=? FOR UPDATE")) {
                ps.setInt(1,id);ps.setString(2,receiver);ps.setInt(3,type-16);
                try(ResultSet rows=ps.executeQuery()) {
                    if(!rows.next()) return;
                    packet[0]=SC_LetterList.createRead(type,id,rows.getString(1));
                }
            }
            try(PreparedStatement ps=con.prepareStatement("UPDATE letter SET isCheck=1 "
                    +"WHERE item_object_id=? AND receiver=? AND template_id=?")) {
                ps.setInt(1,id);ps.setString(2,receiver);ps.setInt(3,type-16);
                if(ps.executeUpdate()>1)throw new SQLException("Duplicate mail identity");
            }
        },()->{if(packet[0]!=null)packet[0].send(pc.getNetConnection());});
    }

    private static void saveLetter(L1PcInstance pc,int id) {
        if(id<=0) return;
        final String receiver=pc.getName();
        final boolean[] kept={false};
        submitMailSql(pc,con->{
            LetterTable.lockRecipient(con,receiver);
            try(PreparedStatement ps=con.prepareStatement("SELECT template_id FROM letter "
                    +"WHERE item_object_id=? AND receiver=? FOR UPDATE")) {
                ps.setInt(1,id);ps.setString(2,receiver);
                try(ResultSet rows=ps.executeQuery()) {
                    if(!rows.next())return;
                    if(rows.getInt(1)==2){kept[0]=true;return;}
                }
            }
            if(LetterTable.count(con,receiver,2)>=10)return;
            try(PreparedStatement ps=con.prepareStatement("UPDATE letter SET template_id=2 "
                    +"WHERE item_object_id=? AND receiver=?")) {
                ps.setInt(1,id);ps.setString(2,receiver);
                kept[0]=ps.executeUpdate()==1;
            }
        },()->SC_LetterList.sendState(pc,64,id,kept[0]));
    }
}
