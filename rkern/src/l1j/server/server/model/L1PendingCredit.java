package l1j.server.server.model;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.L1InventoryTransaction;

/** A durable credit, never a fire-and-forget payment to an offline object.
 * Created in the payer's receipt; consumed in the recipient's receipt. No
 * executor/worker/queue is added. Undelivered credits survive reconnect/crash.
 */
public final class L1PendingCredit {
    private static final Logger LOG=Logger.getLogger(L1PendingCredit.class.getName());
    private static final int KEY=CacheD.accountOrderKey("$pending-credit");
    private L1PendingCredit() {}
    private static final class Credit {
        final String id; final int amount;
        Credit(String id,int amount){this.id=id;this.amount=amount;}
    }
    public static CTransaction.Mutation prepare(int characterId,int amount,String reason) {
        if(characterId<=0 || amount<=0 || amount>CInventory.MAX_AMOUNT)
            throw new IllegalArgumentException("Credit identity/amount");
        final String id=UUID.randomUUID().toString();
        return con->{
            CTransaction.requireTransactionalTables(con,"character_pending_credits","characters");
            try(PreparedStatement lock=con.prepareStatement("SELECT objid FROM characters WHERE objid=? FOR UPDATE")) {
                lock.setInt(1,characterId);
                try(ResultSet rows=lock.executeQuery()){if(!rows.next())throw new SQLException("Credit recipient missing");}
            }
            try(PreparedStatement ps=con.prepareStatement("INSERT INTO character_pending_credits "
                    +"(credit_id,char_id,amount,reason) VALUES(?,?,?,?)")) {
                ps.setString(1,id);ps.setInt(2,characterId);ps.setInt(3,amount);ps.setString(4,reason);
                if(ps.executeUpdate()!=1)throw new SQLException("Credit was not inserted");
            }
        };
    }
    private static List<Credit> read(Connection con,int characterId,boolean lock)throws SQLException {
        List<Credit> result=new ArrayList<>();
        try(PreparedStatement ps=con.prepareStatement("SELECT credit_id,amount FROM character_pending_credits "
                +"WHERE char_id=? ORDER BY credit_id LIMIT 100"+(lock?" FOR UPDATE":""))) {
            ps.setInt(1,characterId);
            try(ResultSet rows=ps.executeQuery()){while(rows.next()){
                int amount=rows.getInt(2);if(amount<=0)throw new SQLException("Invalid pending credit");
                result.add(new Credit(rows.getString(1),amount));
            }}
        }
        return result;
    }
    private static void remove(Connection con,int owner,Credit credit)throws SQLException {
        try(PreparedStatement ps=con.prepareStatement("DELETE FROM character_pending_credits WHERE credit_id=? AND char_id=? AND amount=?")) {
            ps.setString(1,credit.id);ps.setInt(2,owner);ps.setInt(3,credit.amount);
            if(ps.executeUpdate()!=1)throw new SQLException("Credit already claimed/changed");
        }
    }
    /** May be called after an auction receipt. Failure keeps the ledger entry;
     * login performs authoritative recovery if the current session is busy.
     */
    public static void notifyOnline(int characterId) {
        L1Object object=L1World.getInstance().findObject(characterId);
        if(!(object instanceof L1PcInstance))return;
        final L1PcInstance pc=(L1PcInstance)object;
        final int stamp=pc.getSessionStamp();
        CacheD.getInstance().submitDbJob(()->{
            try(Connection con=DatabaseFactory.getInstance().getConnection()) {
                final List<Credit> credits=read(con,characterId,false);
                if(credits.isEmpty())return;
                CIOThreadCallback.getInstance().completeWarehouseMove(pc,stamp,()->{
                    if(pc.isItemTransferBusy() || pc.getTradeID()!=0)return;
                    try(L1InventoryTransaction tx=L1InventoryTransaction.begin(pc,KEY)) {
                        tx.requireTables("character_pending_credits");
                        for(Credit credit:credits) {
                            if(tx.grant(40308,credit.amount)==null)return;
                            tx.sql(c->remove(c,characterId,credit));
                        }
                        tx.commit(()->SC_SystemMessage.send(pc,"경매 환불/정산금이 지급되었습니다."));
                    }
                },failure->{if(failure!=null)LOG.fine("Credit delivery deferred to login");});
            } catch(SQLException failure){throw new IllegalStateException("Credit read failed",failure);}
        });
    }
    /** Existing CacheD login worker calls this BEFORE loading item rows. */
    public static void recoverBeforeLoad(int characterId)throws SQLException {
        try(Connection con=DatabaseFactory.getInstance().getConnection();
            PreparedStatement ps=con.prepareStatement("SELECT ENGINE FROM information_schema.TABLES "
                +"WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='character_pending_credits'")) {
            try(ResultSet rows=ps.executeQuery()) {
                if(!rows.next())return; // migration absent: no credits can have been accepted
                if(!"InnoDB".equalsIgnoreCase(rows.getString(1)))throw new SQLException("Credit ledger must be InnoDB");
            }
            if(read(con,characterId,false).isEmpty())return;
        }
        CTransaction.persistEconomy(UUID.randomUUID().toString(),characterId,KEY,new CTransaction.Mutation[]{con->{
            CTransaction.requireTransactionalTables(con,"character_pending_credits","character_items","characters");
            try(PreparedStatement ps=con.prepareStatement("SELECT objid FROM characters WHERE objid=? FOR UPDATE")) {
                ps.setInt(1,characterId);
                try(ResultSet rows=ps.executeQuery()){if(!rows.next())throw new SQLException("Credit recipient deleted");}
            }
            List<Credit> credits=read(con,characterId,true);
            if(credits.isEmpty())return;
            int stack=0,before=0;long total=0;
            try(PreparedStatement ps=con.prepareStatement("SELECT id,count FROM character_items "
                    +"WHERE char_id=? AND item_id=40308 ORDER BY id FOR UPDATE")) {
                ps.setInt(1,characterId);
                try(ResultSet rows=ps.executeQuery()){while(rows.next()){
                    if(stack==0){stack=rows.getInt(1);before=rows.getInt(2);}total+=rows.getInt(2);
                }}
            }
            for(Credit credit:credits) {
                if(total+credit.amount>CInventory.MAX_AMOUNT)continue; // credit remains durable at the cap
                if(stack==0) {
                    L1ItemInstance item=new L1ItemInstance(CObjectDB.GetItem(40308),credit.amount);
                    item.setId(IdFactory.getInstance().nextId());item.setIdentified(true);
                    MySqlCharactersItemStorage.insertTradeSnapshot(con,
                        MySqlCharactersItemStorage.ItemSnapshot.capture(item,characterId));
                    stack=item.getId();before=credit.amount;
                } else {
                    try(PreparedStatement ps=con.prepareStatement("UPDATE character_items SET count=? "
                            +"WHERE id=? AND char_id=? AND count=?")) {
                        ps.setInt(1,before+credit.amount);ps.setInt(2,stack);ps.setInt(3,characterId);ps.setInt(4,before);
                        if(ps.executeUpdate()!=1)throw new SQLException("Credit balance changed");
                    }
                    before+=credit.amount;
                }
                total+=credit.amount;remove(con,characterId,credit);
            }
        }});
    }
}
