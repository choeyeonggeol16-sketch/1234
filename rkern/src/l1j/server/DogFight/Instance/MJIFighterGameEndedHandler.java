package l1j.server.DogFight.Instance;

import java.util.ArrayList;

public interface MJIFighterGameEndedHandler {
	public void on_ended(int winner_id, ArrayList<MJDogFightInstance> winners);
}
