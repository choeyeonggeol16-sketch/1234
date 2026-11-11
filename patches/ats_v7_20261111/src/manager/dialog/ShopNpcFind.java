package manager.dialog;

import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.shop.Shop;
import manager.SJManager;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class ShopNpcFind extends Dialog {

	protected Object result;
	protected Shell shell;
	private Text text_1;
	public static Display display;

	static private String title = "상점 엔피씨 찾기";

	/**
	 * Create the dialog.
	 * 
	 * @param parent
	 * @param style
	 */
	public ShopNpcFind(Shell parent, int style) {
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
		shell.setSize(270, 351);
		shell.setText("상점 엔피씨 찾기");
		// 화면중앙으로
		display = Display.getDefault();
		shell.setBounds((display.getBounds().width / 2) - (shell.getBounds().width / 2),
				(display.getBounds().height / 2) - (shell.getBounds().height / 2), shell.getBounds().width,
				shell.getBounds().height);
		GridLayout gl_shell = new GridLayout(3, false);
		gl_shell.horizontalSpacing = 10;
		gl_shell.marginWidth = 10;
		gl_shell.marginHeight = 10;
		shell.setLayout(gl_shell);

		Label lblNewLabel_2 = new Label(shell, SWT.NONE);
		lblNewLabel_2.setText("검색명");

		text_1 = new Text(shell, SWT.BORDER);
		text_1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		text_1.setEditable(true);

		Button lblNewButton = new Button(shell, SWT.PUSH);
		lblNewButton.setText("검 색");

		List list = new List(shell, SWT.BORDER | SWT.V_SCROLL | SWT.SINGLE);
		GridData gd_list = new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1);
		list.setTouchEnabled(true);
		list.setItems(new String[] {});
		gd_list.heightHint = 272;
		list.setLayoutData(gd_list);

		Listener listener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				switch (event.type) {
				case SWT.MouseDoubleClick:
					String _selectName = list.getItem(list.getSelectionIndex());
					if (_selectName == null)
						return;
					String _name = _selectName.substring(_selectName.indexOf("[") + 1, _selectName.lastIndexOf("]"));
					L1NpcInstance npc = L1World.getInstance().findNpc(Integer.valueOf(_name));
					if (npc != null) {
						ShopEdit.open(npc.getNpcTemplate());
						close();
					} else {
						SJManager.toMessageBox(title, "월드에 존재하지 않는 엔피씨입니다.");
						close();
					}
					break;
				}
			}
		};
		list.addListener(SWT.MouseDoubleClick, listener);

		// 이벤트 등록.
		text_1.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.keyCode == 13 || e.keyCode == 16777296)
					// 검색
					toSearchItem(text_1, list);
			}
		});

		lblNewButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// 검색
				toSearchItem(text_1, list);
			}
		});

		for (L1NpcInstance npc : L1World.getInstance().getAllNpc()) {
			Shop npcshop = ShopTable.getInstance().get(npc.getNpcId());
			if (npcshop != null) {
				list.add("[" + npc.getNpcId() + "] " + npc.getName());
			}
		}
	}

	private void close() {
		shell.dispose();
	}

	static private void toSearchItem(Text text, List list) {
		String name = text.getText().toLowerCase();

		// 이전 기록 제거
		list.removeAll();

		// 검색명이 없을경우 전체 표현.
		if (name == null || name.length() <= 0) {
			for (L1NpcInstance npc : L1World.getInstance().getAllNpc()) {
				Shop npcshop = ShopTable.getInstance().get(npc.getNpcId());
				if (npcshop != null) {
					list.add("[" + npc.getNpcId() + "] " + npc.getName());
				}
			}
			return;
		}

		for (L1NpcInstance npc : L1World.getInstance().getAllNpc()) {
			Shop npcshop = ShopTable.getInstance().get(npc.getNpcId());
			if (npcshop != null) {
				int pos = npc.getName().toLowerCase().indexOf(name);
				if (pos >= 0) {
					list.add("[" + npc.getNpcId() + "] " + npc.getName());
				}
			}
		}

		// 등록된게 없을경우 안내 멘트.
		if (list.getItemCount() <= 0)
			SJManager.toMessageBox(title, "일치하는 아이템이 없습니다.");

		// 포커스.
		text.setFocus();
	}
}
