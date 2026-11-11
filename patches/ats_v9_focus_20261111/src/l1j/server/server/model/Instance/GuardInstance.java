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
package l1j.server.server.model.Instance;

import l1j.server.Templates.L1Npc;
import l1j.server.server.ActionCodes;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.L1World;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_NPCTalkReturn;
import l1j.server.server.types.Point;

public class GuardInstance extends L1NpcInstance {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	// 타겟을 찾는다
	@Override
	public void searchTarget() {
		L1PcInstance targetPlayer = null;

		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {

			if (pc.getCurrentHp() <= 0 || pc.isDead() || pc.isGm()
					|| pc.isGhost()) {
				continue;
			}
			if (!pc.isInvisble() || getNpcTemplate().is_agrocoi())
			{
				if (pc.isWanted()) {
					targetPlayer = pc;
					break;
				}
			}
		}
	
		if (targetPlayer != null) {
			_hateList.add(targetPlayer, 0);
			_target = targetPlayer;		
		}
	}

	public void setTarget(L1PcInstance targetPlayer) {
		if (targetPlayer != null) {
			_hateList.add(targetPlayer, 0);
			_target = targetPlayer;
		}
	}

	// 타겟이 없는 경우의 처리
	@Override
	public boolean noTarget() {
		if (getLocation()
				.getTileLineDistance(new Point(getHomeX(), getHomeY())) > 0) {
			int dir = moveDirection(getHomeX(), getHomeY());
			if (dir != -1) {
				setDirectionMove(dir);
				//setSleepTime(calcSleepTime(getPassispeed(), MOVE_SPEED));
			} else // 너무 먼 or경로가 발견되지 않는 경우는 텔레포트 해 돌아간다
			{
				teleport(getHomeX(), getHomeY(), 1);
			}
		} else {
			if (L1World.getInstance().getRecognizePlayer(this).size() == 0) {
				return true; // 주위에 플레이어가 없어지면(자) AI처리 종료
			}
		}
		return false;
	}

	public GuardInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onNpcAI() {
		if (isAiRunning()) {
			return;
		}
		setActived(false);
		startAI();
		startChat(CHAT_TIMING_APPEARANCE); // 채팅 시작
	}

	@Override
	public void onAction(L1PcInstance player) {
		if (getCurrentHp() > 0 && !isDead()) {
			Attack attack = new Attack(player, this);
			if (attack.calcHit()) {
				attack.calcDamage();
			}
			attack.action();
			attack.commit();
		}
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		int objid = getId();
		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(
				getNpcTemplate().get_npcId());
		int npcid = getNpcTemplate().get_npcId();
		String htmlid = null;
		String[] htmldata = null;
		boolean hascastle = false;
		String clan_name = "";
		String pri_name = "";

		if (talking != null) {
			// 키퍼
			if (npcid == 70549 || // 켄트성왼쪽 바깥문키퍼
					npcid == 70985) { // 켄트성 오른쪽외문키퍼
				hascastle = checkHasCastle(player,
						L1CastleLocation.KENT_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "gateokeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70656) { // 켄트 키우치문키퍼
				hascastle = checkHasCastle(player,
						L1CastleLocation.KENT_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "gatekeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70600 || // 오크의 삼외문키퍼
					npcid == 70986) {
				hascastle = checkHasCastle(player,
						L1CastleLocation.OT_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "orckeeper";
				} else {
					htmlid = "orckeeperop";
				}
			} else if (npcid == 70687 || // 윈다웃드 성밖문키퍼
					npcid == 70987) {
				hascastle = checkHasCastle(player,
						L1CastleLocation.WW_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "gateokeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70778) { // 윈다웃드 키우치문키퍼
				hascastle = checkHasCastle(player,
						L1CastleLocation.WW_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "gatekeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70800
					|| // 기란 성밖문키퍼
					npcid == 70988 || npcid == 70989 || npcid == 70990
					|| npcid == 70991) {
				hascastle = checkHasCastle(player,
						L1CastleLocation.GIRAN_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "gateokeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70817) { // 기란 키우치문키퍼
				hascastle = checkHasCastle(player,
						L1CastleLocation.GIRAN_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "gatekeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70862 || // Heine 성밖문키퍼
					npcid == 70992) {
				hascastle = checkHasCastle(player,
						L1CastleLocation.HEINE_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "gateokeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70863) { // Heine 키우치문키퍼
				hascastle = checkHasCastle(player, L1CastleLocation.HEINE_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "gatekeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70993 || // 드워후 성밖문키퍼
					npcid == 70994) {
				hascastle = checkHasCastle(player,
						L1CastleLocation.DOWA_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "gateokeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70995) { // 드워후 키우치문키퍼
				hascastle = checkHasCastle(player,
						L1CastleLocation.DOWA_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "gatekeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			} else if (npcid == 70996) { // 에덴 키우치문키퍼
				hascastle = checkHasCastle(player,
						L1CastleLocation.ADEN_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					htmlid = "gatekeeper";
					htmldata = new String[] { player.getName() };
				} else {
					htmlid = "gatekeeperop";
				}
			}

			// 근위병
			else if (npcid == 60514) { // 켄트성근위병
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() // 성주 크란
					== L1CastleLocation.KENT_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "ktguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 60560) { // 오크 근위병
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() // 성주 크란
					== L1CastleLocation.OT_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "orcguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 60552) { // 윈다웃드성근위병
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() // 성주 크란
					== L1CastleLocation.WW_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "wdguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 60524 || // 기란거리 입구 근위병(활)
					npcid == 60525 || // 기란거리 입구 근위병
					npcid == 60529) { // 기란성근위병
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() // 성주 크란
					== L1CastleLocation.GIRAN_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "grguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 70857) { // Heine성Heine 가이드
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() // 성주 크란
					== L1CastleLocation.HEINE_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "heguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 60530 || // 드워후성드워후가드
					npcid == 60531) {
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() // 성주 크란
					== L1CastleLocation.DOWA_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "dcguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 60533 || // 에덴성가이드
					npcid == 60534) {
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() // 성주 크란
					== L1CastleLocation.ADEN_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "adguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			} else if (npcid == 81156) { // 에덴 정찰병(디아드 요새)
				for (L1Clan clan : L1World.getInstance().getAllClans()) {
					if (clan.getCastleId() // 성주 크란
					== L1CastleLocation.DIAD_CASTLE_ID) {
						clan_name = clan.getClanName();
						pri_name = clan.getLeaderName();
						break;
					}
				}
				htmlid = "ktguard6";
				htmldata = new String[] { getName(), clan_name, pri_name };
			}

			// html 표시 패킷 송신
			if (htmlid != null) { // htmlid가 지정되고 있는 경우
				if (htmldata != null) { // html 지정이 있는 경우는 표시
					player.sendPackets(new S_NPCTalkReturn(objid, htmlid,
							htmldata));
				} else {
					player.sendPackets(new S_NPCTalkReturn(objid, htmlid));
				}
			} else {
				if (player.getLawful() < -1000) { // 플레이어가 카오틱
					player.sendPackets(new S_NPCTalkReturn(talking, objid, 2));
				} else {
					player.sendPackets(new S_NPCTalkReturn(talking, objid, 1));
				}
			}
		}
	}

	public void onFinalAction() {

	}

	public void doFinalAction() {

	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) {
		if (getCurrentHp() > 0 && !isDead()) {
			if (damage >= 0) {
				if (!(attacker instanceof EffectInstance)) { 
					setHate(attacker, damage);
				}
			}
			if (damage > 0) {
				if(hasSkillEffect(SkillId.포그_오브_슬리핑)){
					removeSkillEffect(SkillId.포그_오브_슬리핑); 
				}
			}

			onNpcAI();

			if (attacker instanceof L1PcInstance && damage > 0) {
				L1PcInstance pc = (L1PcInstance) attacker;
				pc.setPetTarget(this);
			}

			int newHp = getCurrentHp() - damage;
			if (newHp <= 0 && !isDead()) {
				setCurrentHp(0);
				setDead(true);
				setStatus(ActionCodes.ACTION_Die);
				Death death = new Death(attacker);
				GeneralThreadPool.getInstance().execute(death);
			}
			if (newHp > 0) {
				setCurrentHp(newHp);
			}
		} else if (!isDead()) { 
			setDead(true);
			setStatus(ActionCodes.ACTION_Die);
			Death death = new Death(attacker);
			GeneralThreadPool.getInstance().execute(death);
		}
	}
	@Override
	public void checkTarget(){
		//System.out.println("오버라이드 했는데 여기 진입할까?");
		if (_target == null
				|| 	(Math.abs(this.getX() - this.getHomeX())) > 20 
				||  (Math.abs(this.getY() - this.getHomeY())) > 20
				|| _target.getMapId() != getMapId()
				|| _target.getCurrentHp() <= 0
				|| _target.isDead()
				|| (_target.isInvisble() && !getNpcTemplate().is_agrocoi() && !_hateList.containsKey(_target))
				|| (_target instanceof SummonInstance)
				|| (_target instanceof PetInstance)
				) {
			if (_target != null) {
				tagertClear();
				teleport(getHomeX(), getHomeY(),getSpawn().getHeading());
				
			}
			if (!_hateList.isEmpty()) {
				_target = _hateList.getMaxHateCharacter();
				checkTarget();
			}
			
		}
	}
	@Override
	public void setCurrentHp(int i) {
		super.setCurrentHp(i);
		
		if (getMaxHp() > getCurrentHp()) {
			startHpRegeneration();

		}
	}

	class Death implements Runnable {
		L1Character _lastAttacker;

		public Death(L1Character lastAttacker) {
			_lastAttacker = lastAttacker;
		}

		@Override
		public void run() {
			setDeathProcessing(true);
			setCurrentHp(0);
			setDead(true);
			setStatus(ActionCodes.ACTION_Die);
			getMap().setPassable(getLocation(), true);
			broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Die));
			startChat(CHAT_TIMING_DEAD);
			setDeathProcessing(false);
			allTargetClear();
			startDeleteTimer();
		}
	}
    

	private boolean checkHasCastle(L1PcInstance pc, int castleId) {
		boolean isExistDefenseClan = false;
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (castleId == clan.getCastleId()) {
				isExistDefenseClan = true;
				break;
			}
		}
		if (!isExistDefenseClan) { // 성주 크란이 없다
			return true;
		}

		if (pc.getClanid() != 0) { // 크란 소속중
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				if (clan.getCastleId() == castleId) {
					return true;
				}
			}
		}
		return false;
	}

}
