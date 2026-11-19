package l1j.server.server.model.network.core.diagnostics;

import l1j.server.server.model.network.core.transport.CIOServer;
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
    public static Snapshot Read(CIOServer server){
        long[] v=server.performanceDiagnostics();
        return new Snapshot(v[0],v[1],(int)v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10],v[11],v[12],v[13],v[14],v[15]);
    }
}
