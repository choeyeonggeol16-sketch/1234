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
package l1j.server.server.storage;

import java.util.ArrayList;

import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;

public abstract class CharactersItemStorage {
	public abstract ArrayList<ItemInstance> loadItems(int objId) throws Exception;

	public abstract void storeItem(int objId, ItemInstance item) throws Exception;

	public abstract void deleteItem(ItemInstance item) throws Exception;

	public abstract void updateItemAll(ItemInstance item) throws Exception;

	public abstract void updateItemId(ItemInstance item) throws Exception;

	public abstract void updateItemCount(ItemInstance item) throws Exception;

	public abstract void updateItemIdentified(ItemInstance item) throws Exception;

	public abstract void updateItemEquipped(ItemInstance item) throws Exception;

	public abstract void updateItemEnchantLevel(ItemInstance item) throws Exception;

	public abstract void updateItemDurability(ItemInstance item) throws Exception;

	public abstract void updateItemChargeCount(ItemInstance item) throws Exception;
	
	public abstract void updateItemRemainingTime(ItemInstance item) throws Exception;

	public abstract void updateItemDelayEffect(ItemInstance item) throws Exception;
	
	public abstract void updateItemBless(ItemInstance item) throws Exception;

	public abstract void updateItemAttrEnchantLevel(ItemInstance item) throws Exception;
	
	public abstract void updateItemEndTime(ItemInstance item) throws Exception;

	public abstract int getItemCount(int objId) throws Exception;
	
	public abstract void updateBlessType(ItemInstance item) throws Exception;
	
	public abstract void updateBlessTypeValue(ItemInstance item) throws Exception;

	public static CharactersItemStorage create() {
		if (_instance == null) {
			_instance = new MySqlCharactersItemStorage();
		}
		return _instance;
	}

	private static CharactersItemStorage _instance;
}
