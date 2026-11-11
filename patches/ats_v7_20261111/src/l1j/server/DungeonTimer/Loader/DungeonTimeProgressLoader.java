package l1j.server.DungeonTimer.Loader;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;

import l1j.server.DungeonTimer.DungeonTimeInformation;
import l1j.server.DungeonTimer.Progress.AccountTimeProgress;
import l1j.server.DungeonTimer.Progress.CharacterTimeProgress;
import l1j.server.DungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Executors.Updator;
import l1j.server.Template.SqlHelper.Handler.BatchHandler;
import l1j.server.Template.SqlHelper.Handler.Handler;
import l1j.server.Template.SqlHelper.Handler.SelectorHandler;
import l1j.server.server.model.Instance.L1PcInstance;

public class DungeonTimeProgressLoader{
	public static void load(L1PcInstance pc) {
		Selector.exec("select * from dungeon_time_char_information where owner_info=?", new SelectorHandler() {
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, pc.getId());
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				while (rs.next()) {
					CharacterTimeProgress progress = CharacterTimeProgress.newInstance(rs);
					pc.put_dungeon_progress(progress.get_timer_id(), progress);
				}
			}
		});
		Selector.exec("select * from dungeon_time_account_information where owner_info=?", new SelectorHandler() {
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setString(1, pc.getAccountName());
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				while (rs.next()) {
					AccountTimeProgress progress = AccountTimeProgress.newInstance(rs);
					pc.put_dungeon_progress(progress.get_timer_id(), progress);
				}
			}
		});
		DungeonTimeInformation dtInfo = DungeonTimeInformationLoader.getInstance().from_map_id(pc.getMapId());
		if (dtInfo != null) {
			pc.send_dungeon_progress(dtInfo);
		}
		
		
	}
	
	public static void delete(L1PcInstance pc){
		Updator.exec("delete from dungeon_time_char_information where owner_info=?", new Handler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, pc.getId());
			}
		});
		Updator.exec("delete from dungeon_time_account_information where owner_info=?", new Handler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setString(1, pc.getAccountName());
			}
		});
	}
	
	public static void delete(L1PcInstance pc, int timer_id, boolean is_account){
		String table = is_account ? "dungeon_time_account_information" : "dungeon_time_char_information";
		Updator.exec(String.format("delete from %s where owner_info=? and timer_id=?", table), new Handler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setString(1, pc.getAccountName());
				pstm.setInt(2,  timer_id);
			}
		});
	}
	
	public static void update(L1PcInstance pc){
		update_character(pc);
		update_account(pc);
	}
	
	private static void update_character(L1PcInstance pc){
		Collection<DungeonTimeProgress<?>> col = pc.get_character_progresses();
		int size = col.size();
		if(size <= 0)
			return;
		
		ArrayList<DungeonTimeProgress<?>> list = new ArrayList<DungeonTimeProgress<?>>(col);
		Updator.batch("insert into dungeon_time_char_information set owner_info=?, timer_id=?, remain_seconds=? "
				+ "on duplicate key update remain_seconds=?", new BatchHandler(){
			@Override
			public void handle(PreparedStatement pstm, int callNumber) throws Exception {
				DungeonTimeProgress<?> progress = list.get(callNumber);
				pstm.setObject(1, progress.get_owner_info());
				pstm.setInt(2, progress.get_timer_id());
				if (progress.get_remain_seconds() < 0) {
					pstm.setInt(3, 0);
					pstm.setInt(4, 0);
				} else {
					pstm.setInt(3, progress.get_remain_seconds());
					pstm.setInt(4, progress.get_remain_seconds());
				}
			}
		}, size);
	}
	
	private static void update_account(L1PcInstance pc){
		Collection<DungeonTimeProgress<?>> col = pc.get_account_progresses();
		int size = col.size();
		if(size <= 0)
			return;
		
		ArrayList<DungeonTimeProgress<?>> list = new ArrayList<DungeonTimeProgress<?>>(col);
		Updator.batch("insert into dungeon_time_account_information set owner_info=?, timer_id=?, remain_seconds=? "
				+ "on duplicate key update remain_seconds=?", new BatchHandler(){
			@Override
			public void handle(PreparedStatement pstm, int callNumber) throws Exception {
				DungeonTimeProgress<?> progress = list.get(callNumber);
				pstm.setObject(1, progress.get_owner_info());
				pstm.setInt(2, progress.get_timer_id());
				if (progress.get_remain_seconds() < 0) {
					pstm.setInt(3, 0);
					pstm.setInt(4, 0);
				} else {
					pstm.setInt(3, progress.get_remain_seconds());
					pstm.setInt(4, progress.get_remain_seconds());
				}
			}
		}, size);
	}
	
	public static void update(DungeonTimeProgress<?> progress) {
		if (progress instanceof CharacterTimeProgress) {
			update((CharacterTimeProgress) progress);
		} else {
			update((AccountTimeProgress) progress);
		}
	}
	
	public static void update(CharacterTimeProgress progress){
		Updator.exec("insert into dungeon_time_char_information set owner_info=?, timer_id=?, remain_seconds=? "
				+ "on duplicate key update remain_seconds=?", new Handler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, progress.get_owner_info());
				pstm.setInt(2, progress.get_timer_id());
				if (progress.get_remain_seconds() < 0) {
					pstm.setInt(3, 0);
					pstm.setInt(4, 0);
				} else {
					pstm.setInt(3, progress.get_remain_seconds());
					pstm.setInt(4, progress.get_remain_seconds());
				}
			}			
		});
	}
	
	public static void update(AccountTimeProgress progress){
		Updator.exec("insert into dungeon_time_account_information set owner_info=?, timer_id=?, remain_seconds=? "
				+ "on duplicate key update remain_seconds=?", new Handler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setString(1, progress.get_owner_info());
				pstm.setInt(2, progress.get_timer_id());
				if (progress.get_remain_seconds() < 0) {
					pstm.setInt(3, 0);
					pstm.setInt(4, 0);
				} else {
					pstm.setInt(3, progress.get_remain_seconds());
					pstm.setInt(4, progress.get_remain_seconds());
				}
			}
		});
	}
}
