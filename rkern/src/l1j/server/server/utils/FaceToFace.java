package l1j.server.server.utils;

import java.util.List;

import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public class FaceToFace {

    private FaceToFace() {
    }

    public static L1PcInstance faceToFace(L1PcInstance pc) {
        int pcX = pc.getX();
        int pcY = pc.getY();
        int pcHeading = pc.getHeading();
        List<L1PcInstance> players = CWorld.getInstance().getClusterGrid().queryNearbyPlayers(pc, 1);

        if (players.size() == 0) {
            SC_ServerMessage.send(pc, 93); // \f1 거기에는 아무도 없습니다.
            return null;
        }
        for (L1PcInstance target : players) {
            int targetX = target.getX();
            int targetY = target.getY();
            int targetHeading = target.getHeading();
            if (pcHeading == 0 && pcX == targetX && pcY == (targetY + 1)) {
                if (targetHeading == 4) {
                    return target;
                } else {
                    SC_ServerMessage.send(pc, 91, target.getName()); // \f1%0이 당신을 보고 있지 않습니다.
                    return null;
                }
            } else if (pcHeading == 1 && pcX == (targetX - 1)
                    && pcY == (targetY + 1)) {
                if (targetHeading == 5) {
                    return target;
                } else {
                    SC_ServerMessage.send(pc, 91, target.getName()); // \f1%0이 당신을 보고 있지 않습니다.
                    return null;
                }
            } else if (pcHeading == 2 && pcX == (targetX - 1) && pcY == targetY) {
                if (targetHeading == 6) {
                    return target;
                } else {
                    SC_ServerMessage.send(pc, 91, target.getName()); // \f1%0이 당신을 보고 있지 않습니다.
                    return null;
                }
            } else if (pcHeading == 3 && pcX == (targetX - 1)
                    && pcY == (targetY - 1)) {
                if (targetHeading == 7) {
                    return target;
                } else {
                    SC_ServerMessage.send(pc, 91, target.getName()); // \f1%0이 당신을 보고 있지 않습니다.
                    return null;
                }
            } else if (pcHeading == 4 && pcX == targetX && pcY == (targetY - 1)) {
                if (targetHeading == 0) {
                    return target;
                } else {
                    SC_ServerMessage.send(pc, 91, target.getName()); // \f1%0이 당신을 보고 있지 않습니다.
                    return null;
                }
            } else if (pcHeading == 5 && pcX == (targetX + 1)
                    && pcY == (targetY - 1)) {
                if (targetHeading == 1) {
                    return target;
                } else {
                    SC_ServerMessage.send(pc, 91, target.getName()); // \f1%0이 당신을 보고 있지 않습니다.
                    return null;
                }
            } else if (pcHeading == 6 && pcX == (targetX + 1) && pcY == targetY) {
                if (targetHeading == 2) {
                    return target;
                } else {
                    SC_ServerMessage.send(pc, 91, target.getName()); // \f1%0이 당신을 보고 있지 않습니다.
                    return null;
                }
            } else if (pcHeading == 7 && pcX == (targetX + 1)
                    && pcY == (targetY + 1)) {
                if (targetHeading == 3) {
                    return target;
                } else {
                    SC_ServerMessage.send(pc, 91, target.getName()); // \f1%0이 당신을 보고 있지 않습니다.
                    return null;
                }
            }
        }
        SC_ServerMessage.send(pc, 93); // \f1 거기에는 아무도 없습니다.
        return null;
    }
}