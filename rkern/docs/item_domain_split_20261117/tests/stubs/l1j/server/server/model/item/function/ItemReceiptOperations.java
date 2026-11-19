package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
final class ItemReceiptOperations {static void UseDurableConsume(L1PcInstance pc,L1ItemInstance item,Runnable act){
 L1PcInstance.events.append("DEBIT:").append(item.getItemId()).append('|');act.run();L1PcInstance.events.append("DONE|");}}
