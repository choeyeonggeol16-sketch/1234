package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.datatables.IncreaseEinhasadMap;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * 아인하사드 게이지/감소효율 와이어
 */
public final class SC_REST_EXP_INFO_NOTI extends ProtoPacket {

    private static final int[] LEVEL_TO_BONUS_EFFECT = {
        5, 6, 7, 8, 9, 10, 12, 14, 16, 18, 20, 23, 26, 29, 32, 35,
        35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35
    };

    public static double calcDecreaseCharacterEinhasad(L1PcInstance pc, double descrease) {
        return calcDecreaseCharacterEinhasad(pc, descrease, pc.getAccount().getBlessOfAin());
    }

    public static double calcDecreaseCharacterEinhasad(L1PcInstance pc, double descrease, int blessingBefore) {
        double dec = Math.max(0.0, descrease);
        double effect = Math.max(0.0, Math.min(1.0, calcEinhasadEffectToDouble(pc)));
        if (effect > 0D) {
            dec -= (dec * effect);
        }
        dec = IncreaseEinhasadMap.getInstance().increaseEinhasadValue(pc.getMapId(), dec);

        if (blessingBefore >= 2000001 && blessingBefore <= 18000000) {
            dec *= 0.6;
        } else if (blessingBefore >= 34000000) {
            dec *= 0.8;
        }
        return Math.max(0.0, dec);
    }

    public static double calcEinhasadEffectToDouble(L1PcInstance pc) {
        double effect = calcEinhasadEffect(pc);
        return effect <= 0 ? effect : effect * 0.01D;
    }

    public static int calcEinhasadEffect(L1PcInstance pc) {
        int effect = 0;
        int level = pc.getLevel();
        if (level >= 80) {
            effect = LEVEL_TO_BONUS_EFFECT[Math.min(level - 80, LEVEL_TO_BONUS_EFFECT.length - 1)];
        }
        if (pc.getEinhasadBlessper() >= 0) {
            effect += pc.getEinhasadBlessper();
        }
        if (pc.getResistance().getcalcAinPer() != 0) {
            effect += pc.getResistance().getcalcAinPer();
        }
        return effect;
    }

    public static int get_ein_level(int ein) {
        if (ein >= 3401) return 4;
        if (ein >= 1801) return 3;
        if (ein >= 201)  return 2;
        if (ein > 0)     return 1;
        return 0;
    }

    public static int displayedGauge(int internalUnits) {
        return internalUnits <= 0 ? 0 : (int) (((long) internalUnits + 9999L) / 10000L);
    }

    // ── 패킷 본체 ──

    private final int _restGauge;
    private final int _defaultRation;
    private final int _extraExpRatio;
    private final int _reduceEfficiency;
    private final boolean _blessOfBloodPledge;

    private SC_REST_EXP_INFO_NOTI(int restGauge, int defaultRation, int extraExpRatio,
            int reduceEfficiency, boolean blessOfBloodPledge) {
        _restGauge = restGauge;
        _defaultRation = defaultRation;
        _extraExpRatio = extraExpRatio;
        _reduceEfficiency = reduceEfficiency;
        _blessOfBloodPledge = blessOfBloodPledge;
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_REST_EXP_INFO_NOTI;
    }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _restGauge)
         .int32(2, _defaultRation)
         .int32(3, _extraExpRatio)
         .int32(4, _reduceEfficiency)
         .bool(5, _blessOfBloodPledge);
    }

    public static void send(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null || pc.noPlayerCK)
            return;
        if (pc.getAccount().getGrangKinAngerTime() != 0)
            return;

        int hasad = displayedGauge(pc.getAccount().getBlessOfAin());

        int ration = 0;
        if (pc.hasSkillEffect(L1SkillId.DRAGON_TOPAZ))
            ration += 81;
        if (pc.PC방_버프)
            ration += 40;

        int extra = 0;
        switch (get_ein_level(hasad)) {
            case 4: extra = 60; ration += 100; break;
            case 3: extra = 50; ration += 80;  break;
            case 2: extra = 40; ration += 60;  break;
            case 1: ration += 100;             break;
        }

        L1Clan c = pc.getClan();
        boolean bless = pc.getClanBuffMap() != 0 && c != null && c.getEinhasadBlessBuff() != 0;

        SC_REST_EXP_INFO_NOTI.sendConstructed(pc, hasad, ration * 100, extra, calcEinhasadEffect(pc), bless);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _restGauge, int _defaultRation, int _extraExpRatio, int _reduceEfficiency, boolean _blessOfBloodPledge) {
        w.int32(1, _restGauge).int32(2, _defaultRation).int32(3, _extraExpRatio).int32(4, _reduceEfficiency).bool(5, _blessOfBloodPledge);
    }

    public static boolean sendFields(
            L1PcInstance recipient, int _restGauge, int _defaultRation, int _extraExpRatio, int _reduceEfficiency,
            boolean _blessOfBloodPledge) {
        return sendFields(
                recipient == null ? null : recipient.getNetConnection(), _restGauge, _defaultRation, _extraExpRatio,
                _reduceEfficiency, _blessOfBloodPledge);
    }

    public static boolean sendFields(
            CIOClient recipient, int _restGauge, int _defaultRation, int _extraExpRatio, int _reduceEfficiency,
            boolean _blessOfBloodPledge) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_REST_EXP_INFO_NOTI, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _restGauge, _defaultRation, _extraExpRatio, _reduceEfficiency, _blessOfBloodPledge);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _restGauge, int _defaultRation, int _extraExpRatio, int _reduceEfficiency, boolean _blessOfBloodPledge) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _restGauge, _defaultRation, _extraExpRatio, _reduceEfficiency, _blessOfBloodPledge);
        return writer.finishWithTrailer(ProtoMsg.SC_REST_EXP_INFO_NOTI);
    }

    private static boolean sendCtor(
            CIOClient protoRecipient, int restGauge, int defaultRation, int extraExpRatio, int reduceEfficiency,
            boolean blessOfBloodPledge) {
        int proto56_restGauge = 0;
        int proto56_defaultRation = 0;
        int proto56_extraExpRatio = 0;
        int proto56_reduceEfficiency = 0;
        boolean proto56_blessOfBloodPledge = false;

            proto56_restGauge = restGauge;
            proto56_defaultRation = defaultRation;
            proto56_extraExpRatio = extraExpRatio;
            proto56_reduceEfficiency = reduceEfficiency;
            proto56_blessOfBloodPledge = blessOfBloodPledge;

        return sendFields(
                protoRecipient, proto56_restGauge, proto56_defaultRation, proto56_extraExpRatio, proto56_reduceEfficiency,
                proto56_blessOfBloodPledge);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int restGauge, int defaultRation, int extraExpRatio, int reduceEfficiency,
            boolean blessOfBloodPledge) { return sendCtor(protoRecipient, restGauge, defaultRation, extraExpRatio, reduceEfficiency, blessOfBloodPledge); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int restGauge, int defaultRation, int extraExpRatio, int reduceEfficiency,
            boolean blessOfBloodPledge) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), restGauge, defaultRation, extraExpRatio, reduceEfficiency, blessOfBloodPledge); }

}
