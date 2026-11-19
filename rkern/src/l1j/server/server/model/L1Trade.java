package l1j.server.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.Warehouse;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ItemStatus;
import l1j.server.server.model.network.core.sc.packet.SC_TradeAddItem;
import l1j.server.server.model.network.core.sc.packet.SC_TradeStatus;
import l1j.server.server.monitor.LoggerInstance;

public class L1Trade {
    private static final Logger LOG=Logger.getLogger(L1Trade.class.getName());
    private static boolean characterToken(L1ItemInstance item) {
        return item.getItemId()==L1ItemId.HIGH_CHARACTER_TRADE || item.getItemId()==L1ItemId.LOW_CHARACTER_TRADE;
    }
    /** The trade window contains detached offers, never ownership. Keeping the
     * real row/count in the owner's inventory until COMMIT makes autosave,
     * cancellation, partial offers and reconnect agree with the database.
     */
    public void TradeAddItem(L1PcInstance player,int itemId,int amount) {
        if(player==null || amount<=0)return;
        L1Object object=L1World.getInstance().findObject(player.getTradeID());
        if(!(object instanceof L1PcInstance))return;
        L1PcInstance partner=(L1PcInstance)object;
        if(partner==player || partner.getTradeID()!=player.getId())return;
        L1PcInstance first=player.getId()<partner.getId()?player:partner,second=first==player?partner:player;
        if(!first.tryBeginTradeResolution())return;
        if(!second.tryBeginTradeResolution()){first.endTradeResolution();return;}
        try {
            synchronized(player.getTradeWindowInventory()) {
                if(player.getTradeID()!=partner.getId() || partner.getTradeID()!=player.getId()
                        || player.isItemTransferBusy() || partner.isItemTransferBusy()
                        || player.getTradeOk() || partner.getTradeOk())return;
                L1ItemInstance source=player.getInventory().getItem(itemId);
                if(source==null || source.isEquipped() || !source.getItem().isTradable() || source.getBless()>=128
                        || (!source.isStackable() && amount!=1))return;
                if(characterToken(source)) {unsupportedCharacterTrade(player);return;}
                L1ItemInstance previous=player.getTradeWindowInventory().getItem(itemId);
                long offered=(long)amount+(previous==null?0:previous.getCount());
                if(offered>source.getCount() || offered>L1Inventory.MAX_AMOUNT)return;
                // Preserve distinct persistent IDs even for equal templates.
                L1ItemInstance offer=Warehouse.transferCopy(source,source.getId(),(int)offered,source.getChargeCount());
                if(previous!=null)player.getTradeWindowInventory()._items.remove(previous);
                player.getTradeWindowInventory()._items.add(offer);
                SC_TradeAddItem.send(player,offer,amount,0);
                SC_TradeAddItem.send(partner,offer,amount,1);
            }
        } finally {second.endTradeResolution();first.endTradeResolution();}
    }
    public void doCharacterTrade(L1PcInstance player,boolean first,L1PcInstance partner,boolean second) {
        if(player==null)return;unsupportedCharacterTrade(player);TradeCancel(player);
    }
    private static void unsupportedCharacterTrade(L1PcInstance pc) {
        SC_ChatPacket.sendDirect(pc,pc,"캐릭터 거래는 안전한 계정 이전 처리가 준비될 때까지 이용할 수 없습니다.");
    }
    private static final class Offered {
        final L1PcInstance from,to;final L1ItemInstance item;final int amount;
        Offered(L1PcInstance from,L1PcInstance to,L1ItemInstance item){this.from=from;this.to=to;this.item=item;amount=item.getCount();}
    }
    private static void plan(L1InventoryTransaction tx,L1PcInstance from,L1PcInstance to,List<Offered> logs) {
        for(L1ItemInstance offer:from.getTradeWindowInventory().getItems()) {
            L1ItemInstance source=from.getInventory().getItem(offer.getId());
            if(source==null || source.getItemId()!=offer.getItemId() || source.getCount()<offer.getCount()
                    || offer.getCount()<=0 || source.isEquipped() || characterToken(source)
                    || source.getEnchantLevel()!=offer.getEnchantLevel() || source.getBless()!=offer.getBless()
                    || source.getChargeCount()!=offer.getChargeCount() || source.getAttrEnchantLevel()!=offer.getAttrEnchantLevel()
                    || source.getSpecialEnchant()!=offer.getSpecialEnchant()
                    || source.isPackage()!=offer.isPackage()
                    || !java.util.Objects.equals(source.getEndTime(),offer.getEndTime()))
                throw new IllegalStateException("Trade offer preimage changed");
            tx.transfer(from,source,offer.getCount(),to);
            logs.add(new Offered(from,to,offer));
        }
    }
    public boolean TradeOK(L1PcInstance player) {
        if(player==null || player.isItemTransferBusy())return false;
        L1Object object=L1World.getInstance().findObject(player.getTradeID());
        if(!(object instanceof L1PcInstance)) {TradeCancel(player);return false;}
        L1PcInstance partner=(L1PcInstance)object;
        if(partner==player || partner.getTradeID()!=player.getId()
                || !player.getTradeOk() || !partner.getTradeOk() || partner.isItemTransferBusy())return false;
        if(player.getTradeWindowInventory().getSize()==0 && partner.getTradeWindowInventory().getSize()==0){TradeCancel(player);return false;}
        try(L1InventoryTransaction tx=L1InventoryTransaction.beginTrade(player,partner)) {
            List<Offered> logs=new ArrayList<>();
            plan(tx,player,partner,logs);plan(tx,partner,player,logs);
            if(logs.isEmpty()){TradeCancel(player);return false;}
            tx.onFailure(()->{
                player.endTradeResolution();partner.endTradeResolution();
                player.setTradeOk(false);partner.setTradeOk(false);
                SC_ChatPacket.sendDirect(player,player,"거래를 저장하지 못했습니다. 아이템은 이동되지 않았습니다.");
            });
            tx.commit(()->{
                player.getTradeWindowInventory()._items.clear();partner.getTradeWindowInventory()._items.clear();
                player.setTradeOk(false);partner.setTradeOk(false);player.setTradeID(0);partner.setTradeID(0);
                player.endTradeResolution();partner.endTradeResolution();
                for(Offered line:logs)try{
                    if(line.item.getItemId()==400254)LoggerInstance.getInstance().수표(true,line.from,line.to,line.item,line.amount);
                    else LoggerInstance.getInstance().addTrade(true,line.from,line.to,line.item,line.amount);
                }catch(RuntimeException failure){LOG.log(Level.WARNING,"Committed trade audit failed",failure);}
                SC_TradeStatus.send(player,0);SC_TradeStatus.send(partner,0);
                try{player.getLight().turnOnOffLight();partner.getLight().turnOnOffLight();}
                catch(RuntimeException failure){LOG.log(Level.WARNING,"Committed trade light refresh",failure);}
            });
            return true;
        } catch(RuntimeException failure) {
            LOG.log(Level.WARNING,"Trade not admitted",failure);
            TradeCancel(player);return false;
        }
    }
    private static void clearOffers(L1PcInstance pc) {
        synchronized(pc.getTradeWindowInventory()) {
            for(L1ItemInstance offer:pc.getTradeWindowInventory().getItems()) {
                L1ItemInstance source=pc.getInventory().getItem(offer.getId());
                if(source!=null)try{SC_ItemStatus.send(pc,source);}catch(RuntimeException failure){LOG.fine("Trade resync send expired");}
            }
            pc.getTradeWindowInventory()._items.clear();
        }
    }
    public void TradeCancel(L1PcInstance player) {
        if(player==null || player.isItemTransferBusy())return;
        L1Object object=L1World.getInstance().findObject(player.getTradeID());
        L1PcInstance partner=object instanceof L1PcInstance && object!=player
                && ((L1PcInstance)object).getTradeID()==player.getId()?(L1PcInstance)object:null;
        if(partner!=null && partner.isItemTransferBusy())return;
        player.setTradeOk(false);player.setTradeID(0);clearOffers(player);
        if(partner!=null){partner.setTradeOk(false);partner.setTradeID(0);clearOffers(partner);}
        try{SC_TradeStatus.send(player,1);if(partner!=null)SC_TradeStatus.send(partner,1);}
        catch(RuntimeException failure){LOG.fine("Trade cancel response expired");}
    }
}
