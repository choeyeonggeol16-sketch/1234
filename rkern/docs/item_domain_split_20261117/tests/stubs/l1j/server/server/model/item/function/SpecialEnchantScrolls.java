package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class SpecialEnchantScrolls {
public static void 무기축복(L1PcInstance pc,L1ItemInstance input,L1ItemInstance target){L1PcInstance.events.append("ENCH:0:").append(input.getItemId()).append(':').append(target.getItemId()).append('|');}
public static void 무기축복100(L1PcInstance pc,L1ItemInstance input,L1ItemInstance target){L1PcInstance.events.append("ENCH:1:").append(input.getItemId()).append(':').append(target.getItemId()).append('|');}
public static void 악세인챈(L1PcInstance pc,L1ItemInstance input,L1ItemInstance target){L1PcInstance.events.append("ENCH:2:").append(input.getItemId()).append(':').append(target.getItemId()).append('|');}
public static void 악세인챈100(L1PcInstance pc,L1ItemInstance input,L1ItemInstance target){L1PcInstance.events.append("ENCH:3:").append(input.getItemId()).append(':').append(target.getItemId()).append('|');}
public static void 방어구축복(L1PcInstance pc,L1ItemInstance input,L1ItemInstance target){L1PcInstance.events.append("ENCH:4:").append(input.getItemId()).append(':').append(target.getItemId()).append('|');}
public static void 방어구축복100(L1PcInstance pc,L1ItemInstance input,L1ItemInstance target){L1PcInstance.events.append("ENCH:5:").append(input.getItemId()).append(':').append(target.getItemId()).append('|');}
public static void 옵션부여(L1PcInstance pc,L1ItemInstance input,L1ItemInstance target){L1PcInstance.events.append("ENCH:6:").append(input.getItemId()).append(':').append(target.getItemId()).append('|');}
public static void 품질부여주문서(L1PcInstance pc,L1ItemInstance input,L1ItemInstance target){L1PcInstance.events.append("ENCH:7:").append(input.getItemId()).append(':').append(target.getItemId()).append('|');}
public static void 집행진화주문서(L1PcInstance pc,L1ItemInstance input,L1ItemInstance target){L1PcInstance.events.append("ENCH:8:").append(input.getItemId()).append(':').append(target.getItemId()).append('|');}
}
