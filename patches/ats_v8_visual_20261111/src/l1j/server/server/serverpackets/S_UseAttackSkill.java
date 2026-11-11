package l1j.server.server.serverpackets;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.commons;
import l1j.server.server.ActionCodes;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;

public class S_UseAttackSkill extends ServerBasePacket {

	private static AtomicInteger _sequentialNumber = new AtomicInteger(0);
	
	public S_UseAttackSkill(L1Character cha, int targetobj, int spellgfx, int x, int y, int actionId, boolean motion, int criticar) {
		buildPacket(cha, targetobj, spellgfx, x, y, actionId, 0, motion, criticar);
	}
	
	public static S_UseAttackSkill get(L1Character cha, int targetobj, int spellgfx, int x, int y, int actionId, boolean Motion) {
		if (cha instanceof L1PcInstance) {
			// 그림자계 변신중에 공격 마법을 사용하면(자) 클라이언트가 떨어지기 (위해)때문에 잠정 대응
			if (cha.hasSkillEffect(SkillId.셰이프_체인지) && actionId == ActionCodes.ACTION_SkillAttack) { 
				int tempchargfx = cha.getCurrentSpriteId();
				if (tempchargfx == 5727 || tempchargfx == 5730) {
					actionId = ActionCodes.ACTION_SkillBuff;
				} else if (tempchargfx == 5733 || tempchargfx == 5736) {
					actionId = ActionCodes.ACTION_Attack;
				}
			}
		}
		if (cha.getCurrentSpriteId() == 4013) {
			actionId = ActionCodes.ACTION_Attack;
		}	
		// 특정 변신이 액션값이 다를경우 처리방법.
		if (actionId == 17) {
			switch (cha.getCurrentSpriteId()) {
			case 13153:
			case 11375:
				actionId = 15;
				break;
			}
		}
		S_UseAttackSkill s = new S_UseAttackSkill();
		int newheading = commons.calcheading(cha.getX(), cha.getY(), x, y);
		cha.setHeading(newheading);
		s.writeC(actionId);
		s.writeD(Motion ? cha.getId() : 0);
		s.writeD(targetobj);
		s.writeC(0);
		s.writeC(0);
		s.writeC(newheading);
		s.writeD(_sequentialNumber.incrementAndGet());
		s.writeH(spellgfx);
		s.writeC(6);
		s.writeH(cha.getX());
		s.writeH(cha.getY());
		s.writeH(x);
		s.writeH(y);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		return s;
	}
    // 마법인형
	public static S_UseAttackSkill MagicDoll(DollInstance doll, int targetobj, int spellgfx, int x, int y, int actionId, int isHit, boolean Motion) {
		S_UseAttackSkill s = new S_UseAttackSkill();
		int newheading = commons.calcheading(doll.getX(), doll.getY(), x, y);
		doll.setHeading(newheading);
		s.writeC(actionId);
		s.writeD(Motion ? doll.getId() : 0);
		s.writeD(targetobj);
		s.writeC(isHit > 0 ? 6 : 0);
		s.writeC(0);
		s.writeC(newheading);
		s.writeD(_sequentialNumber.incrementAndGet());
		s.writeH(spellgfx);
		s.writeC(6);
		s.writeH(doll.getX());
		s.writeH(doll.getY());
		s.writeH(x);
		s.writeH(y);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		return s;
	}
    // 마법검 원거리 용
	public static S_UseAttackSkill MagicArrow(L1Character cha, int targetobj, int spellgfx, int x, int y, int actionId) {
		S_UseAttackSkill s = new S_UseAttackSkill();
		int newheading = commons.calcheading(cha.getX(), cha.getY(), x, y);
		cha.setHeading(newheading);
		s.writeC(actionId);
		s.writeD(0);
		s.writeD(targetobj);
		s.writeC(0);
		s.writeC(0);
		s.writeC(newheading);
		s.writeD(_sequentialNumber.incrementAndGet());
		s.writeH(spellgfx);
		s.writeC(6);
		s.writeH(cha.getX());
		s.writeH(cha.getY());
		s.writeH(x);
		s.writeH(y);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		return s;
	}
	
	public static S_UseAttackSkill gett(L1Character cha, int targetobj, int spellgfx, int x, int y, int actionId, int isHit, boolean Motion) {
		if (cha instanceof L1PcInstance) {
			// 그림자계 변신중에 공격 마법을 사용하면(자) 클라이언트가 떨어지기 (위해)때문에 잠정 대응
			if (cha.hasSkillEffect(SkillId.셰이프_체인지) && actionId == ActionCodes.ACTION_SkillAttack) { 
				int tempchargfx = cha.getCurrentSpriteId();
				if (tempchargfx == 5727 || tempchargfx == 5730) {
					actionId = ActionCodes.ACTION_SkillBuff;
				} else if (tempchargfx == 5733 || tempchargfx == 5736) {
					actionId = ActionCodes.ACTION_Attack;
				}
			}
		}
		// 화령의 주인이 디폴트라면 공격 마법의 그래픽이 발생하지 않기 때문에 강제 치환
		// 어딘가 별개로 관리하는 것이 좋아?
		if (cha.getCurrentSpriteId() == 4013) {
			actionId = ActionCodes.ACTION_Attack;
		}	
		// 특정 변신이 액션값이 다를경우 처리방법.
		if (actionId == 17) {
			switch (cha.getCurrentSpriteId()) {
			case 13153:
			case 11375:
				actionId = 15;
				break;
			}
		}
		S_UseAttackSkill s = new S_UseAttackSkill();
		int newheading = commons.calcheading(cha.getX(), cha.getY(), x, y);
		cha.setHeading(newheading);
		s.writeC(actionId);
		s.writeD(Motion ? cha.getId() : 0);
		s.writeD(targetobj);
		s.writeC(isHit > 0 ? 6 : 0);
		s.writeC(0);
		s.writeC(newheading);
		s.writeD(_sequentialNumber.incrementAndGet()); // 번호가 겹치지 않게 보낸다
		s.writeH(spellgfx);
		s.writeC(6); // 타켓지종:6, 범위&타켓지종:8, 범위:0
		s.writeH(cha.getX());
		s.writeH(cha.getY());
		s.writeH(x);
		s.writeH(y);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		return s;
	}
	
	private void buildPacket(L1Character cha, int targetobj, int spellgfx,
			int x, int y, int actionId, int isHit, boolean withCastMotion, int criticar) {
		if (criticar == 1) {
			if (cha instanceof L1PcInstance) {
				// 그림자계 변신중에 공격 마법을 사용하면(자) 클라이언트가 떨어지기 (위해)때문에 잠정 대응
				if (cha.hasSkillEffect(SkillId.셰이프_체인지)
						&& actionId == ActionCodes.ACTION_SkillAttack) {
					int tempchargfx = cha.getCurrentSpriteId();
					if (tempchargfx == 5727 || tempchargfx == 5730) {
						actionId = ActionCodes.ACTION_SkillBuff;
					} else if (tempchargfx == 5733 || tempchargfx == 5736) {
						// 보조 마법 모션으로 하면(자) 공격 마법의 그래픽과
						// 대상에의 데미지 모션이 발생하지 않게 되기 (위해)때문에
						// 공격 모션으로 대용
						actionId = ActionCodes.ACTION_Attack;
					}
				}
			}
			// 화령의 주인이 디폴트라면 공격 마법의 그래픽이 발생하지 않기 때문에 강제 치환
			// 어딘가 별개로 관리하는 것이 좋아?
			if (cha.getCurrentSpriteId() == 4013) {
				actionId = ActionCodes.ACTION_Attack;
			}
			int autonum = _sequentialNumber.incrementAndGet();
	
			int newheading = calcheading(cha.getX(), cha.getY(), x, y);
			cha.setHeading(newheading);
			writeC(Opcodes.S_OPCODE_ATTACKPACKET);
			writeC(actionId);
			writeD(withCastMotion ? cha.getId() : 0);
			writeD(targetobj);
			writeC(isHit);
			writeC(0x00);
			writeC(newheading);
			writeD(autonum); // 번호가 겹치지 않게 보낸다
			writeH(spellgfx);
			writeC(0); // 뱀파-0 , 에볼- 6 타켓지종:6, 범위&타켓지종:8, 범위:0
			if (cha.getHeading() == 0) {
				writeH(cha.getX());
				writeH(cha.getY() -1);
			} else if (cha.getHeading() == 1) {
				writeH(cha.getX() +1);
				writeH(cha.getY() -1);
			} else if (cha.getHeading() == 2) {
				writeH(cha.getX() +1);
				writeH(cha.getY());
			} else if (cha.getHeading() == 3) {
				writeH(cha.getX() +1);
				writeH(cha.getY() +1);
			} else if (cha.getHeading() == 4) {
				writeH(cha.getX());
				writeH(cha.getY() +1);
			} else if (cha.getHeading() == 5) {
				writeH(cha.getX() -1);
				writeH(cha.getY() +1);
			} else if (cha.getHeading() == 6) {
				writeH(cha.getX() -1);
				writeH(cha.getY());
			} else if (cha.getHeading() == 7) {
				writeH(cha.getX() -1);
				writeH(cha.getY() -1);
			}
			writeH(x);
			writeH(y);
			writeD(0);
		} else if (criticar == 2) {
			if (cha instanceof L1PcInstance) {
				// 그림자계 변신중에 공격 마법을 사용하면(자) 클라이언트가 떨어지기 (위해)때문에 잠정 대응
				if (cha.hasSkillEffect(SkillId.셰이프_체인지)
						&& actionId == ActionCodes.ACTION_SkillAttack) {
					int tempchargfx = cha.getCurrentSpriteId();
					if (tempchargfx == 5727 || tempchargfx == 5730) {
						actionId = ActionCodes.ACTION_SkillBuff;
					} else if (tempchargfx == 5733 || tempchargfx == 5736) {
						// 보조 마법 모션으로 하면(자) 공격 마법의 그래픽과
						// 대상에의 데미지 모션이 발생하지 않게 되기 (위해)때문에
						// 공격 모션으로 대용
						actionId = ActionCodes.ACTION_Attack;
					}
				}
			}
			// 화령의 주인이 디폴트라면 공격 마법의 그래픽이 발생하지 않기 때문에 강제 치환
			// 어딘가 별개로 관리하는 것이 좋아?
			if (cha.getCurrentSpriteId() == 4013) {
				actionId = ActionCodes.ACTION_Attack;
			}
			int autonum = _sequentialNumber.incrementAndGet();
	
			int newheading = calcheading(cha.getX(), cha.getY(), x, y);
			cha.setHeading(newheading);
			writeC(Opcodes.S_OPCODE_ATTACKPACKET);
			writeC(actionId);
			writeD(withCastMotion ? cha.getId() : 0);
			writeD(targetobj);
			writeC(isHit);
			writeC(0x00);
			if (cha.getHeading() == 0) {
				writeC(newheading +4);
			} else if (cha.getHeading() == 1) {
				writeC(newheading +4);
			} else if (cha.getHeading() == 2) {
				writeC(newheading +4);
			} else if (cha.getHeading() == 3) {
				writeC(newheading +4);
			} else if (cha.getHeading() == 4) {
				writeC(newheading -4);
			} else if (cha.getHeading() == 5) {
				writeC(newheading -4);
			} else if (cha.getHeading() == 6) {
				writeC(newheading -4);
			} else if (cha.getHeading() == 7) {
				writeC(newheading -4);
			}
			writeD(autonum); // 번호가 겹치지 않게 보낸다
			writeH(spellgfx);
			writeC(0); // 뱀파-0 , 에볼- 6 타켓지종:6, 범위&타켓지종:8, 범위:0
			writeH(cha.getX());
			writeH(cha.getY());
			writeH(x);
			writeH(y);
			writeD(0);
		}
	}
	
	private static int calcheading(int myx, int myy, int tx, int ty) {
		int newheading = 0;
		if (tx > myx && ty > myy) {
			newheading = 3;
		}
		if (tx < myx && ty < myy) {
			newheading = 7;
		}
		if (tx > myx && ty == myy) {
			newheading = 2;
		}
		if (tx < myx && ty == myy) {
			newheading = 6;
		}
		if (tx == myx && ty < myy) {
			newheading = 0;
		}
		if (tx == myx && ty > myy) {
			newheading = 4;
		}
		if (tx < myx && ty > myy) {
			newheading = 5;
		}
		if (tx > myx && ty < myy) {
			newheading = 1;
		}
		return newheading;
	}
	
    private S_UseAttackSkill() {
    	writeC(Opcodes.S_OPCODE_ATTACKPACKET);
    }
	@Override
	public byte[] getContent() throws IOException {
		return getBytes();
	}
}