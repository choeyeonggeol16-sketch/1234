package l1j.server.server.model;

import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_Light;

public class Light {
	private int _chaLightSize = 0;
	private int _ownLightSize = 0;
	private L1Character character = null;

	public void dispose() {
		this.character = null;
	}

	public Light(L1Character cha) {
		character = cha;
	}

	public int getChaLightSize() {
		if (character.isInvisble()) {
			return 0;
		}
		return _chaLightSize;
	}

	public void setChaLightSize(int i) {
		_chaLightSize = i;
	}

	public int getOwnLightSize() {
		return _ownLightSize;
	}

	public void setOwnLightSize(int i) {
		_ownLightSize = i;
	}

	public void turnOnOffLight() {
		if (character == null) {
			return;
		}
		int lightSize = 0;
		if (character instanceof L1NpcInstance) {
			L1NpcInstance npc = (L1NpcInstance) character;
			lightSize = npc.getLightSize();
		}

		if (character.hasSkillEffect(SkillId.¶óÀÌÆ®)) {
			lightSize = 14;
		}
		final int TYPE_ETC_ITEM = 0, TYPE_LIGHT = 2;

		for (ItemInstance item : character.getInventory().getItems()) {
			if (item.getItem().getType2() == TYPE_ETC_ITEM && item.getItem().getType() == TYPE_LIGHT) {
				int itemlightSize = item.getItem().getLightRange();
				if (itemlightSize != 0 && item.isNowLighting()) {
					if (itemlightSize > lightSize)
						lightSize = itemlightSize;
				}
			}
		}
		if (character instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) character;
			pc.sendPackets(new S_Light(pc.getId(), lightSize));
		}
		if (!character.isInvisble()) {
			character.broadcastPacket(new S_Light(character.getId(), lightSize));
		}
		setOwnLightSize(lightSize);
		setChaLightSize(lightSize);
	}
}
