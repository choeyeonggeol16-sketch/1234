package l1j.server.server.model.world.siege;

import java.util.Calendar;

import l1j.server.Config;

/** A published siege timetable, including the original end limit. */
final class CastleSiegeSchedule {
    private final Calendar start;
    final long startMillis;
    final long readyMillis;
    final long endMillis;
    final long limitMillis;

    CastleSiegeSchedule(Calendar start) {
        this.start = (Calendar) start.clone();
        startMillis = this.start.getTimeInMillis();
        Calendar boundary = copyStart();
        boundary.add(Calendar.MINUTE, -5);
        readyMillis = boundary.getTimeInMillis();
        boundary.setTimeInMillis(startMillis);
        boundary.add(Config.ALT_WAR_TIME_UNIT, Config.ALT_WAR_TIME);
        endMillis = limitMillis = boundary.getTimeInMillis();
    }

    private CastleSiegeSchedule(CastleSiegeSchedule previous, long defenseEndMillis) {
        start = previous.start;
        startMillis = previous.startMillis;
        readyMillis = previous.readyMillis;
        limitMillis = previous.limitMillis;
        endMillis = Math.min(defenseEndMillis, limitMillis);
    }

    Calendar copyStart() {
        return (Calendar) start.clone();
    }

    CastleSiegeSchedule withDefenseTime(Calendar now, int minutes) {
        now.add(Calendar.MINUTE, minutes);
        return new CastleSiegeSchedule(this, now.getTimeInMillis());
    }

    CastleSiegeSchedule nextOccurrence() {
        Calendar next = copyStart();
        next.add(Config.ALT_WAR_INTERVAL_UNIT, Config.ALT_WAR_INTERVAL);
        return new CastleSiegeSchedule(next);
    }
}
