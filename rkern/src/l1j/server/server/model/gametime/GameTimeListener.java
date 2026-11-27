package l1j.server.server.model.gametime;

/** Boundary notifications on the world clock. Blocking work belongs on its existing worker. */
public interface GameTimeListener {
    default void onMonthChanged(CDateTime time) {}
    default void onDayChanged(CDateTime time) {}
    default void onHourChanged(CDateTime time) {}
    default void onMinuteChanged(CDateTime time) {}
    default void onSecondChanged(CDateTime time) {}
}
