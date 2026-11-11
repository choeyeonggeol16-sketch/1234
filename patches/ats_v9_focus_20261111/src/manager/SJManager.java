package manager;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import com.sun.management.OperatingSystemMXBean;
import l1j.server.L1DatabaseFactory;
import l1j.server.Main;
import l1j.server.LevelUpPresentSystem.LevelUpPresentSystemLoader;
import l1j.server.Templates.L1Item;
import l1j.server.configure.Config;
import l1j.server.server.GameServer;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.MapReturnTable;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.WeaponBalanceTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.PrivateWarehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.item.TreasureBox;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillIconGFX;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.storage.CharactersItemStorage;
import l1j.server.server.utils.SQLUtil;
import l1j.server.server.utils.SystemUtil;
import manager.composite.LetterComposite;
import manager.dialog.DropMonsterFind;
import manager.dialog.PlayerInventory;
import manager.dialog.PlayerLevel;
import manager.dialog.PresentDialog;
import manager.dialog.ShopNpcFind;



public class SJManager {
	static public final String SERVER_VERSION = "o";
	public static Shell shlLinageLimu;
	private static Text text;
	private static Text text_2;
	private static Text text_3;
	private static Text text_1;
	private static Text text_4;
	private static Text text_5;
	private static Text text_6;
	private static Text text_7;
	private static Text text_8;
	private static Text text_9;
	private static Text text_10;
	private static Text text_11;
	private static Text text_12;
	private static Text text_13;
	private static Text text_14;
	private static Text text_15;
	public static Display display;
	public static boolean start = false;
	private static List list;
	private static Text text_16;
	
	private static SJManager _instance;
	private Text txtFff;
	private Text text_18;
	private Text txtlv;
	private Text txtLoginFalse;
	private Text text_19;
	private Text text_20;
	private Text text_21;
	private Text text_22;
	private Text text_23;
	private Text text_24;
	private Text text_25;
	private Text text_26;
	private Text text_27;
	private Text text_28;
	private Text text_33;
	private Text text_34;
	private Table table;
	private Table table_1;
	private static Text text_36;
	private static Text text_37;
	private static Text text_38;
	private static Text text_39;
	private static Text text_40;
	private static Text text_41;
	private static Text text_29;
	private static Text text_30;
	private static Text text_31;
	private static Text text_32;
	private static Text text_43;
	private static Text text_17;
	private static Text text_42;
	private static Text text_35;

	private Label label4;
	private Label lblThread;
	private Label lblCPU;
	private Label lblPcThread;
	private Label lblExcuThread;
	private Label lblNewLabel_5;
	
	private Button scroll;
	
	ProgressBar Hp_progressBar;
	ProgressBar Mp_progressBar;
	
	private boolean _char_info;
	private String _char_name;
	public SJManager(){}
	
	/**
	 * @wbp.parser.entryPoint
	 */
	public static SJManager getInstance() {
		if (_instance == null) {
			_instance = new SJManager();
			_instance.open();
		}
		return _instance;
	}	

	public static void main(String[] args) { 
		new Main();
		SJManager.getInstance();
	}
	
	/**
	 * Open the window.
	 */
	public void open() {	 
				
				
		display = Display.getDefault();
		createContents();
		shlLinageLimu.setBounds((display.getBounds().width / 2) - (shlLinageLimu.getBounds().width / 2),
				(display.getBounds().height / 2) - (shlLinageLimu.getBounds().height / 2), shlLinageLimu.getBounds().width,
				shlLinageLimu.getBounds().height);
		shlLinageLimu.open();
		shlLinageLimu.layout();
		for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
			if (pc.getNetConnection() != null) {
				list.add(pc.getName());
			}
		}
		
		//
		((LetterComposite) composite_4).reload();
		
		// PerformWorker start
		new Thread(new PerformWorker()).start();

		while (!shlLinageLimu.isDisposed()) {
			try {
				if (!display.readAndDispatch()) {
					display.sleep();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		open();
	}
	
	private void keydown() {
		display.addFilter(SWT.KeyDown, new Listener() {
			@Override
			public void handleEvent(Event event) {
				switch (event.keyCode) {
				case SWT.F5:
					if (_char_info && !_char_name.equals("")) {
						charInfo(_char_name);
					}
					break;
//				case SWT.F6:
//					System.out.println("f6 press");
//					break;
				}
			}
		});
	}

	/**
	 * Create contents of the window.
	 */
	protected void createContents() {
	    start = true;
		shlLinageLimu = new Shell(display,SWT.BORDER | SWT.MIN | SWT.TITLE | SWT.SYSTEM_MODAL);
		shlLinageLimu.setSize(935, 675);   //925, 657
		shlLinageLimu.setText("3.1 Version Lineage Server 포트: "+ Config.GAME_SERVER_PORT +"");
		shlLinageLimu.setImage(SWTResourceManager.getImage("data\\img\\apple.png"));
		shlLinageLimu.addShellListener(new ShellAdapter() {
			@Override
			public void shellClosed(ShellEvent e) {
		        String title = "SJ";
		        String message = "서버를 정상적으로 종료 시켜주세요.";
				MessageBox dialog = new MessageBox(shlLinageLimu,SWT.OK|SWT.ICON_INFORMATION);
				dialog.setText(title); 
				dialog.setMessage(message); 
				int flag = dialog.open();
				if (flag == SWT.OK) { 
					e.doit = false; // true로 하면 종료됨
				} else {
					e.doit = false; 
				}
			};
			@Override
			public void shellDeactivated(ShellEvent e) {
			}
		});
		
		Menu menu = new Menu(shlLinageLimu, SWT.BAR);
		shlLinageLimu.setMenuBar(menu);
		
		MenuItem mntmNewSubmenu = new MenuItem(menu, SWT.CASCADE);
		mntmNewSubmenu.setText("\uD30C \uC77C");
		
		Menu menu_1 = new Menu(mntmNewSubmenu);
		mntmNewSubmenu.setMenu(menu_1);
		
		MenuItem mntmNewItem = new MenuItem(menu_1, SWT.NONE);
		mntmNewItem.setText("SJ");
		
		MenuItem mntmNewItem_3 = new MenuItem(menu_1, SWT.NONE);
		mntmNewItem_3.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				GameServer.getInstance().saveAllCharInfo();
				MessageBox messageBox = new MessageBox(shlLinageLimu,SWT.OK|SWT.ICON_INFORMATION);
				messageBox.setMessage("모든 캐릭터의 정보 저장 완료 !!");
				messageBox.open();
			}
		});
		mntmNewItem_3.setText("\uC2DC\uC2A4\uD15C \uC800\uC7A5");
		
		MenuItem mntmNewItem_1 = new MenuItem(menu_1, SWT.NONE);
		mntmNewItem_1.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				savelog();
				MessageBox messageBox = new MessageBox(shlLinageLimu,SWT.OK|SWT.ICON_INFORMATION);
				messageBox.setMessage("서버의 모든 로그 저장 완료 !!");
				messageBox.open();
			}
		});
		mntmNewItem_1.setText("\uB85C\uADF8 \uC800\uC7A5");
		
		new MenuItem(menu_1, SWT.SEPARATOR);
		

		MenuItem menuItem_1 = new MenuItem(menu_1, SWT.NONE);
		menuItem_1.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MessageBox messageBox = new MessageBox(shlLinageLimu,SWT.YES | SWT.NO |SWT.ICON_QUESTION);
				messageBox.setMessage("서버를 종료하시겠습니까?");
				int type = messageBox.open();
				if(type == SWT.YES){
					GameServer.getInstance().shutdownWithCountdown(1);
				}
			}
		});
		menuItem_1.setText("바로 종료");
		
		
		MenuItem mntmNewItem_2 = new MenuItem(menu_1, SWT.NONE);
		mntmNewItem_2.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MessageBox messageBox = new MessageBox(shlLinageLimu,SWT.YES | SWT.NO |SWT.ICON_QUESTION);
				messageBox.setMessage("서버를 종료하시겠습니까?");
				int type = messageBox.open();
				if(type == SWT.YES){
					GameServer.getInstance().shutdownWithCountdown(10);
					
				}
			}
		});
		mntmNewItem_2.setText("\uC11C\uBC84 \uC885\uB8CC(10)");
		
	//	MenuItem menuItem = new MenuItem(menu, SWT.CASCADE);
	//	menuItem.setText("\uBAA8\uB2C8\uD130");
		
	//	Menu menu_2 = new Menu(menuItem);
	//	menuItem.setMenu(menu_2); */
		
				
		
		
	//	MenuItem mntmNewSubmenu_1 = new MenuItem(menu, SWT.CASCADE);
	//	mntmNewSubmenu_1.setText("\uB3C4\uAD6C");
		
	//	Menu menu_3 = new Menu(mntmNewSubmenu_1);
	//	mntmNewSubmenu_1.setMenu(menu_3);
		
	
		
		MenuItem mntmNewSubmenu_2 = new MenuItem(menu, SWT.CASCADE);
		mntmNewSubmenu_2.setText("\uB9AC\uB85C\uB4DC1");
		
		Menu menu_4 = new Menu(mntmNewSubmenu_2);
		mntmNewSubmenu_2.setMenu(menu_4);
		
		
		MenuItem mntmNewItem_7 = new MenuItem(menu_4, SWT.NONE);
		mntmNewItem_7.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Config.load();
				toMessageBox("Config 리로드 완료.");
				LogAppend("Config Update Complete.....");
			}
		});
		mntmNewItem_7.setText("\uCEE8\uD53C\uADF8(Config)");
		
		MenuItem mntmtreasureboxxml = new MenuItem(menu_4, SWT.NONE);
		mntmtreasureboxxml.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				TreasureBox.load();
				toMessageBox("L1TreasureBox 정보가 리로드 완료.");
				LogAppend("트레져박스 정보가 리로드 되었습니다.");
			}
		});
		mntmtreasureboxxml.setText("\uBC15\uC2A4(TreasureBox.xml)");
		
		MenuItem mntmNewItem_8 = new MenuItem(menu_4, SWT.NONE);
		mntmNewItem_8.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ItemTable.reload();
				toMessageBox("ItemTable 정보가 리로드 완료.");
				LogAppend("아이템 정보가 리로드 되었습니다.");
			}
		});
		mntmNewItem_8.setText("\uC544\uC774\uD15C");
		
		MenuItem mntmshop = new MenuItem(menu_4, SWT.NONE);
		mntmshop.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ShopTable.reload();
				toMessageBox("ShopTable 정보가 리로드 완료.");
				LogAppend("상점 테이블이 리로드 되었습니다.");
			}
		});
		mntmshop.setText("\uC0C1\uC810(shop)");
		
		
			
		MenuItem mntmskills = new MenuItem(menu_4, SWT.NONE);
		mntmskills.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				SkillsTable.reload();
				toMessageBox("SkillsTable 준비중.");
				LogAppend("Skill 준비중.");
			}
		});
		mntmskills.setText("\uC2A4\uD0AC(skills)");
		

		MenuItem menuItem_2 = new MenuItem(menu_4, SWT.NONE);
		menuItem_2.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				DropTable.reload();
				toMessageBox("droplist 리로드 되었습니다.");
				LogAppend("droplist 리로드 되었습니다.");
			}
		});
		menuItem_2.setText("\uB4DC\uB78D\uB9AC\uC2A4\uD2B8");
		
		
		
		new MenuItem(menu_4, SWT.SEPARATOR);
		

		
		MenuItem mntmpolymorphs = new MenuItem(menu_4, SWT.NONE);
		mntmpolymorphs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				PolyTable.reload();
				toMessageBox("polymorphs 정보가 리로드 완료.");
				LogAppend("polymorphs 정보가 리로드 되었습니다.");
			}
		});
		mntmpolymorphs.setText("\uBCC0\uC2E0(polymorphs)");
		

		
		
		MenuItem mntmlevelupquestsitem = new MenuItem(menu_4, SWT.NONE);
		mntmlevelupquestsitem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					LevelUpPresentSystemLoader.getInstance().reload();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				toMessageBox("level_present_items 정보가 리로드 완료.");
				LogAppend("level_present_items 정보가 리로드 되었습니다.");
			}
		});
		mntmlevelupquestsitem.setText("\uB808\uBCA8\uD018\uC2A4\uD2B8(levelup_quests_item)");
		

		
		
		MenuItem mntmNewSubmenu_3 = new MenuItem(menu, SWT.CASCADE);
		mntmNewSubmenu_3.setText("\uB9AC\uB85C\uB4DC2");
		
		Menu menu_5 = new Menu(mntmNewSubmenu_3);
		mntmNewSubmenu_3.setMenu(menu_5);
		
		

		MenuItem mntmweaponbalance = new MenuItem(menu_5, SWT.NONE);
		mntmweaponbalance.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
		        WeaponBalanceTable.reload();
				toMessageBox("weapon_balance 정보가 리로드 완료.");
				LogAppend("weapon_balance 정보가 리로드 되었습니다.");
			}
		});
		mntmweaponbalance.setText("\uC6E8\uD3F0\uB300\uBBF8\uC9C0(weapon_balance)");
		

		
		MenuItem mntmNewSubmenu_4 = new MenuItem(menu, SWT.CASCADE);
		mntmNewSubmenu_4.setText("\uB9AC\uB85C\uB4DC3");
		
		Menu menu_8 = new Menu(mntmNewSubmenu_4);
		mntmNewSubmenu_4.setMenu(menu_8);
		
		MenuItem mntmnpc = new MenuItem(menu_8, SWT.NONE);
		mntmnpc.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				NpcTable.reload();
				toMessageBox("NpcTable 정보가 리로드 완료.");
				LogAppend("NpcTable 정보가 리로드 되었습니다.");
			}
		});
		mntmnpc.setText("\uC5D4\uD53C\uC528(npc)");
		
		MenuItem mntmnpcaction = new MenuItem(menu_8, SWT.NONE);
		mntmnpcaction.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				NPCTalkDataTable.reload();
				toMessageBox("npcaction 정보가 리로드 완료.");
				LogAppend("npcaction 정보가 리로드 되었습니다.");
			}
		});
		mntmnpcaction.setText("\uC5D4\uD53C\uC528\uC561\uC158(npcaction)");
		
		
		
		MenuItem menuItem_38 = new MenuItem(menu_8, SWT.NONE);
		menuItem_38.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MapReturnTable.reload();
				toMessageBox("맵기억불가 정보가 리로드 완료.");
				LogAppend("맵기억불가 정보가 리로드 되었습니다.");
			}
		});
		menuItem_38.setText("\uB9F5\uAE30\uC5B5\uBD88\uAC00");
		

		
		
		
		Composite composite = new Composite(shlLinageLimu, SWT.NONE);
		composite.setBounds(0, 0, 918, 608);
		
		text = new Text(composite, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text.setForeground(SWTResourceManager.getColor(255, 255, 255));
		text.setBackground(SWTResourceManager.getColor(0, 0, 0));
		text.setLocation(10, 10);
		text.setText("\n"+
				 "                                       \n"+
				 "\n"+
			 	 "SERVER ON\n");
				 	 
		           
		text.setSize(322, 231);
		
		Button button = new Button(composite, SWT.NONE);
		button.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (Pcname == null || Pcname.length() <= 0) {
					toMessageBox("선택된 유저가 없습니다.");
					return;
				}
				MessageBox messageBox = new MessageBox(shlLinageLimu, SWT.YES | SWT.NO | SWT.ICON_QUESTION);
				messageBox.setMessage(Pcname + " 유저에게 통합버프를 주시겠습니까?");
				int type = messageBox.open();
				if (type == SWT.YES) {
					L1PcInstance pc = L1World.getInstance().getPlayer(Pcname);
					if (pc != null) {
						int[] allBuffSkill = { SkillId.인챈트_덱스터리티, SkillId.인챈트_마이티, SkillId.블레스_웨폰, SkillId.아이언_스킨 };
						SkillUse l1skilluse = new SkillUse();
						for (int i = 0; i < allBuffSkill.length; i++) {
							if (pc.hasSkillEffect(allBuffSkill[i])) { 
								pc.removeSkillEffect(allBuffSkill[i]);
							}
							l1skilluse.handleCommands(pc, allBuffSkill[i], pc.getId(), pc.getX(), pc.getY(), null, 0, SkillUse.TYPE_GMBUFF);
						}
						pc.sendPackets(S_SkillSound.get(pc.getId(), 4856));
					} else {
						toMessageBox("유저가 월드상에 존재하지 않습니다.");
					}
				}
			}
		});
		button.setText("전체버프");
		button.setBounds(441, 10, 87, 30);
		
		Button button_1 = new Button(composite, SWT.NONE);
		button_1.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (Pcname == null || Pcname.length() <= 0) {
					toMessageBox("선택된 유저가 없습니다.");
					return;
				}
				L1PcInstance pc = L1World.getInstance().getPlayer(Pcname);
				if (pc != null) {
				PlayerInventory.open(pc);
				} else {
					toMessageBox("유저가 월드상에 존재하지 않습니다.");
				}
			}
		});
		button_1.setText("선물1");
		button_1.setBounds(441, 46, 42, 30);
		
		Button button_6 = new Button(composite, SWT.NONE);
		button_6.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				PresentDialog _PresentDialog = new PresentDialog(shlLinageLimu);
				_PresentDialog.open();
			}
		});
		button_6.setText("선물2");
		button_6.setBounds(486, 46, 42, 30);
		
		Button button_2 = new Button(composite, SWT.NONE);
		button_2.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (Pcname == null || Pcname.length() <= 0) {
					toMessageBox("선택된 유저가 없습니다.");
					return;
				}
				MessageBox messageBox = new MessageBox(shlLinageLimu, SWT.YES | SWT.NO | SWT.ICON_QUESTION);
				messageBox.setMessage(Pcname + " 유저를 변신시겠습니까?");
				int type = messageBox.open();
				if (type == SWT.YES) {
					L1PcInstance pc = L1World.getInstance().getPlayer(Pcname);
					if (pc != null) {
						SJManager.getInstance();
						//PlayerPoly dialog = new PlayerPoly(LinAllManager.getShell(),
						//		SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
						//dialog.open(pc);
					} else {
						toMessageBox("유저가 월드상에 존재하지 않습니다.");
					}
				}
			}
		});
		button_2.setText("변 신");
		button_2.setBounds(441, 82, 87, 30);
		
		Button button_3 = new Button(composite, SWT.NONE);
		button_3.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (Pcname == null || Pcname.length() <= 0) {
					toMessageBox("선택된 유저가 없습니다.");
					return;
				}
				MessageBox messageBox = new MessageBox(shlLinageLimu, SWT.YES | SWT.NO | SWT.ICON_QUESTION);
				messageBox.setMessage(Pcname + " 유저의 레벨을 변경하시겠습니까?");
				int type = messageBox.open();
				if (type == SWT.YES) {
					L1PcInstance pc = L1World.getInstance().getPlayer(Pcname);
					if (pc != null) {
						SJManager.getInstance();
						PlayerLevel dialog = new PlayerLevel(SJManager.getShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
						dialog.open(pc);
					} else {
						toMessageBox("유저가 월드상에 존재하지 않습니다.");
					}
				}
			}
		});
		button_3.setText("레 벨");
		button_3.setBounds(441, 118, 87, 30);
		
		Button button_4 = new Button(composite, SWT.NONE);
		button_4.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (Pcname == null || Pcname.length() <= 0) {
					toMessageBox("선택된 유저가 없습니다.");
					return;
				}
				MessageBox messageBox = new MessageBox(shlLinageLimu, SWT.YES | SWT.NO | SWT.ICON_QUESTION);
				messageBox.setMessage(Pcname + " 유저의 채금을 해제하시겠습니까?");
				int type = messageBox.open();
				if (type == SWT.YES) {
					L1PcInstance pc = L1World.getInstance().getPlayer(Pcname);
					if (pc != null) {
						if (pc.hasSkillEffect(SkillId.상태_채팅_금지)) {
							pc.killSkillEffectTimer(SkillId.상태_채팅_금지);
							pc.removeSkillEffect(SkillId.상태_채팅_금지);
							pc.sendPackets(new S_SkillIconGFX(36, 1));
						} else {
							toMessageBox("해당유저는 현재 채금상태가 아닙니다.");
						}
					} else {
						toMessageBox("유저가 월드상에 존재하지 않습니다.");
					}
				}
			}
		});
		button_4.setText("채금풀기");
		button_4.setBounds(441, 190, 87, 30);
		
		Button button_5 = new Button(composite, SWT.NONE);
		button_5.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (Pcname == null || Pcname.length() <= 0) {
					toMessageBox("선택된 유저가 없습니다.");
					return;
				}
				MessageBox messageBox = new MessageBox(shlLinageLimu, SWT.YES | SWT.NO | SWT.ICON_QUESTION);
				messageBox.setMessage(Pcname + " 유저에게 채금을 주시겠습니까?");
				int type = messageBox.open();
				if (type == SWT.YES) {
					L1PcInstance pc = L1World.getInstance().getPlayer(Pcname);
					if (pc != null) {
						pc.setSkillEffect(SkillId.상태_채팅_금지, 10 * 60 * 1000);
						pc.sendPackets(new S_SkillIconGFX(36, 10 * 60));
						pc.sendPackets(new S_ServerMessage(286, String.valueOf(10)));
					} else {
						toMessageBox("유저가 월드상에 존재하지 않습니다.");
					}
				}
			}
		});
		button_5.setText("채 금");
		button_5.setBounds(441, 154, 87, 30);
		
		Button button_7 = new Button(composite, SWT.NONE);
		button_7.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				//LinAllManager.getInstance();
				ShopNpcFind dialog = new ShopNpcFind(SJManager.getShell(),SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
				dialog.open();
			}
		});
		button_7.setText("\uC0C1\uC810");
		button_7.setBounds(441, 223, 42, 21);
		
		
		
		
		
		Button button_8 = new Button(composite, SWT.NONE);
		button_8.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				//LinAllManager.getInstance();
				DropMonsterFind dialog = new DropMonsterFind(SJManager.getShell(),SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
				dialog.open();
			}
		});
		button_8.setText("\uB4DC\uB78D");
		button_8.setBounds(486, 223, 42, 21);
		
		

		
		text_1 = new Text(composite, SWT.BORDER);
		text_1.setBounds(336, 10, 100, 21);
		text_1.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.keyCode == 13) {
					charInfo(text_1.getText());
					//accountInfo();
				}
			}
		});
		//캐릭터검색
		text_1.setBackground(SWTResourceManager.getColor(255, 255, 255));
		
		
		list = new List(composite, SWT.BORDER | SWT.V_SCROLL | SWT.SINGLE);

		Listener listener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				switch (event.type) {
				case SWT.MouseDoubleClick:
					if (event.button == 1) {
						if (list.getItemCount() <= 0) {
							return;
						}
						if (list.getSelectionIndex() < 0) {
							return;
						}
						String _selectName = list.getItem(list.getSelectionIndex());
						L1PcInstance pc = L1World.getInstance().getPlayer(_selectName);
						if (pc == null) {
							return;
						}
						if (_selectName == null || _selectName == "") {
							return;
						}
						text_1.setText(_selectName);
						charInfo(_selectName);
						//accountInfo();
						break;
					}
				}
			}
		};

		list.setTouchEnabled(true);
		list.setForeground(SWTResourceManager.getColor(SWT.COLOR_BLACK));
		list.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		list.setItems(new String[] {});
		list.setBounds(336, 37, 99, 204);
		list.addListener(SWT.MouseDoubleClick, listener);
		
		
		
		
		
//		list = new List(composite, SWT.BORDER | SWT.V_SCROLL);
//		list.setBounds(336, 37, 99, 204);
//		list.setItems(new String[] {});
//		list.setBackground(SWTResourceManager.getColor(255, 255, 255));
		
		CTabFolder tabFolder_2 = new CTabFolder(composite, SWT.BORDER);
		tabFolder_2.setBounds(534, 10, 376, 273);
		tabFolder_2.setSimple(false);
		tabFolder_2.setSelectionBackground(Display.getCurrent().getSystemColor(SWT.COLOR_TITLE_INACTIVE_BACKGROUND_GRADIENT));
		
		CTabItem tbtmNewItem_3 = new CTabItem(tabFolder_2, SWT.NONE);
		tbtmNewItem_3.setText("캐릭터 정보");
		tabFolder_2.setSelection(tbtmNewItem_3);
		Composite composite_3 = new Composite(tabFolder_2, SWT.NONE);
		tbtmNewItem_3.setControl(composite_3);
		
		
		Label lblNewLabel = new Label(composite_3, SWT.BORDER | SWT.SHADOW_IN);
		lblNewLabel.setBounds(10, 10, 98, 67);
		
		
		txtFff = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		txtFff.setEditable(false);
		txtFff.setText("[\uAC00\uC785\uD55C \uD608\uB9F9 \uC5C6\uC74C]");
		txtFff.setBounds(114, 10, 123, 18);
		
		text_18 = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		text_18.setText("\uBA54\uD2F0\uC2A4");
		text_18.setEditable(false);
		text_18.setBounds(114, 34, 123, 18);
		
		txtlv = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		txtlv.setText("0Lv  0.0%");
		txtlv.setEditable(false);
		txtlv.setBounds(114, 58, 123, 18);
		
		Button btnNewButton_1 = new Button(composite_3, SWT.NONE);
		btnNewButton_1.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
			}
		});
		btnNewButton_1.setBounds(243, 10, 117, 42);
		btnNewButton_1.setText("\uBAA8\uB4E0 \uB85C\uADF8");
		
		txtLoginFalse = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		txtLoginFalse.setText("false");
		txtLoginFalse.setEditable(false);
		txtLoginFalse.setBounds(290, 59, 70, 18);
		
		Label lblNewLabel_1 = new Label(composite_3, SWT.NONE);
		lblNewLabel_1.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.NORMAL));
		lblNewLabel_1.setBounds(245, 58, 50, 19);
		lblNewLabel_1.setText("Login :");
		
		Label lblHp = new Label(composite_3, SWT.NONE);
		lblHp.setText("\uD53C :");
		lblHp.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.NORMAL));
		lblHp.setBounds(11, 83, 27, 19);
		
		text_19 = new Text(composite_3, SWT.BORDER);
		text_19.setText("2500/5000");
		text_19.setEditable(false);
		text_19.setBounds(38, 81, 70, 21);
		
		Hp_progressBar = new ProgressBar(composite_3, SWT.NONE);
		Hp_progressBar.setState(SWT.ERROR);
		Hp_progressBar.setSelection(50);
		Hp_progressBar.setForeground(SWTResourceManager.getColor(SWT.COLOR_RED));
		Hp_progressBar.setBounds(124, 82, 236, 17);
		
		Label lblMp = new Label(composite_3, SWT.NONE);
		lblMp.setText("\uC5E0 :");
		lblMp.setFont(SWTResourceManager.getFont("맑은 고딕", 9, SWT.NORMAL));
		lblMp.setBounds(10, 108, 27, 19);
		
		text_20 = new Text(composite_3, SWT.BORDER);
		text_20.setText("2500/5000");
		text_20.setEditable(false);
		text_20.setBounds(38, 108, 70, 21);
		
		Mp_progressBar = new ProgressBar(composite_3, SWT.NONE);
		Mp_progressBar.setState(SWT.PAUSED);
		Mp_progressBar.setSelection(50);
		Mp_progressBar.setForeground(SWTResourceManager.getColor(SWT.COLOR_BLUE));
		Mp_progressBar.setBounds(124, 110, 236, 17);
		
		Label lblNewLabel_2 = new Label(composite_3, SWT.NONE);
		lblNewLabel_2.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.NORMAL));
		lblNewLabel_2.setBounds(10, 135, 27, 17);
		lblNewLabel_2.setText("\uD798");
		
		text_21 = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		text_21.setEditable(false);
		text_21.setText("10/10");
		text_21.setBounds(38, 133, 50, 21);
		
		Label lblCon = new Label(composite_3, SWT.NONE);
		lblCon.setText("\uCF58");
		lblCon.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.NORMAL));
		lblCon.setBounds(10, 158, 27, 17);
		
		text_22 = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		text_22.setText("10/10");
		text_22.setEditable(false);
		text_22.setBounds(38, 158, 50, 21);
		
		Label lblInt = new Label(composite_3, SWT.NONE);
		lblInt.setText("\uC778");
		lblInt.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.NORMAL));
		lblInt.setBounds(10, 181, 27, 17);
		
		text_23 = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		text_23.setText("10/10");
		text_23.setEditable(false);
		text_23.setBounds(38, 181, 50, 21);
		
		Label lblDex = new Label(composite_3, SWT.NONE);
		lblDex.setText("\uB371");
		lblDex.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.NORMAL));
		lblDex.setBounds(94, 135, 27, 17);
		
		text_24 = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		text_24.setText("10/10");
		text_24.setEditable(false);
		text_24.setBounds(122, 133, 50, 21);
		
		Label lblWis = new Label(composite_3, SWT.NONE);
		lblWis.setText("\uC704");
		lblWis.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.NORMAL));
		lblWis.setBounds(94, 158, 27, 17);
		
		text_25 = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		text_25.setText("10/10");
		text_25.setEditable(false);
		text_25.setBounds(122, 158, 50, 21);
		
		Label lblCha = new Label(composite_3, SWT.NONE);
		lblCha.setText("\uCE74");
		lblCha.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.NORMAL));
		lblCha.setBounds(94, 181, 27, 17);
		
		text_26 = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		text_26.setText("10/10");
		text_26.setEditable(false);
		text_26.setBounds(122, 181, 50, 21);
		
		Label lblSp = new Label(composite_3, SWT.NONE);
		lblSp.setText("SP : ");
		lblSp.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.NORMAL));
		lblSp.setBounds(11, 204, 27, 17);
		
		text_27 = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		text_27.setText("100");
		text_27.setEditable(false);
		text_27.setBounds(38, 204, 50, 21);
		
		Label lblMr = new Label(composite_3, SWT.NONE);
		lblMr.setText("MR : ");
		lblMr.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.NORMAL));
		lblMr.setBounds(89, 204, 27, 17);
		
		text_28 = new Text(composite_3, SWT.BORDER | SWT.CENTER);
		text_28.setText("100");
		text_28.setEditable(false);
		text_28.setBounds(122, 204, 50, 21);
		
		Label lblLogoutTime = new Label(composite_3, SWT.RIGHT);
		lblLogoutTime.setText("LogOut Time :");
		lblLogoutTime.setBounds(176, 205, 80, 15);
		
		DateTime dateTime_1 = new DateTime(composite_3, SWT.BORDER);
		dateTime_1.setBounds(262, 206, 98, 18);
		
		DateTime dateTime_2 = new DateTime(composite_3, SWT.BORDER | SWT.TIME);
		dateTime_2.setBounds(239, 227, 121, 18);
		
		Label lblPkCount = new Label(composite_3, SWT.RIGHT);
		lblPkCount.setText("Pk Count :");
		lblPkCount.setBounds(5, 227, 61, 15);
		
		text_33 = new Text(composite_3, SWT.BORDER);
		text_33.setEditable(false);
		text_33.setText("1000");
		text_33.setFont(SWTResourceManager.getFont("맑은 고딕", 8, SWT.NORMAL));
		text_33.setBounds(71, 227, 40, 16);
		
		Label lblDethCount = new Label(composite_3, SWT.RIGHT);
		lblDethCount.setText("Deth Count :");
		lblDethCount.setBounds(116, 227, 70, 15);
		
		text_34 = new Text(composite_3, SWT.BORDER);
		text_34.setEditable(false);
		text_34.setText("1000");
		text_34.setFont(SWTResourceManager.getFont("맑은 고딕", 8, SWT.NORMAL));
		text_34.setBounds(192, 227, 40, 16);
		
		Label lblNewLabel_3 = new Label(composite_3, SWT.NONE);
		lblNewLabel_3.setBounds(10, 10, 98, 67);
		
		CTabItem tbtmNewItem_5 = new CTabItem(tabFolder_2, SWT.NONE);
		tbtmNewItem_5.setText("인벤토리");
		
		
		table = new Table(tabFolder_2, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
		table.setTouchEnabled(true);
		table.setForeground(SWTResourceManager.getColor(255, 255, 255));
		table.setBackground(SWTResourceManager.getColor(51, 51, 51));
		tbtmNewItem_5.setControl(table);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		
		TableColumn tblclmnNewColumn = new TableColumn(table, SWT.NONE);
		tblclmnNewColumn.setResizable(false);
		tblclmnNewColumn.setWidth(266);
		tblclmnNewColumn.setText("\uC774 \uB984");
		
		TableColumn tblclmnNewColumn_1 = new TableColumn(table, SWT.CENTER);
		tblclmnNewColumn_1.setResizable(false);
		tblclmnNewColumn_1.setWidth(79);
		tblclmnNewColumn_1.setText("\uC624\uBE0C\uC81D\uD2B8");
		
		Menu menu_6 = new Menu(table);
		table.setMenu(menu_6);
		
		MenuItem mntmNewItem_23 = new MenuItem(menu_6, SWT.NONE);
		mntmNewItem_23.setText("상세정보");// 오른쪽버튼

		MenuItem mntmNewItem_21 = new MenuItem(menu_6, SWT.NONE);
		mntmNewItem_21.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (Pcname == null || Pcname.length() <= 0) {
					toMessageBox("선택된 유저가 없습니다.");
					return;
				}
				L1PcInstance pc = L1World.getInstance().getPlayer(Pcname);
				if (pc == null) {
					toMessageBox("선택된 유저가 없습니다.");
					return;
				}
				PlayerInventory.open(pc);
			}
		});
		mntmNewItem_21.setText("아이템수정");// 오른쪽버튼

		new MenuItem(menu_6, SWT.SEPARATOR);

		MenuItem mntmNewItem_22 = new MenuItem(menu_6, SWT.NONE);
		mntmNewItem_22.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (table.getSelectionCount() <= 0) {
					toMessageBox("선택된 아이템이 없습니다.");
					return;
				}
				MessageBox messageBox = new MessageBox(shlLinageLimu, SWT.YES | SWT.NO | SWT.ICON_QUESTION);
				messageBox.setMessage("해당 아이템을 정말 삭제하시겠습니까 ?");
				int type = messageBox.open();
				if (type == SWT.YES) {
					itemdelete(table.getSelection());
					charInfo(Pcname);
				}
			}
		});
		mntmNewItem_22.setText("아이템회수(삭제)");
		
		CTabItem tbtmNewItem_6 = new CTabItem(tabFolder_2, SWT.NONE);
		tbtmNewItem_6.setText("창 고");
		
		
		table_1 = new Table(tabFolder_2, SWT.BORDER | SWT.FULL_SELECTION);
		table_1.setLinesVisible(true);
		table_1.setHeaderVisible(true);
		table_1.setForeground(SWTResourceManager.getColor(255, 255, 255));
		table_1.setBackground(SWTResourceManager.getColor(51, 51, 51));
		tbtmNewItem_6.setControl(table_1);

		
		TableColumn tableColumn = new TableColumn(table_1, SWT.NONE);
		tableColumn.setWidth(266);
		tableColumn.setText("\uC774 \uB984");
		tableColumn.setResizable(false);
		
		TableColumn tableColumn_1 = new TableColumn(table_1, SWT.CENTER);
		tableColumn_1.setWidth(79);
		tableColumn_1.setText("\uC624\uBE0C\uC81D\uD2B8");
		tableColumn_1.setResizable(false);
		
		
		
		Menu menu_7 = new Menu(table_1);
		table_1.setMenu(menu_7);

		MenuItem mntmNewItem_4 = new MenuItem(menu_7, SWT.NONE);
		mntmNewItem_4.setText("상세정보");
		
		
	
/////////////////		
		MenuItem mntmNewItem_5 = new MenuItem(menu_7, SWT.NONE);
		mntmNewItem_5.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (Pcname == null || Pcname.length() <= 0) {
					toMessageBox("선택된 유저가 없습니다.");
					return;
				}
				L1PcInstance pc = L1World.getInstance().getPlayer(Pcname);
				if (pc == null) {
					toMessageBox("선택된 유저가 없습니다.");
					return;
				}
				PlayerInventory.open(pc);
			}
		});
		mntmNewItem_5.setText("아이템수정");// 오른쪽버튼

		new MenuItem(menu_7, SWT.SEPARATOR);
	
		
		MenuItem mntmNewItem_6 = new MenuItem(menu_7, SWT.NONE);
		mntmNewItem_6.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (table_1.getSelectionCount() <= 0) {
					toMessageBox("선택된 아이템이 없습니다.");
					return;
				}
				MessageBox messageBox = new MessageBox(shlLinageLimu, SWT.YES | SWT.NO | SWT.ICON_QUESTION);
				messageBox.setMessage("해당 아이템을 정말 삭제하시겠습니까 ?");
				int type = messageBox.open();
				if (type == SWT.YES) {
					wherehouseitemdelete(table_1.getSelection());
					charInfo(Pcname);
				}
			}
		});
		mntmNewItem_6.setText("아이템회수(삭제)");
		
		
		
		CTabItem tbtmNewItem_7 = new CTabItem(tabFolder_2, SWT.NONE);
		tbtmNewItem_7.setText("계 정");
		
		Composite composite_6 = new Composite(tabFolder_2, SWT.NONE);
		tbtmNewItem_7.setControl(composite_6);
		
		Label label = new Label(composite_6, SWT.NONE);
		label.setText("\uC900\uBE44\uC911");
		label.setBounds(143, 108, 61, 12);
		
		TabFolder tabFolder = new TabFolder(composite, SWT.NONE);
		tabFolder.setBounds(10, 247, 521, 303);
		
		TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
		tabItem.setText("채팅");
		
		Composite composite_1 = new Composite(tabFolder, SWT.NONE);
		tabItem.setControl(composite_1);
		
		CTabFolder tabFolder_1 = new CTabFolder(composite_1, SWT.BORDER);
		tabFolder_1.setFont(SWTResourceManager.getFont("굴림", 9, SWT.NORMAL));
		tabFolder_1.setBounds(0, 0, 513, 277);
		tabFolder_1.setSelectionBackground(Display.getCurrent().getSystemColor(SWT.COLOR_TITLE_INACTIVE_BACKGROUND_GRADIENT));
		
		CTabItem tbtmNewItem_2 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_2.setFont(SWTResourceManager.getFont("굴림", 9, SWT.NORMAL));
		tbtmNewItem_2.setText("전체");
		tabFolder_1.setSelection(tbtmNewItem_2);
		text_4 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_4.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_4.setEditable(false);
		text_4.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tbtmNewItem_2.setControl(text_4);
		
		tabFolder_1.setSelection(tbtmNewItem_2);
		CTabItem tbtmNewItem_8 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_8.setFont(SWTResourceManager.getFont("굴림", 9, SWT.NORMAL));
		tbtmNewItem_8.setText("일반");
		
		text_5 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_5.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_5.setBackground(SWTResourceManager.getColor(255, 255, 255));
		text_5.setEditable(false);
		tbtmNewItem_8.setControl(text_5);
		
		CTabItem tbtmNewItem_9 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_9.setFont(SWTResourceManager.getFont("굴림", 9, SWT.NORMAL));
		tbtmNewItem_9.setText("귓말");
		
		text_6 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_6.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_6.setEditable(false);
		text_6.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tbtmNewItem_9.setControl(text_6);
		
		CTabItem tbtmNewItem_10 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_10.setFont(SWTResourceManager.getFont("굴림", 9, SWT.NORMAL));
		tbtmNewItem_10.setText("혈맹");
		
		text_7 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_7.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_7.setBackground(SWTResourceManager.getColor(255, 255, 255));
		text_7.setEditable(false);
		tbtmNewItem_10.setControl(text_7);
		
		CTabItem tbtmNewItem_11 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_11.setFont(SWTResourceManager.getFont("굴림", 9, SWT.NORMAL));
		tbtmNewItem_11.setText("파티");
		
		text_8 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_8.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_8.setBackground(SWTResourceManager.getColor(255, 255, 255));
		text_8.setEditable(false);
		tbtmNewItem_11.setControl(text_8);
		
		CTabItem tbtmNewItem_12 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_12.setFont(SWTResourceManager.getFont("굴림", 9, SWT.NORMAL));
		tbtmNewItem_12.setText("상점");
		
		text_9 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_9.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_9.setBackground(SWTResourceManager.getColor(255, 255, 255));
		text_9.setEditable(false);
		tbtmNewItem_12.setControl(text_9);
		
		CTabItem tbtmNewItem_13 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_13.setFont(SWTResourceManager.getFont("굴림", 9, SWT.NORMAL));
		tbtmNewItem_13.setText("거래");
		
		text_10 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_10.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_10.setBackground(SWTResourceManager.getColor(255, 255, 255));
		text_10.setEditable(false);
		tbtmNewItem_13.setControl(text_10);
		
		CTabItem tbtmNewItem_14 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_14.setFont(SWTResourceManager.getFont("굴림", 9, SWT.NORMAL));
		tbtmNewItem_14.setText("창고");
		
		text_11 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_11.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_11.setBackground(SWTResourceManager.getColor(255, 255, 255));
		text_11.setEditable(false);
		tbtmNewItem_14.setControl(text_11);
		
		CTabItem tbtmEc = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmEc.setText("사용,삭제");
		
		text_16 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_16.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_16.setBackground(SWTResourceManager.getColor(255, 255, 255));
		text_16.setEditable(false);
		tbtmEc.setControl(text_16);
		
		CTabItem tbtmNewItem_15 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_15.setText("인챈");
		
		text_12 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_12.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_12.setBackground(SWTResourceManager.getColor(255, 255, 255));
		text_12.setEditable(false);
		tbtmNewItem_15.setControl(text_12);
		
		CTabItem tbtmNewItem_16 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_16.setText("픽업,드랍");
		
		text_13 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_13.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_13.setBackground(SWTResourceManager.getColor(255, 255, 255));
		text_13.setEditable(false);
		tbtmNewItem_16.setControl(text_13);
		
		CTabItem tbtmNewItem_17 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_17.setText("패널티");
		
		text_14 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_14.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_14.setBackground(SWTResourceManager.getColor(255, 255, 255));
		text_14.setEditable(false);
		tbtmNewItem_17.setControl(text_14);
		
		CTabItem tbtmNewItem_18 = new CTabItem(tabFolder_1, SWT.NONE);
		tbtmNewItem_18.setText("버그");
		
		text_15 = new Text(tabFolder_1, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_15.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_15.setBackground(SWTResourceManager.getColor(255, 255, 255));
		text_15.setEditable(false);
		tbtmNewItem_18.setControl(text_15);
		
		

		TabItem tabItem_1 = new TabItem(tabFolder, SWT.NONE);
		tabItem_1.setText("로그");
		
		
		
		
		Composite composite_2 = new Composite(tabFolder, SWT.NONE);
		tabItem_1.setControl(composite_2);
		
		CTabFolder tabFolder_4 = new CTabFolder(composite_2, SWT.BORDER);
		tabFolder_4.setFont(SWTResourceManager.getFont("굴림", 9, SWT.NORMAL));
		tabFolder_4.setBounds(0, 0, 513, 277);
		tabFolder_4.setSelectionBackground(Display.getCurrent().getSystemColor(SWT.COLOR_TITLE_INACTIVE_BACKGROUND_GRADIENT));
	
		
		CTabItem tbtmNewItem_19 = new CTabItem(tabFolder_4, SWT.NONE);
		tbtmNewItem_19.setFont(SWTResourceManager.getFont("굴림", 9, SWT.NORMAL));
		tbtmNewItem_19.setText("보스");
		tabFolder_4.setSelection(tbtmNewItem_19);
		text_35 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_35.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_35.setEditable(false);
		text_35.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tbtmNewItem_19.setControl(text_35);
		

		
		CTabItem tbtmNewItem_20 = new CTabItem(tabFolder_4, SWT.NONE);
		tbtmNewItem_20.setText("영자");
		
		text_36 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_36.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_36.setEditable(false);
		text_36.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tbtmNewItem_20.setControl(text_36);
		
		CTabItem tbtmNewItem_21 = new CTabItem(tabFolder_4, SWT.NONE);
		tbtmNewItem_21.setText("유저");
		
		text_37 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_37.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_37.setEditable(false);
		text_37.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tbtmNewItem_21.setControl(text_37);
		
		CTabItem tbtmNewItem_22 = new CTabItem(tabFolder_4, SWT.NONE);
		tbtmNewItem_22.setText("스핵");
		
		text_38 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_38.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_38.setEditable(false);
		text_38.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tbtmNewItem_22.setControl(text_38);
		
		CTabItem tbtmNewItem_23 = new CTabItem(tabFolder_4, SWT.NONE);
		tbtmNewItem_23.setText("공성");
		
		text_39 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_39.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_39.setEditable(false);
		text_39.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tbtmNewItem_23.setControl(text_39);
		
		CTabItem tbtmNewItem_24 = new CTabItem(tabFolder_4, SWT.NONE);
		tbtmNewItem_24.setText("제작");
		
		text_40 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_40.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_40.setEditable(false);
		text_40.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tbtmNewItem_24.setControl(text_40);
		
		CTabItem tbtmNewItem_26 = new CTabItem(tabFolder_4, SWT.NONE);
		tbtmNewItem_26.setText("\uB808\uC774\uB4DC");
		
		text_42 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_42.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_42.setEditable(false);
		text_42.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tbtmNewItem_26.setControl(text_42);
		
		CTabItem tbtmNewItem_25 = new CTabItem(tabFolder_4, SWT.NONE);
		tbtmNewItem_25.setText("\uC695\uC124");
		
		text_41 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_41.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_41.setEditable(false);
		text_41.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tbtmNewItem_25.setControl(text_41);
		
		CTabItem tabItem_4 = new CTabItem(tabFolder_4, SWT.NONE);
		tabItem_4.setText("\uBC84\uACBD\uD22C\uACBD");
		
		text_29 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_29.setFont(manager.SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_29.setEditable(false);
		text_29.setBackground(manager.SWTResourceManager.getColor(255, 255, 255));
		tabItem_4.setControl(text_29);
		
		CTabItem tabItem_6 = new CTabItem(tabFolder_4, SWT.NONE);
		tabItem_6.setText("\uC2DC\uC7A5");
		
		text_30 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_30.setFont(manager.SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_30.setEditable(false);
		text_30.setBackground(manager.SWTResourceManager.getColor(255, 255, 255));
		tabItem_6.setControl(text_30);
		
		CTabItem tabItem_5 = new CTabItem(tabFolder_4, SWT.NONE);
		tabItem_5.setText("\uC778\uD615");
		
		text_31 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_31.setFont(manager.SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_31.setEditable(false);
		text_31.setBackground(manager.SWTResourceManager.getColor(255, 255, 255));
		tabItem_5.setControl(text_31);
		
		CTabItem tabItem_8 = new CTabItem(tabFolder_4, SWT.NONE);
		tabItem_8.setText("\uCE90\uB9AD\uBD09\uC778");
		
		text_32 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_32.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_32.setEditable(false);
		text_32.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tabItem_8.setControl(text_32);
		
		CTabItem tabItem_9 = new CTabItem(tabFolder_4, SWT.NONE);
		tabItem_9.setText("\uC624\uD1A0");
		
		text_43 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_43.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_43.setEditable(false);
		text_43.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tabItem_9.setControl(text_43);
		
		CTabItem tabItem_7 = new CTabItem(tabFolder_4, SWT.NONE);
		tabItem_7.setText("\uC2A4\uCD08");
		
		text_17 = new Text(tabFolder_4, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		text_17.setFont(SWTResourceManager.getFont("굴림", 10, SWT.NORMAL));
		text_17.setEditable(false);
		text_17.setBackground(SWTResourceManager.getColor(255, 255, 255));
		tabItem_7.setControl(text_17);
		
		TabItem tabItem_2 = new TabItem(tabFolder, SWT.NONE);
		tabItem_2.setText("편지");
		
		//Composite composite_4 = new Composite(tabFolder, SWT.NONE);
		composite_4 = new LetterComposite(tabFolder, SWT.NONE);
		tabItem_2.setControl(composite_4);
		

		TabItem tabItem_3 = new TabItem(tabFolder, SWT.NONE);
		tabItem_3.setText("준비중..");
		
		Composite composite_5 = new Composite(tabFolder, SWT.NONE);
		tabItem_3.setControl(composite_5);
		
		text_2 = new Text(composite, SWT.BORDER);
		text_2.setBounds(10, 556, 70, 21);
		text_2.setBackground(SWTResourceManager.getColor(255, 255, 255));
		
		text_3 = new Text(composite, SWT.BORDER);
		text_3.setBounds(86, 556, 354, 21);
		text_3.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if(e.keyCode == 13){
					L1World.getInstance().broadcastServerMessage("[******]"+text_3.getText());
					text_4.append("[******] : "+text_3.getText()+"\n");
					text_3.setText("");
				}
			}
		});
		text_3.setBackground(SWTResourceManager.getColor(255, 255, 255));
		
		Button btnNewButton = new Button(composite, SWT.NONE);
		btnNewButton.setBounds(446, 556, 87, 21);
		btnNewButton.setText("\uBCF4 \uB0B4 \uAE30");
		
		CTabFolder tabFolder_3 = new CTabFolder(composite, SWT.BORDER);
		tabFolder_3.setBounds(534, 289, 376, 288);
		tabFolder_3.setSimple(false);
		tabFolder_3.setSelectionBackground(Display.getCurrent().getSystemColor(SWT.COLOR_TITLE_INACTIVE_BACKGROUND_GRADIENT));
		
		CTabItem tbtmNewItem = new CTabItem(tabFolder_3, SWT.NONE);
		tbtmNewItem.setText("서버정보");
		tabFolder_3.setSelection(tbtmNewItem);
		Composite composite_1_1 = new Composite(tabFolder_3, SWT.NONE);
		tbtmNewItem.setControl(composite_1_1);
		
		Composite composite_2_1 = new Composite(composite_1_1, SWT.BORDER);
		composite_2_1.setBounds(161, 10, 207, 140);
		
		Label lblNewLabel_11 = new Label(composite_2_1, SWT.NONE);
		lblNewLabel_11.setBounds(10, 10, 115, 12);
		lblNewLabel_11.setText("경험치  : [ "+ Config.RATE_XP +" ] 배");
		
		Label lblNewLabel_12 = new Label(composite_2_1, SWT.NONE);
		lblNewLabel_12.setBounds(10, 28, 115, 12);
		lblNewLabel_12.setText("아데나  : [ "+ Config.RATE_DROP_ADENA +" ] 배");

		
		Label lblNewLabel_13 = new Label(composite_2_1, SWT.NONE);
		lblNewLabel_13.setBounds(10, 46, 115, 12);
		lblNewLabel_13.setText("아이템  : [ "+ Config.RATE_DROP_ITEMS +" ] 배");
		
		Label lblNewLabel_4 = new Label(composite_2_1, SWT.NONE);
		lblNewLabel_4.setBounds(10, 76, 115, 12);
		lblNewLabel_4.setText("레벨제한  : [ "+ Config.LIMITLEVEL +" ] ");
		
		
		Tree tree = new Tree(composite_1_1, SWT.BORDER);
		tree.setTouchEnabled(true);
		tree.setBounds(10, 37, 145, 158);
		
		TreeColumn trclmnNewColumn = new TreeColumn(tree, SWT.LEFT);
		trclmnNewColumn.setResizable(false);
		trclmnNewColumn.setWidth(137);
		trclmnNewColumn.setText("New Column");
		
		
		
		TreeItem trtmNewTreeitem1 = new TreeItem(tree, SWT.NONE);
		trtmNewTreeitem1.setText(new String[] {"\uB098\uBB47\uC78E"});
		trtmNewTreeitem1.setChecked(true);
		trtmNewTreeitem1.setForeground(SWTResourceManager.getColor(0, 0, 0));
		trtmNewTreeitem1.setText("\uCF04\uD2B8 \uC131");
		
		TreeItem trtmNewTreeitem = new TreeItem(trtmNewTreeitem1, SWT.NONE);
		trtmNewTreeitem.setText("\uC8FC\uC778 \uC5C6\uC74C");
		
		
		TreeItem trtmNewTreeitem2 = new TreeItem(tree, SWT.NONE);
		trtmNewTreeitem2.setText(new String[] {"\uB098\uBB47\uC78E"});
		trtmNewTreeitem2.setChecked(true);
		trtmNewTreeitem2.setForeground(SWTResourceManager.getColor(0, 0, 0));
		trtmNewTreeitem2.setText("\uC624\uD06C\uC758 \uC232");
		
		TreeItem trtmNewTreeitem_1 = new TreeItem(trtmNewTreeitem2, SWT.NONE);
		trtmNewTreeitem_1.setText("\uC8FC\uC778 \uC5C6\uC74C");
		trtmNewTreeitem2.setExpanded(true);
		
			TreeItem trtmNewTreeitem3 = new TreeItem(tree, SWT.NONE);
			trtmNewTreeitem3.setText(new String[] {"\uB098\uBB47\uC78E"});
			trtmNewTreeitem3.setChecked(true);
			trtmNewTreeitem3.setForeground(SWTResourceManager.getColor(0, 0, 0));
			trtmNewTreeitem3.setText("\uC708\uB2E4\uC6B0\uB4DC \uC131");
			
			TreeItem trtmNewTreeitem_2 = new TreeItem(trtmNewTreeitem3, SWT.NONE);
			trtmNewTreeitem_2.setText("\uC8FC\uC778 \uC5C6\uC74C");
			trtmNewTreeitem3.setExpanded(true);
			
			TreeItem trtmNewTreeitem4 = new TreeItem(tree, SWT.NONE);
			trtmNewTreeitem4.setText(new String[] {"\uB098\uBB47\uC78E"});
			trtmNewTreeitem4.setChecked(true);
			trtmNewTreeitem4.setForeground(SWTResourceManager.getColor(0, 0, 0));
			trtmNewTreeitem4.setText("\uAE30\uB780 \uC131");
			
			TreeItem trtmNewTreeitem_3 = new TreeItem(trtmNewTreeitem4, SWT.NONE);
			trtmNewTreeitem_3.setText("\uC8FC\uC778 \uC5C6\uC74C");
			trtmNewTreeitem4.setExpanded(true);
			
			
			TreeItem trtmNewTreeitem5 = new TreeItem(tree, SWT.NONE);
			trtmNewTreeitem5.setText(new String[] {"\uB098\uBB47\uC78E"});
			trtmNewTreeitem5.setChecked(true);
			trtmNewTreeitem5.setForeground(SWTResourceManager.getColor(0, 0, 0));
			trtmNewTreeitem5.setText("\uD558\uC774\uB124 \uC131");
			
			TreeItem trtmNewTreeitem_7 = new TreeItem(trtmNewTreeitem5, SWT.NONE);
			trtmNewTreeitem_7.setText("\uC8FC\uC778 \uC5C6\uC74C");
			
			
			TreeItem trtmNewTreeitem6 = new TreeItem(tree, SWT.NONE);
			trtmNewTreeitem6.setText(new String[] {"\uB098\uBB47\uC78E"});
			trtmNewTreeitem6.setChecked(true);
			trtmNewTreeitem6.setForeground(SWTResourceManager.getColor(0, 0, 0));
			trtmNewTreeitem6.setText("\uB4DC\uC6CC\uD504 \uC131");
			
			TreeItem trtmNewTreeitem_6 = new TreeItem(trtmNewTreeitem6, SWT.NONE);
			trtmNewTreeitem_6.setText("\uC8FC\uC778 \uC5C6\uC74C");
			
			
			TreeItem trtmNewTreeitem7 = new TreeItem(tree, SWT.NONE);
			trtmNewTreeitem7.setText(new String[] {"\uB098\uBB47\uC78E"});
			trtmNewTreeitem7.setChecked(true);
			trtmNewTreeitem7.setForeground(SWTResourceManager.getColor(0, 0, 0));
			trtmNewTreeitem7.setText("\uC544\uB374 \uC131");
			
			TreeItem trtmNewTreeitem_5 = new TreeItem(trtmNewTreeitem7, SWT.NONE);
			trtmNewTreeitem_5.setText("\uC8FC\uC778 \uC5C6\uC74C");
			
			TreeItem trtmNewTreeitem8 = new TreeItem(tree, SWT.NONE);
			trtmNewTreeitem8.setText(new String[] {"\uB098\uBB47\uC78E"});
			trtmNewTreeitem8.setChecked(true);
			trtmNewTreeitem8.setForeground(SWTResourceManager.getColor(0, 0, 0));
			trtmNewTreeitem8.setText("\uB514\uC544\uB4DC \uC694\uC0C8");
			
			TreeItem trtmNewTreeitem_4 = new TreeItem(trtmNewTreeitem8, SWT.NONE);
			trtmNewTreeitem_4.setText("\uC8FC\uC778 \uC5C6\uC74C");
			
			Composite composite_4_1 = new Composite(composite_1_1, SWT.BORDER);
			composite_4_1.setBounds(161, 156, 207, 39);
			
			lblNewLabel_5 = new Label(composite_4_1, SWT.NONE);
			lblNewLabel_5.setBounds(38, 10, 129, 25);
			
			label4 = new Label(composite_1_1, SWT.NONE);
			label4.setBounds(10, 221, 116, 12);
			label4.setText("Memory : 1024MB");
			
			lblThread = new Label(composite_1_1, SWT.NONE);
			lblThread.setBounds(142, 221, 102, 12);
			lblThread.setText("Thread : 1000");
			
			lblCPU = new Label(composite_1_1, SWT.NONE);
			lblCPU.setBounds(266, 221, 80, 12);
			lblCPU.setText("CPU : 100%");
			
			scroll = new Button(composite_1_1, SWT.CHECK);
			scroll.setBounds(10, 10, 94, 16);
			scroll.setText("\uB85C\uADF8\uC2A4\uD06C\uB864\uB77D");
			
			
			
			
			lblPcThread = new Label(composite, SWT.NONE);
			lblPcThread.setBounds(10, 586, 407, 12);
			lblPcThread.setText("PC : 0");
			
			lblExcuThread = new Label(composite, SWT.NONE);
			lblExcuThread.setBounds(437, 586, 471, 12);
			lblExcuThread.setText("Excu : 0");			
			
			keydown();
	}
	
	
	
	
	
	
	
	/**
	 * 현제 시간
	 * @return
	 */
	private String getLogTime() {
		Calendar currentDate = Calendar.getInstance();
		SimpleDateFormat dateFormat = new SimpleDateFormat("MM.dd HH:mm:ss");
		String time = dateFormat.format(currentDate.getTime());
		return time;
	}
	/**
	 * 서버 로그
	 * @param Msg
	 */
	public void LogAppend(final String Msg) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					text.append("[S]" + Msg + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	
	/**
	 * 캐릭 접속 로그
	 * @param name
	 * @param ip
	 */
	public void LogConnectAppend(final String name, final String ip) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					boolean ch = false;
					for (String s : list.getItems()) {
						if (name.equals(s))
							ch = true;
					}
					if (!ch) {
						if(!scroll.getSelection())
						text.append("I:" + ip + " T: " + getLogTime() +" 접 [" + name + "] "+ (list.getItems().length + 1) +"명\n");
						list.add(name);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	 
	
	/**
	 * 캐릭 접속 종료
	 * @param name
	 * @param ip
	 */
	public synchronized void LogLogOutAppend(final String name, final String ip) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					boolean ch = false;
					for (String s : list.getItems()) {
						if (name.equals(s))
							ch = true;
					}
					if (ch) {
						if(!scroll.getSelection())
						text.append("I:" + ip + " T: " + getLogTime() +" 종 [" + name + "] "+ (list.getItems().length - 1) +"명\n");
						list.remove(name);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	public void ItemRecoveryShopAppend(final String Itemname,final String name,final int count, final int type) {

		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					if(type == 0){//픽업
						text_16.append("픽업 : ["+name+"][아이템 : "+Itemname+"] Time : "+getLogTime()+"\n");		   
					}else if(type == 1){//픽업
						text_16.append("삭제 : ["+name+"][아이템 : "+Itemname+"] Time : "+getLogTime()+"\n");		   
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	/**
	 * 아이템삭제메세지
	 * 
	 * @param name
	 * @param msg
	 */
	public void itemDeleteAppend(final String name,  ItemInstance item, int count) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					text_16.append(" T: " + getLogTime() + " ");
					text_16.append("삭제 :[" + name + "] " + getFormatItemName( item , count) + "\n");
					
					//, pc.getAccountName(), "["+pc.getName()+"]", getFormatItemName(item, count));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	public String getFormatItemName(ItemInstance item, int count) {
		StringBuilder sb = new StringBuilder(256);
		sb.append("[ ").append(item.getId()).append("] ");
		if (item.getEnchantLevel() > 0) {
			sb.append("+").append(item.getEnchantLevel());
		} else if (item.getEnchantLevel() < 0) {
			sb.append(item.getEnchantLevel());
		}
		sb.append(item.getName());
		if (item.isStackable()) {
			sb.append("( ").append(count).append(") ");
		}
		return sb.toString();
	}
	
	/**
	 * 전체채팅 로그 
	 * @param name
	 * @param msg
	 */
	public void AllChatAppend(final String name, final String msg) {
		display.syncExec(new Runnable() {
			public void run() { 
				try {
					if(!scroll.getSelection())
					text_4.append("T:" + getLogTime() + " [" + name + "] : " + msg + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	/**
	 * 일반 채팅 로그 
	 * @param name
	 * @param msg
	 */
	public void NomalchatAppend(final String name, final String msg) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_5.append("T:" + getLogTime() + " [" + name + "] : " + msg + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	/**
	 * 귓말 채팅 로그
	 * @param Aname
	 * @param Dname
	 * @param msg
	 */
	public void WisperChatAppend(final String Aname, final String Dname, final String msg) {

		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_6.append("T:" + getLogTime() + "[" + Aname + "]->[" + Dname + "] : " + msg + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	/**
	 * 클랜 채팅 로그
	 * @param Clanname
	 * @param name
	 * @param msg
	 */
	public void ClanChatAppend(final String Clanname, final String name, final String msg) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_7.append("T:" + getLogTime() + " [" + Clanname + "]" + name + " : " + msg + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	/**
	 * 파티채팅 로그
	 * @param partylist
	 * @param name
	 * @param msg
	 */
	public void PartyChatAppend(final String partylist, final String name, final String msg) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_8.append(partylist + "T:" + getLogTime() + " [" + name + "] : " + msg + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	public void PartyChatAppend(final String name, final String msg) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_8.append("[" + name + "] : " + msg + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	
	/**
	 * 상점 구입 로그
	 * @param Itemname
	 * @param count
	 * @param price
	 * @param npcname
	 * @param name
	 * @param msg
	 */

	
	public void ShopAppend(final String Itemname,final int count,final long price,final String npcname,
			final String name) {

		display.syncExec(new Runnable() {
			public void run() {
				try{
					if(!scroll.getSelection())
					text_9.append("["+npcname+"]"+name+" Time : "+getLogTime()+"\n");
					text_9.append("[아이템 : "+Itemname+"] [갯수 :"+count+"] [가격 : "+price+"]\n\n");
				}catch(Exception e){e.printStackTrace();}
			}
		});
	}
	public void ShopAppend1(final String Itemname,final int count,final long price,final String npcname,
			final String name) {

		display.syncExec(new Runnable() {
			public void run() {
				try{
					if(!scroll.getSelection())
					text_30.append("["+npcname+"]"+name+" Time : "+getLogTime()+"\n");
					text_30.append("[아이템 : "+Itemname+"] [갯수 :"+count+"] [가격 : "+price+"]\n\n");
				}catch(Exception e){e.printStackTrace();}
			}
		});
	}
	public void ShopAppend11(final String Itemname,final int count,final long price,final String npcname,final String name) {

		display.syncExec(new Runnable() {
			public void run() {
				try{
					if(!scroll.getSelection())
					text_29.append("["+npcname+"]"+name+" Time : "+getLogTime()+"\n");
					text_29.append("[아이템 : "+Itemname+"] [갯수 :"+count+"] [가격 : "+price+"]\n\n");
				}catch(Exception e){e.printStackTrace();}
			}
		});
	}
	
	/**
	 * 상점 판매 로그
	 * @param Itemname
	 * @param count
	 * @param price
	 * @param npcname
	 * @param name
	 * @param msg
	 */
	public void ShopSellAppend(final ItemInstance Item, final int count, final long price, final L1PcInstance name) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_9.append("[매입NPC]" + name.getName() + "(" + name.getId() + ") Time : " + getLogTime() + "\n");
					text_9.append("sell-[아이템 : " + Item.getLogName() + "(" + Item.getId() + ")] [갯수 :" + count + "] [가격 : "
							+ price + "]\n\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	/**
	 * 거래 로그
	 * @param Itemname
	 * @param count
	 * @param Aname
	 * @param Dname
	 */

	public void TradeAppend(final int EnchantLevel,final String Itemname,final int count, final String Aname, final String Dname) {

		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_10.append("[" + Aname + "]->[" + Dname + "] Time : " + getLogTime() + "\n");
					text_10.append("+[" + EnchantLevel + "][" + Itemname + "][" + count + "]\n\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

	}
	
	
	/**
	 * 아이템 사용 삭제 로그
	 * 아이템 사용 : type = 0, 삭제  : type = 1
	 * 
	 * @param Item
	 * @param count
	 * @param name
	 * @param type
	 */
	public void ItemConsumeAppend(final ItemInstance Item, final int count, final String name, final int type) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					if (type == 0) {
						text_16.append("[사용전] [" + name + "] [아이템 : " + Item.getLogName() + "(" + Item.getId() + ")] Time : "
								+ getLogTime() + "\n");
						// text_16.append("[아이템 : "+Itemname+"]\n\n");
					} else if (type == 1) {
						text_16.append("[사용후] [" + name + "] [아이템 : " + Item.getLogName() + "(" + Item.getId() + ")] Time : "
								+ getLogTime() + "\n");
					} else if (type == 2) {
						text_16.append("[삭제] [" + name + "] [아이템 : " + Item.getLogName() + "(" + Item.getId() + ")] Time : "
								+ getLogTime() + "\n");
						// text_16.append("[아이템 : "+Itemname+"]\n\n");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	

	
	/**
	 * 장비교환 상점	
	 * 
	 * @param Item
	 * @param count
	 * @param name
	 * @param type
	 */
	public void ItemTradeShopAppend(final ItemInstance Item, final ItemInstance Item2) {

		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_9.append("[장비교환] [아이템 : " + Item.getLogName() + "(" + Item.getId() + ")]  - >  " + Item2.getLogName()
							+ "(" + Item2.getId() + ")] Time : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	public void DollConsumeAppend(final String Itemname, final String name, final int type) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					if (type == 0) {
						text_31.append("[성공] [" + name + "] [아이템 : " + Itemname +"] Time : " + getLogTime() + "\n");
					} else if (type == 1) {
						text_31.append("[실패] [" + name + "] [아이템 : " + Itemname + "] Time : " + getLogTime() + "\n");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public void ChaTrade(final String Itemname,final String name, final int type,final String charName) {

		display.syncExec(new Runnable() {
			public void run() {
				try{
					if(!scroll.getSelection())
					if(type == 0){//성공
						text_32.append("["+name+"]=> 캐릭이  >>>  " + charName + " 을 봉인함 Time : "+getLogTime()+" \n");
					}else{//실패
						text_32.append("["+name+"]=> 캐릭이  >>>  " + charName + " 을 봉인함 Time : "+getLogTime()+"\n");
					}
				}catch(Exception e){e.printStackTrace();}
			}
		});

	}
	
	/**
	 * 창고에 넣기 : type = 0,창고에서 빼기 : type = 1
	 * 요정넣기, type = 2,요정 빼기 : type = 3
	 * 혈맹넣기, type = 4,혈맹 빼기 : type = 5
	 * @param Item
	 * @param count
	 * @param name
	 * @param type
	 */
	public void WarehouseAppend(final String Itemname,final int count,final String name, final int type) {

		display.syncExec(new Runnable() {
			public void run() {
				try{
					if(!scroll.getSelection())
					if(type == 0){
						text_11.append("["+name+"]->[창고맞김] Time : "+getLogTime()+"\n");
						text_11.append("[아이템 : "+Itemname+"]\n\n");
					}else if(type == 1){
						text_11.append("[창고찾음]->["+name+"] Time : "+getLogTime()+"\n");
						text_11.append("[아이템 : "+Itemname+"]\n\n");
					}
				}catch(Exception e){e.printStackTrace();}
			}
		});
	}
	/**
	 * 요정넣기, type = 0,요정 빼기 : type = 1
	 * 혈맹넣기, type = 2,혈맹 빼기 : type = 3
	 * @param Itemname
	 * @param count
	 * @param name
	 * @param type
	 */
	public void EPWarehouseAppend(final String Itemname,final int count,final String name, final int type) {

		display.syncExec(new Runnable() {
			public void run() {
				try{
					if(!scroll.getSelection())
					if(type == 0){
						text_11.append("["+name+"]->[요정창고] Time : "+getLogTime()+"\n");
						text_11.append("[아이템 : "+Itemname+"]\n\n");
					}else if(type == 1){
						text_11.append("[요정창고]->["+name+"] Time : "+getLogTime()+"\n");
						text_11.append("[아이템 : "+Itemname+"]\n\n");
					}else if(type == 2){
						text_11.append("["+name+"]->[혈맹창고:맞김] Time : "+getLogTime()+"\n");
						text_11.append("[아이템 : "+Itemname+"]\n\n");
					}else if(type == 3){
						text_11.append("[혈맹창고:찾음]->["+name+"] Time : "+getLogTime()+"\n");
						text_11.append("[아이템 : "+Itemname+"]\n\n");
					}
				}catch(Exception e){e.printStackTrace();}
			}
		});

	}
	
	/**
	 * 인첸트 로그
	 * 성공 : type = 0,실패 : type = 1
	 * @param Itemname
	 * @param name
	 * @param type
	 */
	
	public void EnchantAppend(final String Itemname,final int oldEnchant,final int newEnchant,final String name, final int type) {

		display.syncExec(new Runnable() {
			public void run() {
				try{
					if(!scroll.getSelection())
					if(type == 0){//성공
						text_12.append("["+name+"]=> 인첸트 성공! Time : "+getLogTime()+"\n");
						text_12.append("[아이템 : +"+oldEnchant+" "+Itemname+"] - > [아이템 : +"+newEnchant+" "+Itemname+"] \n");
					}else{//실패
						text_12.append("["+name+"]=> 인첸트 실패! Time : "+getLogTime()+"\n");
						text_12.append("[아이템 : +"+oldEnchant+" "+Itemname+"]\n");
					}
				}catch(Exception e){e.printStackTrace();}
			}
		});

	}
	
	/**
	 * 드랍 엔 픽업
	 * 픽업 : type=0,드랍 : type=1
	 * @param Itemname
	 * @param name
	 * @param count
	 * @param type
	 */
		
	public void PicupAppend(final String Itemname,final String name,final int count, final int type) {

		display.syncExec(new Runnable() {
			public void run() {
				try{
					if(!scroll.getSelection())
					if(type == 0){//픽업
						text_13.append("픽업 : ["+name+"][아이템 : "+Itemname+"] Time : "+getLogTime()+"\n");		   
					}else if(type == 1){//픽업
						text_13.append("삭제 : ["+name+"][아이템 : "+Itemname+"] Time : "+getLogTime()+"\n");		   
					}else{//드랍
						text_13.append("드랍 : ["+name+"][아이템 : "+Itemname+"] Time : "+getLogTime()+"\n");		    		
					}
				}catch(Exception e){e.printStackTrace();}
			}
		});

	}
	
	
	
	/**
	 * 패널티 떨굼 : 0 , 증발 : 1
	 * @param Itemname
	 * @param name
	 * @param count
	 * @param type
	 */
	public void PenaltyAppend(final String Itemname,final String name,final int count, final int type) {

		display.syncExec(new Runnable() {
			public void run() {
				try{
					if(!scroll.getSelection())
					if (type == 0) {// 떨굼
						text_14.append("떨굼 : [" + name + "][아이템 : " + Itemname + "] Time : " + getLogTime() + "\n");
					} else if (type == 2) {
						text_14.append("떨굼 : [" + name + "][경험치 : " + count + "] Time : " + getLogTime() + "\n");
					} else if (type == 3) {
						text_14.append("복구 : [" + name + "][경험치 : " + count + "] Time : " + getLogTime() + "\n");
					} else {// 증발
						text_14.append("증발 : [" + name + "][아이템 : " + Itemname + "] Time : " + getLogTime() + "\n");
					}
				}catch(Exception e){e.printStackTrace();}
			}
		});

	}
	
	
	/**
	 * 버그 이용자  기타
	 * @param Msg
	 */
	public void bugAppend(final String name, final String Msg) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_15.append("[" + name + "] [" + Msg + "] 시간 : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	public void bugautoAppend(final String name, final String Msg) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_43.append("[" + name + "] [" + Msg + "] 시간 : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	public void ReturnStausAppend(final String Msg, final String name) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_17.append("[" + Msg + "] [" + name + "]  시간 : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	
	
	public void ChatBug(final String name, final String Msg) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_41.append("[" + name + "]" + Msg + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * 레이드
	 * @param Msg
	 */
	public void RaidLoader(final String name, final String type) {
		display.syncExec(new Runnable() {
			public void run() {
				try {			
					if(!scroll.getSelection())
					text_42.append("아이디 : [" + name + "] [타입 : " + type + "] 오픈시간 : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	/**
	 *  스핵로그
	 * @param name
	 * @param pc
	 */
	public void TimeSpeed(final String name, L1PcInstance pc) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_38.append("스핵의심 : [" + name + "] 변신 :[" + pc.getCurrentSpriteId() + "] 직업 :["+ pc.getClassName() +"] 시간 : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 *  보스로그
	 * @param name
	 */
	public void BossAppend(final String name) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_35.append("보스스폰 : [" + name + "]  Time : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * 영자로그
	 * @param name
	 * @param cmd
	 * @param arg
	 */
	public void GmAppend(final String name, final String cmd, final String arg) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_36.append("명령 : [" + name + "] [" + cmd + "] [" + arg + "] Time : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * 유저로그
	 * @param name
	 * @param msg
	 */
	public void usercommandAppend(String name, String msg) {
		display.syncExec(new Runnable() {
			public void run() {
				try {
					if(!scroll.getSelection())
					text_37.append("명령 : [" + name + "] [" + msg + "] Time : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	/**
	 * 공성로그
	 * @param name
	 * @param msg
	 */
	public void castleMessageAppend(final String name, final String msg) {
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					if(!scroll.getSelection())
					text_39.append(" Time : " + getLogTime() + " ");
					text_39.append("[" + name + "] 의 공성이 " + msg + "되었습니다.\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	/**
	 * 제작로그
	 * @param name
	 * @param msg
	 * @param craftId
	 */

	public void CraftInfo(final String name, final String item, int Count, int enc) {
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					if(!scroll.getSelection())
					text_40.append("성공 : [" + name + "] [" + item + "] [" + Count + "] [" + enc + "]Time : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	public void CraftInfo2(final String name, final String item, int Count, int enc, final int craftId) {
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					if(!scroll.getSelection())
					text_40.append("실패 : [" + name + "] [" + item + "] [" + Count + "] [" + enc + "][" + craftId + "]Time : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	public void CraftInfos(final String name, final String msg, final int craftId) {
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					if(!scroll.getSelection())
					text_40.append("성공 : [" + name + "] [" + msg + "] [" + craftId + "] Time : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	public void CraftInfosx(final String name, final int craftId, final int craftIdS) {
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					if(!scroll.getSelection())
					text_40.append("실패 : [" + name + "] [" + craftId + "] [" + craftIdS + "] Time : " + getLogTime() + "\n");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	
	
	public static void savelog() {
		try {
			savelog(text, "시스템.txt");
			savelog(text_4, "전체채팅.txt");
			savelog(text_5, "일반채팅.txt");
			savelog(text_6, "귓속말.txt");
			savelog(text_7, "혈맹채팅.txt");
			savelog(text_8, "파티채팅.txt");
			savelog(text_9, "상점.txt");
			savelog(text_10, "교환.txt");
			savelog(text_11, "창고.txt");
			savelog(text_16, "삭제.txt");
			savelog(text_12, "인첸트.txt");
			savelog(text_13, "드랍&픽업.txt");
			savelog(text_14, "패널티.txt");
			savelog(text_15, "버그.txt");
			savelog(text_35, "보스스폰.txt");
			savelog(text_36, "영자명령.txt");
			savelog(text_37, "유저명령.txt");
			savelog(text_38, "스핵.txt");
			savelog(text_39, "공성.txt");
			savelog(text_40, "제작.txt");
			savelog(text_42, "레이드.txt");
			savelog(text_41, "욕설.txt");
			savelog(text_29, "버경투경.txt");
			savelog(text_30, "개인상점.txt");
			savelog(text_31, "인형.txt");
			savelog(text_32, "캐릭봉인.txt");
			savelog(text_43, "오토.txt");
			savelog(text_17, "스초.txt");
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public static String getDate() {
		SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd hh-mm", Locale.KOREA);
		return s.format(Calendar.getInstance().getTime());
	}

	public static void savelog(Text textPane, String name) {
		try {	
			File f = null;
			String sTemp = "";
			sTemp = getDate();
			StringTokenizer s = new StringTokenizer(sTemp, " ");
			String data = s.nextToken();
			File dir = new File("ManagerLog");
			if (!dir.isDirectory()) {
				dir.mkdir();
			}
			f = new File("ManagerLog/" + data);
			if (!f.exists()) {
				f.mkdir();
			}

			flush(textPane, name, data);
			textPane.setText("");
			
		} catch (Exception e) {
			e.getStackTrace();
		}
	}
	

	public static void flush(Text text, String FileName, String date) {
		try {
			RandomAccessFile rnd = new RandomAccessFile("ManagerLog/" + date + "/" + FileName + ".txt", "rw");
			rnd.seek(rnd.length());
			rnd.write(text.getText().getBytes());
			rnd.close();
		} catch (Exception e) {
		}
	}

	/////////////////////////////////////////////////////

	public static long cpu;

	// loop worker
	private class PerformWorker implements Runnable {
		@Override
		public void run() {
			try {
				PerformReader p = new PerformReader();				
				while (true) {
					// TODO 매니져 동기화시간
					Thread.sleep(1000L);
					display.asyncExec(p);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
	}
	
	public int thread = 0;
	public int poolsize = 0;
	public int queue = 0;
	public long task = 0;
	public long complete = 0;
	public int pcthread = 0;
	public int pcpoolsize = 0;
	public int pcqueue = 0;
	public long pctask = 0;
	public long pccomplete = 0;
	
	
	// ui change worker
	private class PerformReader implements Runnable {
		@Override
		public void run() {
			try {
				//updateExecutor((ThreadPoolExecutor) GeneralThreadPool.getInstance().executor());		
				//updatePcExecutor((ThreadPoolExecutor) GeneralThreadPool.getInstance().pcScheduler());
				long mem = SystemUtil.getUsedMemoryMB();
				
				//Calendar cal = Calendar.getInstance();
				//long timeMin = ((cal.getTimeInMillis() - Server.StartTime.getTimeInMillis()) / 1000) / 60;
				long timeMin = ((System.currentTimeMillis() - Main.startTime) / 1000) / 60;
				long timeHour = timeMin / 60;
				timeMin -= timeHour * 60;
				long timeDay = timeHour / 24;
				timeHour -= timeDay * 24;
				
				cpu = (int) (getUseCpu() * 100D);
				label4.setText(String.format("Memory : %d", mem));		// 메모리
				lblThread.setText(String.format("Thread : %d", Thread.activeCount()));	// 쓰레드 갯수
				lblCPU.setText(String.format("CPU : %d%%", cpu));		// 시피유 사용량			
				
				lblPcThread.setText(String.format("PC_Thread : %d Pool : %d Queue : %d Task : %d Complete : %d", pcthread, pcpoolsize, pcqueue, pctask, pccomplete));	// 쓰레드 갯수
				lblExcuThread.setText(String.format("Ex_Thread : %d Pool : %d Queue : %d Task : %d Complete : %d" , thread, poolsize, queue, task, complete));
		
				lblNewLabel_5.setText(String.format(timeDay+"일 " + timeHour+"시간 "+timeMin+"분 "));//서버 가동시간
				
				if (_char_info && !_char_name.equals("")) {
					L1PcInstance pc = L1World.getInstance().getPlayer(_char_name);
					if (pc != null) {
						if (pc.getClan() != null)
							txtFff.setText(pc.getClanname());// 혈맹이름[ ]포함
						text_18.setText(pc.getName());// 캐릭명
						
						txtlv.setText(String.format("%dLv     %s", pc.getLevel(), nf.format(ExpTable.getExpPercentagedouble(pc.getLevel(), pc.getExp()))) + "%");// 레벨 경험치퍼센트
						text_19.setText(String.format("%d/%d", pc.getCurrentHp(), pc.getMaxHp()));// hp 111/111
						text_20.setText(String.format("%d/%d", pc.getCurrentMp(), pc.getMaxMp()));// mp 111/111
						text_21.setText(String.format("%d", pc.getAbility().getTotalStr()));// 힘 베이스 포함
						text_24.setText(String.format("%d", pc.getAbility().getTotalDex()));// 덱스
						text_22.setText(String.format("%d", pc.getAbility().getTotalCon()));// 콘
						text_25.setText(String.format("%d", pc.getAbility().getTotalWis()));// 위즈
						text_23.setText(String.format("%d", pc.getAbility().getTotalInt()));// 인트
						text_26.setText(String.format("%d", pc.getAbility().getTotalCha()));// 카리
						text_27.setText(String.format("%d", pc.getSp()));// sp
						text_28.setText(String.format("%d", pc.getResistance().getMr()) + "%");// mr %포함
						text_33.setText(String.format("%d", pc.get_PKcount()));// PK
						text_34.setText(deth);// 데스
						
						int Hppercent = (int) Math.round(((double) pc.getCurrentHp() / (double) pc.getMaxHp()) * 100);
						Hp_progressBar.setSelection(Hppercent);
						
						int Mppercent = (int) Math.round(((double) pc.getCurrentMp() / (double) pc.getMaxMp()) * 100);
						Mp_progressBar.setSelection(Mppercent);
					} else {
						txtLoginFalse.setText("Login : Off");// 로긴 상태 Login : 포함
					}
				}
				
				// 라벨 3개 만들어서 위에 꺼 맞쳐주면됨. 잠시.
				//lblCPUProgressBack.setBounds(0, 0, 50, 330 - (int) (cpu * perToheight));		// 시피유 퍼센트 게이지바 이건 표현힘들면 안해도됨 
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		private double getUseCpu() {
			return ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getSystemCpuLoad();
		}
	}
	
	static public void toMessageBox(final String msg) {
		toMessageBox(SERVER_VERSION, msg);
	}

	static public void toMessageBox(final String title, final String msg) {
		MessageBox messageBox = new MessageBox(shlLinageLimu, SWT.ICON_WARNING);
		messageBox.setText(String.format("알림 :: %s", title));
		messageBox.setMessage(msg);
		messageBox.open();
	}
	ConcurrentHashMap<String,String[]> charlist = new ConcurrentHashMap<String,String[]>();
	private LetterComposite composite_4;
	
	public LetterComposite getLetterComposite() {
		return composite_4;
	}
	
	public static Shell getShell() {
		return shlLinageLimu;
	}

	
	String Clanname="";String Pcname="";
	String exp="";String stasts="";//로긴상태
	String hp="";String mp="";
	String str="";String dex="";
	String con="";String wis="";
	String Int="";String cha="";
	String sp="";String mr="";
	String er="";String dg="";
	String Ltime="";String toptime="";
	String gitime="";String pk="";
	String deth="";String logindate="";
	String logintime="";
	String accountname = "";
	int objid = 0;
	int MaxHp = 0;
	int CurrentHp = 0;
	int MaxMp = 0;
	int CurrentMp = 0;
	public static NumberFormat nf = NumberFormat.getInstance();
	
	
	ConcurrentHashMap<Integer,ItemInstance> items = new ConcurrentHashMap<Integer,ItemInstance>();
	/**
	 * 캐릭터 정보
	 * 
	 * @param name
	 */
	public void charInfo(final String name) {
//		L1PcInstance pc = L1World.getInstance().getPlayer(name);
//		if (pc == null) {
//			MessageBox messageBox = new MessageBox(shlLinageLimu, SWT.OK | SWT.ICON_INFORMATION);
//			messageBox.setMessage("그러한 캐릭명은 존재하지않습니다!");
//			messageBox.open();
//			return;
//		}
		if (ServercharInfo(name)) {
			/** 인벤 */
			InvantoryList();
			/** 창고 */
			warehouseList();
		} else if (DBcharInfo(name)) {
			/** 인벤 */
			InvantoryList();
			/** 창고 */
			warehouseList();
		} else {
			MessageBox messageBox = new MessageBox(shlLinageLimu, SWT.OK | SWT.ICON_INFORMATION);
			messageBox.setMessage("그러한 캐릭명은 존재하지않습니다!");
			messageBox.open();
			return;
		}

		display.syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					txtFff.setText(Clanname);// 혈맹이름[ ]포함
					text_18.setText(Pcname);// 캐릭명
					txtlv.setText(exp);// 레벨 경험치퍼센트
					txtLoginFalse.setText(stasts);// 로긴 상태 Login : 포함
					text_19.setText(hp);// hp 111/111
					text_20.setText(mp);// mp 111/111
					text_21.setText(str);// 힘 베이스 포함
					text_24.setText(dex);// 덱스
					text_22.setText(con);// 콘
					text_25.setText(wis);// 위즈
					text_23.setText(Int);// 인트
					text_26.setText(cha);// 카리
					text_27.setText(sp);// sp
					text_28.setText(mr);// mr %포함
					text_33.setText(pk);// PK
					text_34.setText(deth);// 데스
//					lblNewLabel_35.setText(logindate);// 로그아웃 날짜
//					lblNewLabel_37.setText(logintime);// 로그아웃 시간


					/** 인벤토리 */
					table.removeAll();
					for (ItemInstance item : items.values()) { // 인벤 반복
						TableItem tableItem = new TableItem(table, SWT.NONE);
						item.setIdentified(true);
						if (item.getItem().getItemId() == 40308) { //
							if (item.getBless() == 0)
								tableItem.setForeground(SWTResourceManager.getColor(255, 255, 102));
							else if (item.getBless() == 1)
								tableItem.setForeground(SWTResourceManager.getColor(255, 255, 255));
							else if (item.getBless() == 2)
								tableItem.setForeground(SWTResourceManager.getColor(255, 0, 0));
								if (item.getEnchantLevel() > 0) {
									tableItem.setText(new String[] { item.getName() + "(+" + item.getEnchantLevel() + " ) " + "(" + item.getCount() + ")", "" + item.getId() });
								} else {
									tableItem.setText(new String[] { item.getName() + "(" + item.getCount() + ")", "" + item.getId() });
								}
							
						} else {
							if (item.getBless() == 0)
								tableItem.setForeground(SWTResourceManager.getColor(255, 255, 102));
							else if (item.getBless() == 1)
								tableItem.setForeground(SWTResourceManager.getColor(255, 255, 255));
							else if (item.getBless() == 2)
								tableItem.setForeground(SWTResourceManager.getColor(255, 0, 0));
							
							if (item.getEnchantLevel() > 0) {
								tableItem.setText(new String[] { item.getName() + "(+" + item.getEnchantLevel() + " ) " + "(" + item.getCount() + ")", "" + item.getId() });
							} else {
								tableItem.setText(new String[] { item.getName() + "(" + item.getCount() + ")", "" + item.getId() });
							}
						}
					}
					/** 인벤토리 */

					/** 창고 */
					table_1.removeAll();
					for (ItemInstance item : warehouseitems.values()) {
						TableItem tableItem = new TableItem(table_1, SWT.NONE);
						item.setIdentified(true);
						if (item.getItem().getItemId() == 40308) { //
							if (item.getBless() == 0)
								tableItem.setForeground(SWTResourceManager.getColor(255, 255, 102));
							else if (item.getBless() == 1)
								tableItem.setForeground(SWTResourceManager.getColor(255, 255, 255));
							else if (item.getBless() == 2)
								tableItem.setForeground(SWTResourceManager.getColor(255, 0, 0));
							
							if (item.getEnchantLevel() > 0) {
								tableItem.setText(new String[] { item.getName() + "(+" + item.getEnchantLevel() + " ) " + "(" + item.getCount() + ")", "" + item.getId() });
							} else {
								tableItem.setText(new String[] { item.getName() + "(" + item.getCount() + ")", "" + item.getId() });
							}
							
						} else {
							if (item.getBless() == 0)
								tableItem.setForeground(SWTResourceManager.getColor(255, 255, 102));
							else if (item.getBless() == 1)
								tableItem.setForeground(SWTResourceManager.getColor(255, 255, 255));
							else if (item.getBless() == 2)
								tableItem.setForeground(SWTResourceManager.getColor(255, 0, 0));
							if (item.getEnchantLevel() > 0) {
								tableItem.setText(new String[] { item.getName() + "(+" + item.getEnchantLevel() + " ) " + "(" + item.getCount() + ")", "" + item.getId() });
							} else {
								tableItem.setText(new String[] { item.getName() + "(" + item.getCount() + ")", "" + item.getId() });
							}
						}
					}
					_char_info = true;
					_char_name = Pcname;
					/** 창고 */
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	
	
	
	public boolean ServercharInfo(final String name) {
		try {
			nf.setMaximumFractionDigits(2);
			nf.setMinimumFractionDigits(2);
			L1PcInstance target = L1World.getInstance().getPlayer(name);
			if (target == null)
				return false;
			if (target.getClan() == null) {
				Clanname = "[가입한 혈맹 없음]";
			} else {
				Clanname = "[" + target.getClan().getClanName() + "]";
			}
			Pcname = target.getName();
			exp = target.getLevel() + "Lv     " + nf.format(ExpTable.getExpPercentagedouble(target.getLevel(), target.getExp())) + "%";
			stasts = "Login : ON";
			hp = "" + target.getCurrentHp() + "/" + target.getMaxHp();
			mp = "" + target.getCurrentMp() + "/" + target.getMaxMp();
			str = "" + target.getAbility().getTotalStr() + "/" + target.getAbility().getBaseStr();
			dex = "" + target.getAbility().getTotalDex() + "/" + target.getAbility().getBaseDex();
			con = "" + target.getAbility().getTotalCon() + "/" + target.getAbility().getBaseCon();
			wis = "" + target.getAbility().getTotalWis() + "/" + target.getAbility().getBaseWis();
			Int = "" + target.getAbility().getTotalInt() + "/" + target.getAbility().getBaseInt();
			cha = "" + target.getAbility().getTotalCha() + "/" + target.getAbility().getBaseCha();
			sp = "" + target.getSp();
			mr = "" + (target.getResistance().getMr() + (target.getLevel() / 2)) + "%";
			// er = "" + target.get_Er();
			er = "" + target.getMeleeDmg();
			// dg = "" + target.getDg();
			dg = "" + target.getDamageReduction();
			pk = "" + target == null ? "0" : String.valueOf(target.getKills());
			deth = "" + target == null ? "0" : String.valueOf(target.getDeaths());


			if (target.getLastLoginTime() != null) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
				String str1 = sdf.format(new Date(target.getLastLoginTime().getTime()));
				logindate = str1;
				SimpleDateFormat sdf2 = new SimpleDateFormat("HH시 mm분 ss초", Locale.KOREA);
				String str2 = sdf2.format(new Date(target.getLastLoginTime().getTime()));
				logintime = str2;
			} else {
				logindate = "0000-00-00";
				logintime = "00시 00분 00초";
			}
			MaxHp = target.getMaxHp();
			CurrentHp = target.getCurrentHp();
			MaxMp = target.getMaxMp();
			CurrentMp = target.getCurrentMp();
			objid = target.getId();
			accountname = target.getAccountName();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}
	
	
	public void InvantoryList() {
		items.clear();
		try {
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			ItemInstance item = null;
			L1Item itemTemplate = null;
			try {
				con = L1DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement("SELECT * FROM character_items WHERE char_id = ?");
				pstm.setInt(1, objid);
				rs = pstm.executeQuery();
				while (rs.next()) {
					int itemId = rs.getInt("item_id");
					itemTemplate = ItemTable.getInstance().getTemplate(itemId);
					if (itemTemplate == null) {
						continue;
					}
					item = new ItemInstance();
					item.setId(rs.getInt("id"));
					item.setItem(itemTemplate);
					item.setCount(rs.getInt("count"));
					item.setEquipped(rs.getInt("Is_equipped") != 0 ? true : false);
					item.setEnchantLevel(rs.getInt("enchantlvl"));
					item.setIdentified(rs.getInt("is_id") != 0 ? true : false);
					item.setDurability(rs.getInt("durability"));
					item.setChargeCount(rs.getInt("charge_count"));
					item.setRemainingTime(rs.getInt("remaining_time"));
					item.setLastUsed(rs.getTimestamp("last_used"));
					item.setBless(rs.getInt("bless"));
					item.setAttrEnchantLevel(rs.getInt("attr_enchantlvl"));
					//item.setSpecialEnchant(rs.getInt("special_enchant"));
					item.setEndTime(rs.getTimestamp("end_time"));
					/** 패키지상점 **/
					//item.setPackage(rs.getInt("package") != 0 ? true : false);
					items.put(item.getId(), item);
				}
			} catch (SQLException e) {
				throw e;
			} finally {
				SQLUtil.close(rs);
				SQLUtil.close(pstm);
				SQLUtil.close(con);
				item = null;
				itemTemplate = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	/**
	 * 창고 아이템 리스트
	 */
	ConcurrentHashMap<Integer, ItemInstance> warehouseitems = new ConcurrentHashMap<Integer, ItemInstance>();
 



	public void warehouseList() {
		warehouseitems.clear();
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_warehouse WHERE account_name = ?");
			pstm.setString(1, accountname);
			rs = pstm.executeQuery();
			ItemInstance item = null;
			L1Item itemTemplate = null;
			while (rs.next()) {
				itemTemplate = ItemTable.getInstance().getTemplate(rs.getInt("item_id"));
				item = new ItemInstance();
				int objectId = rs.getInt("id");
				item.setId(objectId);
				item.setItem(itemTemplate);
				item.setCount(rs.getInt("count"));
				item.setEquipped(false);
				item.setEnchantLevel(rs.getInt("enchantlvl"));
				item.setIdentified(rs.getInt("is_id") != 0 ? true : false);
				item.setDurability(rs.getInt("durability"));
				item.setChargeCount(rs.getInt("charge_count"));
				item.setRemainingTime(rs.getInt("remaining_time"));
				item.setLastUsed(rs.getTimestamp("last_used"));
				item.setAttrEnchantLevel(rs.getInt("attr_enchantlvl"));
				item.setBless(rs.getInt("bless"));
				warehouseitems.put(item.getId(), item);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public boolean DBcharInfo(final String name) {
		nf.setMaximumFractionDigits(2);
		nf.setMinimumFractionDigits(2);
		Connection con1 = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con1 = L1DatabaseFactory.getInstance().getConnection();
			pstm = con1.prepareStatement("SELECT * FROM characters WHERE char_name=?");
			pstm.setString(1, name);
			rs = pstm.executeQuery();
			if (!rs.next()) {
				return false;
			}
			String cname = rs.getString("Clanname");

			if (cname == null || cname.equals("")) {
				Clanname = "[가입한 혈맹 없음]";
			} else {
				Clanname = "[" + cname + "]";
			}
			Pcname = name;
			int lv = rs.getInt("level");
			exp = lv + "Lv     " + nf.format(ExpTable.getExpPercentagedouble(lv, rs.getInt("Exp"))) + "%";
			stasts = "Login : OFF";
			hp = "" + rs.getShort("CurHp") + "/" + rs.getShort("MaxHp");
			mp = "" + rs.getShort("CurMp") + "/" + rs.getShort("MaxMp");
			str = "" + rs.getByte("Str") + "/" + rs.getByte("BaseStr");
			dex = "" + rs.getByte("Dex") + "/" + rs.getByte("BaseDex");
			con = "" + rs.getByte("Con") + "/" + rs.getByte("BaseCon");
			wis = "" + rs.getByte("Wis") + "/" + rs.getByte("BaseWis");
			Int = "" + rs.getByte("Intel") + "/" + rs.getByte("BaseIntel");
			cha = "" + rs.getByte("Cha") + "/" + rs.getByte("BaseCha");
			sp = "" + 0;
			mr = "" + 0 + "%";
			er = "" + 0;
			dg = "" + 0;
			//pk = "" + rs.getInt("PC_Kill");
			//deth = "" + rs.getInt("PC_Death");
			if (rs.getTimestamp("lastLogoutTime") != null) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
				String str2 = sdf.format(new Date(rs.getTimestamp("lastLogoutTime").getTime()));
				logindate = str2;
				SimpleDateFormat sdf2 = new SimpleDateFormat("HH시 mm분 ss초", Locale.KOREA);
				String str3 = sdf2.format(new Date(rs.getTimestamp("lastLogoutTime").getTime()));
				logintime = str3;
			} else {
				logindate = "0000-00-00";
				logintime = "00시 00분 00초";
			}
			MaxHp = rs.getShort("MaxHp");
			CurrentHp = rs.getShort("CurHp");
			MaxMp = rs.getShort("MaxMp");
			CurrentMp = rs.getShort("CurMp");
			objid = rs.getInt("objid");
			accountname = rs.getString("account_name");

		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con1);
		}
		return true;
	}

	/**
	 * 계정 정보 취득
	 * 
	 * @param name
	 */
//	public void accountInfo() {
//		accountCharDBInfo();
//		display.syncExec(new Runnable() {
//			@Override
//			public void run() {
//				try {
//					Account account = Account.load(accountname);
//					lblNewLabel_49.setText(account.getName());// 아이디
//					label_27.setText(account.get_Password());// 비번
//					lblNewLabel_42.setText("IP : " + account.getHost());// 아이피
//					if (account.isBanned())
//						btnCheckButton.setSelection(true);// 벤 체크박스
//					else
//						btnCheckButton.setSelection(false);
//					trtmNewTreeitem.removeAll();
//					for (String[] s : charlist.values()) {
//						trtmNewTreeitem_1 = new TreeItem(trtmNewTreeitem, SWT.NONE);
//						trtmNewTreeitem_1.setForeground(SWTResourceManager.getColor(255, 255, 255));
//						trtmNewTreeitem_1.setBackground(SWTResourceManager.getColor(51, 51, 51));
//						trtmNewTreeitem_1.setText(s[0]);
//					}
//				} catch (Exception e) {
//					// e.printStackTrace();
//				}
//			}
//		});
//	}
	
	public void accountCharDBInfo() {
		charlist.clear();
		nf.setMaximumFractionDigits(2);
		nf.setMinimumFractionDigits(2);
		Connection conn = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			conn = L1DatabaseFactory.getInstance().getConnection();
			pstm = conn.prepareStatement("SELECT * FROM characters WHERE account_name=? ORDER BY objid");
			pstm.setString(1, accountname);
			rs = pstm.executeQuery();
			while (rs.next()) {
				String name = rs.getString("char_name");
				String clanname = "[" + rs.getString("Clanname") + "]";
				if (clanname.equals("[]") || clanname.equals("[null]")) {
					clanname = "[가입한 혈맹 없음]";
				}

				int lv = rs.getInt("level");
				String exp = lv + "Lv     " + nf.format(ExpTable.getExpPercentagedouble(lv, rs.getInt("Exp"))) + "%";
				String login = "0000-00-00  00:00:00";
				String loginbefore = "0일전";
				if (rs.getTimestamp("lastLogoutTime") != null) {
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.KOREA);
					String str2 = sdf.format(new Date(rs.getTimestamp("lastLogoutTime").getTime()));
					login = str2;
			//		SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd", Locale.KOREA);
			//		String str3 = sdf2.format(new Date(rs.getTimestamp("lastLogoutTime").getTime()));
//					loginbefore = getDiffDayCount(str3, nowDate()) + "일전";
				}
				charlist.put(name, new String[] { name, clanname, exp, login, loginbefore });
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(conn);
		}
	}
	
	

	/**
	 * 인벤토리 아이템 삭제 함수
	 * 
	 * @param tableitem
	 */
	public void itemdelete(final TableItem[] tableitem) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(Pcname);
			if (target != null) {
				for (TableItem table : tableitem) {
					/** 인벤토리에서 삭제 */
					ItemInstance item = target.getInventory().getItem(Integer.parseInt(table.getText(1)));
					if (item != null) {
						target.getInventory().removeItem(Integer.parseInt(table.getText(1)));
					} else {
						System.out.println("삭제 하려는 아이템이 더이상 인벤토리에 존재 하지 않습니다.");
					}
					/** 아이템 리스트에서 삭제 */
					items.remove(table.getText(1));
				}
				/** 데이터 저장 */
				target.saveInventory();
			} else {
				for (TableItem table : tableitem) {
					ItemInstance temfitem = items.get(Integer.parseInt(table.getText(1)));
					if (temfitem != null) {
						CharactersItemStorage.create().deleteItem(temfitem);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 창고 아이템 삭제
	 * 
	 * @param tableitem
	 */
	public void wherehouseitemdelete(final TableItem[] tableitem) {
		try {
			PrivateWarehouse warehouse = WarehouseManager.getInstance().getPrivateWarehouse(accountname);
			if (warehouse != null) {
				for (TableItem table : tableitem) {
					ItemInstance temfitem = warehouseitems.get(Integer.parseInt(table.getText(1)));
					if (temfitem != null) {
						warehouse.deleteItem2(temfitem);
					}
				}
			} else {
				Connection con = null;
				PreparedStatement pstm = null;
				try {
					con = L1DatabaseFactory.getInstance().getConnection();
					for (TableItem table : tableitem) {
						pstm = con.prepareStatement("DELETE FROM character_warehouse WHERE id = ?");
						pstm.setInt(1, Integer.parseInt(table.getText(1)));
						pstm.execute();
					}
				} catch (SQLException e) {
					e.printStackTrace();
				} finally {
					SQLUtil.close(pstm);
					SQLUtil.close(con);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
