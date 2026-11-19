package l1j.server.server.command.executor;

import java.io.File;
import java.sql.Connection;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.DateUtil;
import l1j.server.server.utils.L1QueryUtil;
import l1j.server.server.utils.SQLUtil;

public class L1DatabaseInit implements L1CommandExecutor {

	public static L1CommandExecutor getInstance() {
		return new L1DatabaseInit();
	}

	public void execute(L1PcInstance pc, String cmdName, String poby) {
		Connection con = null;
		try {

			con = DatabaseFactory.getInstance().getConnection();

			L1QueryUtil.execute(con, "DELETE FROM accounts", new Object[0]);

			Calendar calendar = (Calendar) DateUtil.getRealTime().clone();
			calendar.add(6, 1);
			calendar.set(11, 21);
			calendar.set(12, 0);
			calendar.set(13, 0);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String fm = sdf.format(calendar.getTime());

			L1QueryUtil.execute(con, "DELETE FROM board_posts WHERE board_id=1", new Object[0]);

			L1QueryUtil.execute(con, "UPDATE castle SET tax_rate=" + Config.Tax_Rate + ", public_money=0",	new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_teleport", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_buddys", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_buff", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_config", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_elf_warehouse", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_exclude", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_items", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_quests", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_level_gifts", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_special_warehouse", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_new_quest", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM letter", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM report", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_skills", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM tam", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_warehouse", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM characters", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM clan_warehouse_log", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_supplementary_service", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_slot_items", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM adshop", new Object[0]);

			L1QueryUtil.execute(con, "UPDATE house SET is_on_sale=1, is_purchase_basement=0, tax_deadline=?", new Object[] { new Timestamp(System.currentTimeMillis() + 86400000L) });

			L1QueryUtil.execute(con, "DELETE FROM pets", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM character_teleport_item", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM clan_data", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM board_posts WHERE board_id=7", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM tb_mjct_mapping", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM tb_kda", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM attendance_startup", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM attendance_userinfo", new Object[0]);


			L1QueryUtil.execute(con, "DELETE FROM tb_mbook_characterInfo", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM tb_mbook_wq_decks", new Object[0]);

			L1QueryUtil.execute(con, "DELETE FROM tb_mbook_wq_startup;", new Object[0]);

			File file = new File("emblem");
			String[] fnameList = file.list();
			for (String name : fnameList) {
				File f = new File("emblem/" + name);
				if (!f.isDirectory())
					f.delete();
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(con);
		}
	}
}