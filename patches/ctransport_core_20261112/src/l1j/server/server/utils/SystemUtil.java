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
package l1j.server.server.utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.StringTokenizer;

public class SystemUtil {
	/**
	 * 시스템이 이용중의 heap 사이즈를 메가바이트 단위로 돌려준다.<br>
	 * 이 값에 스택의 사이즈는 포함되지 않는다.
	 * 
	 * @return 이용중의 heap 사이즈 
	 */
	public static long getUsedMemoryMB() {
		return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576;
	}
	
	static private Date	date = new Date(0);
	static private String[] weekDay = {"일", "월", "화", "수", "목", "금", "토"};
	public static int[] GetIntTokenizeIntArray(String str)
	{
		ArrayList<Integer> list = GetIntTokenizeArrayList(str);
		int[] returnValue = new int[list.size()];
		for (int i = 0; i < list.size(); i++) returnValue[i] = ((Integer)list.get(i)).intValue();
		return returnValue;
	}
	public static ArrayList<Integer> GetIntTokenizeArrayList(String str)
	{
		String temp2 = "";
		StringTokenizer values = new StringTokenizer(str, " ");
		while (values.hasMoreElements()) { temp2 = temp2 + values.nextToken();
		}
		StringTokenizer List = new StringTokenizer(temp2, ",");
		ArrayList<Integer> list = new ArrayList<Integer>();
		while (List.hasMoreElements()) {
			String value = List.nextToken();
			list.add(new Integer(value));
		}
		return list;
	}
	/**
	 * 시간에 해당하는 요일을 리턴.
	 * @param time
	 * @return
	 */
	static public String getYoil(long time) {
		date.setTime(time);
		Calendar c = Calendar.getInstance();
		c.setTime(date);
		return weekDay[ c.get(Calendar.DAY_OF_WEEK)-1 ];
	}
}
