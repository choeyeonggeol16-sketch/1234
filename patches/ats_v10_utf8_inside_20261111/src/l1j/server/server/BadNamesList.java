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
 */
package l1j.server.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.server.utils.StreamUtil;

public class BadNamesList {
	private static Logger _log = Logger.getLogger(BadNamesList.class.getName());

	private static BadNamesList _instance;

	private ArrayList<String> _nameList = new ArrayList<String>();

	private ArrayList<String> _badText = new ArrayList<String>();

	private ArrayList<String> _MailText = new ArrayList<String>();

	public static BadNamesList getInstance() {
		if (_instance == null) {
			_instance = new BadNamesList();
		}
		return _instance;
	}

	public static void reload() {
		BadNamesList oldInstance = _instance;
		_instance = new BadNamesList();
		oldInstance._badText.clear();
		oldInstance._MailText.clear();
	}

	private BadNamesList() {
		badnameLoading();
		badTextLoading();
		//MailTextLoading();
	}

	private void badnameLoading() {
		LineNumberReader lnr = null;

		try {
			File mobDataFile = new File("data/badnames.txt");
			lnr = new LineNumberReader(new BufferedReader(new FileReader(mobDataFile)));

			String line = null;
			StringTokenizer st = null;
			while ((line = lnr.readLine()) != null) {
				if (line.trim().length() == 0 || line.startsWith("#")) {
					continue;
				}
				st = new StringTokenizer(line, ";");

				_nameList.add(st.nextToken());
			}

			_log.config("loaded " + _nameList.size() + " bad names");
		} catch (FileNotFoundException e) {
			_log.warning("badnames.txt is missing in data folder");
		} catch (Exception e) {
			_log.warning("error while loading bad names list : " + e);
		} finally {
			StreamUtil.close(lnr);
		}
	}

	public boolean isBadName(String name) {
		for (String badName : _nameList) {
			if (name.toLowerCase().contains(badName.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	public void addBadName(String name) {
		_nameList.add(name);
	}

	public String[] getAllBadNames() {
		return _nameList.toArray(new String[_nameList.size()]);
	}

	/** 전체창 욕설시 자동 채금시스템 sunny **/
	public boolean isBadText(String name) {
		for (String badText : _badText) {
			if (name.toLowerCase().contains(badText.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	public boolean isMailText(String name) {
		for (String MailText : _MailText) {
			if (name.toLowerCase().contains(MailText.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	public void addBadText(String Text) {
		_badText.add(Text);
	}

	public void addMailText(String Text) {
		_MailText.add(Text);
	}

	public String[] getAllBadText() {
		return _badText.toArray(new String[_badText.size()]);
	}

	public String[] getAllMailText() {
		return _MailText.toArray(new String[_MailText.size()]);
	}
	/** 전체창 욕설시 자동 채금시스템 sunny **/
	private void badTextLoading() {
		LineNumberReader lnr = null;

		try {
			File mobDataFile = new File("data/badtexts.txt");
			lnr = new LineNumberReader(new BufferedReader(new FileReader(mobDataFile)));

			String line = null;
			StringTokenizer st = null;
			while ((line = lnr.readLine()) != null) {
				if (line.trim().length() == 0 || line.startsWith("#")) {
					continue;
				}
				st = new StringTokenizer(line, ";");

				_badText.add(st.nextToken());
			}

			_log.config("loaded " + _nameList.size() + " bad texts");
		} catch (FileNotFoundException e) {
			_log.warning("badtexts.txt is missing in data folder");
		} catch (Exception e) {
			_log.warning("error while loading bad texts list : " + e);
		} finally {
			StreamUtil.close(lnr);
		}
	}
}
