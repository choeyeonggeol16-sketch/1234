package l1j.server.BugRace;
import l1j.server.Templates.L1Npc;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Attack;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_ChangeName;
import l1j.server.server.serverpackets.S_DoActionGFX;

public class MessengerInstance extends L1NpcInstance{
	private static final long serialVersionUID = 1L;

	private String m_current_message;
	public MessengerInstance(L1Npc template) {
		super(template);
		m_current_message = "";
	}

	public String get_current_message(){
		return m_current_message;
	}
	public void set_current_message(String message){
		m_current_message = message;
	}
	
	public void broadcast_message() {
		broadcastPacket(new S_ChangeName(getId(), get_current_message()));
	}
	
	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (perceivedFrom == null) {
			return;
		}
		getMap().setPassable(getLocation(), true);
		perceivedFrom.addKnownObject(this);
		perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Die));
	}
	
	@Override
	public void onAction(L1PcInstance pc) {
		Attack attack = new Attack(pc, this);
		attack.calcHit();
		attack.action();
	}
	
	@Override
	public void onNpcAI() {
		if (isAiRunning()) {
			return;
		}
		setActived(false);
		startAI();
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
	}
	@Override
	public void onFinalAction(L1PcInstance player, String action) {
	}

	public void doFinalAction(L1PcInstance player) {
	}
}
