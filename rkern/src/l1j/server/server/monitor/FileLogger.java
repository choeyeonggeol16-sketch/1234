package l1j.server.server.monitor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.JTextArea;

import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.swing.chocco;

public class FileLogger implements Logger {

	private static final int TRIGGER_FLUSH_COUNT = 1000;
	private static final int MAX_LOG_ENTRIES = 65536; // 1,000줄 도달 시 저장


	private static class LogEntry {
		final String fileName;
		final String logText;
        final long enqueueNanos;

		LogEntry(String fileName, String logText) {
			this.fileName = fileName;
			this.logText = logText;
            this.enqueueNanos = System.nanoTime();
		}
	}

	private final Queue<LogEntry> _logQueue = new LinkedBlockingQueue<>(MAX_LOG_ENTRIES);
    private final AtomicLong rejectedLogs = new AtomicLong();
    private final AtomicInteger peakPendingLogs = new AtomicInteger();
    private final AtomicLong longestQueueWaitNanos = new AtomicLong();
    private final AtomicLong longestFlushNanos = new AtomicLong();
    private final AtomicLong failedFlushes = new AtomicLong();

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	public FileLogger() {}

	private void enqueueLog(String fileName, String msg) {
        if (msg == null || msg.isEmpty()) return;
        final LogEntry entry = new LogEntry(fileName, msg);
        if (!_logQueue.offer(entry)) {
            // Never do filesystem I/O on an NPC, CIO or game packet caller.
            // Under saturation the durable audit contract is fail-closed:
            // the caller is informed, never given a false success response.
            LoggerInstance.getInstance().triggerFlush();
            if (!_logQueue.offer(entry)) {
                rejectedLogs.incrementAndGet();
                throw new IllegalStateException("Audit log queue saturated (record NOT persisted): " + fileName);
            }
        }
        final int pending = _logQueue.size();
        peakPendingLogs.accumulateAndGet(pending, Math::max);
        if (pending >= TRIGGER_FLUSH_COUNT) {
            LoggerInstance.getInstance().triggerFlush();
        }
    }

	// ==================== 로그 적재 (UI 즉시 반영 & 1000줄 카운팅) ====================

	public void addShop(String itemName, int count, long price, String npc, String pc) {
		String msg = String.format("[%s] 엔피씨 : %s, 캐릭명 : %s, 아이템 : %s, 갯수 : %d, 가격 : %d\r\n", getLocalTime(), npc, pc, itemName, count, price);
		appendUi(chocco.txtShop, msg);
		enqueueLog("NPC상점.txt", msg);
	}

	public void addChat(ChatType type, L1PcInstance pc, String msg) {
		if (pc == null) return;
		String log = "";
		switch (type) {
		case Clan:
			log = String.format("%s\t혈맹(%s)\t[%s]\t%s\r\n", getLocalTime(), pc.getClanname(), pc.getName(), msg);
			appendUi(chocco.txtClanChat, log);
			break;
		case Global:
		case Normal:
		case Shouting:
			log = String.format("%s\t전체\t[%s]\t%s\r\n", getLocalTime(), pc.getName(), msg);
			appendUi(chocco.txtGlobalChat, log);
			break;
		case Alliance:
		case Guardian:
			log = String.format("%s\t혈맹\t[%s]\t%s\r\n", getLocalTime(), pc.getName(), msg);
			appendUi(chocco.txtClanChat, log);
			break;
		case Party:
		case Group:
			log = String.format("%s\t파티\t[%s]\t%s\r\n", getLocalTime(), pc.getName(), msg);
			appendUi(chocco.txtPartyChat, log);
			break;
		}
		enqueueLog("채팅.txt", log);
	}

	public void addWhisper(L1PcInstance pcfrom, L1PcInstance pcto, String msg) {
		if (pcfrom == null || pcto == null) return;
		String log = String.format("%s\t귓말\t[%s] -> [%s]\t%s\r\n", getLocalTime(), pcfrom.getName(), pcto.getName(), msg);
		appendUi(chocco.txtWhisper, log);
		enqueueLog("채팅.txt", log);
	}

	public void addCommand(String msg) {
		msg = String.format("%s\t%s\r\n", getLocalTime(), msg);
		enqueueLog("명령어.txt", msg);
	}

	public void addConnection(String msg) {
		msg = String.format("%s\t%s\r\n", getLocalTime(), msg);
		enqueueLog("로그인.txt", msg);
	}

	public void addError(String msg) {
		msg = String.format("%s\t%s\r\n", getLocalTime(), msg);
		enqueueLog("에러로그.txt", msg);
	}

	public void addEnchant(L1PcInstance pc, L1ItemInstance item, boolean success) {
		if (pc == null || item == null) return;
		String msg = String.format("%s\t%s:[%s]\t%s\t%s\r\n", getLocalTime(), pc.getAccountName(), pc.getName(), (success ? "성공" : "실패"), getFormatItemName(item, 1));
		appendUi(chocco.txtEnchant, msg);
		enqueueLog("인챈트.txt", msg);
	}

	public void addTrade(boolean success, L1PcInstance pcfrom, L1PcInstance pcto, L1ItemInstance item, int count) {
		if (pcfrom == null || pcto == null || item == null) return;
		String msg = String.format("%s\t%s\t%s:[%s]\t%s\t%s:[%s]\r\n", getLocalTime(), (success ? "OO완료OO" : "XX취소XX"), pcfrom.getAccountName(), pcfrom.getName(), getFormatItemName(item, count), pcto.getAccountName(), pcto.getName());
		appendUi(chocco.txtTrade, msg);
		enqueueLog("교환,시장.txt", msg);
	}

	public void 수표(boolean success, L1PcInstance pcfrom, L1PcInstance pcto, L1ItemInstance item, int count) {
		if (pcfrom == null || pcto == null || item == null) return;
		String msg = String.format("%s\t%s\t%s:[%s]\t%s\t%s:[%s]\r\n", getLocalTime(), (success ? "OO완료OO" : "XX취소XX"), pcfrom.getAccountName(), pcfrom.getName(), getFormatItemName(item, count), pcto.getAccountName(), pcto.getName());
		appendUi(chocco.txtTrade, msg);
		enqueueLog("수표거래.txt", msg);
	}

	public void 개인상점구매(boolean success, L1PcInstance pcfrom, L1PcInstance pcto, L1ItemInstance item, int count) {
		if (pcfrom == null || pcto == null || item == null) return;
		String msg = String.format("%s\t%s\t%s:[%s]\t%s\t%s:[%s]\r\n", getLocalTime(), (success ? "상점구매" : "상점취소"), pcfrom.getAccountName(), pcfrom.getName(), getFormatItemName(item, count), pcto.getAccountName(), pcto.getName());
		appendUi(chocco.txtTrade, msg);
		enqueueLog("교환,시장.txt", msg);
	}

	public void addWarehouse(WarehouseType type, boolean put, L1PcInstance pc, L1ItemInstance item, int count) {
		if (pc == null || item == null) return;
		String msg = String.format("%s\t창고:%s\t%s:[%s]\t%s\r\n", getLocalTime(), (put ? "맡기기" : "찾기"), pc.getAccountName(), pc.getName(), getFormatItemName(item, count));
		appendUi(chocco.txtWarehouse, msg);
		enqueueLog("창고.txt", msg);
	}

	public void addItemAction(ItemActionType type, L1PcInstance pc, L1ItemInstance item, int count) {
		if (pc == null || item == null) return;
		String locName = MapsTable.getInstance().getMapName(pc.getMapId());
		String msg = String.format("%s\t%s\t%s:[%s]\t%s\t[%s]\r\n", getLocalTime(), type.name(), pc.getAccountName(), pc.getName(), getFormatItemName(item, count), locName);
		if (type == ItemActionType.Pickup) {
			appendUi(chocco.txtPickup, msg);
		}
		enqueueLog("아이템로그.txt", msg);
	}

	public void addLevel(L1PcInstance pc, int level) {
		if (pc == null) return;
		String msg = String.format("%s\t%s:[%s]\tLevelUp %d\r\n", getLocalTime(), pc.getAccountName(), pc.getName(), level);
		enqueueLog("레벨업.txt", msg);
	}

	public void addCmd(String s) {
		enqueueLog("CMD.txt", s);
	}

	public void addAll(String msg) {}

	// ==================== 1,000줄 도달 시 파일 쓰기 실행 ====================

	public int pendingLogs() {
		return _logQueue.size();
	}

    /** Bounded queue observability. Durations include the oldest buffered entry. */
    public long rejectedLogs() { return rejectedLogs.get(); }
    public int peakPendingLogs() { return peakPendingLogs.get(); }
    public long longestQueueWaitMillis() {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(longestQueueWaitNanos.get());
    }
    public long longestFlushMillis() {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(longestFlushNanos.get());
    }
    public long failedFlushes() { return failedFlushes.get(); }

    // Persist an entire batch before removing any entry. On failure the queue is
    // retained for retry (at-least-once; a partial disk write may be repeated).
    private volatile boolean lastFlushSucceeded = true;

    public boolean lastFlushSucceeded() { return lastFlushSucceeded; }

    public synchronized void flushImmediately() {
        final long flushStarted = System.nanoTime();
        try {
        flushQueuedEntries();
        } finally {
            longestFlushNanos.accumulateAndGet(System.nanoTime() - flushStarted, Math::max);
        }
    }

    private void flushQueuedEntries() {
        if (_logQueue.isEmpty()) { lastFlushSucceeded = true; return; }
        final String today = LocalDateTime.now().format(DATE_FORMATTER);
        final File dir = new File("logDB/" + today);
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            lastFlushSucceeded = false;
            failedFlushes.incrementAndGet();
            System.err.println("[AUDIT-LOG] cannot create directory: " + dir);
            return;
        }
        final ArrayList<LogEntry> batch = new ArrayList<>(1024);
        final HashMap<String, BufferedWriter> writers = new HashMap<>();
        while (!_logQueue.isEmpty()) {
            batch.clear();
            for (LogEntry entry : _logQueue) {
                batch.add(entry);
                if (batch.size() >= 1024) break;
            }
            if (batch.isEmpty()) break;
            longestQueueWaitNanos.accumulateAndGet(
                    Math.max(0L, System.nanoTime() - batch.get(0).enqueueNanos), Math::max);
            IOException error = null;
            try {
                for (LogEntry entry : batch) {
                    BufferedWriter out = writers.get(entry.fileName);
                    if (out == null) {
                        out = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(new File(dir, entry.fileName), true), StandardCharsets.UTF_8));
                        writers.put(entry.fileName, out);
                    }
                    out.write(entry.logText);
                }
                for (BufferedWriter out : writers.values()) out.flush();
            } catch (IOException e) {
                error = e;
            } finally {
                for (BufferedWriter out : writers.values()) {
                    try { out.close(); }
                    catch (IOException e) { if (error == null) error = e; }
                }
                writers.clear();
            }
            if (error != null) {
                lastFlushSucceeded = false;
                failedFlushes.incrementAndGet();
                System.err.println("[AUDIT-LOG] write/close failed, retained " + _logQueue.size()
                        + " records for retry: " + error);
                return;
            }
            // Producers only append, so the front of the FIFO still represents
            // this persisted batch; remove after flush and close succeed.
            for (int i = 0; i < batch.size(); i++) _logQueue.poll();
        }
        lastFlushSucceeded = true;
    }

    private static void appendUi(JTextArea area, String msg) {
        l1j.server.swing.ManagerLogBuffer.append(area, msg);
    }

	public String getLocalTime() {
		return LocalDateTime.now().format(TIME_FORMATTER);
	}

	public String getFormatItemName(L1ItemInstance item, int count) {
		StringBuilder sb = new StringBuilder(128);
		sb.append("[").append(item.getId()).append("]");
		if (item.getEnchantLevel() > 0)
			sb.append("+").append(item.getEnchantLevel());
		else if (item.getEnchantLevel() < 0)
			sb.append(item.getEnchantLevel());
		sb.append(item.getName());
		if (item.isStackable())
			sb.append("(").append(count).append(")");
		return sb.toString();
	}
}