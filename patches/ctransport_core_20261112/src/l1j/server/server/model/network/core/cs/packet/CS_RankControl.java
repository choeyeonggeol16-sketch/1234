package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI2;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.FaceToFace;

public final class CS_RankControl {

    private static final Logger LOG = Logger.getLogger(CS_RankControl.class.getName());

    private CS_RankControl() {}

    public static void handle(CIOClient client, int type, int rank, String name) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        if (pc == null || pc.isGhost()) {
            return;
        }

        try {
            final L1Clan clan = L1World.getInstance().getClan(pc.getClanid());

            switch (type) {

                /*
                 * 계급 부여
                 */
                case 1:
                    handleRankAssignment(client, pc, clan, rank, name);
                    return;

                /*
                 * 동맹 목록
                 */
                case 2:
                    if (clan != null && clan.getAlliance() != 0) {
                        SC_PacketBox.send(pc, pc, SC_PacketBox.ALLIANCE_LIST);
                    }
                    return;

                /*
                 * 동맹 가입
                 */
                case 3:
                    handleAllianceJoin(pc, clan);
                    return;

                /*
                 * 동맹 탈퇴
                 */
                case 4:
                    handleAllianceLeave(pc, clan);
                    return;

                /*
                 * 생존의 외침
                 */
                case 5:
                    handleCryOfSurvival(pc);
                    return;

                /*
                 * 무기 허세
                 */
                case 6:
                    handleWeaponShowOff(pc);
                    return;

                default:
                    return;
            }

        } catch (Exception ignored) {
       
        }
    }

    private static void handleRankAssignment(CIOClient client, L1PcInstance pc, L1Clan clan, int rank, String name) throws Exception {

   
        if (clan == null) {
            SC_SystemMessage.send(pc, "혈맹에 가입되어 있지 않습니다.");
            return;
        }

        if (name == null || name.isEmpty()) {
            return;
        }

        final int pcRank = pc.getClanRank();

        if (!pc.isCrown() && pcRank != L1Clan.수호 && pcRank != L1Clan.부군주) {
            SC_SystemMessage.send(pc, "계급 부여 실패 : 계급 부여 권한이 없음.");
            return;
        }
        final L1PcInstance onlineTarget = L1World.getInstance().getPlayer(name);

        if (onlineTarget != null) {
            if (onlineTarget.getClanid() != pc.getClanid()) {
                SC_SystemMessage.send(pc, "같은 혈맹원이 아닙니다.");
                return;
            }

            processRankAssignment(pc, onlineTarget, clan, rank);
            return;
        }

        // An offline rank assignment previously restored the character through
        // JDBC on the shared NIO.2 completion/packet thread. Resolve its ID
        // from the resident name index and load it on an existing CacheD worker.
        // Key the read to the target character so earlier ordered saves finish
        // before the snapshot is restored. The owner handoff revalidates all
        // mutable privileges and the live target before applying the result.
        final l1j.server.server.templates.L1CharName known =
                CharacterTable.getInstance().getCharName(name);
        if (known == null || known.getId() <= 0) {
            SC_SystemMessage.send(pc, "그러한 캐릭터는 없습니다.");
            return;
        }
        final int targetId = known.getId();
        final int sourceClanId = pc.getClanid();
        try {
            CacheD.getInstance().submitDbJobOrdered(targetId, () -> {
                final L1PcInstance restored;
                try {
                    restored = CharacterTable.getInstance().restoreCharacter(name);
                } catch (Exception failure) {
                    LOG.log(Level.WARNING, "Offline rank target load failed: " + name, failure);
                    CIOThreadCallback.getInstance().completeSession(client, () ->
                            SC_SystemMessage.send(pc, "계급 변경 대상 정보를 불러오지 못했습니다."));
                    return;
                }
                if (!CIOThreadCallback.getInstance().completeSession(client, () -> {
                    if (client.getActiveChar() != pc || pc.isGhost()
                            || pc.getClanid() != sourceClanId
                            || L1World.getInstance().getClan(sourceClanId) != clan
                            || (!pc.isCrown() && pc.getClanRank() != L1Clan.수호
                                    && pc.getClanRank() != L1Clan.부군주)) {
                        return;
                    }
                    // The offline character may have logged in while the DB
                    // worker was loading it. Never save a detached snapshot
                    // over a freshly connected instance.
                    final L1PcInstance live = L1World.getInstance().getPlayer(name);
                    final L1PcInstance target = live != null ? live : restored;
                    if (target == null || target.getId() != targetId
                            || target.getClanid() != sourceClanId) {
                        SC_SystemMessage.send(pc, "그러한 캐릭터는 없습니다.");
                        return;
                    }
                    processRankAssignment(pc, target, clan, rank);
                    if (live == null) {
                        final String rankString = getRankString(rank);
                        for (L1PcInstance member : clan.getOnlineClanMember()) {
                            if (member != null) {
                                SC_SystemMessage.send(member, target.getName()
                                        + " 님의 계급이 " + rankString + "(으)로 변경되었습니다.");
                            }
                        }
                    }
                })) {
                    LOG.warning("Offline rank owner completion rejected: " + name);
                }
            });
        } catch (RejectedExecutionException refused) {
            LOG.log(Level.WARNING, "Offline rank DB admission rejected: " + name, refused);
            SC_SystemMessage.send(pc, "계급 변경 저장이 지연되고 있습니다. 다시 시도해 주세요.");
        }
    }

    private static void handleAllianceJoin(L1PcInstance pc, L1Clan clan) {

        if (clan == null) {
            return;
        }

        final L1PcInstance allianceLeader = FaceToFace.faceToFace(pc);

        if (allianceLeader == null) {
            return;
        }

        if (pc.getLevel() < 25 || !pc.isCrown()) {
            SC_ServerMessage.send(pc, 1206);
            return;
        }

        if (clan.getAlliance() != 0) {
            SC_ServerMessage.send(pc, 1202);
            return;
        }

        if (clan.getAlliance() > 4) {
            SC_SystemMessage.send(pc, "동맹은 4개 혈맹 까지만 가능합니다.");
            return;
        }

        if (clan.getCurrentWar() != null) {
            SC_ServerMessage.send(pc, 1234);
            return;
        }

        if (allianceLeader.getLevel() > 24 && allianceLeader.isCrown()) {
            allianceLeader.setTempID(pc.getId());
            SC_MessageYN.send(allianceLeader, 223, pc.getName());
        } else {
            SC_ServerMessage.send(pc, 1201);
        }
    }

    private static void handleAllianceLeave(L1PcInstance pc, L1Clan clan) {

        if (clan == null) {
            return;
        }

        if (clan.getCurrentWar() != null) {
            SC_ServerMessage.send(pc, 1203);
            return;
        }

        if (clan.getAlliance() != 0) {
            SC_MessageYN.send(pc, 1210, "");
        } else {
            SC_ServerMessage.send(pc, 1233);
        }
    }

    private static void handleCryOfSurvival(L1PcInstance pc) {

        final L1ItemInstance weapon = pc.getWeapon();

        if (weapon == null) {
            SC_ServerMessage.send(pc, 1973);
            return;
        }

        if (pc.get_food() < 225) {
            SC_ServerMessage.send(pc, 3461);
            return;
        }

        int addHp = 0;
        int gfxId1 = 8683;
        int gfxId2 = 829;

        final long curTime = System.currentTimeMillis() / 1000L;
        final int fullTime = (int) ((curTime - pc.getCryOfSurvivalTime()) / 60L);

        if (fullTime < 30) {
            if (!(pc.getLevel() >= 90 && fullTime >= 10)) {
                final long time = (pc.getCryOfSurvivalTime() + (60L * 30L)) - curTime;
                
                SC_SystemMessage.send(pc, "생존의 외침: " + (time / 60L) + "분 " + (time % 60L) + "초 후 사용 가능.");
                return;
            }
        }

        final int enchant = weapon.getEnchantLevel();

        if (weapon.getItem().get_safeenchant() == 0) {
            if (enchant >= 0 && enchant <= 9) {
                gfxId1 = 8773;
                gfxId2 = 8910;
                addHp = 1400;
            } else {
                gfxId1 = 8686;
                gfxId2 = 8908;
                addHp = 1600;
            }
        } else {
            if (enchant >= 0 && enchant <= 6) {
                gfxId1 = 8684;
                gfxId2 = 8907;
                addHp = 500;
            } else if (enchant == 7 || enchant == 8) {
                addHp = enchant == 7 ? 800 : 1000;
                gfxId1 = 8685;
                gfxId2 = 8909;
            } else if (enchant == 9 || enchant == 10) {
                addHp = enchant == 9 ? 1200 : 1400;
                gfxId1 = 8773;
                gfxId2 = 8910;
            } else {
                gfxId1 = 8686;
                gfxId2 = 8908;
                addHp = 1600;
            }
        }

        long sound = SC_SkillSound.pack(pc.getId(), gfxId1);
        SC_SkillSound.sendPacked(pc, sound);
        SC_SkillSound.broadcastPacked(pc, sound);

        sound = SC_SkillSound.pack(pc.getId(), gfxId2);
        SC_SkillSound.sendPacked(pc, sound);
        SC_SkillSound.broadcastPacked(pc, sound);

        pc.setCryOfSurvivalTime();
        pc.set_food(0);

        SC_PacketBox.send(pc, SC_PacketBox.FOOD, 0);
        pc.setCurrentHp(pc.getCurrentHp() + addHp);
    }

    private static void handleWeaponShowOff(L1PcInstance pc) {

        final L1ItemInstance weapon = pc.getWeapon();

        if (weapon == null) {
            SC_ServerMessage.send(pc, 1973);
            return;
        }

        final int enchantLevel = weapon.getEnchantLevel();
        int gfx = 8684;

        if (enchantLevel < 0) {
            SC_ServerMessage.send(pc, 79);
            return;
        } else if (enchantLevel >= 7 && enchantLevel <= 8) {
            gfx = 8685;
        } else if (enchantLevel >= 9 && enchantLevel <= 10) {
            gfx = 8773;
        } else if (enchantLevel >= 11) {
            gfx = 8686;
        }

        SC_SkillSound.send(pc, pc.getId(), gfx);
        SC_SkillSound.broadcast(pc, pc.getId(), gfx);
    }

    private static void processRankAssignment(L1PcInstance pc, L1PcInstance target, L1Clan clan, int rank) {

        try {
            /*
             * 군주 계급은 다른 사람에게 지정할 수 없습니다.
             */
            if (target.isCrown() && target.getId() == clan.getLeaderId()) {
                SC_SystemMessage.send(pc, "계급 부여 실패 : 대상이 혈맹의 군주");
                return;
            }

            final int pcRank = pc.getClanRank();

            /*
             * 부군주 권한 제한.
             */
            if (pcRank == L1Clan.부군주 && rank == 3) {
                SC_SystemMessage.send(pc, "계급 부여 실패 : 부여 계급이 자신보다 높거나 같은 계급");
                return;
            }

            /*
             * 수호기사 권한 제한.
             */
            if (pcRank == L1Clan.수호) {
                if (rank == 9) {
                    SC_SystemMessage.send(pc, "계급 부여 실패 : 부여 계급이 자신보다 높거나 같은 계급");
                    return;
                }

                final int targetRank = target.getClanRank();

                if (targetRank == L1Clan.군주 || targetRank == L1Clan.수호 || targetRank == L1Clan.부군주) {
                    SC_SystemMessage.send(pc, "계급 부여 실패 : 대상이 현재 자신보다 높거나 같은 계급");
                    return;
                }
            }

            target.setClanRank(rank);

            target.save();

            SC_PacketBox.send(target, SC_PacketBox.MSG_RANK_CHANGED, rank, target.getName());

            if (pc.getId() != target.getId()) {
                SC_PacketBox.send(pc, SC_PacketBox.MSG_RANK_CHANGED, rank, target.getName());
            }

            clan.UpdataClanMember(target.getName(), rank);

            final String rankString = getRankString(rank);

            SC_SystemMessage.send(target, "계급: " + rankString + "(으)로 임명됨");
            SC_ActionUI2.send(target, target, SC_ActionUI2.CLAN_RANK);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getRankString(int rank) {
        switch (rank) {
            case 3:
                return "부군주";
            case 7:
                return "수련";
            case 9:
                return "수호기사";
            case 13:
                return "정예";
            default:
                return "일반";
        }
    }
}