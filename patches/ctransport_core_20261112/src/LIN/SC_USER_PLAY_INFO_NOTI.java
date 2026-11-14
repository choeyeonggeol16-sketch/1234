package LIN;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import l1j.server.MJDungeonTimer.DungeonTimeInformation;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.MJDungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;



/**
 * 던전 잔여시간 UI 와이어
 * 
 */
public final class SC_USER_PLAY_INFO_NOTI extends ProtoPacket {

    private static final class MAP_TIME_LIMIT_INFO {
        final int serial;
        final byte[] description;
        final int remained;
        final int stay;

        MAP_TIME_LIMIT_INFO(int serial, byte[] description, int remained, int stay) {
            this.serial = serial;
            this.description = description;
            this.remained = remained;
            this.stay = stay;
        }
    }

    private final List<LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO> _infos;

    private SC_USER_PLAY_INFO_NOTI(List<LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO> infos) {
        _infos = infos;
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_USER_PLAY_INFO_NOTI;
    }

    @Override
    protected void body(ProtoWriter w) {
        for (LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO info : _infos) {
            w.msg(1)                     // repeated 요소 하나 열기
             .uint32(1, info.serial)
             .bytes(2, info.description) // 이미 MS949 — String 아님
             .uint32(3, info.remained)
             .uint32(4, info.stay)
             .end();                     // 길이 백패치
        }
    }

    public static void send(L1PcInstance pc) {
        ArrayList<LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO> list = new ArrayList<>();
        for (DungeonTimeInformation dtInfo : DungeonTimeInformationLoader.getInstance().get_enumerator()) {
            if (dtInfo == null || !dtInfo.get_is_presentation()) {
                continue;
            }
            byte[] desc;
            try {
                desc = dtInfo.get_description().getBytes("MS949");
            } catch (UnsupportedEncodingException e) {
                continue;
            }

            DungeonTimeProgress<?> progress = pc.get_progress(dtInfo);
            int remained = (progress == null) ? dtInfo.get_amount_seconds() : progress.get_remain_seconds();

            list.add(new LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO(dtInfo.get_serial_id(), desc, remained, dtInfo.get_amount_seconds()));
        }
        LIN.SC_USER_PLAY_INFO_NOTI.sendConstructed(pc, list);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, List<LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO> _infos) {
        for (LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO info : _infos) {
                w.msg(1).uint32(1, info.serial).bytes(2, info.description).uint32(3, info.remained).uint32(4, info.stay).end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, List<LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO> _infos) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _infos);
    }

    public static boolean sendFields(CIOClient recipient, List<LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO> _infos) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_USER_PLAY_INFO_NOTI, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _infos);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(List<LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO> _infos) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _infos);
        return writer.finishWithTrailer(ProtoMsg.SC_USER_PLAY_INFO_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient, List<LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO> infos) {
        List<LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO> proto101_infos = null;

            proto101_infos = infos;

        return sendFields(protoRecipient, proto101_infos);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, List<LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO> infos) { return sendCtor(protoRecipient, infos); }

    public static boolean sendConstructed(L1PcInstance protoRecipient, List<LIN.SC_USER_PLAY_INFO_NOTI.MAP_TIME_LIMIT_INFO> infos) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), infos); }

}
