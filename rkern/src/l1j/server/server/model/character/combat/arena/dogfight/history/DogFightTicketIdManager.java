package l1j.server.server.model.character.combat.arena.dogfight.history;

import l1j.server.server.datatables.CObjectDB;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import l1j.server.server.model.character.combat.arena.dogfight.DogFightSettings;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.executors.Updator;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;
import l1j.server.server.storage.sql.handler.Handler;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.IntRange;

public class DogFightTicketIdManager {
    private static final Logger _log = Logger.getLogger(DogFightTicketIdManager.class.getName());

    public static final int START_TICKET_ITEM_ID = 12000000;
    /** 회차당 할당되는 티켓 ID 수(=코너 수). 실제 게임 설정과 일치시킬 것 */
    private static final int IDS_PER_ROUND = 2;
    /** 아이템이 저장될 수 있는 모든 테이블 — 서버 스키마에 맞게 조정 */
    private static final String[] ITEM_TABLES = {
            "character_items", "character_warehouse", "clan_warehouse", "character_special_warehouse", "character_elf_warehouse", "character_package_warehouse"
    };

    private static volatile DogFightTicketIdManager m_instance;

    public static DogFightTicketIdManager getInstance() {
        if (m_instance == null) {
            synchronized (DogFightTicketIdManager.class) {
                if (m_instance == null) {
                    m_instance = new DogFightTicketIdManager();
                }
            }
        }
        return m_instance;
    }
  
    private int m_item_id;
    private int m_prev_item_id;
    /** 부팅 완료 시점의 m_item_id 스냅샷 — 이번 세션 생성분 복구용 */
    private volatile int m_boot_item_id;

    private DogFightTicketIdManager() {
        do_load();
    }

    private void do_load() {
        Selector.exec("select * from dogfight_item_id", new FullSelectorHandler() {
            @Override
            public void result(ResultSet rs) throws Exception {
                if (rs.next()) {
                    m_item_id = rs.getInt("last_item_id");
                } else {
                    m_item_id = START_TICKET_ITEM_ID;
                }
            }
        });

        if (m_item_id == START_TICKET_ITEM_ID) {
            do_update();
        } else {
            create_live_templates();
        }
        m_prev_item_id = m_item_id - 1;
        m_boot_item_id = m_item_id; // ★ 부팅 시점 스냅샷
    }

    /**
     * 인벤/창고 등에 실제 존재하는 티켓 id만 조회해서 템플릿 생성.
     * 역대 누적과 무관하게 "현재 유통 중인 티켓 수"에만 비례.
     */
    private void create_live_templates() {
        final L1Item base = CObjectDB.LoadItem().getTemplate(DogFightSettings.TICKET_ITEM_ID);
        if (base == null) {
            _log.warning("[DogFight] 티켓 베이스 템플릿 없음. id=" + DogFightSettings.TICKET_ITEM_ID);
            return;
        }
        final Set<Integer> liveIds = new HashSet<>();
        collect_live_ids(liveIds);
        for (int id : liveIds) {
            CObjectDB.LoadItem().create_dogfight_ticket(base, id, create_ticket_name(id));
        }
    }
    
    public synchronized L1Item ensure_template(int item_id){
        // 1. 이미 템플릿이 있으면 그대로 반환
        L1Item existing = CObjectDB.LoadItem().getTemplate(item_id);
        if(existing != null){
            return existing;
        }

        // 2. 티켓 대역인지 확인 — 아니면 절대 개입하지 않음
        if(!is_ticket_item_id(item_id)){
            _log.warning("[DogFight] 티켓 대역이 아닌 ID의 ensure 요청: " + item_id);
            return null;
        }

        // 3. ★ 무한생성 방지 — DB에 실제로 이 티켓을 보유한 로우가 있는지 확인
        //    (존재하지 않는 ID로 템플릿 몰래 만들어주면 가짜 티켓이 생기는 꼴)
        final boolean[] exists = {false};
        boolean queryOk = false;
        for (final String table : ITEM_TABLES) {
            try {
                Selector.exec("select count(*) as cnt from " + table
                        + " where item_id = " + item_id + " limit 1", new FullSelectorHandler() {
                    @Override
                    public void result(ResultSet rs) throws Exception {
                        if (rs.next() && rs.getInt("cnt") > 0) {
                            exists[0] = true;
                        }
                    }
                });
                if (exists[0]) {
                    queryOk = true;
                    break;
                }
            } catch (Exception e) {
                _log.warning("[DogFight] ensure 조회 실패(" + table + "): " + e.getMessage());
                queryOk = true; // 조회 실패시엔 생성 허용 (보수적으로 복구 우선)
                break;
            }
        }
        if (!exists[0]) {
            // DB 어디에도 없음 → 이미 유실된/가짜 ID → 템플릿 만들어주지 않음
            _log.warning("[DogFight] DB에 존재하지 않는 티켓 ID 요청 — 복구 거부: " + item_id);
            return null;
        }

        // 베이스 템플릿 확인
        L1Item base = CObjectDB.LoadItem().getTemplate(DogFightSettings.TICKET_ITEM_ID);
        if (base == null) {
            _log.warning("[DogFight] 베이스 템플릿 없음 — 즉석 복구 불가. id=" + item_id);
            return null;
        }

        // 즉석 생성 + 등록
        CObjectDB.LoadItem().create_dogfight_ticket(base, item_id, create_ticket_name(item_id));
        _log.info("[DogFight] 티켓 템플릿 즉석 복구: id=" + item_id);
        return CObjectDB.LoadItem().getTemplate(item_id);
    }

    /** 유통 중인 티켓 id를 찾아 set에 수집 */
    private void collect_live_ids(Set<Integer> out) {
        for (final String table : ITEM_TABLES) {
            try {
                Selector.exec("select distinct item_id from " + table
                        + " where item_id >= " + START_TICKET_ITEM_ID
                        + " and item_id < " + m_item_id, new FullSelectorHandler() {
                    @Override
                    public void result(ResultSet rs) throws Exception {
                        while (rs.next()) {
                            out.add(rs.getInt("item_id"));
                        }
                    }
                });
            } catch (Exception e) {
                _log.warning("[DogFight] " + table + " 조회 실패: " + e.getMessage());
            }
        }
    }

    /** 원본은 (START - i + 2) / 2 → 0/음수 회차 나오는 부호 버그 */
    private String create_ticket_name(int item_id) {
        int round = ((item_id - START_TICKET_ITEM_ID) / IDS_PER_ROUND) + 1;
        return String.format("#%d회차 투견티켓", round);
    }

    /**
     * ItemTable.reload() 후 템플릿 복구.
     * 1) 이번 세션 생성분 (부팅~현재): 진행 중 게임 티켓은 DB 저장 지연 가능하므로 ID 범위로 복구
     * 2) 이전 세션 유통분: DB 조회해서 복구
     * → 전체 히스토리 재생성이 아니라 두 횟수의 합만큼만 생성.
     */
    public void update_item_template() {
        final L1Item base = CObjectDB.LoadItem().getTemplate(DogFightSettings.TICKET_ITEM_ID);
        if (base == null) {
            _log.warning("[DogFight] 티켓 베이스 템플릿 없음. id=" + DogFightSettings.TICKET_ITEM_ID);
            return;
        }

        final Set<Integer> toRestore = new HashSet<>();
        // 1이번 세션 분 — DB 저장 지연 때문에 ID 범위로 복구
        for (int i = m_boot_item_id; i < m_item_id; ++i) {
            toRestore.add(i);
        }
        // 2과거 세션 유통분 — DB 조회
        collect_live_ids(toRestore);

        for (int id : toRestore) {
            CObjectDB.LoadItem().create_dogfight_ticket(base, id, create_ticket_name(id));
        }
        _log.info("[DogFight] 티켓 템플릿 복구: 세션 생성분 " + Math.max(0, m_item_id - m_boot_item_id)
                + "개 + 유통분, 총 " + toRestore.size() + "개");
    }

    public synchronized int next_id() {
        int id = m_item_id++;
        m_prev_item_id = m_item_id - 1;
        do_update();
        return id;
    }

    public synchronized int[] next_ids(int amount) {
        int[] ids = new int[amount];
        for (int i = 0; i < amount; ++i) {
            ids[i] = m_item_id++;
        }
        m_prev_item_id = m_item_id - 1;
        do_update();
        return ids;
    }

    public int get_current_id() {
        return m_item_id;
    }

    public boolean is_ticket_item_id(int item_id) {
        if (m_prev_item_id < START_TICKET_ITEM_ID) {
            return false;
        }
        return IntRange.includes(item_id, START_TICKET_ITEM_ID, m_prev_item_id);
    }

    private void do_update() {
        Updator.exec("insert into dogfight_item_id set identity=?, last_date=?, last_item_id=? "
                + "on duplicate key update "
                + "last_date=?, last_item_id=?", new Handler() {
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                String local_time = CommonUtil.getLocalTime();
                int idx = 0;
                pstm.setInt(++idx, 0);
                pstm.setString(++idx, local_time);
                pstm.setInt(++idx, m_item_id);
                pstm.setString(++idx, local_time);
                pstm.setInt(++idx, m_item_id);
            }
        });
    }
}