package l1j.server.server.model.network.core.diagnostics;

/** View of the dedicated native CPerfMon thread15. Counts are sampled,
 * not a transaction across the game world and transport. OS fields with
 * unavailable flags must not be interpreted as measurements of zero. */
public final class CPerfMon {
    private CPerfMon(){}
    public record Snapshot(long samples,long sampledAtMillis,int owner,
            long processCpuPermille,long workingSetBytes,long committedBytes,
            long systemMemoryLoadPercent,long totalPhysicalBytes,long availablePhysicalBytes,
            long connections,long pendingTasks,long allSockets,long loggedInSockets,
            long enteringAccounts,long completedCallbacks,long availabilityFlags){}
}
