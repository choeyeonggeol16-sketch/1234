package l1j.server.server.model.BookQuest.Loader;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Calendar;

import l1j.server.MJTemplate.MJPropertyReader;
import l1j.server.MJTemplate.Command.MJCommand;
import l1j.server.MJTemplate.Command.MJCommandArgs;
import l1j.server.MJTemplate.Command.MJCommandTree;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1World;
import l1j.server.server.model.BookQuest.BQSInformation;
import l1j.server.server.model.BookQuest.BQSWQUpdator;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterCriteriaProgress;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.packets.Protocol.Proto;
import LIN.SC_CRITERIA_UPDATE_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.L1SpawnUtil;

public class BQSLoadManager implements MJCommand{
    private static BQSLoadManager _instance;
    public static BQSLoadManager getInstance(){
        if(_instance == null)
            _instance = new BQSLoadManager();
        return _instance;
    }
    
    public static int criteriaIdToAchievementId(int criteria_id, int achievement_level){
        int result = (criteria_id * 3) - (3 - achievement_level);
        if(criteria_id > 577)
            result += 31;
        return result;
    }
    
    public static int achievementIdToCriteriaId(int achievement_id){
        int temp = achievement_id;
        if(achievement_id >= 1763)
            temp -= 31;
        
        return ((temp - 1) / 3) + 1;
    }
    
    public static int achievementIdToAchievementLevel(int achievement_id){
        int temp = achievement_id;
        if(achievement_id >= 1763){
            temp -= 31;
        }
        return ((temp - 1) % 3) + 1;
    }
    
    public static void loadBqsUpdateCalendar(){
        Selector.exec("select * from tb_mbook_wq_startup", new FullSelectorHandler(){
            @Override
            public void result(ResultSet rs) throws Exception{
                if(rs.next()){
                    Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
                    BQS_UPDATE_CALENDAR = (Calendar)cal.clone();
                    BQS_UPDATE_CALENDAR.setTimeInMillis(rs.getTimestamp("update_info").getTime());
                    BQS_UPDATE_CALENDAR.set(Calendar.HOUR_OF_DAY, BQS_UPDATE_STDHOUR);
                    int diff_hours = (((int)(cal.getTimeInMillis() - BQS_UPDATE_CALENDAR.getTimeInMillis()) / 1000) / 3600);
                    if(diff_hours > BQS_UPDATE_HOURS)
                        updateBqsUpdateCalendar();
                    cal.clear();
                }else{
                    updateBqsUpdateCalendar();
                }
            }
        });
    }
    
    public static void updateBqsUpdateCalendar(){
        Calendar oldCal = BQS_UPDATE_CALENDAR;
        BQS_UPDATE_CALENDAR = RealTimeClock.getInstance().getRealTimeCalendar();
        BQS_UPDATE_CALENDAR.set(Calendar.HOUR_OF_DAY, BQS_UPDATE_STDHOUR);
        BQS_UPDATE_CALENDAR.set(Calendar.MINUTE, 0);
        BQS_UPDATE_CALENDAR.set(Calendar.SECOND, 0);
        Updator.exec("insert into tb_mbook_wq_startup set id=1, update_info=? on duplicate key update update_info=?", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception{
                Timestamp ts = new Timestamp(BQS_UPDATE_CALENDAR.getTimeInMillis());
                pstm.setTimestamp(1, ts);
                pstm.setTimestamp(2, ts);
            }
        });
        if(oldCal != null){
            oldCal.clear();
            oldCal = null;
        }
        BQSWQDecksLoader.getInstance().updateDecks();
    }
    
    public static void truncateBqsUpdateCalendar(){
        Updator.exec("truncate table tb_mbook_wq_startup", new Handler(){
            public void handle(PreparedStatement pstm) throws Exception{
            }
        });
        updateBqsUpdateCalendar();
    }
    
    public static int BQS_WQ_WIDTH;
    public static int BQS_WQ_HEIGHT;
    public static int BQS_WQ_MINLEVEL;
    public static int BQS_WQ_STD_EXP;
    public static int BQS_UPDATE_STDHOUR;
    public static int BQS_UPDATE_HOURS;
    public static int BQS_UPDATE_TYPE;
    public static Calendar BQS_UPDATE_CALENDAR;
    public static boolean BQS_IS_ONUPDATE_BOOKS;
    private MJCommandTree _commands;
    private BQSLoadManager(){
        _commands = createCommand();
    }
    
    public void run(){
        loadConfig();
        BQSInformationLoader.getInstance();
        BQSRewardsLoader.getInstance();
        BQSWQInformationLoader.getInstance();
        BQSWQRewardsLoader.getInstance();
        BQSWQDecksLoader.getInstance();
        loadBqsUpdateCalendar();
        BQSWQUpdator.getInstance().run();
    }
    
    private void loadConfig(){
        MJPropertyReader reader = null;
        try{	
            reader = new MJPropertyReader("./config/mj_bookquestsystem.properties");
            BQS_UPDATE_STDHOUR = reader.readInt("UpdateStandardHour", "9");
            BQS_UPDATE_HOURS = reader.readInt("UpdateHours", "24");
            BQS_UPDATE_TYPE = reader.readInt("UpdateType", "11");
            BQS_WQ_WIDTH = reader.readInt("WeekQuest_Width", "3");
            BQS_WQ_HEIGHT = reader.readInt("WeekQuest_Height", "3");
            BQS_WQ_MINLEVEL = reader.readInt("WeekQuest_MinLevel", "56");
            BQS_WQ_STD_EXP = reader.readInt("WeekQuest_StandardExp", "721306");
            BQS_IS_ONUPDATE_BOOKS = reader.readBoolean("IsOnUpdateBooks", "false");
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            if(reader != null){
                reader.dispose();
                reader = null;
            }
        }
    }

    private MJCommandTree createCommand(){
        return new MJCommandTree(".북시스템", "몬스터 도감 / 주간 퀘스트 관련 명령어를 실행합니다.", null)
        .add_command(createCriteriaCommand())
        .add_command(createV2Command())
        .add_command(createSpawnCommand())
        .add_command(createCharacterUpdateCommand())
        .add_command(createReloadConfigCommand())
        .add_command(createInitializedCommand());
    }
    
    private MJCommandTree createCriteriaCommand(){
        return new MJCommandTree("도감", "몬스터북 관련 명령을 수행합니다.", null)
        .add_command(new MJCommandTree("리로드", "몬스터 도감 내 각종 데이터를 리로드합니다.", null)
            .add_command(new MJCommandTree("보상", "", null){
                @Override
                protected void to_handle_command(MJCommandArgs args) throws Exception{
                    BQSRewardsLoader.reload();
                    args.notify("tb_mbook_reward_info/tb_mbook_reward_items 테이블을 리로드 하였습니다.");
                }
            })
            .add_command(new MJCommandTree("도감정보", "", null){
                @Override
                protected void to_handle_command(MJCommandArgs args) throws Exception{
                    BQSInformationLoader.reload();
                    args.notify("tb_mbook_information 테이블을 리로드 하였습니다.");
                }
            })
        )
        .add_command(new MJCommandTree("케릭터초기화", "보내온 케릭터 이름으로 해당 케릭터의 몬스터 북을 초기화 합니다.", new String[]{"케릭터이름"}){
            @Override
            protected void to_handle_command(MJCommandArgs args) throws Exception{
                String character_name = args.nextString();
                L1PcInstance pc = L1World.getInstance().getPlayer(character_name);
                L1PcInstance gm = args.getOwner();
                if(pc == null){
                    args.notify("케릭터 이름 %s을(를) 찾을 수 없습니다.");
                    return;					
                }
                ThreadPool.getInstance().execute(new Runnable(){
                    @Override
                    public void run(){
                        try{
                            BQSCharacterData bqs = pc.getBqs();
                            if(bqs.has_progresses()){
                                for(BQSCharacterCriteriaProgress progress : bqs.get_progresses().values()){
                                    progress.set_current_ahcievement_level(0);
                                    progress.get_progress().set_quantity(0L);
                                    LIN.SC_CRITERIA_UPDATE_NOTI.sendOf(pc, progress.get_progress());
                                }
                            }
                            bqs.get_progresses().clear();
                            bqs.get_achievements().clear();
                            BQSCharacterDataLoader.storeCharacterBqs(pc.getBqs(), false);
                            pc.sendPackets("운영자에 의해 도감 정보가 초기화되었습니다. 몬스터는 갱신되나, 보상을 위해서는 리스하시기 바랍니다.");
                            gm.sendPackets(String.format("%s의 몬스터북 정보를 초기화 하였습니다.", pc.getName()));
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                });
            }
        })
        .add_command(new MJCommandTree("전체초기화", "현재 접속중인 모든 케릭터의 도감을 초기화합니다.", null){
            protected void to_handle_command(MJCommandArgs args) throws Exception{
                L1PcInstance gm = args.getOwner();
                ThreadPool.getInstance().execute(new Runnable(){
                    @Override
                    public void run(){
                        BQS_IS_ONUPDATE_BOOKS = false;
                        String message = "운영자에 의해 몬스터 도감 정보가 초기화되었습니다. 몬스터는 갱신되나, 보상을 위해서는 리스하시기 바랍니다.";
                        int amount = 0;
                        try{
                            for(L1PcInstance pc : L1World.getInstance().getAllPlayers()){
                                if(pc == null)
                                    continue;
                                
                                BQSCharacterData bqs = pc.getBqs();
                                if(bqs == null)
                                    continue;
                                
                                ++amount;
                                if(bqs.has_progresses()){
                                    for(BQSCharacterCriteriaProgress progress : bqs.get_progresses().values()){
                                        progress.set_current_ahcievement_level(0);
                                        progress.get_progress().set_quantity(0L);
                                        LIN.SC_CRITERIA_UPDATE_NOTI.sendOf(pc, progress.get_progress());
                                    }
                                }
                                bqs.get_progresses().clear();
                                bqs.get_achievements().clear();
                                BQSCharacterDataLoader.storeCharacterBqs(pc.getBqs(), false);
                                SC_SystemMessage.send(pc, message);
                            }
                            gm.sendPackets(String.format("%d명의 캐릭터 도감정보를 초기화하였습니다.", amount));
                        }catch(Exception e){
                            e.printStackTrace();
                        }finally{
                        //	message.clear();
                            BQS_IS_ONUPDATE_BOOKS = true;
                        }
                    }
                });
            }
        });
    }
    
    private MJCommandTree createV2Command(){
        return new MJCommandTree("주퀘", "주간퀘스트 관련 명령을 수행합니다.", null)
        .add_command(new MJCommandTree("리로드", "주간퀘스트 내 각종 데이터를 리로드합니다.", null)
            .add_command(new MJCommandTree("보상", "", null){
                @Override
                protected void to_handle_command(MJCommandArgs args) throws Exception{
                    BQSWQRewardsLoader.reload();
                    args.notify("tb_mbook_wq_reward_info/tb_mbook_wq_reward_items 테이블을 리로드 하였습니다.");
                }
            })
            .add_command(new MJCommandTree("주간정보", "", null){
                @Override
                protected void to_handle_command(MJCommandArgs args) throws Exception{
                    BQSWQInformationLoader.reload();
                    args.notify("tb_mbook_information 테이블을 리로드 하였습니다.");
                }
            })
        )
        .add_command(new MJCommandTree("케릭터초기화", "보내온 케릭터 이름으로 해당 케릭터의 주간퀘스트를 초기화 합니다.", new String[]{"케릭터이름"}){
            @Override
            protected void to_handle_command(MJCommandArgs args) throws Exception{
                String character_name = args.nextString();
                L1PcInstance pc = L1World.getInstance().getPlayer(character_name);
                L1PcInstance gm = args.getOwner();
                if(pc == null){
                    args.notify("케릭터 이름 %s을(를) 찾을 수 없습니다.");
                    return;					
                }
                ThreadPool.getInstance().execute(new Runnable(){
                    @Override
                    public void run(){
                        try{
                            BQSCharacterData bqs = pc.getBqs();
                            bqs
                            .set_decks_version(0L)
                            .realloc_decks(pc)
                            .send_decks_noti(pc);
                            pc.sendPackets("운영자에 의해 주간퀘스트 정보가 초기화되었습니다.");
                            gm.sendPackets(String.format("%s의 몬스터북 정보를 초기화 하였습니다.", pc.getName()));
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                });
            }
        })
        .add_command(new MJCommandTree("전체초기화", "현재 접속중인 모든 케릭터의 주간퀘스트를 초기화합니다.", null){
            protected void to_handle_command(MJCommandArgs args) throws Exception{
                L1PcInstance gm = args.getOwner();
                ThreadPool.getInstance().execute(new Runnable(){
                    @Override
                    public void run(){
                        gm.sendPackets(String.format("%d명의 캐릭터 주간퀘스트를 초기화하였습니다.", BQSWQDecksLoader.getInstance().notifyUpdated()));
                    }
                });
            }
        });
    }
    
    private MJCommandTree createSpawnCommand(){
        return new MJCommandTree("스폰", "북아이디를 기반으로 몬스터를 소환합니다.", new String[]{"북아이디", "마릿수"}){
            @Override
            protected void to_handle_command(MJCommandArgs args) throws Exception{
                int criteria_id = args.nextInt();
                int count = args.nextInt();
                BQSInformation bqsInfo = BQSInformationLoader.getInstance().getInformation(criteria_id);
                if(bqsInfo == null){
                    args.notify(String.format("도감번호 %d을(를) 찾을 수 없습니다.", criteria_id));
                    return;
                }
                for(int i=count - 1; i>=0; --i)
                    L1SpawnUtil.spawn(args.getOwner(), bqsInfo.getNpcId(), 0, 0);
                args.notify(String.format("%s(%d:%d)을(를) %d마리 소환했습니다.", bqsInfo.getNpcName(), bqsInfo.getNpcId(), criteria_id, count));
            }
        };
    }
    
    private MJCommandTree createCharacterUpdateCommand(){
        return new MJCommandTree("케릭터업데이트", "보내온 북아이디에 해당하는 도감정보를 지정된 케릭터의 도감에서 업데이트 시킵니다.", new String[]{"케릭터이름", "북아이디", "변경시킬 수"}){
            @Override
            protected void to_handle_command(MJCommandArgs args) throws Exception{
                String character_name = args.nextString();
                int criteria_id = args.nextInt();
                int count = args.nextInt();
                L1PcInstance gm = args.getOwner();
                L1PcInstance pc = L1World.getInstance().getPlayer(character_name);
                if(pc == null){
                    args.notify("케릭터 이름 %s을(를) 찾을 수 없습니다.");
                    return;
                }
                
                BQSInformation bqsInfo = BQSInformationLoader.getInstance().getInformation(criteria_id);
                if(bqsInfo == null){
                    args.notify(String.format("도감번호 %d을(를) 찾을 수 없습니다.", criteria_id));
                    return;
                }
                
                ThreadPool.getInstance().execute(new Runnable(){
                    @Override
                    public void run(){
                        BQSCharacterData bqs = pc.getBqs();
                        if(bqs == null)
                            return;
                        try{
                            for(int i=count - 1; i>=0; --i){
                                bqs.onUpdate(bqsInfo);
                                Thread.sleep(100L);
                            }
                        }catch(Exception e){
                            e.printStackTrace();
                        }finally{
                            if(gm != null)
                                gm.sendPackets(String.format("%s의 도감 %d번을(를) %d만큼 증가 시켰습니다.", character_name, criteria_id, count));
                        }
                    }
                });
            }
        };
    }
    
    private MJCommandTree createReloadConfigCommand(){
        return new MJCommandTree("컨피그리로드", "도감 시스템에서 사용하는 컨픽을 리로드합니다.", null){
            @Override
            protected void to_handle_command(MJCommandArgs args) throws Exception{
                loadConfig();
                BQSWQUpdator.getInstance().update_listener();
                args.notify("mj_bookquestsystem.properties가 리로드 되었습니다.");
            }
        };
    }
    
    private MJCommandTree createInitializedCommand(){
        return new MJCommandTree("시스템초기화", "모든 도감 정보가 초기화됩니다.", null){
            @Override
            protected void to_handle_command(MJCommandArgs args) throws Exception{
                try{
                    SC_PacketBox.broadcastGreen("시스템 갱신으로 인해 도감 시스템이 잠시 중단됩니다.");
                    loadConfig();
                    BQS_IS_ONUPDATE_BOOKS = false;
                    BQSInformationLoader.reload();
                    BQSRewardsLoader.reload();
                    BQSWQInformationLoader.reload();
                    BQSWQRewardsLoader.reload();
                    BQSWQDecksLoader.truncate();
                    BQSCharacterDataLoader.deleteCharactersBps(L1World.getInstance().getAllPlayers());
                    BQSWQDecksLoader.reload();
                    truncateBqsUpdateCalendar();
                    BQSWQUpdator.getInstance().update_listener();
                }catch(Exception e){
                    e.printStackTrace();
                }finally{
                    BQS_IS_ONUPDATE_BOOKS = true;
                    SC_PacketBox.broadcastGreen("도감 시스템이 다시 활성화 되었습니다. 원할한 게임진행을 위해 게임을 재시작 해주시기 바랍니다.");
                }
            }
        };
    }
    
    @Override
    public void execute(MJCommandArgs args) {
        _commands.execute(args, new StringBuilder(256).append(_commands.to_operation()));
    }
}