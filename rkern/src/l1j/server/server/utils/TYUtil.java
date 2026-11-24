package l1j.server.server.utils;

import l1j.server.server.datatables.CObjectDB;
import java.util.ArrayList;
import java.util.StringTokenizer;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;

import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class TYUtil
{
  public static ArrayList<Integer> GetIntTokenizeArrayList(String str)
  {
    String temp2 = "";
    StringTokenizer values = new StringTokenizer(str, " ");
    while (values.hasMoreElements()) {
      temp2 = temp2 + values.nextToken();
    } 
    StringTokenizer List = new StringTokenizer(temp2, ",");
    ArrayList<Integer> list = new ArrayList<Integer>();
    while (List.hasMoreElements())
    {
      String value = List.nextToken();
      list.add(new Integer(value));
    }
    return list;
  }
  
  public static int[] GetIntTokenizeIntArray(String str)
  {
    ArrayList<Integer> list = GetIntTokenizeArrayList(str);
    int[] returnValue = new int[list.size()];
    for (int i = 0; i < list.size(); i++) {
      returnValue[i] = ((Integer)list.get(i)).intValue();
    }
    return returnValue;
  }
  public static L1ItemInstance GetItemInstByID(int itemId)
  {
    return CObjectDB.LoadItem().createItem(itemId);
  }
  public static void SetSystemMessage(L1PcInstance pc, String msg)
  {
    SC_SystemMessage.send(pc, msg);
  }
  
}
