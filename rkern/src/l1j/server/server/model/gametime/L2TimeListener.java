package l1j.server.server.model.gametime;

/** Boundary notifications on the world clock. Blocking work belongs on its existing worker. */
public interface L2TimeListener {
    default void onMonthChanged(L2DateTime time) {}
    default void onDayChanged(L2DateTime time) {}
    default void onHourChanged(L2DateTime time) {}
    default void onMinuteChanged(L2DateTime time) {}
    default void onSecondChanged(L2DateTime time) {}
}
