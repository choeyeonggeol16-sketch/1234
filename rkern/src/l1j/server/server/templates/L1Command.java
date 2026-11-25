/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.   See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.templates;

import java.util.function.Supplier;
import l1j.server.server.command.executor.L1CommandExecutor;
import l1j.server.server.model.Instance.L1PcInstance;

public class L1Command {
	private final String _name;
	private final int _level;
	private final String _executorClassName;
	private final Supplier<L1CommandExecutor> _factory;

	public L1Command(String name, int level, String executorClassName) {
		this(name, level, executorClassName, null);
	}

	public L1Command(String name, int level, String executorClassName, Supplier<L1CommandExecutor> factory) {
		_name = name;
		_level = level;
		_executorClassName = executorClassName;
		_factory = factory;
	}

	public boolean isExecutable() {
		return _factory != null;
	}

	public boolean execute(L1PcInstance pc, String name, String arg) {
		if (_factory == null) return false;
		_factory.get().execute(pc, name, arg);
		return true;
	}

	public String getName() {
		return _name;
	}

	public int getLevel() {
		return _level;
	}

	public String getExecutorClassName() {
		return _executorClassName;
	}
}
