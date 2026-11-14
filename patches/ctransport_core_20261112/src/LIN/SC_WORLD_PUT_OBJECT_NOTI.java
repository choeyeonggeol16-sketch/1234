package LIN;

import l1j.server.DogFight.Instance.MJDogFightInstance;
import l1j.server.MJ3SEx.EActionCodes;
import l1j.server.MJTemplate.L1Instance.MJEffectTriggerInstance;
import l1j.server.server.datatables.MJNpcMarkTable;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1FieldObjectInstance;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SignboardInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.utils.MJCommons;


/**
 * SC_WORLD_PUT_OBJECT_NOTI (0x0077) — 오브젝트 스폰 통보
 * MJ 원본과 필드번호·조건부 방출(has bit) 완전 동일.
 * 스폰 핫패스: encode() 1회 → Raw 공유 권장.
 */
public final class SC_WORLD_PUT_OBJECT_NOTI extends ProtoPacket {

    private long _bit;

    private int _point;
    private int _objectnumber;
    private int _objectsprite;
    private int _action;
    private int _direction;
    private int _lightRadius;
    private int _objectcount;
    private int _alignment;
    private String _desc;
    private String _title;
    private int _speeddata;
    private int _emotion;
    private int _drunken;
    private boolean _isghost;
    private boolean _isparalyzed;
    private boolean _isuser;
    private boolean _isinvisible;
    private boolean _ispoisoned;
    private int _emblemid;
    private String _pledgename;
    private String _mastername;
    private int _altitude;
    private int _hitratio;
    private int _safelevel;
    private String _shoptitle;
    private int _weaponsprite;
    private int _couplestate;
    private int _boundarylevelindex;
    private int _weakelemental;
    private int _manaratio;
    private int _botindex;
    private int _homeserverno;
    private int _team_id;
    private int _dialog_radius;
    private int _speed_value_flag;
    private int _second_speed_type;
    private int _explosion_remain_time_ms;
    private boolean _proclamation_siege_mark;
    private int _npc_class_id;
    private LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion _companion;

    // ────────────── 표시용 헬퍼 ──────────────

    private static int get_presentation_npc_emblem(L1NpcInstance npc){
        if (npc.getNpcId() == 6200008) {
            for (L1Clan checkClan : L1World.getInstance().getAllClans()) {
                if (checkClan.getCastleId() == 4) {
                    return checkClan.getEmblemId();
                }
            }
        }
        return 0;
    }

    private static String get_presentation_npc_title(L1NpcInstance npc){
        if((npc instanceof L1FieldObjectInstance)){
            L1NpcTalkData talkdata = NPCTalkDataTable.getInstance().getTemplate(npc.getNpcTemplate().get_npcId());
            return talkdata != null ? talkdata.getNormalAction() : "";
        }
        if ((npc instanceof L1SignboardInstance))
            return npc.getNameId();
        if ((npc.getTitle() != null) && (!npc.getTitle().isEmpty()))
            return npc.getTitle();
        return null;
    }

    private static String get_presentation_npc_desc(L1NpcInstance npc){
        if(npc.getNpcId() == 6200008){
            String clan_name = ((L1MerchantInstance) npc).getClanname();
            return MJCommons.isNullOrEmpty(clan_name) ? null : clan_name;
        }
        else if((npc instanceof L1SignboardInstance))
            return null;
        else if((npc.getNameId() != null) && (!npc.getNameId().isEmpty()))
            return npc.getNameId();
        return null;
    }

    private static int get_presentation_npc_action(L1NpcInstance npc){
        int npcid = npc.getNpcId();
        int sprid = npc.getCurrentSpriteId();
        if(npc.getShopName() != null)
            return EActionCodes.act_shop.toInt();

        if(sprid == 110) return 24;
        if(sprid == 111) return 4;
        if(sprid == 51 || npcid == 60519) return 24;
        if(sprid == 816) return 20;
        if(npcid == 203053) return 4;
        if(npcid == 203052) return 20;

        if(npc.getNpcTemplate().is_doppel()){
            if(sprid != 31 || sprid == 727 || sprid == 985
            || sprid == 98 || sprid == 6632 || sprid == 6634 || sprid == 6636
            || sprid == 6638)
            return 4;
        }
        return npc.getStatus();
    }

    // ────────────── 팩토리 (MJ 시그니처 유지) ──────────────

    public static LIN.SC_WORLD_PUT_OBJECT_NOTI newInstance(MJEffectTriggerInstance trigger){
        LIN.SC_WORLD_PUT_OBJECT_NOTI noti = new LIN.SC_WORLD_PUT_OBJECT_NOTI();
        noti.set_point(trigger.get_long_location_reverse());
        noti.set_objectnumber(trigger.getId());
        noti.set_objectsprite(trigger.get_sprite());
        noti.set_direction(1);
        noti.set_action(trigger.get_action());
        noti.set_lightRadius(0);
        noti.set_objectcount(0);
        noti.set_alignment(0);
        noti.set_desc("");
        noti.set_title("");
        noti.set_speeddata(1);
        noti.set_emotion(1);
        noti.set_drunken(0);
        noti.set_isghost(false);
        noti.set_isparalyzed(false);
        noti.set_isuser(false);
        noti.set_isinvisible(false);
        noti.set_ispoisoned(false);
        noti.set_emblemid(0);
        noti.set_pledgename("");
        noti.set_mastername("");
        noti.set_altitude(0);
        noti.set_hitratio(-1);
        noti.set_safelevel(1);
        noti.set_weaponsprite(-1);
        noti.set_couplestate(0);
        noti.set_boundarylevelindex(1);
        noti.set_speed_value_flag(1);
        noti.set_second_speed_type(1);
        return noti;
    }

    /** 원본 메서드명(오타 포함) 유지 — 호출부 호환용 */
    public static LIN.SC_WORLD_PUT_OBJECT_NOTI new_namechat_isntance(L1NpcInstance npc, String name, boolean is_ghost){
        LIN.SC_WORLD_PUT_OBJECT_NOTI noti = new LIN.SC_WORLD_PUT_OBJECT_NOTI();
        noti.set_point(npc.getLongLocationReverse());
        noti.set_objectnumber(npc.getId());
        noti.set_objectsprite(npc.getCurrentSpriteId());
        noti.set_action(npc.getStatus());
        noti.set_direction(npc.getHeading());
        noti.set_lightRadius(npc.getNpcTemplate().getLightSize());
        noti.set_objectcount(1);
        noti.set_alignment(npc.getTempLawful());
        noti.set_desc(name);
        noti.set_title("");
        noti.set_speeddata(1);
        noti.set_emotion(1);
        noti.set_drunken(0);
        noti.set_isghost(is_ghost);
        noti.set_isparalyzed(false);
        noti.set_isuser(false);
        noti.set_isinvisible(false);
        noti.set_ispoisoned(false);
        noti.set_emblemid(0);
        noti.set_pledgename(null);
        noti.set_mastername("");
        noti.set_altitude(5);
        noti.set_hitratio(-1);
        noti.set_safelevel(1);
        noti.set_weaponsprite(1);
        noti.set_couplestate(0);
        noti.set_boundarylevelindex(0);
        return noti;
    }

    public static LIN.SC_WORLD_PUT_OBJECT_NOTI newInstance(MJDogFightInstance dogfight){
        LIN.SC_WORLD_PUT_OBJECT_NOTI noti = new LIN.SC_WORLD_PUT_OBJECT_NOTI();
        noti.set_point(dogfight.getLongLocationReverse());
        noti.set_objectnumber(dogfight.getId());
        noti.set_objectsprite(dogfight.getCurrentSpriteId());
        noti.set_action(dogfight.getStatus());
        noti.set_direction(dogfight.getHeading());
        noti.set_lightRadius(dogfight.getNpcTemplate().getLightSize());
        noti.set_objectcount(1);
        noti.set_alignment(dogfight.getTempLawful());
        noti.set_desc(dogfight.getName());
        noti.set_title("");
        noti.set_speeddata(1);
        noti.set_emotion(1);
        noti.set_drunken(0);
        noti.set_isghost(false);
        noti.set_isparalyzed(false);
        noti.set_isuser(false);
        noti.set_isinvisible(false);
        noti.set_ispoisoned(false);
        noti.set_emblemid(0);
        noti.set_pledgename(null);
        noti.set_mastername("");
        noti.set_altitude(5);
        noti.set_hitratio((int)dogfight.getCurrentHpPercent());
        noti.set_safelevel(dogfight.getLevel());
        noti.set_weaponsprite(1);
        noti.set_couplestate(0);
        noti.set_boundarylevelindex(0);
        if(dogfight.get_explosion_time_mills() > 0)
            noti.set_explosion_remain_time_ms(dogfight.get_explosion_time_mills());
        return noti;
    }

    public static LIN.SC_WORLD_PUT_OBJECT_NOTI newInstance(L1NpcInstance npc){
        LIN.SC_WORLD_PUT_OBJECT_NOTI noti = new LIN.SC_WORLD_PUT_OBJECT_NOTI();
        boolean is_companion = npc instanceof L1CompanionInstance;
        L1CompanionInstance companion_instance = is_companion ? (L1CompanionInstance)npc : null;

        noti.set_point(npc.getLongLocationReverse());
        noti.set_objectnumber(npc.getId());
        noti.set_objectsprite(npc.getCurrentSpriteId());
        noti.set_action(get_presentation_npc_action(npc));
        noti.set_direction(npc.getHeading());
        noti.set_lightRadius(npc.getNpcTemplate().getLightSize());
        noti.set_objectcount(1);
        noti.set_alignment(npc.getTempLawful());
        noti.set_desc(is_companion ? companion_instance.getName() : get_presentation_npc_desc(npc));
        noti.set_title(get_presentation_npc_title(npc));
        noti.set_speeddata(npc.getMoveSpeed());
        noti.set_emotion(npc.getBraveSpeed());
        noti.set_drunken(0);
        noti.set_isghost(false);
        noti.set_isparalyzed(npc.getParalysis() != null);
        noti.set_isuser(npc.getShopName() != null || !npc.isPassObject());
        noti.set_isinvisible(npc.isInvisble());
        noti.set_ispoisoned(npc.getPoison() != null);
        noti.set_emblemid(get_presentation_npc_emblem(npc));
        noti.set_pledgename(null);
        if(!is_companion)
            noti.set_mastername(npc.getMaster() != null ? npc.getMaster().getName() : null);
        else
            noti.set_mastername(companion_instance.get_master_name());
        noti.set_altitude(5);
        noti.set_hitratio(-1);
        noti.set_safelevel(npc.getLevel());
        if(npc.getShopName() != null)
            noti.set_shoptitle(npc.getShopName());
        noti.set_weaponsprite(1);
        noti.set_couplestate(0);
        noti.set_boundarylevelindex(0);
        noti.set_weakelemental(npc.getNpcTemplate().get_weakAttr());
        noti.set_manaratio((int)npc.getCurrentMpPercent());
        noti.set_botindex(0);
        noti.set_team_id(MJNpcMarkTable.getInstance().get(npc.getNpcId()));
        noti.set_dialog_radius(0);
        noti.set_speed_value_flag(0);
        noti.set_second_speed_type(0);
        if(npc.getExplosionTime() != 0)
            noti.set_explosion_remain_time_ms((int)npc.getExplosionTime());
        if(is_companion){
            noti.set_npc_class_id(companion_instance.get_class_info().get_class_id());
            LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion companion = new LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion();
            companion.set_attack_delay_reduce(companion_instance.get_attackdelay_reduce());
            companion.set_move_delay_reduce(companion_instance.get_movedelay_reduce());
            companion.set_pvp_dmg_ratio(companion_instance.get_pvp_dmg_ratio());
            noti.set_companion(companion);
        } else if (npc instanceof L1MonsterInstance && npc.getNpcTemplate().get_npc_class_id() > 0) {
            noti.set_npc_class_id(npc.getNpcTemplate().get_npc_class_id());
        }
        return noti;
    }
    
    /**
     * 마법인형 전용 팩토리
     */
    public static LIN.SC_WORLD_PUT_OBJECT_NOTI newInstance(L1DollInstance doll){
        LIN.SC_WORLD_PUT_OBJECT_NOTI noti = new LIN.SC_WORLD_PUT_OBJECT_NOTI();
        if (doll == null) return noti;

        int id = 0, spriteId = 0, status = 0, heading = 0, moveSpeed = 0, level = 1;
        String nameId = "", title = "", masterName = "";

        try {
            id = doll.getId();
            spriteId = doll.getCurrentSpriteId();
            status = doll.getStatus();
            heading = doll.getHeading();
            moveSpeed = doll.getMoveSpeed();
            level = doll.getLevel();

            if (doll.getNameId() != null) nameId = doll.getNameId();
            if (doll.getTitle() != null) title = doll.getTitle();
            if (doll.getMaster() != null && doll.getMaster().getName() != null)
                masterName = doll.getMaster().getName();
        } catch (Exception ignored) {}

        noti.set_point(doll.getLongLocationReverse());  
        noti.set_objectnumber(id);
        noti.set_objectsprite(spriteId);
        noti.set_action(status);
        noti.set_direction(heading);
        noti.set_lightRadius(0);
        noti.set_objectcount(1);
        noti.set_alignment(0);
        noti.set_desc(nameId);
        noti.set_title(title);
        noti.set_speeddata(moveSpeed);
        noti.set_emotion(0);
        noti.set_drunken(0);
        noti.set_isghost(false);
        noti.set_isparalyzed(false);
        noti.set_isuser(false);
        noti.set_isinvisible(false);
        noti.set_ispoisoned(false);
        noti.set_emblemid(0);
        noti.set_pledgename(null);
        noti.set_mastername(masterName);
        noti.set_altitude(5);
        noti.set_hitratio(-1);
        noti.set_safelevel(level);
        noti.set_weaponsprite(1);
        noti.set_couplestate(0);
        noti.set_boundarylevelindex(0);
        return noti;
    }
   /* **
    * 서먼(소환수) 전용 팩토리
    * 주인 본인에게는 HP/MP 게이지(hitratio/manaratio) 노출, 타인에게는 미노출
    */
   public static LIN.SC_WORLD_PUT_OBJECT_NOTI newInstance(L1SummonInstance summon, L1PcInstance viewer){
       LIN.SC_WORLD_PUT_OBJECT_NOTI noti = new LIN.SC_WORLD_PUT_OBJECT_NOTI();
       if (summon == null) return noti;

       int id = 0, spriteId = 0, status = 0, heading = 0, lightSize = 0, moveSpeed = 0, level = 1;
       int hpPercent = -1, mpPercent = -1;
       String nameId = "", title = "", masterName = "";

       try {
           id = summon.getId();
           spriteId = summon.getCurrentSpriteId();
           status = summon.getStatus();
           heading = summon.getHeading();
           moveSpeed = summon.getMoveSpeed();
           level = summon.getLevel();

           nameId = summon.getNameId() != null ? summon.getNameId() : "";
           title = summon.getTitle() != null ? summon.getTitle() : "";

           if (summon.getLight() != null) {
               lightSize = summon.getLight().getChaLightSize();
           }

           boolean isOwner = false;
           if (summon.isExsistMaster() && summon.getMaster() != null) {
               masterName = summon.getMaster().getName() != null
                       ? summon.getMaster().getName() : "";
               if (viewer != null && summon.getMaster().getId() == viewer.getId()) {
                   isOwner = true;
               }
           }
           

           if (isOwner) {
               int maxHp = summon.getMaxHp();
               hpPercent = maxHp > 0
                       ? Math.max(0, Math.min(100, 100 * summon.getCurrentHp() / maxHp))
                       : 100;
               int maxMp = summon.getMaxMp();
               mpPercent = maxMp > 0
                       ? Math.max(0, Math.min(100, 100 * summon.getCurrentMp() / maxMp))
                       : 100;
           }
       } catch (Exception ignored) {}

       noti.set_point(summon.getLongLocationReverse());
       noti.set_objectnumber(id);
       noti.set_objectsprite(spriteId);
       noti.set_action(status);
       noti.set_direction(heading);
       noti.set_lightRadius(lightSize);
       noti.set_objectcount(1);
       noti.set_alignment(0);
       noti.set_desc(nameId);
       noti.set_title(title);
       noti.set_speeddata(moveSpeed);
       noti.set_emotion(0);
       noti.set_drunken(0);
       noti.set_isghost(false);
       noti.set_isparalyzed(false);
       noti.set_isuser(false);
       noti.set_isinvisible(false);
       noti.set_ispoisoned(summon.getPoison() != null);
       noti.set_emblemid(0);
       noti.set_pledgename(null);
       noti.set_mastername(masterName);
       noti.set_altitude(5);
       noti.set_hitratio(hpPercent);
       noti.set_safelevel(level);
       noti.set_weaponsprite(1);
       noti.set_couplestate(0);
       noti.set_boundarylevelindex(0);
       noti.set_manaratio(mpPercent);
       return noti;
   }

    public static LIN.SC_WORLD_PUT_OBJECT_NOTI newInstance(){
        return new LIN.SC_WORLD_PUT_OBJECT_NOTI();
    }

    private SC_WORLD_PUT_OBJECT_NOTI() {}

    // ────────────── setter/has  ──────────────

    public void set_point(int v){ _bit |= 0x1L; _point = v; }
    public boolean has_point(){ return (_bit & 0x1L) != 0L; }
    public void set_objectnumber(int v){ _bit |= 0x2L; _objectnumber = v; }
    public boolean has_objectnumber(){ return (_bit & 0x2L) != 0L; }
    public void set_objectsprite(int v){ _bit |= 0x4L; _objectsprite = v; }
    public boolean has_objectsprite(){ return (_bit & 0x4L) != 0L; }
    public void set_action(int v){ _bit |= 0x8L; _action = v; }
    public boolean has_action(){ return (_bit & 0x8L) != 0L; }
    public void set_direction(int v){ _bit |= 0x10L; _direction = v; }
    public boolean has_direction(){ return (_bit & 0x10L) != 0L; }
    public void set_lightRadius(int v){ _bit |= 0x20L; _lightRadius = v; }
    public boolean has_lightRadius(){ return (_bit & 0x20L) != 0L; }
    public void set_objectcount(int v){ _bit |= 0x40L; _objectcount = v; }
    public boolean has_objectcount(){ return (_bit & 0x40L) != 0L; }
    public void set_alignment(int v){ _bit |= 0x80L; _alignment = v; }
    public boolean has_alignment(){ return (_bit & 0x80L) != 0L; }
    public void set_desc(String v){ _bit |= 0x100L; _desc = v; }
    public boolean has_desc(){ return (_bit & 0x100L) != 0L; }
    public void set_title(String v){ _bit |= 0x200L; _title = v; }
    public boolean has_title(){ return (_bit & 0x200L) != 0L; }
    public void set_speeddata(int v){ _bit |= 0x400L; _speeddata = v; }
    public boolean has_speeddata(){ return (_bit & 0x400L) != 0L; }
    public void set_emotion(int v){ _bit |= 0x800L; _emotion = v; }
    public boolean has_emotion(){ return (_bit & 0x800L) != 0L; }
    public void set_drunken(int v){ _bit |= 0x1000L; _drunken = v; }
    public boolean has_drunken(){ return (_bit & 0x1000L) != 0L; }
    public void set_isghost(boolean v){ _bit |= 0x2000L; _isghost = v; }
    public boolean has_isghost(){ return (_bit & 0x2000L) != 0L; }
    public void set_isparalyzed(boolean v){ _bit |= 0x4000L; _isparalyzed = v; }
    public boolean has_isparalyzed(){ return (_bit & 0x4000L) != 0L; }
    public void set_isuser(boolean v){ _bit |= 0x8000L; _isuser = v; }
    public boolean has_isuser(){ return (_bit & 0x8000L) != 0L; }
    public void set_isinvisible(boolean v){ _bit |= 0x10000L; _isinvisible = v; }
    public boolean has_isinvisible(){ return (_bit & 0x10000L) != 0L; }
    public void set_ispoisoned(boolean v){ _bit |= 0x20000L; _ispoisoned = v; }
    public boolean has_ispoisoned(){ return (_bit & 0x20000L) != 0L; }
    public void set_emblemid(int v){ _bit |= 0x40000L; _emblemid = v; }
    public boolean has_emblemid(){ return (_bit & 0x40000L) != 0L; }
    public void set_pledgename(String v){ _bit |= 0x80000L; _pledgename = v; }
    public boolean has_pledgename(){ return (_bit & 0x80000L) != 0L; }
    public void set_mastername(String v){ _bit |= 0x100000L; _mastername = v; }
    public boolean has_mastername(){ return (_bit & 0x100000L) != 0L; }
    public void set_altitude(int v){ _bit |= 0x200000L; _altitude = v; }
    public boolean has_altitude(){ return (_bit & 0x200000L) != 0L; }
    public void set_hitratio(int v){ _bit |= 0x400000L; _hitratio = v; }
    public boolean has_hitratio(){ return (_bit & 0x400000L) != 0L; }
    public void set_safelevel(int v){ _bit |= 0x800000L; _safelevel = v; }
    public boolean has_safelevel(){ return (_bit & 0x800000L) != 0L; }
    public void set_shoptitle(String v){ _bit |= 0x1000000L; _shoptitle = v; }
    public boolean has_shoptitle(){ return (_bit & 0x1000000L) != 0L; }
    public void set_weaponsprite(int v){ _bit |= 0x2000000L; _weaponsprite = v; }
    public boolean has_weaponsprite(){ return (_bit & 0x2000000L) != 0L; }
    public void set_couplestate(int v){ _bit |= 0x4000000L; _couplestate = v; }
    public boolean has_couplestate(){ return (_bit & 0x4000000L) != 0L; }
    public void set_boundarylevelindex(int v){ _bit |= 0x8000000L; _boundarylevelindex = v; }
    public boolean has_boundarylevelindex(){ return (_bit & 0x8000000L) != 0L; }
    public void set_weakelemental(int v){ _bit |= 0x10000000L; _weakelemental = v; }
    public boolean has_weakelemental(){ return (_bit & 0x10000000L) != 0L; }
    public void set_manaratio(int v){ _bit |= 0x20000000L; _manaratio = v; }
    public boolean has_manaratio(){ return (_bit & 0x20000000L) != 0L; }
    public void set_botindex(int v){ _bit |= 0x40000000L; _botindex = v; }
    public boolean has_botindex(){ return (_bit & 0x40000000L) != 0L; }
    public void set_homeserverno(int v){ _bit |= 0x80000000L; _homeserverno = v; }
    public boolean has_homeserverno(){ return (_bit & 0x80000000L) != 0L; }
    public void set_team_id(int v){ _bit |= 0x100000000L; _team_id = v; }
    public boolean has_team_id(){ return (_bit & 0x100000000L) != 0L; }
    public void set_dialog_radius(int v){ _bit |= 0x200000000L; _dialog_radius = v; }
    public boolean has_dialog_radius(){ return (_bit & 0x200000000L) != 0L; }
    public void set_speed_value_flag(int v){ _bit |= 0x400000000L; _speed_value_flag = v; }
    public boolean has_speed_value_flag(){ return (_bit & 0x400000000L) != 0L; }
    public void set_second_speed_type(int v){ _bit |= 0x800000000L; _second_speed_type = v; }
    public boolean has_second_speed_type(){ return (_bit & 0x800000000L) != 0L; }
    public void set_explosion_remain_time_ms(int v){ _bit |= 0x1000000000L; _explosion_remain_time_ms = v; }
    public boolean has_explosion_remain_time_ms(){ return (_bit & 0x1000000000L) != 0L; }
    public void set_proclamation_siege_mark(boolean v){ _bit |= 0x2000000000L; _proclamation_siege_mark = v; }
    public boolean has_proclamation_siege_mark(){ return (_bit & 0x2000000000L) != 0L; }
    public void set_npc_class_id(int v){ _bit |= 0x4000000000L; _npc_class_id = v; }
    public boolean has_npc_class_id(){ return (_bit & 0x4000000000L) != 0L; }
    public void set_companion(LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion v){ _bit |= 0x8000000000L; _companion = v; }
    public boolean has_companion(){ return (_bit & 0x8000000000L) != 0L; }

    // ────────────── 직렬화 ──────────────

    @Override
    public int msgId() { return ProtoMsg.SC_WORLD_PUT_OBJECT_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        if (has_point())					w.uint32(1, _point);
        if (has_objectnumber())				w.uint32(2, _objectnumber);
        if (has_objectsprite())				w.int32(3, _objectsprite);
        if (has_action())					w.int32(4, _action);
        if (has_direction())				w.int32(5, _direction);
        if (has_lightRadius())				w.int32(6, _lightRadius);
        if (has_objectcount())				w.int32(7, _objectcount);
        if (has_alignment())				w.int32(8, _alignment);
        if (has_desc())						w.string(9, _desc);
        if (has_title())					w.string(10, _title);
        if (has_speeddata())				w.int32(11, _speeddata);
        if (has_emotion())					w.int32(12, _emotion);
        if (has_drunken())					w.int32(13, _drunken);
        if (has_isghost())					w.bool(14, _isghost);
        if (has_isparalyzed())				w.bool(15, _isparalyzed);
        if (has_isuser())					w.bool(16, _isuser);
        if (has_isinvisible())				w.bool(17, _isinvisible);
        if (has_ispoisoned())				w.bool(18, _ispoisoned);
        if (has_emblemid())					w.uint32(19, _emblemid);
        if (has_pledgename())				w.string(20, _pledgename);
        if (has_mastername())				w.string(21, _mastername);
        if (has_altitude())					w.int32(22, _altitude);
        if (has_hitratio())					w.int32(23, _hitratio);
        if (has_safelevel())				w.int32(24, _safelevel);
        if (has_shoptitle())				w.string(25, _shoptitle);
        if (has_weaponsprite())				w.int32(26, _weaponsprite);
        if (has_couplestate())				w.int32(27, _couplestate);
        if (has_boundarylevelindex())		w.int32(28, _boundarylevelindex);
        if (has_weakelemental())			w.int32(29, _weakelemental);
        if (has_manaratio())				w.int32(30, _manaratio);
        if (has_botindex())					w.uint32(31, _botindex);
        if (has_homeserverno())				w.int32(32, _homeserverno);
        if (has_team_id())					w.uint32(33, _team_id);
        if (has_dialog_radius())			w.uint32(34, _dialog_radius);
        if (has_speed_value_flag())			w.int32(35, _speed_value_flag);
        if (has_second_speed_type())		w.int32(36, _second_speed_type);
        if (has_explosion_remain_time_ms())	w.uint32(37, _explosion_remain_time_ms);
        if (has_proclamation_siege_mark())	w.bool(38, _proclamation_siege_mark);
        if (has_npc_class_id())				w.int32(39, _npc_class_id);
        if (has_companion()) {
            w.msg(40);
            _companion.writeTo(w);
            w.end();
        }
    }

    // ────────────── Companion 서브메시지 (field 40) ──────────────

    public static final class Companion {
        private double _move_delay_reduce;
        private double _attack_delay_reduce;
        private int _pvp_dmg_ratio;

        public LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion set_move_delay_reduce(double v){ _move_delay_reduce = v; return this; }
        public LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion set_attack_delay_reduce(double v){ _attack_delay_reduce = v; return this; }
        public LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion set_pvp_dmg_ratio(int v){ _pvp_dmg_ratio = v; return this; }

        void writeTo(ProtoWriter w){
            w.doubleV(1, _move_delay_reduce);		// fixed64
            w.doubleV(2, _attack_delay_reduce);		// fixed64
            w.uint32(3, _pvp_dmg_ratio);
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(
            ProtoWriter w, long _bit, int _point, int _objectnumber, int _objectsprite, int _action, int _direction,
            int _lightRadius, int _objectcount, int _alignment, String _desc, String _title, int _speeddata, int _emotion,
            int _drunken, boolean _isghost, boolean _isparalyzed, boolean _isuser, boolean _isinvisible,
            boolean _ispoisoned, int _emblemid, String _pledgename, String _mastername, int _altitude, int _hitratio,
            int _safelevel, String _shoptitle, int _weaponsprite, int _couplestate, int _boundarylevelindex,
            int _weakelemental, int _manaratio, int _botindex, int _homeserverno, int _team_id, int _dialog_radius,
            int _speed_value_flag, int _second_speed_type, int _explosion_remain_time_ms, boolean _proclamation_siege_mark,
            int _npc_class_id, LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion _companion) {
        if (((_bit & 1L) != 0L)) w.uint32(1, _point);
            if (((_bit & 2L) != 0L)) w.uint32(2, _objectnumber);
            if (((_bit & 4L) != 0L)) w.int32(3, _objectsprite);
            if (((_bit & 8L) != 0L)) w.int32(4, _action);
            if (((_bit & 16L) != 0L)) w.int32(5, _direction);
            if (((_bit & 32L) != 0L)) w.int32(6, _lightRadius);
            if (((_bit & 64L) != 0L)) w.int32(7, _objectcount);
            if (((_bit & 128L) != 0L)) w.int32(8, _alignment);
            if (((_bit & 256L) != 0L)) w.string(9, _desc);
            if (((_bit & 512L) != 0L)) w.string(10, _title);
            if (((_bit & 1024L) != 0L)) w.int32(11, _speeddata);
            if (((_bit & 2048L) != 0L)) w.int32(12, _emotion);
            if (((_bit & 4096L) != 0L)) w.int32(13, _drunken);
            if (((_bit & 8192L) != 0L)) w.bool(14, _isghost);
            if (((_bit & 16384L) != 0L)) w.bool(15, _isparalyzed);
            if (((_bit & 32768L) != 0L)) w.bool(16, _isuser);
            if (((_bit & 65536L) != 0L)) w.bool(17, _isinvisible);
            if (((_bit & 131072L) != 0L)) w.bool(18, _ispoisoned);
            if (((_bit & 262144L) != 0L)) w.uint32(19, _emblemid);
            if (((_bit & 524288L) != 0L)) w.string(20, _pledgename);
            if (((_bit & 1048576L) != 0L)) w.string(21, _mastername);
            if (((_bit & 2097152L) != 0L)) w.int32(22, _altitude);
            if (((_bit & 4194304L) != 0L)) w.int32(23, _hitratio);
            if (((_bit & 8388608L) != 0L)) w.int32(24, _safelevel);
            if (((_bit & 16777216L) != 0L)) w.string(25, _shoptitle);
            if (((_bit & 33554432L) != 0L)) w.int32(26, _weaponsprite);
            if (((_bit & 67108864L) != 0L)) w.int32(27, _couplestate);
            if (((_bit & 134217728L) != 0L)) w.int32(28, _boundarylevelindex);
            if (((_bit & 268435456L) != 0L)) w.int32(29, _weakelemental);
            if (((_bit & 536870912L) != 0L)) w.int32(30, _manaratio);
            if (((_bit & 1073741824L) != 0L)) w.uint32(31, _botindex);
            if (((_bit & 2147483648L) != 0L)) w.int32(32, _homeserverno);
            if (((_bit & 4294967296L) != 0L)) w.uint32(33, _team_id);
            if (((_bit & 8589934592L) != 0L)) w.uint32(34, _dialog_radius);
            if (((_bit & 17179869184L) != 0L)) w.int32(35, _speed_value_flag);
            if (((_bit & 34359738368L) != 0L)) w.int32(36, _second_speed_type);
            if (((_bit & 68719476736L) != 0L)) w.uint32(37, _explosion_remain_time_ms);
            if (((_bit & 137438953472L) != 0L)) w.bool(38, _proclamation_siege_mark);
            if (((_bit & 274877906944L) != 0L)) w.int32(39, _npc_class_id);
            if (((_bit & 549755813888L) != 0L)) {
                w.msg(40);
                _companion.writeTo(w);
                w.end();
            }
    }

    public static boolean sendFields(
            L1PcInstance recipient, long _bit, int _point, int _objectnumber, int _objectsprite, int _action,
            int _direction, int _lightRadius, int _objectcount, int _alignment, String _desc, String _title, int _speeddata,
            int _emotion, int _drunken, boolean _isghost, boolean _isparalyzed, boolean _isuser, boolean _isinvisible,
            boolean _ispoisoned, int _emblemid, String _pledgename, String _mastername, int _altitude, int _hitratio,
            int _safelevel, String _shoptitle, int _weaponsprite, int _couplestate, int _boundarylevelindex,
            int _weakelemental, int _manaratio, int _botindex, int _homeserverno, int _team_id, int _dialog_radius,
            int _speed_value_flag, int _second_speed_type, int _explosion_remain_time_ms, boolean _proclamation_siege_mark,
            int _npc_class_id, LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion _companion) {
        return sendFields(
                recipient == null ? null : recipient.getNetConnection(), _bit, _point, _objectnumber, _objectsprite,
                _action, _direction, _lightRadius, _objectcount, _alignment, _desc, _title, _speeddata, _emotion, _drunken,
                _isghost, _isparalyzed, _isuser, _isinvisible, _ispoisoned, _emblemid, _pledgename, _mastername, _altitude,
                _hitratio, _safelevel, _shoptitle, _weaponsprite, _couplestate, _boundarylevelindex, _weakelemental,
                _manaratio, _botindex, _homeserverno, _team_id, _dialog_radius, _speed_value_flag, _second_speed_type,
                _explosion_remain_time_ms, _proclamation_siege_mark, _npc_class_id, _companion);
    }

    public static boolean sendFields(
            CIOClient recipient, long _bit, int _point, int _objectnumber, int _objectsprite, int _action, int _direction,
            int _lightRadius, int _objectcount, int _alignment, String _desc, String _title, int _speeddata, int _emotion,
            int _drunken, boolean _isghost, boolean _isparalyzed, boolean _isuser, boolean _isinvisible,
            boolean _ispoisoned, int _emblemid, String _pledgename, String _mastername, int _altitude, int _hitratio,
            int _safelevel, String _shoptitle, int _weaponsprite, int _couplestate, int _boundarylevelindex,
            int _weakelemental, int _manaratio, int _botindex, int _homeserverno, int _team_id, int _dialog_radius,
            int _speed_value_flag, int _second_speed_type, int _explosion_remain_time_ms, boolean _proclamation_siege_mark,
            int _npc_class_id, LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion _companion) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_WORLD_PUT_OBJECT_NOTI, Proto.capacityBound(1024L + (_desc == null ? 0L : 2L * _desc.length()) + (_title == null ? 0L : 2L * _title.length()) + (_pledgename == null ? 0L : 2L * _pledgename.length()) + (_mastername == null ? 0L : 2L * _mastername.length()) + (_shoptitle == null ? 0L : 2L * _shoptitle.length()) + 64L));
        if (writer == null) return false;
        try {
            writeFields(
                    writer, _bit, _point, _objectnumber, _objectsprite, _action, _direction, _lightRadius, _objectcount,
                    _alignment, _desc, _title, _speeddata, _emotion, _drunken, _isghost, _isparalyzed, _isuser,
                    _isinvisible, _ispoisoned, _emblemid, _pledgename, _mastername, _altitude, _hitratio, _safelevel,
                    _shoptitle, _weaponsprite, _couplestate, _boundarylevelindex, _weakelemental, _manaratio, _botindex,
                    _homeserverno, _team_id, _dialog_radius, _speed_value_flag, _second_speed_type,
                    _explosion_remain_time_ms, _proclamation_siege_mark, _npc_class_id, _companion);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(
            long _bit, int _point, int _objectnumber, int _objectsprite, int _action, int _direction, int _lightRadius,
            int _objectcount, int _alignment, String _desc, String _title, int _speeddata, int _emotion, int _drunken,
            boolean _isghost, boolean _isparalyzed, boolean _isuser, boolean _isinvisible, boolean _ispoisoned,
            int _emblemid, String _pledgename, String _mastername, int _altitude, int _hitratio, int _safelevel,
            String _shoptitle, int _weaponsprite, int _couplestate, int _boundarylevelindex, int _weakelemental,
            int _manaratio, int _botindex, int _homeserverno, int _team_id, int _dialog_radius, int _speed_value_flag,
            int _second_speed_type, int _explosion_remain_time_ms, boolean _proclamation_siege_mark, int _npc_class_id,
            LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion _companion) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(
                writer, _bit, _point, _objectnumber, _objectsprite, _action, _direction, _lightRadius, _objectcount,
                _alignment, _desc, _title, _speeddata, _emotion, _drunken, _isghost, _isparalyzed, _isuser, _isinvisible,
                _ispoisoned, _emblemid, _pledgename, _mastername, _altitude, _hitratio, _safelevel, _shoptitle,
                _weaponsprite, _couplestate, _boundarylevelindex, _weakelemental, _manaratio, _botindex, _homeserverno,
                _team_id, _dialog_radius, _speed_value_flag, _second_speed_type, _explosion_remain_time_ms,
                _proclamation_siege_mark, _npc_class_id, _companion);
        return writer.finishWithTrailer(ProtoMsg.SC_WORLD_PUT_OBJECT_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        long proto112_bit = 0;
        int proto112_point = 0;
        int proto112_objectnumber = 0;
        int proto112_objectsprite = 0;
        int proto112_action = 0;
        int proto112_direction = 0;
        int proto112_lightRadius = 0;
        int proto112_objectcount = 0;
        int proto112_alignment = 0;
        String proto112_desc = null;
        String proto112_title = null;
        int proto112_speeddata = 0;
        int proto112_emotion = 0;
        int proto112_drunken = 0;
        boolean proto112_isghost = false;
        boolean proto112_isparalyzed = false;
        boolean proto112_isuser = false;
        boolean proto112_isinvisible = false;
        boolean proto112_ispoisoned = false;
        int proto112_emblemid = 0;
        String proto112_pledgename = null;
        String proto112_mastername = null;
        int proto112_altitude = 0;
        int proto112_hitratio = 0;
        int proto112_safelevel = 0;
        String proto112_shoptitle = null;
        int proto112_weaponsprite = 0;
        int proto112_couplestate = 0;
        int proto112_boundarylevelindex = 0;
        int proto112_weakelemental = 0;
        int proto112_manaratio = 0;
        int proto112_botindex = 0;
        int proto112_homeserverno = 0;
        int proto112_team_id = 0;
        int proto112_dialog_radius = 0;
        int proto112_speed_value_flag = 0;
        int proto112_second_speed_type = 0;
        int proto112_explosion_remain_time_ms = 0;
        boolean proto112_proclamation_siege_mark = false;
        int proto112_npc_class_id = 0;
        LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion proto112_companion = null;


        return sendFields(
                protoRecipient, proto112_bit, proto112_point, proto112_objectnumber, proto112_objectsprite, proto112_action,
                proto112_direction, proto112_lightRadius, proto112_objectcount, proto112_alignment, proto112_desc,
                proto112_title, proto112_speeddata, proto112_emotion, proto112_drunken, proto112_isghost,
                proto112_isparalyzed, proto112_isuser, proto112_isinvisible, proto112_ispoisoned, proto112_emblemid,
                proto112_pledgename, proto112_mastername, proto112_altitude, proto112_hitratio, proto112_safelevel,
                proto112_shoptitle, proto112_weaponsprite, proto112_couplestate, proto112_boundarylevelindex,
                proto112_weakelemental, proto112_manaratio, proto112_botindex, proto112_homeserverno, proto112_team_id,
                proto112_dialog_radius, proto112_speed_value_flag, proto112_second_speed_type,
                proto112_explosion_remain_time_ms, proto112_proclamation_siege_mark, proto112_npc_class_id,
                proto112_companion);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient, MJEffectTriggerInstance trigger) {
        long notiWire_bit = 0;
                int notiWire_point = 0;
                int notiWire_objectnumber = 0;
                int notiWire_objectsprite = 0;
                int notiWire_action = 0;
                int notiWire_direction = 0;
                int notiWire_lightRadius = 0;
                int notiWire_objectcount = 0;
                int notiWire_alignment = 0;
                String notiWire_desc = null;
                String notiWire_title = null;
                int notiWire_speeddata = 0;
                int notiWire_emotion = 0;
                int notiWire_drunken = 0;
                boolean notiWire_isghost = false;
                boolean notiWire_isparalyzed = false;
                boolean notiWire_isuser = false;
                boolean notiWire_isinvisible = false;
                boolean notiWire_ispoisoned = false;
                int notiWire_emblemid = 0;
                String notiWire_pledgename = null;
                String notiWire_mastername = null;
                int notiWire_altitude = 0;
                int notiWire_hitratio = 0;
                int notiWire_safelevel = 0;
                String notiWire_shoptitle = null;
                int notiWire_weaponsprite = 0;
                int notiWire_couplestate = 0;
                int notiWire_boundarylevelindex = 0;
                int notiWire_weakelemental = 0;
                int notiWire_manaratio = 0;
                int notiWire_botindex = 0;
                int notiWire_homeserverno = 0;
                int notiWire_team_id = 0;
                int notiWire_dialog_radius = 0;
                int notiWire_speed_value_flag = 0;
                int notiWire_second_speed_type = 0;
                int notiWire_explosion_remain_time_ms = 0;
                boolean notiWire_proclamation_siege_mark = false;
                int notiWire_npc_class_id = 0;
                LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion notiWire_companion = null;


                { notiWire_bit |= 1L; notiWire_point = (trigger.get_long_location_reverse()); }
                { notiWire_bit |= 2L; notiWire_objectnumber = (trigger.getId()); }
                { notiWire_bit |= 4L; notiWire_objectsprite = (trigger.get_sprite()); }
                { notiWire_bit |= 16L; notiWire_direction = 1; }
                { notiWire_bit |= 8L; notiWire_action = (trigger.get_action()); }
                { notiWire_bit |= 32L; notiWire_lightRadius = 0; }
                { notiWire_bit |= 64L; notiWire_objectcount = 0; }
                { notiWire_bit |= 128L; notiWire_alignment = 0; }
                { notiWire_bit |= 256L; notiWire_desc = (""); }
                { notiWire_bit |= 512L; notiWire_title = (""); }
                { notiWire_bit |= 1024L; notiWire_speeddata = 1; }
                { notiWire_bit |= 2048L; notiWire_emotion = 1; }
                { notiWire_bit |= 4096L; notiWire_drunken = 0; }
                { notiWire_bit |= 8192L; notiWire_isghost = false; }
                { notiWire_bit |= 16384L; notiWire_isparalyzed = false; }
                { notiWire_bit |= 32768L; notiWire_isuser = false; }
                { notiWire_bit |= 65536L; notiWire_isinvisible = false; }
                { notiWire_bit |= 131072L; notiWire_ispoisoned = false; }
                { notiWire_bit |= 262144L; notiWire_emblemid = 0; }
                { notiWire_bit |= 524288L; notiWire_pledgename = (""); }
                { notiWire_bit |= 1048576L; notiWire_mastername = (""); }
                { notiWire_bit |= 2097152L; notiWire_altitude = 0; }
                { notiWire_bit |= 4194304L; notiWire_hitratio = (-1); }
                { notiWire_bit |= 8388608L; notiWire_safelevel = 1; }
                { notiWire_bit |= 33554432L; notiWire_weaponsprite = (-1); }
                { notiWire_bit |= 67108864L; notiWire_couplestate = 0; }
                { notiWire_bit |= 134217728L; notiWire_boundarylevelindex = 1; }
                { notiWire_bit |= 17179869184L; notiWire_speed_value_flag = 1; }
                { notiWire_bit |= 34359738368L; notiWire_second_speed_type = 1; }
                return LIN.SC_WORLD_PUT_OBJECT_NOTI.sendFields(
                        protoRecipient, notiWire_bit, notiWire_point, notiWire_objectnumber, notiWire_objectsprite,
                        notiWire_action, notiWire_direction, notiWire_lightRadius, notiWire_objectcount, notiWire_alignment,
                        notiWire_desc, notiWire_title, notiWire_speeddata, notiWire_emotion, notiWire_drunken,
                        notiWire_isghost, notiWire_isparalyzed, notiWire_isuser, notiWire_isinvisible, notiWire_ispoisoned,
                        notiWire_emblemid, notiWire_pledgename, notiWire_mastername, notiWire_altitude, notiWire_hitratio,
                        notiWire_safelevel, notiWire_shoptitle, notiWire_weaponsprite, notiWire_couplestate,
                        notiWire_boundarylevelindex, notiWire_weakelemental, notiWire_manaratio, notiWire_botindex,
                        notiWire_homeserverno, notiWire_team_id, notiWire_dialog_radius, notiWire_speed_value_flag,
                        notiWire_second_speed_type, notiWire_explosion_remain_time_ms, notiWire_proclamation_siege_mark,
                        notiWire_npc_class_id, notiWire_companion);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient, MJEffectTriggerInstance trigger) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection(), trigger);
    }

    public static boolean sendNew_namechat_isntance(CIOClient protoRecipient, L1NpcInstance npc, String name, boolean is_ghost) {
        long notiWire_bit = 0;
                int notiWire_point = 0;
                int notiWire_objectnumber = 0;
                int notiWire_objectsprite = 0;
                int notiWire_action = 0;
                int notiWire_direction = 0;
                int notiWire_lightRadius = 0;
                int notiWire_objectcount = 0;
                int notiWire_alignment = 0;
                String notiWire_desc = null;
                String notiWire_title = null;
                int notiWire_speeddata = 0;
                int notiWire_emotion = 0;
                int notiWire_drunken = 0;
                boolean notiWire_isghost = false;
                boolean notiWire_isparalyzed = false;
                boolean notiWire_isuser = false;
                boolean notiWire_isinvisible = false;
                boolean notiWire_ispoisoned = false;
                int notiWire_emblemid = 0;
                String notiWire_pledgename = null;
                String notiWire_mastername = null;
                int notiWire_altitude = 0;
                int notiWire_hitratio = 0;
                int notiWire_safelevel = 0;
                String notiWire_shoptitle = null;
                int notiWire_weaponsprite = 0;
                int notiWire_couplestate = 0;
                int notiWire_boundarylevelindex = 0;
                int notiWire_weakelemental = 0;
                int notiWire_manaratio = 0;
                int notiWire_botindex = 0;
                int notiWire_homeserverno = 0;
                int notiWire_team_id = 0;
                int notiWire_dialog_radius = 0;
                int notiWire_speed_value_flag = 0;
                int notiWire_second_speed_type = 0;
                int notiWire_explosion_remain_time_ms = 0;
                boolean notiWire_proclamation_siege_mark = false;
                int notiWire_npc_class_id = 0;
                LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion notiWire_companion = null;


                { notiWire_bit |= 1L; notiWire_point = (npc.getLongLocationReverse()); }
                { notiWire_bit |= 2L; notiWire_objectnumber = (npc.getId()); }
                { notiWire_bit |= 4L; notiWire_objectsprite = (npc.getCurrentSpriteId()); }
                { notiWire_bit |= 8L; notiWire_action = (npc.getStatus()); }
                { notiWire_bit |= 16L; notiWire_direction = (npc.getHeading()); }
                { notiWire_bit |= 32L; notiWire_lightRadius = (npc.getNpcTemplate().getLightSize()); }
                { notiWire_bit |= 64L; notiWire_objectcount = 1; }
                { notiWire_bit |= 128L; notiWire_alignment = (npc.getTempLawful()); }
                { notiWire_bit |= 256L; notiWire_desc = name; }
                { notiWire_bit |= 512L; notiWire_title = (""); }
                { notiWire_bit |= 1024L; notiWire_speeddata = 1; }
                { notiWire_bit |= 2048L; notiWire_emotion = 1; }
                { notiWire_bit |= 4096L; notiWire_drunken = 0; }
                { notiWire_bit |= 8192L; notiWire_isghost = is_ghost; }
                { notiWire_bit |= 16384L; notiWire_isparalyzed = false; }
                { notiWire_bit |= 32768L; notiWire_isuser = false; }
                { notiWire_bit |= 65536L; notiWire_isinvisible = false; }
                { notiWire_bit |= 131072L; notiWire_ispoisoned = false; }
                { notiWire_bit |= 262144L; notiWire_emblemid = 0; }
                { notiWire_bit |= 524288L; notiWire_pledgename = null; }
                { notiWire_bit |= 1048576L; notiWire_mastername = (""); }
                { notiWire_bit |= 2097152L; notiWire_altitude = 5; }
                { notiWire_bit |= 4194304L; notiWire_hitratio = (-1); }
                { notiWire_bit |= 8388608L; notiWire_safelevel = 1; }
                { notiWire_bit |= 33554432L; notiWire_weaponsprite = 1; }
                { notiWire_bit |= 67108864L; notiWire_couplestate = 0; }
                { notiWire_bit |= 134217728L; notiWire_boundarylevelindex = 0; }
                return LIN.SC_WORLD_PUT_OBJECT_NOTI.sendFields(
                        protoRecipient, notiWire_bit, notiWire_point, notiWire_objectnumber, notiWire_objectsprite,
                        notiWire_action, notiWire_direction, notiWire_lightRadius, notiWire_objectcount, notiWire_alignment,
                        notiWire_desc, notiWire_title, notiWire_speeddata, notiWire_emotion, notiWire_drunken,
                        notiWire_isghost, notiWire_isparalyzed, notiWire_isuser, notiWire_isinvisible, notiWire_ispoisoned,
                        notiWire_emblemid, notiWire_pledgename, notiWire_mastername, notiWire_altitude, notiWire_hitratio,
                        notiWire_safelevel, notiWire_shoptitle, notiWire_weaponsprite, notiWire_couplestate,
                        notiWire_boundarylevelindex, notiWire_weakelemental, notiWire_manaratio, notiWire_botindex,
                        notiWire_homeserverno, notiWire_team_id, notiWire_dialog_radius, notiWire_speed_value_flag,
                        notiWire_second_speed_type, notiWire_explosion_remain_time_ms, notiWire_proclamation_siege_mark,
                        notiWire_npc_class_id, notiWire_companion);
    }

    public static boolean sendNew_namechat_isntance(L1PcInstance protoRecipient, L1NpcInstance npc, String name, boolean is_ghost) {
        return sendNew_namechat_isntance(protoRecipient == null ? null : protoRecipient.getNetConnection(), npc, name, is_ghost);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient, MJDogFightInstance dogfight) {
        long notiWire_bit = 0;
                int notiWire_point = 0;
                int notiWire_objectnumber = 0;
                int notiWire_objectsprite = 0;
                int notiWire_action = 0;
                int notiWire_direction = 0;
                int notiWire_lightRadius = 0;
                int notiWire_objectcount = 0;
                int notiWire_alignment = 0;
                String notiWire_desc = null;
                String notiWire_title = null;
                int notiWire_speeddata = 0;
                int notiWire_emotion = 0;
                int notiWire_drunken = 0;
                boolean notiWire_isghost = false;
                boolean notiWire_isparalyzed = false;
                boolean notiWire_isuser = false;
                boolean notiWire_isinvisible = false;
                boolean notiWire_ispoisoned = false;
                int notiWire_emblemid = 0;
                String notiWire_pledgename = null;
                String notiWire_mastername = null;
                int notiWire_altitude = 0;
                int notiWire_hitratio = 0;
                int notiWire_safelevel = 0;
                String notiWire_shoptitle = null;
                int notiWire_weaponsprite = 0;
                int notiWire_couplestate = 0;
                int notiWire_boundarylevelindex = 0;
                int notiWire_weakelemental = 0;
                int notiWire_manaratio = 0;
                int notiWire_botindex = 0;
                int notiWire_homeserverno = 0;
                int notiWire_team_id = 0;
                int notiWire_dialog_radius = 0;
                int notiWire_speed_value_flag = 0;
                int notiWire_second_speed_type = 0;
                int notiWire_explosion_remain_time_ms = 0;
                boolean notiWire_proclamation_siege_mark = false;
                int notiWire_npc_class_id = 0;
                LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion notiWire_companion = null;


                { notiWire_bit |= 1L; notiWire_point = (dogfight.getLongLocationReverse()); }
                { notiWire_bit |= 2L; notiWire_objectnumber = (dogfight.getId()); }
                { notiWire_bit |= 4L; notiWire_objectsprite = (dogfight.getCurrentSpriteId()); }
                { notiWire_bit |= 8L; notiWire_action = (dogfight.getStatus()); }
                { notiWire_bit |= 16L; notiWire_direction = (dogfight.getHeading()); }
                { notiWire_bit |= 32L; notiWire_lightRadius = (dogfight.getNpcTemplate().getLightSize()); }
                { notiWire_bit |= 64L; notiWire_objectcount = 1; }
                { notiWire_bit |= 128L; notiWire_alignment = (dogfight.getTempLawful()); }
                { notiWire_bit |= 256L; notiWire_desc = (dogfight.getName()); }
                { notiWire_bit |= 512L; notiWire_title = (""); }
                { notiWire_bit |= 1024L; notiWire_speeddata = 1; }
                { notiWire_bit |= 2048L; notiWire_emotion = 1; }
                { notiWire_bit |= 4096L; notiWire_drunken = 0; }
                { notiWire_bit |= 8192L; notiWire_isghost = false; }
                { notiWire_bit |= 16384L; notiWire_isparalyzed = false; }
                { notiWire_bit |= 32768L; notiWire_isuser = false; }
                { notiWire_bit |= 65536L; notiWire_isinvisible = false; }
                { notiWire_bit |= 131072L; notiWire_ispoisoned = false; }
                { notiWire_bit |= 262144L; notiWire_emblemid = 0; }
                { notiWire_bit |= 524288L; notiWire_pledgename = null; }
                { notiWire_bit |= 1048576L; notiWire_mastername = (""); }
                { notiWire_bit |= 2097152L; notiWire_altitude = 5; }
                { notiWire_bit |= 4194304L; notiWire_hitratio = ((int)dogfight.getCurrentHpPercent()); }
                { notiWire_bit |= 8388608L; notiWire_safelevel = (dogfight.getLevel()); }
                { notiWire_bit |= 33554432L; notiWire_weaponsprite = 1; }
                { notiWire_bit |= 67108864L; notiWire_couplestate = 0; }
                { notiWire_bit |= 134217728L; notiWire_boundarylevelindex = 0; }
                if(dogfight.get_explosion_time_mills() > 0)
                    { notiWire_bit |= 68719476736L; notiWire_explosion_remain_time_ms = (dogfight.get_explosion_time_mills()); }
                return LIN.SC_WORLD_PUT_OBJECT_NOTI.sendFields(
                        protoRecipient, notiWire_bit, notiWire_point, notiWire_objectnumber, notiWire_objectsprite,
                        notiWire_action, notiWire_direction, notiWire_lightRadius, notiWire_objectcount, notiWire_alignment,
                        notiWire_desc, notiWire_title, notiWire_speeddata, notiWire_emotion, notiWire_drunken,
                        notiWire_isghost, notiWire_isparalyzed, notiWire_isuser, notiWire_isinvisible, notiWire_ispoisoned,
                        notiWire_emblemid, notiWire_pledgename, notiWire_mastername, notiWire_altitude, notiWire_hitratio,
                        notiWire_safelevel, notiWire_shoptitle, notiWire_weaponsprite, notiWire_couplestate,
                        notiWire_boundarylevelindex, notiWire_weakelemental, notiWire_manaratio, notiWire_botindex,
                        notiWire_homeserverno, notiWire_team_id, notiWire_dialog_radius, notiWire_speed_value_flag,
                        notiWire_second_speed_type, notiWire_explosion_remain_time_ms, notiWire_proclamation_siege_mark,
                        notiWire_npc_class_id, notiWire_companion);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient, MJDogFightInstance dogfight) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection(), dogfight);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient, L1NpcInstance npc) {
        long notiWire_bit = 0;
                int notiWire_point = 0;
                int notiWire_objectnumber = 0;
                int notiWire_objectsprite = 0;
                int notiWire_action = 0;
                int notiWire_direction = 0;
                int notiWire_lightRadius = 0;
                int notiWire_objectcount = 0;
                int notiWire_alignment = 0;
                String notiWire_desc = null;
                String notiWire_title = null;
                int notiWire_speeddata = 0;
                int notiWire_emotion = 0;
                int notiWire_drunken = 0;
                boolean notiWire_isghost = false;
                boolean notiWire_isparalyzed = false;
                boolean notiWire_isuser = false;
                boolean notiWire_isinvisible = false;
                boolean notiWire_ispoisoned = false;
                int notiWire_emblemid = 0;
                String notiWire_pledgename = null;
                String notiWire_mastername = null;
                int notiWire_altitude = 0;
                int notiWire_hitratio = 0;
                int notiWire_safelevel = 0;
                String notiWire_shoptitle = null;
                int notiWire_weaponsprite = 0;
                int notiWire_couplestate = 0;
                int notiWire_boundarylevelindex = 0;
                int notiWire_weakelemental = 0;
                int notiWire_manaratio = 0;
                int notiWire_botindex = 0;
                int notiWire_homeserverno = 0;
                int notiWire_team_id = 0;
                int notiWire_dialog_radius = 0;
                int notiWire_speed_value_flag = 0;
                int notiWire_second_speed_type = 0;
                int notiWire_explosion_remain_time_ms = 0;
                boolean notiWire_proclamation_siege_mark = false;
                int notiWire_npc_class_id = 0;
                LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion notiWire_companion = null;


                boolean is_companion = npc instanceof L1CompanionInstance;
                L1CompanionInstance companion_instance = is_companion ? (L1CompanionInstance)npc : null;

                { notiWire_bit |= 1L; notiWire_point = (npc.getLongLocationReverse()); }
                { notiWire_bit |= 2L; notiWire_objectnumber = (npc.getId()); }
                { notiWire_bit |= 4L; notiWire_objectsprite = (npc.getCurrentSpriteId()); }
                { notiWire_bit |= 8L; notiWire_action = (get_presentation_npc_action(npc)); }
                { notiWire_bit |= 16L; notiWire_direction = (npc.getHeading()); }
                { notiWire_bit |= 32L; notiWire_lightRadius = (npc.getNpcTemplate().getLightSize()); }
                { notiWire_bit |= 64L; notiWire_objectcount = 1; }
                { notiWire_bit |= 128L; notiWire_alignment = (npc.getTempLawful()); }
                { notiWire_bit |= 256L; notiWire_desc = (is_companion ? companion_instance.getName() : get_presentation_npc_desc(npc)); }
                { notiWire_bit |= 512L; notiWire_title = (get_presentation_npc_title(npc)); }
                { notiWire_bit |= 1024L; notiWire_speeddata = (npc.getMoveSpeed()); }
                { notiWire_bit |= 2048L; notiWire_emotion = (npc.getBraveSpeed()); }
                { notiWire_bit |= 4096L; notiWire_drunken = 0; }
                { notiWire_bit |= 8192L; notiWire_isghost = false; }
                { notiWire_bit |= 16384L; notiWire_isparalyzed = (npc.getParalysis() != null); }
                { notiWire_bit |= 32768L; notiWire_isuser = (npc.getShopName() != null || !npc.isPassObject()); }
                { notiWire_bit |= 65536L; notiWire_isinvisible = (npc.isInvisble()); }
                { notiWire_bit |= 131072L; notiWire_ispoisoned = (npc.getPoison() != null); }
                { notiWire_bit |= 262144L; notiWire_emblemid = (get_presentation_npc_emblem(npc)); }
                { notiWire_bit |= 524288L; notiWire_pledgename = null; }
                if(!is_companion)
                    { notiWire_bit |= 1048576L; notiWire_mastername = (npc.getMaster() != null ? npc.getMaster().getName() : null); }
                else
                    { notiWire_bit |= 1048576L; notiWire_mastername = (companion_instance.get_master_name()); }
                { notiWire_bit |= 2097152L; notiWire_altitude = 5; }
                { notiWire_bit |= 4194304L; notiWire_hitratio = (-1); }
                { notiWire_bit |= 8388608L; notiWire_safelevel = (npc.getLevel()); }
                if(npc.getShopName() != null)
                    { notiWire_bit |= 16777216L; notiWire_shoptitle = (npc.getShopName()); }
                { notiWire_bit |= 33554432L; notiWire_weaponsprite = 1; }
                { notiWire_bit |= 67108864L; notiWire_couplestate = 0; }
                { notiWire_bit |= 134217728L; notiWire_boundarylevelindex = 0; }
                { notiWire_bit |= 268435456L; notiWire_weakelemental = (npc.getNpcTemplate().get_weakAttr()); }
                { notiWire_bit |= 536870912L; notiWire_manaratio = ((int)npc.getCurrentMpPercent()); }
                { notiWire_bit |= 1073741824L; notiWire_botindex = 0; }
                { notiWire_bit |= 4294967296L; notiWire_team_id = (MJNpcMarkTable.getInstance().get(npc.getNpcId())); }
                { notiWire_bit |= 8589934592L; notiWire_dialog_radius = 0; }
                { notiWire_bit |= 17179869184L; notiWire_speed_value_flag = 0; }
                { notiWire_bit |= 34359738368L; notiWire_second_speed_type = 0; }
                if(npc.getExplosionTime() != 0)
                    { notiWire_bit |= 68719476736L; notiWire_explosion_remain_time_ms = ((int)npc.getExplosionTime()); }
                if(is_companion){
                    { notiWire_bit |= 274877906944L; notiWire_npc_class_id = (companion_instance.get_class_info().get_class_id()); }
                    LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion companion = new LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion();
                    companion.set_attack_delay_reduce(companion_instance.get_attackdelay_reduce());
                    companion.set_move_delay_reduce(companion_instance.get_movedelay_reduce());
                    companion.set_pvp_dmg_ratio(companion_instance.get_pvp_dmg_ratio());
                    { notiWire_bit |= 549755813888L; notiWire_companion = companion; }
                } else if (npc instanceof L1MonsterInstance && npc.getNpcTemplate().get_npc_class_id() > 0) {
                    notiWire_bit |= 274877906944L;
                    notiWire_npc_class_id = npc.getNpcTemplate().get_npc_class_id();
                }
                return LIN.SC_WORLD_PUT_OBJECT_NOTI.sendFields(
                        protoRecipient, notiWire_bit, notiWire_point, notiWire_objectnumber, notiWire_objectsprite,
                        notiWire_action, notiWire_direction, notiWire_lightRadius, notiWire_objectcount, notiWire_alignment,
                        notiWire_desc, notiWire_title, notiWire_speeddata, notiWire_emotion, notiWire_drunken,
                        notiWire_isghost, notiWire_isparalyzed, notiWire_isuser, notiWire_isinvisible, notiWire_ispoisoned,
                        notiWire_emblemid, notiWire_pledgename, notiWire_mastername, notiWire_altitude, notiWire_hitratio,
                        notiWire_safelevel, notiWire_shoptitle, notiWire_weaponsprite, notiWire_couplestate,
                        notiWire_boundarylevelindex, notiWire_weakelemental, notiWire_manaratio, notiWire_botindex,
                        notiWire_homeserverno, notiWire_team_id, notiWire_dialog_radius, notiWire_speed_value_flag,
                        notiWire_second_speed_type, notiWire_explosion_remain_time_ms, notiWire_proclamation_siege_mark,
                        notiWire_npc_class_id, notiWire_companion);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient, L1NpcInstance npc) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection(), npc);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient, L1DollInstance doll) {
        long notiWire_bit = 0;
                int notiWire_point = 0;
                int notiWire_objectnumber = 0;
                int notiWire_objectsprite = 0;
                int notiWire_action = 0;
                int notiWire_direction = 0;
                int notiWire_lightRadius = 0;
                int notiWire_objectcount = 0;
                int notiWire_alignment = 0;
                String notiWire_desc = null;
                String notiWire_title = null;
                int notiWire_speeddata = 0;
                int notiWire_emotion = 0;
                int notiWire_drunken = 0;
                boolean notiWire_isghost = false;
                boolean notiWire_isparalyzed = false;
                boolean notiWire_isuser = false;
                boolean notiWire_isinvisible = false;
                boolean notiWire_ispoisoned = false;
                int notiWire_emblemid = 0;
                String notiWire_pledgename = null;
                String notiWire_mastername = null;
                int notiWire_altitude = 0;
                int notiWire_hitratio = 0;
                int notiWire_safelevel = 0;
                String notiWire_shoptitle = null;
                int notiWire_weaponsprite = 0;
                int notiWire_couplestate = 0;
                int notiWire_boundarylevelindex = 0;
                int notiWire_weakelemental = 0;
                int notiWire_manaratio = 0;
                int notiWire_botindex = 0;
                int notiWire_homeserverno = 0;
                int notiWire_team_id = 0;
                int notiWire_dialog_radius = 0;
                int notiWire_speed_value_flag = 0;
                int notiWire_second_speed_type = 0;
                int notiWire_explosion_remain_time_ms = 0;
                boolean notiWire_proclamation_siege_mark = false;
                int notiWire_npc_class_id = 0;
                LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion notiWire_companion = null;


                if (doll == null) return LIN.SC_WORLD_PUT_OBJECT_NOTI.sendFields(
                        protoRecipient, notiWire_bit, notiWire_point, notiWire_objectnumber, notiWire_objectsprite,
                        notiWire_action, notiWire_direction, notiWire_lightRadius, notiWire_objectcount, notiWire_alignment,
                        notiWire_desc, notiWire_title, notiWire_speeddata, notiWire_emotion, notiWire_drunken,
                        notiWire_isghost, notiWire_isparalyzed, notiWire_isuser, notiWire_isinvisible, notiWire_ispoisoned,
                        notiWire_emblemid, notiWire_pledgename, notiWire_mastername, notiWire_altitude, notiWire_hitratio,
                        notiWire_safelevel, notiWire_shoptitle, notiWire_weaponsprite, notiWire_couplestate,
                        notiWire_boundarylevelindex, notiWire_weakelemental, notiWire_manaratio, notiWire_botindex,
                        notiWire_homeserverno, notiWire_team_id, notiWire_dialog_radius, notiWire_speed_value_flag,
                        notiWire_second_speed_type, notiWire_explosion_remain_time_ms, notiWire_proclamation_siege_mark,
                        notiWire_npc_class_id, notiWire_companion);

                int id = 0, spriteId = 0, status = 0, heading = 0, moveSpeed = 0, level = 1;
                String nameId = "", title = "", masterName = "";

                try {
                    id = doll.getId();
                    spriteId = doll.getCurrentSpriteId();
                    status = doll.getStatus();
                    heading = doll.getHeading();
                    moveSpeed = doll.getMoveSpeed();
                    level = doll.getLevel();

                    if (doll.getNameId() != null) nameId = doll.getNameId();
                    if (doll.getTitle() != null) title = doll.getTitle();
                    if (doll.getMaster() != null && doll.getMaster().getName() != null)
                        masterName = doll.getMaster().getName();
                } catch (Exception ignored) {}

                { notiWire_bit |= 1L; notiWire_point = (doll.getLongLocationReverse()); }
                { notiWire_bit |= 2L; notiWire_objectnumber = id; }
                { notiWire_bit |= 4L; notiWire_objectsprite = spriteId; }
                { notiWire_bit |= 8L; notiWire_action = status; }
                { notiWire_bit |= 16L; notiWire_direction = heading; }
                { notiWire_bit |= 32L; notiWire_lightRadius = 0; }
                { notiWire_bit |= 64L; notiWire_objectcount = 1; }
                { notiWire_bit |= 128L; notiWire_alignment = 0; }
                { notiWire_bit |= 256L; notiWire_desc = nameId; }
                { notiWire_bit |= 512L; notiWire_title = title; }
                { notiWire_bit |= 1024L; notiWire_speeddata = moveSpeed; }
                { notiWire_bit |= 2048L; notiWire_emotion = 0; }
                { notiWire_bit |= 4096L; notiWire_drunken = 0; }
                { notiWire_bit |= 8192L; notiWire_isghost = false; }
                { notiWire_bit |= 16384L; notiWire_isparalyzed = false; }
                { notiWire_bit |= 32768L; notiWire_isuser = false; }
                { notiWire_bit |= 65536L; notiWire_isinvisible = false; }
                { notiWire_bit |= 131072L; notiWire_ispoisoned = false; }
                { notiWire_bit |= 262144L; notiWire_emblemid = 0; }
                { notiWire_bit |= 524288L; notiWire_pledgename = null; }
                { notiWire_bit |= 1048576L; notiWire_mastername = masterName; }
                { notiWire_bit |= 2097152L; notiWire_altitude = 5; }
                { notiWire_bit |= 4194304L; notiWire_hitratio = (-1); }
                { notiWire_bit |= 8388608L; notiWire_safelevel = level; }
                { notiWire_bit |= 33554432L; notiWire_weaponsprite = 1; }
                { notiWire_bit |= 67108864L; notiWire_couplestate = 0; }
                { notiWire_bit |= 134217728L; notiWire_boundarylevelindex = 0; }
                return LIN.SC_WORLD_PUT_OBJECT_NOTI.sendFields(
                        protoRecipient, notiWire_bit, notiWire_point, notiWire_objectnumber, notiWire_objectsprite,
                        notiWire_action, notiWire_direction, notiWire_lightRadius, notiWire_objectcount, notiWire_alignment,
                        notiWire_desc, notiWire_title, notiWire_speeddata, notiWire_emotion, notiWire_drunken,
                        notiWire_isghost, notiWire_isparalyzed, notiWire_isuser, notiWire_isinvisible, notiWire_ispoisoned,
                        notiWire_emblemid, notiWire_pledgename, notiWire_mastername, notiWire_altitude, notiWire_hitratio,
                        notiWire_safelevel, notiWire_shoptitle, notiWire_weaponsprite, notiWire_couplestate,
                        notiWire_boundarylevelindex, notiWire_weakelemental, notiWire_manaratio, notiWire_botindex,
                        notiWire_homeserverno, notiWire_team_id, notiWire_dialog_radius, notiWire_speed_value_flag,
                        notiWire_second_speed_type, notiWire_explosion_remain_time_ms, notiWire_proclamation_siege_mark,
                        notiWire_npc_class_id, notiWire_companion);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient, L1DollInstance doll) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection(), doll);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient, L1SummonInstance summon, L1PcInstance viewer) {
        long notiWire_bit = 0;
               int notiWire_point = 0;
               int notiWire_objectnumber = 0;
               int notiWire_objectsprite = 0;
               int notiWire_action = 0;
               int notiWire_direction = 0;
               int notiWire_lightRadius = 0;
               int notiWire_objectcount = 0;
               int notiWire_alignment = 0;
               String notiWire_desc = null;
               String notiWire_title = null;
               int notiWire_speeddata = 0;
               int notiWire_emotion = 0;
               int notiWire_drunken = 0;
               boolean notiWire_isghost = false;
               boolean notiWire_isparalyzed = false;
               boolean notiWire_isuser = false;
               boolean notiWire_isinvisible = false;
               boolean notiWire_ispoisoned = false;
               int notiWire_emblemid = 0;
               String notiWire_pledgename = null;
               String notiWire_mastername = null;
               int notiWire_altitude = 0;
               int notiWire_hitratio = 0;
               int notiWire_safelevel = 0;
               String notiWire_shoptitle = null;
               int notiWire_weaponsprite = 0;
               int notiWire_couplestate = 0;
               int notiWire_boundarylevelindex = 0;
               int notiWire_weakelemental = 0;
               int notiWire_manaratio = 0;
               int notiWire_botindex = 0;
               int notiWire_homeserverno = 0;
               int notiWire_team_id = 0;
               int notiWire_dialog_radius = 0;
               int notiWire_speed_value_flag = 0;
               int notiWire_second_speed_type = 0;
               int notiWire_explosion_remain_time_ms = 0;
               boolean notiWire_proclamation_siege_mark = false;
               int notiWire_npc_class_id = 0;
               LIN.SC_WORLD_PUT_OBJECT_NOTI.Companion notiWire_companion = null;


               if (summon == null) return LIN.SC_WORLD_PUT_OBJECT_NOTI.sendFields(
                       protoRecipient, notiWire_bit, notiWire_point, notiWire_objectnumber, notiWire_objectsprite,
                       notiWire_action, notiWire_direction, notiWire_lightRadius, notiWire_objectcount, notiWire_alignment,
                       notiWire_desc, notiWire_title, notiWire_speeddata, notiWire_emotion, notiWire_drunken,
                       notiWire_isghost, notiWire_isparalyzed, notiWire_isuser, notiWire_isinvisible, notiWire_ispoisoned,
                       notiWire_emblemid, notiWire_pledgename, notiWire_mastername, notiWire_altitude, notiWire_hitratio,
                       notiWire_safelevel, notiWire_shoptitle, notiWire_weaponsprite, notiWire_couplestate,
                       notiWire_boundarylevelindex, notiWire_weakelemental, notiWire_manaratio, notiWire_botindex,
                       notiWire_homeserverno, notiWire_team_id, notiWire_dialog_radius, notiWire_speed_value_flag,
                       notiWire_second_speed_type, notiWire_explosion_remain_time_ms, notiWire_proclamation_siege_mark,
                       notiWire_npc_class_id, notiWire_companion);

               int id = 0, spriteId = 0, status = 0, heading = 0, lightSize = 0, moveSpeed = 0, level = 1;
               int hpPercent = -1, mpPercent = -1;
               String nameId = "", title = "", masterName = "";

               try {
                   id = summon.getId();
                   spriteId = summon.getCurrentSpriteId();
                   status = summon.getStatus();
                   heading = summon.getHeading();
                   moveSpeed = summon.getMoveSpeed();
                   level = summon.getLevel();

                   nameId = summon.getNameId() != null ? summon.getNameId() : "";
                   title = summon.getTitle() != null ? summon.getTitle() : "";

                   if (summon.getLight() != null) {
                       lightSize = summon.getLight().getChaLightSize();
                   }

                   boolean isOwner = false;
                   if (summon.isExsistMaster() && summon.getMaster() != null) {
                       masterName = summon.getMaster().getName() != null
                               ? summon.getMaster().getName() : "";
                       if (viewer != null && summon.getMaster().getId() == viewer.getId()) {
                           isOwner = true;
                       }
                   }


                   if (isOwner) {
                       int maxHp = summon.getMaxHp();
                       hpPercent = maxHp > 0
                               ? Math.max(0, Math.min(100, 100 * summon.getCurrentHp() / maxHp))
                               : 100;
                       int maxMp = summon.getMaxMp();
                       mpPercent = maxMp > 0
                               ? Math.max(0, Math.min(100, 100 * summon.getCurrentMp() / maxMp))
                               : 100;
                   }
               } catch (Exception ignored) {}

               { notiWire_bit |= 1L; notiWire_point = (summon.getLongLocationReverse()); }
               { notiWire_bit |= 2L; notiWire_objectnumber = id; }
               { notiWire_bit |= 4L; notiWire_objectsprite = spriteId; }
               { notiWire_bit |= 8L; notiWire_action = status; }
               { notiWire_bit |= 16L; notiWire_direction = heading; }
               { notiWire_bit |= 32L; notiWire_lightRadius = lightSize; }
               { notiWire_bit |= 64L; notiWire_objectcount = 1; }
               { notiWire_bit |= 128L; notiWire_alignment = 0; }
               { notiWire_bit |= 256L; notiWire_desc = nameId; }
               { notiWire_bit |= 512L; notiWire_title = title; }
               { notiWire_bit |= 1024L; notiWire_speeddata = moveSpeed; }
               { notiWire_bit |= 2048L; notiWire_emotion = 0; }
               { notiWire_bit |= 4096L; notiWire_drunken = 0; }
               { notiWire_bit |= 8192L; notiWire_isghost = false; }
               { notiWire_bit |= 16384L; notiWire_isparalyzed = false; }
               { notiWire_bit |= 32768L; notiWire_isuser = false; }
               { notiWire_bit |= 65536L; notiWire_isinvisible = false; }
               { notiWire_bit |= 131072L; notiWire_ispoisoned = (summon.getPoison() != null); }
               { notiWire_bit |= 262144L; notiWire_emblemid = 0; }
               { notiWire_bit |= 524288L; notiWire_pledgename = null; }
               { notiWire_bit |= 1048576L; notiWire_mastername = masterName; }
               { notiWire_bit |= 2097152L; notiWire_altitude = 5; }
               { notiWire_bit |= 4194304L; notiWire_hitratio = hpPercent; }
               { notiWire_bit |= 8388608L; notiWire_safelevel = level; }
               { notiWire_bit |= 33554432L; notiWire_weaponsprite = 1; }
               { notiWire_bit |= 67108864L; notiWire_couplestate = 0; }
               { notiWire_bit |= 134217728L; notiWire_boundarylevelindex = 0; }
               { notiWire_bit |= 536870912L; notiWire_manaratio = mpPercent; }
               return LIN.SC_WORLD_PUT_OBJECT_NOTI.sendFields(
                       protoRecipient, notiWire_bit, notiWire_point, notiWire_objectnumber, notiWire_objectsprite,
                       notiWire_action, notiWire_direction, notiWire_lightRadius, notiWire_objectcount, notiWire_alignment,
                       notiWire_desc, notiWire_title, notiWire_speeddata, notiWire_emotion, notiWire_drunken,
                       notiWire_isghost, notiWire_isparalyzed, notiWire_isuser, notiWire_isinvisible, notiWire_ispoisoned,
                       notiWire_emblemid, notiWire_pledgename, notiWire_mastername, notiWire_altitude, notiWire_hitratio,
                       notiWire_safelevel, notiWire_shoptitle, notiWire_weaponsprite, notiWire_couplestate,
                       notiWire_boundarylevelindex, notiWire_weakelemental, notiWire_manaratio, notiWire_botindex,
                       notiWire_homeserverno, notiWire_team_id, notiWire_dialog_radius, notiWire_speed_value_flag,
                       notiWire_second_speed_type, notiWire_explosion_remain_time_ms, notiWire_proclamation_siege_mark,
                       notiWire_npc_class_id, notiWire_companion);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient, L1SummonInstance summon, L1PcInstance viewer) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection(), summon, viewer);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient) {
        return sendCtor(protoRecipient);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }

}
