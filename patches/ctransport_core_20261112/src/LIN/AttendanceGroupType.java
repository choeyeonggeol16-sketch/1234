package LIN;

import LIN.SC_ATTENDANCE_BONUS_GROUP_INFO;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;


public enum AttendanceGroupType{
    NORMAL(0),
    PC_CAFE(1);
    private int value;
    private LIN.SC_ATTENDANCE_BONUS_GROUP_INFO _gInfo;
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
    
    public boolean equals(LIN.AttendanceGroupType v){
        return value == v.value;
    }
    
    public int getBonusSize(){
        return _bonusSize;
    }
    
    public void setGroupInfo(LIN.SC_ATTENDANCE_BONUS_GROUP_INFO gInfo){
        ProtoPacketWriter.Raw tmpPacket = _gInfoPacket;
        LIN.SC_ATTENDANCE_BONUS_GROUP_INFO tmpInfo = _gInfo;
        if(gInfo.bonusGroup() != null)
            _bonusSize = gInfo.bonusGroup().size();
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
        LIN.AttendanceBonusGroup bGroup = _gInfo.bonusGroup().get(index);
        L1Inventory inv = pc.getInventory();
        for(LIN.AttendanceBonusInfo bInfo : bGroup.bonuses()){
            if(inv.checkAddItem(bInfo.itemId(), bInfo.itemCount()) == L1Inventory.OK)
                inv.storeItem(bInfo.itemId(), bInfo.itemCount());
        }
    }
    
    public static LIN.AttendanceGroupType fromInt(int i){
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