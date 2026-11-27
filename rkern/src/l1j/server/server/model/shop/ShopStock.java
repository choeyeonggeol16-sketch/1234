package l1j.server.server.model.shop;

import java.util.Arrays;
import java.util.List;

import l1j.server.server.templates.L1ShopItem;

/** Catalog row positions stay stable while the DB worker settles reserved quantities. */
final class ShopStock {
    private final List<L1ShopItem> offers;
    private final int[] reserved;

    ShopStock(List<L1ShopItem> offers) {
        this.offers = offers;
        reserved = new int[offers.size()];
    }

    Reservation reserve(List<L1ShopBuyOrder> orders) {
        int[] rows = new int[orders.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = orders.get(i).getOrderNumber();
        Arrays.sort(rows);
        int[] quantities = new int[rows.length];
        for (L1ShopBuyOrder order : orders) {
            int row = order.getOrderNumber();
            if (row < 0 || row >= offers.size() || offers.get(row) != order.getItem()) return null;
            int index = Arrays.binarySearch(rows, row);
            quantities[index] = Math.addExact(quantities[index], order.getCount());
        }
        synchronized (this) {
            for (int i = 0; i < rows.length; i++) {
                if (quantities[i] > offers.get(rows[i]).getCount() - reserved[rows[i]]) return null;
            }
            for (int i = 0; i < rows.length; i++) reserved[rows[i]] += quantities[i];
        }
        return new Reservation(rows, quantities);
    }

    final class Reservation {
        private final int[] rows;
        private final int[] quantities;
        private boolean finished;

        private Reservation(int[] rows, int[] quantities) {
            this.rows = rows;
            this.quantities = quantities;
        }

        void consume() { finish(true); }
        void release() { finish(false); }

        private void finish(boolean committed) {
            synchronized (ShopStock.this) {
                if (finished) return;
                finished = true;
                for (int i = 0; i < rows.length; i++) {
                    int amount = quantities[i];
                    if (amount == 0) continue;
                    int row = rows[i];
                    if (committed) {
                        L1ShopItem offer = offers.get(row);
                        offer.setCount(offer.getCount() - amount);
                    }
                    reserved[row] -= amount;
                }
            }
        }
    }
}
