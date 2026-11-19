package l1j.server.MJTemplate;

public class MJKeyValuePair<T, K> {
	public T key;
	public K value;
	public MJKeyValuePair(){}
	public MJKeyValuePair(T key, K value){
		this.key = key;
		this.value = value;
	}
}
