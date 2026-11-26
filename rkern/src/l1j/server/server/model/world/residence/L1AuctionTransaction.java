package l1j.server.server.model.world.residence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1PendingCredit;
import l1j.server.server.model.L1World;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;

/** Existing auction actions share one CacheD resource lane and receipt engine. */
public final class L1AuctionTransaction {
    public static final int ORDER_KEY=CacheD.accountOrderKey("$auction-global");
    private static final Logger LOG=Logger.getLogger(L1AuctionTransaction.class.getName());
    private L1AuctionTransaction() {}
    public static void bid(L1PcInstance pc,int houseId,int amount) {
        L1Clan clan=pc.getClan();
        if(clan==null || !pc.isCrown() || clan.getLeaderId()!=pc.getId() || clan.getHouseId()!=0)return;
        L1AuctionBoard board=AuctionBoardTable.getInstance().getAuctionBoardTable(houseId);
        if(board==null || board.getDeadline()==null || board.getDeadline().getTimeInMillis()<=System.currentTimeMillis()
                || amount<=board.getPrice() || board.getBidderId()==pc.getId())return;
        for(L1AuctionBoard existing:AuctionBoardTable.getInstance().getAuctionBoardTableList())
            if(existing.getBidderId()==pc.getId())return;
        final int price=board.getPrice(),oldBidder=board.getBidderId();
        try(L1InventoryTransaction tx=L1InventoryTransaction.begin(pc,ORDER_KEY)) {
            if(!tx.debit(40308,amount))return;
            tx.requireTables("board_auction","clan_data");
            tx.sql(con->{
                try(PreparedStatement ps=con.prepareStatement("SELECT hashouse,leader_id FROM clan_data WHERE clan_id=? FOR UPDATE")) {
                    ps.setInt(1,clan.getClanId());
                    try(ResultSet rows=ps.executeQuery()){
                        if(!rows.next() || rows.getInt(1)!=0 || rows.getInt(2)!=pc.getId())throw new SQLException("Auction bidder eligibility changed");
                    }
                }
                try(PreparedStatement ps=con.prepareStatement("UPDATE board_auction SET price=?,bidder=?,bidder_id=? "
                        +"WHERE house_id=? AND price=? AND bidder_id=? AND deadline>NOW()")) {
                    ps.setInt(1,amount);ps.setString(2,pc.getName());ps.setInt(3,pc.getId());
                    ps.setInt(4,houseId);ps.setInt(5,price);ps.setInt(6,oldBidder);
                    if(ps.executeUpdate()!=1)throw new SQLException("Auction bid preimage/deadline changed");
                }
            });
            if(oldBidder>0)tx.sql(L1PendingCredit.prepare(oldBidder,price,"AUCTION_REFUND"));
            tx.commit(()->{
                synchronized(board){
                    if(board.getPrice()!=price || board.getBidderId()!=oldBidder)throw new IllegalStateException("Auction memory changed after commit");
                    board.setPrice(amount);board.setBidder(pc.getName());board.setBidderId(pc.getId());
                }
                if(oldBidder>0)L1PendingCredit.notifyOnline(oldBidder);
            });
        } catch(RuntimeException failure){SC_SystemMessage.send(pc,"입찰을 완료하지 못했습니다. 잔액을 확인해 주세요.");}
    }
    public static void list(L1PcInstance pc,L1House house,int price) {
        if(pc==null || house==null || price<=0 || price>CInventory.MAX_AMOUNT || pc.getClan()==null
                || pc.getClan().getLeaderId()!=pc.getId() || pc.getClan().getHouseId()!=house.getHouseId() || house.isOnSale())return;
        L1AuctionBoard board=boardFor(house,price);board.setOldOwner(pc.getName());board.setOldOwnerId(pc.getId());
        try(L1InventoryTransaction tx=L1InventoryTransaction.begin(pc,ORDER_KEY)) {
            tx.requireTables("board_auction","house","clan_data");
            tx.sql(con->{
                try(PreparedStatement ps=con.prepareStatement("SELECT hashouse FROM clan_data WHERE leader_id=? FOR UPDATE")) {
                    ps.setInt(1,pc.getId());
                    try(ResultSet rows=ps.executeQuery()){if(!rows.next() || rows.getInt(1)!=house.getHouseId())throw new SQLException("House ownership changed");}
                }
                insert(con,board);
                try(PreparedStatement ps=con.prepareStatement("UPDATE house SET is_on_sale=1,is_purchase_basement=1 WHERE house_id=? AND is_on_sale=0")) {
                    ps.setInt(1,house.getHouseId());if(ps.executeUpdate()!=1)throw new SQLException("House already on auction");
                }
            });
            tx.commit(()->{house.setOnSale(true);house.setPurchaseBasement(true);AuctionBoardTable.getInstance().putCommitted(board);});
        } catch(RuntimeException failure){SC_SystemMessage.send(pc,"경매 등록을 완료하지 못했습니다.");}
    }
    private static L1AuctionBoard boardFor(L1House house,int price) {
        L1AuctionBoard b=new L1AuctionBoard();b.setHouseId(house.getHouseId());b.setHouseName(house.getHouseName());
        b.setHouseArea(house.getHouseArea());b.setLocation(house.getLocation());b.setPrice(price);
        Calendar deadline=Calendar.getInstance(java.util.TimeZone.getTimeZone(Config.TIME_ZONE));deadline.add(Calendar.DATE,1);
        deadline.set(Calendar.MINUTE,0);deadline.set(Calendar.SECOND,0);deadline.set(Calendar.MILLISECOND,0);
        b.setDeadline(deadline);b.setOldOwner("");b.setOldOwnerId(0);b.setBidder("");b.setBidderId(0);return b;
    }
    private static void insert(java.sql.Connection con,L1AuctionBoard b)throws SQLException {
        try(PreparedStatement ps=con.prepareStatement("INSERT INTO board_auction "
                +"(house_id,house_name,house_area,deadline,price,location,old_owner,old_owner_id,bidder,bidder_id) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            ps.setInt(1,b.getHouseId());ps.setString(2,b.getHouseName());ps.setInt(3,b.getHouseArea());
            ps.setTimestamp(4,new Timestamp(b.getDeadline().getTimeInMillis()));ps.setInt(5,b.getPrice());ps.setString(6,b.getLocation());
            ps.setString(7,b.getOldOwner());ps.setInt(8,b.getOldOwnerId());ps.setString(9,b.getBidder());ps.setInt(10,b.getBidderId());
            if(ps.executeUpdate()!=1)throw new SQLException("Auction insert failed");
        }
    }
    /** Rename uses the same CacheD global house/auction lane, not JDBC on the
     * CIO owner. Ownership and the original name are checked inside COMMIT.
     */
    public static void renameHouse(L1PcInstance pc, int houseId, String name) {
        if (pc == null || name == null || name.isEmpty() || name.length() > 16 || houseId <= 0)
            return;
        final L1Clan clan = pc.getClan();
        if (!pc.isCrown() || clan == null || clan.getLeaderId() != pc.getId()
                || clan.getHouseId() != houseId) {
            SC_SystemMessage.send(pc, "아지트 이름 변경 권한이 없습니다.");
            return;
        }
        final int clanId = clan.getClanId();
        final HouseTable houses = HouseTable.getInstance();
        final L1House house = houses.getHouseTable(houseId);
        if (house == null || house.isOnSale()) {
            SC_SystemMessage.send(pc, "아지트 이름을 변경할 수 없습니다.");
            return;
        }
        final String originalName = house.getHouseName();
        if (java.util.Objects.equals(originalName, name)) return;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, ORDER_KEY)) {
            tx.requireTables("house", "clan_data");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE house SET house_name=? WHERE house_id=? AND house_name=? "
                        + "AND is_on_sale=0 AND EXISTS (SELECT 1 FROM clan_data "
                        + "WHERE clan_id=? AND leader_id=? AND hashouse=?)")) {
                    ps.setString(1, name);
                    ps.setInt(2, houseId);
                    ps.setString(3, originalName);
                    ps.setInt(4, clanId);
                    ps.setInt(5, pc.getId());
                    ps.setInt(6, houseId);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("House rename owner/state before-image changed");
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc, "아지트 이름 변경을 완료하지 못했습니다."));
            tx.commit(() -> {
                if (HouseTable.getInstance() != houses || houses.getHouseTable(houseId) != house
                        || pc.getClan() != clan || pc.getClanid() != clanId
                        || clan.getLeaderId() != pc.getId() || clan.getHouseId() != houseId
                        || house.isOnSale() || !java.util.Objects.equals(originalName, house.getHouseName()))
                    throw new IllegalStateException("House rename memory preimage changed after COMMIT");
                house.setHouseName(name);
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "House rename not admitted houseId=" + houseId, failure);
            SC_SystemMessage.send(pc, "아지트 이름 변경을 시작하지 못했습니다.");
        }
    }

    /** Basement purchase and currency debit must commit in the same durable receipt. */
    public static boolean upgradeBasement(L1PcInstance pc, L1House house) {
        if (pc == null || house == null || !pc.isCrown() || pc.getClan() == null)
            return false;
        final int houseId = house.getHouseId(), clanId = pc.getClanid();
        final HouseTable houses = HouseTable.getInstance();
        if (clanId <= 0 || pc.getClan().getLeaderId() != pc.getId()
                || pc.getClan().getHouseId() != houseId
                || houses.getHouseTable(houseId) != house
                || house.isOnSale() || house.isPurchaseBasement())
            return false;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, ORDER_KEY)) {
            if (!tx.debit(40308, 5000000)) return false;
            tx.requireTables("house", "clan_data");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT leader_id,hashouse FROM clan_data WHERE clan_id=? FOR UPDATE")) {
                    ps.setInt(1, clanId);
                    try (ResultSet rows = ps.executeQuery()) {
                        if (!rows.next() || rows.getInt(1) != pc.getId()
                                || rows.getInt(2) != houseId)
                            throw new SQLException("Basement owner/leader changed");
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE house SET is_purchase_basement=1 WHERE house_id=? "
                        + "AND is_purchase_basement=0 AND is_on_sale=0")) {
                    ps.setInt(1, houseId);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Basement state changed");
                }
            });
            tx.commit(() -> {
                if (HouseTable.getInstance() != houses || houses.getHouseTable(houseId) != house
                        || pc.getClan() == null || pc.getClanid() != clanId
                        || pc.getClan().getLeaderId() != pc.getId()
                        || pc.getClan().getHouseId() != houseId
                        || house.isOnSale() || house.isPurchaseBasement())
                    throw new IllegalStateException("Basement memory preimage changed after commit");
                house.setPurchaseBasement(true);
            });
            return true;
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Basement purchase not admitted houseId=" + houseId, failure);
            return false;
        }
    }

    /** Tax payment and adena debit share one CacheD receipt. No live state
     * changes before the SQL commit; failed/ambiguous commits retain the gate.
     */
    public static void payTax(L1PcInstance pc,L1House house) {
        if(pc==null || house==null || pc.getClan()==null)return;
        final int houseId=house.getHouseId(),clanId=pc.getClanid();
        final HouseTable houses=HouseTable.getInstance();
        if(clanId<=0 || pc.getClan().getHouseId()!=houseId
                || houses.getHouseTable(houseId)!=house || house.isOnSale()
                || house.getTaxDeadline()==null)return;
        final Calendar check=Calendar.getInstance(java.util.TimeZone.getTimeZone(Config.TIME_ZONE));
        check.add(Calendar.DATE,3);
        check.set(Calendar.MINUTE,0);check.set(Calendar.SECOND,0);check.set(Calendar.MILLISECOND,0);
        if(house.getTaxDeadline().after(check))return;
        final long previous=house.getTaxDeadline().getTimeInMillis();
        final Calendar next=Calendar.getInstance(java.util.TimeZone.getTimeZone(Config.TIME_ZONE));
        next.add(Calendar.DATE,Config.HOUSE_TAX_INTERVAL);
        next.set(Calendar.MINUTE,0);next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);
        try(L1InventoryTransaction tx=L1InventoryTransaction.begin(pc,ORDER_KEY)) {
            if(!tx.debit(40308,2000)) {SC_SystemMessage.send(pc,"아데나가 부족합니다.");return;}
            tx.requireTables("house","clan_data");
            tx.sql(con->{
                try(PreparedStatement ps=con.prepareStatement("SELECT hashouse FROM clan_data WHERE clan_id=? FOR UPDATE")) {
                    ps.setInt(1,clanId);
                    try(ResultSet rows=ps.executeQuery()) {
                        if(!rows.next() || rows.getInt(1)!=houseId)
                            throw new SQLException("Tax payer no longer owns house");
                    }
                }
                try(PreparedStatement ps=con.prepareStatement("UPDATE house SET tax_deadline=? "
                        +"WHERE house_id=? AND tax_deadline=? AND is_on_sale=0")) {
                    ps.setTimestamp(1,new Timestamp(next.getTimeInMillis()));
                    ps.setInt(2,houseId);ps.setTimestamp(3,new Timestamp(previous));
                    if(ps.executeUpdate()!=1)throw new SQLException("House tax deadline changed");
                }
            });
            tx.commit(()->{
                if(HouseTable.getInstance()!=houses || houses.getHouseTable(houseId)!=house
                        || pc.getClan()==null || pc.getClanid()!=clanId
                        || pc.getClan().getHouseId()!=houseId || house.isOnSale()
                        || house.getTaxDeadline()==null
                        || house.getTaxDeadline().getTimeInMillis()!=previous)
                    throw new IllegalStateException("House tax memory preimage changed after commit");
                house.setTaxDeadline(next);
            });
        } catch(RuntimeException failure) {
            LOG.log(Level.WARNING,"House tax payment not admitted houseId="+houseId,failure);
            SC_SystemMessage.send(pc,"아지트 세금 납부를 완료하지 못했습니다.");
        }
    }
    /** Called on the existing house timer. Receipt contains foreclosure and listing. */
    public static void foreclose(L1House house) {
        if(house==null || house.isOnSale() || house.getTaxDeadline()==null)return;
        final L1World world=L1World.getInstance();
        final HouseTable houses=HouseTable.getInstance();
        final AuctionBoardTable boards=AuctionBoardTable.getInstance();
        if(houses.getHouseTable(house.getHouseId())!=house
                || boards.getAuctionBoardTable(house.getHouseId())!=null)return;
        final boolean previousBasement=house.isPurchaseBasement();
        final List<ClanHouseImage> clans=clanImages(world,house.getHouseId(),0);
        final L1AuctionBoard board=boardFor(house,100000);
        final Timestamp previous=new Timestamp(house.getTaxDeadline().getTimeInMillis());
        final Calendar next=(Calendar)board.getDeadline().clone();next.add(Calendar.DATE,Config.HOUSE_TAX_INTERVAL);
        runTimer(world,house.getHouseId(),con->{
            CTransaction.requireTransactionalTables(con,"house","board_auction","clan_data");
            try(PreparedStatement ps=con.prepareStatement("UPDATE house SET is_on_sale=1,is_purchase_basement=1,tax_deadline=? "
                    +"WHERE house_id=? AND is_on_sale=0 AND tax_deadline=? AND tax_deadline<NOW()")) {
                ps.setTimestamp(1,new Timestamp(next.getTimeInMillis()));ps.setInt(2,house.getHouseId());ps.setTimestamp(3,previous);
                if(ps.executeUpdate()!=1)throw new SQLException("Foreclosure preimage changed");
            }
            insert(con,board);
            try(PreparedStatement ps=con.prepareStatement("UPDATE clan_data SET hashouse=0 WHERE hashouse=?")){ps.setInt(1,house.getHouseId());ps.executeUpdate();}
        },()->{
            verifyTimerImage(houses,boards,house,null);
            if(house.isOnSale() || house.isPurchaseBasement()!=previousBasement
                    || house.getTaxDeadline()==null
                    || house.getTaxDeadline().getTimeInMillis()!=previous.getTime())
                throw new IllegalStateException("Foreclosure memory preimage changed");
            verifyClanImages(world,clans,house.getHouseId(),0);
            house.setOnSale(true);house.setPurchaseBasement(true);house.setTaxDeadline(next);
            for(ClanHouseImage image:clans)image.clan.setHouseId(0);
            boards.putCommitted(board);
        });
    }
    /** Settlement and the old owner's durable proceeds are one receipt. */
    public static void expire(L1AuctionBoard board) {
        if(board==null || board.getDeadline()==null)return;
        final L1World world=L1World.getInstance();
        final HouseTable houses=HouseTable.getInstance();
        final AuctionBoardTable boards=AuctionBoardTable.getInstance();
        final int houseId=board.getHouseId(),price=board.getPrice(),bidder=board.getBidderId(),oldOwner=board.getOldOwnerId();
        final L1House house=houses.getHouseTable(houseId);
        if(house==null || boards.getAuctionBoardTable(houseId)!=board)return;
        final boolean previousSale=house.isOnSale(),previousBasement=house.isPurchaseBasement();
        final Long previousTax=house.getTaxDeadline()==null?null:house.getTaxDeadline().getTimeInMillis();
        final List<ClanHouseImage> clans=clanImages(world,houseId,bidder);
        final Timestamp deadline=new Timestamp(board.getDeadline().getTimeInMillis());
        final Calendar next=Calendar.getInstance(java.util.TimeZone.getTimeZone(Config.TIME_ZONE));
        next.add(Calendar.DATE,oldOwner==0 && bidder==0 ? 1 : Config.HOUSE_TAX_INTERVAL);
        next.set(Calendar.MINUTE,0);next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);
        final boolean relist=oldOwner==0 && bidder==0;
        final CTransaction.Mutation credit=oldOwner>0 && bidder>0 && (long)price*9/10>0
                ? L1PendingCredit.prepare(oldOwner,(int)((long)price*9/10),"AUCTION_PROCEEDS") : null;
        runTimer(world,houseId,con->{
            CTransaction.requireTransactionalTables(con,"house","board_auction","clan_data");
            try(PreparedStatement ps=con.prepareStatement("SELECT price,bidder_id,old_owner_id,deadline FROM board_auction WHERE house_id=? FOR UPDATE")) {
                ps.setInt(1,houseId);try(ResultSet rows=ps.executeQuery()){
                    if(!rows.next() || rows.getInt(1)!=price || rows.getInt(2)!=bidder || rows.getInt(3)!=oldOwner
                            || !deadline.equals(rows.getTimestamp(4)) || deadline.getTime()>System.currentTimeMillis())
                        throw new SQLException("Auction settlement preimage changed");
                }
            }
            if(relist) {
                try(PreparedStatement ps=con.prepareStatement("UPDATE board_auction SET deadline=? WHERE house_id=?")){
                    ps.setTimestamp(1,new Timestamp(next.getTimeInMillis()));ps.setInt(2,houseId);ps.executeUpdate();}
                return;
            }
            if(bidder>0) {
                try(PreparedStatement ps=con.prepareStatement("SELECT clan_id,hashouse FROM clan_data WHERE leader_id=? FOR UPDATE")) {
                    ps.setInt(1,bidder);try(ResultSet rows=ps.executeQuery()){
                        if(!rows.next() || (rows.getInt(2)!=0 && rows.getInt(2)!=houseId))throw new SQLException("Winning clan unavailable");}
                }
                try(PreparedStatement ps=con.prepareStatement("UPDATE clan_data SET hashouse=0 WHERE hashouse=?")){ps.setInt(1,houseId);ps.executeUpdate();}
                try(PreparedStatement ps=con.prepareStatement("UPDATE clan_data SET hashouse=? WHERE leader_id=?")){
                    ps.setInt(1,houseId);ps.setInt(2,bidder);if(ps.executeUpdate()!=1)throw new SQLException("Winning clan missing");}
                if(credit!=null)credit.apply(con);
            }
            try(PreparedStatement ps=con.prepareStatement("UPDATE house SET is_on_sale=0,tax_deadline=? WHERE house_id=?")) {
                ps.setTimestamp(1,new Timestamp(next.getTimeInMillis()));ps.setInt(2,houseId);if(ps.executeUpdate()!=1)throw new SQLException("House settlement missing");}
            try(PreparedStatement ps=con.prepareStatement("DELETE FROM board_auction WHERE house_id=?")){ps.setInt(1,houseId);if(ps.executeUpdate()!=1)throw new SQLException("Auction deletion missing");}
        },()->{
            verifyTimerImage(houses,boards,house,board);
            if(board.getPrice()!=price || board.getBidderId()!=bidder || board.getOldOwnerId()!=oldOwner
                    || board.getDeadline()==null || board.getDeadline().getTimeInMillis()!=deadline.getTime()
                    || house.isOnSale()!=previousSale || house.isPurchaseBasement()!=previousBasement
                    || !java.util.Objects.equals(previousTax,
                        house.getTaxDeadline()==null?null:house.getTaxDeadline().getTimeInMillis()))
                throw new IllegalStateException("Auction settlement memory preimage changed");
            if(relist){board.setDeadline(next);return;}
            verifyClanImages(world,clans,houseId,bidder);
            if(bidder>0) {
                int winners=0;
                for(ClanHouseImage image:clans)if(image.leaderId==bidder)winners++;
                if(winners!=1)throw new IllegalStateException("Winning clan memory unavailable");
            }
            if(bidder>0)for(ClanHouseImage image:clans){
                if(image.houseId==houseId)image.clan.setHouseId(0);
                if(image.leaderId==bidder)image.clan.setHouseId(houseId);
            }
            house.setOnSale(false);house.setTaxDeadline(next);boards.removeCommitted(houseId);
            if(credit!=null)L1PendingCredit.notifyOnline(oldOwner);
        });
    }
    private static final class ClanHouseImage {
        final L1Clan clan;
        final int houseId,leaderId;
        ClanHouseImage(L1Clan clan){this.clan=clan;houseId=clan.getHouseId();leaderId=clan.getLeaderId();}
    }
    private static List<ClanHouseImage> clanImages(L1World world,int houseId,int bidder) {
        List<ClanHouseImage> images=new ArrayList<>();
        for(L1Clan clan:world.getAllClans())if(clan.getHouseId()==houseId
                || (bidder>0 && clan.getLeaderId()==bidder))images.add(new ClanHouseImage(clan));
        return images;
    }
    private static void verifyClanImages(L1World world,List<ClanHouseImage> images,int houseId,int bidder) {
        int current=0;
        for(L1Clan clan:world.getAllClans())if(clan.getHouseId()==houseId
                || (bidder>0 && clan.getLeaderId()==bidder))current++;
        if(current!=images.size())throw new IllegalStateException("Auction clan membership changed");
        for(ClanHouseImage image:images)
            if(world.getClan(image.clan.getClanId())!=image.clan || image.clan.getHouseId()!=image.houseId
                    || image.clan.getLeaderId()!=image.leaderId)
                throw new IllegalStateException("Auction clan memory preimage changed");
    }
    private static void verifyTimerImage(HouseTable houses,AuctionBoardTable boards,
            L1House house,L1AuctionBoard board) {
        if(HouseTable.getInstance()!=houses || AuctionBoardTable.getInstance()!=boards
                || houses.getHouseTable(house.getHouseId())!=house
                || boards.getAuctionBoardTable(house.getHouseId())!=board)
            throw new IllegalStateException("Auction world/table identity changed");
    }
    private static void runTimer(L1World world,int house,CTransaction.Mutation operation,Runnable apply) {
        try(CacheD.WarehouseMove move=CacheD.beginEconomyMove(ORDER_KEY,house)) {
            move.stageWarehouseSql(operation);
            // The timer remains an existing trigger only. Post-COMMIT game
            // metadata runs on the existing global CIO owner (shard 0), while
            // both CacheD resource lanes remain reserved until that owner ACK.
            move.commit(()->CIOThreadCallback.getInstance().completeWorldMutation(world,apply,failure->{
                move.finishOwnerApply(failure);
                if(failure!=null)LOG.log(Level.SEVERE,"Auction memory reconciliation required",failure);
            }));
        } catch(RuntimeException busy){LOG.log(Level.FINE,"Auction timer deferred",busy);}
    }
}
