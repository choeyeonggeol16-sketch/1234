package l1j.server.server.Astar;

public class NpcNode {
	public int f;
	public int h;
	public int g;
	public int x, y;
	public NpcNode prev;
	public NpcNode direct[];
	public NpcNode next;

	// *************************************************************************
	// Name : Node()
	// Desc : »ý¼ºÀÚ
	// *************************************************************************
	NpcNode() {
		direct = new NpcNode[8];

		for (int i = 0; i < 8; i++) {
			direct[i] = null;
		}
	}

	public void close() {
		for (int i = 0; i < 8; i++) {
			direct[i] = null;
		}
		prev = next = null;
	}

	public void clear() {
		f = 0;
		h = 0;
		g = 0;
		x = 0;
		y = 0;
		prev = null;
		direct = null;
		next = null;
	}
}
