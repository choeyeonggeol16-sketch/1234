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
package l1j.server.server.model.npc.action;

import org.w3c.dom.Element;

import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.npc.L1NpcHtml;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public class L1NpcTeleportAction extends L1NpcXmlAction {
	private final L1Location _loc;
	private final int _heading;
	private final int _price;
	private final boolean _effect;

	// private static Random _rand = new Random(System.nanoTime());

	public L1NpcTeleportAction(Element element) {
		super(element);

		int x = L1NpcXmlParser.getIntAttribute(element, "X", -1);
		int y = L1NpcXmlParser.getIntAttribute(element, "Y", -1);
		int mapId = L1NpcXmlParser.getIntAttribute(element, "Map", -1);
		_loc = new L1Location(x, y, mapId);

		_heading = L1NpcXmlParser.getIntAttribute(element, "Heading", 5);

		_price = L1NpcXmlParser.getIntAttribute(element, "Price", 0);
		_effect = L1NpcXmlParser.getBoolAttribute(element, "Effect", true);
	}

	@Override
	public L1NpcHtml execute(String actionName, L1PcInstance pc, L1Object obj, byte[] args) {
        if (_price < 0 || _heading < 0 || _heading > 7
                || !_loc.getMap().isInMap(_loc.getX(), _loc.getY())) {
            return L1NpcHtml.HTML_CLOSE;
        }
        if (_loc.getMapId() == 2010 && (pc.getLevel() < 45 || pc.getLevel() > 51)) {
            return L1NpcHtml.HTML_CLOSE;
        }
        final L1ItemInstance payment = _price == 0 ? null
                : pc.getInventory().GetFirstItemByClassID(L1ItemId.ADENA);
        if (_price > 0 && (payment == null || payment.getCount() < _price)) {
            SC_ServerMessage.send(pc, 337, "$4");
            return L1NpcHtml.HTML_CLOSE;
        }
        L1Location location = _loc;
        final int mapId = _loc.getMapId();
        if (mapId == 340 || mapId == 350 || mapId == 360 || mapId == 370) {
            location = L1Location.randomLocation(_loc, 1, 10, true);
        }
        if (!location.getMap().isInMap(location.getX(), location.getY())) {
            return L1NpcHtml.HTML_CLOSE;
        }
        // Keep the synchronous XML list contract. Validate actual debit result.
        if (_price > 0 && pc.getInventory().removeItem(payment, _price) != _price) {
            SC_ServerMessage.send(pc, 337, "$4");
            return L1NpcHtml.HTML_CLOSE;
        }
        pc.start_teleport(location.getX(), location.getY(), location.getMapId(), _heading, 169, _effect, false);
        return null;
    }
}
