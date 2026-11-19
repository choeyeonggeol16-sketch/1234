package l1j.server.MJTemplate.Kda;

import java.util.Comparator;

import l1j.server.MJTemplate.MJRnd;

public class TeamKda {
	public static Comparator<TeamKda> create_dealer_comparator(){
		return new Comparator<TeamKda>(){
			@Override
			public int compare(TeamKda o1, TeamKda o2) {
				int o1_val = o1.get_damage();
				int	o2_val = o2.get_damage();				
				if(o1_val > o2_val)
					return -1;
				else if(o1_val < o2_val)
					return 1;
				return MJRnd.isBoolean() ? 1 : -1;
			}	
		};
	}
	
	public static Comparator<TeamKda> create_killer_comparator(){
		return new Comparator<TeamKda>(){
			@Override
			public int compare(TeamKda o1, TeamKda o2) {
				int o1_val = o1.get_kill();
				int	o2_val = o2.get_kill();				
				if(o1_val > o2_val)
					return -1;
				else if(o1_val < o2_val)
					return 1;
				return MJRnd.isBoolean() ? 1 : -1;
			}	
		};
	}
	
	public static Comparator<TeamKda> create_winner_comparator(){
		return new Comparator<TeamKda>(){
			@Override
			public int compare(TeamKda o1, TeamKda o2) {
				int o1_val = o1.get_kill();
				int	o2_val = o2.get_kill();				
				if(o1_val > o2_val)
					return -1;
				else if(o1_val < o2_val)
					return 1;
				
				o1_val = o1.get_death();
				o2_val = o2.get_death();
				if(o1_val > o2_val)
					return -1;
				else if(o1_val < o2_val)
					return 1;
				
				o1_val = o1.get_damage();
				o2_val = o2.get_damage();
				if(o1_val > o2_val)
					return -1;
				else if(o1_val < o2_val)
					return 1;
				
				return MJRnd.isBoolean() ? 1 : -1;
			}
		};
	}
	
	public static TeamKda newInstance(int team_id){
		return new TeamKda().set_team_id(team_id); 
	}
	
	private int _team_id;
	private int _kill;
	private int _death;
	private int _damage;
	private int _tanking;
	protected TeamKda(){}
	
	public TeamKda set_team_id(int team_id){
		_team_id = team_id;
		return this;
	}
	
	public int get_team_id(){
		return _team_id;
	}
	
	public int inc_kill(){
		return ++_kill;
	}
	
	public int get_kill(){
		return _kill;
	}
	
	public int add_kill(int kill){
		return (_kill += kill);
	}
	
	public int inc_death(){
		return ++_death;
	}
	
	public int get_death(){
		return _death;
	}
	
	public int inc_damage(){
		return ++_damage;
	}
	
	public int get_damage(){
		return _damage;
	}
	
	public int add_damage(int damage){
		return (_damage += damage);
	}
	
	public int inc_tanking(){
		return ++_tanking;
	}
	
	public int get_tanking(){
		return _tanking;
	}
	
	public int add_tanking(int tanking){
		return (_tanking += tanking);
	}
}
