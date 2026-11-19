package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import java.nio.charset.Charset;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.model.Instance.L1PcInstance;

public final class SC_RaceBoard {
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_RaceBoard() {}

    public static boolean send(L1PcInstance pc, int number, boolean isViewedGm) {
        return pc != null && send(pc.getNetConnection(), number, isViewedGm);
    }

    public static boolean send(CIOClient client, int number, boolean isViewedGm) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final BugRaceController controller = BugRaceController.getInstance();
        final String[] columns = new String[15];
        int wireSize = 17;
        for (int i = 0; i < 5; i++) {
            String name = "NoData";
            String condition = "대기중";
            String winRateStr = "0.0%";
            double rate = 0.0;
            double ration = 0.0;
            int moveSpeed = 0;
            try {
                if (controller != null) {
                    if (controller._littleBugBear != null && controller._littleBugBear[i] != null
                            && controller._littleBugBear[i].getName() != null)
                        name = controller._littleBugBear[i].getName();
                    if (controller._bugCondition != null && controller._bugCondition[i] != null)
                        condition = controller._bugCondition[i];
                    if (controller._winRate != null) rate = controller._winRate[i];
                    if (controller._winViewRate != null)
                        winRateStr = Double.toString(controller._winViewRate[i]) + "%";
                    double[] rations = null;
                    try { rations = controller.calc_rations(); } catch (Exception ignored) {}
                    if (rations != null && rations.length > i) ration = rations[i];
                    moveSpeed = controller.getSpeed(i);
                }
            } catch (Exception ignored) {}
            final int index = i * 3;
            if (isViewedGm) {
                columns[index] = String.format("%s/%s/%.2f%%/%.2f배/%d", name, condition, rate, ration, moveSpeed);
                columns[index + 1] = "";
                columns[index + 2] = "";
            } else {
                columns[index] = name;
                columns[index + 1] = condition;
                columns[index + 2] = winRateStr;
            }
            for (int j = index; j < index + 3; j++)
                wireSize += columns[j].getBytes(MS949).length + 1;
        }
        final CIOFrame frame = connection.beginDirectFrame(wireSize);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT);
            buffer.writeD(number);
            buffer.writeS("maeno4");
            buffer.writeC(0);
            buffer.writeH(15);
            for (String column : columns) buffer.writeS(column);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
