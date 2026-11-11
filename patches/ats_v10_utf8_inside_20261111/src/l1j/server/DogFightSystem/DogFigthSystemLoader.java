package l1j.server.DogFightSystem;

import l1j.server.server.GeneralThreadPool;

public class DogFigthSystemLoader {
	private static DogFigthSystemLoader m_instance;
	public static DogFigthSystemLoader getInstance(){
		if(m_instance == null)
			m_instance = new DogFigthSystemLoader();
		return m_instance;
	}

	private DogFightController m_thread;
	private DogFigthSystemLoader(){
		DogFightSystemSettings.DogfightInfoLoad();
		DogFightCharacters.do_load();
		if(DogFightSystemSettings.DogfightInfo.isRun){
			GeneralThreadPool.getInstance().schedule(new Runnable(){
				@Override
				public void run(){
					if(!is_run_game())
						do_run_game();					
				}
			}, 10000);
		}
	}
	
	public void close_game(){
		DogFightController.racing_im = true;
	}
	
	public void do_run_game(){
		DogFightController.racing_im = false;
		m_thread = DogFightController.getInstance();
		GeneralThreadPool.getInstance().execute(m_thread);
	}
	
	public boolean is_run_game(){
		return m_thread != null;
	}
	
	private int _LastTicketNumber = 100000000;
	
	public int getLastTicketNumber() {
		return _LastTicketNumber;
	}
	
	public void setLastTicketNumber(int value) {
		_LastTicketNumber = value;
	}
}


