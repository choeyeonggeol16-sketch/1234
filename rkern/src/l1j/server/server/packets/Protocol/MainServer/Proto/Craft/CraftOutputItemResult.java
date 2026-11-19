package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

public class CraftOutputItemResult {
    public final CraftOutputItem outputItem;
    public final boolean isSuccess;
    public CraftOutputItemResult(CraftOutputItem outputItem, boolean isSuccess){
        this.outputItem = outputItem;
        this.isSuccess = isSuccess;
    }

}