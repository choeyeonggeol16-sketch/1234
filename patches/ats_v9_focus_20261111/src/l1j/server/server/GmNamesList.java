/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
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

public class GmNamesList {
	private static Logger _log = Logger.getLogger(GmNamesList.class.getName());

	private static GmNamesList _instance;

	private ArrayList<String> _nameList = new ArrayList<String>();

	public static GmNamesList getInstance() {
		if (_instance == null) {
			_instance = new GmNamesList();
		}
		return _instance;
	}

	private GmNamesList() {
		LineNumberReader lnr = null;

		try {
			File mobDataFile = new File("data/gmnames.txt");
			lnr = new LineNumberReader(new BufferedReader(new FileReader(
					mobDataFile)));

			String line = null;
			while ((line = lnr.readLine()) != null) {
				if (line.trim().length() == 0 || line.startsWith("#")) {
					continue;
				}
				StringTokenizer st = new StringTokenizer(line, ";");

				_nameList.add(st.nextToken());
			}

			_log.config("loaded " + _nameList.size() + " gm names");
		} catch (FileNotFoundException e) {
			_log.warning("gmnames.txt is missing in data folder");
		} catch (Exception e) {
			_log.warning("error while loading gm names list : " + e);
		} finally {
			StreamUtil.close(lnr);
		}
	}

	public boolean isGmName(String name) {
		for (String gmName : _nameList) {
			if (name.toLowerCase().contains(gmName.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	public String[] getAllGmNames() {
		return _nameList.toArray(new String[_nameList.size()]);
	}
}
