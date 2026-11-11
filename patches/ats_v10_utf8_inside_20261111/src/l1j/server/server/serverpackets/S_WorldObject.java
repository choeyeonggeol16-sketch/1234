package l1j.server.server.serverpackets;

import l1j.server.server.ActionCodes;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.DoorInstance;
import l1j.server.server.model.Instance.FieldObjectInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;

public class S_WorldObject extends ServerBasePacket {

	private static final int STATUS_POISON = 1;
	private static final int STATUS_INVISIBLE = 2;
	private static final int STATUS_PC = 4;
	private static final int STATUS_FREEZE = 8;
	private static final int STATUS_BRAVE = 16;
	private static final int STATUS_ELFBRAVE = 32;
	private static final int STATUS_FASTMOVABLE = 64;
	private static final int STATUS_GHOST = 128; 
	private static final int STATUS_SLOWS = 256; 

	// 문
	public static S_WorldObject door(DoorInstance door) {
		S_WorldObject s = new S_WorldObject();
		s.writeH(door.getX());
		s.writeH(door.getY());
		s.writeD(door.getId());
		s.writeH(door.getCurrentSpriteId());
		int doorStatus = door.getStatus();
		int openStatus = door.getOpenStatus();
		if (door.isDead()) {
			s.writeC(doorStatus);
		} else if (openStatus == ActionCodes.ACTION_Open) {
			s.writeC(openStatus);
		} else if (door.getMaxHp() > 1 && doorStatus != 0) {
			s.writeC(doorStatus);
		} else {
			s.writeC(openStatus);
		}
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeD(1);
		s.writeH(0);
		s.writeS(null);
		s.writeS(null);
		int status = 0;
		if (door.getPoison() != null) {
			if (door.getPoison().getEffectId() == 1) {
				status |= STATUS_POISON;
			}
		}
		s.writeC(status);
		s.writeD(0);
		s.writeS(null);
		s.writeS(null);
		s.writeC(0);
		s.writeC(0xFF);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0xFF);
		s.writeC(0xFF);		
		return s;		
	}

	// 펫 
	public static S_WorldObject pet(PetInstance pet, L1PcInstance pc) {
		S_WorldObject s = new S_WorldObject();
		s.writeH(pet.getX());
		s.writeH(pet.getY());
		s.writeD(pet.getId());
		s.writeH(pet.getCurrentSpriteId()); // SpriteID in List.spr
		s.writeC(pet.getStatus()); // Modes in List.spr
		s.writeC(pet.getHeading());
		s.writeC(pet.getLightSize()); // (Bright) - 0~15
		s.writeC(pet.getMoveSpeed()); // 스피드 - 0:normal, 1:fast,
		// 2:slow
		s.writeD(pet.getExp());
		s.writeH(pet.getTempLawful());
		s.writeS(pet.getName());
		s.writeS(pet.getTitle());
		s.writeC(0); 
		s.writeD(0); // ??
		s.writeS(null); // ??
		s.writeS(pet.getMaster() != null ? pet.getMaster().getName() : "");
		s.writeC(0); // ??
		// HP의 퍼센트
		if (pet.getMaster() != null && pet.getMaster().getId() == pc.getId()) {
			s.writeC(100 * pet.getCurrentHp() / pet.getMaxHp());
		} else {
			s.writeC(0xFF);
		}
		s.writeC(0);
		s.writeC(pet.getLevel()); // PC = 0, Mon = Lv
		s.writeC(0);
		s.writeC(0xFF);
		s.writeC(0xFF);
		return s;

	}

	// 서먼 
	public static S_WorldObject summon(SummonInstance pet, L1PcInstance pc) {
		S_WorldObject s = new S_WorldObject();
		s.writeH(pet.getX());
		s.writeH(pet.getY());
		s.writeD(pet.getId());
		s.writeH(pet.getCurrentSpriteId()); // SpriteID in List.spr
		s.writeC(pet.getStatus()); // Modes in List.spr
		s.writeC(pet.getHeading());
		s.writeC(pet.getLightSize()); // (Bright) - 0~15
		s.writeC(pet.getMoveSpeed()); // 스피드 - 0:normal, 1:fast, // 2:slow
		s.writeD(0);
		s.writeH(0);
		s.writeS(pet.getNameId());
		s.writeS(pet.getTitle());
		s.writeC(0);
		s.writeD(0); // ??
		s.writeS(null); // ??
		if (pet.isExsistMaster()) {
			s.writeS(pet.getMaster().getName());
		} else {
			s.writeS("");
		}
		s.writeC(0); // ??
		if (pet.getMaster() != null
				&& pet.getMaster().getId() == pc.getId()) {
			int percent = pet.getMaxHp() != 0 ? 100 * pet.getCurrentHp()
					/ pet.getMaxHp() : 100;
					s.writeC(percent);
		} else {
			s.writeC(0xFF);
		}
		s.writeC(0);
		s.writeC(pet.getLevel()); // PC = 0, Mon = Lv
		s.writeC(0);
		s.writeC(0xFF);
		s.writeC(0xFF);		
		return s;


	}
	// 인형
	public static S_WorldObject doll(DollInstance doll, L1PcInstance pc) {
		S_WorldObject s = new S_WorldObject();
		s.writeH(doll.getX());
		s.writeH(doll.getY());
		s.writeD(doll.getId());
		s.writeH(doll.getCurrentSpriteId()); // SpriteID in List.spr
		s.writeC(doll.getStatus()); // Modes in List.spr
		s.writeC(doll.getHeading());
		s.writeC(doll.getLightSize()); // (Bright) - 0~15
		s.writeC(doll.getMoveSpeed()); // ⅩΥ【Ι - 0:normal,1:fast,2:slow
		s.writeD(0);
		s.writeH(0);
		s.writeS(doll.getNameId());
		s.writeS(doll.getTitle());
		s.writeC(0);
		s.writeD(0); // ??
		s.writeS(null); // ??
		s.writeS(doll.getMaster() != null ? doll.getMaster().getName() : "");
		s.writeC(0); // ??
		s.writeC(0xFF);
		s.writeC(0);
		s.writeC(doll.getLevel()); // PC = 0, Mon = Lv
		s.writeC(0);
		s.writeC(0xFF);
		s.writeC(0xFF);	
		return s;
	}

	// 엔피시 
	public static S_WorldObject npc(L1NpcInstance npc) {
		S_WorldObject s = new S_WorldObject();
		s.writeH(npc.getX());
		s.writeH(npc.getY());
		s.writeD(npc.getId());
		if (npc.getCurrentSpriteId() == 0) {
			s.writeH(npc.getCurrentSpriteId());
		} else {
			s.writeH(npc.getCurrentSpriteId());
		}
		if (npc.getNpcTemplate().is_doppel() && npc.getCurrentSpriteId() != 31) { // 슬라임의 모습을 하고 있지 않으면 돕펠
			s.writeC(4); // 장검
		} else if (npc.getCurrentSpriteId() == 51) { // 창 경비병
			s.writeC (24);
		} else if (npc.getCurrentSpriteId() == 816) { // 오성 오크스카우트
			s.writeC (20);
		} else {
			s.writeC(npc.getStatus());
		}
		s.writeC(npc.getHeading());
		s.writeC(npc.getLight().getChaLightSize());
		s.writeC(npc.getMoveSpeed());
		s.writeD(npc.getExp());
		s.writeH(npc.getTempLawful());	
		s.writeS(npc.getNameId());
		//System.out.println(npc.getNameId());
		if (npc instanceof FieldObjectInstance) { // SIC의 벽자, 간판 등
			L1NpcTalkData talkdata = NPCTalkDataTable.getInstance().getTemplate(npc.getNpcTemplate().get_npcId());
			if (talkdata != null) {
				s.writeS(talkdata.getNormalAction()); // 타이틀이 HTML명으로서 해석된다
			} else {
				s.writeS(null);
			}
		} else {
			s.writeS(npc.getTitle());

		} 
		/**
		 * 세팅 - 0:mob,item(atk pointer), 1:poisoned(), 2:invisable(), 4:pc,
		 * 8:cursed(), 16:brave(), 32:??, 64:??(??), 128:invisable but name
		 */
		int status = 0;
		if (npc.getPoison() != null) { 
			if (npc.getPoison().getEffectId() == 1) {
				status |= STATUS_POISON;
			}
		}
		if (npc.getNpcTemplate().is_doppel()) {
			// PC속성이라면 에바의 축복을 건네줄 수 없기 때문에 WIZ 퀘스트의 돕펠은 예외
			if (npc.getNpcTemplate().get_npcId() != 81069) {
				status |= STATUS_PC;
			}
		}
		if (npc.getNpcTemplate().get_npcId() != 145682) {
			if(npc.isParal()) {
				status |= STATUS_FREEZE;	
			}
		}
		s.writeC(status);
		s.writeD(0); // 0이외에 하면(자) C_27이 난다
		s.writeS(null);
		s.writeS(null); // 마스터명?
		s.writeC(0);
		s.writeC(0xFF); // HP
		s.writeC(0);
		s.writeC(npc.getLevel());
		if (((npc instanceof L1NpcShopInstance)) && (((L1NpcShopInstance) npc).getState() == 1)) {
			s.writeByte(((L1NpcShopInstance) npc).getShopName().getBytes());
		}
		s.writeC(255);
		s.writeC(255);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0xFF);
		s.writeH(0); 	
		return s;

	}

	// 자신
	public static S_WorldObject in(L1PcInstance pc) {
		int status = STATUS_PC; 

		S_WorldObject s = new S_WorldObject();
		s.writeH(pc.getX());
		s.writeH(pc.getY());
		s.writeD(pc.getId());
		s.writeH(pc.getCurrentSpriteId());
		if(pc.isDead()) {
			s.writeC(ActionCodes.ACTION_Die);
		} else if(pc.isPrivateShop()) {
			s.writeC(ActionCodes.ACTION_Shop);
		} else if(pc.isFishing()) {
			s.writeC(ActionCodes.ACTION_Fishing);
		} else {
			s.writeC(pc.getCurrentWeapon());
		}
		s.writeC(pc.getHeading());
		s.writeC(pc.getLight().getOwnLightSize());
		s.writeC(pc.getMoveSpeed());
		s.writeD(pc.getExp());
		s.writeH(pc.getLawful());
		s.writeS(pc.getName());
		s.writeS(pc.getTitle());
		s.writeC(pc.isInvisble() || pc.isGmInvis() 
				? status |= STATUS_INVISIBLE
				: pc.isBrave() 
				? status |= STATUS_BRAVE
				: pc.isElfBrave()
				? status |= STATUS_ELFBRAVE
				: pc.isFastMovable()
				? status |= STATUS_FASTMOVABLE
				: pc.isGhost()
				? status |= STATUS_GHOST
				: pc.isParal()
				? status |= STATUS_FREEZE
				: pc.isSlows() || pc.isSlow()
				? status |= STATUS_SLOWS
				: status);	
		s.writeD(pc.getClanid() > 0 ? pc.getClan().getEmblemId() : 0);	
		s.writeS(pc.getClanname());
		s.writeS(null); // 펫호팅?
		s.writeC(0); // ?
		s.writeC(-1);
		s.writeC(pc.isDragonPearl() ? 0x08 : 0x00);
		s.writeC(0); // PC = 0, Mon = Lv
		s.writeC(0); // ?
		s.writeC(0xFF);
		s.writeC(0xFF);
		return s;	
	}

	// 타겟
	public static S_WorldObject out(L1PcInstance pc) {
		int status = STATUS_PC;

		S_WorldObject s = new S_WorldObject();
		s.writeH(pc.getX());
		s.writeH(pc.getY());
		s.writeD(pc.getId());
		s.writeH(pc.getCurrentSpriteId());
		if(pc.isDead()) {
			s.writeC(ActionCodes.ACTION_Die);
		} else if(pc.isPrivateShop()) {
			s.writeC(ActionCodes.ACTION_Shop);
		} else if(pc.isFishing()) {
			s.writeC(ActionCodes.ACTION_Fishing);
		} else {
			s.writeC(pc.getCurrentWeapon());
		}
		s.writeC(pc.getHeading());
		s.writeC(pc.getLight().getChaLightSize());
		s.writeC(pc.getMoveSpeed());
		s.writeD(0x0000); // exp
		s.writeH(pc.getLawful());
		s.writeS(pc.getName());
		s.writeS(pc.getTitle());
		s.writeC(pc.getPoison() != null && pc.getPoison().getEffectId() == 1
				? status |= STATUS_POISON
				: pc.isInvisble() || pc.isGmInvis() 
				? status |= STATUS_INVISIBLE
				: pc.isBrave() 
				? status |= STATUS_BRAVE
				: pc.isElfBrave()
				? status |= STATUS_ELFBRAVE
				: pc.isFastMovable()
				? status |= STATUS_FASTMOVABLE
				: pc.isGhost()
				? status |= STATUS_GHOST
				: pc.isParal()
				? status |= STATUS_FREEZE
				: pc.isSlows() || pc.isSlow()
				? status |= STATUS_SLOWS
				: status);	
		s.writeD(pc.getClanid() > 0 ? pc.getClan().getEmblemId() : 0); 
		s.writeS(pc.getClanname());
		s.writeS(null); // 펫호팅?
		s.writeC(0); // ?
		s.writeC(-1);
		s.writeC(pc.isDragonPearl() ? 0x08 : 0x00);
		s.writeC(0); // PC = 0, Mon = Lv
		s.writeC(0); // ?
		s.writeC(0xFF);
		s.writeC(0xFF); 
		return s;	
	}

	private S_WorldObject() {
		writeC(Opcodes.S_OPCODE_CHARPACK);
	}

	@Override
	public byte[] getContent() {
		// TODO 자동 생성된 메소드 스텁
		return getBytes();
	}
}
