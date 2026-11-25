package l1j.server.server.Controller;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CParty;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_Pledge;

import l1j.server.server.utils.eKind;

public final class AuraController implements Runnable {

    private static final Logger LOG = Logger.getLogger(AuraController.class.getName());
    private static final AuraController INSTANCE = new AuraController();

    private static final int TICK_MS = 2000;
    private static final int RANGE = 18;

    private final Set<Integer> trackedCrownIds = ConcurrentHashMap.newKeySet(512);

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    // Avoid queuing redundant aura scans while a prior tick is pending/running.
    private final AtomicBoolean queued = new AtomicBoolean(false);
    private volatile boolean stopped = true;
    private volatile ScheduledFuture<?> ticker;
    private volatile int generation;

    public static AuraController getInstance() {
        return INSTANCE;
    }

    private AuraController() {}

    public synchronized void start() {
        if (started.get()) return;
        stopped = false;
        final int epoch = ++generation;
        // Reuse one worker callback for this start generation; an older callback
        // cannot change the new generation's world state after stop/restart.
        final Runnable work = () -> {
            try {
                if (!stopped && generation == epoch) run();
            } finally {
                queued.set(false);
            }
        };
        // Timer only signals admission; AOI/packet work stays on instant workers.
        final ScheduledFuture<?> future = ThreadPool.getInstance().scheduleWithFixedDelay(() -> {
            if (stopped || generation != epoch || !queued.compareAndSet(false, true)) return;
            try {
                ThreadPool.getInstance().execute(work);
            } catch (RejectedExecutionException rejected) {
                queued.set(false);
                // Keep retrying on subsequent ticks; never run AOI on the timer.
                LOG.log(Level.WARNING, "Aura tick admission rejected", rejected);
            }
        }, 0L, TICK_MS);
        if (future == null) {
            stopped = true;
            ++generation;
            throw new RejectedExecutionException("Aura recurring tick admission rejected");
        }
        ticker = future;
        started.set(true);
    }

    public synchronized void stop() {
        stopped = true;
        ++generation;
        final ScheduledFuture<?> future = ticker;
        ticker = null;
        if (future != null) future.cancel(false);
        started.set(false);
    }

    public void addMember(L1PcInstance pc) {
        if (pc == null) return;
        trackedCrownIds.add(pc.getId());
    }

    public void removeMember(L1PcInstance pc) {
        if (pc == null) return;
        trackedCrownIds.remove(pc.getId());
        safeEnd(pc);
    }

    public boolean getMember(L1PcInstance pc) {
        return pc != null && trackedCrownIds.contains(pc.getId());
    }

    @Override
    public void run() {
        if (stopped) return;

        if (!running.compareAndSet(false, true)) return;

        try {
            tickAura();
            tickClanBuff();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "AuraController tick error", t);
        } finally {
            running.set(false);
        }
    }

    private void tickAura() {
        if (trackedCrownIds.isEmpty()) return;

        final L1World world = L1World.getInstance();

        for (Iterator<Integer> it = trackedCrownIds.iterator(); it.hasNext(); ) {
            final int crownId = it.next();

            final L1PcInstance crown = findPc(world, crownId);
            if (!isValidPc(crown)) {
                it.remove();
                continue;
            }

            final CParty party = crown.getParty();
            if (party == null || party.getNumOfMembers() < 2) {
                if (crown.getPbavatar()) safeEnd(crown);
                continue;
            }

            final L1PcInstance[] members = party.getMembers();
            if (members == null || members.length == 0) {
                if (crown.getPbavatar()) safeEnd(crown);
                continue;
            }

            final int cx = crown.getX();
            final int cy = crown.getY();
            final int cmap = crown.getMapId();

            for (L1PcInstance m : members) {
                if (!isValidPc(m)) continue;

                final boolean shouldHaveAura = shouldHaveAura(crown, party, m, cx, cy, cmap);

                if (shouldHaveAura) {
                    ensureAuraOn(m);
                } else {
                    if (m.getPbavatar()) safeEnd(m);
                }
            }
        }
    }

    private static L1PcInstance findPc(L1World world, int objId) {
        try {
            return (L1PcInstance) world.findObject(objId);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean shouldHaveAura(L1PcInstance crown, CParty party, L1PcInstance m, int cx, int cy, int cmap) {
        if (m == null) return false;
        if (m.isDead()) return false;
        if (m.getParty() != party) return false;
        if (m.getMapId() != cmap) return false;

        final int dx = m.getX() - cx;
        if (dx > RANGE || dx < -RANGE) return false;

        final int dy = m.getY() - cy;
        if (dy > RANGE || dy < -RANGE) return false;

        // crown 본인도 포함해서 오라 주는 구조면 true 유지
        return true;
    }

    private void ensureAuraOn(L1PcInstance pc) {
        if (pc == null) return;

        // 이미 1st가 살아있으면 스탯/패킷 중복 적용 금지
        if (pc.hasSkillEffect(L1SkillId.BRAVE_AVATAR_1ST)) {
            if (!pc.getPbavatar()) pc.setPbavatar(true);
            return;
        }

        // 다른 단계 오라 제거
        if (pc.hasSkillEffect(L1SkillId.BRAVE_AVATAR_2ND)) pc.killSkillEffectTimer(L1SkillId.BRAVE_AVATAR_2ND);
        if (pc.hasSkillEffect(L1SkillId.BRAVE_AVATAR_3RD)) pc.killSkillEffectTimer(L1SkillId.BRAVE_AVATAR_3RD);

        pc.setSkillEffect(L1SkillId.BRAVE_AVATAR_1ST, -1);
        SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.아우라, 3432, true);

        pc.AddMDefend(10);
        pc.AddSpecialResistance(eKind.ALL, 2);

        pc.AddStatBonusStr((byte) 1);
        pc.AddStatBonusDex((byte) 1);
        pc.AddStatBonusInt((byte) 1);

        pc.resetBaseMr();
        pc.setPbavatar(true);

        SC_SPMR.send(pc);
        SC_OwnCharStatus2.send(pc);
    }

    public void brave_end(L1PcInstance pc) {
        safeEnd(pc);
    }

    public void allbrave_end(L1PcInstance pc) {
        if (pc == null) return;
        final CParty party = pc.getParty();
        if (party == null) return;
        for (L1PcInstance m : party.getMembers()) {
            safeEnd(m);
        }
    }

    private void safeEnd(L1PcInstance pc) {
        if (pc == null) return;

        boolean changed = false;

        if (pc.hasSkillEffect(L1SkillId.BRAVE_AVATAR_1ST)) {
            pc.killSkillEffectTimer(L1SkillId.BRAVE_AVATAR_1ST);
            changed = true;
        }
        if (pc.hasSkillEffect(L1SkillId.BRAVE_AVATAR_2ND)) {
            pc.killSkillEffectTimer(L1SkillId.BRAVE_AVATAR_2ND);
            changed = true;
        }
        if (pc.hasSkillEffect(L1SkillId.BRAVE_AVATAR_3RD)) {
            pc.killSkillEffectTimer(L1SkillId.BRAVE_AVATAR_3RD);
            changed = true;
        }

        // 이미 꺼진 상태면 아무 것도 하지 않음
        if (!changed && !pc.getPbavatar()) return;

        pc.AddMDefend(-10);
        pc.AddSpecialResistance(eKind.ALL, -2);

        pc.AddStatBonusStr((byte) -1);
        pc.AddStatBonusDex((byte) -1);
        pc.AddStatBonusInt((byte) -1);

        pc.setPbavatar(false);

        SC_SPMR.send(pc);
        SC_OwnCharStatus2.send(pc);
        SC_InventoryIcon.icoEnd(pc, L1SkillId.아우라);
    }

    private void tickClanBuff() {
        try {
            for (L1Clan clan : L1World.getInstance().getAllClans()) {
                if (clan == null) continue;
                synchronized (clan) {
                if (l1j.server.server.storage.CacheD.getInstance().hasPendingOwnerApply(clan.getClanId())) continue;

                final int bless = clan.getBless();
                if (bless == 0) continue;

                final int[] time = clan.getBuffTime();
                if (time == null || bless - 1 < 0 || bless - 1 >= time.length) continue;

                clan.setBuffTime(bless - 1, time[bless - 1] - 1);

                if (clan.getBuffTime()[bless - 1] <= 0) {
                    // Expiration is game state, not contingent on a notification.
                    // Preserve member notice -> skill removal in the success case.
                    try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                            SC_Pledge.nativeBlessWriter(clan, bless),
                            Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                        for (L1PcInstance member : clan.getOnlineClanMember()) {
                            if (member == null) continue;
                            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(shared, member);
                            try { member.removeSkillEffect(bless + 504); }
                            catch (RuntimeException skillFailure) {
                                LOG.log(Level.SEVERE, "Clan blessing expiry failed for member=" + member.getId(), skillFailure);
                            }
                        }
                    } catch (RuntimeException noticeFailure) {
                        LOG.log(Level.WARNING, "Clan bless expiry notice failed", noticeFailure);
                        // Do not leave the elapsed clan blessing marked active.
                    }
                    clan.setBless(0);
                    ClanTable.getInstance().updateBless(clan.getClanId(), 0);
                }
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Clanbuff tick error", t);
        }
    }

    private static boolean isValidPc(L1PcInstance pc) {
        if (pc == null) return false;
        try {
            if (pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return false;
        } catch (Throwable t) {
            return false;
        }
        return true;
    }
}
