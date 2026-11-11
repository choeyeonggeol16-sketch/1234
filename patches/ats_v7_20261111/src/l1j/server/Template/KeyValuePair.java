package l1j.server.Template;

public class KeyValuePair<T, K> {
	public T key;
	public K value;
	public KeyValuePair(){}
	public KeyValuePair(T key, K value){
		this.key = key;
		this.value = value;
	}
}
