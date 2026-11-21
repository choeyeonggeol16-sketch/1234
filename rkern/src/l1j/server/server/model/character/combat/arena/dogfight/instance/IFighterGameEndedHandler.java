package l1j.server.server.model.character.combat.arena.dogfight.instance;

import java.util.ArrayList;

public interface IFighterGameEndedHandler {
	public void on_ended(int winner_id, ArrayList<DogFightInstance> winners);
}
