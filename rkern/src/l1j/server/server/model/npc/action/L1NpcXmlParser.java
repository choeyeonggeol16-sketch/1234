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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import l1j.server.server.model.quest.CQuestSystem;
import l1j.server.server.utils.IterableElementList;

public class L1NpcXmlParser {
	public static List<L1NpcAction> listActions(Element element) {
		List<L1NpcAction> result = new ArrayList<L1NpcAction>();
		NodeList list = element.getChildNodes();
		L1NpcAction action = null;
		for (Element elem : new IterableElementList(list)) {
			action = L1NpcActionFactory.newAction(elem);
			if (action != null) {
				result.add(action);
			}
		}
		return result;
	}

	public static Element getFirstChildElementByTagName(Element element, String tagName) {
		IterableElementList list = new IterableElementList(element.getChildNodes());
		for (Element elem : list) {
            if (tagName.equals(elem.getTagName())) return elem;
		}
		return null;
	}

	public static int getIntAttribute(Element element, String name, int defaultValue) {
		String value = element.getAttribute(name);
		if (value.isEmpty()) return defaultValue;
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public static boolean getBoolAttribute(Element element, String name, boolean defaultValue) {
		boolean result = defaultValue;
		String value = element.getAttribute(name);
		if (!value.equals("")) {
			result = Boolean.valueOf(value);
		}
		return result;
	}

	private final static Map<String, Integer> _questIds = new HashMap<String, Integer>();
	static {
		_questIds.put("firstquest", CQuestSystem.QUEST_FIRSTQUEST);
		_questIds.put("level15", CQuestSystem.QUEST_LEVEL15);
		_questIds.put("level30", CQuestSystem.QUEST_LEVEL30);
		_questIds.put("level45", CQuestSystem.QUEST_LEVEL45);
		_questIds.put("level50", CQuestSystem.QUEST_LEVEL50);
		_questIds.put("lyra", CQuestSystem.QUEST_LYRA);
		_questIds.put("oilskinmant", CQuestSystem.QUEST_OILSKINMANT);
		_questIds.put("doromond", CQuestSystem.QUEST_DOROMOND);
		_questIds.put("ruba", CQuestSystem.QUEST_RUBA);
		_questIds.put("lukein", CQuestSystem.QUEST_LUKEIN1);
		_questIds.put("tbox1", CQuestSystem.QUEST_TBOX1);
		_questIds.put("tbox2", CQuestSystem.QUEST_TBOX2);
		_questIds.put("tbox3", CQuestSystem.QUEST_TBOX3);
		_questIds.put("cadmus", CQuestSystem.QUEST_CADMUS);
		_questIds.put("resta", CQuestSystem.QUEST_RESTA);
		_questIds.put("kamyla", CQuestSystem.QUEST_KAMYLA);
		_questIds.put("lizard", CQuestSystem.QUEST_LIZARD);
		_questIds.put("desire", CQuestSystem.QUEST_DESIRE);
		_questIds.put("shadows", CQuestSystem.QUEST_SHADOWS);
		_questIds.put("icequeenring", CQuestSystem.QUEST_ICEQUEENRING);
	}

	public static int parseQuestId(String questId) {
		if (questId.equals("")) {
			return -1;
		}
		Integer result = _questIds.get(questId.toLowerCase(Locale.ROOT));
		if (result == null) {
			throw new IllegalArgumentException();
		}
		return result;
	}

	public static int parseQuestStep(String questStep) {
		if (questStep.equals("")) {
			return -1;
		}
		if (questStep.equalsIgnoreCase("End")) {
			return CQuestSystem.QUEST_END;
		}
		return Integer.parseInt(questStep);
	}
}
