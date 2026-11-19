package l1j.server.MJTemplate.Time;

import l1j.server.server.model.gametime.TimeListener;

public abstract class MJTimeScheduler implements TimeListener{
	public abstract void run();
	public abstract void dispose();
}
