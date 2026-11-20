package l1j.server.server.model.network.core.cs.packet;

import java.util.Random;
import l1j.server.Config;
import l1j.server.MJDungeonTimer.DungeonTimeInformation;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeProgressLoader;
import l1j.server.MJDungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.MJINNSystem.Loader.MJINNHelperLoader;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.model.npc.action.L1NpcAction;

final class HtmlCmdHandler {

    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(HtmlCmdHandler.class.getName());

    /*
     * 원본도 static Random 하나를 공유했다.
     * packet wrapper만 제거하고 random semantics는 그대로 유지.
     */
    static final Random _random = new Random(System.nanoTime());

    private HtmlCmdHandler() {
    }

    public static void handle(
            CIOClient client,
            int objid,
            String s,
            String s2,
            byte[] actionData) {

        if (client == null) return;

        // 게임중이 아니면
        if (client.getConnectionState() != ConnectionState.IN_GAME) {
            return;
        }

        try {
            L1PcInstance pc = client.getActiveChar();
            if (pc == null) return;

            if (s == null) return;

            if (NpcLocalCommands.dispatchLocalCommand(pc, s)) return;

            String htmlid = null;

            if (!canInteractWithNpc(client, pc))
                return;

        //	System.out.println("Action : " + s);
            L1Object obj = L1World.getInstance().findObject(objid);
            if (!dispatchTarget(pc, obj, objid, s)) return;
            if (obj instanceof L1NpcInstance npc) trackDialogue(client, npc, false);
            if (dispatchRegisteredAction(pc, obj, s, actionData)) return;
            if (!(obj instanceof L1NpcInstance npc)) return;

            NpcCastleActions.sendTaxScreens(pc, obj, s);

            if (s.equalsIgnoreCase("mayer3")) {
                htmlid = "mayer3";
            }
            NpcServiceHtmlCommands.handle(client, pc, obj, objid, s, s2, htmlid, npc.getNpcId());
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING, "NPC command execution failed", e);
        }
    }

    static void finish(L1PcInstance pc, L1Object obj, int objid, String htmlid,
            int[] materials, int[] counts, int[] createitem, int[] createcount,
            String success_htmlid, String failure_htmlid, String[] htmldata) {
        if (htmlid != null && htmlid.equalsIgnoreCase("colos2")) {
            htmldata = NpcGameEventActions.makeUbInfoStrings(((L1NpcInstance) obj).getNpcTemplate().get_npcId());
        }
        if (createitem != null) {
            NpcCraftCommands.execute(pc, obj, objid, materials, counts,
                    createitem, createcount, success_htmlid, failure_htmlid, htmlid, htmldata);
            return;
        }

        if (htmlid != null) {
            SC_NPCTalkReturn.send(pc, objid, htmlid, htmldata);
        }
    }

    static boolean canInteractWithNpc(CIOClient client, L1PcInstance pc) {
        return client != null && client.getConnectionState() == ConnectionState.IN_GAME
                && pc != null && !pc.isGhost() && !pc.isPrivateShop()
                && !pc.is무인상점() && !pc.isAutoClanjoin();
    }

    /** Shared live target admission for ordinary HTML and XML amount commands.
     * Shared by talk, HTML, and amount admission; full talk-channel lifecycle is separate. */
    static boolean isNpcTargetInRange(L1PcInstance pc, L1NpcInstance npc) {
        return pc.getMapId() == npc.getMapId()
                && Math.abs((long) pc.getX() - npc.getX()) <= 6
                && Math.abs((long) pc.getY() - npc.getY()) <= 6;
    }

    static void trackDialogue(CIOClient client, L1NpcInstance npc, boolean newTalk) {
        client.trackNpcDialogue(L1World.getInstance().smartIdOf(npc), newTalk);
    }

    static boolean canReplyToDialogue(L1PcInstance pc, long target, int version) {
        final CIOClient client = pc.getNetConnection();
        return client != null && client.canReplyToNpcDialogue(pc, target, version);
    }

    /** Existing target policy and pre-dispatch side effects; false stops the packet. */
    private static boolean dispatchTarget(L1PcInstance pc, L1Object obj, int objid, String s) {
        if (obj != null) {
            if (obj instanceof L1NpcInstance) {
                L1NpcInstance npc = (L1NpcInstance) obj;
                int nd = Config.네쥬던전초기화;

                // 보안 체크: 맵 일치 및 거리 확인
                if (!isNpcTargetInRange(pc, npc)) return false;

    //
                final int templateId = npc.getNpcId();
                if (templateId == 7000020 || templateId == 460000378 || templateId == 460000379) { //기감 시간 초기화 에러 방지 neige
                    if(pc.getAccount().gethero21() != nd ){
                        if(Config.네쥬던전체킹 == 1){
                            DungeonTimeProgressLoader.delete(pc);
                            pc.initialize_dungeon_progress();
                            System.out.println("던전체킹 1 , 네쥬 기감 초기화 : "+pc.getName());
                        }else{
                            DungeonTimeInformation dtInfo = DungeonTimeInformationLoader.getInstance().from_timer_id(1);
                            DungeonTimeProgress<?> progress = pc.get_progress(dtInfo);
                            if(progress != null){
                                progress.set_remain_seconds(dtInfo.get_amount_seconds());
                                DungeonTimeProgressLoader.update(progress);
                                System.out.println("던전체킹 2 , 네쥬 기감 초기화 : "+pc.getName());
                            }
                        }
                        pc.getAccount().updateHero21(pc.getId(), nd);
                    }
                }
                npc.onFinalAction(pc, s);
                if(s.contains("buy")){SC_SkillSound.send(pc, pc.getId(), 21129);}
                if(s.contains("sell")){SC_SkillSound.send(pc, pc.getId(), 21129);}
                if (pc.isGm()) {
                    SC_SystemMessage.send(pc, "\\f:[NPC번호]  \\aA" + npc.getNpcId() + "     \\f:[액션]  \\aA" + s);
                    SC_SystemMessage.send(pc, "\\f:[obj번호]  \\aA" + objid);

                }
            } else if (obj instanceof L1PcInstance target) {
                NpcPlayerMenuHandler.handle(pc, target, s);
                return false;
            }
        } else {
            return false;
        }

        return true;
    }

    /** Existing inn and XML handlers have the same precedence; true consumes the packet. */
    private static boolean dispatchRegisteredAction(L1PcInstance pc, L1Object obj, String s, byte[] actionData) {
        if(obj.instanceOf(MJL1Type.L1TYPE_NPC)){
            if(MJINNHelperLoader.getInstance().onAction((L1NpcInstance)obj, pc, s))
                return true;
        }

        L1NpcAction action = NpcActionTable.getInstance().get(s, pc, obj);
        if (action != null) {
            action.dispatch(s, pc, obj, actionData != null ? actionData : EMPTY_BYTES, 0);
            return true;
        }

        return false;
    }


}
