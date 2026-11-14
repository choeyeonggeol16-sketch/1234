package l1j.server.server.packets.Protocol.Packet.Attend;

import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.Packet.SC_ATTENDANCE_BONUS_GROUP_INFO;

public enum AttendanceGroupType{
    NORMAL(0),
    PC_CAFE(1);
    private int value;
    private SC_ATTENDANCE_BONUS_GROUP_INFO _gInfo;
    private ProtoPacketWriter.Raw _gInfoPacket;
    private int _bonusSize;
    private int _checkSum;
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
        return _bonusSize;
    }
    
    public void setGroupInfo(SC_ATTENDANCE_BONUS_GROUP_INFO gInfo){
        ProtoPacketWriter.Raw tmpPacket = _gInfoPacket;
        SC_ATTENDANCE_BONUS_GROUP_INFO tmpInfo = _gInfo;
        if(gInfo.get_bonusGroup() != null)
            _bonusSize = gInfo.get_bonusGroup().size();
      //  gInfo.set_groupType(this);
        _gInfo = gInfo;
        _gInfoPacket = new ProtoPacketWriter.Raw(gInfo.encode());   // 1회 인코딩 후 Raw 공유
        if(tmpPacket != null){
            tmpPacket = null;
        }
        
        if(tmpInfo != null){
            tmpInfo = null;
        }
    }
    
    public void send(L1PcInstance pc){
        if(_gInfoPacket != null)
            pc.sendPackets(_gInfoPacket, false);
    }
    
    public void doReward(L1PcInstance pc, int index){
        AttendanceBonusGroup bGroup = _gInfo.get_bonusGroup().get(index);
        L1Inventory inv = pc.getInventory();
        for(AttendanceBonusInfo bInfo : bGroup.get_bonuses()){
            if(inv.checkAddItem(bInfo.get_itemId(), bInfo.get_itemCount()) == L1Inventory.OK)
                inv.storeItem(bInfo.get_itemId(), bInfo.get_itemCount());
        }
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