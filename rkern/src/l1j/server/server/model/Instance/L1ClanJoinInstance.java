package l1j.server.server.model.Instance;

import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.IdFactory;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.network.packet.CServerPacket;
import l1j.server.server.shared.L1Type;
import l1j.server.server.utils.BytesOutputStream;

public class L1ClanJoinInstance extends L1Object{
	private static final long serialVersionUID = 1L;
	private static final int USE_ITEM_ID = 3000248;
	private static final ConcurrentHashMap<Integer, L1ClanJoinInstance> _clan_to_cj = new ConcurrentHashMap<Integer, L1ClanJoinInstance>(32);
	
	public static boolean ban_user(L1PcInstance pc){
		L1ClanJoinInstance cjInstance = _clan_to_cj.get(pc.getClanid());
		if(cjInstance == null){
			return false;
		}
		removeInstance(pc.getClanid());
		return true;
	}
	
	public static boolean use_item(L1PcInstance pc, L1ItemInstance item){
		if(item.getItemId() != USE_ITEM_ID)
			return false;
		
		if(item.getItem().getMinLevel() > pc.getLevel()){
			pc.sendPackets(String.format("%s레벨부터 사용할 수 있습니다.", item.getItem().getMinLevel()));
			return true;
		}
		
		L1ClanJoinInstance cjInstance = _clan_to_cj.get(pc.getClanid());
		if(cjInstance == null){
			L1Clan clan = pc.getClan();
			
			if(clan == null){
				pc.sendPackets("\\aH혈맹이 없으면 사용할 수 없습니다.");
				return true;
			}
			
			if (!clan.getLeaderName().equalsIgnoreCase(pc.getName())) {
				pc.sendPackets("\\aH혈맹군주가 아닌경우 사용할 수 없습니다.");
				return true;
			}
			
			int x = pc.getX();
			int y = pc.getY();
			if(x < 33422 || y < 32794 || x > 33436 || y > 32799){
				pc.sendPackets("\\aH기란여관 앞주변에서만 사용가능 합니다.");
				return true;
			}
			
			pc.setSkillEffect(L1SkillId.무인혈맹, -1);
			pc.sendPackets("\\aH무인 혈맹 가입 로봇을 배치하였습니다.");
			pc.sendPackets("\\aH로봇을 어택할 시 가입종류에 따라 가입이 진행됩니다.");
			newInstance(pc);
		}else{
			L1Clan clan = pc.getClan();
			if(clan.getLeaderId() != pc.getId()){
				pc.sendPackets("혈맹의 군주만 사용할 수 있습니다.");
				return true;
			}
			pc.sendPackets("\\aH무인 혈맹 가입 로봇을 종료합니다.");
			if (pc.hasSkillEffect(L1SkillId.무인혈맹)) {
				pc.removeSkillEffect(L1SkillId.무인혈맹);
			}
			removeInstance(pc.getClanid());
		}
		
		return true;
	}
	
	public static L1ClanJoinInstance newInstance(L1PcInstance pc){
		L1ClanJoinInstance cjInstance = newInstance();
		cjInstance.set_name(pc.getName());
		cjInstance.set_title(pc.getTitle());
		cjInstance.set_owner_id(pc.getId());
		cjInstance.set_clan_id(pc.getClanid());
		cjInstance.set_lawful(pc.getLawful());
		cjInstance.setId(IdFactory.getInstance().nextId());
		cjInstance.set_emblem_id(IdFactory.getInstance().nextId());
		cjInstance.setX(pc.getX());
		cjInstance.setY(pc.getY());
		cjInstance.setMap(pc.getMapId());
		cjInstance.set_heading(pc.getHeading());
		cjInstance.set_gfx(pc.getClassId());
		SC_WorldPutObject.get(
		        pc.getNetConnection(),
		        cjInstance.serialize());
		_clan_to_cj.put(pc.getClanid(), cjInstance);
		L1World.getInstance().storeAndEnterWorld(cjInstance);
		return cjInstance;
	}
	
	public static L1ClanJoinInstance newInstance(){
		return new L1ClanJoinInstance();
	}
	
	public static void removeInstance(int clan_id){
		L1ClanJoinInstance cjInstance = _clan_to_cj.remove(clan_id);
		if(cjInstance != null){
			cjInstance.dispose();		
		}
	}
	
	private String _name;
	private String _title;
	private int _owner_id;
	private int _clan_id;
	private int _emblem_id;
	private int _lawful;
	private int _gfx;
	private int _heading;
	private CServerPacket _world_put;
	private L1ClanJoinInstance(){
		
	}
	
	public void dispose(){
		L1World world = L1World.getInstance();
		world.removeVisibleObject(this);
		world.removeObject(this);
//		if(_world_put != null){
//			_world_put.clear();
//			_world_put = null;
//		}
		
		try (var sharedRemove = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
		        SC_RemoveObject.nativeWriter(getId()), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
		for (L1PcInstance player : world.getAllPlayers()) {
			if (player == null) {
				continue;
			}

			if (player.knownsObject(this)) {
				player.removeKnownObject(this);

				l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(sharedRemove, player);
			}
		}
		}
	}
	
	public L1ClanJoinInstance set_name(String name){
		_name = name;
		return this;
	}
	
	public String get_name(){
		return _name;
	}
	
	public L1ClanJoinInstance set_title(String title){
		_title = title;
		return this;
	}
	
	public String get_title(){
		return _title;
	}
	
	public L1ClanJoinInstance set_owner_id(int owner_id){
		_owner_id = owner_id;
		return this;
	}
	
	public int get_owner_id(){
		return _owner_id;
	}
	
	public L1PcInstance get_owner(){
		return L1World.getInstance().getPlayer(get_name());
	}
	
	public L1ClanJoinInstance set_clan_id(int clan_id){
		_clan_id = clan_id;
		return this;
	}
	
	public int get_clan_id(){
		return _clan_id;
	}
	
	public L1Clan get_clan(){
		return L1World.getInstance().getClan(get_clan_id());
	}
	
	public L1ClanJoinInstance set_emblem_id(int emblem_id){
		_emblem_id = emblem_id;
		return this;
	}
	
	public int get_emblem_id(){
		return _emblem_id;
	}
	
	public L1ClanJoinInstance set_lawful(int lawful){
		_lawful = lawful;
		return this;
	}
	
	public int get_lawful(){
		return _lawful;
	}
	
	public L1ClanJoinInstance set_gfx(int gfx){
		_gfx = gfx;
		return this;
	}
	
	public int get_gfx(){
		return _gfx;
	}
	
	public L1ClanJoinInstance set_heading(int heading){
		_heading = heading;
		return this;
	}
	
	public int get_heading(){
		return _heading;
	}
	
	public L1ClanJoinInstance set_world_put(CServerPacket pck){
		_world_put = pck;
		return this;
	}
	
	public CServerPacket get_world_put(){
		return _world_put;
	}
	
	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (_world_put == null || perceivedFrom == null || perceivedFrom.knownsObject(this)) return;
		final var client = perceivedFrom.getNetConnection();
		if (client == null || !client.isConnected() || client.getActiveChar() != perceivedFrom) return;
		if (client.tryWritePacket(client, _world_put)
				&& perceivedFrom.getNetConnection() == client && client.isConnected()) {
			perceivedFrom.addKnownObject(this);
		}
	}

	@Override
	public void onPerceive(L1SupportInstance perceivedFrom) {
	}
	
	public void receiveDamage(L1Character attacker, int damage){
		if(attacker == null || !attacker.instanceOf(L1Type.L1TYPE_PC))
			return;
		
		join((L1PcInstance)attacker);		
	}
	
	public void join(L1PcInstance joinner){
		if(joinner.getClanid() != 0 || joinner.getRedKnightClanId() != 0)
			return;
		
		L1Clan clan = get_clan();
		if(clan == null)
			return;
		
		try{
			L1PcInstance management = clan.getonline간부();
			switch(clan.getJoinType()){
			case 0:
                if (!L1ClanJoin.getInstance().ClanJoin(clan, joinner,
                        committed -> joinner.sendPackets(String.format(
                                committed ? "%s 혈맹에 가입하였습니다." : "%s 혈맹 가입에 실패하였습니다.",
                                clan.getClanName())))) {
                    joinner.sendPackets(String.format("%s 혈맹 가입에 실패하였습니다.", clan.getClanName()));
                }
				break;
			case 1:
				if(management == null){
					joinner.sendPackets(String.format("%s 혈맹은 현재 가입 불가 상태입니다.", clan.getClanName()));
					return;
				}
				management.setTempID(joinner.getId());
				SC_MessageYN.send(management, 97, joinner.getName());
				joinner.sendPackets(String.format("%s 혈맹 가입 승인을 기다리는 중입니다.", clan.getClanName()));
				break;
			default:
				joinner.sendPackets(String.format("%s 혈맹은 현재 가입 불가 상태입니다.", clan.getClanName()));
				break;
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public byte[] serialize(){
		BytesOutputStream s = new BytesOutputStream(256);
		byte[] 				b = null;
		try{
			L1Clan clan = get_clan();
			
			s.write(0x08);				// point
			s.writePoint(getX(), getY());
			s.write(0x10);				// objectnumber
			s.writeBit(getId());
			s.write(0x18); 				// objectsprite
			s.writeBit(_gfx == 0 ? 6094 : 6080);
			//s.writeBit(get_gfx());
			s.write(0x20); 				// action
			s.writeBit(0x00);
			s.write(0x28); 				// direction
			s.writeBit(get_heading());
			s.write(0x30); 				// lightRadius
			s.writeBit(15);
			s.write(0x38); 				// objectcount
			s.write(1);
			s.write(0x40); 				// alignment(lawful)
			s.writeBit(get_lawful());
			s.write(0x4A);				// desc
			if(clan == null){
				s.writeS2(String.format("무인가입^%s", get_name()));
			}else{
				s.writeS2(String.format("혈맹: %s^군주 <%s>", clan.getClanName(), get_name()));
			}
			s.write(0x52); 				// title
			s.writeS2(get_title());
			s.write(0x58);	 			// speed data
			s.write(0x00);
			s.write(0x60); 				// emotion
			s.write(0x00);
			s.write(0x68);				// drunken
			s.writeBit(0x00);
			s.write(0x70); 				// isghost
			s.write(0x00);
			s.write(0x78); 				// isparalyzed
			s.write(0x00);
			s.writeBit(0x80);			// isuser
			s.write(0x00);
			s.writeBit(0x88);			// isinvisible
			s.write(0x00);
			s.writeBit(0x90); 			// ispoisoned
			s.write(0x00);
			s.writeBit(0x98);			// emblemid
			s.write(0x00);
			s.writeBit(0xA2); 			// pledgename
			s.write(0x00);				
			s.writeBit(0xAA); 			// mastername
			s.write(0x00);
			s.writeBit(0xB0); 			// altitude
			s.write(0x00);
			s.writeBit(0xB8);			// hitratio
			s.writeBit(-1);
			s.writeBit(0xC0); 			// safelevel
			s.write(0x01);
			s.writeBit(0xCA);			// shop title.
			s.writeBit(0x00);
			s.writeBit(0xD0); 		// weapon sprite
			s.writeBit(-1);
			s.writeBit(0xD8); 		// couplestate
			s.writeB(false);
			s.writeBit(0xE0); 		// boundarylevelindex
			s.write(0x01);
			s.writeBit(0xE8); 		// weakelemental
			s.writeBit(0x00);
			s.writeBit(0xF0);		// manaratio
			s.writeBit(-1);
			s.writeBit(0xF8);		// botindex
			s.writeBit(0x00);
			s.writeBit(0x100); 		// home server no
			s.write(0x00);
			s.writeBit(0x108); 		// team_id
			s.writeBit(0x00);
			s.writeBit(0x110); 		// dialog_radius
			s.write(0x05);
			s.writeBit(0x118); 		// speed_value_flag
			s.write(0x00);
			s.writeBit(0x120);		// second_speed_type
			s.write(0x00);
			s.writeH(0x00);
			b = s.toArray();
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(s != null){
				s.close();
				s.dispose();
				s = null;
			}
		}
		return b;
	}
	
	private static int _instanceType = -1;
	@Override
	public int getL1Type(){
		return _instanceType == -1 ? _instanceType = super.getL1Type() | L1Type.L1TYPE_CLANJOIN : _instanceType;		
	}
}
