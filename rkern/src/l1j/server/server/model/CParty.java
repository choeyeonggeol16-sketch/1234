package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import l1j.server.Config;
import l1j.server.server.Controller.AuraController;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.character.Item.Drop.CContributeData;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_LIST;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_LIST_CHANGE;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_MARK_CHANGE_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_MEMBER_STATUS;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_PARTY_SYNC_PERIODIC_INFO;
import l1j.server.server.model.network.core.sc.packet.SC_Party;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class CParty {

    private static final Logger _log = Logger.getLogger(CParty.class.getName());

    // NC-style ordered SID roster. The existing world identity includes the
    // allocation generation, so neither PC objects nor reused IDs are retained.
    private long[] m_aOrderedMember = new long[Math.max(2, Config.MAX_PT)];
    private int[] m_aTacticalSign = new int[m_aOrderedMember.length];
    private long[] m_aLastFullListSentAt = new long[m_aOrderedMember.length];
    private int m_nMemberCount;
    private long m_MasterSid;
    private boolean m_Dismissed;

    private static final long FULL_LIST_COOLDOWN_MS = 1500L;
    private long lootCursor;
    private static final java.util.concurrent.atomic.AtomicLong LOOT_IDENTITIES = new java.util.concurrent.atomic.AtomicLong();
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

    private L1PcInstance ResolveUser(long sid) {
        if (sid == 0L) return null;
        final L1Object object = L1World.getInstance().findObjectBySmartId(sid);
        return object instanceof L1PcInstance pc && pc.getParty() == this ? pc : null;
    }

    private int FindMemberIndex(int objectId) {
        for (int i = 0; i < m_nMemberCount; i++)
            if ((int) m_aOrderedMember[i] == objectId) return i;
        return -1;
    }

    private int FindMemberIndex(L1PcInstance pc) {
        return pc == null || pc.getParty() != this ? -1 : FindMemberIndex(pc.getId());
    }

    private void EnsureCapacity() {
        if (m_nMemberCount < m_aOrderedMember.length) return;
        // Preserve the existing GM capacity exception and configuration reload.
        final int capacity = Math.max(m_nMemberCount + 1,
                Math.max(Config.MAX_PT, m_aOrderedMember.length * 2));
        m_aOrderedMember = Arrays.copyOf(m_aOrderedMember, capacity);
        m_aTacticalSign = Arrays.copyOf(m_aTacticalSign, capacity);
        m_aLastFullListSentAt = Arrays.copyOf(m_aLastFullListSentAt, capacity);
    }

    private void InsertMemberSid(long sid) {
        m_aOrderedMember[m_nMemberCount++] = sid;
    }

    private void DeleteMemberSid(int index) {
        final int move = --m_nMemberCount - index;
        if (move > 0) {
            System.arraycopy(m_aOrderedMember, index + 1, m_aOrderedMember, index, move);
            System.arraycopy(m_aTacticalSign, index + 1, m_aTacticalSign, index, move);
            System.arraycopy(m_aLastFullListSentAt, index + 1, m_aLastFullListSentAt, index, move);
        }
        m_aOrderedMember[m_nMemberCount] = 0L;
        m_aTacticalSign[m_nMemberCount] = 0;
        m_aLastFullListSentAt[m_nMemberCount] = 0L;
    }

    private synchronized int GetTacticalSignId(int objectId) {
        final int index = FindMemberIndex(objectId);
        return index < 0 ? 0 : m_aTacticalSign[index];
    }

    private synchronized boolean canSendFullListNow(int objectId) {
        final int index = FindMemberIndex(objectId);
        if (index < 0) return false;
        final long now = System.currentTimeMillis();
        final long last = m_aLastFullListSentAt[index];
        if (last != 0L && now - last < FULL_LIST_COOLDOWN_MS) return false;
        m_aLastFullListSentAt[index] = now;
        return true;
    }

    /** 특정 대상 목록에게 공유 바이트 전송 */
    private static void sendRawTo(L1PcInstance[] targets, byte[] data) {
        if(targets==null || targets.length==0 || data==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRawArray(targets,data);
    }

    /** 파티 전원에게 공유 바이트 전송 */
    public void broadcastRaw(byte[] data) {
        sendRawTo(getMembers(), data);
    }

    /** exclude 제외한 멤버 배열 */
    private L1PcInstance[] getMembersExcept(L1PcInstance exclude) {
        final L1PcInstance[] snapshot = getMembers();
        int size = 0;
        for (L1PcInstance member : snapshot) {
            if (member.getId() != exclude.getId()) snapshot[size++] = member;
        }
        return size == snapshot.length ? snapshot : java.util.Arrays.copyOf(snapshot, size);
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
                    GetTacticalSignId(m.getId())));
        }
        final L1PcInstance leader = getLeader();
        byte[] data=new SC_PARTY_MEMBER_LIST(
                leader!=null?leader.getName():"",members).encode();
        try(var shared=l1j.server.server.model.network.core.broadcast.CSharedBroadcast.openRaw(data)){
            for(L1PcInstance t:targets){
                if(!ok(t))continue;
                if(!force && !canSendFullListNow(t.getId()))continue;
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,t);
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
    public boolean addMember(L1PcInstance pc) {
        if (pc == null) throw new NullPointerException();
        if (pc.getAutoHunt()) {
            pc.sendPackets("자동사냥 중에는 파티에 참가할 수 없습니다.");
            return false;
        }
        if (pc.isLogoutStarted() || pc.getParty() != null) return false;
        final long sid = L1World.getInstance().smartIdOf(pc);
        if (sid == 0L) return false;
        // DB/skill lookup must not run while holding the roster monitor.
        final boolean crownAura = pc.isCrown()
                && SkillsTable.getInstance().spellCheck(pc.getId(), 121);

        synchronized (this) {
            final L1PcInstance leader = getLeader();
            if (m_Dismissed || FindMemberIndex(pc.getId()) >= 0
                    || (m_nMemberCount > 0 && leader == null)
                    || (m_nMemberCount >= Config.MAX_PT && (leader == null || !leader.isGm()))) {
                SC_ServerMessage.send(pc, 417);
                return false;
            }
            if (pc.isLogoutStarted() || L1World.getInstance().smartIdOf(pc) != sid)
                return false;
            EnsureCapacity();
            if (!pc.compareAndSetParty(null, this)) return false;
            // Logout can win the admission race before seeing the new party.
            if (pc.isLogoutStarted() || L1World.getInstance().smartIdOf(pc) != sid) {
                pc.compareAndSetParty(this, null);
                return false;
            }
            final int beforeSize = m_nMemberCount;
            InsertMemberSid(sid);
            if (beforeSize == 0) m_MasterSid = sid;
            if (crownAura) AuraController.getInstance().addMember(pc);
            // State is committed before packet emission. Packet failure cannot
            // roll back ownership or leave a half-inserted member.
            try {
                final L1PcInstance[] after = getMembers();
                final L1PcInstance master = getLeader();
                if (beforeSize > 0 && ok(pc) && master != null) {
                    final ArrayList<SC_PARTY_MEMBER_LIST.Member> list = new ArrayList<>(after.length);
                    for (L1PcInstance member : after)
                        list.add(SC_PARTY_MEMBER_LIST.Member.from(member, GetTacticalSignId(member.getId())));
                    SC_PARTY_MEMBER_LIST.sendFields(pc, master.getName(), list);
                }
                if (beforeSize == 1 && after.length == 2) {
                    final L1PcInstance existing = after[0] == pc ? after[1] : after[0];
                    sendPartyListTo(new L1PcInstance[]{ existing }, after, true);
                } else if (after.length > 1) {
                    sendRawTo(getMembersExcept(pc), SC_PARTY_MEMBER_LIST_CHANGE
                            .ofNewMember(SC_PARTY_MEMBER_LIST.Member.from(pc, 0)).encode());
                }
            } catch (RuntimeException failure) {
                _log.log(java.util.logging.Level.WARNING, "Party join notification failed", failure);
            }
            return true;
        }
    }

    /** All roster fields have already been retired when the notification runs. */
    private void NotifyRemoved(L1PcInstance pc, boolean wasLeader) {
        final L1PcInstance[] rest = getMembers();
        sendRawTo(rest, SC_PARTY_MEMBER_LIST_CHANGE.ofOutUser(pc.getName()).encode());
        if (pc.getParty() == null) clearPartyUIForLeaver(pc);
        final L1PcInstance master = getLeader();
        if (wasLeader && master != null)
            sendRawTo(rest, SC_PARTY_MEMBER_LIST_CHANGE.ofLeaderChange(master.getName()).encode());
    }

    private void CleanupDeparted(L1PcInstance pc) {
        // Do not hold the party monitor while touching a creature's skills.
        if (pc.getParty() != null) return;
        try {
            if (pc.isCrown()) AuraController.getInstance().removeMember(pc);
            if (pc.getPbavatar()) AuraController.getInstance().brave_end(pc);
            if (pc._CubeEffect) {
                if (pc.hasSkillEffect(L1SkillId.CUBE_AVATAR)) pc.removeSkillEffect(L1SkillId.CUBE_AVATAR);
                if (pc.hasSkillEffect(L1SkillId.CUBE_RICH)) pc.removeSkillEffect(L1SkillId.CUBE_RICH);
                if (pc.hasSkillEffect(L1SkillId.CUBE_GOLEM)) pc.removeSkillEffect(L1SkillId.CUBE_GOLEM);
                if (pc.hasSkillEffect(L1SkillId.CUBE_OGRE)) pc.removeSkillEffect(L1SkillId.CUBE_OGRE);
                pc._CubeEffect = false;
            }
        } catch (RuntimeException failure) {
            _log.log(java.util.logging.Level.WARNING, "Party departure effect cleanup failed", failure);
        }
    }

    public synchronized boolean isVacancy() {
        return !m_Dismissed && m_nMemberCount < Config.MAX_PT;
    }

    public synchronized int getVacancy() {
        return m_Dismissed ? 0 : Config.MAX_PT - m_nMemberCount;
    }

    public synchronized boolean isMember(L1PcInstance pc) {
        return FindMemberIndex(pc) >= 0;
    }

    public synchronized boolean isDismissed() {
        return m_Dismissed;
    }

    public synchronized L1PcInstance getLeader() {
        return ResolveUser(m_MasterSid);
    }

    public synchronized boolean isLeader(L1PcInstance pc) {
        return pc != null && pc.getParty() == this && (int) m_MasterSid == pc.getId();
    }

    public boolean isAutoDivision(L1PcInstance pc) {
        return pc.getPartyType() == 1 || pc.getPartyType() == 4;
    }

    public String getMembersNameList() {
        final StringBuilder result = new StringBuilder(64);
        for (L1PcInstance pc : getMembers()) {
            result.append(pc.getName()).append(' ');
        }
        return result.toString();
    }

    public void refresh(L1PcInstance pc) {// 파티추가
        L1PcInstance leader = getLeader();
        final int partyColor=(leader!=null && leader.getId()==pc.getId())?2:1;
        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_Party.nativeNameColorWriter(pc.getId(),partyColor),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance member : getMembers()) {
            if(member == null || pc.getId() == member.getId())
                continue;

            if (leader != null && leader.getId() == pc.getId()) {
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,member);
            } else {
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,member);
            }
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
                    GetTacticalSignId(member.getId())));
        }
        broadcastRaw(new SC_PARTY_SYNC_PERIODIC_INFO(list).encode());
    }

    public void handshakePartyMemberStatus(L1PcInstance pc1, L1PcInstance pc2) {
        if (!ok(pc1) || !ok(pc2)) return;
        SC_PARTY_MEMBER_STATUS.sendFrom(pc2, pc1, GetTacticalSignId(pc1.getId()), true);
        SC_PARTY_MEMBER_STATUS.sendFrom(pc1, pc2, GetTacticalSignId(pc2.getId()), true);
    }

    public void refreshPartyMemberStatus(L1PcInstance pc) {
        if (!ok(pc) || !isMember(pc)) return;
        byte[] data = SC_PARTY_MEMBER_STATUS
                .from(pc, GetTacticalSignId(pc.getId()), true)
                .encode();
        broadcastRaw(data);
    }

    /** The ordinary L1 party disbands on leader departure; arena teams elect
     * a replacement. Keep the two existing gameplay policies distinct. */
    protected boolean dismissOnMasterLeave() { return true; }

    private L1PcInstance[] Dismiss(L1PcInstance departing) {
        // Only called with the party monitor held; exactly one transition.
        final L1PcInstance[] members = getMembers();
        m_Dismissed = true;
        ResetLootIndex();
        Arrays.fill(m_aOrderedMember, 0, m_nMemberCount, 0L);
        Arrays.fill(m_aTacticalSign, 0, m_nMemberCount, 0);
        Arrays.fill(m_aLastFullListSentAt, 0, m_nMemberCount, 0L);
        m_nMemberCount = 0;
        m_MasterSid = 0L;
        for (L1PcInstance member : members) member.compareAndSetParty(this, null);
        // Logout may already have unpublished the calling character.
        departing.compareAndSetParty(this, null);
        try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_ServerMessage.nativeWriter(418), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance member : members) {
                if (member.getParty() != null) continue;
                try {
                    clearPartyUIForLeaver(member);
                    if (ok(member)) l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared, member);
                } catch (RuntimeException failure) {
                    _log.log(java.util.logging.Level.WARNING, "Party dismissal notification failed", failure);
                }
            }
        } catch (RuntimeException failure) {
            _log.log(java.util.logging.Level.WARNING, "Party dismissal packet failed", failure);
        }
        return members;
    }

    public synchronized void passLeader(L1PcInstance pc) {
        final int index = FindMemberIndex(pc);
        if (m_Dismissed || index < 0 || ResolveUser(m_aOrderedMember[index]) != pc) return;
        m_MasterSid = m_aOrderedMember[index];
        broadcastRaw(SC_PARTY_MEMBER_LIST_CHANGE.ofLeaderChange(pc.getName()).encode());
        refreshPartyList();
    }

    private void Withdraw(L1PcInstance pc, boolean kick) {
        L1PcInstance[] dismissed = null;
        synchronized (this) {
            final int index = FindMemberIndex(pc);
            if (m_Dismissed || index < 0) return;
            final boolean wasLeader = m_MasterSid == m_aOrderedMember[index];
            if (m_nMemberCount <= 2 || (!kick && wasLeader && dismissOnMasterLeave())) {
                dismissed = Dismiss(pc);
            } else {
                DeleteMemberSid(index);
                pc.compareAndSetParty(this, null);
                if (wasLeader) {
                    m_MasterSid = 0L;
                    for (int i = 0; i < m_nMemberCount; i++) {
                        if (ResolveUser(m_aOrderedMember[i]) != null) {
                            m_MasterSid = m_aOrderedMember[i];
                            break;
                        }
                    }
                }
                try {
                    NotifyRemoved(pc, wasLeader);
                    try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                            SC_ServerMessage.nativeWriter(420, pc.getName()),
                            Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                        for (L1PcInstance member : getMembers())
                            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared, member);
                        if (!kick && pc.getParty() == null)
                            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared, pc);
                    }
                    if (kick) sendKickMessage(pc);
                } catch (RuntimeException failure) {
                    _log.log(java.util.logging.Level.WARNING, "Party departure notification failed", failure);
                }
            }
            if (kick) {
                try { SC_ServerMessage.send(pc, 419); }
                catch (RuntimeException failure) {
                    _log.log(java.util.logging.Level.WARNING, "Party kick notification failed", failure);
                }
            }
        }
        if (dismissed == null) {
            CleanupDeparted(pc);
        } else {
            boolean found = false;
            for (L1PcInstance member : dismissed) {
                CleanupDeparted(member);
                if (member == pc) found = true;
            }
            if (!found) CleanupDeparted(pc);
        }
    }

    public void leaveMember(L1PcInstance pc) { Withdraw(pc, false); }

    public void kickMember(L1PcInstance pc) { Withdraw(pc, true); }

    public synchronized L1PcInstance[] getMembers() {
        final L1PcInstance[] result = new L1PcInstance[m_nMemberCount];
        int size = 0;
        for (int i = 0; i < m_nMemberCount; i++) {
            final L1PcInstance member = ResolveUser(m_aOrderedMember[i]);
            if (member != null) result[size++] = member;
        }
        return size == result.length ? result : Arrays.copyOf(result, size);
    }

    public synchronized int getNumOfMembers() {
        return m_nMemberCount;
    }

    private void sendKickMessage(L1PcInstance kickpc) {
        SC_ServerMessage.send(kickpc, 419);
    }

    protected void sendLeftMessage(L1PcInstance sendTo, L1PcInstance left) {
        SC_ServerMessage.send(sendTo, 420, left.getName());
    }

    public List<L1PcInstance> getList() {// 하딘 파티 멤버 리스트
        return List.of(getMembers());
    }

    public Stream<L1PcInstance> createMembersStream(){
        return Arrays.stream(getMembers());
    }

    public void broadcastRootMent(String message) {
        if(message==null)return;
        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_SystemMessage.nativeWriter(message),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:getMembers()){
                if(pc!=null && pc.RootMent1)
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
            }
        }
    }

    

    /** Same map/range/hate policy for both currency and item routing. */
    private boolean CheckRouteCond(L1Location origin, CContributeData snap, L1PcInstance member) {
        if (member == null || member.getParty() != this || member.isDead()
                || member.getMapId() != origin.getMapId()
                || member.getLocation().getTileLineDistance(origin) > Config.PARTY_LOOT_RANGE) return false;
        if (Config.PARTY_LOOT_ELIGIBILITY != 0) return true;
        if (snap == null) return false;
        return snap.IsRouteContributor(member.getId());
    }

    private boolean CheckRouteAdenaCond(L1Location origin, CContributeData snap, L1PcInstance member) {
        return CheckRouteCond(origin, snap, member);
    }

    public boolean Route(L1NpcInstance npc, L1Inventory source, L1ItemInstance item,
            CContributeData snap, L1PcInstance primaryOwner) {
        return npc != null && Route(npc.getLocation(), source, item, snap, primaryOwner, null);
    }

    /** Queued drops retain the original party, roster and death position. */
    public boolean Route(L1Location origin, L1Inventory source, L1ItemInstance item,
            CContributeData snap, L1PcInstance primaryOwner, L1PcInstance[] admittedMembers) {
        if (!Config.PARTY_LOOT_ENABLE || primaryOwner == null || primaryOwner.getParty() != this
                || origin == null || source == null || item == null) return false;
        final CWorld world = L1World.getInstance().getWorldExecutor();
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

    private void RouteAdena(CWorld world, L1Location origin, L1Inventory source, L1ItemInstance item,
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
                    _log.log(java.util.logging.Level.WARNING, "Party adena notification failed", e);
                }
            }
        }
        RecoverRemainder(world, origin, source, item);
    }

    private void RouteItem(CWorld world, L1Location origin, L1Inventory source, L1ItemInstance item,
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
                try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                        SC_SystemMessage.nativeWriter(msg), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                    for (int i = 0; i < size; i++) {
                        final L1PcInstance member = members[i];
                        if (member.getParty() == this && member.RootMent1)
                            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared, member);
                    }
                }
            } catch (RuntimeException e) {
                _log.log(java.util.logging.Level.WARNING, "Party item notification failed", e);
            }
        }
        RecoverRemainder(world, origin, source, item);
    }

    private void RecoverRemainder(CWorld world, L1Location origin, L1Inventory source, L1ItemInstance item) {
        if (source.getItem(item.getId()) != item || item.getCount() <= 0) return;
        if (!world.RecoverDropRemainder(origin, source, item)) {
            _log.warning("Party drop ground recovery rejected: item=" + item.getId()
                    + ", count=" + item.getCount());
        }
    }

    // ─────────────────────────────────────────────
    // 표식 — 0x0153 전용 NOTI + 상태 갱신
    // ─────────────────────────────────────────────
    public synchronized void setPartyMark(L1PcInstance target, int markId) {
        final int index = FindMemberIndex(target);
        if (m_Dismissed || index < 0) return;
        m_aTacticalSign[index] = markId;
        broadcastRaw(new SC_PARTY_MEMBER_MARK_CHANGE_NOTI(target.getId(), markId).encode());
        refreshPartyMemberStatus(target);
    }
}