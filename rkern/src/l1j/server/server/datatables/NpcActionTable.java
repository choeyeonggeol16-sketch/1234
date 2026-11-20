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
package l1j.server.server.datatables;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.action.L1NpcAction;
import l1j.server.server.model.npc.action.L1NpcXmlParser;
import l1j.server.server.utils.FileUtil;

public class NpcActionTable {
	private static final Logger LOG = Logger.getLogger(NpcActionTable.class.getName());
	private static volatile NpcActionTable _instance;
	private final List<L1NpcAction> _actions;
	private final List<L1NpcAction> _talkActions;

	private static void loadDirectoryActions(DocumentBuilder builder, File dir,
			List<L1NpcAction> actions, List<L1NpcAction> talkActions) throws SAXException, IOException {
		File[] files = dir.listFiles(file -> file.isFile() && FileUtil.getExtension(file).equalsIgnoreCase("xml"));
		if (files == null) throw new IOException("Cannot list NPC actions in " + dir);
		for (File file : files) {
			Element root = builder.parse(file).getDocumentElement();
			String nodeName = root.getNodeName();
			if ("NpcActionList".equalsIgnoreCase(nodeName)) {
				actions.addAll(L1NpcXmlParser.listActions(root));
			} else if ("NpcTalkActionList".equalsIgnoreCase(nodeName)) {
				talkActions.addAll(L1NpcXmlParser.listActions(root));
			}
		}
	}

	private NpcActionTable() throws Exception {
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		List<L1NpcAction> actions = new ArrayList<>();
		List<L1NpcAction> talkActions = new ArrayList<>();
		File root = new File("./data/xml/NpcActions/");
		File usersDir = new File(root, "users");
		if (usersDir.exists()) {
			loadDirectoryActions(builder, usersDir, actions, talkActions);
		}
		loadDirectoryActions(builder, root, actions, talkActions);
		_actions = List.copyOf(actions);
		_talkActions = List.copyOf(talkActions);
	}

	public static void load() {
		try {
			_instance = new NpcActionTable();
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "NpcAction를 읽어들일 수 없었습니다", e);
			System.exit(0);
		}
	}

	public static NpcActionTable getInstance() {
		return _instance;
	}

	public L1NpcAction get(String actionName, L1PcInstance pc, L1Object obj) {
		for (L1NpcAction action : _actions) {
			if (action.acceptsRequest(actionName, pc, obj)) {
				return action;
			}
		}
		return null;
	}

	public L1NpcAction get(L1PcInstance pc, L1Object obj) {
		for (L1NpcAction action : _talkActions) {
			if (action.acceptsRequest("", pc, obj)) {
				return action;
			}
		}
		return null;
	}
}
