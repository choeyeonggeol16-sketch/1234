package l1j.server.server.model.Instance;

import l1j.server.server.model.character.action.CAttackAction;

import java.sql.Connection;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.PreparedStatement;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import l1j.server.MJCompanion.CompanionSettings;
import l1j.server.MJCompanion.Basic.Buff.MJCompanionBuffInfo;
import l1j.server.MJCompanion.Basic.ClassInfo.MJCompanionClassInfo;
import l1j.server.MJCompanion.Basic.Exp.MJCompanionExpPenalty;
import l1j.server.MJCompanion.Basic.FriendShip.MJCompanionFriendShipBonus;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionClassSkillInfo;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillEffect;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillInfo;
import l1j.server.MJCompanion.Basic.Stat.MJCompanionStatEffect;
import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.MJCompanion.Instance.MJCompanionUpdateFlag;
import l1j.server.MJKDASystem.MJKDA;
import l1j.server.MJTemplate.MJObjectWrapper;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJTemplate.L1Instance.MJEffectTriggerInstance;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.BatchHandler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.server.ActionCodes;
import l1j.server.server.ThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.Ability;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PinkName;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.Packet.SC_COMPANION_BUFF_NOTI;
import l1j.server.server.packets.Protocol.Packet.SC_COMPANION_SKILL_NOTI;
import l1j.server.server.packets.Protocol.Packet.SC_COMPANION_SKILL_NOTI.Skill;
import l1j.server.server.packets.Protocol.Packet.SC_COMPANION_STATUS_NOTI;
import l1j.server.server.packets.Protocol.Packet.SC_UPDATE_INVENTORY_NOTI;
import l1j.server.server.packets.Protocol.Packet.SC_WORLD_PUT_OBJECT_NOTI;
import l1j.server.server.packets.Protocol.Packet.Companion.CompanionT.eCommand;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.npc.ai.dFollow;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.CharactersItemStorage;
import l1j.server.server.storage.mysql.MySqlCharacterStorage;
import l1j.server.server.templates.L1Npc;

import l1j.server.server.model.network.core.sc.packet.SC_HPMeter;
public class L1CompanionInstance extends L1NpcInstance{
    private static final Logger COMPANION_LOG = Logger.getLogger(L1CompanionInstance.class.getName());
    
    public static void do_store_companions(){
        for(L1CompanionInstance companion : L1World.getInstance().getAllCompanions()){
            companion.update_instance();
            companion.update_buffs();
            companion.update_skills();
        }
    }
    
    private static boolean unload_companion(L1PcInstance master, L1ItemInstance item){
        L1CompanionInstance companion = master.get_companion();
        if(companion == null)
            return false;
        
        if(companion.get_control_object_id() == item.getId()){
            master.remove_companion();		
            return true;
        }
        return false;
    }
    
    private static boolean use_stats_initialized(L1PcInstance master, L1ItemInstance item){
        if(item.getItemId() != CompanionSettings.STATS_INITIALIZED_ITEM_ID)
            return false;
        
        L1CompanionInstance companion = master.get_companion();
        if(companion == null){
            master.sendPackets("펫을 소환한 상태에서 사용해주세요.");
            return true;
        }
        if(companion.getLevel() < CompanionSettings.STATS_INITIALIZED_NEED_LEVEL){
            master.sendPackets(String.format("스탯 초기화를 위해서는 최소 %d레벨 이상이 되어야 합니다.", CompanionSettings.STATS_INITIALIZED_NEED_LEVEL));
            return true;
        }
        if(!master.getInventory().consumeItem(CompanionSettings.STATS_INITIALIZED_ITEM_ID, 1)){
            master.sendPackets("아이템 정보를 찾을 수 없습니다.");
            return true;
        }
        companion.do_stats_initialized();
        return true;
    }
    
    private static boolean use_name_change_initialized(L1PcInstance master, L1ItemInstance item){
        if(item.getItemId() != CompanionSettings.NAME_CHANGED_ITEM_ID)
            return false;
        
        L1CompanionInstance companion = master.get_companion();
        if(companion == null){
            master.sendPackets("펫을 소환한 상태에서 사용해주세요.");
            return true;
        }
        
        if(!master.getInventory().consumeItem(CompanionSettings.NAME_CHANGED_ITEM_ID, 1)){
            master.sendPackets("아이템 정보를 찾을 수 없습니다.");
            return true;
        }
        String source_name = companion.getName();
        companion.set_name_changed_count(0);
        companion.do_name_change(companion.get_class_info().get_class_npc_name_id());
        master.sendPackets(String.format("%s의 이름을 초기화했습니다.", source_name));
        return true;
    }
    
    private static boolean use_friend_ship_bonuses(L1PcInstance master, L1ItemInstance item){
        MJCompanionFriendShipBonus bonus = MJCompanionFriendShipBonus.from_item_id(item.getItemId());
        if(bonus == null)
            return false;
        
        L1CompanionInstance companion = master.get_companion();
        if(companion == null){
            master.sendPackets("펫을 소환한 상태에서 사용해주세요.");
            return true;
        }
        if(!master.getInventory().consumeItem(item.getItemId(), 1)){
            master.sendPackets("아이템 정보를 찾을 수 없습니다.");
            return true;
        }
        int friend_ship_guage = bonus.get_friend_ship_guage();
        int friend_ship_marble = bonus.get_friend_ship_marble();
        int update_flag = MJCompanionUpdateFlag.UPDATE_FRIEND_SHIP;
        if(friend_ship_guage > 0){
            companion.add_friend_ship_guage(friend_ship_guage);
            if(companion.get_friend_ship_guage() >= CompanionSettings.FRIEND_SHIP_MAX_GUAGE)
                companion.do_combo_run();
            update_flag |= MJCompanionUpdateFlag.UPDATE_COMBO_TIME | MJCompanionUpdateFlag.UPDATE_DELAY_REDUCE;
            master.sendPackets(String.format("%s의 투지게이지가 %d만큼 상승했습니다.", companion.getName(), friend_ship_guage));
        }
        if(friend_ship_marble > 0){
            companion.add_friend_ship_marble(friend_ship_marble);
            companion.update_friend_ship_marble();
            master.sendPackets(String.format("%s와의 우정포인트가 %d만큼 상승했습니다.", companion.getName(), friend_ship_marble));
        }
        SC_COMPANION_STATUS_NOTI.send(master, companion, update_flag);
        if(bonus.get_effect_id() > 0)
            companion.send_effect(bonus.get_effect_id());
        return true;
    }
    
    private static boolean use_buff_item(L1PcInstance master, L1ItemInstance item){
        MJCompanionBuffInfo bInfo = MJCompanionBuffInfo.from_item_id(item.getItemId());
        if(bInfo == null)
            return false;
        
        L1CompanionInstance companion = master.get_companion();
        if(companion == null){
            master.sendPackets("펫을 소환한 상태에서 사용해주세요.");
            return true;
        }
        if(!master.getInventory().consumeItem(item.getItemId(), 1)){
            master.sendPackets("아이템 정보를 찾을 수 없습니다.");
            return true;
        }
        
        for(int skill_id : bInfo.get_deduplications_spell_id()){
            if(companion.hasSkillEffect(skill_id))
                companion.removeSkillEffect(skill_id);
        }
        SC_COMPANION_BUFF_NOTI.send(master, bInfo.get_spell_id(), bInfo.get_duration());
        companion.setSkillEffect(bInfo.get_spell_id(), bInfo.get_duration() * 1000);
        if(bInfo.get_pvp_offense() > 0)
            SC_COMPANION_STATUS_NOTI.send(master, companion, MJCompanionUpdateFlag.UPDATE_PVP_DAMAGE);
        if(bInfo.get_effect_id() > 0)
            companion.send_effect(bInfo.get_effect_id());
        if(bInfo.get_trigger_effect_id() > 0){
            MJEffectTriggerInstance trigger = MJEffectTriggerInstance.newInstance(
                    bInfo.get_spell_id(), bInfo.get_trigger_effect_id(), 0, companion.getX(), companion.getY(), companion.getMapId());
            companion.add_trigger(trigger);
        }
        return true;
    }
    
    private static boolean use_elixir(L1PcInstance master, L1ItemInstance item){
        int item_id = item.getItemId();
        if(item_id == CompanionSettings.ELIXIR_STR_ITEM_ID){
            use_elixir(master, item_id, new int[]{ 1, 0, 0});
        }else if(item_id == CompanionSettings.ELIXIR_CON_ITEM_ID){
            use_elixir(master, item_id, new int[]{ 0, 1, 0});
        }else if(item_id == CompanionSettings.ELIXIR_INT_ITEM_ID){			
            use_elixir(master, item_id, new int[]{ 0, 0, 1});
        }else{
            return false;
        }
        return true;
    }
    
    private static void use_elixir(L1PcInstance master, int item_id, int[] added_stats){
        L1CompanionInstance companion = master.get_companion();
        if(companion == null){
            master.sendPackets("펫을 소환한 상태에서 사용해주세요.");
            return;
        }
        if(companion.get_remain_elixir() <= 0){			
            master.sendPackets("펫에게 남아있는 엘릭서 갯수가 모자랍니다.");
            return;
        }
        Ability ability = companion.getAbility();
        if(ability.getAddedStr() + added_stats[0] > CompanionSettings.MAX_STAT ||
                ability.getAddedCon() + added_stats[1] > CompanionSettings.MAX_STAT ||
                ability.getAddedInt() + added_stats[2] > CompanionSettings.MAX_STAT
                ){
            master.sendPackets(String.format("모든 스탯은 %d까지만 올릴 수 있습니다.", CompanionSettings.MAX_STAT));
            return;
        }
        if(!master.getInventory().consumeItem(item_id, 1)){
            master.sendPackets("아이템 정보를 찾을 수 없습니다.");
            return;
        }
        ability.addAddedStr(added_stats[0]);
        ability.addAddedCon(added_stats[1]);
        ability.addAddedInt(added_stats[2]);
        companion.set_elixir_use_count(companion.get_elixir_use_count() + 1);
        companion.update_stats();
        companion.update_elixir_use_count();
        MJCompanionInstanceCache.update_cache(companion, true);
        SC_COMPANION_STATUS_NOTI.send(master, companion, MJCompanionUpdateFlag.UPDATE_ALL);
    }
    
    public static boolean use_item(L1PcInstance master, L1ItemInstance item){
        if(use_stats_initialized(master, item))
            return true;
        
        if(use_friend_ship_bonuses(master, item))
            return true;
        
        if(use_buff_item(master, item))
            return true;
        
        if(use_elixir(master, item))
            return true;
        
        if(use_name_change_initialized(master, item))
            return true;
        
        MJCompanionClassInfo cInfo = MJCompanionClassInfo.from_item_id(item.getItemId());
        if(cInfo == null)
            return false;
        
        if(unload_companion(master, item))
            return true;
        
        if(master.get_companion() != null){
            master.remove_companion();
            return true;
        }
        
        L1CompanionInstance companion = from_database(master, item.getId());
        if(companion == null){
            companion = newInstance(master, cInfo, item.getId());
            companion.update_instance();
            companion.update_skills();
        }else{
            if(companion.getCurrentHp() <= 0){
                if(!master.getInventory().checkItem(41246, 1000)){
                    master.sendPackets("결정체가 부족합니다.");
                    return true;
                }
                master.getInventory().consumeItem(41246, 1000);
                companion.setDead(true);
                companion.resurrect(companion.get_base_max_hp() / 4);
                companion.setResurrect(true);
            }
            companion.set_is_oblivion(false);
            companion.load_skills();
            companion.load_buffs();
        }

        companion.update_skill_effect();
        companion.update_stats();
        master.set_companion(companion);
        companion.set_master(master);
        companion.set_master_id(master.getId());
        companion.set_master_name(master.getName());
        companion.setX(master.getX() + (MJRnd.next(3) * (MJRnd.isBoolean() ? 1 : -1)));
        companion.setY(master.getY() + (MJRnd.next(3) * (MJRnd.isBoolean() ? 1 : -1)));
        companion.setMap(master.getMapId());
        companion.setHeading(master.getHeading());
        companion.setLightSize(companion.getNpcTemplate().getLightSize());
        companion.do_store_object();			
        SC_COMPANION_SKILL_NOTI.send(master, companion);
        SC_COMPANION_BUFF_NOTI.send(master, companion);
        companion.set_command_state(eCommand.TM_Aggressive);
        MJCompanionInstanceCache.update_cache(companion, true);
        return true;
    }
    
    private static L1CompanionInstance from_database(final L1PcInstance master, final int control_object_id){
        final MJObjectWrapper<L1CompanionInstance> wrapper = new MJObjectWrapper<L1CompanionInstance>();
        Selector.exec("select * from companion_instance where control_object_id=?", new SelectorHandler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, control_object_id);
            }

            @Override
            public void result(ResultSet rs) throws Exception {
                if(rs.next()){
                    wrapper.value = newInstance(master, rs);
                }
            }
        });
        return wrapper.value;
    }
    
    private static L1CompanionInstance newInstance(L1PcInstance master, MJCompanionClassInfo cInfo, int control_object_id){
        L1CompanionInstance companion = newInstance();
        companion.setId(IdFactory.getInstance().nextId());
        companion.set_control_object_id(control_object_id);
        companion.set_class_info(cInfo);
        
        L1Npc npc = companion.getNpcTemplate();
        companion.setName(cInfo.get_class_npc_name_id());
        companion.setLevel(1);
        companion.setExp(1);
        companion.setMaxHp(npc.get_hp());
        companion.setMaxMp(npc.get_mp());
        companion.setCurrentHp(companion.getMaxHp());
        companion.setCurrentMp(companion.getMaxMp());
        companion.setLawful(npc.get_lawful());
        companion.set_tier(1);
        companion.set_is_oblivion(false);
        companion.set_name_changed_count(0);
        MJCompanionClassSkillInfo class_skill_info = MJCompanionClassSkillInfo.get_companion_skills(cInfo.get_class_name(), companion.get_tier());
        companion.do_open_tier(class_skill_info);
        return companion;
    }
    
    private static L1CompanionInstance newInstance(L1PcInstance master, ResultSet rs) throws SQLException{
        L1CompanionInstance companion = newInstance();
        companion.setId(rs.getInt("object_id"));
        companion.set_control_object_id(rs.getInt("control_object_id"));
        MJCompanionClassInfo cInfo = MJCompanionClassInfo.from_class_name(rs.getString("class_name"));
        if(cInfo == null)
            throw new IllegalArgumentException(String.format("MJCompanionInstance::newInstance()...invalid class info %s", rs.getString("class_name")));
        companion.set_class_info(cInfo);
        companion.setName(rs.getString("companion_name"));
        companion.setLevel(rs.getInt("level"));
        companion.setExp(rs.getInt("exp"));
        companion.set_keep_exp(rs.getInt("keep_exp"));
        companion.set_friend_ship_guage(rs.getInt("friend_ship_guage"));
        companion.set_friend_ship_marble(rs.getInt("friend_ship_marble"));
        companion.setMaxHp(rs.getInt("max_hp"));
        companion.setMaxMp(rs.getInt("max_mp"));
        companion.setCurrentHp(rs.getInt("current_hp"));
        companion.setCurrentMp(rs.getInt("current_mp"));
        companion.setLawful(rs.getInt("lawful"));
        companion.set_tier(rs.getInt("tier"));
        companion.getAbility().addAddedStr(rs.getInt("str"));
        companion.getAbility().addAddedCon(rs.getInt("con"));
        companion.getAbility().addAddedInt(rs.getInt("intel"));
        companion.set_elixir_use_count(rs.getInt("elixir_use_count"));
        companion.set_is_oblivion(rs.getInt("is_oblivion") != 0);
        companion.set_is_traning(rs.getInt("is_traning") != 0);
        companion.set_name_changed_count(rs.getInt("name_changed_count"));
        return companion;
    }
    
    private static L1CompanionInstance newInstance(){
        return new L1CompanionInstance();
    }
    
    private static final long serialVersionUID = 1L;
    private int m_control_object_id;
    private MJCompanionClassInfo m_class_info;
    private eCommand m_command_state;
    private L1PcInstance m_master;
    private int m_master_id;
    private String m_master_name;
    private int m_tier;
    private int m_keep_exp;
    private int m_elixir_use_count;
    private int m_friend_ship_guage;
    private int m_friend_ship_marble;
    private int m_pvp_dmg_ratio;
    private boolean m_is_oblivion;
    private boolean m_is_traning;
    private boolean m_is_combo_time;
    private int m_combo_count;
    private int m_name_changed_count;
    private ArrayList<Skill> m_exercise_skills;
    private ArrayList<Skill> m_non_exercise_skills;
    private MJCompanionSkillEffect m_skill_effect;
    private MJCompanionStatEffect m_stat_effect;
    private boolean m_is_teleporting;
    private long m_pink_name_millis;
    private Object m_death_lock;
    private ConcurrentHashMap<Integer, MJEffectTriggerInstance> m_triggers;
    private L1CompanionInstance(){
        super(null);
    
        m_exercise_skills = new ArrayList<Skill>();
        m_non_exercise_skills = new ArrayList<Skill>();
        m_is_combo_time = false;
        m_is_teleporting = false;
        m_is_traning = false;
        m_pink_name_millis = 0;
        m_death_lock = new Object();
        m_combo_count = 0;
        m_name_changed_count = 0;
        m_triggers = new ConcurrentHashMap<Integer, MJEffectTriggerInstance>();
    }
    
    public void set_control_object_id(int control_object_id){
        m_control_object_id = control_object_id;
    }
    public int get_control_object_id(){
        return m_control_object_id;
    }
    public void set_class_info(MJCompanionClassInfo cInfo){
        m_class_info = cInfo;
        L1Npc npc = NpcTable.getInstance().getTemplate(cInfo.get_npc_id());
        if(npc == null)
            throw new IllegalArgumentException(String.format("MJCompanionInstance::set_class_info()... found companion npc template...class name %s, npc id : %d ", cInfo.get_class_name(), cInfo.get_npc_id()));
        setting_template(npc);
    }
    public MJCompanionClassInfo get_class_info(){
        return m_class_info;
    }
    public void set_command_state(eCommand command_state){
        m_command_state = command_state;
        if(m_command_state.equals(eCommand.TM_GetItem)){
            find_items();
        }else{
            if(!isAiRunning())
                startAI();
        }
    }
    public eCommand get_command_state(){
        return m_command_state;
    }
    public void set_master(L1PcInstance master){
        m_master = master;
    }
    public L1PcInstance get_master(){
        return m_master;
    }
    public void set_master_id(int master_id){
        m_master_id = master_id;
    }
    public int get_master_id(){
        return m_master_id;
    }
    public void set_master_name(String master_name){
        m_master_name = master_name;
    }
    public String get_master_name(){
        return m_master_name;
    }
    public void set_tier(int tier){
        m_tier = tier;
    }
    public int get_tier(){
        return m_tier;
    }
    public void set_keep_exp(int keep_exp){
        m_keep_exp = keep_exp;
    }
    public int get_keep_exp(){
        return m_keep_exp;
    }
    public void set_elixir_use_count(int elixir_use_count){
        m_elixir_use_count = elixir_use_count;
    }
    public int get_elixir_use_count(){
        return m_elixir_use_count;
    }
    public void set_friend_ship_guage(int friend_ship_guage){
        m_friend_ship_guage = friend_ship_guage;
    }
    public void add_friend_ship_guage(int friend_ship_guage){
        m_friend_ship_guage = Math.min(m_friend_ship_guage + (int)friend_ship_guage, CompanionSettings.FRIEND_SHIP_MAX_GUAGE);
    }
    public int get_friend_ship_guage(){
        return m_friend_ship_guage;
    }
    public boolean get_is_combo_time(){
        return m_is_combo_time;
    }
    public void set_is_combo_time(boolean is_combo_time){
        m_is_combo_time = is_combo_time;
    }
    public int get_combo_count(){
        return m_combo_count;
    }
    public void set_combo_count(int combo_count){
        m_combo_count = combo_count;
    }
    public int increase_combo_count(){
        return ++m_combo_count;
    }
    public void set_name_changed_count(int name_changed_count){
        m_name_changed_count = name_changed_count;
    }
    public int get_name_changed_count(){
        return m_name_changed_count;
    }
    public int increase_name_changed_count(){
        return ++m_name_changed_count;
    }
    public void set_friend_ship_marble(int friend_ship_marble){
        m_friend_ship_marble = friend_ship_marble;
    }
    public void add_friend_ship_marble(int friend_ship_marble){
        m_friend_ship_marble += friend_ship_marble;
    }
    public int get_friend_ship_marble(){
        return m_friend_ship_marble;
    }
    public double get_attackdelay_reduce(){
        return (m_skill_effect == null ? 0D : m_skill_effect.get_attack_delay_reduce()) +
                (get_is_combo_time() ? CompanionSettings.COMBO_ATTACK_DELAY_REDUCE : 0D);
    }
    public double get_movedelay_reduce(){
        return m_skill_effect == null ? 0D : m_skill_effect.get_move_delay_reduce() + 
                (get_is_combo_time() ? CompanionSettings.COMBO_MOVE_DELAY_REDUCE : 0D);
    }
    public int get_pvp_dmg_ratio(){
        return m_pvp_dmg_ratio + MJCompanionBuffInfo.get_pvp_melee_offense_bonus(this);
    }
    public void set_pvp_dmg_ratio(int pvp_dmg_ratio){
        m_pvp_dmg_ratio = pvp_dmg_ratio;
    }
    public boolean get_is_oblivion(){
        return m_is_oblivion;
    }
    public void set_is_oblivion(boolean is_oblivion){
        m_is_oblivion = is_oblivion;
    }
    public boolean get_is_traning(){
        return m_is_traning;
    }
    public void set_is_traning(boolean is_traning){
        m_is_traning = is_traning;
    }
    public void set_exercise_skills(ArrayList<Skill> exercise_skills){
        m_exercise_skills = exercise_skills;
    }
    public void add_exercise_skills(Skill sk){
        m_exercise_skills.add(sk);
    }
    public ArrayList<Skill> get_exercise_skills(){
        return m_exercise_skills;
    }
    public MJCompanionSkillInfo select_exercise_skills(){
        if(m_exercise_skills == null || m_exercise_skills.size() <= 0)
            return null;
        
        Skill sk = m_exercise_skills.get(MJRnd.next(m_exercise_skills.size()));
        return sk == null ? null : MJCompanionSkillInfo.get_companion_skills(sk.get_id(), sk.get_enchant());
    }
    public void set_non_exercise_skills(ArrayList<Skill> non_exercise_skills){
        m_non_exercise_skills = non_exercise_skills;
    }
    public void add_non_exercise_skills(Skill sk){
        m_non_exercise_skills.add(sk);
    }
    public ArrayList<Skill> get_non_exercise_skills(){
        return m_non_exercise_skills;
    }
    public ArrayList<Skill> get_all_skills(){
        ArrayList<Skill> skills = new ArrayList<Skill>(m_exercise_skills);
        skills.addAll(m_non_exercise_skills);
        return skills;
    }
    public Skill find_skills(int skill_id){
        Skill sk = find_skills(skill_id, m_non_exercise_skills);
        return sk == null ? find_skills(skill_id, m_exercise_skills) : sk;
    }
    public Skill find_skills(int skill_id, ArrayList<Skill> skills){
        for(Skill sk : skills){
            if(skill_id == sk.get_id())
                return sk;
        }
        return null;
    }
    
    public MJCompanionSkillEffect get_skill_effect(){
        return m_skill_effect;
    }
    public int get_melee_dmg(){
        return m_skill_effect.get_melee_dmg() +
                m_stat_effect.get_melee_dmg();
    }
    public int get_melee_hit(){
        return m_skill_effect.get_melee_hit() +
                m_stat_effect.get_melee_hit();
    }
    public int get_melee_cri_hit(){
        return m_skill_effect.get_melee_cri_hit() +
                m_stat_effect.get_melee_cri();
    }
    public int get_ignore_reduction(){
        return m_skill_effect.get_ignore_reduction();
    }
    public int get_blood_suck_hit(){
        return m_skill_effect.get_blood_suck_hit();
    }
    public int get_blood_suck_heal(){
        return m_skill_effect.get_blood_suck_heal();
    }
    public int get_dmg_reduction(){
        return m_skill_effect.get_dmg_reduction() +
                m_stat_effect.get_reduction();
    }
    public int get_spell_dmg_multi(){
        return m_skill_effect.get_spell_dmg_multi() +
                m_stat_effect.get_skill_dmg();
    }
    public int get_spell_hit(){
        return m_skill_effect.get_spell_hit() +
                m_stat_effect.get_skill_ratio();
    }
    public int get_spell_dmg(){
        return m_skill_effect.get_spell_dmg_add() +
                m_stat_effect.get_skill_dmg();
    }
    public int get_spell_cri(){
        return m_stat_effect.get_skill_cri();
    }
    public int get_fire_elemental_dmg(){
        return m_skill_effect.get_fire_elemental_dmg();
    }
    public int get_water_elemental_dmg(){
        return m_skill_effect.get_water_elemental_dmg();
    }
    public int get_air_elemental_dmg(){
        return m_skill_effect.get_air_elemental_dmg();
    }
    public int get_earth_elemental_dmg(){
        return m_skill_effect.get_earth_elemental_dmg();
    }
    public int get_light_elemental_dmg(){
        return m_skill_effect.get_light_elemental_dmg();
    }
    public int get_combo_dmg(){
        return m_skill_effect.get_combo_dmg_multi();
    }
    public void use_healing_potion(int heal_hp){
        applyNpcHpDelta(m_skill_effect.get_potion_hp() + heal_hp);
    }
    public void set_is_teleporting(boolean is_teleporting){
        m_is_teleporting = is_teleporting;
    }
    public boolean get_is_teleporting(){
        return m_is_teleporting;
    }
    public void update_pink_name_millis(){
        m_pink_name_millis = System.currentTimeMillis();
    }
    public long get_pink_name_millis(){
        return m_pink_name_millis;
    }
    public void add_trigger(MJEffectTriggerInstance trigger){
        m_triggers.put(trigger.get_identity(), trigger);
    }
    public void remove_trigger(int identity){
        MJEffectTriggerInstance trigger = m_triggers.remove(identity);
        if(trigger != null){
            trigger.dispose();
        }
    }
    public void remove_triggers(){
         ArrayList<MJEffectTriggerInstance> triggers = get_triggers();
         m_triggers.clear();
         if(triggers != null){
             for(MJEffectTriggerInstance trigger : triggers){
                    trigger.dispose();
                }
         }
    }
    public ArrayList<MJEffectTriggerInstance> get_triggers(){
        return m_triggers.size() <= 0 ? null : new ArrayList<MJEffectTriggerInstance>(m_triggers.values());
    }
    
    public int get_pink_name_remain_seconds(){
        long diff = System.currentTimeMillis() - get_pink_name_millis();
        return (int)(diff >= 1000 && diff < 20000 ? diff / 1000 : 0);
    }
    
    public void do_name_change(String replace_name){
        setName(replace_name);
        update_name();
        byte[] data = SC_WORLD_PUT_OBJECT_NOTI.newInstance(this).encode();

        for (L1PcInstance visible : L1World.getInstance().getRecognizePlayer(this)) {
        	SC_RemoveObject.send(
        			visible.getNetConnection(),
        			this);

            l1j.server.server.packets.Protocol.Proto.sendRaw(visible, data);
        }
        if(m_master != null)
            SC_COMPANION_STATUS_NOTI.send(m_master, this, MJCompanionUpdateFlag.UPDATE_NAME);
    }
    
    public void do_open_tier(MJCompanionClassSkillInfo class_skill_info){
        set_tier(class_skill_info.get_tier());
        ArrayList<Skill> skills = new ArrayList<Skill>();
        for(int skill_id : class_skill_info.get_skills_id()){
            Skill sk = Skill.newInstance();
            sk.set_id(skill_id);
            sk.set_enchant(0);
            skills.add(sk);
            if(MJCompanionSkillInfo.is_exercise_skill(skill_id))
                add_exercise_skills(sk);
            else
                add_non_exercise_skills(sk);
        }
        if(skills.size() > 0)
            update_skills(skills);
    }
    public int get_remain_stats(){
        int level = getLevel();
        Ability ability = getAbility();
        int total_added_stats = 
                ability.getAddedStr() + 
                ability.getAddedCon() + 
                ability.getAddedInt() -
                get_elixir_use_count();
        
        int added_stat = (Math.min(level, 50)) / 10;
        if(level > 50)
            added_stat += (level - 50);
        
        return added_stat - total_added_stats;
    }
    
    public int get_remain_elixir(){
        int level = getLevel();
        if(level < CompanionSettings.ELIXIR_MIN_USE_LEVEL)
            return 0;
        
        int remain_level = (level - CompanionSettings.ELIXIR_MIN_USE_LEVEL) + CompanionSettings.ELIXIR_BY_LEVEL;
        int remain_elixir = remain_level / CompanionSettings.ELIXIR_BY_LEVEL;
        return Math.min(remain_elixir, CompanionSettings.ELIXIR_MAX_USE_COUNT);
    }
    
    public void set_target(L1Character target){
        if(target == null)
            return;
        
        if(m_command_state.equals(eCommand.TM_Aggressive)){
            setHate(target, 0);
            if(_target != null){
                setHate(_target, 0);
                _target = target;
            }
            if(!isAiRunning())
                startAI();
        }
    }
    
    private void set_target_by_defensive(L1Character target){
        if(target == null)
            return;
        
        if(m_command_state.equals(eCommand.TM_Defensive)){
            setHate(target, 0);
            if(_target != null){
                setHate(_target, 0);
                _target = target;
            }
            if(!isAiRunning())
                startAI();
        }
    }
    
    public void do_store_object(){
        L1World.getInstance().storeAndEnterWorld(this);
        for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
            onPerceive(pc);
        }
    }
    
    private void do_death_penalty() {
        int old_level = getLevel();
        if(old_level <= 30)
            return;
        
        if(m_master != null && 
                m_master.getInventory().checkItem(CompanionSettings.EXP_SAFETY_ITEMID) &&
                m_master.getInventory().consumeItem(CompanionSettings.EXP_SAFETY_ITEMID, 1)){
            m_master.sendPackets("가호로 인해 펫의 경험치가 보호되었습니다.");
            return;
        }
        int need_exp = ExpTable.getNeedExpNextLevel(old_level);
        int exp = (int)(need_exp * 0.05);
        final int controlObjectId = get_control_object_id();
        final int ownerId = m_master != null ? m_master.getId() : getId();
        // Admission precedes the memory mutation. If CacheD is full/closed,
        // leave the companion's XP untouched rather than accepting a change
        // with no persistence work. SQL failures remain in the ordered retry lane.
        try {
            CacheD.getInstance().submitDbJobRetryOrdered(ownerId, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "UPDATE companion_instance SET keep_exp=? WHERE control_object_id=?")) {
                ps.setInt(1, exp);
                ps.setInt(2, controlObjectId);
                ps.executeUpdate();
            } catch (java.sql.SQLException ex) {
                throw new IllegalStateException("Companion recovery persistence failed: " + controlObjectId, ex);
            }
            });
        } catch (RejectedExecutionException rejected) {
            // Death/cleanup must still complete. Do NOT apply an XP penalty
            // if its recovery value cannot be durably queued.
            COMPANION_LOG.log(Level.SEVERE, "Companion penalty persistence admission failed: " + controlObjectId, rejected);
            return;
        }
        set_keep_exp(exp);
        update_exp(-exp);
    }
    
    public void do_restore_keep_exp(boolean is_pass_needed){
        int keep_exp = get_keep_exp();
        if(keep_exp <= 0){
            m_master.sendPackets("복구할 경험치가 없습니다.");
            return;
        }
        if(!is_pass_needed){
            if(CompanionSettings.EXP_RESTORE_NEED_ADENA > 0){
                if(!m_master.getInventory().checkItem(L1ItemId.ADENA, CompanionSettings.EXP_RESTORE_NEED_ADENA) ||
                        !m_master.getInventory().consumeItem(L1ItemId.ADENA, CompanionSettings.EXP_RESTORE_NEED_ADENA))
                m_master.sendPackets(String.format("경험치 복구를 위해서는 최소 아데나 %d(이)가 필요합니다.", CompanionSettings.EXP_RESTORE_NEED_ADENA));
                return;
            }
        }
        
        set_keep_exp(0);
        update_exp(getExp() + keep_exp, MJCompanionUpdateFlag.UPDATE_EXP);
        update_keep_exp();
    }
    
    public void do_combo_run(){
        set_is_combo_time(true);
        set_friend_ship_guage(0);
        add_friend_ship_marble(CompanionSettings.FRIEND_SHIP_TO_MARBLE);
        update_friend_ship_marble();
        ThreadPool.getInstance().scheduleRequired(new Runnable(){
            @Override
            public void run(){
                if(!get_is_combo_time())
                    return;
                
                set_is_combo_time(false);
                if(m_master != null){
                    SC_COMPANION_STATUS_NOTI.send(m_master, L1CompanionInstance.this, 
                            MJCompanionUpdateFlag.UPDATE_FRIEND_SHIP | MJCompanionUpdateFlag.UPDATE_DELAY_REDUCE | MJCompanionUpdateFlag.UPDATE_COMBO_TIME);	
                }
            }
        }, CompanionSettings.COMBO_DURATION_MILLIS);
    }

    public void do_update_regen_tick(){
        int hpr = m_skill_effect.get_regen_hp();
        hpr += m_stat_effect.get_hpr();
        hpr = Math.max(hpr, 1);
        applyNpcHpDelta(hpr);
        setCurrentMp(getCurrentMp() + 1);
    }
    
    public void do_oblivion(boolean is_pass_needed){
        if(!is_pass_needed){
            if(CompanionSettings.OBLIVION_NEED_ADENA > 0){
                if(!m_master.getInventory().checkItem(L1ItemId.ADENA, CompanionSettings.OBLIVION_NEED_ADENA) ||
                        !m_master.getInventory().consumeItem(L1ItemId.ADENA, CompanionSettings.OBLIVION_NEED_ADENA))
                m_master.sendPackets(String.format("망각하기 위해서는 최소 아데나 %d(이)가 필요합니다.", CompanionSettings.OBLIVION_NEED_ADENA));
                return;
            }			
        }
        
        m_master.sendPackets(String.format("펫 %s(이)를 망각시켰습니다.", getName()));
        set_is_oblivion(true);
        Ability ability = getAbility();
        ability.addAddedStr(-ability.getAddedStr());
        ability.addAddedCon(-ability.getAddedCon());
        ability.addAddedInt(-ability.getAddedInt());
        oblivion_buffs();
        oblivion_skills();
        set_tier(1);		
        MJCompanionClassSkillInfo class_skill_info = MJCompanionClassSkillInfo.get_companion_skills(get_class_info().get_class_name(), get_tier());
        do_open_tier(class_skill_info);
        setName(m_class_info.get_class_npc_name());
        set_name_changed_count(0);
        deleteMe();
    }
    
    public void do_stats_initialized(){
        if(m_master == null)
            return;
        
        Ability ability = getAbility();
        ability.addAddedStr(-ability.getAddedStr());
        ability.addAddedCon(-ability.getAddedCon());
        ability.addAddedInt(-ability.getAddedInt());
        update_stats();
        update_instance();
        SC_COMPANION_STATUS_NOTI.send(m_master, this, MJCompanionUpdateFlag.UPDATE_ALL);		
    }
    
    public void do_traning(boolean is_pass_needed){
        if(m_master == null)
            return;
        
        if(get_is_traning()){
            m_master.sendPackets("오늘 이미 훈련했습니다.");
            return;
        }
        
        if(getLevel() < CompanionSettings.TRANING_MIN_LEVEL){
            m_master.sendPackets(String.format("훈련은 펫의 레벨이 최소 %d 이상 되어야 합니다.", CompanionSettings.TRANING_MIN_LEVEL));
            return;
        }
        
        if(!is_pass_needed){
            if(get_friend_ship_marble() < CompanionSettings.TRANING_NEED_FRIEND_SHIP){
                m_master.sendPackets(String.format("훈련 시키기 위해서는 %d 우정 포인트가 필요합니다.", CompanionSettings.TRANING_NEED_FRIEND_SHIP));
                return;
            }
            
            if(!m_master.getInventory().checkItem(CompanionSettings.TRANING_NEED_ITEM_ID, CompanionSettings.TRANING_NEED_ITEM_COUNT) ||
                    !m_master.getInventory().consumeItem(CompanionSettings.TRANING_NEED_ITEM_ID, CompanionSettings.TRANING_NEED_ITEM_COUNT)){
                m_master.sendPackets(String.format("훈련 시키기 위해서는 환생의 보석이 %d개 필요합니다.", CompanionSettings.TRANING_NEED_ITEM_COUNT));
                return;
            }
            set_friend_ship_marble(get_friend_ship_marble() - CompanionSettings.TRANING_NEED_FRIEND_SHIP);
        }
        set_is_traning(true);
        update_traning();
        double exp = CompanionSettings.TRANING_PERCENT_BY_EXP * ExpTable.getNeedExpNextLevel(CompanionSettings.TRANING_EXP_BY_LEVEL);
        update_exp((int)(getExp() + exp), MJCompanionUpdateFlag.UPDATE_EXP);
        m_master.sendPackets(String.format("펫 %s(을)를 훈련시켰습니다.", getName()));
    }
    
    private void find_items(){
        if(m_master == null)
            return;
        
        ArrayList<L1GroundInventory> ground_inventories = new ArrayList<L1GroundInventory>();
        for(L1Object obj : L1World.getInstance().getVisibleObjects(this, 10)){
            if(obj != null && obj instanceof L1GroundInventory)
                ground_inventories.add((L1GroundInventory)obj);
        }

        for(L1GroundInventory inventory : ground_inventories){
            for(L1ItemInstance item : inventory.getItems()){
                if(item.getItemOwner() != null && item.getItemOwner().getId() != m_master.getId())
                    continue;
                
                if(_inventory.checkAddItem(item, item.getCount()) == L1Inventory.OK){
                    _targetItem = item;
                }
            }
        }
    }
    
    private void do_drain_items(){
        L1PcInventory master_inventory = m_master.getInventory();
        List<L1ItemInstance> items = _inventory.getItems();
        for(L1ItemInstance item : items){
            if(master_inventory.checkAddItem(item, item.getCount()) == L1Inventory.OK){
                _inventory.tradeItem(item,  item.getCount(), master_inventory);
                SC_ServerMessage.send(m_master, 143, getName(), item.getLogName());
            }else{
                _inventory.tradeItem(item,  item.getCount(), L1World.getInstance().getInventory(getX(), getY(), getMapId()));
            }
        }
    }
    
    private L1Inventory find_drop_inventory(){
        if(m_master != null){
            return m_master.getInventory();
        }
        L1Object obj = L1World.getInstance().findObject(m_master_id);
        if(obj != null && obj instanceof L1PcInstance){
            L1PcInstance pc = (L1PcInstance)obj;
            if(pc.getNetConnection() != null && pc.getNetConnection().isConnected())
                return pc.getInventory();
        }
        return MySqlCharacterStorage.isValidUserObjectId(m_master_id) ? 
                null : 
                L1World.getInstance().getInventory(getX(), getY(), getMapId());
    }
    
    private void do_drop_items(){
        List<L1ItemInstance> items = _inventory.getItems();
        if(items == null || items.size() <= 0)
            return;

        L1Inventory target_inventory = find_drop_inventory();		
        if(target_inventory == null){
            CharactersItemStorage storage = CharactersItemStorage.create();
            for(L1ItemInstance item : items){
                try {
                    storage.storeItem(m_master_id, item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            _inventory.clearItems();
        }else{
            for(L1ItemInstance item : items){
                _inventory.tradeItem(item, item.getCount(), target_inventory);
            }
        }
    }
    
    public void do_pink_name(){
        update_pink_name_millis();
        send_pink_name(20);
    }
    
    public void on_master_attacked(L1PcInstance receiver){
        if(m_command_state.equals(eCommand.TM_Aggressive)){
            update_pink_name_millis();
            send_pink_name(20);
        }
    }
    
    public boolean on_buff_stopped(int skill_id){
        MJCompanionBuffInfo bInfo = MJCompanionBuffInfo.from_spell_id(skill_id);
        if(bInfo == null)
            return false;
        
        if(m_master != null){
            SC_COMPANION_BUFF_NOTI.send(m_master, skill_id, 0);
            if(bInfo.get_pvp_offense() > 0)
                SC_COMPANION_STATUS_NOTI.send(m_master, this, MJCompanionUpdateFlag.UPDATE_PVP_DAMAGE);
        }
        if(bInfo.get_trigger_effect_id() > 0){
            remove_trigger(bInfo.get_spell_id());
        }
        return true;
    }
    
    @Override
    protected void onDeath(L1Character last_attacker) {
        synchronized(m_death_lock){
            if(isDead())
                return;
            setDead(true);
        }

        setCurrentHp(0);
        setStatus(ActionCodes.ACTION_Die);
        send_action(ActionCodes.ACTION_Die);
        releaseNpcTileNow();

        if (getZoneType() != 1 && getZoneType() != -1){
            if(last_attacker instanceof L1PcInstance && get_pink_name_remain_seconds() <= 0){
                L1PcInstance pc = (L1PcInstance)last_attacker;
                int lawful = MJKDA.calculate_lawful(pc);
                pc.setLawful(lawful);
                pc.send_lawful();
            }

            if(getMap().isEnabledDeathPenalty())
                do_death_penalty();
        }

        if(get_is_combo_time())
            set_is_combo_time(false);

        MJCompanionInstanceCache.update_cache(this, true);
        if(m_master != null)
            SC_COMPANION_STATUS_NOTI.send(m_master, this, MJCompanionUpdateFlag.UPDATE_ALL);

        ThreadPool.getInstance().scheduleRequired(() -> {
            if(isDead() && getCurrentHp() <= 0 && L1World.getInstance().contains_companion(getId())){
                if(m_master != null)
                    m_master.remove_companion();
                else
                    deleteMe();
            }else{
                MJCompanionInstanceCache.update_cache(this, false);
            }
        }, 60000);
    }

    @Override
    public void deleteMe(){
        if(get_is_combo_time()){
            set_is_combo_time(false);
        }
        MJCompanionInstanceCache.update_cache(this, false);
        update_instance();
        update_skills();
        update_buffs();
        do_drop_items();
        if(m_master != null){
            m_master.set_companion(null);
            m_master = null;
        }
        remove_triggers();
        super.deleteMe();
    }
    
    @Override
    public void onPerceive(L1PcInstance perceivedFrom) {
        perceivedFrom.addKnownObject(this);
        
        SC_WORLD_PUT_OBJECT_NOTI.sendNewInstance(perceivedFrom, this);
        if(perceivedFrom.getId() == get_master_id()){
            SC_COMPANION_STATUS_NOTI.send(perceivedFrom, this, MJCompanionUpdateFlag.UPDATE_ALL);
        }
//        if(isDead()){
//            SC_DoActionGFX.send(perceivedFrom.getNetConnection(), getId(), ActionCodes.ACTION_Die);
//        }else{
//            int remain_pink_name_seconds = get_pink_name_remain_seconds();
//            if(remain_pink_name_seconds > 0)
//                perceivedFrom.sendPackets(S_PinkName.create(getId(), remain_pink_name_seconds));
//        }
    }
    
    public int get_temp_max_hp(){
        return m_skill_effect == null ? 0 : m_skill_effect.get_max_hp();
    }
    
    public int get_base_max_hp(){
        return super.getMaxHp();
    }
    
    @Override
    protected void on_moved(){
        for(MJEffectTriggerInstance trigger : m_triggers.values())
            trigger.do_move(getX(), getY(), getHeading());
    }
    
    public void on_attacked(){
//		for(MJEffectTriggerInstance trigger : m_triggers.values())
//			trigger.do_action(0);
    }
    
    @Override
    public int getMaxHp() {
        return get_base_max_hp() + get_temp_max_hp();
    }
    
    @Override
    public void setCurrentHp(int i){
        super.setCurrentHp(i);
        if(m_master != null){
            SC_COMPANION_STATUS_NOTI.send(m_master, this, MJCompanionUpdateFlag.UPDATE_HP);
            SC_HPMeter.send(m_master, this);
        }
    }
    
    @Override
    protected void onNpcAtomicHpChanged(int hp) {
        if (m_master != null) {
            SC_COMPANION_STATUS_NOTI.send(m_master, this, MJCompanionUpdateFlag.UPDATE_HP);
            SC_HPMeter.send(m_master, this);
        }
    }

    @Override
    public void setCurrentMp(int i) {
        super.setCurrentMp(i);
    }
    
    @Override
    public boolean noTarget(){
        if(m_master == null){
            deleteMe();
            return true;
        }
        if(m_master.getMapId() != getMapId())
            return true;
        
        if(m_command_state.equals(eCommand.TM_GetItem)){
            find_items();
            if(_targetItem != null)
                return false;
        }
        
        if(getLocation().getTileLineDistance(m_master.getLocation()) > 2){
            dFollow.follow(this, m_master);

            if(m_command_state.equals(eCommand.TM_GetItem)){
                do_drain_items();
            }
        }
        return false;
    }

    @Override
    public boolean noTargetMaster(){
        return noTarget();
    }
    
    @Override
    public void receiveDamage(L1Character attacker, int damage){
        if(attacker instanceof L1PcInstance){
            if(m_master == null){
                deleteMe();
                return;
            }
            L1PinkName.onAction(m_master, attacker);
        }
        
        if(getCurrentHp() <= 0) return;
        if(hasSkillEffect(L1SkillId.FOG_OF_SLEEPING)){
            removeSkillEffect(L1SkillId.FOG_OF_SLEEPING);
        }else if (hasSkillEffect(L1SkillId.PHANTASM)){
            removeSkillEffect(L1SkillId.PHANTASM);
        }
        
        int reduction = get_dmg_reduction();
        damage -= reduction;
        damage -= MJCompanionBuffInfo.get_pvp_melee_defense_bonus(this);
        if(damage <= 0)
            return;
        
        if(attacker instanceof L1PcInstance){
            ((L1PcInstance)attacker).set_pet_target(this);
            damage = (int)(damage * CompanionSettings.MAGNIFICATION_BY_PC_TO_PET);
            set_target_by_defensive(attacker);
        }
        int new_hp = applyNpcHpDelta(-(long) damage);
        if (new_hp == 0) onDeath(attacker);
    }
    
    @Override
    public void onAction(L1PcInstance player){
        if(player == null)
            return;
        
        if(m_master == null){
            deleteMe();
            return;
        }
        
        if(m_master.get_teleport())
            return;
        
        if (getZoneType() == 1) {
            CAttackAction.swingOnly(player, this);
            return;
        }
        if (player.checkNonPvP(player, this))
            return;
        
        CAttackAction.attackPcNpc(player, this, false);
    }
    @Override
    public void onGetItem(L1ItemInstance item){
        if(item == null || item.getItem() == null || m_master == null)
            return;
        
        L1PcInventory master_inventory = m_master.getInventory();
        if(master_inventory.checkAddItem(item, item.getCount()) == L1Inventory.OK){
            _inventory.tradeItem(item,  item.getCount(), master_inventory);
            SC_ServerMessage.send(m_master, 143, getName(), item.getLogName());
        }else{
            _inventory.tradeItem(item,  item.getCount(), L1World.getInstance().getInventory(getX(), getY(), getMapId()));
        }
    }
    
    @Override
    public boolean checkCondition(){
        if(m_master == null){
            deleteMe();
            return true;
        }
        
        if(m_master.isInWarArea()){
            m_master.remove_companion();
            return true;
        }
        return false;
    }
    
    private void update_friend_ship_by_exp(int exp){
        double guage = exp * CompanionSettings.FRIEND_SHIP_BY_EXP;
        double guage_bonus = MJCompanionBuffInfo.get_friend_ship_bonus_percent(this);
        if(guage_bonus > 0)
            guage += (exp * guage_bonus);
        add_friend_ship_guage((int)guage);
    }
    
    private int calculate_total_exp(int exp){
        int total_exp = (int)((exp > 0 ? (exp * CompanionSettings.EXP_SCALE) : exp) + getExp());
        int exp_by_level = ExpTable.getExpByLevel(CompanionSettings.COMPANION_MAX_LEVEL + 1);
        total_exp = Math.min(total_exp, exp_by_level - 1);
        return total_exp;
    }
    
    public void update_exp(int exp){
        if(m_master == null)
            return;
        
        if(exp > 0){
            double bonus_percent = MJCompanionBuffInfo.get_exp_bonus_percent(this);
            if(bonus_percent > 0)
                exp += (exp * bonus_percent);
            exp *= MJCompanionExpPenalty.get_companion_penalty_rate(getLevel());
        }
        
        int update_flag = MJCompanionUpdateFlag.UPDATE_EXP;
        int total_exp = calculate_total_exp(exp);
        if(exp > 0){
            update_flag |= MJCompanionUpdateFlag.UPDATE_FRIEND_SHIP;
            update_friend_ship_by_exp(exp);
            if(!get_is_combo_time() && m_friend_ship_guage >= CompanionSettings.FRIEND_SHIP_MAX_GUAGE){
                update_flag |= MJCompanionUpdateFlag.UPDATE_COMBO_TIME | MJCompanionUpdateFlag.UPDATE_DELAY_REDUCE;
                do_combo_run();
            }
        }
        update_exp(total_exp, update_flag);
    }
    
    public void update_exp(int total_exp, int update_flag){
        setExp(total_exp);
        int before_level = getLevel();
        int next_level = ExpTable.getLevelByExp(total_exp);
        if(next_level > before_level){
            setLevel(next_level);
            int gap = getLevel() - before_level;
            if(gap > 0){
                for(int i = gap - 1; i>=0; --i){
                    addMaxHp(MJRnd.next(m_stat_effect.get_add_min_hp(), m_stat_effect.get_add_max_hp()));
                    addMaxMp(getAbility().getTotalWis() / 2);
                }
                SC_SkillSound.broadcast(this, getId(), 6353);
                SC_ServerMessage.send(m_master, 320, getName());
                update_instance();
                MJCompanionInstanceCache.update_cache(this, true);
                SC_COMPANION_STATUS_NOTI.send(m_master, this, MJCompanionUpdateFlag.UPDATE_ALL | update_flag);
                SC_UPDATE_INVENTORY_NOTI.send_companion_control_item_name(m_master, get_control_object_id(), get_class_info().get_class_npc_name_id(), getLevel());
            }
        }else{
            SC_COMPANION_STATUS_NOTI.send(m_master, this, update_flag);
        }
    }
    public void load_skills(){
        ArrayList<Skill> exercise = new ArrayList<Skill>();
        ArrayList<Skill> non_exercise = new ArrayList<Skill>();
        Selector.exec("select * from companion_instance_skills where object_id=?", new SelectorHandler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, getId());
            }

            @Override
            public void result(ResultSet rs) throws Exception {
                while(rs.next()){
                    Skill sk = Skill.newInstance();
                    sk.set_id(rs.getInt("skill_id"));
                    sk.set_enchant(rs.getInt("enchant"));
                    if(MJCompanionSkillInfo.is_exercise_skill(sk.get_id())){
                        exercise.add(sk);
                    }else{
                        non_exercise.add(sk);
                    }
                }
            }
        });
        set_exercise_skills(exercise);
        set_non_exercise_skills(non_exercise);
    }
    
    private void oblivion_skills(){
        Updator.exec("delete from companion_instance_skills where object_id=?", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, getId());
            }
        });
        m_exercise_skills.clear();
        m_non_exercise_skills.clear();
    }
    
    public void update_skills(){
        final ArrayList<Skill> skills = get_all_skills();
        update_skills(skills);
    }
    
    public void update_skills(ArrayList<Skill> skills){
        Updator.batch("insert into companion_instance_skills set object_id=?, skill_id=?, enchant=? "
                + "on duplicate key update "
                + "enchant=?", 
                new BatchHandler(){
                    @Override
                    public void handle(PreparedStatement pstm, int callNumber) throws Exception {
                        int idx = 0;
                        Skill sk = skills.get(callNumber);
                        pstm.setInt(++idx, getId());
                        pstm.setInt(++idx, sk.get_id());
                        pstm.setInt(++idx, sk.get_enchant());
                        pstm.setInt(++idx, sk.get_enchant());
                    }
                }, skills.size());
    }
    
    public void load_buffs(){
        Selector.exec("select * from companion_instance_buff where object_id=?", new SelectorHandler(){

            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, getId());
            }

            @Override
            public void result(ResultSet rs) throws Exception {
                while(rs.next()){
                    int spell_id = rs.getInt("spell_id");
                    int remain_seconds = rs.getInt("remain_seconds");
                    if(remain_seconds > 0)
                        setSkillEffect(spell_id, remain_seconds * 1000);
                }
            }
        });
    }
    
    public void update_skill_effect(){
        if(m_skill_effect != null){
            getAC().addAc(-m_skill_effect.get_ac());
            getResistance().addMr(-m_skill_effect.get_mr());
        }
        
        MJCompanionSkillEffect buff_effect = MJCompanionSkillEffect.newInstance();
        for(Skill skInfo : get_non_exercise_skills()){
            MJCompanionSkillInfo cskInfo = MJCompanionSkillInfo.get_companion_skills(skInfo.get_id(), skInfo.get_enchant());
            if(cskInfo == null)
                continue;
            
            buff_effect.add_melee_dmg(cskInfo.get_melee_dmg())
            .add_melee_hit(cskInfo.get_melee_hit())
            .add_melee_cri_hit(cskInfo.get_melee_cri_hit())
            .add_ignore_reduction(cskInfo.get_ignore_reduction())
            .add_blood_suck_hit(cskInfo.get_blood_suck_hit())
            .add_blood_suck_heal(cskInfo.get_blood_suck_heal())
            .add_regen_hp(cskInfo.get_regen_hp())
            .add_ac(cskInfo.get_ac())
            .add_mr(cskInfo.get_mr())
            .add_potion_hp(cskInfo.get_potion_hp())
            .add_dmg_reduction(cskInfo.get_dmg_reduction())
            .add_max_hp(cskInfo.get_max_hp())
            .add_spell_dmg_multi(cskInfo.get_spell_dmg_multi())
            .add_spell_hit(cskInfo.get_spell_hit())
            .add_move_delay_reduce(cskInfo.get_move_delay_reduce())
            .add_attack_delay_reduce(cskInfo.get_attack_delay_reduce())
            .add_fire_elemental_dmg(cskInfo.get_fire_elemental_dmg())
            .add_water_elemental_dmg(cskInfo.get_water_elemental_dmg())
            .add_air_elemental_dmg(cskInfo.get_air_elemental_dmg())
            .add_earth_elemental_dmg(cskInfo.get_earth_elemental_dmg())
            .add_light_elemental_dmg(cskInfo.get_light_elemental_dmg())
            .add_combo_dmg_multi(cskInfo.get_combo_dmg_multi())
            .add_spell_dmg_add(cskInfo.get_spell_dmg_add());
        }
        m_skill_effect = buff_effect;
        getAC().addAc(m_skill_effect.get_ac());
        getResistance().addMr(m_skill_effect.get_mr());
    }
    
    public void update_stats(){
        if(m_stat_effect != null){
            getAC().addAc(-m_stat_effect.get_ac());
            getResistance().addMr(-m_stat_effect.get_mr());
        }
        Ability ability = getAbility();
        m_stat_effect = MJCompanionStatEffect.createInstance(ability.getTotalStr(), ability.getTotalCon(), ability.getTotalInt());
        getAC().addAc(m_stat_effect.get_ac());
        getResistance().addMr(m_stat_effect.get_mr());
    }
    
    private void oblivion_buffs(){
        for(int spell_id : L1SkillId.COMPANION_BUFFS){
            if(!hasSkillEffect(spell_id))
                continue;
            
            removeSkillEffect(spell_id);
        }
        Updator.exec("delete from companion_instance_buff where object_id=?", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, getId());
            }
        });
    }
    
    public void update_buffs(){
        ArrayList<Integer[]> store_items = new ArrayList<Integer[]>(2);
        for(int spell_id : L1SkillId.COMPANION_BUFFS){
            if(!hasSkillEffect(spell_id))
                continue;
            
            store_items.add(new Integer[]{ spell_id, getSkillEffectTimeSec(spell_id)});
        }
        if(store_items.size() > 0)
            update_buffs(store_items);
    }
    
    private void update_buffs(final ArrayList<Integer[]> store_items){
        final int object_id = getId();
        Updator.batch("insert into companion_instance_buff set object_id=?, spell_id=?, remain_seconds=? "
                + "on duplicate key update "
                + "remain_seconds=?", 
                new BatchHandler(){
                    @Override
                    public void handle(PreparedStatement pstm, int callNumber) throws Exception {
                        int idx = 0;
                        Integer[] integers = store_items.get(callNumber);
                        pstm.setInt(++idx, object_id);
                        pstm.setInt(++idx, integers[0]);
                        pstm.setInt(++idx, integers[1]);
                        pstm.setInt(++idx, integers[1]);
                    }
                }, store_items.size());
    }
    
    public void update_elixir_use_count(){
        MJCompanionInstanceCache.update_cache(this, true);
        Updator.exec("update companion_instance set elixir_use_count=?, str=?, con=?, intel=? where control_object_id=?", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                Ability ability = getAbility();
                pstm.setInt(1, get_elixir_use_count());
                pstm.setInt(2, ability.getAddedStr());
                pstm.setInt(3, ability.getAddedCon());
                pstm.setInt(4, ability.getAddedInt());
                pstm.setInt(5, get_control_object_id());
            }
        });
    }
    
    public void update_friend_ship_marble(){
        Updator.exec("update companion_instance set friend_ship_marble=? where control_object_id=?", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, get_friend_ship_marble());
                pstm.setInt(2, get_control_object_id());
            }
        });
    }
    
    public void update_name(){
        MJCompanionInstanceCache.update_cache(this, true);
        Updator.exec("update companion_instance set companion_name=?, name_changed_count=? where control_object_id=?", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setString(1, getName());
                pstm.setInt(2, get_name_changed_count());
                pstm.setInt(3, get_control_object_id());
            }
        });
    }
    
    public void update_tier(){
        Updator.exec("update companion_instance set tier=? where control_object_id=?", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, get_tier());
                pstm.setInt(2, get_control_object_id());
            }
        });
    }
    
    public void update_name_changed_count(){
        Updator.exec("update companion_instance set name_changed_count=? where control_object_id=?", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, get_keep_exp());
                pstm.setInt(2, get_name_changed_count());
            }
        });
    }
    
    public void update_keep_exp(){
        Updator.exec("update companion_instance set keep_exp=? where control_object_id=?", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, get_keep_exp());
                pstm.setInt(2, get_control_object_id());
            }
        });
    }
    
    public void update_traning(){
        Updator.exec("update companion_instance set is_traning=? where control_object_id=?", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, get_is_traning() ? 1 : 0);
                pstm.setInt(2, get_control_object_id());
            }
        });
    }
    
    public void update_instance(){
        Updator.exec("insert into companion_instance set object_id=?, control_object_id=?, class_name=?, companion_name=?, friend_ship_guage=?, friend_ship_marble=?, level=?, exp=?, keep_exp=?, current_hp=?, current_mp=?, max_hp=?, max_mp=?, lawful=?, tier=?, str=?, con=?, intel=?, elixir_use_count=?, is_oblivion=?, is_traning=?, name_changed_count=? "
                + "on duplicate key update "
                + "companion_name=?, friend_ship_guage=?, friend_ship_marble=?, level=?, exp=?, keep_exp=?, current_hp=?, current_mp=?, max_hp=?, max_mp=?, lawful=?, tier=?, str=?, con=?, intel=?, elixir_use_count=?, is_oblivion=?, is_traning=?, name_changed_count=?", 
                new Handler(){
                    @Override
                    public void handle(PreparedStatement pstm) throws Exception {
                        MJCompanionClassInfo cInfo = get_class_info();
                        int idx = 0;
                        pstm.setInt(++idx, getId());
                        pstm.setInt(++idx, get_control_object_id());
                        pstm.setString(++idx, cInfo.get_class_name());
                        pstm.setString(++idx, getName());
                        pstm.setInt(++idx, get_friend_ship_guage());
                        pstm.setInt(++idx, get_friend_ship_marble());
                        pstm.setInt(++idx, getLevel());
                        pstm.setInt(++idx, getExp());
                        pstm.setInt(++idx, get_keep_exp());
                        pstm.setInt(++idx, getCurrentHp());
                        pstm.setInt(++idx, getCurrentMp());
                        pstm.setInt(++idx, get_base_max_hp());
                        pstm.setInt(++idx, getMaxMp());
                        pstm.setInt(++idx, getLawful());
                        pstm.setInt(++idx, get_tier());
                        pstm.setInt(++idx, getAbility().getAddedStr());
                        pstm.setInt(++idx, getAbility().getAddedCon());
                        pstm.setInt(++idx, getAbility().getAddedInt());
                        pstm.setInt(++idx, get_elixir_use_count());
                        pstm.setInt(++idx, get_is_oblivion() ? 1 : 0);
                        pstm.setInt(++idx, get_is_traning() ? 1 : 0);
                        pstm.setInt(++idx, get_name_changed_count());
                        
                        pstm.setString(++idx, getName());
                        pstm.setInt(++idx, get_friend_ship_guage());
                        pstm.setInt(++idx, get_friend_ship_marble());
                        pstm.setInt(++idx, getLevel());
                        pstm.setInt(++idx, getExp());
                        pstm.setInt(++idx, get_keep_exp());
                        pstm.setInt(++idx, getCurrentHp());
                        pstm.setInt(++idx, getCurrentMp());
                        pstm.setInt(++idx, get_base_max_hp());
                        pstm.setInt(++idx, getMaxMp());
                        pstm.setInt(++idx, getLawful());
                        pstm.setInt(++idx, get_tier());
                        pstm.setInt(++idx, getAbility().getAddedStr());
                        pstm.setInt(++idx, getAbility().getAddedCon());
                        pstm.setInt(++idx, getAbility().getAddedInt());
                        pstm.setInt(++idx, get_elixir_use_count());
                        pstm.setInt(++idx, get_is_oblivion() ? 1 : 0);
                        pstm.setInt(++idx, get_is_traning() ? 1 : 0);
                        pstm.setInt(++idx, get_name_changed_count());
                    }
                });
    }
}