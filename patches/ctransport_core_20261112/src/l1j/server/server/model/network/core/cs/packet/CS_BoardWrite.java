package l1j.server.server.model.network.core.cs.packet;

import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1BoardInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1BoardPost;

public final class CS_BoardWrite {

    private static final Logger LOG =
            Logger.getLogger(
                    CS_BoardWrite.class.getName());

    private CS_BoardWrite() {
    }

    public static void handle(
            CIOClient client,
            int id,
            String title,
            String content) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        final L1Object target =
                L1World
                        .getInstance()
                        .findObject(id);

        if (target == null) {

            LOG.warning(
                    "Invalid NPC ID: " + id);

            System.out.println(
                    "Invalid NPC ID: " + id);

            return;
        }

        /*
         * 원본 순서 그대로.
         *
         * target이 board인지 확인하기 전에
         * title 검증부터 한다.
         */
        if (title == null
                || title.length() > 16) {

            SC_SystemMessage.send(pc, "게시판 제목 글자수가 초과하였습니다.");

            return;
        }

        if (!(target instanceof L1BoardInstance)) {
            return;
        }

        final L1BoardInstance board =
                (L1BoardInstance) target;

        final int npcId =
                board.getNpcId();

        /*
         * 기존 board != null 검사는
         * instanceof를 통과했으므로 필요 없음.
         */
        if (pc.getLevel() < Config.BoardLevel
                && npcId != 900006
                && npcId != 80006
                && npcId != 500002) {

            SC_SystemMessage.send(pc, "레벨 "
                                    + Config.BoardLevel
                                    + " 아니시거나, GM게시판 입니다.");

            return;
        }

        switch (npcId) {

        /*
         * 드래곤 키 게시판
         */
        case 900006:
            SC_SystemMessage.send(pc, "현재 사용할 수 없는 게시판입니다.");
            return;

        /*
         * GM 서버 정보 게시판
         */
        case 4200015:

            if (pc.getAccessLevel()
                    == Config.GMCODE) {

                L1BoardPost.createGM(
                        pc.getName(),
                        title,
                        content);

            } else {

                SC_SystemMessage.send(pc, "해당 게시판은 운영자 전용 게시판 입니다.");
            }

            return;

        case 45000178:
        case 4747470:

            if (pc.getAccessLevel() == 0
                    || pc.getAccessLevel()
                            == Config.GMCODE) {

                SC_SystemMessage.send(pc, "\\fY명령어를 이용하여 주시기 바랍니다.");

                return;
            }

            /*
             * 원본은 여기서 break만 하고
             * switch 이후 별도 로직이 없으므로 종료와 동일.
             */
            return;

        /*
         * GM 게시판 1
         */
        case 4200020:

            if (pc.getAccessLevel()
                    == Config.GMCODE) {

                L1BoardPost.createGM1(
                        pc.getName(),
                        title,
                        content);

            } else {

                SC_SystemMessage.send(pc, "해당 게시판은 운영자 전용 게시판 입니다.");
            }

            return;

        /*
         * GM 게시판 2
         */
        case 4200021:

            if (pc.getAccessLevel()
                    == Config.GMCODE) {

                L1BoardPost.createGM2(
                        pc.getName(),
                        title,
                        content);

            } else {

                SC_SystemMessage.send(pc, "해당 게시판은 운영자 전용 게시판 입니다.");
            }

            return;

        /*
         * GM 게시판 3
         */
        case 71008:

            if (pc.getAccessLevel()
                    == Config.GMCODE) {

                L1BoardPost.createGM3(
                        pc.getName(),
                        title,
                        content);

            } else {

                SC_SystemMessage.send(pc, "해당 게시판은 운영자 전용 게시판 입니다.");
            }

            return;

        /*
         * 건의사항
         */
        case 500002:

            if (pc.getInventory()
                    .checkItem(
                            L1ItemId.ADENA,
                            500)) {

                pc.getInventory()
                        .consumeItem(
                                L1ItemId.ADENA,
                                500);

                L1BoardPost.createPhone(
                        pc.getName(),
                        "-비밀글-",
                        content);

                SC_SystemMessage.send(pc, "등록 완료 : 글 제목은 운영자도 못보니 참고하세요.");

            } else {

                SC_SystemMessage.send(pc, "아데나가 부족합니다.");
            }

            return;

        /*
         * 일반 게시판
         */
        default:

            if (pc.getInventory()
                    .checkItem(
                            L1ItemId.ADENA,
                            500)) {

                pc.getInventory()
                        .consumeItem(
                                L1ItemId.ADENA,
                                500);

                L1BoardPost.create(
                        pc.getName(),
                        title,
                        content);

                SC_SystemMessage.send(pc, "게시글 등록이 완료되었습니다.");

            } else {

                SC_SystemMessage.send(pc, "아데나가 부족합니다.");
            }

            return;
        }
    }
}