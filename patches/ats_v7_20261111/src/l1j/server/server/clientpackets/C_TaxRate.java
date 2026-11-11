package l1j.server.server.clientpackets;

import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.server.GameClient;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;

// Referenced classes of package l1j.server.server.clientpackets:
// ClientBasePacket

public class C_TaxRate extends ClientBasePacket {

	private static final String C_TAX_RATE = "[C] C_TaxRate";
	public C_TaxRate(byte abyte0[], GameClient clientthread)
			throws Exception {
		super(abyte0);
		int i = readD();
		int j = readC();

		L1PcInstance player = clientthread.getActiveChar();
		if ( player == null)return;
		if (i == player.getId()) {
			L1Clan clan = L1World.getInstance().getClan(player.getClanid());
			if (clan != null) {
				int castle_id = clan.getCastleId();
				if (castle_id != 0) {
					if (j >= 10 && j <= 50) {
						CastleWarBusiness.getInstance().setTaxRate(castle_id, j);
						CastleWarBusiness.getInstance().updateCastle(castle_id);
					}
				}
			}
		}
	}

	@Override
	public String getType() {
		return C_TAX_RATE;
	}

}
