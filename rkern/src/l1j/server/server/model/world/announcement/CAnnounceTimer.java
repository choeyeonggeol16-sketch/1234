/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 * 
 */
package l1j.server.server.model.world.announcement;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CServerTimer;

public final class CAnnounceTimer {
    private static final Logger LOG = Logger.getLogger(CAnnounceTimer.class.getName());
    private static final String FILE = "data/Announcecycle.txt";
    private static final long FIRST_DELAY_MS = TimeUnit.MINUTES.toMillis(3L);
    private static final CAnnounceTimer INSTANCE = new CAnnounceTimer();

    private final Object reloadLock = new Object();
    private List<String> messages = List.of();
    private int nextMessage;
    private boolean stopped;
    private CServerTimer timer;

    private CAnnounceTimer() {}

    public static CAnnounceTimer getInstance() {
        return INSTANCE;
    }

    public boolean reload() {
        synchronized (reloadLock) {
            final List<String> loaded = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(FILE))) {
                for (String line; (line = reader.readLine()) != null;) {
                    if (!line.isEmpty()) loaded.add(line);
                }
            } catch (IOException failure) {
                LOG.log(Level.WARNING, "Could not load " + FILE, failure);
                return false;
            }

            synchronized (this) {
                if (stopped) return false;
                messages = loaded;
                if (nextMessage >= loaded.size()) nextMessage = 0;
                if (timer != null || loaded.isEmpty() || !Config.Use_Show_Announcecycle) return true;

                final long intervalMs = TimeUnit.MINUTES.toMillis(Config.Show_Announcecycle_Time);
                if (intervalMs <= 0L) {
                    LOG.warning("Show_Announcecycle_Time must be positive");
                    return false;
                }
                timer = CServerTimer.fixedRate(this::broadcast, FIRST_DELAY_MS, intervalMs);
                if (timer == null) LOG.warning("Announcement timer registration rejected");
                return timer != null;
            }
        }
    }

    public synchronized void stop() {
        stopped = true;
        if (timer != null) timer.cancel();
        timer = null;
        messages = List.of();
        nextMessage = 0;
    }

    private void broadcast() {
        final String message;
        synchronized (this) {
            if (stopped || messages.isEmpty()) return;
            message = messages.get(nextMessage);
            if (++nextMessage == messages.size()) nextMessage = 0;
        }
        try {
            CSharedBroadcast.sendWrittenRecipientsBestEffort(
                    L1World.getInstance().getAllPlayers(), SC_SystemMessage.nativeWriter(message));
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Scheduled announcement failed", failure);
        }
    }
}
