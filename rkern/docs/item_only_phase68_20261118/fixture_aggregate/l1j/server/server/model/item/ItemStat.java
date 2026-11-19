package l1j.server.server.model.item;
import l1j.server.server.model.Instance.L1PcInstance;
public enum ItemStat {
 AC(WType.AC), A(WType.B), B(WType.H), C(WType.POTION), D(WType.HEAL), E(WType.NONE);
 public final WType wt;
 ItemStat(WType wt) { this.wt=wt; }
 public enum WType { NONE, B, H, AC, POTION, HEAL }
 public void apply(L1PcInstance pc, int v) { pc.value += (long) v * (ordinal()+1); }
}
