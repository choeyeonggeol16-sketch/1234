package l1j.server.CharacterAction.Attack;

import java.io.IOException;

import l1j.server.commons;
import l1j.server.ActionCodes.EActionCodes;
import l1j.server.ActionCodes.SpriteInformation;
import l1j.server.CharacterAction.AbstractActionHandler;
import l1j.server.CharacterAction.Executor.CharacterActionExecutor;
import l1j.server.Template.PacketHelper.PacketParser;
import l1j.server.configure.Config;
import l1j.server.server.clientpackets.ClientBasePacket;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_AttackStatus;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_UseArrowSkill;
import l1j.server.server.utils.EffectUtil;
import l1j.server.server.utils.SkillUtil;

public class AttackActionHandler extends AbstractActionHandler implements PacketParser {
	protected int tId;
	protected int x;
	protected int y;

	protected L1Character cha;

	@Override
	public void dispose() {
		super.dispose();
		cha = null;
	}

	@Override
	public void parse(L1PcInstance owner, ClientBasePacket pck) {
		this.owner = owner;
		tId = pck.readD();
		x = pck.readH();
		y = pck.readH();
	}

	@Override
	public void doWork() {
		
		if(owner.hasSkillEffect(SkillId.메디테이션)) {
			owner.removeSkillEffect(SkillId.메디테이션);
		}
		owner.setRegenState(L1PcInstance.STATE_ATTACK);
		register();
	}

	@Override
	public void handle() {
		
		if (!validation()) {
			owner.setRegenState(L1PcInstance.STATE_NONE);
			return;
		}	
		if (Config.ServerInfo.ATTACK_SPEED_PARSE) {
			if (owner.isGm()) {
				long current_frame = System.currentTimeMillis() - owner.getLastAttackActionMillis();
				owner.sendPackets(String.format("spr_frame : %d , real_frame : %d", getInterval(), current_frame));
				owner.setLastAttackActionMillis(System.currentTimeMillis());
			}
		}
		SkillUtil.cancelBarrier(owner);		
		
		owner.delInvis(); 
		if (cha != null && !cha.isDead() && tId != 0) {
			cha.onAction(owner);
			
			
		
		} else { // 허공 칼질 및 활 모션 구현 

			int weaponId = 0;
			int weaponType = 0;
			ItemInstance weapon = owner.getWeapon();
			ItemInstance arrow = null;
			ItemInstance sting = null;
			if (weapon != null) {
				weaponId = weapon.getItem().getItemId();
				weaponType = weapon.getItem().getType1();
				if (weaponType == 20) {
					arrow = owner.getInventory().getArrow();
				}
				if (weaponType == 62) {
					sting = owner.getInventory().getSting();
				}
			}
			owner.setHeading(owner.targetDirection(x, y)); // 방향세트
			if (weaponType == 20 && (weaponId == ItemId.사이하_활 || weaponId == ItemId.가이아_격노 || arrow != null)) {
				calcOrbit(owner.getX(), owner.getY(), owner.getHeading());
				if (arrow != null) {
					int sprId = owner.getCurrentSpriteId(), arrowEffect = 0;		
					switch (sprId) {
					case 7968: case 7967: arrowEffect = EffectUtil.화살_이미지_1; break;
					case 8842: case 8900: case 16002: arrowEffect = EffectUtil.화살_이미지_2; break;
					case 8845: case 8913: case 16074: arrowEffect = EffectUtil.화살_이미지_4; break;
					case 15814: arrowEffect = EffectUtil.화살_이미지_5; break;
					case 13635: arrowEffect = EffectUtil.화살_이미지_6; break;
					case 13631: arrowEffect = EffectUtil.화살_이미지_7; break;
					case 17535: arrowEffect = EffectUtil.화살_이미지_8; break;
					case 18352: arrowEffect = EffectUtil.화살_이미지_9; break; //달의 질리언
					case 18330: arrowEffect = EffectUtil.화살_이미지_10; break; //가드리아
					case 18320: arrowEffect = EffectUtil.화살_이미지_1; break; //기르타스
					default: arrowEffect = EffectUtil.화살_이미지_3; break; }
					S_UseArrowSkill ua = S_UseArrowSkill.get(owner, 0, arrowEffect, x, y, false);
					owner.sendPackets(ua);
					Broadcaster.broadcastPacket(owner, ua);				
					owner.getInventory().removeItem(arrow, 1);
				} else if (weaponId == ItemId.사이하_활 || weaponId == ItemId.가이아_격노) {
					S_UseArrowSkill ua = S_UseArrowSkill.get(owner, 0, 2349, x, y, false);
					owner.sendPackets(ua);
					Broadcaster.broadcastPacket(owner, ua);	
				} 
			} else if (weaponType == 62 && sting != null) {
				calcOrbit(owner.getX(), owner.getY(), owner.getHeading());
				int sprId = owner.getCurrentSpriteId(), stingEffect = 0;
				switch (sprId) {
				case 7968: case 7967: stingEffect = EffectUtil.화살_이미지_1; break;
				case 8842: case 8900: case 16002: stingEffect = EffectUtil.화살_이미지_2; break;
				case 8845: case 8913: case 16074: stingEffect = EffectUtil.화살_이미지_4; break;
				case 15814: stingEffect = EffectUtil.화살_이미지_5; break;
				case 13635: stingEffect = EffectUtil.화살_이미지_6; break;
				case 13631: stingEffect = EffectUtil.화살_이미지_7; break;
				case 17535: stingEffect = EffectUtil.화살_이미지_8; break;
				case 18352: stingEffect = EffectUtil.화살_이미지_9; break; //달의 질리언
				case 18330: stingEffect = EffectUtil.화살_이미지_10; break; //가드리아
				case 18320: stingEffect = EffectUtil.화살_이미지_1; break; //기르타스
				default: stingEffect = EffectUtil.스팅_이미지; break; }
				S_UseArrowSkill ua = S_UseArrowSkill.get(owner, 0, stingEffect, x, y, false);
				owner.sendPackets(ua);
				Broadcaster.broadcastPacket(owner, ua);
				owner.getInventory().removeItem(sting, 1);
			} else {
				// 근거리 무기 땅 공격
				if (cha == null) {
					S_AttackStatus s = S_AttackStatus.get(owner, 0, owner.getCurrentWeapon() + 1);
					owner.sendPackets(s);
					Broadcaster.broadcastPacket(owner, s);
				}			
			}
		}
	}

	@Override
	public boolean validation() {
		if (owner == null) {
			return false;
		}
		if (Config.STANDBY_SERVER) {
			return false;
		}
		if (owner.isGmInvis() || commons.isNonAction(owner) 
				|| owner.isDead() || owner.getTeleport() 
				|| (owner.isInvisble()) || owner.isInvisDelay() || owner.isGhost()) {
			return false;
		}	
		if (owner.getInventory().getWeight100() > 82) {
			owner.sendPackets(new S_ServerMessage(110));
			return false;
		}	
		int ox = owner.getX();
		int oy = owner.getY();
		int oh = owner.getHeading(); 

		L1Object obj = L1World.getInstance().findObject(tId);

		if (obj != null && obj instanceof L1Character) {
			if (obj instanceof L1NpcInstance) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				if (npc.getHiddenStatus() != 0) {
					return false;
				}
			}
			this.cha = (L1Character) obj;
			SpriteInformation sInfo = cha.getCurrentSprite();
			int cx = Math.abs(ox - x);
			int cy = Math.abs(oy - y);
			int r = owner.getAttackRang();
			int h = commons.calcheading(ox, oy, x, y); 

			if (r <= 3) {
				if (cx > sInfo.getWidth() + r || cy > sInfo.getHeight() + r) {
					if (System.currentTimeMillis() - owner.getLastMoveActionMillis() > 1000) {
						return false;
					}
					int diff_h = Math.abs(commons.calcheading(ox, oy, cha.getX(), cha.getY()) - oh);
					if (diff_h <= 1 && cx < sInfo.getWidth() + r + 5 && cy < sInfo.getHeight() + r + 5) {
						return true;
					}
					return false;
				}
			}

			if (!owner.getMap().isUserPassable(ox, oy, h)) {
				return false;
			}

			if (!owner.glanceCheck(x, y)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int getRegistIndex() {
		return CharacterActionExecutor.ACTION_IDX_ATTACK;
	}

	@Override
	public long getInterval() {
		return owner.getCurrentSpriteInterval(EActionCodes.fromInt(owner.getCurrentWeapon() + 1));
	} 

	private void calcOrbit(int cX, int cY, int head) {
		float disX = Math.abs(cX - x);
		float disY = Math.abs(cY - y);
		float dis = Math.max(disX, disY);
		float avgX = 0;
		float avgY = 0;

		if (dis == 0) {
			avgX = commons.HEADING_TABLE_X[head];
			avgY = commons.HEADING_TABLE_Y[head];
		} else {
			avgX = disX / dis;
			avgY = disY / dis;
		}
		int addX = (int) Math.floor((avgX * 15) + 0.59f);
		int addY = (int) Math.floor((avgY * 15) + 0.59f);
		if (cX > x) {
			addX *= -1;
		}
		if (cY > y) {
			addY *= -1;
		}
		x = x + addX;
		y = y + addY;
	}
}
