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

import java.util.Arrays;
import java.util.StringTokenizer;

import org.w3c.dom.Element;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.dialog.L1NpcHtml;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public abstract class L1NpcXmlAction implements L1NpcAction {
	private static final int[] ANY_NPC = new int[0];
	private static final String CLASSES = "PKEWDTB";
	private final String _name;
	private final int[] _npcIds;
	private final int _minLevel;
	private final int _maxLevel;
	private final int _questId;
	private final int _questStep;
	private final int _classMask;

	public L1NpcXmlAction(Element element) {
		String name = element.getAttribute("Name");
		_name = name.isEmpty() ? null : name;
		_npcIds = parseNpcIds(element.getAttribute("NpcId"));
		int level = L1NpcXmlParser.getIntAttribute(element, "Level", 0);
		_minLevel = level == 0 ? L1NpcXmlParser.getIntAttribute(element, "LevelMin", 1) : level;
		_maxLevel = level == 0 ? L1NpcXmlParser.getIntAttribute(element, "LevelMax", 99) : level;
		_questId = L1NpcXmlParser.parseQuestId(element.getAttribute("QuestId"));
		_questStep = L1NpcXmlParser.parseQuestStep(element.getAttribute("QuestStep"));

		_classMask = parseClasses(element.getAttribute("Class"));
	}

	private static int parseClasses(String classes) {
		int mask = 0;
		for (int i = 0; i < classes.length(); i++) {
			int type = CLASSES.indexOf(Character.toUpperCase(classes.charAt(i)));
			if (type < 0) throw new IllegalArgumentException("Unknown NPC action class: " + classes.charAt(i));
			mask |= 1 << type;
		}
		return mask;
	}

	private static int[] parseNpcIds(String npcIds) {
		if (npcIds.isEmpty()) return ANY_NPC;
		StringTokenizer tok = new StringTokenizer(npcIds.replace(" ", ""), ",");
		int count = tok.countTokens();
		if (count == 0) return ANY_NPC;
		int[] result = new int[count];
		for (int i = 0; i < result.length; i++) {
			result[i] = Integer.parseInt(tok.nextToken());
		}
		Arrays.sort(result);
		return result;
	}

	private boolean acceptsNpcId(L1Object obj) {
		if (0 < _npcIds.length) {
			if (!(obj instanceof L1NpcInstance)) {
				return false;
			}
			int npcId = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();

			if (Arrays.binarySearch(_npcIds, npcId) < 0) {
				return false;
			}
		}
		return true;
	}

	private boolean acceptsCharType(int type) {
		return _classMask == 0 || (type >= 0 && type < CLASSES.length() && (_classMask & (1 << type)) != 0);
	}

	private boolean acceptsActionName(String name) {
		if (_name == null) {
			return true;
		}
		return _name.equals(name);
	}

    /** These actions have no XML predicate other than the common level bounds. */
    final boolean hasDefaultConditions() {
        return _name == null && _npcIds.length == 0 && _classMask == 0
                && _questId == -1 && _questStep == -1
                && _minLevel == 1 && _maxLevel == 99;
    }

    static String sourceDescription(Element element) {
        String source = element.getOwnerDocument().getDocumentURI();
        return (source == null ? "NPC action XML" : source) + " <" + element.getTagName()
                + "> Name=" + element.getAttribute("Name") + " NpcId=" + element.getAttribute("NpcId");
    }

    static L1NpcHtml rejectUnsafeQuestReward(L1PcInstance pc) {
        SC_SystemMessage.send(pc, "이 NPC의 퀘스트 보상 설정을 확인해 주세요.");
        return ABORTED;
    }

	private boolean acceptsQuest(L1PcInstance pc) {
		if (_questId == -1) {
			return true;
		}
		if (_questStep == -1) {
			return 0 < pc.getQuest().get_step(_questId);
		}
		return pc.getQuest().get_step(_questId) == _questStep;
	}

	public boolean acceptsRequest(String actionName, L1PcInstance pc, L1Object obj) {
		if (!acceptsActionName(actionName) || !acceptsNpcId(obj)) {
			return false;
		}
		int level = pc.getLevel();
		if (level < _minLevel) {
			SC_SystemMessage.send(pc, "레벨 " + _minLevel + "부터 입장 가능합니다!");
			return false;
		}
		if (level > _maxLevel) {
			SC_SystemMessage.send(pc, "레벨 " + _maxLevel + "까지 이용 가능합니다!");
			return false;
		}
		return acceptsQuest(pc) && acceptsCharType(pc.getType());
	}

	public abstract L1NpcHtml execute(String actionName, L1PcInstance pc, L1Object obj, byte args[]);

	public L1NpcHtml executeWithAmount(String actionName, L1PcInstance pc, L1Object obj, int amount) {
		return null;
	}
}
