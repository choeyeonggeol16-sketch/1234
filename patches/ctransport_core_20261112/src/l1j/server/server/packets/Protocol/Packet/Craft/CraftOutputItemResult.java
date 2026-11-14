package l1j.server.server.packets.Protocol.Packet.Craft;

public class CraftOutputItemResult {
    public final LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem outputItem;
    public final boolean isSuccess;
    public CraftOutputItemResult(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem outputItem, boolean isSuccess){
        this.outputItem = outputItem;
        this.isSuccess = isSuccess;
    }

}