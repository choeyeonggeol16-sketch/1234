/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 * 
 */
package l1j.server.server;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastList;
import l1j.server.Config;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class Announcecycle {
	private static Logger _log = Logger
			.getLogger(Announcecycle.class.getName());

	private static Announcecycle _instance;

	private FastList<String> _Announcecycle;

	private int _Announcecyclesize = 0;

	private Announcecycle() {
		loadAnnouncecycle();
	}

	public static Announcecycle getInstance() {
		if (_instance == null) {
			_instance = new Announcecycle();
		}

		return _instance;
	}

	public void reloadAnnouncecycle(){
		File file = new File("data/Announcecycle.txt");
		if (file.exists()) {
			FastList<String> tmp = _Announcecycle;
			_Announcecycle = readFromDiskmulti(file);
			if(tmp != null){
				tmp.clear();
				tmp = null;
			}
		} else {
			_log.config("data/Announcecycle.txt");
		}	
	}
	
	public void loadAnnouncecycle() {
		File file = new File("data/Announcecycle.txt");
		if (file.exists()) {
			FastList<String> tmp = _Announcecycle;
			_Announcecycle = readFromDiskmulti(file);
			if(tmp != null){
				tmp.clear();
				tmp = null;
			}
			doAnnouncecycle();
		} else {
			_log.config("data/Announcecycle.txt");
		}
	}

	private FastList<String> readFromDiskmulti(File file) {
		FastList<String> list = new FastList<String>();
		LineNumberReader lnr = null;
		try {
			int i = 0;
			String line = null;
			lnr = new LineNumberReader(new FileReader(file));
			while ((line = lnr.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(line, "\n\r");
				if (st.hasMoreTokens()) {
					String showAnnouncecycle = st.nextToken();
					list.add(showAnnouncecycle);
					i++;
				}
			}

			_log.config("Announcecycle: Loaded " + i + " Announcecycle.");
		} catch (IOException e1) {
			_log.log(Level.SEVERE, "Error reading Announcecycle", e1);
        } finally {
            if (lnr != null) {
                try { lnr.close(); }
                catch (IOException e) { _log.log(Level.WARNING, "Announcecycle close failed", e); }
            }
        }
		return list;
	}

	public void doAnnouncecycle() {
		AnnouncTask rs = new AnnouncTask();
		ThreadPool.getInstance().scheduleAtFixedRateRequired(rs, 180000,	60000 * Config.Show_Announcecycle_Time);

	}

	/** The task launching the function doAnnouncCycle() */
	class AnnouncTask implements Runnable {
		public void run() {
			try {
				ShowAnnounceToAll(_Announcecycle.get(_Announcecyclesize));
				_Announcecyclesize++;
				if (_Announcecyclesize >= _Announcecycle.size())
					_Announcecyclesize = 0;
			} catch (Exception e) {
				_log.log(Level.WARNING, "", e);
			}
		}
	}

	private void ShowAnnounceToAll(String msg) {
		Collection<L1PcInstance> allpc = L1World.getInstance().getAllPlayers();
		
		for (L1PcInstance pc : allpc)
			SC_SystemMessage.send(pc, msg);
	}
}
