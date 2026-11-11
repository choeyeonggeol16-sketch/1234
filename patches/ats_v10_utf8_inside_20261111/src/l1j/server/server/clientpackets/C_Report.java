/*
 * make by Eva Team (http://eva.gg.gg)
 *	/신고 
 * 
 */

package l1j.server.server.clientpackets;

import l1j.server.server.GameClient;


public class C_Report extends ClientBasePacket {

	private static final String C_REPORT = "[C] C_Report";
	
	public C_Report(byte abyte0[], GameClient clientthread) throws Exception {
		super(abyte0);

		int type = readC();
//		System.out.println(type);

	}

	@Override
	public String getType() {
		return C_REPORT;
	}
}
