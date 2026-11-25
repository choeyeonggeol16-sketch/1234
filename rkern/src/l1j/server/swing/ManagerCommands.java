package l1j.server.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JTextField;

import l1j.server.server.BuilderCmdHandler;
import l1j.server.server.model.Instance.L1ManagerInstance;

@SuppressWarnings("serial")
public class ManagerCommands extends javax.swing.JFrame {
	private static L1ManagerInstance _mngr;
	
	private JLayeredPane 	_layer;
	private JTextField 		_tfCommand;
	private JButton			_btnOk;
	private JButton 		_btnCancel;
	
	public ManagerCommands(int x, int y){
		if(_mngr == null)
			_mngr = L1ManagerInstance.getInstance();
		initComponents();
		setLocation(x, y);
		setSize(200, 80);
		setTitle("명령어 입력");
		setResizable(false);
		setVisible(true);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
	}
	
	private void initComponents() {
		_layer = new JLayeredPane();
		_tfCommand = new JTextField();
		_tfCommand.addKeyListener(new KeyAdapter(){
			@Override public void keyPressed(KeyEvent evt){ onKeyPressed(evt);}
		});
		_layer.add(_tfCommand);

		_btnOk = new JButton();
		_btnOk.setText("실행");
		_btnOk.addActionListener(new ActionListener(){
			@Override public void actionPerformed(ActionEvent evt){ onCommand(evt);}
		});
		_layer.add(_btnOk);

		_btnCancel = new JButton();
		_btnCancel.setText("취소");
		_btnCancel.addActionListener(new ActionListener(){
			@Override public void actionPerformed(ActionEvent evt){ onCancel(evt);}
		});
		_layer.add(_btnCancel);

		_tfCommand.setBounds(0, 0, 200, 20);
		_btnOk.setBounds(5, 30, 80, 20);
		_btnCancel.setBounds(110, 30, 80, 20);
		javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
		getContentPane().setLayout(layout);
		layout.setHorizontalGroup(
				layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addGroup(layout.createSequentialGroup()
				.addComponent(_layer, javax.swing.GroupLayout.DEFAULT_SIZE, 200, javax.swing.GroupLayout.DEFAULT_SIZE))
		);
		layout.setVerticalGroup(
				layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addGroup(layout.createSequentialGroup()
				.addComponent(_layer, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE))
		);
		pack();
		
	}
	
	private void onKeyPressed(KeyEvent evt){
		if(evt.getKeyCode() == KeyEvent.VK_ENTER){
			onCommand(null);
		}
	}
	
	private void onCommand(ActionEvent evt){
		String s = _tfCommand.getText();
		if(s == null || s.equalsIgnoreCase("")){
			MessageBox.show(this, "명령어가 선택되지 않았습니다.", true);
			return;
		}
		try{
			BuilderCmdHandler.getInstance().BuilderCmd2Handler(_mngr, s);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			//setVisible(false);
			//dispose();
		}
	}
	
	private void onCancel(ActionEvent evt){
		setVisible(false);
		dispose();
		chocco._isManagerCommands = false;
	}
}
