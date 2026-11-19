package l1j.server.server.model.character.effect;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.utils.eKind;

/**
 * Stateless clan buff effects; existing callers keep their public PC methods. 
 */
public final class PcClanBuffEffects {
    
    private PcClanBuffEffects() {
    }
    
    public enum Kind {
        혈맹버프AC ,
        혈맹버프마방 ,
        혈맹버프PVP추가 ,
        혈맹버프PVP감소 ,
        혈맹버프HP증가 ,
        혈맹버프올내성 ,
        혈맹버프올적중 ,
        혈맹버프올스텟 ;
    }
    
    public static void apply(L1PcInstance pc, Kind kind) {
        switch (kind) {
        case 혈맹버프AC -> {
            pc.getAC().addAc(-5);
            SC_OwnCharAttrDef.send(pc);
        }
        case 혈맹버프마방 -> {
            pc.getResistance().addMr(10);
            SC_SPMR.send(pc);
        }
        case 혈맹버프PVP추가 -> {
            pc.getResistance().addPVPweaponTotalDamage(3);
        }
        case 혈맹버프PVP감소 -> {
            pc.getResistance().addcalcPcDefense(3);
        }
        case 혈맹버프HP증가 -> {
            pc.addMaxHp(150);
            SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
        }
        case 혈맹버프올내성 -> {
            pc.getResistance().addSpecialResistance(eKind.ALL, 3);
        }
        case 혈맹버프올적중 -> {
            pc.getResistance().addSpecialPierce(eKind.ALL, 3);
        }
        case 혈맹버프올스텟 -> {
            pc.getAbility().addAddedStr((byte)3);
            pc.getAbility().addAddedDex((byte)3);
            pc.getAbility().addAddedCon((byte)3);
            pc.getAbility().addAddedInt((byte)3);
            pc.getAbility().addAddedWis((byte)3);
            pc.getAbility().addAddedCha((byte)3);
            SC_OwnCharStatus2.send(pc);
        }
        }
    }
}
