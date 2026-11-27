package l1j.server.server.model.world.instance.raid.loader;

import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.Account;
import l1j.server.server.storage.CacheD;

public final class RaidUserTimeStore {
    private static final Logger LOG = Logger.getLogger(RaidUserTimeStore.class.getName());

    private RaidUserTimeStore() {}

    public static boolean update(Account account, long untilMillis) {
        if (account == null) return false;
        synchronized (account) {
            account.setDragonRaid(new Timestamp(untilMillis));
            try {
                CacheD.getInstance().saveRaidBuff(account.getName(), account.getAccountId(), untilMillis);
                return true;
            } catch (RuntimeException failure) {
                LOG.log(Level.SEVERE, "Raid cooldown save admission failed: " + account.getName(), failure);
                return false;
            }
        }
    }
}
