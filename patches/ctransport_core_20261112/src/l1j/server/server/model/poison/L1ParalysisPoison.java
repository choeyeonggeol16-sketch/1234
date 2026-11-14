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
package l1j.server.server.model.poison;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.ThreadPool;
import l1j.server.server.SingleTask;
import l1j.server.server.datatables.MonsterParalyzeDelay;
import l1j.server.server.datatables.MonsterParalyzeDelay.MonsterParalyze;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.packets.Protocol.Proto;
import LIN.SC_SPELL_BUFF_NOTI;
import LIN.SC_SPELL_BUFF_NOTI.eDurationShowType;
import LIN.SC_SPELL_BUFF_NOTI.eNotiType;
public class L1ParalysisPoison extends L1Poison {

	private class ParalysisPoisonTimer extends SingleTask {
		@Override
		public void execute() {
            // A cure racing an already dispatched timer must complete before
            // the old timer can reapply freeze. No new task or thread needed.
            synchronized (L1ParalysisPoison.this) {
                if (_cured || !isActive() || _target.getPoison() != L1ParalysisPoison.this) return;

			_effectId = 2;
			_target.setPoisonEffect(2);

			if( isActive() )
			{
				if (_target instanceof L1PcInstance) {
					L1PcInstance player = (L1PcInstance) _target;

					if (player.isDead() == false)
					{
						LIN.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
						int notiWire_spellId = 0;
						int notiWire_duration = 0;
						LIN.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
						int notiWire_onIconId = 0;
						int notiWire_offIconId = 0;
						int notiWire_iconPriority = 0;
						int notiWire_tooltipStrId = 0;
						int notiWire_endStrId = 0;
						boolean notiWire_isGood = false;
						int notiWire_bit = 0;


						{ notiWire_bit |= 1; notiWire_notiType = LIN.SC_SPELL_BUFF_NOTI.eNotiType.RESTAT; }
						{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.CURSE_PARALYZE; }
						{ notiWire_bit |= 4; notiWire_duration = (_time / 1000); }
						{ notiWire_bit |= 8; notiWire_durationShowType = LIN.SC_SPELL_BUFF_NOTI.eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }
						{ notiWire_bit |= 16; notiWire_onIconId = 5270; }
						{ notiWire_bit |= 32; notiWire_offIconId = 5270; }
						{ notiWire_bit |= 64; notiWire_iconPriority = 10; }
						{ notiWire_bit |= 128; notiWire_tooltipStrId = 213; }
						{ notiWire_bit |= 512; notiWire_endStrId = 0; }
						{ notiWire_bit |= 1024; notiWire_isGood = false; }
						LIN.SC_SPELL_BUFF_NOTI.sendFields(
						        player, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType,
						        notiWire_onIconId, notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, 0,
						        notiWire_endStrId, notiWire_isGood, 0, 0, 0, notiWire_bit);
						SC_Paralysis.send(player, SC_Paralysis.TYPE_FREEZE, true);
					}

					_timer = new ParalysisTimer();
					_target.setSkillEffect(L1SkillId.STATUS_POISON_PARALYZED, 0);
					_target.setParalyzed(true);
					
                    // A rejected cure deadline must not leave a PC paralyzed forever.
                    if (ThreadPool.getInstance().schedule(_timer, _time) == null) {
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
	private SingleTask _timer;
	private final int _delay;
	private final int _time;
	private int _effectId = 1;
    private boolean _cured;
	
	private L1ParalysisPoison(L1Character cha, MonsterParalyze paralyze) {
		_target = cha;
		_delay = paralyze.paralyze_delay;
		_time = paralyze.paralyze_millis;
	}

	public static boolean doInfection(L1Character cha, int skill_id) {
		MonsterParalyze paralyze = MonsterParalyzeDelay.getInstance().get_paralyze(skill_id);
		return doInfection(cha, paralyze);
	}
	
	public static boolean doInfection(L1Character cha, MonsterParalyze paralyze) {
		if (!L1Poison.isValidTarget(cha)) {
			return false;
		}

        // Publish poison ownership BEFORE a zero-delay timer can execute.
        final L1ParalysisPoison poison = new L1ParalysisPoison(cha, paralyze);
        cha.setPoison(poison);
        poison.doInfection();
        return cha.getPoison() == poison;
	}

	private void doInfection() {
		sendMessageIfPlayer(_target, 212);
		_target.setPoisonEffect(1);

		if (_target instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) _target;
			_timer = new ParalysisPoisonTimer();
			_target.setSkillEffect(L1SkillId.STATUS_POISON_PARALYZING, 0);
			LIN.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
			int notiWire_spellId = 0;
			int notiWire_duration = 0;
			LIN.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
			int notiWire_onIconId = 0;
			int notiWire_offIconId = 0;
			int notiWire_iconPriority = 0;
			int notiWire_tooltipStrId = 0;
			int notiWire_endStrId = 0;
			boolean notiWire_isGood = false;
			int notiWire_bit = 0;


			{ notiWire_bit |= 1; notiWire_notiType = LIN.SC_SPELL_BUFF_NOTI.eNotiType.RESTAT; }
			{ notiWire_bit |= 2; notiWire_spellId = L1SkillId.CURSE_PARALYZE; }
			{ notiWire_bit |= 4; notiWire_duration = (_delay / 1000); }
			{ notiWire_bit |= 8; notiWire_durationShowType = LIN.SC_SPELL_BUFF_NOTI.eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }
			{ notiWire_bit |= 16; notiWire_onIconId = 5270; }
			{ notiWire_bit |= 32; notiWire_offIconId = 5270; }
			{ notiWire_bit |= 64; notiWire_iconPriority = 10; }
			{ notiWire_bit |= 128; notiWire_tooltipStrId = 212; }
			{ notiWire_bit |= 512; notiWire_endStrId = 0; }
			{ notiWire_bit |= 1024; notiWire_isGood = false; }
			LIN.SC_SPELL_BUFF_NOTI.sendFields(
			        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType,
			        notiWire_onIconId, notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, 0,
			        notiWire_endStrId, notiWire_isGood, 0, 0, 0, notiWire_bit);
            if (ThreadPool.getInstance().schedule(_timer, _delay) == null) {
                cure();
            }
		}
	}

	@Override
	public int getEffectId() {
		return _effectId;
	}

	@Override
	public synchronized void cure() {
        // Never run a canceled timer's execute() recursively. It used to call
        // cure() a second time and send duplicate unfreeze/status packets.
        if (_cured) return;
        _cured = true;
        final SingleTask timer = _timer;
        _timer = null;
        if (timer != null) timer.cancel();
        // A delayed callback for an older poison must not clear the new
        // owner's paralysis, status icons, or poison visuals.
        if (_target.getPoison() != this) return;
        _target.killSkillEffectTimer(L1SkillId.STATUS_POISON_PARALYZING);
        _target.killSkillEffectTimer(L1SkillId.STATUS_POISON_PARALYZED);

        if (_target instanceof L1PcInstance) {
            L1PcInstance pc = (L1PcInstance) _target;
            LIN.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
            int notiWire_spellId = 0;
            LIN.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
            boolean notiWire_isGood = false;
            int notiWire_bit = 0;


            { notiWire_bit |= 1; notiWire_notiType = LIN.SC_SPELL_BUFF_NOTI.eNotiType.END; }
            { notiWire_bit |= 2; notiWire_spellId = L1SkillId.CURSE_PARALYZE; }
            { notiWire_bit |= 8; notiWire_durationShowType = LIN.SC_SPELL_BUFF_NOTI.eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }
            { notiWire_bit |= 1024; notiWire_isGood = false; }
            LIN.SC_SPELL_BUFF_NOTI.sendFields(
                    pc, notiWire_notiType, notiWire_spellId, 0, notiWire_durationShowType, 0, 0, 0, 0, 0, 0,
                    notiWire_isGood, 0, 0, 0, notiWire_bit);
            if (!pc.isDead()) {
                SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, false);
            }
        }
        _target.setParalyzed(false);
        _target.setPoisonEffect(0);
        // Do not erase a replacement poison installed by another callback.
        if (_target.getPoison() == this) _target.setPoison(null);
	}

}
