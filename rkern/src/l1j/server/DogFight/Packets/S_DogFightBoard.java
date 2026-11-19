package l1j.server.DogFight.Packets;

import java.util.ArrayList;

import l1j.server.DogFight.Game.MJDogFightGame;
import l1j.server.DogFight.History.MJDogFightHistory;
import l1j.server.DogFight.History.MJDogFightTicketInfo;
import l1j.server.DogFight.Instance.MJDogFightInstance;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.session.CIOClient;

public class S_DogFightBoard extends ServerPacket {

	private final boolean _isGm;
	private final ArrayList<MJDogFightTicketInfo> _ticketsInfo;

	public static S_DogFightBoard newInstance(boolean is_gm, ArrayList<MJDogFightTicketInfo> tickets_info) {
		return new S_DogFightBoard(is_gm, tickets_info);
	}

	private S_DogFightBoard(boolean is_gm, ArrayList<MJDogFightTicketInfo> tickets_info) {
		_isGm = is_gm;
		_ticketsInfo = tickets_info;
	}

	@Override
	public void writeImpl(CIOClient client, WriteBuffer buffer) {
		buffer.writeC(Opcodes.S_HYPERTEXT);
		buffer.writeD(1);
		buffer.writeS("maeno4");
		buffer.writeC(0);
		buffer.writeH(15);

		if (_ticketsInfo != null) {
			if (_ticketsInfo.size() == 2) {
				create_normal_size(buffer, _isGm, _ticketsInfo);
			} else {
				create_full_size(buffer, _isGm, _ticketsInfo);
			}
		}
	}

	private void create_full_size(
			WriteBuffer buffer,
			boolean is_gm,
			ArrayList<MJDogFightTicketInfo> tickets_info) {

		int remain = 5;

		for (MJDogFightTicketInfo tInfo : tickets_info) {
			write_tickets_info(buffer, tInfo, is_gm);
			--remain;
		}

		if (remain > 0) {
			for (int i = remain - 1; i >= 0; --i) {
				buffer.writeS("-");
				buffer.writeS("-");
				buffer.writeS("-");
			}
		}
	}

	private void create_normal_size(
			WriteBuffer buffer,
			boolean is_gm,
			ArrayList<MJDogFightTicketInfo> tickets_info) {

		buffer.writeS("-");
		buffer.writeS("-");
		buffer.writeS("-");

		write_tickets_info(buffer, tickets_info.get(0), is_gm);

		buffer.writeS("-");
		buffer.writeS("-");
		buffer.writeS("-");

		write_tickets_info(buffer, tickets_info.get(1), is_gm);

		buffer.writeS("-");
		buffer.writeS("-");
		buffer.writeS("-");
	}

	private void write_tickets_info(
			WriteBuffer buffer,
			MJDogFightTicketInfo tInfo,
			boolean is_gm) {

		if (is_gm) {

			buffer.writeS(String.format("[%s]", tInfo.get_corner_name()));
			buffer.writeS(String.format("%.2f%%", tInfo.get_dividend()));
			buffer.writeS(String.format("%.1f%%", tInfo.get_win_rate()));

			MJDogFightInstance fight =
					MJDogFightGame.getDogFight(tInfo.get_corner_id());

			if (fight != null) {
				buffer.writeS(String.format("[%d]", fight.getTotalPrice()));
			}

		} else {

			buffer.writeS(
					MJDogFightHistory.getInstance()
					.get_leader_name(tInfo.get_corner_id())
					.replace("[Ȧ]", "")
					.replace("[¦]", "")
					);

			buffer.writeS(String.format("[%s]", tInfo.get_corner_name()));
			buffer.writeS(String.format("%.1f%%", tInfo.get_win_rate()));
		}
	}

}