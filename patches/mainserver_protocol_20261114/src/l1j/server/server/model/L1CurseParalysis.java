/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.ThreadPool;
import l1j.server.server.SingleTask;
import l1j.server.server.datatables.MonsterParalyzeDelay;
import l1j.server.server.datatables.MonsterParalyzeDelay.MonsterParalyze;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eDurationShowType;
import l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public class L1CurseParalysis extends L1Paralysis {
	
	private class ParalysisDelayTimer extends SingleTask {
		@Override
		public void execute() {
            // A cure racing an already dispatched timer must complete before
            // the old timer can reapply freeze. No new task or thread needed.
            synchronized (L1CurseParalysis.this) {
                if (_cured || !isActive() || _target.getParalysis() != L1CurseParalysis.this) return;


			if( isActive() )
			{
//				_target.killSkillEffectTimer(L1SkillId.STATUS_CURSE_PARALYZING);

				if (_target instanceof L1PcInstance) {
					L1PcInstance player = (L1PcInstance) _target;

					if (player.isDead() == false)
					{
						l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
						int notiWire_spellId = 0;
						int notiWire_duration = 0;
						l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
						int notiWire_onIconId = 0;
						int notiWire_offIconId = 0;
						int notiWire_iconPriority = 0;
						int notiWire_tooltipStrId = 0;
						int notiWire_endStrId = 0;
						boolean notiWire_isGood = false;
						int notiWire_bit = 0;


						{ notiWire_bit |= 1; notiWire_notiType = eNotiType.RESTAT; }
						{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.CURSE_PARALYZE; }
						{ notiWire_bit |= 4; notiWire_duration = (_time / 1000); }
						{ notiWire_bit |= 8; notiWire_durationShowType = eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }
						{ notiWire_bit |= 16; notiWire_onIconId = 5270; }
						{ notiWire_bit |= 32; notiWire_offIconId = 5270; }
						{ notiWire_bit |= 64; notiWire_iconPriority = 10; }
						{ notiWire_bit |= 128; notiWire_tooltipStrId = 213; }
						{ notiWire_bit |= 512; notiWire_endStrId = 0; }
						{ notiWire_bit |= 1024; notiWire_isGood = false; }
						SC_SPELL_BUFF_NOTI.sendFields(
						        player, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType,
						        notiWire_onIconId, notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, 0,
						        notiWire_endStrId, notiWire_isGood, 0, 0, 0, notiWire_bit);
						SC_Paralysis.send(player, SC_Paralysis.TYPE_FREEZE, true);
					}
					_timer = new ParalysisTimer();
					_target.setParalyzed(true);
                    if (ThreadPool.getInstance().schedule(_timer, _time) == null) {
                        // A missing release deadline must never strand paralysis.
                        cure();
                    }
				}
			}
		            }
        }
	}


	private class ParalysisTimer extends SingleTask {
		@Override
		public void execute() {
			_timer = null;
			cure(); 
		}
	}


	private final L1Character _target;
	private final int _delay;
	private final int _time;
	private SingleTask _timer;
    private boolean _cured;
	private L1CurseParalysis(L1Character cha, int skill_id) {
		_target = cha;
		MonsterParalyze paralyze = MonsterParalyzeDelay.getInstance().get_paralyze(skill_id);
		_delay = paralyze.paralyze_delay;
		_time = paralyze.paralyze_millis;

	}

	private void curse() {
		if (_target instanceof L1PcInstance) {
			L1PcInstance player = (L1PcInstance) _target;
			SC_ServerMessage.send(player, 212);
		}

		_target.setPoisonEffect(2);
		_timer = new ParalysisDelayTimer();
		_target.setSkillEffect(L1SkillId.STATUS_CURSE_PARALYZING, _delay);
		if(_target instanceof L1PcInstance){
			L1PcInstance pc = (L1PcInstance)_target;
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
			int notiWire_onIconId = 0;
			int notiWire_offIconId = 0;
			int notiWire_iconPriority = 0;
			int notiWire_tooltipStrId = 0;
			int notiWire_endStrId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = eNotiType.RESTAT; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.CURSE_PARALYZE; }
			{ notiWire_bit |= 4; notiWire_duration = (_delay / 1000); }
			{ notiWire_bit |= 8; notiWire_durationShowType = eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }
			{ notiWire_bit |= 16; notiWire_onIconId = 5270; }
			{ notiWire_bit |= 32; notiWire_offIconId = 5270; }
			{ notiWire_bit |= 64; notiWire_iconPriority = 10; }
			{ notiWire_bit |= 128; notiWire_tooltipStrId = 212; }
			{ notiWire_bit |= 512; notiWire_endStrId = 0; }
			{ notiWire_bit |= 1024; notiWire_isGood = false; }
			SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType,
			        notiWire_onIconId, notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, 0,
			        notiWire_endStrId, notiWire_isGood, 0, 0, 0, notiWire_bit);
		}
        if (ThreadPool.getInstance().schedule(_timer, _delay) == null) {
            cure();
        }
	}

	public static boolean curse(L1Character cha, int skill_id) {
		if (!(cha instanceof L1PcInstance || cha instanceof L1MonsterInstance)) {
			return false;
		}

		if (cha.hasSkillEffect(L1SkillId.STATUS_CURSE_PARALYZING)
				|| cha.hasSkillEffect(L1SkillId.STATUS_CURSE_PARALYZED)) {
			return false;
		}
        final L1CurseParalysis curse = new L1CurseParalysis(cha, skill_id);
        cha.setParalaysis(curse);
        curse.curse();
        return cha.getParalysis() == curse;
	}

	@Override
	public int getEffectId() {
		return 2;
	}

	@Override
	public synchronized void cure() {
        if (_cured) return;
        _cured = true;
        final SingleTask timer = _timer;
        _timer = null;
        if (timer != null) timer.cancel();
        // An old release timer may outlive the curse that installed it.
        // Only the current owner may unfreeze or remove the status effects.
        if (_target.getParalysis() != this) return;

        _target.setPoisonEffect(0);
        if (_target.getParalysis() == this) _target.setParalaysis(null);

        if (_target instanceof L1PcInstance) {
            L1PcInstance pc = (L1PcInstance) _target;
            l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
            int notiWire_spellId = 0;
            l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
            boolean notiWire_isGood = false;
            int notiWire_bit = 0;


            { notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
            { notiWire_bit |= 2; notiWire_spellId = L1SkillId.CURSE_PARALYZE; }
            { notiWire_bit |= 8; notiWire_durationShowType = eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }
            { notiWire_bit |= 1024; notiWire_isGood = false; }
            SC_SPELL_BUFF_NOTI.sendFields(
                    pc, notiWire_notiType, notiWire_spellId, 0, notiWire_durationShowType, 0, 0, 0, 0, 0, 0,
                    notiWire_isGood, 0, 0, 0, notiWire_bit);
            if (!pc.isDead()) SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, false);
        }
        _target.setParalyzed(false);
        _target.removeSkillEffect(L1SkillId.STATUS_CURSE_PARALYZING);
        _target.removeSkillEffect(L1SkillId.STATUS_CURSE_PARALYZED);
	}

}
