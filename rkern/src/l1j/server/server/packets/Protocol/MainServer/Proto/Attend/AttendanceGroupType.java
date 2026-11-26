package l1j.server.server.packets.Protocol.MainServer.Proto.Attend;

import java.util.List;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ATTENDANCE_BONUS_GROUP_INFO;

public enum AttendanceGroupType{
    NORMAL(0),
    PC_CAFE(1);
    private record BonusCatalog(List<AttendanceBonusGroup> days, ProtoPacketWriter.Raw packet) {}

    private final int value;
    private final int _checkSum;
    private volatile BonusCatalog catalog;
    AttendanceGroupType(int val){
        value = val;
        _checkSum = 1 << val;
    }
    public int toInt(){
        return value;
    }
    
    public int toCheckSum(){
        return _checkSum;
    }
    
    public boolean isCheckSum(int val){
        return (val & _checkSum) == _checkSum;
    }
    
    public boolean equals(AttendanceGroupType v){
        return value == v.value;
    }
    
    public int getBonusSize(){
        final BonusCatalog current = catalog;
        return current == null ? 0 : current.days().size();
    }
    
    public void setGroupInfo(SC_ATTENDANCE_BONUS_GROUP_INFO gInfo){
        catalog = new BonusCatalog(List.copyOf(gInfo.get_bonusGroup()), new ProtoPacketWriter.Raw(gInfo.encode()));
    }
    
    public void send(L1PcInstance pc){
        final BonusCatalog current = catalog;
        if (current != null) pc.sendPackets(current.packet(), false);
    }
    
    public AttendanceBonusGroup getBonus(int index) {
        final BonusCatalog current = catalog;
        if (current == null || index < 0 || index >= current.days().size()) return null;
        final AttendanceBonusGroup bonus = current.days().get(index);
        return bonus.get_attendaceIndex() == index + 1 ? bonus : null;
    }
    
    public static AttendanceGroupType fromInt(int i){
        switch(i){
        case 0:
            return NORMAL;
        case 1:
            return PC_CAFE;
        default:
            return null;
        }
    }
}
