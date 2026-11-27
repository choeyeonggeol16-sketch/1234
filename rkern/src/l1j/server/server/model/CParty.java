package l1j.server.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import l1j.server.Config;
import l1j.server.server.Controller.AuraController;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.drop.CContributeData;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_Party;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_LIST;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_LIST_CHANGE;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_MARK_CHANGE_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_STATUS;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_SYNC_PERIODIC_INFO;

public class CParty {

    private static final Logger _log = Logger.getLogger(CParty.class.getName());

    protected final List<L1PcInstance> _membersList = new CopyOnWriteArrayList<>();
    protected final ConcurrentHashMap<Integer, Integer> _membersMark = new ConcurrentHashMap<>();

    /** 전체 LIST 재전송 쿨다운 — 멘트/도배 방지 */
    private static final long FULL_LIST_COOLDOWN_MS = 1500L;
    private final ConcurrentHashMap<Integer, Long> _lastFullListSentAt = new ConcurrentHashMap<>();

    private volatile L1PcInstance _leader;
    private long lootCursor;
    private static final AtomicLong LOOT_IDENTITIES = new AtomicLong();
    private final long lootIdentity = LOOT_IDENTITIES.incrementAndGet();
    public final long GetLootIdentity() { return lootIdentity; }

    public synchronized int NextLootIndex(int size) {
        if (size <= 1) return 0;
        return (int) Math.floorMod(lootCursor++, (long) size);
    }

    private synchronized void ResetLootIndex() { lootCursor = 0L; }

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
        if(targets==null || targets.length==0 || data==null)return;
        CSharedBroadcast.sendRawArray(targets,data);
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
    private void sendPartyListTo(L1PcInstance[] targets,L1PcInstance[] snapshot,boolean force) {
        if(targets==null || targets.length==0)return;
        ArrayList<SC_PARTY_MEMBER_LIST.Member> members=new ArrayList<>();
        for(L1PcInstance m:snapshot){
            if(!ok(m))continue;
            members.add(SC_PARTY_MEMBER_LIST.Member.from(m,
                    _membersMark.getOrDefault(m.getId(),0)));
        }
        final L1PcInstance leader = getLeader();
        byte[] data = new SC_PARTY_MEMBER_LIST(leader != null ? leader.getName() : "", members).encode();
        try(var shared=CSharedBroadcast.openRaw(data)){
            for(L1PcInstance t:targets){
                if(!ok(t))continue;
                if(!force && !canSendFullListNow(t.getId()))continue;
                CSharedBroadcast.sendRecipient(shared,t);
            }
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
        final L1PcInstance leader = getLeader();
        if ((_membersList.size() >= Config.MAX_PT && (leader == null || !leader.isGm()))
                || _membersList.contains(pc)) {
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

        final L1PcInstance currentLeader = getLeader();
        if (beforeSize > 0 && currentLeader != null) {
            // 신규 멤버: 전체 LIST (여기서 멘트 1번)
            SC_PARTY_MEMBER_LIST.sendFields(pc, currentLeader.getName(), list);
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

        final boolean wasLeader = isLeader(pc);
        if (!_membersList.remove(pc)) return;
        _membersMark.remove(pc.getId());
        _lastFullListSentAt.remove(pc.getId());
        if (pc.getParty() == this) pc.setParty(null);

        final L1PcInstance[] rest = getMembers();
        final L1PcInstance newLeader = wasLeader && rest.length != 0 ? rest[0] : null;
        if (wasLeader) setLeader(newLeader);

        // 잔여 멤버: out_user 알림
        sendRawTo(rest,
                SC_PARTY_MEMBER_LIST_CHANGE.ofOutUser(pc.getName()).encode());
        // 탈퇴자 본인: out_user + 빈 LIST → UI/HP바 완전 제거
        clearPartyUIForLeaver(pc);

        if (newLeader != null) {
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
        final L1PcInstance leader = getLeader();
        return pc != null && leader != null && pc.getId() == leader.getId();
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

    public void refresh(L1PcInstance pc) {
        final int partyColor = isLeader(pc) ? 2 : 1;
        try (var shared = new CAsyncSendConstBuffer(
                SC_Party.nativeNameColorWriter(pc.getId(), partyColor), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance member : getMembers()) {
                if (member == null || pc.getId() == member.getId()) continue;
                CSharedBroadcast.sendRecipient(shared, member);
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
        ResetLootIndex();
        L1PcInstance[] members=getMembers();
        _membersList.clear();
        _membersMark.clear();
        _lastFullListSentAt.clear();
        _leader=null;
        for (L1PcInstance member : members) {
            if (member != null && member.getParty() == this) member.setParty(null);
        }
        try(var shared=new CAsyncSendConstBuffer(
                SC_ServerMessage.nativeWriter(418),Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance m:members){
                if (m == null || m.getParty() != null) continue;
                try { clearPartyUIForLeaver(m); }
                catch (RuntimeException failure) {
                    _log.log(Level.WARNING, "Could not clear party UI for " + m.getId(), failure);
                }
                if (ok(m)) CSharedBroadcast.sendRecipientBestEffort(shared, m);
            }
        }
    }

    public void passLeader(L1PcInstance pc) { // 리더위임
        if (pc == null || !_membersList.contains(pc)) return;
        setLeader(pc);
        broadcastRaw(SC_PARTY_MEMBER_LIST_CHANGE.ofLeaderChange(pc.getName()).encode());
        refreshPartyList();
    }

    public void leaveMember(L1PcInstance pc) {
        if (!isMember(pc)) return;
        if(isLeader(pc) || getNumOfMembers()==2){
            breakup();
        } else {
            removeMember(pc);
            try(var shared=new CAsyncSendConstBuffer(
                    SC_ServerMessage.nativeWriter(420,pc.getName()),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){
                for(L1PcInstance member:getMembers())
                    CSharedBroadcast.sendRecipientBestEffort(shared, member);
                CSharedBroadcast.sendRecipientBestEffort(shared, pc);
            }
        }
    }

    public void kickMember(L1PcInstance pc) {
        if (!isMember(pc)) return;
        if(getNumOfMembers()==2){
            breakup();
        } else {
            removeMember(pc);
            try(var shared=new CAsyncSendConstBuffer(
                    SC_ServerMessage.nativeWriter(420,pc.getName()),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){
                for(L1PcInstance member:getMembers())
                    CSharedBroadcast.sendRecipientBestEffort(shared, member);
            }
            sendKickMessage(pc);
        }
        SC_ServerMessage.send(pc,419); // original duplicate kick notification retained
    }

    public L1PcInstance[] getMembers() {
        return _membersList.toArray(L1PcInstance[]::new);
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
        if(message==null)return;
        try(var shared=new CAsyncSendConstBuffer(
                SC_SystemMessage.nativeWriter(message),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:_membersList){
                if(pc!=null && pc.RootMent1)
                    CSharedBroadcast.sendRecipient(shared,pc);
            }
        }
    }

    

    /** Same map/range/hate policy for both currency and item routing. */
    private boolean CheckRouteCond(L1Location origin, CContributeData snap, L1PcInstance member) {
        if (member == null || member.getParty() != this || member.isDead()
                || member.getMap() != origin.getMap()
                || member.getLocation().getTileLineDistance(origin) > Config.PARTY_LOOT_RANGE) return false;
        if (Config.PARTY_LOOT_ELIGIBILITY != 0) return true;
        if (snap == null) return false;
        return snap.IsRouteContributor(member.getId());
    }

    private boolean CheckRouteAdenaCond(L1Location origin, CContributeData snap, L1PcInstance member) {
        return CheckRouteCond(origin, snap, member);
    }

    public boolean Route(L1NpcInstance npc, CInventory source, L1ItemInstance item,
            CContributeData snap, L1PcInstance primaryOwner) {
        return npc != null && Route(npc.getLocation(), source, item, snap, primaryOwner, null);
    }

    /** Queued drops retain the original party, roster and death position. */
    public boolean Route(L1Location origin, CInventory source, L1ItemInstance item,
            CContributeData snap, L1PcInstance primaryOwner, L1PcInstance[] admittedMembers) {
        if (!Config.PARTY_LOOT_ENABLE || primaryOwner == null || primaryOwner.getParty() != this
                || origin == null || source == null || item == null) return false;
        final CWorld world = CWorld.getInstance();
        if (!world.PickItemCond(source, item, item.getCount())) return false;

        final boolean adena = item.getItemId() == L1ItemId.ADENA;
        final L1PcInstance[] members = admittedMembers == null ? getMembers() : admittedMembers.clone();
        int size = 0;
        for (L1PcInstance member : members) {
            if (adena ? CheckRouteAdenaCond(origin, snap, member) : CheckRouteCond(origin, snap, member)) {
                members[size++] = member;
            }
        }
        if (size == 0) return false;

        final L1PcInstance leader = getLeader();
        final String tag = leader != null && leader.getPartyType() == 1 ? "자동 분배" : "일반 분배";
        if (adena) RouteAdena(world, origin, source, item, snap, members, size, tag);
        else RouteItem(world, origin, source, item, snap, members, size, tag);
        return true;
    }

    private void RouteAdena(CWorld world, L1Location origin, CInventory source, L1ItemInstance item,
            CContributeData snap, L1PcInstance[] members, int size, String tag) {
        final int winnerIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(size);
        final long total = item.getCount();
        final long each = total / size;
        final long remainder = total % size;

        for (int i = 0; i < size; i++) {
            final int amount = (int) (each + (i == winnerIndex ? remainder : 0L));
            if (amount <= 0) continue;
            final L1PcInstance member = members[i];
            final L1PcInstance recipient = CheckRouteAdenaCond(origin, snap, member) ? member : null;
            final int result = world.PickItem(origin, source, item, amount, recipient);
            if (result == CWorld.PICK_INVENTORY && member.RootMent1) {
                try {
                    SC_SystemMessage.send(member, item.getNumberedName(amount)
                            + " 획득 : " + member.getName() + "(" + tag + ")");
                } catch (RuntimeException e) {
                    _log.log(Level.WARNING, "Party adena notification failed", e);
                }
            }
        }
        RecoverRemainder(world, origin, source, item);
    }

    private void RouteItem(CWorld world, L1Location origin, CInventory source, L1ItemInstance item,
            CContributeData snap, L1PcInstance[] members, int size, String tag) {
        final int mode = Config.PARTY_LOOT_MODE;
        // The previous general mode already chose a random eligible winner.
        // Random mode also had a uniform final selection; keep that distribution.
        final int index = mode == 2 ? NextLootIndex(size)
                : java.util.concurrent.ThreadLocalRandom.current().nextInt(size);
        final L1PcInstance target = members[index];
        final int amount = item.getCount();
        final L1PcInstance recipient = CheckRouteCond(origin, snap, target) ? target : null;
        final int result = world.PickItem(origin, source, item, amount, recipient);
        if (result == CWorld.PICK_INVENTORY) {
            try {
                final String msg = item.getNumberedName(amount) + " 획득 : "
                        + target.getName() + "(" + tag + ")";
                try (var shared = new CAsyncSendConstBuffer(
                        SC_SystemMessage.nativeWriter(msg), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                    for (int i = 0; i < size; i++) {
                        final L1PcInstance member = members[i];
                        if (member.getParty() == this && member.RootMent1)
                            CSharedBroadcast.sendRecipient(shared, member);
                    }
                }
            } catch (RuntimeException e) {
                _log.log(Level.WARNING, "Party item notification failed", e);
            }
        }
        RecoverRemainder(world, origin, source, item);
    }

    private void RecoverRemainder(CWorld world, L1Location origin, CInventory source, L1ItemInstance item) {
        if (source.GetByServerID(item.getId()) != item || item.getCount() <= 0) return;
        if (!world.RecoverDropRemainder(origin, source, item)) {
            _log.warning("Party drop ground recovery rejected: item=" + item.getId()
                    + ", count=" + item.getCount());
        }
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
