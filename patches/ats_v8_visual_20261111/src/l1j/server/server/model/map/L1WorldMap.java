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
package l1j.server.server.model.map;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.MapReader;
import l1j.server.server.datatables.MapsTable;


public class L1WorldMap {
	private static Logger _log = Logger.getLogger(L1WorldMap.class.getName());

	private static L1WorldMap _instance;
	private Map<Integer, L1Map> _maps;

	public static L1WorldMap getInstance() {
		if (_instance == null) {
			_instance = new L1WorldMap();
		}
		return _instance;
	}

	private L1WorldMap() {
		MapReader in = MapReader.getDefaultReader();
		try {
			_maps = in.read();
			if (_maps == null) {
				throw new RuntimeException("MAP의 read에 실패");
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);

			System.exit(0);
		}
	}

	/**
	 * 지정된 MAP의 정보를 보관 유지하는 L1Map를 돌려준다.
	 * 
	 * @param mapId
	 *            MAP ID
	 * @return MAP 정보를 보관 유지하는, L1Map 오브젝트.
	 */
	public L1Map getMap(short mapId) {
		L1Map map = _maps.get((int) mapId);
		if (map == null) { 
			map = L1Map.newNull(); 
		}
		return map;
	}

	public void cloneMap(int targetId, int newId){//레이드
		L1Map copymap = null;
		if(_maps.containsKey(newId)==false){
		copymap = _maps.get(targetId).copyMap(newId);
		_maps.put(newId, copymap);
		}
		
		if(MapsTable.getInstance().getMaplist().containsKey(newId) == false)
			MapsTable.getInstance().clone(targetId, newId);
	}
	public L1Map cloneMapAndGet(int targetId, int newId) {
		L1Map copymap = null;
		copymap = _maps.get(targetId).copyMap(newId);	
		_maps.put(newId, copymap);
		return copymap;
	}
	public synchronized void removeMap(int mapId) {
		_maps.remove(mapId);
	}

}
