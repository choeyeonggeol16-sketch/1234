package l1j.server.server.model.shop;

import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1TaxCalculator;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SurvivalCry;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.siege.CCastleSiegeManager;
import l1j.server.server.storage.CacheD;
import l1j.server.server.utils.TYUtil;

final class NpcShopSettlement {
    private static final Logger LOG = Logger.getLogger(NpcShopSettlement.class.getName());
    private static final int ADENA_CHECK = 400254;
    private static final int CHECK_VALUE = 100_000_000;
    private static final int ADENA_CHANGE_THRESHOLD = 19 * CHECK_VALUE;
    private static final String BUY_FAILED = "상점 거래를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    private static final String SALE_FAILED = "판매를 완료하지 못했습니다. 아이템과 잔액을 확인해 주세요.";

    private NpcShopSettlement() {}

    static void purchase(CBuySell shop, L1PcInstance pc, L1ShopBuyOrderList orders, Runnable afterCommitted) {
        ShopPolicy policy = shop.policy();
        int npcId = shop.getNpcId();
        int price = policy == ShopPolicy.ADENA ? orders.getTotalPriceTaxIncluded() : orders.getTotalPrice();
        L1Clan clan = policy == ShopPolicy.CLAN ? pc.getClan() : null;
        if (policy == ShopPolicy.CLAN && (clan == null
                || (Config.혈맹포인트상점 && !pc.isGm() && clan.getLeaderId() != pc.getId()))) return;
        if (price <= 0 || price > policy.limit()) {
            if (policy == ShopPolicy.ADENA || policy == ShopPolicy.STOCK)
                SC_ServerMessage.send(pc, 904, "2000000000");
            else SC_SystemMessage.send(pc, policy.overLimit());
            return;
        }

        int orderKey = switch (policy) {
            case ADENA -> taxOrderKey(npcId, orders, pc.getId());
            case CLAN -> clan.getClanId();
            case TAM -> CacheD.accountOrderKey(pc.getAccountName());
            default -> ownerShopLane(pc.getId());
        };
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, orderKey)) {
            for (L1ShopBuyOrder order : orders.orders()) {
                if (!shop.canOffer(order.getItem(), pc)) return;
                if (policy == ShopPolicy.CHECK && Config.IS_CHECKBOX_RULES_USE) {
                    int itemId = order.getItem().getItemId();
                    if (itemId >= 4100251 && itemId <= 4100253) {
                        SC_SystemMessage.send(pc, order.getItem().getItem().getName() + "는 이벤트기간에만 구매가능 합니다.");
                        return;
                    }
                }
            }
            int mileage = policy == ShopPolicy.ADENA ? mileage(npcId, price) : 0;
            if (!stagePayment(tx, policy, pc, clan, npcId, price)) return;

            ShopStock.Reservation reservation = shop.stock() == null ? null : shop.stock().reserve(orders.orders());
            if (shop.stock() != null && reservation == null) {
                SC_SystemMessage.send(pc, "현재 구매할 수 없는 상태입니다.");
                return;
            }
            boolean submitted = false;
            try {
                if (reservation != null || clan != null) {
                    tx.onDurableCommit(() -> {
                        if (reservation != null) reservation.consume();
                        if (clan != null) clan.addWarPointCommitted(-price);
                    });
                    tx.onTerminalFailure(() -> {
                        if (tx.isUncertainFailure()) {
                            if (reservation != null) reservation.consume();
                            if (clan != null) clan.markWarPointUncertain();
                        } else if (reservation != null) reservation.release();
                    });
                }
                int[] ticketIds = ShopPolicy.isRace(npcId) ? new int[orders.orders().size()] : null;
                int row = 0;
                for (L1ShopBuyOrder order : orders.orders()) {
                    L1ItemInstance item = ShopDelivery.create(npcId, policy, order);
                    if (ticketIds != null) ticketIds[row++] = item.getItemId();
                    if (tx.grant(item) == null) throw new IllegalStateException("Shop grant rejected");
                }
                if (mileage > 0) grant(tx, 131300, mileage);

                Runnable publishTax = policy == ShopPolicy.ADENA ? stageTax(tx, npcId, orders) : null;
                tx.onFailure(() -> SC_SystemMessage.send(pc, BUY_FAILED));
                tx.commit(() -> {
                    if (publishTax != null) publishTax.run();
                    if (policy == ShopPolicy.TAM) pc.getAccount().addTamPoint(-price);
                    try {
                        if (mileage > 0) SC_SystemMessage.send(pc, "\\fR[마일리지] 획득 : " + mileage + "개");
                        if (clan != null) SC_SurvivalCry.send(pc, 2);
                        if (policy == ShopPolicy.TAM) SC_ActionUI.send(pc, SC_ActionUI.TAM, pc.getAccount().getTamPoint());
                        if (ticketIds != null) publishRaceTickets(orders, ticketIds);
                        if (afterCommitted != null) afterCommitted.run();
                    } catch (RuntimeException failure) {
                        LOG.log(Level.WARNING, "Committed NPC shop notification failed", failure);
                    }
                });
                submitted = true;
            } finally {
                if (!submitted && reservation != null) reservation.release();
            }
        } catch (RuntimeException failure) {
            LOG.log(Level.FINE, "NPC purchase failed for shop " + npcId, failure);
            SC_SystemMessage.send(pc, BUY_FAILED);
        }
    }

    private static boolean stagePayment(L1InventoryTransaction tx, ShopPolicy policy, L1PcInstance pc,
            L1Clan clan, int npcId, int price) {
        if (policy == ShopPolicy.CLAN) {
            if (pc.getClan() == clan && clan.getWarPoint() >= price) {
                ClanTable.stageWarPointDelta(tx, clan, -price);
                return true;
            }
        } else if (policy == ShopPolicy.TAM) {
            var account = pc.getAccount();
            int before = account.getTamPoint();
            if (before >= price) {
                tx.requireTables("accounts");
                tx.sql(con -> {
                    try (var ps = con.prepareStatement("UPDATE accounts SET Tam_Point=? WHERE login=? AND Tam_Point=?")) {
                        ps.setInt(1, before - price);
                        ps.setString(2, account.getName());
                        ps.setInt(3, before);
                        if (ps.executeUpdate() != 1) throw new SQLException("Account currency changed");
                    }
                });
                return true;
            }
        } else if (policy == ShopPolicy.CHECK) {
            if (stageChecks(tx, pc, price)) return true;
        } else if (tx.debit(policy.currency(npcId), price)) {
            return true;
        }

        if (policy == ShopPolicy.ADENA || policy == ShopPolicy.STOCK) SC_ServerMessage.send(pc, 189);
        else if (policy == ShopPolicy.MOON) SC_ServerMessage.send(pc, 337, "$10196");
        else SC_SystemMessage.send(pc, policy.insufficient);
        return false;
    }

    private static boolean stageChecks(L1InventoryTransaction tx, L1PcInstance pc, int price) {
        int remaining = price;
        for (L1ItemInstance item : pc.getInventory().GetUnequippedItemListByClassID(ADENA_CHECK)) {
            if (item.getBless() >= 128) continue;
            int use = Math.min(remaining, tx.GetPreparedItemAmount(pc, item));
            if (use > 0 && !tx.debit(item, use)) throw new IllegalStateException("Check balance changed");
            remaining -= use;
            if (remaining == 0) return true;
        }
        return false;
    }

    private static void publishRaceTickets(L1ShopBuyOrderList orders, int[] ticketIds) {
        BugRaceController race = BugRaceController.getInstance();
        for (int i = 0; i < ticketIds.length; i++)
            race.on_buy_ticket(ticketIds[i], orders.orders().get(i).getCount());
    }

    private static int mileage(int npcId, int price) {
        String configured = Config.마일리지엔피씨;
        int divisor = Config.마일리지값;
        if (!Config.마일리지지급여부 || divisor <= 0 || price < divisor
                || configured == null || configured.isBlank()) return 0;
        try {
            for (int id : TYUtil.GetIntTokenizeIntArray(configured)) if (id == npcId) return price / divisor;
        } catch (NumberFormatException invalid) {
            LOG.log(Level.WARNING, "Invalid mileage shop configuration", invalid);
        }
        return 0;
    }

    private static int ownerShopLane(int ownerId) {
        return CacheD.accountOrderKey("npc-shop:", ownerId);
    }

    private static int taxOrderKey(int npcId, L1ShopBuyOrderList orders, int ownerId) {
        L1TaxCalculator calc = orders.getTaxCalculator();
        int castleId = L1CastleLocation.getCastleIdByNpcid(npcId);
        boolean taxable = castleId != 0 && calc.calcCastleTaxPrice(orders.getTotalPrice()) > 0
                || calc.calcDiadTaxPrice(orders.getTotalPrice()) > 0;
        return taxable ? CacheD.accountOrderKey("$castle-treasury-global") : ownerShopLane(ownerId);
    }

    private static Runnable stageTax(L1InventoryTransaction tx, int npcId, L1ShopBuyOrderList orders) {
        L1TaxCalculator calc = orders.getTaxCalculator();
        int price = orders.getTotalPrice();
        int castleId = L1CastleLocation.getCastleIdByNpcid(npcId);
        int castleTax = calc.calcCastleTaxPrice(price);
        int nationalTax = calc.calcNationalTaxPrice(price);
        if (castleId == L1CastleLocation.ADEN_CASTLE_ID || castleId == L1CastleLocation.DIAD_CASTLE_ID) {
            castleTax = Math.addExact(castleTax, nationalTax);
            nationalTax = 0;
        }
        int diadTax = calc.calcDiadTaxPrice(price);
        if ((castleId == 0 || (castleTax <= 0 && nationalTax <= 0)) && diadTax <= 0) return null;
        Map<Integer, Integer> taxes = new TreeMap<>();
        if (castleId != 0 && castleTax > 0) taxes.merge(castleId, castleTax, Math::addExact);
        if (castleId != 0 && nationalTax > 0) taxes.merge(L1CastleLocation.ADEN_CASTLE_ID, nationalTax, Math::addExact);
        if (diadTax > 0) taxes.merge(L1CastleLocation.DIAD_CASTLE_ID, diadTax, Math::addExact);
        tx.requireTables("castle");
        Runnable[] updates = new Runnable[taxes.size()];
        int index = 0;
        for (var tax : taxes.entrySet()) {
            var castle = CCastleSiegeManager.getInstance().get(tax.getKey());
            if (castle == null) throw new IllegalStateException("Tax castle missing");
            int before = castle.getPublicMoney();
            long after = (long) before + tax.getValue();
            if (before < 0 || after > CInventory.MAX_AMOUNT) throw new IllegalStateException("Tax treasury limit");
            tx.sql(con -> {
                try (var ps = con.prepareStatement(
                        "UPDATE castle SET public_money=? WHERE castle_id=? AND public_money=?")) {
                    ps.setInt(1, (int) after);
                    ps.setInt(2, tax.getKey());
                    ps.setInt(3, before);
                    if (ps.executeUpdate() != 1) throw new SQLException("Tax treasury changed");
                }
            });
            updates[index++] = () -> castle.setPublicMoney((int) after);
        }
        return () -> {
            for (Runnable update : updates) update.run();
        };
    }

    static void buyback(CBuySell shop, L1ShopSellOrderList orders) {
        L1PcInstance pc = orders.getPc();
        int currency = shop.policy().buybackCurrency(shop.getNpcId());
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, ownerShopLane(pc.getId()))) {
            long total = 0;
            for (L1ShopSellOrder order : orders.orders()) {
                L1ItemInstance item = pc.getInventory().GetByServerID(order.item().getTargetId());
                int price = shop.assessOwnedUnitPrice(item);
                if (price <= 0 || price != order.item().getAssessedPrice()) return;
                total += (long) price * order.count();
                if (total > CInventory.MAX_AMOUNT || !tx.debit(item, order.count())) return;
            }

            int paidChecks = currency == L1ItemId.ADENA ? (int) (total / CHECK_VALUE) : 0;
            int payout = (int) total - paidChecks * CHECK_VALUE;
            int convertedChecks = 0;
            if (currency == L1ItemId.ADENA && payout > 0) {
                long balance = 0;
                for (L1ItemInstance adena : pc.getInventory().GetUnequippedItemListByClassID(L1ItemId.ADENA))
                    balance += tx.GetPreparedItemAmount(pc, adena);
                if (balance + payout > ADENA_CHANGE_THRESHOLD) {
                    convertedChecks = balance >= ADENA_CHANGE_THRESHOLD ? 19
                            : (int) ((balance + payout - ADENA_CHANGE_THRESHOLD + CHECK_VALUE - 1) / CHECK_VALUE);
                    if (!tx.debit(L1ItemId.ADENA, convertedChecks * CHECK_VALUE))
                        throw new IllegalStateException("Adena conversion balance changed");
                }
            }
            if (paidChecks + convertedChecks > 0) grant(tx, ADENA_CHECK, paidChecks + convertedChecks);
            if (payout > 0) grant(tx, currency, payout);

            int converted = convertedChecks;
            tx.onFailure(() -> SC_SystemMessage.send(pc, SALE_FAILED));
            tx.commit(paidChecks + converted == 0 ? null : () -> {
                try {
                    if (paidChecks > 0) SC_SystemMessage.send(pc, "\\fR판매액 중 1억 단위는 수표 " + paidChecks + "장으로 지급했습니다.");
                    if (converted > 0) SC_SystemMessage.send(pc, "\\fR19억 초과 잔액을 1억 수표 " + converted + "장으로 변환했습니다.");
                } catch (RuntimeException failure) {
                    LOG.log(Level.FINE, "Committed NPC buyback notification failed", failure);
                }
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.FINE, "NPC buyback failed for shop " + shop.getNpcId(), failure);
            SC_SystemMessage.send(pc, SALE_FAILED);
        }
    }

    private static void grant(L1InventoryTransaction tx, int template, int amount) {
        if (tx.grant(template, amount) == null) throw new IllegalStateException("NPC currency or reward grant rejected");
    }
}
