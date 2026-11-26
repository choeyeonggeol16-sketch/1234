package l1j.server.server.shared.arrangement;
public interface ArrangeParsee<T>{
	public ArrangeParsee<T> init(Class<T> cls);
	public void ready(int size);
	public void parse(int idx, String data);
	public T[] result();
}
