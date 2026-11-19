package l1j.server.server.model.item.function;
/** Existing allocation-free primitive maps shared by item bonus domains. */
final class ItemBonusPrimitiveMaps {
    private ItemBonusPrimitiveMaps() {}
static final class IntIntMap {
        private int[] keys;
        private int[] vals;
        private boolean[] used;
        private int mask;
        private int size;

        IntIntMap(int expected) {
            int cap = 1;
            while (cap < (expected * 2)) cap <<= 1;
            keys = new int[cap];
            vals = new int[cap];
            used = new boolean[cap];
            mask = cap - 1;
        }

        void put(int k, int v) {
            if (size * 2 >= keys.length) rehash();
            int idx = mix(k) & mask;
            while (used[idx]) {
                if (keys[idx] == k) { vals[idx] = v; return; }
                idx = (idx + 1) & mask;
            }
            used[idx] = true;
            keys[idx] = k;
            vals[idx] = v;
            size++;
        }

        int getOrZero(int k) {
            int idx = mix(k) & mask;
            while (used[idx]) {
                if (keys[idx] == k) return vals[idx];
                idx = (idx + 1) & mask;
            }
            return 0;
        }

        private void rehash() {
            int[] oldK = keys;
            int[] oldV = vals;
            boolean[] oldU = used;

            int cap = oldK.length << 1;
            keys = new int[cap];
            vals = new int[cap];
            used = new boolean[cap];
            mask = cap - 1;
            size = 0;

            for (int i = 0; i < oldK.length; i++) {
                if (!oldU[i]) continue;
                put(oldK[i], oldV[i]);
            }
        }

        private static int mix(int x) {
            x ^= (x >>> 16);
            x *= 0x7feb352d;
            x ^= (x >>> 15);
            x *= 0x846ca68b;
            x ^= (x >>> 16);
            return x;
        }
    }

static final class IntDoubleMap {
		private int[] keys;
		private double[] vals;
		private boolean[] used;
		private int mask;
		private int size;

		IntDoubleMap(int expected) {
			int cap = 1;
			while (cap < (expected * 2)) cap <<= 1;
			keys = new int[cap];
			vals = new double[cap];
			used = new boolean[cap];
			mask = cap - 1;
		}

		void put(int k, double v) {
			if (size * 2 >= keys.length) rehash();
			int idx = mix(k) & mask;
			while (used[idx]) {
				if (keys[idx] == k) { vals[idx] = v; return; }
				idx = (idx + 1) & mask;
			}
			used[idx] = true;
			keys[idx] = k;
			vals[idx] = v;
			size++;
		}

		double getOrZero(int k) {
			int idx = mix(k) & mask;
			while (used[idx]) {
				if (keys[idx] == k) return vals[idx];
				idx = (idx + 1) & mask;
			}
			return 0.0;
		}

		private void rehash() {
			int[] oldK = keys;
			double[] oldV = vals;
			boolean[] oldU = used;

			int cap = oldK.length << 1;
			keys = new int[cap];
			vals = new double[cap];
			used = new boolean[cap];
			mask = cap - 1;
			size = 0;

			for (int i = 0; i < oldK.length; i++) {
				if (!oldU[i]) continue;
				put(oldK[i], oldV[i]);
			}
		}

		private static int mix(int x) {
			x ^= (x >>> 16);
			x *= 0x7feb352d;
			x ^= (x >>> 15);
			x *= 0x846ca68b;
			x ^= (x >>> 16);
			return x;
		}
	}
}
