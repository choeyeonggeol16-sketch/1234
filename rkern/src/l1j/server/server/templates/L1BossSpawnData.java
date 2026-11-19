package l1j.server.server.templates;

public class L1BossSpawnData {
	private int _id;
	private String _mapname;

	public int getBossSpawnId(){
		return this._id;
	}
	public void setBossSpawnId(int id){
		this._id = id;
	}
	
	public String getBossSpawnMapName(){
		return this._mapname;
	}
	public void setBossSpawnMapName(String id){
		this._mapname = id;
	}
}