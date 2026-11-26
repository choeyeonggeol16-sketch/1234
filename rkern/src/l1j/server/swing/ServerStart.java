package l1j.server.swing;
 
import java.awt.Color;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import l1j.server.Server;
import l1j.server.server.model.Instance.L1ManagerInstance;

public class ServerStart extends JFrame { 

	private static final long serialVersionUID = 1L;
	
	private static L1ManagerInstance _mngr;
	
	public ServerStart() {
		
		if (_mngr == null) {
			_mngr = L1ManagerInstance.getInstance();
		}
		
		chocco.first.setText("\uB85C\uB529 \uC911");
		chocco.first.setForeground(Color.red);

		if (!Server.startGameServer()) {
			chocco.first.setText("서버 초기화 실패");
			return;
		}
		Server.ClosingMent();
		chocco.first.setText("\uC624\uD508 \uC644\uB8CC");
		chocco.first.setForeground(Color.green);
		JOptionPane.showMessageDialog(null, "\uC11C\uBC84 \uAC00\uB3D9 \uC644\uB8CC", "",
				JOptionPane.INFORMATION_MESSAGE);
		setVisible(false);
		dispose();
	}
}
