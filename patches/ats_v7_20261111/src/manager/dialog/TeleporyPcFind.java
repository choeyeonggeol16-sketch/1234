package manager.dialog;

import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import manager.SJManager;

public class TeleporyPcFind extends Dialog {

	protected Object result;
	protected Shell shell;
	private Text text_1;
	public static Display display;
	protected Combo class_select;
	protected static List user_list;
	protected static int classType = 8;
	static private String title = "상점 엔피씨 찾기";

	/**
	 * Create the dialog.
	 * 
	 * @param parent
	 * @param style
	 */
	public TeleporyPcFind(Shell parent, int style) {
		super(parent, style);
		setText("SWT Dialog");
	}

	/**
	 * Open the dialog.
	 * 
	 * @return the result
	 */
	public Object open() {
		createContents();
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
	private void createContents() {

		shell = new Shell(getParent(), getStyle());
		shell.setSize(337, 400);
		shell.setText("유저 텔레포트");
		// 화면중앙으로
		display = Display.getDefault();
		shell.setBounds((display.getBounds().width / 2) - (shell.getBounds().width / 2),
				(display.getBounds().height / 2) - (shell.getBounds().height / 2), shell.getBounds().width,
				shell.getBounds().height);
		GridLayout gl_shell = new GridLayout(4, false);
		gl_shell.horizontalSpacing = 10;
		gl_shell.marginWidth = 10;
		gl_shell.marginHeight = 10;
		shell.setLayout(gl_shell);

		Label lblNewLabel_2 = new Label(shell, SWT.NONE);
		lblNewLabel_2.setText("검색명");

		text_1 = new Text(shell, SWT.BORDER);
		GridData gd_text_1 = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gd_text_1.widthHint = 98;
		text_1.setLayoutData(gd_text_1);
		text_1.setEditable(true);

		Button lblNewButton = new Button(shell, SWT.PUSH);
		lblNewButton.setText("검 색");

		Button btnNewButton = new Button(shell, SWT.NONE);
		GridData gd_btnNewButton = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_btnNewButton.widthHint = 54;
		btnNewButton.setLayoutData(gd_btnNewButton);
		btnNewButton.setText("다 음");

		btnNewButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (user_list.getSelectionCount() <= 0) {
					SJManager.toMessageBox("선택된 유저가 없습니다.");
					return;
				}
				java.util.List<String> data = new ArrayList<String>();

				for (String vegetable : user_list.getSelection()) {
					data.add(vegetable);
				}

				if (data.size() <= 0) {
					SJManager.toMessageBox("선택된 유저가 없습니다.");
					return;
				}

				// 다음
				SJManager.getInstance();
				TeleportPc dialog = new TeleportPc(SJManager.getShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
				dialog.open(data);
				close();
			}
		});

		class_select = new Combo(shell, SWT.NONE);
		class_select.setItems(new String[] { "군주", "기사", "요정", "법사", "다크엘프", "용기사", "환술사", "전사", "전체" });
		class_select.addSelectionListener(event_list_map_select);
		class_select.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		new Label(shell, SWT.NONE);
		new Label(shell, SWT.NONE);

		user_list = new List(shell, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		GridData gd_list = new GridData(SWT.FILL, SWT.CENTER, false, false, 4, 1);
		user_list.setTouchEnabled(true);
		user_list.setItems(new String[] {});
		gd_list.heightHint = 291;
		user_list.setLayoutData(gd_list);

		// 이벤트 등록.
		text_1.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.keyCode == 13 || e.keyCode == 16777296)
					// 검색
					toSearchItem(text_1, user_list);
			}
		});

		lblNewButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// 검색
				toSearchItem(text_1, user_list);
			}
		});

		for (L1PcInstance target : L1World.getInstance().getAllPlayers()) {
			user_list.add(target.getName());
		}
	}

	private void close() {
		shell.dispose();
	}

	// 컨트롤러에 있는 맵 하나 선택할때 처리하는 변수.
	private SelectionAdapter event_list_map_select = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent e) {
			try {
				user_list.removeAll();
				String className = class_select.getItem(class_select.getSelectionIndex());
				if (className.equalsIgnoreCase("군주"))
					classType = 0;
				if (className.equalsIgnoreCase("기사"))
					classType = 1;
				if (className.equalsIgnoreCase("요정"))
					classType = 2;
				if (className.equalsIgnoreCase("법사"))
					classType = 3;
				if (className.equalsIgnoreCase("다크엘프"))
					classType = 4;
				if (className.equalsIgnoreCase("용기사"))
					classType = 5;
				if (className.equalsIgnoreCase("환술사"))
					classType = 6;
				if (className.equalsIgnoreCase("전사"))
					classType = 7;
				if (className.equalsIgnoreCase("전체"))
					classType = 8;

				if (classType != 8) {
					for (L1PcInstance target : L1World.getInstance().getAllPlayers()) {
						if (target.getType() == classType) {
							user_list.add(target.getName());
						}
					}
				} else {
					for (L1PcInstance target : L1World.getInstance().getAllPlayers()) {
						user_list.add(target.getName());
					}
				}
			} catch (Exception e2) {
			}
		}
	};

	static private void toSearchItem(Text text, List list) {
		String name = text.getText().toLowerCase();

		// 이전 기록 제거
		list.removeAll();

		// 검색명이 없을경우 전체 표현.
		if (name == null || name.length() <= 0) {
			if (classType != 8) {
				for (L1PcInstance target : L1World.getInstance().getAllPlayers()) {
					if (target.getType() == classType) {
						user_list.add(target.getName());
					}
				}
			} else {
				for (L1PcInstance target : L1World.getInstance().getAllPlayers()) {
					user_list.add(target.getName());
				}
			}
			return;
		}

		for (L1PcInstance target : L1World.getInstance().getAllPlayers()) {
			int pos = target.getName().toLowerCase().indexOf(name);
			if (pos >= 0) {
				if (classType != 8) {
					if (target.getType() == classType) {
						user_list.add(target.getName());
					}
				} else {
					user_list.add(target.getName());
				}
			}
		}

		// 등록된게 없을경우 안내 멘트.
		if (list.getItemCount() <= 0)
			SJManager.toMessageBox(title, "일치하는 유저가 없습니다.");

		// 포커스.
		text.setFocus();
	}
}
