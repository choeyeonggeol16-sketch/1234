package l1j.server.MJTemplate.ItemPresentator;

import java.io.IOException;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public interface Presentator {
	public byte[] do_presentation(L1ItemInstance item) throws IOException;
	public void do_equip(L1PcInstance pc, L1ItemInstance item, int signed);
}
