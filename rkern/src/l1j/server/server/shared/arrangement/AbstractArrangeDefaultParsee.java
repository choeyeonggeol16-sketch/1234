package l1j.server.server.shared.arrangement;

import java.lang.reflect.Array;

public abstract class AbstractArrangeDefaultParsee<E> implements ArrangeParsee<E>{
	protected E[] _datas;
	protected Class<E> _cls;
	
	@Override
	public ArrangeParsee<E> init(Class<E> cls){
		_cls = cls;
		return this;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void ready(int size){
		_datas = (E[]) Array.newInstance(_cls, size);
	}
	
	@Override
	public E[] result(){
		return _datas;
	}
	
	@Override
	public abstract void parse(int idx, String data);
}
