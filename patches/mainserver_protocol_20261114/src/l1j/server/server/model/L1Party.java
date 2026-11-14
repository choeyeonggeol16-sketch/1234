package l1j.server.server.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

import l1j.server.Config;
import l1j.server.server.Controller.AuraController;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.Packet.SC_PARTY_MEMBER_LIST;
import l1j.server.server.packets.Protocol.Packet.SC_PARTY_MEMBER_LIST_CHANGE;
import l1j.server.server.packets.Protocol.Packet.SC_PARTY_MEMBER_MARK_CHANGE_NOTI;
import l1j.server.server.packets.Protocol.Packet.SC_PARTY_MEMBER_STATUS;
import l1j.server.server.packets.Protocol.Packet.SC_PARTY_SYNC_PERIODIC_INFO;
import l1j.server.server.model.network.core.sc.packet.SC_Party;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1Party {

    @SuppressWarnings("unused")
    private static final Logger _log = Logger.getLogger(L1Party.class.getName());

    protected final List<L1PcInstance> _membersList = new ArrayList<L1PcInstance>();
    protected final HashMap<Integer, Integer> _membersMark = new HashMap<Integer, Integer>();

    /** 전체 LIST 재전송 쿨다운 — 멘트/도배 방지 */
    private static final long FULL_LIST_COOLDOWN_MS = 1500L;
    private final ConcurrentHashMap<Integer, Long> _lastFullListSentAt = new ConcurrentHashMap<>();

    private L1PcInstance _leader = null;

    // ─────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────
    private static boolean ok(L1PcInstance pc) {
        return pc != null && pc.getNetConnection() != null;
    }

    private boolean canSendFullListNow(int objectId) {
        long now = System.currentTimeMillis();
        Long last = _lastFullListSentAt.get(objectId);
        if (last != null && (now - last.longValue()) < FULL_LIST_COOLDOWN_MS) {
            return false;
        }
        _lastFullListSentAt.put(objectId, now);
        return true;
    }

    /** 특정 대상 목록에게 공유 바이트 전송 */
    private static void sendRawTo(L1PcInstance[] targets, byte[] data) {
        if (targets == null || targets.length == 0 || data == null) return;
        byte[] raw = data;
        for (L1PcInstance t : targets) {
            if (ok(t)) l1j.server.server.packets.Protocol.Proto.sendRaw(t, raw);
        }
    }

    /** 파티 전원에게 공유 바이트 전송 */
    public void broadcastRaw(byte[] data) {
        sendRawTo(getMembers(), data);
    }

    /** exclude 제외한 멤버 배열 */
    private L1PcInstance[] getMembersExcept(L1PcInstance exclude) {
        ArrayList<L1PcInstance> out = new ArrayList<>();
        for (L1PcInstance m : _membersList) {
            if (m.getId() != exclude.getId()) out.add(m);
        }
        return out.toArray(new L1PcInstance[0]);
    }

    /**
     * 전체 LIST 전송 (쿨다운 적용, force=true면 무시)
     */
    private void sendPartyListTo(L1PcInstance[] targets, L1PcInstance[] snapshot, boolean force) {
        if (targets == null || targets.length == 0) return;

        ArrayList<SC_PARTY_MEMBER_LIST.Member> members = new ArrayList<>();
        for (L1PcInstance m : snapshot) {
            if (!ok(m)) continue;
            members.add(SC_PARTY_MEMBER_LIST.Member.from(m, _membersMark.getOrDefault(m.getId(), 0)));
        }
        byte[] data = new SC_PARTY_MEMBER_LIST(
                _leader != null ? _leader.getName() : "", members).encode();

        for (L1PcInstance t : targets) {
            if (!ok(t)) continue;
            if (!force && !canSendFullListNow(t.getId())) continue;
            l1j.server.server.packets.Protocol.Proto.sendRaw(t, data);
        }
    }

    /** 탈퇴자/해산자 UI 완전 정리 — out_user + 빈 LIST (피바/HP바 잔존 방지) */
    private void clearPartyUIForLeaver(L1PcInstance leaver) {
        if (!ok(leaver)) return;
        sendRawTo(new L1PcInstance[]{ leaver }, SC_PARTY_MEMBER_LIST_CHANGE.ofOutUser(leaver.getName()).encode());
        sendRawTo(new L1PcInstance[]{ leaver }, SC_PARTY_MEMBER_LIST.empty().encode());
    }

    // ─────────────────────────────────────────────
    // party ops
    // ─────────────────────────────────────────────
    public void addMember(L1PcInstance pc) {
        if (pc == null) {
            throw new NullPointerException();
        }
        if (pc.getAutoHunt()) {
            pc.sendPackets("자동사냥 중에는 파티에 참가할 수 없습니다.");
            return;
        }
        if (_membersList.size() == Config.MAX_PT && !_leader.isGm() || _membersList.contains(pc)) {
            SC_ServerMessage.send(pc, 417);
            return;
        }

        if (_membersList.isEmpty()) {
            setLeader(pc);
        }

        if (pc.isCrown()) {
            if (SkillsTable.getInstance().spellCheck(pc.getId(), 121)) {
                AuraController.getInstance().addMember(pc);
            }
        }

        _membersMark.put(pc.getId(), 0);
        _membersList.add(pc);
        pc.setParty(this);

        int beforeSize = _membersList.size() - 1;
        L1PcInstance[] after = getMembers();

        ArrayList<SC_PARTY_MEMBER_LIST.Member> list = new ArrayList<>();
        for (L1PcInstance m : after) {
            list.add(SC_PARTY_MEMBER_LIST.Member.from(m, _membersMark.getOrDefault(m.getId(), 0)));
        }

        if (beforeSize > 0) {
            // 신규 멤버: 전체 LIST (여기서 멘트 1번)
            SC_PARTY_MEMBER_LIST.sendFields(pc, _leader.getName(), list);
        }
        if (beforeSize == 1 && after.length == 2) {
            // 2인 파티 성립: 기존 멤버에게도 LIST (CHANGE 아님)
            L1PcInstance existing = (after[0].getId() == pc.getId()) ? after[1] : after[0];
            sendPartyListTo(new L1PcInstance[]{ existing }, after, true);
            return;
        }
        if (after.length > 1) {
            // 3인 이상 확장: 기존 멤버들에게만 new_user 알림
            sendRawTo(getMembersExcept(pc),
                    SC_PARTY_MEMBER_LIST_CHANGE
                            .ofNewMember(SC_PARTY_MEMBER_LIST.Member.from(pc, 0)).encode());
        }
    }

    protected void removeMember(L1PcInstance pc) {
        if (pc == null || !_membersList.contains(pc)) {
            return;
        }

        // 잔여 멤버 스냅샷 (본인 제외)
        L1PcInstance[] rest = getMembersExcept(pc);

        // 잔여 멤버: out_user 알림
        sendRawTo(rest,
                SC_PARTY_MEMBER_LIST_CHANGE.ofOutUser(pc.getName()).encode());
        // 탈퇴자 본인: out_user + 빈 LIST → UI/HP바 완전 제거
        clearPartyUIForLeaver(pc);

        boolean wasLeader = isLeader(pc);
        _membersList.remove(pc);
        _membersMark.remove(pc.getId());
        pc.setParty(null);

        // 리더가 나갔으면 승계 + 알림
        if (wasLeader && !_membersList.isEmpty()) {
            L1PcInstance newLeader = _membersList.get(0);
            setLeader(newLeader);
            sendRawTo(rest,
                    SC_PARTY_MEMBER_LIST_CHANGE.ofLeaderChange(newLeader.getName()).encode());
        }

        if (wasLeader && pc.isCrown()) {
            AuraController.getInstance().removeMember(pc);
        }
        if (pc.getPbavatar()) {
            AuraController.getInstance().brave_end(pc);
        }

        if (pc._CubeEffect) {
            if (pc.hasSkillEffect(L1SkillId.CUBE_AVATAR))
                pc.removeSkillEffect(L1SkillId.CUBE_AVATAR);
            if (pc.hasSkillEffect(L1SkillId.CUBE_RICH))
                pc.removeSkillEffect(L1SkillId.CUBE_RICH);
            if (pc.hasSkillEffect(L1SkillId.CUBE_GOLEM))
                pc.removeSkillEffect(L1SkillId.CUBE_GOLEM);
            if (pc.hasSkillEffect(L1SkillId.CUBE_OGRE))
                pc.removeSkillEffect(L1SkillId.CUBE_OGRE);
            pc._CubeEffect = false;
        }
    }

    public boolean isVacancy() {
        return _membersList.size() < Config.MAX_PT;
    }

    public int getVacancy() {
        return Config.MAX_PT - _membersList.size();
    }

    public boolean isMember(L1PcInstance pc) {
        return _membersList.contains(pc);
    }

    protected void setLeader(L1PcInstance pc) {
        _leader = pc;
    }

    public L1PcInstance getLeader() {
        return _leader;
    }

    public boolean isLeader(L1PcInstance pc) {
        return _leader != null && pc.getId() == _leader.getId();
    }

    public boolean isAutoDivision(L1PcInstance pc) {
        return pc.getPartyType() == 1 || pc.getPartyType() == 4;
    }

    public String getMembersNameList() {
        String _result = new String("");
        for (L1PcInstance pc : _membersList) {
            _result = _result + pc.getName() + " ";
        }
        return _result;
    }

    public void refresh(L1PcInstance pc) {// 파티추가
        L1PcInstance leader = getLeader();
        for (L1PcInstance member : getMembers()) {
            if(member == null || pc.getId() == member.getId())
                continue;

            if (leader != null && leader.getId() == pc.getId()) {
                SC_Party.sendNameColor(member, pc, 2);
            } else {
                SC_Party.sendNameColor(member, pc, 1);
            }
        }
    }

    public void memberDie(L1PcInstance pc) {// 파티원 죽었을 때 — 상태갱신 (0x0339)
        refreshPartyMemberStatus(pc);
    }

    public void broadcastPartyList() {
        // LIST는 "소속되었습니다" 멘트 트리거 → 주기동기화로 대체
        refreshPartyList();
    }

    public void refreshPartyList() {
        ArrayList<SC_PARTY_SYNC_PERIODIC_INFO.MemberStatus> list = new ArrayList<>();
        for (L1PcInstance member : getMembers()) {
            if (!ok(member)) continue;
            list.add(new SC_PARTY_SYNC_PERIODIC_INFO.MemberStatus(
                    member.getName(),
                    member.getId(),
                    (int) member.getCurrentHpPercent(),
                    (int) member.getCurrentMpPercent(),
                    member.isDead() ? 0 : 1,
                    member.getMapId(),
                    member.getLongLocation(),
                    _membersMark.getOrDefault(member.getId(), 0)));
        }
        broadcastRaw(new SC_PARTY_SYNC_PERIODIC_INFO(list).encode());
    }

    public void handshakePartyMemberStatus(L1PcInstance pc1, L1PcInstance pc2) {
        if (!ok(pc1) || !ok(pc2)) return;
        SC_PARTY_MEMBER_STATUS.sendFrom(pc2, pc1, _membersMark.getOrDefault(pc1.getId(), 0), true);
        SC_PARTY_MEMBER_STATUS.sendFrom(pc1, pc2, _membersMark.getOrDefault(pc2.getId(), 0), true);
    }

    public void refreshPartyMemberStatus(L1PcInstance pc) {
        if (!ok(pc)) return;
        byte[] data = SC_PARTY_MEMBER_STATUS
                .from(pc, _membersMark.getOrDefault(pc.getId(), 0), true)
                .encode();
        broadcastRaw(data);
    }

    protected void breakup() {
        l1j.server.server.model.character.Item.Drop.PartyLootCursor.clearParty(this);
        L1PcInstance[] members = getMembers();
        _membersList.clear();
        _membersMark.clear();
        _lastFullListSentAt.clear();
        _leader = null;

        for (L1PcInstance m : members) {
            if (m == null) continue;
            m.setParty(null);
            clearPartyUIForLeaver(m);       // 각자 UI 완전 정리
            if (ok(m)) SC_ServerMessage.send(m, 418);
        }
    }

    public void passLeader(L1PcInstance pc) { // 리더위임
        if (pc == null || !_membersList.contains(pc)) return;
        setLeader(pc);
        broadcastRaw(SC_PARTY_MEMBER_LIST_CHANGE.ofLeaderChange(pc.getName()).encode());
        refreshPartyList();
    }

    public void leaveMember(L1PcInstance pc) {
        if (isLeader(pc) || getNumOfMembers() == 2) {
            breakup();
        } else {
            removeMember(pc);
            for (L1PcInstance member : getMembers()) {
                sendLeftMessage(member, pc);
            }
            sendLeftMessage(pc, pc);
        }
    }

    public void kickMember(L1PcInstance pc) { // 리더추방
        if (getNumOfMembers() == 2) {
            breakup();
        } else {
            removeMember(pc);
            for (L1PcInstance member : getMembers()) {
                sendLeftMessage(member, pc);
            }
            sendKickMessage(pc);
        }
        SC_ServerMessage.send(pc, 419); // 파티로부터 추방되었습니다.
    }

    public L1PcInstance[] getMembers() {
        return _membersList.toArray(new L1PcInstance[_membersList.size()]);
    }

    public int getNumOfMembers() {
        return _membersList.size();
    }

    private void sendKickMessage(L1PcInstance kickpc) {
        SC_ServerMessage.send(kickpc, 419);
    }

    protected void sendLeftMessage(L1PcInstance sendTo, L1PcInstance left) {
        SC_ServerMessage.send(sendTo, 420, left.getName());
    }

    public List<L1PcInstance> getList() {// 하딘 파티 멤버 리스트
        return _membersList;
    }

    public Stream<L1PcInstance> createMembersStream(){
        return _membersList.stream();
    }

    public void broadcastRootMent(String message) {
        if (message == null) return;
        for (L1PcInstance pc : _membersList) {
            if (pc != null && pc.RootMent1) SC_SystemMessage.send(pc, message);
        }
    }

    public void broadcastRootMent(ServerPacket packet){
        createMembersStream().filter((L1PcInstance pc) -> pc.RootMent1).forEach((L1PcInstance pc) -> pc.sendPackets(packet, false));
    }

    // ─────────────────────────────────────────────
    // 표식 — 0x0153 전용 NOTI + 상태 갱신
    // ─────────────────────────────────────────────
    public void setPartyMark(L1PcInstance target, int markId) {
        if (target == null) return;
        _membersMark.put(target.getId(), markId);
        broadcastRaw(new SC_PARTY_MEMBER_MARK_CHANGE_NOTI(target.getId(), markId).encode());
        refreshPartyMemberStatus(target);
    }
}