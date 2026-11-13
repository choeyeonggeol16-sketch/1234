package l1j.server.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.management.ManagementFactory;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;

import com.sun.management.OperatingSystemMXBean;

import l1j.server.Config;
import l1j.server.Server;
import l1j.server.server.GameServer;
import l1j.server.server.Controller.LoginController;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.SystemUtil;


public class chocco extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final Color WIN_FACE = new Color(212, 208, 200);
    private static final Color WIN_TEXT = Color.BLACK;
    private static final Color LOG_BG = Color.BLACK;
    private static final Color LOG_FG = new Color(210, 210, 210);
    private static final Font FIXED_FONT = new Font("Monospaced", Font.PLAIN, 12);
    private static final Font UI_FONT = new Font("Dialog", Font.PLAIN, 12);
    private static final DateTimeFormatter SERVER_CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_LOG_LINES = 300;
    private static final Preferences WINDOW_PREFS = Preferences.userRoot().node("Software/PROJECT_L1/LINEAGE_SERVER");

    public static boolean inf;
    public static String[] save_text = new String[7];
    public static boolean serverstart;
    public static int check = 0;
    public static int x_size = 0;
    public static int count = 0;
    public static boolean _isManagerCommands = false;
    public static long cpu;

    private long serverStartAt;

    // 기존 코드/외부 참조 호환 필드.
    private ImageIcon start = new ImageIcon("data/img/start.jpg");
    private ImageIcon exit = new ImageIcon("data/img/exit.jpg");
    private ImageIcon down = new ImageIcon("data/img/down.jpg");
    private ImageIcon so = new ImageIcon("data/img/so.jpg");
    private ImageIcon in = new ImageIcon("data/img/in.jpg");

    private JButton buf, ex, info, info1, max, logsv, logcl, st, sv, updown, set;
    public static JLabel first, label2, stime;
    private JLabel label1, label4, lblThread, lblCPU, lblCPUProgressBack, lblCPUProgress;
    public static javax.swing.JLayeredPane layout1;
    private javax.swing.JLayeredPane layout2, layout3, layout4, layout5;
    private javax.swing.JInternalFrame iframe, iframe2, iframe3;
    private JTextField chat, name, exp, lawful, karma, adena, item, enweapon, enarmor, weightlimit, chatlvl, maxuser;
    private JTabbedPane tabframe, chocco;
    private JScrollPane spGlobalChat, spClanChat, spPartyChat, spWhisper, spShop, spTrade, spWarehouse, spEnchant, spPickup, spUserList;

    public static JTextArea txtGlobalChat, txtClanChat, txtPartyChat, txtWhisper, txtShop, txtTrade, txtWarehouse, txtEnchant, txtPickup;
    public static java.awt.List userlist, iplist;
    private javax.swing.JPopupMenu popmenu1, popmenu2;
    private JMenuItem menu1, menu2, menu3, menu4, menu5, menu6, menu7;

    public static JFrame setting;
    private JLabel exp1, lawful1, karma1, adena1, item1, enweapon1, enarmor1, weightlimit1, chatlvl1, maxuser1;

    private JFrame userFrame;
    private JTextArea statusArea;
    private ScheduledExecutorService monitorScheduler;

    public chocco() {
        initComponents();
        restoreWindowBounds();
        x_size = getHeight();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCloseRequest();
            }
        });

        startMonitoring();

        stMouseClicked();
    }

    private void initComponents() {
        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Lineage Server");
        setName("LineageServer");
        setResizable(true);
        setMinimumSize(new Dimension(640, 480));
        setSize(640, 480);

        getContentPane().setBackground(WIN_FACE);
        getContentPane().setLayout(new BorderLayout(2, 2));

        // -----------------------------------------------------------------
        // Compatibility objects from the old manager. They are no longer a
        // toolbar; menu entries call the same handlers instead.
        // -----------------------------------------------------------------
        layout1 = new javax.swing.JLayeredPane();
        layout2 = new javax.swing.JLayeredPane();
        layout3 = new javax.swing.JLayeredPane();
        layout4 = new javax.swing.JLayeredPane();
        layout5 = new javax.swing.JLayeredPane();
        iframe = new javax.swing.JInternalFrame();
        iframe2 = new javax.swing.JInternalFrame();
        iframe3 = new javax.swing.JInternalFrame();

        st = new JButton(start);
        ex = new JButton(exit);
        sv = new JButton();
        buf = new JButton();
        info = new JButton();
        info1 = new JButton();
        max = new JButton();
        logsv = new JButton();
        logcl = new JButton();
        updown = new JButton(down);
        lblCPUProgressBack = new JLabel();
        lblCPUProgress = new JLabel();

        setJMenuBar(buildMenuBar());

        // -----------------------------------------------------------------
        // Left: existing L1J logs, kept because they are genuinely useful.
        // Styled like an old fixed-font server console.
        // -----------------------------------------------------------------
        tabframe = new JTabbedPane();
        tabframe.setFont(UI_FONT);
        tabframe.setBackground(WIN_FACE);
        tabframe.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        spGlobalChat = addLogTab("전체", txtGlobalChat = createLogArea());
        spClanChat = addLogTab("혈맹", txtClanChat = createLogArea());
        spPartyChat = addLogTab("파티", txtPartyChat = createLogArea());
        spWhisper = addLogTab("귓속말", txtWhisper = createLogArea());
        spShop = addLogTab("NPC 상점", txtShop = createLogArea());
        spTrade = addLogTab("거래", txtTrade = createLogArea());
        spWarehouse = addLogTab("창고", txtWarehouse = createLogArea());
        spEnchant = addLogTab("인챈트", txtEnchant = createLogArea());
        spPickup = addLogTab("아이템", txtPickup = createLogArea());

        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBackground(WIN_FACE);
        logPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        logPanel.add(tabframe, BorderLayout.CENTER);

        JPanel statusPanel = buildStatusPanel();
        statusPanel.setPreferredSize(new Dimension(300, 0));

        JPanel center = new JPanel(new BorderLayout(3, 0));
        center.setBackground(WIN_FACE);
        center.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
        center.add(logPanel, BorderLayout.CENTER);
        center.add(statusPanel, BorderLayout.EAST);
        getContentPane().add(center, BorderLayout.CENTER);

        // -----------------------------------------------------------------
        // Bottom: God's Voice / whisper line. Empty name = world broadcast.
        // -----------------------------------------------------------------
        JPanel commandBar = new JPanel(new BorderLayout(3, 0));
        commandBar.setBackground(WIN_FACE);
        commandBar.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        name = new JTextField();
        name.setFont(UI_FONT);
        name.setToolTipText("캐릭터명 (비워두면 전체 공지)");
        name.setPreferredSize(new Dimension(95, 22));

        chat = new JTextField();
        chat.setFont(UI_FONT);
        chat.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent evt) {
                chatKeyPressed(evt);
            }
        });

        JLabel voice = new JLabel("전체 공지");
        voice.setFont(UI_FONT);
        voice.setPreferredSize(new Dimension(72, 22));

        stime = new JLabel("--:--", SwingConstants.RIGHT);
        stime.setFont(FIXED_FONT);
        stime.setPreferredSize(new Dimension(52, 22));

        commandBar.add(name, BorderLayout.WEST);

        JPanel voicePanel = new JPanel(new BorderLayout(3, 0));
        voicePanel.setBackground(WIN_FACE);
        voicePanel.add(voice, BorderLayout.WEST);
        voicePanel.add(chat, BorderLayout.CENTER);
        commandBar.add(voicePanel, BorderLayout.CENTER);
        commandBar.add(stime, BorderLayout.EAST);
        getContentPane().add(commandBar, BorderLayout.SOUTH);

        // Existing user/IP manager is retained, but moved behind Report > User.
        initUserManager();
        initServerSetting();
    }


    private void restoreWindowBounds() {
        int x = WINDOW_PREFS.getInt("posx", 0);
        int y = WINDOW_PREFS.getInt("posy", 0);
        int w = Math.max(640, WINDOW_PREFS.getInt("sizex", 640));
        int h = Math.max(480, WINDOW_PREFS.getInt("sizey", 480));
        setBounds(x, y, w, h);
    }

    private void saveWindowBounds() {
        WINDOW_PREFS.putInt("posx", getX());
        WINDOW_PREFS.putInt("posy", getY());
        WINDOW_PREFS.putInt("sizex", getWidth());
        WINDOW_PREFS.putInt("sizey", getHeight());
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.setFont(UI_FONT);

        JMenu file = menu("파일");
        addItem(file, "크래시 및 보고", () -> showSimpleReport("크래시 및 보고", "수동 크래시/보고 기능은 아직 연결되지 않았습니다."));
        file.addSeparator();
        addItem(file, "종료", this::onCloseRequest);

        JMenu report = menu("보고서");
        addItem(report, "메모리", () -> showSimpleReport("메모리", "사용 메모리 : " + SystemUtil.getUsedMemoryMB() + " MB"));
        addItem(report, "인스턴스", () -> showSimpleReport("인스턴스", "온라인 유저 : " + LoginController.getInstance().getOnlinePlayerCount() + "\n스레드 : " + Thread.activeCount()));
        JMenu creature = menu("크리처 분포");
        addItem(creature, "유저 분포", this::showUserManager);
        addDisabledItem(creature, "NPC 분포");
        report.add(creature);
        addDisabledItem(report, "아이템 분포");
        addDisabledItem(report, "공격형 몬스터 주변");
        addItem(report, "시간 분석", () -> showNetworkReport("시간 분석", l1j.server.server.model.network.core.CNetworkDiagnostics::timings));
        addItem(report, "시간 분석 초기화", () -> { l1j.server.server.model.network.core.CNetworkDiagnostics.resetTiming(); showSimpleReport("시간 분석", "완료 시간 통계를 초기화했습니다."); });
        addItem(report, "패킷 카운트", () -> showNetworkReport("패킷 카운트", l1j.server.server.model.network.core.CNetworkDiagnostics::packetCounts));
        addItem(report, "패킷 카운트 초기화", () -> { l1j.server.server.model.network.core.CNetworkDiagnostics.resetCounts(); showSimpleReport("패킷 카운트", "패킷 카운트를 초기화했습니다. 최근 기록은 유지됩니다."); });
        addDisabledItem(report, "프로파일 초기화");
        addItem(report, "프로파일", () -> showNetworkReport("프로파일", this::networkProfile));
        addDisabledItem(report, "테스트");
        addDisabledItem(report, "NPC 핑");
        addItem(report, "소켓", () -> showNetworkReport("소켓", this::networkSockets));
        addItem(report, "최근 패킷 128개", this::showRecentPackets);
        addItem(report, "유저", this::showUserManager);
        addItem(report, "참조·수명 상태", () -> showNetworkReport("참조·수명 상태", this::networkReferences));
        addItem(report, "전송량 초기화", () -> { l1j.server.server.model.network.core.CNetworkDiagnostics.resetTraffic(); showSimpleReport("전송률", "수신/송신 접수량 통계를 초기화했습니다."); });
        addItem(report, "전송률", () -> showNetworkReport("전송률", l1j.server.server.model.network.core.CNetworkDiagnostics::traffic));
        addItem(report, "서버 시간", () -> showSimpleReport("서버 시간", LocalTime.now().toString()));
        addDisabledItem(report, "공유 팩토리 보고");

        JMenu broadcast = menu("방송");
        addItem(broadcast, "서버 종료", () -> exMouseClicked(null));
        addItem(broadcast, "전체 공지", this::showGodsVoiceDialog);
        broadcast.addSeparator();
        addItem(broadcast, "이벤트 / 버프", () -> bufMouseClicked(null));

        JMenu setMenu = menu("설정");
        addDisabledItem(setMenu, "유저 공격 차단");
        addDisabledItem(setMenu, "시간");
        addDisabledItem(setMenu, "신규 접속 출력");
        addDisabledItem(setMenu, "HTML 캐시");
        addItem(setMenu, "접속 인원 제한", this::openServerSetting);
        addDisabledItem(setMenu, "유저 길찾기");
        addDisabledItem(setMenu, "월드 충돌");
        addDisabledItem(setMenu, "파티 매칭");
        setMenu.addSeparator();
        addItem(setMenu, "서버 설정", this::openServerSetting);

        JMenu log = menu("로그");
        addDisabledItem(log, "LogD 연결");
        log.addSeparator();
        addItem(log, "로컬 로그 지우기", () -> logclMouseClicked(null));

        JMenu reload = menu("리로드");
        addDisabledItem(reload, "이동체");
        addDisabledItem(reload, "패스노드");
        reload.addSeparator();
        addItem(reload, "매니저 명령", () -> openManagerCommands(getX() + 80, getY() + 80));

        JMenu help = menu("도움말");
        addItem(help, "정보...", () -> JOptionPane.showMessageDialog(
                this,
                "Lineage Server\nL1J Server Manager",
                "Lineage Server",
                JOptionPane.INFORMATION_MESSAGE));

        JMenu petition = menu("청원");
        addDisabledItem(petition, "연결");
        addDisabledItem(petition, "닫기");

        bar.add(file);
        bar.add(report);
        bar.add(broadcast);
        bar.add(setMenu);
        bar.add(log);
        bar.add(reload);
        bar.add(help);
        bar.add(petition);
        return bar;
    }

    private JMenu menu(String text) {
        JMenu m = new JMenu(text);
        m.setFont(UI_FONT);
        return m;
    }

    private void addItem(JMenu menu, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(UI_FONT);
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private void showNetworkReport(String title, java.util.function.Supplier<String> read) {
        new javax.swing.SwingWorker<String, Void>() {
            protected String doInBackground() { return read.get(); }
            protected void done() {
                try { showSimpleReport(title, get()); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); showSimpleReport(title, "조회가 중단되었습니다."); }
                catch (java.util.concurrent.ExecutionException failure) { showSimpleReport(title, "서버가 시작/종료 중이거나 조회할 수 없습니다: " + failure.getCause()); }
            }
        }.execute();
    }
    private void showRecentPackets() {
        String text = JOptionPane.showInputDialog(this, "소켓 보고서의 진단 id를 입력하세요.");
        if (text == null) return;
        try {
            long id = Long.parseLong(text.trim());
            showNetworkReport("최근 패킷 128개", () -> l1j.server.server.model.network.core.CNetworkDiagnostics.recent(id));
        } catch (NumberFormatException failure) { showSimpleReport("최근 패킷", "숫자 id를 입력하세요."); }
    }
    private String networkProfile() {
        StringBuilder text = new StringBuilder("Native 성능 샘플(availabilityFlags로 가용성 확인):\n")
                .append(l1j.server.Server.networkPerformance()).append("\nNative worker 상태:\n");
        long[] workers = l1j.server.Server.networkThreadDiagnostics();
        for (int offset = 0; offset + 2 < workers.length; offset += 3) {
            text.append("owner=").append(offset / 3).append(" callbacks=").append(workers[offset])
                    .append(" totalMs=").append(workers[offset + 1])
                    .append(" activeSinceMs=").append(workers[offset + 2]).append('\n');
        }
        return text.append(l1j.server.server.model.network.core.CNetworkDiagnostics.workers()).toString();
    }
    private String networkSockets() {
        StringBuilder text = new StringBuilder(l1j.server.server.model.network.core.CNetworkDiagnostics.connections());
        long[][] rows = l1j.server.Server.networkSocketDiagnostics();
        text.append("\nNative sockets=").append(rows.length)
                .append("\n[handle,number,socket,closing,terminal,packetLease,readPosted,writePosted,outputBytes,addressFamily,ipv4,port]\n")
                .append("outputBytes는 native 출력 큐만의 잔여량이며 Java 준비량/실행 전 예약량은 별도입니다.\n");
        for (int i = 0; i < Math.min(rows.length, 512); i++) text.append(java.util.Arrays.toString(rows[i])).append('\n');
        if (rows.length > 512) text.append("최대 512개 표시\n");
        return text.toString();
    }
    private String networkReferences() {
        return "Native 객체의 정확한 refcount를 노출하는 API는 없습니다. 아래는 실제 등록/대여/I/O 상태입니다.\n"
                + "[보유 번호 소켓,로그인 소켓,진입 계정,중복 진입 거절]\n"
                + java.util.Arrays.toString(l1j.server.Server.networkAccountRegistryDiagnostics())
                + "\n[송신 접수,실행,암호화 frame,평문 frame,정상종료,강제종료,거절,pending,owner오류]\n"
                + java.util.Arrays.toString(l1j.server.Server.networkSendDiagnostics()) + "\n" + networkSockets();
    }

    private void addDisabledItem(JMenu menu, String text) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(UI_FONT);
        item.setEnabled(false);
        menu.add(item);
    }

    private JScrollPane addLogTab(String title, JTextArea area) {
        JScrollPane sp = new JScrollPane(area);
        sp.getViewport().setBackground(LOG_BG);
        tabframe.addTab(title, sp);
        return sp;
    }

    private JPanel buildStatusPanel() {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBackground(WIN_FACE);
        outer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(BevelBorder.LOWERED),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)));

        JLabel title = new JLabel("Release-1(최고속), [서버상태]");
        title.setFont(FIXED_FONT);
        title.setForeground(WIN_TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        first = new JLabel("상태 : 준비");
        first.setFont(FIXED_FONT);
        first.setForeground(new Color(0, 0, 128));
        first.setAlignmentX(LEFT_ALIGNMENT);

        statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setFocusable(false);
        statusArea.setOpaque(false);
        statusArea.setFont(FIXED_FONT);
        statusArea.setForeground(WIN_TEXT);
        statusArea.setLineWrap(false);
        statusArea.setText(
                "IO버퍼풀크기: n/a\n"
                + "플레이 유저: 0\n"
                + "최대 유저: 0\n\n"
                + "가동 시간: 0일 00시간 00분 00초\n"
                + "서버 시간: --:--\n\n"
                + "슬립 카운트 : n/a\n"
                + "대기중 쓰기 : n/a, /8k (n/a)");
        statusArea.setAlignmentX(LEFT_ALIGNMENT);

        JPanel javaStats = new JPanel(new GridLayout(4, 1, 0, 1));
        javaStats.setBackground(WIN_FACE);
        javaStats.setAlignmentX(LEFT_ALIGNMENT);
        javaStats.setBorder(BorderFactory.createTitledBorder("서버 상태"));

        label1 = statLabel("접속자        :");
        label2 = statLabel(" 0명");
        label4 = statLabel("메모리        : 0 MB");
        lblThread = statLabel("스레드        : 0");
        lblCPU = statLabel("CPU          : 0%");

        JPanel connected = new JPanel(new BorderLayout());
        connected.setBackground(WIN_FACE);
        connected.add(label1, BorderLayout.WEST);
        connected.add(label2, BorderLayout.CENTER);

        javaStats.add(connected);
        javaStats.add(label4);
        javaStats.add(lblThread);
        javaStats.add(lblCPU);

        outer.add(title);
        outer.add(Box.createVerticalStrut(3));
        outer.add(first);
        outer.add(Box.createVerticalStrut(8));
        outer.add(statusArea);
        outer.add(Box.createVerticalGlue());
        outer.add(javaStats);
        return outer;
    }

    private JLabel statLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FIXED_FONT);
        label.setForeground(WIN_TEXT);
        return label;
    }

    private void initUserManager() {
        userlist = new java.awt.List();
        iplist = new java.awt.List();
        userlist.setFont(FIXED_FONT);
        iplist.setFont(FIXED_FONT);

        popmenu1 = new javax.swing.JPopupMenu();
        popmenu2 = new javax.swing.JPopupMenu();
        menu1 = new JMenuItem("강제종료");
        menu2 = new JMenuItem("아이피차단");
        menu3 = new JMenuItem("캐릭터정보");
        menu4 = new JMenuItem("아이피 삭제(List)");
        menu5 = new JMenuItem("아이피 삭제(dB, List)");
        menu6 = new JMenuItem("선물 주기");
        menu7 = new JMenuItem("귓속말");

        menu1.setIcon(in); menu1.addActionListener(e -> menu1ActionPerformed(e)); popmenu1.add(menu1);
        menu2.setIcon(in); menu2.addActionListener(e -> menu2ActionPerformed(e)); popmenu1.add(menu2);
        menu7.setIcon(in); menu7.addActionListener(e -> menu7ActionPerformed(e)); popmenu1.add(menu7);
        menu3.setIcon(in); menu3.addActionListener(e -> menu3ActionPerformed(e)); popmenu1.add(menu3);
        menu6.setIcon(in); menu6.addActionListener(e -> menu6ActionPerformed(e)); popmenu1.add(menu6);
        menu4.setIcon(in); menu4.addActionListener(e -> menu4ActionPerformed(e)); popmenu2.add(menu4);
        menu5.setIcon(in); menu5.addActionListener(e -> menu5ActionPerformed(e)); popmenu2.add(menu5);

        userlist.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent evt) { userlistMouseClicked(evt); }
        });
        iplist.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent evt) { iplistMouseClicked(evt); }
        });
        userlist.addActionListener(e -> userlistActionPerformed(e));

        chocco = new JTabbedPane();
        chocco.setFont(UI_FONT);
        chocco.addTab("유저", so, userlist);
        chocco.addTab("IP", so, iplist);

        userFrame = new JFrame("유저 관리");
        userFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        userFrame.setSize(300, 420);
        userFrame.setLocation(getX() + 80, getY() + 80);
        userFrame.getContentPane().setLayout(new BorderLayout());
        userFrame.getContentPane().add(chocco, BorderLayout.CENTER);
    }

    private void showUserManager() {
        if (userFrame == null) return;
        if (!userFrame.isVisible()) {
            userFrame.setLocation(getX() + 80, getY() + 80);
        }
        userFrame.setVisible(true);
        userFrame.toFront();
    }

    private void initServerSetting() {
        setting = new JFrame("서버 설정");
        setting.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setting.setResizable(false);

        chatlvl = new JTextField("", 5);
        maxuser = new JTextField("", 5);
        exp = new JTextField("", 5);
        adena = new JTextField("", 5);
        item = new JTextField("", 5);
        weightlimit = new JTextField("", 5);
        lawful = new JTextField("", 5);
        karma = new JTextField("", 5);
        enweapon = new JTextField("", 5);
        enarmor = new JTextField("", 5);
        set = new JButton("설정 저장");

        chatlvl1 = new JLabel(" 채팅 레벨");
        maxuser1 = new JLabel(" 최대 인원");
        exp1 = new JLabel("경험치 배율");
        adena1 = new JLabel("아데나 배율");
        item1 = new JLabel("아이템 배율");
        weightlimit1 = new JLabel("무게 최대치");
        lawful1 = new JLabel("라우풀 배율");
        karma1 = new JLabel("카르마 배율");
        enweapon1 = new JLabel("무기 인챈율");
        enarmor1 = new JLabel("방어 인챈율");

        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 3));
        panel.setBackground(WIN_FACE);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(chatlvl1); panel.add(chatlvl);
        panel.add(maxuser1); panel.add(maxuser);
        panel.add(exp1); panel.add(exp);
        panel.add(adena1); panel.add(adena);
        panel.add(item1); panel.add(item);
        panel.add(weightlimit1); panel.add(weightlimit);
        panel.add(lawful1); panel.add(lawful);
        panel.add(karma1); panel.add(karma);
        panel.add(enweapon1); panel.add(enweapon);
        panel.add(enarmor1); panel.add(enarmor);

        set.setMargin(new Insets(2, 8, 2, 8));
        set.addActionListener(e -> setMouseClicked(null));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(WIN_FACE);
        bottom.add(set);

        setting.getContentPane().setLayout(new BorderLayout());
        setting.getContentPane().add(panel, BorderLayout.CENTER);
        setting.getContentPane().add(bottom, BorderLayout.SOUTH);
        setting.pack();
        setting.setSize(340, 300);
    }

    private void openServerSetting() {
        if (!serverstart) {
            JOptionPane.showMessageDialog(this, "서버가 실행되지 않았습니다.", "Lineage Server", JOptionPane.ERROR_MESSAGE);
            return;
        }

        chatlvl.setText(String.valueOf(Config.GLOBAL_CHAT_LEVEL));
        maxuser.setText(String.valueOf(Config.MAX_ONLINE_USERS));
        exp.setText(String.valueOf(Config.RATE_XP));
        adena.setText(String.valueOf(Config.RATE_DROP_ADENA));
        item.setText(String.valueOf(Config.RATE_DROP_ITEMS));
        weightlimit.setText(String.valueOf(Config.RATE_WEIGHT_LIMIT));
        lawful.setText(String.valueOf(Config.RATE_LAWFUL));
        karma.setText(String.valueOf(Config.RATE_KARMA));
        enweapon.setText(String.valueOf(Config.ENCHANT_CHANCE_WEAPON));
        enarmor.setText(String.valueOf(Config.ENCHANT_CHANCE_ARMOR));

        setting.setLocation(getX() + 120, getY() + 100);
        setting.setVisible(true);
        setting.toFront();
    }

    private void showGodsVoiceDialog() {
        if (!serverstart) {
            JOptionPane.showMessageDialog(this, "서버가 실행되지 않았습니다.", "Lineage Server", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String message = JOptionPane.showInputDialog(this, "", "전체 공지", JOptionPane.PLAIN_MESSAGE);
        if (message == null) return;
        message = message.trim();
        if (message.isEmpty()) return;

        appendLog(txtTrade, "[******]  :  " + message);
        L1World.getInstance().broadcastServerMessage("[******] " + message);
    }

    private void showSimpleReport(String title, String text) {
        JTextArea area = new JTextArea(text, 10, 42);
        area.setEditable(false);
        area.setFont(FIXED_FONT);
        area.setBackground(WIN_FACE);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), title, JOptionPane.INFORMATION_MESSAGE);
    }

    // Fixed-font, low-overhead log area close to the original Win32 server log.
    private JTextArea createLogArea() {
        JTextArea area = new JTextArea(5, 20);
        area.setEditable(false);
        area.setColumns(20);
        area.setRows(5);
        area.setFont(FIXED_FONT);
        area.setBackground(LOG_BG);
        area.setForeground(LOG_FG);
        area.setCaretColor(Color.WHITE);
        area.setMargin(new Insets(3, 4, 3, 4));
        return area;
    }

    public static void appendLog(JTextArea textArea, String msg) {
        if (textArea == null || msg == null) return;
        SwingUtilities.invokeLater(() -> {
            textArea.append(msg + "\n");
            if (textArea.getLineCount() > MAX_LOG_LINES) {
                try {
                    int endOffset = textArea.getLineEndOffset(textArea.getLineCount() - MAX_LOG_LINES);
                    textArea.replaceRange("", 0, endOffset);
                } catch (Exception ignored) {
                }
            }
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

	private void onCloseRequest() {
		int a = JOptionPane.showConfirmDialog(this, "매니저를 종료할까요?", " ", JOptionPane.YES_NO_OPTION);
		if (a != JOptionPane.YES_OPTION) return;

		saveWindowBounds();

		if (serverstart) {
			try {
				GameServer.getInstance().shutdownWithCountdown(0);
			} catch (Throwable t) {
				t.printStackTrace();
				dispose();
				System.exit(0);
			}
		} else {
			dispose();
			System.exit(0);
		}
	}

	private void stMouseClicked() {
		if (!serverstart) {
			first.setText("상태 : 로딩중");
			first.setForeground(new Color(128, 0, 0));
			serverStartAt = System.currentTimeMillis();
			new Thread(() -> {
				new Server();
				new ServerStart();
			}, "Lineage Server-Boot").start();
			serverstart = true;
		} else {
			JOptionPane.showMessageDialog(this, "이미 서버가 실행중입니다.", "Lineage Server", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void exMouseClicked(java.awt.event.MouseEvent evt) {
		if (serverstart) {
			int a = JOptionPane.showConfirmDialog(this, "확인: 서버종료", " ", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
			if (a == JOptionPane.YES_OPTION) {
				String[] _opts = {"10초 후 종료", "30초 후 종료", "5분 후 종료", "취소"};
				int _sel = JOptionPane.showOptionDialog(this, "서버 종료 대기시간을 선택하세요.", "서버 종료", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, _opts, _opts[0]);
				int _sec = (_sel == 0) ? 10 : (_sel == 1) ? 30 : (_sel == 2) ? 300 : -1;
				if (_sec > 0) { GameServer.getInstance().shutdownWithCountdown(_sec); }
			}
		} else {
			if (monitorScheduler != null) monitorScheduler.shutdownNow();
			System.exit(0);
		}
	}

	private void openManagerCommands(int x, int y) {
		if (serverstart) {
			if (!_isManagerCommands) {
				_isManagerCommands = true;
				new ManagerCommands(x, y);
			} else {
				MJMessageBox.show(this, "이미 창이 활성화 되어 있습니다.", false);
			}
		} else {
			MJMessageBox.show(this, "서버가 실행중이지 않습니다.", false);
		}
	}

	private static final int[] ALL_BUFF_SKILLS = {
			26, 42, 48, 57, 68, 79, 158, 163, 168
	};

	private void bufMouseClicked(java.awt.event.MouseEvent evt) {
		if (serverstart) {
			Object smallList[] = { "올버프", "버그경주", "무한대전", "전체채금", "채금해제" };
			String value = (String) JOptionPane.showInputDialog(this, "이벤트를 시작하시겠습니까?", " Server Message", JOptionPane.QUESTION_MESSAGE, null, smallList, smallList[0]);
			if (value != null) {
				if (value.equals("올버프")) {
					for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
						if (pc == null) {
							continue;
						}

						for (int skillId : ALL_BUFF_SKILLS) {
							L1SkillUse.cast(
									pc,
									skillId,
									pc.getId(),
									pc.getX(),
									pc.getY(),
									null,
									0,
									L1SkillUse.TYPE_GMBUFF);
						}
					}
				} else if (value.equals("전체채금")) {
					L1World.getInstance().set_worldChatElabled(false);
					appendLog(txtGlobalChat, "전체채금 실행");
				} else if (value.equals("채금해제")) {
					L1World.getInstance().set_worldChatElabled(true);
					appendLog(txtGlobalChat, "채금해제 실행");
				} else {
					JOptionPane.showMessageDialog(this, "준비중인 서비스 입니다..", " Server Message", JOptionPane.INFORMATION_MESSAGE);
				}
			}
		} else {
			JOptionPane.showMessageDialog(this, "서버가 실행되지 않았습니다.", " Server Message", JOptionPane.ERROR_MESSAGE);
		}
	}


	private void logclMouseClicked(java.awt.event.MouseEvent evt) {
		if (serverstart) {
			txtGlobalChat.setText(""); txtClanChat.setText(""); txtPartyChat.setText("");
			txtWhisper.setText(""); txtShop.setText(""); txtTrade.setText("");
			txtWarehouse.setText(""); txtEnchant.setText(""); txtPickup.setText("");
		} else JOptionPane.showMessageDialog(this, "서버가 실행되지 않았습니다.", "Server Message", JOptionPane.ERROR_MESSAGE);
	}

	private void setMouseClicked(java.awt.event.MouseEvent evt) {
		try {
			Config.GLOBAL_CHAT_LEVEL = Short.parseShort(chatlvl.getText());
			Config.MAX_ONLINE_USERS = Short.parseShort(maxuser.getText());
			Config.RATE_XP = Double.parseDouble(exp.getText());
			Config.RATE_DROP_ADENA = Double.parseDouble(adena.getText());
			Config.RATE_DROP_ITEMS = Double.parseDouble(item.getText());
			Config.RATE_WEIGHT_LIMIT = Double.parseDouble(weightlimit.getText());
			Config.RATE_LAWFUL = Double.parseDouble(lawful.getText());
			Config.RATE_KARMA = Double.parseDouble(karma.getText());
			Config.ENCHANT_CHANCE_ARMOR = Integer.parseInt(enarmor.getText());
			Config.ENCHANT_CHANCE_WEAPON = Integer.parseInt(enweapon.getText());

			appendLog(txtGlobalChat, "\n        [   서버 설정   ]");
			appendLog(txtGlobalChat, "    [설정] 채팅 레벨 : " + Config.GLOBAL_CHAT_LEVEL);
			appendLog(txtGlobalChat, "    [설정] 최대 접속 인원 : " + Config.MAX_ONLINE_USERS);
			appendLog(txtGlobalChat, "    [설정] 경험치 : " + Config.RATE_XP + " - 아데나 : " + Config.RATE_DROP_ADENA + " - 아이템 : " + Config.RATE_DROP_ITEMS);
			appendLog(txtGlobalChat, "    [설정] 무게 : " + Config.RATE_WEIGHT_LIMIT + " - 라우풀 : " + Config.RATE_LAWFUL + " - 카르마 : " + Config.RATE_KARMA);
			appendLog(txtGlobalChat, "    [설정] [인챈트] 무기 : " + Config.ENCHANT_CHANCE_WEAPON + " - 방어구 : " + Config.ENCHANT_CHANCE_ARMOR);
			appendLog(txtGlobalChat, "       [  서버 설정 완료  ]");
			setting.setVisible(false);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "입력값이 올바르지 않습니다.", "Server Message", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void chatKeyPressed(java.awt.event.KeyEvent evt) {
		if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
			if (serverstart) {
				String message = chat.getText().trim();
				if (!message.isEmpty()) {
					if (name.getText().trim().isEmpty()) {
						appendLog(txtTrade, "[******]  :  " + message);
						L1World.getInstance().broadcastServerMessage("[******] " + message);
					} else {
						L1PcInstance pc = L1World.getInstance().getPlayer(name.getText().trim());
						if (pc != null) {
							appendLog(txtWhisper, "[******] -> [" + name.getText() + "]  :  " + message);
							SC_SystemMessage.send(pc, "[******] -> " + message);
						} else {
							JOptionPane.showMessageDialog(this, "월드내에 " + name.getText() + " (은)는 존재하지않습니다.", "Server Message", JOptionPane.ERROR_MESSAGE);
						}
					}
					chat.setText("");
				}
			} else {
				JOptionPane.showMessageDialog(this, "서버가 실행되지 않았습니다.", "Server Message", JOptionPane.ERROR_MESSAGE);
				chat.setText("");
			}
		}
	}

	private void userlistActionPerformed(java.awt.event.ActionEvent evt) {
		if (!evt.getActionCommand().isEmpty()) name.setText(evt.getActionCommand());
	}

	private void userlistMouseClicked(java.awt.event.MouseEvent evt) {
		if (evt.getButton() == java.awt.event.MouseEvent.BUTTON3) popmenu1.show(userlist, evt.getX(), evt.getY());
	}

	private void iplistMouseClicked(java.awt.event.MouseEvent evt) {
		if (evt.getButton() == java.awt.event.MouseEvent.BUTTON3) popmenu2.show(iplist, evt.getX(), evt.getY());
	}

	private void menu1ActionPerformed(java.awt.event.ActionEvent evt) {
		if (serverstart && userlist.getSelectedItem() != null) {
			L1PcInstance players = L1World.getInstance().getPlayer(userlist.getSelectedItem());
			if (players != null) {
				GameServer.disconnectChar(userlist.getSelectedItem());
				JOptionPane.showMessageDialog(this, players.getName() + "를 강제추방 하였습니다.", "Server Message", JOptionPane.INFORMATION_MESSAGE);
				userlist.remove(userlist.getSelectedItem());
			} else {
				JOptionPane.showMessageDialog(this, "월드내에 " + userlist.getSelectedItem() + " (은)는 존재하지않습니다.", "Server Message", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void menu2ActionPerformed(java.awt.event.ActionEvent evt) {
		if (serverstart && userlist.getSelectedItem() != null) {
			L1PcInstance pc = L1World.getInstance().getPlayer(userlist.getSelectedItem());
			if (pc != null && pc.getNetConnection() != null) {
				IpTable.getInstance().banIp(pc.getNetConnection().getIp());
				iplist.add(pc.getNetConnection().getIp());
				GameServer.disconnectChar(pc);
				userlist.remove(userlist.getSelectedItem());
				JOptionPane.showMessageDialog(this, pc.getName() + "를 강제추방, 밴 하였습니다.", "Server Message", JOptionPane.INFORMATION_MESSAGE);
			}
		}
	}

	private void menu3ActionPerformed(java.awt.event.ActionEvent evt) {
		if (serverstart && !inf && userlist.getSelectedItem() != null) {
			L1PcInstance pc = L1World.getInstance().getPlayer(userlist.getSelectedItem());
			if (pc != null) new infomation(pc);
		}
	}

	private void menu4ActionPerformed(java.awt.event.ActionEvent evt) {
		if (serverstart && iplist.getSelectedItem() != null) {
			String selectedIp = iplist.getSelectedItem();
			iplist.remove(selectedIp);
			JOptionPane.showMessageDialog(this, "차단리스트(List) 삭제:" + selectedIp, "Server Message", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private void menu5ActionPerformed(java.awt.event.ActionEvent evt) {
		if (serverstart && iplist.getSelectedItem() != null) {
			String selectedIp = iplist.getSelectedItem();
			IpTable.getInstance().liftBanIp(selectedIp);
			iplist.remove(selectedIp);
			JOptionPane.showMessageDialog(this, "차단리스트(dB, List) 삭제:" + selectedIp, "Server Message", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private void menu7ActionPerformed(java.awt.event.ActionEvent evt) {
		if (serverstart && userlist.getSelectedItem() != null) {
			L1PcInstance pc = L1World.getInstance().getPlayer(userlist.getSelectedItem());
			if (pc != null) name.setText(pc.getName());
		}
	}

	private void menu6ActionPerformed(java.awt.event.ActionEvent evt) {
		if (serverstart && !inf && userlist.getSelectedItem() != null) {
			L1PcInstance pc = L1World.getInstance().getPlayer(userlist.getSelectedItem());
			if (pc != null) new Give(pc);
		}
	}

	// 모니터링 전용 단일 스케줄러
	private void startMonitoring() {
		monitorScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "Lineage Server-Status");
			t.setDaemon(true);
			return t;
		});

		OperatingSystemMXBean osBean =
				(OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

		 LoginController lc = LoginController.getInstance();

		monitorScheduler.scheduleAtFixedRate(() -> {
			try {
				final long mem = SystemUtil.getUsedMemoryMB();
				final int thread = Thread.activeCount();
				double cpuLoad = osBean.getSystemCpuLoad();
				int cpuVal = (int) (cpuLoad * 100D);
				if (cpuVal < 0) cpuVal = 0;
				if (cpuVal > 100) cpuVal = 100;

				final int finalCpu = cpuVal;
				final int players = lc.getOnlinePlayerCount();
				final long now = System.currentTimeMillis();
				final long uptime = serverstart && serverStartAt > 0L ? now - serverStartAt : 0L;
				final String elapsed = formatElapsed(uptime);
				final String serverClock = SERVER_CLOCK.format(LocalTime.now());

				cpu = finalCpu;
				count = players;

				SwingUtilities.invokeLater(() -> {
					label2.setText(" "+ String.valueOf(players));
					label4.setText("메모리        : " + mem + " MB");
					lblThread.setText("스레드        : " + thread);
					lblCPU.setText("CPU          : " + finalCpu + "%");
					stime.setText(serverClock);

					statusArea.setText(
							"IO버퍼풀크기: n/a\n"
							+ "플레이 유저: " + players + "\n"
							+ "최대 유저: " + Config.MAX_ONLINE_USERS + "\n\n"
							+ "가동 시간: " + elapsed + "\n"
							+ "서버 시간: " + serverClock + "\n\n"
							+ "슬립 카운트 : n/a\n"
							+ "대기중 쓰기 : n/a, /8k (n/a)");

					if (serverstart) {
						first.setText("상태 : 실행중");
						first.setForeground(new Color(0, 96, 0));
					}
				});
			} catch (Throwable ignored) {
			}
		}, 1000, 1000, TimeUnit.MILLISECONDS);
	}


	private static String formatElapsed(long ms) {
		long total = Math.max(0L, ms / 1000L);
		long days = total / 86400L;
		total %= 86400L;
		long hours = total / 3600L;
		total %= 3600L;
		long minutes = total / 60L;
		long seconds = total % 60L;
		return String.format("%d일 %02d시간 %02d분 %02d초", days, hours, minutes, seconds);
	}

	public static void main(String args[]) {
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ignored) {
			}
			Config.load();
			new chocco().setVisible(true);
		});
	}
}
