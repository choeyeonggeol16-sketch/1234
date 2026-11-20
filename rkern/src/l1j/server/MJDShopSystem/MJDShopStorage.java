package l1j.server.MJDShopSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;
import l1j.server.server.storage.CacheD;

public class MJDShopStorage implements Runnable{

    public static MJDShopItem copyListing(MJDShopItem source) {
        MJDShopItem copy = new MJDShopItem();
        copy.objId=source.objId; copy.itemId=source.itemId; copy.name=source.name;
        copy.invId=source.invId; copy.count=source.count; copy.price=source.price;
        copy.iden=source.iden; copy.enchant=source.enchant; copy.attr=source.attr;
        copy.isPurchase=source.isPurchase;
        return copy;
    }

    /** Called only by an existing CacheD transaction worker. */
    public static void consumeListing(Connection con, int ownerId,
            MJDShopItem expected, int amount) throws SQLException {
        if (expected == null || amount <= 0 || amount > expected.count)
            throw new SQLException("Invalid private shop listing debit");
        try (PreparedStatement ps=con.prepareStatement(
                "SELECT item_id,count,price,type,enchant,iden,attr FROM character_shop "
                + "WHERE objid=? AND item_objid=? FOR UPDATE")) {
            ps.setInt(1,ownerId); ps.setInt(2,expected.objId);
            try (ResultSet rs=ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1)!=expected.itemId || rs.getInt(2)!=expected.count
                        || rs.getInt(3)!=expected.price || rs.getBoolean(4)!=expected.isPurchase
                        || rs.getInt(5)!=expected.enchant
                        || rs.getInt(6)!=getPackIden2AppIden(expected.iden)
                        || rs.getInt(7)!=expected.attr || rs.next())
                    throw new SQLException("Private shop listing preimage changed");
            }
        }
        String sql=amount==expected.count
                ? "DELETE FROM character_shop WHERE objid=? AND item_objid=? AND count=?"
                : "UPDATE character_shop SET count=count-? WHERE objid=? AND item_objid=? AND count=?";
        try (PreparedStatement ps=con.prepareStatement(sql)) {
            int i=0;
            if (amount!=expected.count) ps.setInt(++i,amount);
            ps.setInt(++i,ownerId); ps.setInt(++i,expected.objId); ps.setInt(++i,expected.count);
            if (ps.executeUpdate()!=1) throw new SQLException("Private shop listing debit failed");
        }
    }
	
	public MJDShopStorage(L1Character c, boolean isDelete) {
        // Capture/admit on the caller before legacy ThreadPool dispatch. The
        // legacy Runnable no longer reads live listings or changes game state.
        if (c == null) return;
        if (isDelete) deleteProcess(c); else updateProcess(c);
    }
	
	@Override
	public void run() {
        // Compatibility for existing execute(new MJDShopStorage(...)) callers;
        // the ordered immutable operation was admitted by the constructor.
    }
	
	public static void clearProcess(){
		Connection 				con	= null;
		PreparedStatement 	pstm 	= null;	
		try{
			con	= DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement("delete from character_shop");
			pstm.executeUpdate();
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(pstm, con);
		}
	}
	
	public static void deleteProcess(L1Character c) {
        if (c == null) return;
        final int ownerId = c.getId();
        CacheD.getInstance().submitDbJobRetryOrdered(ownerId, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement("DELETE FROM character_shop WHERE objid=?")) {
                ps.setInt(1,ownerId); ps.executeUpdate();
            } catch (SQLException e) { throw new IllegalStateException("Private shop close persistence",e); }
        });
        c.disposeShopInfo();
    }
	
	public static void deleteProcess(L1Character c, int itemObjId) {
        final int ownerId = c.getId();
        CacheD.getInstance().submitDbJobRetryOrdered(ownerId, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement("DELETE FROM character_shop WHERE objid=? AND item_objid=?")) {
                ps.setInt(1,ownerId); ps.setInt(2,itemObjId); ps.executeUpdate();
            } catch (SQLException e) { throw new IllegalStateException("Private shop row delete persistence",e); }
        });
    }
	
	public static void updateProcess(ArrayList<L1NpcInstance> cList) {
		PreparedStatement 	pstm 		= null;	
		Connection				con		= null;
		ArrayList<MJDShopItem> list 	= null;
		ArrayList<MJDShopItem> tmp	= null;
		L1Character 				cha	= null;				
		try{
			int cSize = cList.size();
			con		= DatabaseFactory.getInstance().getConnection();
			con.setAutoCommit(false);
			pstm 		= createUpdatePstm(con);
			for(int c=0; c<cSize; c++){
				cha = cList.get(c);
				list = new ArrayList<MJDShopItem>(14);
				tmp = cha.getSellings();

				if(tmp != null)	
					list.addAll(tmp);
				
				tmp = cha.getPurchasings();
				if(tmp != null)
					list.addAll(tmp);
					
				int size = list.size();
				if(size <= 0){
					list.clear();
					list = null;
					continue;
				}
				
				MJDShopItem ditem = null;
				for(int i=0; i<size; i++){
					ditem = list.get(i);
					if(ditem == null)
						continue;
					
					setUpdatePstm(pstm, cha, ditem);
					pstm.addBatch();
					pstm.clearParameters();
				}
			}
			pstm.executeBatch();
			pstm.clearBatch();
			con.commit();
		}catch(Exception e){
			try {
				con.rollback();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		}finally{
			try {
				if(con != null)
					con.setAutoCommit(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
			SQLUtil.close(pstm, con);
		}
	}
	
	private static void updateProcess(L1Character c) {
        final ArrayList<MJDShopItem> snapshot = new ArrayList<>();
        ArrayList<MJDShopItem> list = c.getSellings();
        if (list != null) synchronized (list) { for (MJDShopItem i:list) snapshot.add(copyListing(i)); }
        list = c.getPurchasings();
        if (list != null) synchronized (list) { for (MJDShopItem i:list) snapshot.add(copyListing(i)); }
        submitListingSnapshot(c.getId(), c.getName(), c instanceof L1PcInstance, snapshot, false, null, 0);
    }
	
	public static void updateProcess(L1Character c, MJDShopItem ditem) {
        final ArrayList<MJDShopItem> snapshot = new ArrayList<>(1);
        snapshot.add(copyListing(ditem));
        submitListingSnapshot(c.getId(), c.getName(), c instanceof L1PcInstance, snapshot, false, null, 0);
    }

    /** Replace a player's entire shop in FIFO order before accepting any trade. */
    public static void replacePlayerListings(L1PcInstance owner, ArrayList<MJDShopItem> sells,
            ArrayList<MJDShopItem> buys) {
        final ArrayList<MJDShopItem> snapshot = new ArrayList<>(
                (sells == null ? 0 : sells.size()) + (buys == null ? 0 : buys.size()));
        if (sells != null) for (MJDShopItem item : sells) snapshot.add(copyListing(item));
        if (buys != null) for (MJDShopItem item : buys) snapshot.add(copyListing(item));
        final var client = owner.getNetConnection();
        final Account account = client == null ? null : client.getAccount();
        final String login = account == null ? null : account.getName();
        final int opens = account == null ? 0 : account.Shop_open_count + 1;
        submitListingSnapshot(owner.getId(), owner.getName(), true, snapshot, true, login, opens);
        if (account != null) account.Shop_open_count = opens;
    }

    private static void submitListingSnapshot(int ownerId, String ownerName,
            boolean isUser, ArrayList<MJDShopItem> snapshot, boolean replace, String login, int opens) {
        final int accountLane = login == null ? 0 : CacheD.accountOrderKey(login);
        CacheD.getInstance().submitDbJobRetryOrdered(ownerId, accountLane, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection()) {
                con.setAutoCommit(false);
                try (PreparedStatement ps=createUpdatePstm(con)) {
                    if (replace) {
                        l1j.server.server.storage.cached.CTransaction.requireTransactionalTables(con, "character_shop");
                        try (PreparedStatement delete = con.prepareStatement("DELETE FROM character_shop WHERE objid=?")) {
                            delete.setInt(1, ownerId);
                            delete.executeUpdate();
                        }
                    }
                    for (MJDShopItem listing:snapshot) {
                        setUpdatePstm(ps, ownerId, ownerName, isUser, listing);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    if (login != null) {
                        l1j.server.server.storage.cached.CTransaction.requireTransactionalTables(con, "accounts");
                        try (PreparedStatement count = con.prepareStatement(
                                "UPDATE accounts SET Shop_open_count=? WHERE login=?")) {
                            count.setInt(1, opens);
                            count.setString(2, login);
                            count.executeUpdate();
                        }
                    }
                    con.commit();
                } catch (Exception failure) {
                    try { con.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                    throw failure;
                }
            } catch (Exception e) { throw new IllegalStateException("Private shop snapshot persistence",e); }
        });
    }
	
	private static PreparedStatement createUpdatePstm(Connection con) throws Exception{
		return con.prepareStatement(
				"insert into character_shop set objid=?, item_objid=?, char_name=?, item_id=?, item_name=?, count=?, enchant=?, price=?, type=?, iden=?, attr=?, invgfx=?, isUser=? " +
				"on duplicate key update count=?, enchant=?, price=?, type=?, iden=?, attr=?, invgfx=?, isUser=?");
	}
	
	private static void setUpdatePstm(PreparedStatement pstm, L1Character c, MJDShopItem ditem) throws Exception{
        setUpdatePstm(pstm, c.getId(), c.getName(), c instanceof L1PcInstance, ditem);
    }

    private static void setUpdatePstm(PreparedStatement pstm, int ownerId,
            String ownerName, boolean isUser, MJDShopItem ditem) throws Exception {
        int idx=0;
        int iden=getPackIden2AppIden(ditem.iden);
        pstm.setInt(++idx, ownerId);
        pstm.setInt(++idx, ditem.objId);
        pstm.setString(++idx, ownerName);
		pstm.setInt(++idx, ditem.itemId);
		pstm.setString(++idx, ditem.name);
		pstm.setInt(++idx, ditem.count);
		pstm.setInt(++idx, ditem.enchant);
		pstm.setInt(++idx, ditem.price);
		pstm.setBoolean(++idx, ditem.isPurchase);
		pstm.setInt(++idx, iden);
		pstm.setInt(++idx, ditem.attr);
		pstm.setInt(++idx, ditem.invId);
		pstm.setBoolean(++idx, isUser);
		// on duplicate key.
		pstm.setInt(++idx, ditem.count);
		pstm.setInt(++idx, ditem.enchant);
		pstm.setInt(++idx, ditem.price);
		pstm.setBoolean(++idx, ditem.isPurchase);
		pstm.setInt(++idx, iden);
		pstm.setInt(++idx, ditem.attr);
		pstm.setInt(++idx, ditem.invId);
		pstm.setBoolean(++idx, isUser);
	}
	
	/** pack iden type -> app center iden. **/
	private static final int[] _pIdenToAIden = new int[]{
		3, 2, 4
	};
	public static int getPackIden2AppIden(int i){
		if(i < 0 || i >= _pIdenToAIden.length)
			return 1;
		
		return _pIdenToAIden[i];
	}
	
	/** app center iden type -> pack iden. **/
	private static final int[] _aIdenToPIden = new int[]{
		-1, -1, 1, 0, 2
	};
	public static int getAppIden2PackIden(int i){
		if(i < 0 || i >= _aIdenToPIden.length)
			return -1;
		
		return _aIdenToPIden[i];
	}
}
