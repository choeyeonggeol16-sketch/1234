package l1j.server.AcionCodes.IntervalDecorator;

import l1j.server.server.model.L1Character;

public interface ActionIntervalDecorator<T extends L1Character> {
	public double decoration(T owner, double interval);
}
