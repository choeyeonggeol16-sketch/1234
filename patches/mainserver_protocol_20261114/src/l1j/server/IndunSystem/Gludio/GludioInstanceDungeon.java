package l1j.server.IndunSystem.Gludio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.packets.Protocol.Packet.GludioDungeonPackets;
import l1j.server.server.templates.L1Npc;

/** The Orim/Gludio instance room, battle and map lifetime; the existing server owns all execution. */
public final class GludioInstanceDungeon {
    private static final Logger LOG = Logger.getLogger(GludioInstanceDungeon.class.getName());
    public static final int KEY_ITEM = 4200254;
    public static final int MINOR = 201, NORMAL = 202, DURATION_SECONDS = 900;
    private static final int FIRST_MAP = 14301, LAST_MAP = 14400, MAX_ROOMS = 100;
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final GludioInstanceDungeon INSTANCE = new GludioInstanceDungeon();
    public static GludioInstanceDungeon getInstance() { return INSTANCE; }

    public static final class Member {
        public final int charId;
        public final L1PcInstance pc;
        public boolean ready, gameInfoSent;
        Member(L1PcInstance pc, boolean ready) {
            this.charId = pc.getId(); this.pc = pc; this.ready = ready;
        }
    }

    public static final class Room implements Runnable {
        public final int id, kind, ownerId;
        public int minLevel, maximum, fee, distribution;
        public boolean boost, closed;
        public String title, password;
        public final ArrayList<Member> members = new ArrayList<>(6);
        public final ArrayList<L1NpcInstance> npcs = new ArrayList<>(256);
        public final int[] entryX = new int[6], entryY = new int[6];
        public boolean admitting, started, ended, entryChecked, clonedMap;
        public int mapId, wave, introStep, phase, kaspaWave;
        public long enteredAt, battleAt, nextEventAt, nextKaspaAt, exitAt;
        public L1NpcInstance crystal, boss, exitCircle;
        public final L1NpcInstance[] guards = new L1NpcInstance[27];
        public final L1NpcInstance[] family = new L1NpcInstance[4];
        public ScheduledFuture<?> idle;
        Room(int id, int kind, L1PcInstance owner, int minLevel, boolean boost,
             boolean closed, String title, String password, int maximum, int fee, int distribution) {
            this.id=id;this.kind=kind;this.ownerId=owner.getId();this.minLevel=minLevel;
            this.boost=boost;this.closed=closed;this.title=title;this.password=password;
            this.maximum=maximum;this.fee=fee;this.distribution=distribution;
            members.add(new Member(owner,true));
        }
        public int keyCount() { return boost ? 3 : 1; }
        @Override public void run() { INSTANCE.tick(this); }
    }

    private static final class Spawn {
        final int type,attr,npcId,x,y,heading,count;
        final String location;
        Spawn(int type,int attr,int npcId,int x,int y,int heading,int count,String location) {
            this.type=type;this.attr=attr;this.npcId=npcId;this.x=x;this.y=y;
            this.heading=heading;this.count=count;this.location=location;
        }
    }

    private final HashMap<Integer,Room> rooms=new HashMap<>();
    private final HashMap<Integer,Room> memberRooms=new HashMap<>();
    private final HashMap<Integer,Room> activeMaps=new HashMap<>();
    private final HashMap<Integer,Invite> pendingInvites=new HashMap<>();
    private static final long INVITE_WINDOW_NANOS=60*SECOND;
    private static final class Invite {
        final Room room;
        final L1PcInstance guest;
        final long deadline;
        Invite(Room r,L1PcInstance guest,long deadline) {
            this.room=r;this.guest=guest;this.deadline=deadline;
        }
    }
    private int roomSequence=1;
    private volatile Spawn[] spawns;
    private volatile boolean initialized;
    private GludioInstanceDungeon() {}

    /** Reads the supplied SQL once. Missing map terrain is checked only at start, not at boot. */
    public synchronized void initialize() {
        if(initialized) return;
        initialized=true;
        ArrayList<Spawn> loaded=new ArrayList<>();
        try(Connection c=DatabaseFactory.getInstance().getConnection();
            PreparedStatement ps=c.prepareStatement("SELECT type,attr,npc_id,locx,locy,heading,`count`,location FROM spawnlist_gludio_indun");
            ResultSet rs=ps.executeQuery()) {
            while(rs.next()) {
                int id=rs.getInt("npc_id"), type=rs.getInt("type"), cnt=rs.getInt("count");
                if(id<=0 || cnt<=0 || cnt>100 || NpcTable.getInstance().getTemplate(id)==null) {
                    LOG.warning("[Gludio-Indun] Invalid spawn NPC="+id+" count="+cnt);
                    initialized=false;return;
                }
                loaded.add(new Spawn(type,rs.getInt("attr"),id,rs.getInt("locx"),
                        rs.getInt("locy"),rs.getInt("heading"),cnt,rs.getString("location")));
            }
        } catch(Exception e) {
            LOG.log(Level.WARNING,"[Gludio-Indun] spawnlist_gludio_indun unavailable",e);
            initialized=false;return;
        }
        if(loaded.isEmpty()) {LOG.warning("[Gludio-Indun] Empty spawn table");initialized=false;return;}
        spawns=loaded.toArray(new Spawn[0]);
        LOG.info("[Gludio-Indun] Loaded "+spawns.length+" spawn entries; maps are checked on entry");
    }

    public boolean available() {return spawns!=null;}
    public synchronized Room room(int id) {return rooms.get(id);}
    public synchronized Room[] roomList(int kind) {
        ArrayList<Room> list=new ArrayList<>();
        for(Room r:rooms.values()) if(!r.ended && !r.started && !r.admitting && (kind==0 || r.kind==kind)) list.add(r);
        return list.toArray(new Room[0]);
    }
    public synchronized int count(int kind) {
        int count=0;for(Room r:rooms.values()) if(!r.ended && !r.started && !r.admitting && r.kind==kind) count++;
        return count;
    }
    public synchronized Member[] members(Room r) {
        return r==null?new Member[0]:r.members.toArray(new Member[0]);
    }

    public synchronized Room create(L1PcInstance owner,int kind,int minLevel,boolean boost,
            boolean closed,String title,String password,int max,int fee,int distribution) {
        // The lobby must be creatable even if combat spawn data has not loaded yet.
        // Dungeon readiness is checked when START is requested, before key debit.
        if(owner==null || (kind!=MINOR && kind!=NORMAL)
                || memberRooms.containsKey(owner.getId()) || rooms.size()>=MAX_ROOMS
                || max<1 || max>6 || minLevel<1 || owner.getLevel()<minLevel
                || title==null || title.isBlank() || title.length()>128
                || (closed && (password==null || password.length()!=4))) return null;
        Room r=new Room(roomSequence++,kind,owner,minLevel,boost,closed,title,
                password==null?"":password,max,fee,distribution);
        r.idle=ThreadPool.getInstance().schedule(()->expire(r),600_000L);
        if(r.idle==null) return null;
        rooms.put(r.id,r);memberRooms.put(owner.getId(),r);
        return r;
    }
    public synchronized boolean join(L1PcInstance pc,int id,String password) {
        return join0(pc,id,password,false);
    }
    private boolean join0(L1PcInstance pc,int id,String password,boolean invited) {
        Room r=rooms.get(id);
        if(pc==null || r==null || r.started || r.admitting || r.ended || memberRooms.containsKey(pc.getId())
                || r.members.size()>=r.maximum || pc.getLevel()<r.minLevel
                || (r.closed && !invited && !r.password.equals(password))
                || !pc.getInventory().checkItem(KEY_ITEM,r.keyCount())) return false;
        r.members.add(new Member(pc,false));memberRooms.put(pc.getId(),r);
        pendingInvites.remove(pc.getId());
        return true;
    }
    public synchronized boolean invite(L1PcInstance owner,int roomId,String guestName) {
        Room r=rooms.get(roomId);
        if(owner==null || r==null || r.ended || r.started || r.admitting
                || r.ownerId!=owner.getId() || memberRooms.get(owner.getId())!=r
                || guestName==null || guestName.isBlank() || guestName.length()>24
                || r.members.size()>=r.maximum) return false;
        L1PcInstance guest=L1World.getInstance().getPlayer(guestName);
        if(guest==null || guest==owner || memberRooms.containsKey(guest.getId())
                || guest.getLevel()<r.minLevel || !guest.getInventory().checkItem(KEY_ITEM,r.keyCount())
                || guest.getNetConnection()==null || !guest.getNetConnection().isConnected()) return false;
        final long now=System.nanoTime();
        // At most one pending invite per target; do not retain expired invitations indefinitely.
        pendingInvites.entrySet().removeIf(e->now-e.getValue().deadline>=0 || e.getValue().room.ended);
        Invite invite=new Invite(r,guest,now+INVITE_WINDOW_NANOS);
        pendingInvites.put(guest.getId(),invite);
        if(!SC_MessageYN.send(guest,48,6008,owner.getName()+"님이 글루디오 인스턴스 던전에 초대했습니다. 수락하시겠습니까?")) {
            pendingInvites.remove(guest.getId());return false;
        }
        return true;
    }
    public synchronized Room answerInvite(L1PcInstance guest,boolean accept) {
        if(guest==null) return null;
        Invite invite=pendingInvites.remove(guest.getId());
        if(!accept || invite==null || invite.guest!=guest || System.nanoTime()-invite.deadline>=0) return null;
        Room r=invite.room;
        if(rooms.get(r.id)!=r || r.ended || r.started || r.admitting) return null;
        return join0(guest,r.id,"",true)?r:null;
    }
    public synchronized boolean ready(L1PcInstance pc,int id,boolean ready) {
        Room r=rooms.get(id);
        if(pc==null || r==null || r.started || r.admitting || r.ended || memberRooms.get(pc.getId())!=r) return false;
        for(Member m:r.members) if(m.pc==pc) {m.ready=ready;return true;}
        return false;
    }
    public synchronized boolean change(L1PcInstance pc,int id,String title,Integer minLevel,
            Boolean closed,String password,Integer max,Integer distribution,Boolean boost) {
        Room r=rooms.get(id);
        if(pc==null || r==null || r.started || r.admitting || r.ended || pc.getId()!=r.ownerId) return false;
        String newTitle=title==null?r.title:title;
        int newMin=minLevel==null?r.minLevel:minLevel;
        int newMax=max==null?r.maximum:max;
        boolean newClosed=closed==null?r.closed:closed;
        String newPassword=password==null?r.password:password;
        boolean newBoost=boost==null?r.boost:boost;
        if(newTitle.isBlank() || newTitle.length()>128 || newMin<1 || newMax<r.members.size()
                || newMax>6 || (newClosed && newPassword.length()!=4)) return false;
        for(Member m:r.members) if(m.pc.getLevel()<newMin
                || !m.pc.getInventory().checkItem(KEY_ITEM,newBoost?3:1)) return false;
        r.title=newTitle;r.minLevel=newMin;r.maximum=newMax;r.closed=newClosed;
        r.password=newPassword;r.boost=newBoost;
        if(distribution!=null) r.distribution=distribution;
        return true;
    }
    public synchronized boolean kick(L1PcInstance owner,int id,int charId) {
        Room r=rooms.get(id);
        if(owner==null || r==null || r.started || r.admitting || r.ended || owner.getId()!=r.ownerId
                || charId==r.ownerId) return false;
        for(int i=0;i<r.members.size();i++) if(r.members.get(i).charId==charId) {
            r.members.remove(i);memberRooms.remove(charId);return true;
        }
        return false;
    }
    public synchronized boolean leave(L1PcInstance pc,int id) {
        Room r=rooms.get(id);
        if(pc==null || r==null || r.ended || r.admitting || memberRooms.get(pc.getId())!=r) return false;
        if(r.started) {finish(r,"던전에서 퇴장했습니다.");return true;}
        memberRooms.remove(pc.getId());r.members.removeIf(m->m.pc==pc);
        if(r.ownerId==pc.getId() || r.members.isEmpty()) dissolve(r);
        return true;
    }
    private synchronized void expire(Room r) {
        if(rooms.get(r.id)==r && !r.started && !r.admitting) dissolve(r);
    }
    private void dissolve(Room r) {
        r.ended=true;rooms.remove(r.id);
        pendingInvites.entrySet().removeIf(e->e.getValue().room==r);
        if(r.idle!=null) r.idle.cancel(false);
        for(Member m:r.members) memberRooms.remove(m.charId);
    }

    /** All 1..6 participants' keys are debited in a single durable CacheD economy
     * receipt. A failed or uncertain commit never initiates a dungeon teleport.
     */
    public synchronized boolean start(L1PcInstance owner,int id) {
        Room r=rooms.get(id);
        if(owner==null || r==null || r.started || r.admitting || r.ended) return false;
        if(!available()) initialize();
        if(!available()
                || r.ownerId!=owner.getId() || r.members.isEmpty() || r.members.size()>6
                || (r.kind==NORMAL && r.members.size()<2)) return false;
        final int terrain=r.kind==NORMAL?731:730;
        final L1WorldMap maps=L1WorldMap.getInstance();
        if(maps.getMap((short)terrain).isNull()) return false;
        for(Member m:r.members) if(m.pc==null || !m.ready || m.pc.getNetConnection()==null
                || !m.pc.getNetConnection().isConnected() || m.pc.getLevel()<r.minLevel
                || m.pc.get_teleport()
                || m.pc.getMapId()==r.mapId && r.mapId!=0
                || !m.pc.getInventory().checkItem(KEY_ITEM,r.keyCount())) return false;
        final int mapId=reserveMapId(terrain);
        if(mapId<0 || NpcTable.getInstance().getTemplate(50000220)==null
                || NpcTable.getInstance().getTemplate(50000221)==null
                || NpcTable.getInstance().getTemplate(r.boost?(r.kind==NORMAL?460000302:460000301)
                        :(r.kind==NORMAL?50000256:50000255))==null) return false;
        try {
            // Original GludioInDungeon: use the native 730/731 terrain for the first
            // room; clone into 14301..14400 only when that terrain is already occupied.
            // Unlike the original, do not overwrite 730 with a clone of 731.
            r.clonedMap = mapId != terrain;
            if (r.clonedMap) maps.cloneMap(terrain,mapId);
            r.mapId=mapId;
            activeMaps.put(mapId,r); // reserve BEFORE asynchronous CacheD commit
            // Original MJPoint selected an entry before spawning the crystal/walls.
            if (!assignEntryPositions(r)) {
                // Missing terrain is configuration/data failure, not a runtime
                // exception. Keys have not been debited and no NPC was spawned.
                releaseMap(r);
                return false;
            }
            r.crystal=spawn(r,50000220,32801,32862,4,null,null);
            if(r.crystal==null) throw new IllegalStateException("Crystal spawn failed");
            spawnWalls(r);
            // No item is spent if map generation, initial NPC spawn or receipt prepare fails.
            final int count = r.members.size();
            final L1PcInstance[] players = new L1PcInstance[count];
            for (int i = 0; i < count; i++) players[i] = r.members.get(i).pc;
            try(L1InventoryTransaction tx = count == 1
                    ? L1InventoryTransaction.begin(owner, KEY_ITEM)
                    : count == 2 ? L1InventoryTransaction.begin(owner, players[1])
                    : L1InventoryTransaction.beginParty(players)) {
                for (L1PcInstance member : players) {
                    if (!tx.debit(member, KEY_ITEM, r.keyCount())) {
                        clearNpcs(r);releaseMap(r);return false;
                    }
                }
                r.admitting=true;
                tx.onFailure(()->{
                    synchronized(INSTANCE) {
                        if(!r.ended && r.admitting) {
                            r.admitting=false;clearNpcs(r);releaseMap(r);
                            GludioDungeonPackets.sendRoomResult(owner, GludioDungeonPackets.SC_ARENACO_BYPASS_INDUN_GAME_START_ACK, 2, r.id);
                            notice(r,"열쇠 처리에 실패해 던전 시작이 취소되었습니다.");
                        }
                    }
                });
                tx.commitAfterInventoryRelease(()->{
                    synchronized(INSTANCE) { INSTANCE.admit(r); }
                });
            }
            return true;
        } catch(Exception e) {
            LOG.log(Level.WARNING,"[Gludio-Indun] Start failed room="+id,e);
            if(!r.started) {r.admitting=false;clearNpcs(r);releaseMap(r);}
            return false;
        }
    }
    private int reserveMapId(int terrain) {
        // The original server runs the first room on the actual source map.
        if(!activeMaps.containsKey(terrain)) return terrain;
        for(int id=FIRST_MAP;id<=LAST_MAP;id++) if(!activeMaps.containsKey(id)
                && L1WorldMap.getInstance().getMap((short)id).isNull()) return id;
        return -1;
    }
    private void releaseMap(Room r) {
        final int id=r.mapId;
        if(id==0) return;
        activeMaps.remove(id,r);
        if(r.clonedMap) L1WorldMap.getInstance().removeMap(id);
        r.mapId=0;
        r.clonedMap=false;
    }
    private void admit(Room r) {
        if(!r.admitting || r.ended || r.started || activeMaps.get(r.mapId)!=r) return;
        r.admitting=false;r.started=true;
        r.enteredAt=System.nanoTime();r.battleAt=0;
        r.nextEventAt=r.enteredAt+7*SECOND;
        r.phase=0;r.wave=0;r.introStep=0;
        if(r.idle!=null) r.idle.cancel(false);
        // Source44: SC_ARENACO_BYPASS_INDUN_GAME_START_ACK (0x08b2)
        // precedes the dungeon transfer. This is a normal in-session
        // SC_Teleport, not the separate HIBREED reconnect (0x0071).
        GludioDungeonPackets.sendRoomResult(r.members.get(0).pc, GludioDungeonPackets.SC_ARENACO_BYPASS_INDUN_GAME_START_ACK, 1, r.id);
        for(Member m:r.members) {
            L1PcInstance pc=m.pc;
            if(pc!=null && pc.getNetConnection()!=null && pc.getNetConnection().isConnected()) {
                // The original game leaves the lobby through a separate server
                // transition. Here the same socket remains open: close the old
                // room UI explicitly before the normal in-session teleport.
                // This is client UI only; keep the active room and the receipt.
                GludioDungeonPackets.sendRoomResult(pc,GludioDungeonPackets.SC_ARENACO_EXIT_INDUN_ROOM_ACK,1,r.id);
                final int index = r.members.indexOf(m);
                // Original GludioInDungeonEx enters through a normal in-session teleport;
                // 18339 is not the interserver transition animation.
                pc.start_teleport(r.entryX[index],r.entryY[index],r.mapId,pc.getHeading(),18339,false,false);
                // Send the original game-info/status handshake when the physical
                // teleport has finished (the client's map/UI has then switched).
            }
        }
        notice(r,r.boost?"Boost 모드가 활성화되었습니다.":"글루디오 연구실에 입장했습니다.");
        schedule(r);
    }
    // Original MJPoint.newInstance(32801, 32862, 5, mapId, 50) selects a
    // walkable position around the crystal BEFORE spawning room NPCs.
    // The local port uses distinct slots for up to six members and never
    // debits keys or teleports a member onto an unwalkable tile.
    private boolean assignEntryPositions(Room r) {
        final L1Map map=L1WorldMap.getInstance().getMap((short)r.mapId);
        if(map.isNull()) return false;
        final WorldCollisionHash collision=WorldCollisionHash.getInstance();
        final short mapId=(short)r.mapId;
        final int need=r.members.size();
        int found=0;
        // The original search radius is 5. Consider all cells, not only its
        // outer perimeter, and start beside (not on top of) the central crystal.
        for(int radius=1;radius<=5 && found<need;radius++) {
            for(int dy=-radius;dy<=radius && found<need;dy++) {
                for(int dx=-radius;dx<=radius && found<need;dx++) {
                    if(Math.max(Math.abs(dx),Math.abs(dy))!=radius) continue;
                    final int x=32801+dx,y=32862+dy;
                    if(!map.isInMap(x,y) || !map.isPassable(x,y)
                            || !collision.canEnter(mapId,x,y,0)) continue;
                    r.entryX[found]=x;r.entryY[found]=y;found++;
                }
            }
        }
        if(found!=need) {
            int terrainCells=0, openCells=0;
            for (int dy=-5; dy<=5; dy++) for (int dx=-5; dx<=5; dx++) {
                final int x=32801+dx, y=32862+dy;
                if (map.isInMap(x,y) && map.isPassable(x,y)) {
                    terrainCells++;
                    if (collision.canEnter(mapId,x,y,0)) openCells++;
                }
            }
            LOG.warning("[Gludio-Indun] Missing source-map walkable entry cells; "
                    + "terrainCells="+terrainCells+" openCells="+openCells
                    + " check maps/"+(r.kind==NORMAL?731:730)+".txt and data/mapcache/; "
                    +"map="+r.mapId+" base="+map.getBaseMapId()+" found="+found
                    +" need="+need+" bounds="+map.getX()+","+map.getY()
                    +" size="+map.getWidth()+"x"+map.getHeight()
                    +" centerInside="+map.isInMap(32801,32862)
                    +" centerTile="+(map.isInMap(32801,32862)?map.getOriginalTile(32801,32862):-1)
                    +" centerPassable="+map.isPassable(32801,32862));
        }
        return found==need;
    }

    private void schedule(Room r) {
        if(r.ended) return;
        if(ThreadPool.getInstance().schedule(r,1000L)==null) finish(r,"던전 스케줄 등록 실패로 종료되었습니다.");
    }
    private void spawnWalls(Room r) {
        for(int i=0;i<6;i++) r.guards[i]=spawn(r,50000221,32797+i,32851,0,null,null);
        for(int i=6;i<13;i++) r.guards[i]=spawn(r,50000221,32812,32860+i-6,0,null,null);
        for(int i=13;i<20;i++) r.guards[i]=spawn(r,50000221,32796+i-13,32875,0,null,null);
        for(int i=20;i<27;i++) r.guards[i]=spawn(r,50000221,32787,32860+i-20,0,null,null);
    }
    private L1NpcInstance spawn(Room r,int id,int x,int y,int heading,String location,L1NpcInstance target) {
        L1Npc tpl=NpcTable.getInstance().getTemplate(id);
        if(tpl==null) return null;
        L1NpcInstance npc=NpcTable.getInstance().newNpcInstance(id);
        if(npc==null) return null;
        npc.setId(IdFactory.getInstance().nextId());
        npc.setMap((short)r.mapId);
        npc.setX(x);npc.setY(y);npc.setHomeX(x);npc.setHomeY(y);
        npc.setHeading(heading);
        npc.setLightSize(tpl.getLightSize());
        if(location!=null) npc.setSpawnLocation(location);
        L1World.getInstance().storeAndEnterWorld(npc);
        npc.getLight().turnOnOffLight();
        r.npcs.add(npc);
        if(npc instanceof L1MonsterInstance) {
            npc.onNpcAI();
            if(target!=null && !target.isDead()) npc.attackTarget(target);
        }
        return npc;
    }
    private void spawnRows(Room r,int type,int attr,int multiple,boolean doubled,L1NpcInstance target) {
        Spawn[] data=spawns;
        if(data==null) return;
        for(Spawn s:data) if(s.type==type && s.attr==attr) {
            int n=Math.min(200,s.count*multiple*(doubled?2:1));
            for(int i=0;i<n;i++) if(spawn(r,s.npcId,s.x,s.y,s.heading,s.location,target)==null)
                LOG.warning("[Gludio-Indun] Spawn missing NPC="+s.npcId);
        }
    }
    private void removeWalls(Room r) {
        for(int i=0;i<r.guards.length;i++) {
            final L1NpcInstance wall=r.guards[i];
            if(wall!=null) {
                if(!wall._destroyed) wall.deleteMe();
                r.guards[i]=null;
            }
        }
    }
    private void beginFamily(Room r) {
        int[] normal={50000222,50000223,50000224,50000225};
        int[] boost={460000297,460000298,460000299,460000300};
        int[] ids=r.boost?boost:normal;
        int[] xs={32799,32763,32800,32838},ys={32901,32864,32825,32862};
        for(int i=0;i<4;i++) r.family[i]=spawn(r,ids[i],xs[i],ys[i],0,null,null);
        removeWalls(r);
        r.nextKaspaAt=System.nanoTime()+SECOND*20;
    }
    private void checkFamily(Room r,long now) {
        if(r.kind!=NORMAL) return;
        // Original KaspaFamily emits the attr=5 group three times after
        // the initial family spawn: regular, doubled, regular.
        if(r.kaspaWave<3 && now>=r.nextKaspaAt) {
            spawnRows(r,r.boost?6:3,5,1,r.kaspaWave==1,r.crystal);
            r.kaspaWave++;
            r.nextKaspaAt=now+20*SECOND;
        }
    }
    // Original kaspa_check() disables an elemental stream when its family
    // member dies. Index: 0 Merikior/water, 1 Balthazar/wind,
    // 2 Sema/fire, 3 Kaspa/earth.
    private static boolean elementActive(Room r,int attr) {
        if(r.kind!=NORMAL || r.family[0]==null) return true;
        final int index = attr==1?0:attr==2?1:attr==3?3:2;
        final L1NpcInstance npc = r.family[index];
        return npc==null || (!npc._destroyed && !npc.isDead());
    }

    private void tick(Room r) {
        synchronized(this) {
            if(r.ended || !r.started) return;
            try {
                long now=System.nanoTime();
                // Original checkMember(): SC_ARENA_GAME_INFO_NOTI and
                // SC_ARENA_PLAY_STATUS_NOTI follow successful map arrival,
                // never the earlier start-teleport request.
                for(Member m:r.members) {
                    final L1PcInstance pc=m.pc;
                    if(!m.gameInfoSent && pc!=null && pc.getMapId()==r.mapId
                            && !pc.get_teleport() && pc.getNetConnection()!=null
                            && pc.getNetConnection().isConnected()) {
                        m.gameInfoSent=true;
                        // The local transition has already dismissed the lobby.
                        // SC_GAME_INFO opens the information/reward panel again
                        // on this client. Only publish the play timer/status.
                        GludioDungeonPackets.sendArenaPlayStatus(pc,DURATION_SECONDS+3,r.battleAt!=0,r.kind);
                    }
                }
                if (!r.entryChecked && now-r.enteredAt >= 3*SECOND) {
                    r.entryChecked=true;
                    for(Member m:r.members) {
                        final L1PcInstance pc=m.pc;
                        if(pc!=null && (pc.getMapId()!=r.mapId || pc.get_teleport()
                                || !pc.getMap().isPassable(pc.getX(),pc.getY())))
                            LOG.warning("[Gludio-Indun] Entry movement state pc="+pc.getId()
                                    +" map="+pc.getMapId()+" x="+pc.getX()+" y="+pc.getY()
                                    +" teleport="+pc.get_teleport()+" passable="
                                    +pc.getMap().isPassable(pc.getX(),pc.getY()));
                    }
                }
                int live=0;
                for(Member m:r.members) if(m.pc!=null && m.pc.getMapId()==r.mapId &&
                        m.pc.getNetConnection()!=null && m.pc.getNetConnection().isConnected()) live++;
                if(live==0 && now-r.enteredAt>20*SECOND) {finish(r,"던전 이용자가 없어 종료되었습니다.");return;}
                if(r.crystal==null || r.crystal.isDead() || r.crystal.getCurrentHp()<=0) {
                    finish(r,"중앙 수정구가 파괴되었습니다.");return;
                }
                if(r.battleAt!=0 && now-r.battleAt>=DURATION_SECONDS*SECOND) {
                    finish(r,"던전 이용 시간이 종료되었습니다.");return;
                }
                if(r.phase==0 && now>=r.nextEventAt) {
                    r.introStep++;
                    if(r.introStep==2) notice(r,"중앙 수정구를 보호하세요.");
                    if(r.introStep==3) notice(r,"시간이 초과되거나 수정구가 파괴되면 실패합니다.");
                    if(r.introStep>=6) {
                        // The room intro is over. Release the fire barriers on
                        // both minor and normal difficulty before enemies spawn.
                        removeWalls(r);
                        r.phase=1;r.battleAt=now;r.nextEventAt=now;
                        for(Member m:r.members) if(m.gameInfoSent)
                            GludioDungeonPackets.sendArenaPlayStatus(m.pc,DURATION_SECONDS+3,true,r.kind);
                        notice(r,"저주받은 무리가 몰려오기 시작합니다.");
                    } else r.nextEventAt=now+7*SECOND;
                }
                if(r.phase==1 && now>=r.nextEventAt) {
                    if(r.wave==4 && r.kind==NORMAL) beginFamily(r);
                    if(r.wave==12) notice(r,"저주받은 기운이 다가오기 시작합니다.");
                    int type=r.boost?5:2;
                    for(int attr=1;attr<=4;attr++)
                        if(elementActive(r,attr)) spawnRows(r,type,attr,1,false,r.crystal);
                    r.wave++;
                    if(r.wave>=16) {r.phase=2;r.nextEventAt=now+7*SECOND;}
                    else r.nextEventAt=now+20*SECOND;
                }
                checkFamily(r,now);
                if(r.phase==2 && now>=r.nextEventAt) {
                    int id=r.boost?(r.kind==NORMAL?460000302:460000301):
                            (r.kind==NORMAL?50000256:50000255);
                    r.boss=spawn(r,id,32794,32861,3,null,null);
                    if(r.boss==null) {finish(r,"보스 생성 실패로 종료되었습니다.");return;}
                    r.phase=3;
                }
                if(r.phase==3 && r.boss!=null && (r.boss.isDead() || r.boss._destroyed)) {
                    r.crystal.setCurrentHp(r.crystal.getMaxHp());
                    notice(r,"중앙 수정구 방어에 성공했습니다.");
                    r.exitCircle=spawn(r,50000262,32807,32862,6,null,null);
                    spawn(r,50000261,32811,32862,6,null,null);
                    r.phase=4;r.exitAt=now+30*SECOND;
                }
                if(r.phase==4) {
                    for(Member m:r.members) if(m.pc!=null && m.pc.getMapId()==r.mapId && r.exitCircle!=null
                            && m.pc.getX()==r.exitCircle.getX() && m.pc.getY()==r.exitCircle.getY())
                        m.pc.start_teleport(33436,32795,4,5,18339,true,false);
                    if(now>=r.exitAt) {finish(r,"글루디오 연구실이 종료되었습니다.");return;}
                }
                schedule(r);
            } catch(Throwable failure) {
                LOG.log(Level.WARNING,"[Gludio-Indun] tick room="+r.id,failure);
                finish(r,"던전 처리 오류로 종료되었습니다.");
            }
        }
    }
    private void notice(Room r,String msg) {
        for(Member m:r.members) if(m.pc!=null) m.pc.sendPackets(msg);
    }
    private void finish(Room r,String reason) {
        if(r.ended) return;
        dissolve(r);
        notice(r,reason);
        for(Member m:r.members) {
            L1PcInstance pc=m.pc;
            if(pc!=null) {
                GludioDungeonPackets.sendArenaPlayStatus(pc,0,false,r.kind);
                if(pc.getMapId()==r.mapId)
                    pc.start_teleport(33436,32795,4,5,18339,true,false);
            }
        }
        scheduleCleanup(r,15_000L);
    }
    private void scheduleCleanup(Room r,long delay) {
        if(ThreadPool.getInstance().schedule(()->cleanup(r),delay)==null)
            LOG.warning("[Gludio-Indun] Cleanup admission refused; retain map="+r.mapId);
    }
    private void cleanup(Room r) {
        synchronized(this) {
            if(r.mapId==0) return;
            for(L1Object obj:L1World.getInstance().getVisibleObjects(r.mapId).values())
                if(obj instanceof L1PcInstance) {
                    LOG.warning("[Gludio-Indun] Player still on map; cleanup deferred="+r.mapId);
                    scheduleCleanup(r,15000L);return;
                }
            clearNpcs(r);
            for(L1ItemInstance item:L1World.getInstance().getAllItem()) if(item!=null && item.getMapId()==r.mapId)
                L1World.getInstance().getInventory(item.getX(),item.getY(),item.getMapId()).removeItem(item);
            releaseMap(r);
        }
    }
    private static void clearNpcs(Room r) {
        for(L1NpcInstance npc:r.npcs) {
            try {if(npc!=null && !npc._destroyed) npc.deleteMe();}
            catch(Exception failure) {LOG.log(Level.WARNING,"[Gludio-Indun] NPC cleanup",failure);}
        }
        r.npcs.clear();
    }
}
