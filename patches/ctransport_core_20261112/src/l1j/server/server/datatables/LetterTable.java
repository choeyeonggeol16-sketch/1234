package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.IdFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;

/** Connection-bound mail SQL, or explicit asynchronous legacy adapters.
 * A returned legacy ID means admitted, never a confirmed send.
 */
public class LetterTable {
    private static final Logger LOG = Logger.getLogger(LetterTable.class.getName());
    public static final int MAIL_ORDER_KEY = CacheD.accountOrderKey("$letter-global");
    private static final LetterTable INSTANCE = new LetterTable();
    public static LetterTable getInstance() { return INSTANCE; }
    public LetterTable() {}

    public static final class Letter {
        public final int id, code, type;
        public final String sender, receiver;
        public final Object date, subject, content;
        public Letter(int id, int code, Object date, String sender, String receiver,
                int type, Object subject, Object content) {
            if (id <= 0 || sender == null || receiver == null || receiver.isEmpty()
                    || type < 0 || type > 2) throw new IllegalArgumentException("Invalid mail");
            this.id=id; this.code=code; this.type=type; this.sender=sender; this.receiver=receiver;
            this.date=date instanceof Timestamp ? new Timestamp(((Timestamp) date).getTime()) : date;
            this.subject=subject instanceof byte[] ? ((byte[]) subject).clone() : subject;
            this.content=content instanceof byte[] ? ((byte[]) content).clone() : content;
        }
    }

    /** Lock the recipient character, not just existing mail rows: empty mailboxes
     * must also serialize count+insert across concurrent writers.
     */
    public static void lockRecipient(Connection con, String name) throws SQLException {
        try (PreparedStatement ps=con.prepareStatement(
                "SELECT objid FROM characters WHERE char_name=? FOR UPDATE")) {
            ps.setString(1,name);
            try (ResultSet rows=ps.executeQuery()) {
                if (!rows.next()) throw new SQLException("Mail recipient is unavailable", "45000");
            }
        }
    }

    public static int count(Connection con, String receiver, int type) throws SQLException {
        try (PreparedStatement ps=con.prepareStatement(
                "SELECT COUNT(*) FROM letter WHERE receiver=? AND template_id=?")) {
            ps.setString(1,receiver); ps.setInt(2,type);
            try (ResultSet rows=ps.executeQuery()) {
                if (!rows.next()) throw new SQLException("Missing mailbox count");
                return rows.getInt(1);
            }
        }
    }

    public static void insertLetters(Connection con, List<Letter> letters) throws SQLException {
        if (letters == null || letters.isEmpty()) throw new SQLException("Empty mail batch");
        ArrayList<Letter> ordered=new ArrayList<>(letters);
        ordered.sort(Comparator.comparing(x -> x.receiver.toLowerCase(Locale.ROOT)));
        HashSet<String> recipients=new HashSet<>();
        for (Letter letter : ordered) {
            String key=letter.receiver.toLowerCase(Locale.ROOT);
            if (!recipients.add(key)) throw new SQLException("Duplicate mail recipient");
            lockRecipient(con,letter.receiver);
            int limit=letter.type==0 ? 40 : letter.type==1 ? 80 : 10;
            if (count(con,letter.receiver,letter.type)>=limit)
                throw new SQLException("Mailbox is full", "45000");
        }
        for (Letter letter : ordered) insert(con,letter);
    }

    private static void insert(Connection con, Letter letter) throws SQLException {
        try (PreparedStatement ps=con.prepareStatement("INSERT INTO letter "
                + "(item_object_id,code,sender,receiver,date,template_id,subject,content,isCheck) "
                + "VALUES (?,?,?,?,?,?,?,?,0)")) {
            ps.setInt(1,letter.id); ps.setInt(2,letter.code); ps.setString(3,letter.sender);
            ps.setString(4,letter.receiver);
            if (letter.date instanceof Timestamp) ps.setTimestamp(5,(Timestamp)letter.date);
            else ps.setString(5,(String)letter.date);
            ps.setInt(6,letter.type);
            if (letter.subject instanceof byte[]) ps.setBytes(7,(byte[])letter.subject);
            else ps.setString(7,(String)letter.subject);
            if (letter.content instanceof byte[]) ps.setBytes(8,(byte[])letter.content);
            else ps.setString(8,(String)letter.content);
            if (ps.executeUpdate()!=1) throw new SQLException("Mail INSERT did not write exactly one row");
        }
    }

    /** Extra tables must participate in the same transactional storage engine. */
    public static void assertTransactional(Connection con) throws SQLException {
        try (PreparedStatement ps=con.prepareStatement("SELECT TABLE_NAME,ENGINE "
                + "FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME IN ('letter','characters')")) {
            boolean letter=false, characters=false;
            try (ResultSet rows=ps.executeQuery()) {
                while (rows.next()) {
                    String name=rows.getString(1);
                    if (!"InnoDB".equalsIgnoreCase(rows.getString(2)))
                        throw new SQLException("Transactional mail requires InnoDB: "+name);
                    if ("letter".equals(name)) letter=true;
                    if ("characters".equals(name)) characters=true;
                }
            }
            if (!letter || !characters) throw new SQLException("Transactional mail tables are missing");
        }
    }

    private static void enqueue(Letter letter, L1PcInstance owner, Runnable afterCommit) {
        final String receipt=UUID.randomUUID().toString();
        final CIOClient session=owner==null ? null : owner.getNetConnection();
        final int stamp=owner==null ? 0 : owner.getSessionStamp();
        CacheD.getInstance().submitDbJobOrdered(MAIL_ORDER_KEY, () -> {
            try {
                CTransaction.persistWarehouseMove(receipt,MAIL_ORDER_KEY,0,
                        new CTransaction.Mutation[]{LetterTable::assertTransactional,
                            con -> insertLetters(con,java.util.Collections.singletonList(letter))});
                if (afterCommit!=null && owner!=null) {
                    CIOThreadCallback.getInstance().completeWarehouseMove(owner,stamp,()->{
                        if (owner.getNetConnection()==session && session!=null && session.isConnected()
                                && session.getActiveChar()==owner) afterCommit.run();
                    }, failure -> {
                        if (failure!=null) LOG.log(Level.WARNING,"Committed mail notification rejected",failure);
                    });
                }
            } catch (SQLException failure) {
                throw new IllegalStateException("Mail transaction failed",failure);
            }
        });
    }

    public int writeLetter(int code, Timestamp date, String sender, String receiver,
            int type, String subject, String content) {
        int id=IdFactory.getInstance().nextId();
        enqueue(new Letter(id,code,date,sender,receiver,type,subject,content),null,null);
        return id;
    }
    public void writeLetter(int code, Timestamp date, String sender, String receiver,
            int type, String subject, String content, L1PcInstance owner, Runnable afterCommit) {
        enqueue(new Letter(IdFactory.getInstance().nextId(),code,date,sender,receiver,type,subject,content),owner,afterCommit);
    }
    public void writeLetter(int code, String date, String sender, String receiver,
            int type, String subject, String content) {
        enqueue(new Letter(IdFactory.getInstance().nextId(),code,date,sender,receiver,type,subject,content),null,null);
    }
    public void writeLetter(int code, String date, String sender, String receiver,
            int type, String subject, String content, L1PcInstance owner, Runnable afterCommit) {
        enqueue(new Letter(IdFactory.getInstance().nextId(),code,date,sender,receiver,type,subject,content),owner,afterCommit);
    }
    public void writeLetter(int id,int code,String sender,String receiver,String date,
            int type,byte[] subject,byte[] content) {
        enqueue(new Letter(id,code,date,sender,receiver,type,subject,content),null,null);
    }
    public void writeLetter(int id,int code,String sender,String receiver,String date,
            int type,String subject,String content) {
        enqueue(new Letter(id,code,date,sender,receiver,type,subject,content),null,null);
    }

    /** Internal item/GM cleanup adapter; packet handlers must use owner-bound SQL. */
    public void deleteLetter(int id) {
        if (id<=0) return;
        CacheD.getInstance().submitDbJobOrdered(MAIL_ORDER_KEY,()->{
            try (Connection con=l1j.server.DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps=con.prepareStatement("DELETE FROM letter WHERE item_object_id=?")) {
                ps.setInt(1,id);ps.executeUpdate();
            } catch(SQLException failure) { throw new IllegalStateException("Mail cleanup failed",failure); }
        });
    }

    /** Old synchronous packet APIs cannot prove recipient authorization. */
    @Deprecated public int getLetterCount(String name,int type) {
        throw new IllegalStateException("Use connection-bound count on a CacheD worker");
    }
    @Deprecated public void SaveLetter(int id,int type) {
        throw new IllegalStateException("Mail keep requires the authenticated receiver");
    }
    @Deprecated public void CheckLetter(int id) {
        throw new IllegalStateException("Mail read requires the authenticated receiver");
    }
}
