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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import l1j.server.configure.Config;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.RepeatTask;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_AddPartyMember;
import l1j.server.server.serverpackets.S_HPMeter;
import l1j.server.server.serverpackets.S_PartyList;
import l1j.server.server.serverpackets.S_PartyPosition;
import l1j.server.server.serverpackets.S_PromotePartyLeader;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.ServerBasePacket;

public class L1Party {
	
	private final List<L1PcInstance> _membersList = new ArrayList<L1PcInstance>();

	private L1PcInstance _leader = null;

	PartyPositionBroadcastTask _broadcastTask = null;
	
	class PartyPositionBroadcastTask extends RepeatTask {
		public PartyPositionBroadcastTask(){
			super(10000);
		}

		@Override
		public void execute(){
			if (getMembers().length == 0) {
				_broadcastTask = null;
				cancel();
				return;
			}
			
			for (L1PcInstance member :getMembers()) {
				member.sendPackets(new S_PartyPosition(member));
			}
		}
	}
	
	public void startPositionBroadcast() {
		_broadcastTask = new PartyPositionBroadcastTask();
		GeneralThreadPool.getInstance().execute(_broadcastTask);
	}
	public void addMember(L1PcInstance pc) {
		if (pc == null) {
			throw new NullPointerException();
		}
		if (_membersList.size() == Config.MAX_PT && !_leader.isGm()  || _membersList.contains(pc)) {
			pc.sendPackets(new S_ServerMessage(417));
			return;
		}

		if (_membersList.isEmpty()) {
			setLeader(pc);
		} else {
			createMiniHp(pc);
			L1PcInstance[] c = getMembers();
			if(c != null){
				for(int i = 0; i < c.length; i++) {
					c[i].sendPackets(new S_AddPartyMember(pc));
				}
			}
		}
		updateMiniHP(pc);
		_membersList.add(pc);
		pc.setParty(this);

		if(_membersList.size() >= 2) {
			pc.sendPackets(new S_PartyList(pc));
		}
	}

	private void removeMember(L1PcInstance pc) {
		if (!_membersList.contains(pc)) {
			return;
		}
		_membersList.remove(pc);
		pc.setParty(null);
		if (!_membersList.isEmpty()) {
			deleteMiniHp(pc);
		}
	}

	public boolean isVacancy() {
		return _membersList.size() < Config.MAX_PT;
	}

	public int getVacancy() {
		return Config.MAX_PT - _membersList.size();
	}

	public boolean isMember(L1PcInstance pc) {
		return _membersList.contains(pc);
	}

	private void setLeader(L1PcInstance pc) {
		_leader = pc;
	}

	public L1PcInstance getLeader() {
		return _leader;
	}

	public boolean isLeader(L1PcInstance pc) {
		return pc.getId() == _leader.getId();
	}

	public String getMembersNameList() {
		String _result = new String("");
		for (L1PcInstance pc : _membersList) {
			_result = _result + pc.getName() + " ";
		}
		return _result;
	}

	public void promoteLeader(L1PcInstance pc) {
		if (!isMember(pc)) {
			return;
		}
		setLeader(pc);
		
		for (L1PcInstance member : getMembers()) {
			member.sendPackets(new S_PromotePartyLeader(pc));
		}
	}
	private void createMiniHp(L1PcInstance pc) {

		for (L1PcInstance member : getMembers()) {
			member.sendPackets(new S_HPMeter(pc.getId(), 100 * pc.getCurrentHp() / pc.getMaxHp()));
			pc.sendPackets(new S_HPMeter(member.getId(), 100 * member.getCurrentHp() / member.getMaxHp()));
		}
	}

	private void deleteMiniHp(L1PcInstance pc) {

		for (L1PcInstance member : getMembers()) {
			pc.sendPackets(new S_HPMeter(member.getId(), 0xff));
			member.sendPackets(new S_HPMeter(pc.getId(), 0xff));
		}
	}

	public void updateMiniHP(L1PcInstance pc) {

		for (L1PcInstance member : getMembers()) {
			member.sendPackets(new S_HPMeter(pc.getId(), 100 * pc.getCurrentHp() / pc.getMaxHp()));
		}
	}

	private void breakup() {

		for (L1PcInstance member : getMembers()) {
			removeMember(member);
			member.sendPackets(new S_ServerMessage(418));
		}
	}

	public void leaveMember(L1PcInstance pc) {
		if (isLeader(pc)) {
			breakup();
		} else {
			if (getNumOfMembers() == 2) {
			breakup();

			} else {
				removeMember(pc);
				for (L1PcInstance member : getMembers()) {
					sendLeftMessage(member, pc);
				}
				sendLeftMessage(pc, pc);
			}
		}
	}

	public void kickMember(L1PcInstance pc) {
		if (getNumOfMembers() == 2) {
			breakup();
		} else {
			// 나머지의 파티 멤버가 2명 이상 있다
			removeMember(pc);
			for (L1PcInstance member : getMembers()) {
				sendLeftMessage(member, pc);
			}
			sendLeftMessage(pc, pc);
			pc.sendPackets(new S_ServerMessage(419));
		}
	}
	
	private boolean _share = false;
	
	public void setShareParty()
	{
		_share = true;
	}
	
	public boolean isShareParty()
	{
		return _share;
	}

	public L1PcInstance[] getMembers() {
		return _membersList.toArray(new L1PcInstance[_membersList.size()]);
	}

	public int getNumOfMembers() {
		return _membersList.size();
	}

	private void sendLeftMessage(L1PcInstance sendTo, L1PcInstance left) {
		// %0가 파티로부터 떠났습니다.
		sendTo.sendPackets(new S_ServerMessage(420, left.getName()));
	}

	public Stream<L1PcInstance> createMembersStream(){
		return _membersList.stream();
	}
	
	public Stream<L1PcInstance> createVisibleMembersStream(L1PcInstance pc){
		int mid = pc.getMapId();
		L1Location pt = pc.getLocation();
		return createMembersStream().filter((L1PcInstance m) -> m.getMapId() == mid && pt.getTileLineDistance(m.getLocation()) <= 50 );
	}
	
	public void broadcast(ServerBasePacket packet){
		createMembersStream().forEach((L1PcInstance pc) -> pc.sendPackets(packet, false));
		packet.clear();
	}
	public void broadcastRootMent(ServerBasePacket packet){
		createMembersStream().filter((L1PcInstance pc) -> pc.RootMent).forEach((L1PcInstance pc) -> pc.sendPackets(packet, false));
		packet.clear();
	}

}
