package l1j.server.server.Opcodes;

public class ServerOpcodes {
	
	static public enum TYPE {
		Client,
		Server
	};
	
	private int uid;
	private TYPE type;
	private String name;
	private String name_op;
	private int _op;
	public int getUid() {
		return uid;
	}
	public void setUid(int uid) {
		this.uid = uid;
	}
	public TYPE getType() {
		return type;
	}
	public void setType(TYPE type) {
		this.type = type;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getNameOp() {
		return name_op;
	}
	public void setNameOp(String name_op) {
		this.name_op = name_op;
	}
	public int getOp() {
		return _op;
	}
	public void setOp(int now_op) {
		this._op = now_op;
	}
}
