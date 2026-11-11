package manager.dialog;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import manager.SWTResourceManager;

public class TeleportPc extends Dialog {

	protected Object result;
	protected Shell shell;
	private Text loc_x;
	private Text loc_y;
	private Text loc_map;

	private String setName = "";
	
	public String getSetName() {
		return setName;
	}

	public void setSetName(String setName) {
		this.setName = setName;
	}

	public static Display display;

	/**
	 * Create the dialog.
	 * 
	 * @param parent
	 * @param style
	 */
	public TeleportPc(Shell parent, int style) {
		super(parent, style);
		setText("SWT Dialog");
	}

	/**
	 * Open the dialog.
	 * 
	 * @return the result
	 */
	public Object open(java.util.List<String> data) {
		createContents(data);
		shell.open();
		shell.layout();
		Display display = getParent().getDisplay();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		return result;
	}

	/**
	 * Create contents of the dialog.
	 */
	private void createContents(java.util.List<String> user_list) {
		shell = new Shell(getParent(), getStyle());
		shell.setSize(553, 465);
		shell.setText("유저 텔레포트");
		display = Display.getDefault();
		shell.setBounds((display.getBounds().width / 2) - (shell.getBounds().width / 2),
				(display.getBounds().height / 2) - (shell.getBounds().height / 2), shell.getBounds().width,
				shell.getBounds().height);

		CTabFolder tabFolder = new CTabFolder(shell, SWT.BORDER);
		tabFolder.setTabHeight(19);
		tabFolder.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		tabFolder.setBounds(124, 72, 413, 355);
		tabFolder.setSelectionBackground(SWTResourceManager.getColor(SWT.COLOR_TITLE_BACKGROUND_GRADIENT));

		CTabItem tbtmSafe = new CTabItem(tabFolder, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		tbtmSafe.setText("  마 을  ");

		Composite composite_1 = new Composite(tabFolder, SWT.NONE);
		composite_1.setFont(SWTResourceManager.getFont("맑은 고딕", 9, SWT.NORMAL));
		tbtmSafe.setControl(composite_1);
		composite_1.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));

		Group group = new Group(composite_1, SWT.NONE);
		group.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.BOLD));
		group.setText("목록");
		group.setTouchEnabled(true);
		group.setForeground(SWTResourceManager.getColor(SWT.COLOR_TITLE_FOREGROUND));
		group.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		group.setBounds(10, 10, 387, 309);

		tabFolder.setSelection(tbtmSafe);

		Button btnRadioButton = new Button(group, SWT.RADIO);
		btnRadioButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton);
			}
		});
		btnRadioButton.setBounds(10, 20, 91, 16);
		btnRadioButton.setText("글루딘");

		Button btnRadioButton_1 = new Button(group, SWT.RADIO);
		btnRadioButton_1.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_1);
			}
		});
		btnRadioButton_1.setBounds(10, 42, 91, 16);
		btnRadioButton_1.setText("켄트");

		Button btnRadioButton_2 = new Button(group, SWT.RADIO);
		btnRadioButton_2.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_2);
			}
		});
		btnRadioButton_2.setBounds(144, 42, 91, 16);
		btnRadioButton_2.setText("우드벡");

		Button btnRadioButton_3 = new Button(group, SWT.RADIO);
		btnRadioButton_3.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_3);
			}
		});
		btnRadioButton_3.setBounds(10, 64, 91, 16);
		btnRadioButton_3.setText("하이네");

		Button btnRadioButton_4 = new Button(group, SWT.RADIO);
		btnRadioButton_4.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_4);
			}
		});
		btnRadioButton_4.setBounds(10, 86, 91, 16);
		btnRadioButton_4.setText("은기사");

		Button btnRadioButton_5 = new Button(group, SWT.RADIO);
		btnRadioButton_5.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_5);
			}
		});
		btnRadioButton_5.setBounds(10, 108, 91, 16);
		btnRadioButton_5.setText("기란");

		Button btnRadioButton_6 = new Button(group, SWT.RADIO);
		btnRadioButton_6.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_6);
			}
		});
		btnRadioButton_6.setBounds(144, 64, 91, 16);
		btnRadioButton_6.setText("화전민");

		Button btnRadioButton_7 = new Button(group, SWT.RADIO);
		btnRadioButton_7.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_7);
			}
		});
		btnRadioButton_7.setBounds(286, 64, 91, 16);
		btnRadioButton_7.setText("말하는 섬");

		Button btnRadioButton_8 = new Button(group, SWT.RADIO);
		btnRadioButton_8.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_8);
			}
		});
		btnRadioButton_8.setBounds(144, 86, 91, 16);
		btnRadioButton_8.setText("웰던");

		Button btnRadioButton_9 = new Button(group, SWT.RADIO);
		btnRadioButton_9.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_9);
			}
		});
		btnRadioButton_9.setBounds(144, 108, 91, 16);
		btnRadioButton_9.setText("오렌");

		Button btnRadioButton_10 = new Button(group, SWT.RADIO);
		btnRadioButton_10.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_10);
			}
		});
		btnRadioButton_10.setBounds(286, 86, 91, 16);
		btnRadioButton_10.setText("실베리아");

		Button btnRadioButton_11 = new Button(group, SWT.RADIO);
		btnRadioButton_11.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_11);
			}
		});
		btnRadioButton_11.setBounds(286, 108, 91, 16);
		btnRadioButton_11.setText("베히모스");

		Button btnRadioButton_12 = new Button(group, SWT.RADIO);
		btnRadioButton_12.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_12);
			}
		});
		btnRadioButton_12.setBounds(10, 130, 91, 16);
		btnRadioButton_12.setText("침묵의 동굴");

		Button btnRadioButton_13 = new Button(group, SWT.RADIO);
		btnRadioButton_13.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_13);
			}
		});
		btnRadioButton_13.setBounds(286, 42, 91, 16);
		btnRadioButton_13.setText("클라우디아");

		Button btnRadioButton_14 = new Button(group, SWT.RADIO);
		btnRadioButton_14.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_14);
			}
		});
		btnRadioButton_14.setBounds(144, 20, 91, 16);
		btnRadioButton_14.setText("아덴");

		Button btnRadioButton_15 = new Button(group, SWT.RADIO);
		btnRadioButton_15.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_15);
			}
		});
		btnRadioButton_15.setBounds(286, 20, 91, 16);
		btnRadioButton_15.setText("수상한 정원");

		CTabItem tbtmDongeon = new CTabItem(tabFolder, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		tbtmDongeon.setText("  던 전  ");

		Composite composite_2 = new Composite(tabFolder, SWT.NONE);
		composite_2.setFont(SWTResourceManager.getFont("맑은 고딕", 9, SWT.NORMAL));
		tbtmDongeon.setControl(composite_2);
		composite_2.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));

		Group group_2 = new Group(composite_2, SWT.NONE);
		group_2.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.BOLD));
		group_2.setText("목록");
		group_2.setTouchEnabled(true);
		group_2.setForeground(SWTResourceManager.getColor(SWT.COLOR_TITLE_FOREGROUND));
		group_2.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		group_2.setBounds(10, 10, 387, 309);

		Button btnRadioButton_16 = new Button(group_2, SWT.RADIO);
		btnRadioButton_16.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_16);
			}
		});
		btnRadioButton_16.setBounds(10, 20, 91, 16);
		btnRadioButton_16.setText("그림자 신전");

		Button btnRadioButton_17 = new Button(group_2, SWT.RADIO);
		btnRadioButton_17.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_17);
			}
		});
		btnRadioButton_17.setBounds(286, 20, 91, 16);
		btnRadioButton_17.setText("상아탑 던전");

		Button btnRadioButton_18 = new Button(group_2, SWT.RADIO);
		btnRadioButton_18.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_18);
			}
		});
		btnRadioButton_18.setBounds(10, 42, 101, 16);
		btnRadioButton_18.setText("용의 계곡 던전");

		Button btnRadioButton_19 = new Button(group_2, SWT.RADIO);
		btnRadioButton_19.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_19);
			}
		});
		btnRadioButton_19.setBounds(144, 42, 91, 16);
		btnRadioButton_19.setText("에바 왕국");

		Button btnRadioButton_20 = new Button(group_2, SWT.RADIO);
		btnRadioButton_20.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_20);
			}
		});
		btnRadioButton_20.setBounds(286, 42, 91, 16);
		btnRadioButton_20.setText("수련 던전");

		Button btnRadioButton_21 = new Button(group_2, SWT.RADIO);
		btnRadioButton_21.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_21);
			}
		});
		btnRadioButton_21.setBounds(10, 64, 91, 16);
		btnRadioButton_21.setText("욕망의 동굴");

		Button btnRadioButton_22 = new Button(group_2, SWT.RADIO);
		btnRadioButton_22.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_22);
			}
		});
		btnRadioButton_22.setBounds(144, 64, 91, 16);
		btnRadioButton_22.setText("글루디오 던전");

		Button btnRadioButton_23 = new Button(group_2, SWT.RADIO);
		btnRadioButton_23.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_23);
			}
		});
		btnRadioButton_23.setBounds(286, 64, 91, 16);
		btnRadioButton_23.setText("요정숲 던전");

		Button btnRadioButton_33 = new Button(group_2, SWT.RADIO);
		btnRadioButton_33.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_33);
			}
		});
		btnRadioButton_33.setBounds(144, 20, 91, 16);
		btnRadioButton_33.setText("말섬 던전");

		CTabItem tbtmFiled = new CTabItem(tabFolder, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		tbtmFiled.setText("  필 드  ");

		Composite composite_3 = new Composite(tabFolder, SWT.NONE);
		composite_3.setFont(SWTResourceManager.getFont("맑은 고딕", 9, SWT.NORMAL));
		tbtmFiled.setControl(composite_3);
		composite_3.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));

		Group group_3 = new Group(composite_3, SWT.NONE);
		group_3.setFont(SWTResourceManager.getFont("맑은 고딕", 10, SWT.BOLD));
		group_3.setText("목록");
		group_3.setTouchEnabled(true);
		group_3.setForeground(SWTResourceManager.getColor(SWT.COLOR_TITLE_FOREGROUND));
		group_3.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		group_3.setBounds(10, 10, 387, 309);

		Button btnRadioButton_24 = new Button(group_3, SWT.RADIO);
		btnRadioButton_24.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_24);
			}
		});
		btnRadioButton_24.setBounds(10, 20, 91, 16);
		btnRadioButton_24.setText("엘모어 격전지");

		Button btnRadioButton_25 = new Button(group_3, SWT.RADIO);
		btnRadioButton_25.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_25);
			}
		});
		btnRadioButton_25.setBounds(286, 20, 91, 16);
		btnRadioButton_25.setText("풍룡의 둥지");

		Button btnRadioButton_26 = new Button(group_3, SWT.RADIO);
		btnRadioButton_26.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_26);
			}
		});
		btnRadioButton_26.setBounds(10, 42, 91, 16);
		btnRadioButton_26.setText("황혼의 산맥");

		Button btnRadioButton_27 = new Button(group_3, SWT.RADIO);
		btnRadioButton_27.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_27);
			}
		});
		btnRadioButton_27.setBounds(144, 20, 91, 16);
		btnRadioButton_27.setText("거울의 숲");

		Button btnRadioButton_28 = new Button(group_3, SWT.RADIO);
		btnRadioButton_28.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_28);
			}
		});
		btnRadioButton_28.setBounds(144, 42, 91, 16);
		btnRadioButton_28.setText("밀림지대");

		Button btnRadioButton_29 = new Button(group_3, SWT.RADIO);
		btnRadioButton_29.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_29);
			}
		});
		btnRadioButton_29.setBounds(286, 42, 91, 16);
		btnRadioButton_29.setText("화룡의 둥지");

		Button btnRadioButton_30 = new Button(group_3, SWT.RADIO);
		btnRadioButton_30.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_30);
			}
		});
		btnRadioButton_30.setBounds(10, 64, 91, 16);
		btnRadioButton_30.setText("용의 계곡");

		Button btnRadioButton_31 = new Button(group_3, SWT.RADIO);
		btnRadioButton_31.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_31);
			}
		});
		btnRadioButton_31.setBounds(144, 64, 91, 16);
		btnRadioButton_31.setText("암흑룡의 상흔");

		Button btnRadioButton_32 = new Button(group_3, SWT.RADIO);
		btnRadioButton_32.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_32);
			}
		});
		btnRadioButton_32.setBounds(286, 64, 91, 16);
		btnRadioButton_32.setText("하이네 필드");

		Button btnRadioButton_34 = new Button(group_3, SWT.RADIO);
		btnRadioButton_34.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_34);
			}
		});
		btnRadioButton_34.setBounds(10, 86, 91, 16);
		btnRadioButton_34.setText("사막 필드");

		Button btnRadioButton_35 = new Button(group_3, SWT.RADIO);
		btnRadioButton_35.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_35);
			}
		});
		btnRadioButton_35.setBounds(144, 86, 91, 16);
		btnRadioButton_35.setText("죽음의 폐허");

		Button btnRadioButton_36 = new Button(group_3, SWT.RADIO);
		btnRadioButton_36.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_36);
			}
		});
		btnRadioButton_36.setBounds(286, 86, 91, 16);
		btnRadioButton_36.setText("오크부락");

		Button btnRadioButton_37 = new Button(group_3, SWT.RADIO);
		btnRadioButton_37.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getTeleportSetting(btnRadioButton_37);
			}
		});
		btnRadioButton_37.setBounds(10, 108, 91, 16);
		btnRadioButton_37.setText("거미숲");

		Label lblNewLabel = new Label(shell, SWT.NONE);
		lblNewLabel.setAlignment(SWT.CENTER);
		lblNewLabel.setBounds(10, 39, 35, 15);
		lblNewLabel.setText("LocX");

		loc_x = new Text(shell, SWT.BORDER);
		loc_x.setBounds(51, 36, 73, 21);

		Label lblNewLabel_1 = new Label(shell, SWT.NONE);
		lblNewLabel_1.setAlignment(SWT.CENTER);
		lblNewLabel_1.setBounds(130, 39, 30, 15);
		lblNewLabel_1.setText("LocY");

		loc_y = new Text(shell, SWT.BORDER);
		loc_y.setBounds(166, 36, 73, 21);

		Label lblNewLabel_2 = new Label(shell, SWT.NONE);
		lblNewLabel_2.setAlignment(SWT.CENTER);
		lblNewLabel_2.setBounds(245, 39, 45, 15);
		lblNewLabel_2.setText("MapID");

		loc_map = new Text(shell, SWT.BORDER);
		loc_map.setBounds(296, 36, 73, 21);

		List list = new List(shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		list.setBounds(10, 101, 108, 325);

		Label lblNewLabel_3 = new Label(shell, SWT.NONE);
		lblNewLabel_3.setAlignment(SWT.CENTER);
		lblNewLabel_3.setBounds(10, 18, 359, 15);
		lblNewLabel_3.setText("\uC138\uD305 \uC88C\uD45C");

		Label lblUserddd = new Label(shell, SWT.NONE);
		lblUserddd.setAlignment(SWT.CENTER);
		lblUserddd.setFont(SWTResourceManager.getFont("맑은 고딕", 14, SWT.BOLD));
		lblUserddd.setBounds(10, 72, 108, 23);
		lblUserddd.setText("유저목록");

		for (String s : user_list) {
			list.add(s);
		}
	}

	public void getTeleportSetting(Button button) {
		setName = button.getText();
 
	}
}
