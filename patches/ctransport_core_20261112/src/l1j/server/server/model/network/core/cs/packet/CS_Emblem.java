package l1j.server.server.model.network.core.cs.packet;

import java.io.File;
import java.io.FileOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;

public final class CS_Emblem {

    private static final Logger LOGGER = Logger.getLogger(CS_Emblem.class.getName());

    private static final int EMBLEM_SIZE = 384;

    private static final File EMBLEM_DIR = new File("emblem");

    private CS_Emblem() {}

    public static void handle(CIOClient client, byte[] emblem, int emblemSize) {

        if (client == null || emblem == null || emblemSize != EMBLEM_SIZE || emblem.length < EMBLEM_SIZE) {
            return;
        }

        try {
            final L1PcInstance player = client.getActiveChar();

            if (player == null || player.isGhost()) {
                return;
            }

            final int rank = player.getClanRank();

            // 군주(4) 또는 부군주(10)만 엠블럼 등록 가능
            if (rank != 4 && rank != 10) {
                return;
            }

            final int clanId = player.getClanid();

            if (clanId == 0) {
                return;
            }

            final L1Clan clan = ClanTable.getInstance().getTemplate(clanId);

            if (clan == null) {
                return;
            }

            if (!EMBLEM_DIR.isDirectory()) {
                if (EMBLEM_DIR.exists() || !EMBLEM_DIR.mkdirs()) {
                    LOGGER.severe("엠블럼 디렉토리 생성 실패: " + EMBLEM_DIR.getPath());
                    return;
                }
            }

            final int newEmblemId = IdFactory.getInstance().nextId();
            final File emblemFile = new File(EMBLEM_DIR, Integer.toString(newEmblemId));

            try (FileOutputStream fos = new FileOutputStream(emblemFile)) {
                fos.write(emblem, 0, EMBLEM_SIZE);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, player.getName() + " 혈맹 엠블럼 파일 저장 중 I/O 오류", e);
                return;
            }

            clan.setEmblemId(newEmblemId);
            ClanTable.getInstance().updateClan(clan);

            for (L1PcInstance pc : clan.getOnlineClanMember()) {
                if (pc == null) {
                    continue;
                }

                SC_ReturnedStat.sendEmblem(pc, pc.getId(), newEmblemId);
                SC_ReturnedStat.broadcastEmblem(pc, pc.getId(), newEmblemId);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "UPLOAD_EMBLEM 처리 중 비정상 예외 발생", e);
        }
    }
}