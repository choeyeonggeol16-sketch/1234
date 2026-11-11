package l1j.server.CharacterAction.Executor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import l1j.server.CharacterAction.AbstractActionHandler;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;

public class CharacterActionExecutor implements Runnable{
	public static final int ACTION_IDX_WALK 			= 0;
	public static final int ACTION_IDX_SPELL 			= 1;
	public static final int ACTION_IDX_PICKUP 			= 2;
	public static final int ACTION_IDX_WAND 			= 3;
	public static final int ACTION_IDX_ATTACK 			= 4;
	public static final int ACTION_IDX_ATTACKCONTINUE 	= 5;
	private static final int MAXIMUM_ACTION_AMOUNT 		= 6;
	public static long 		NETWORK_DELAY_MILLIS		= 20L;
	
	public static CharacterActionExecutor execute(L1PcInstance pc){
		CharacterActionExecutor executor = new CharacterActionExecutor(pc);
		GeneralThreadPool.getInstance().execute(executor);
		return executor;
	}
	
	private AbstractActionHandler[] 	_handler;
	private L1PcInstance				_owner;
	private ArrayBlockingQueue<Object>	_signal;
	private Object						_signaled;
	private Object						_dummy;
	private CharacterActionExecutor(L1PcInstance owner){
		_handler 		= new AbstractActionHandler[MAXIMUM_ACTION_AMOUNT];
		_owner			= owner;
		_signal			= new ArrayBlockingQueue<Object>(1);
		_dummy			= new Object();
	}
	
	public void register(int idx, AbstractActionHandler handler){
		if(idx == ACTION_IDX_WALK) {
			for(int i = _handler.length - 1; i>=ACTION_IDX_ATTACK; --i) {
				unregister(idx);
			}		
		}
		if(_handler == null) {
			StringBuilder sb = new StringBuilder(256);
			if(_owner != null){
				sb.append(_owner.getName()).append(" 액션 핸들러 널 처리되어 있음. ");
				sb.append("(").append(idx).append(")");
				sb.append("Connected : ").append(_owner.getNetConnection());
				if(_owner.getNetConnection() != null){
					sb.append("Ip : ").append(_owner.getNetConnection().getHostname());
				}
			} else {
				handler.dispose();
				return;
			}
			System.out.println(sb.toString());
			return;
		}
		_handler[idx] = handler;
		_signal.offer(_dummy);
	}
	
	public void unregister(int idx){
		AbstractActionHandler handler = _handler[idx];
		if(handler != null) {
			_handler[idx] = null;
			handler.dispose();
		}
	}
	
	private AbstractActionHandler pop(int idx){
		AbstractActionHandler handler = _handler[idx];
		if(idx != ACTION_IDX_ATTACKCONTINUE) {
			_handler[idx] = null;
		}
		return handler;
	}
	
	private int size() {
		int amount = 0;
		for(int i = _handler.length - 1; i>=0; --i) {
			if(_handler[i] != null)
				++amount;
		}	
		return amount;
	}
	
	public boolean hasSpell(){
		return _handler[ACTION_IDX_SPELL] != null;
	}
	
	public boolean hasAction(int idx){
		return _handler[idx] != null;
	}
	
	private void update_action(int idx) {
		switch(idx) {
		case ACTION_IDX_WALK:
			break;	
		case ACTION_IDX_SPELL:
		case ACTION_IDX_WAND:
		case ACTION_IDX_ATTACK:
		case ACTION_IDX_ATTACKCONTINUE:
			break;
		}
	}
	
	@Override
	public void run() {
		try{
			while(true) {
				if(_signaled == null) {
					_signaled = _signal.poll(3000L, TimeUnit.MILLISECONDS);
				}
				if(_owner == null || _owner.getNetConnection() == null) {
					dispose();
					return;
				}
				
				if(_signaled == null) {
					continue;
				}
				long interval	= 0L;
				long error		= System.currentTimeMillis();
				for(int i=0; i<MAXIMUM_ACTION_AMOUNT; ++i) {
					AbstractActionHandler handler = pop(i);
					if(handler == null) {
						continue;
					}
					if(interval > 0L) {
						Thread.sleep(interval);
					}
					update_action(i);
					handler.handle();
					interval = handler.getInterval();
					
					if(i != ACTION_IDX_ATTACKCONTINUE) {
						handler.dispose();
						interval -= NETWORK_DELAY_MILLIS;
					} else {
						if(hasSpell()){
							i = ACTION_IDX_SPELL - 1;
							continue;
						}
					}
					interval -= (System.currentTimeMillis() - error);
				}	
				if(size() <= 0) {
					_signaled = null;
				}
				if(interval > 0L) {
					Thread.sleep(interval);
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			if(_signal != null) {
				GeneralThreadPool.getInstance().execute(this);
			}
		}
	}
	
	private void dispose() {
		if(_handler != null) {
			for(AbstractActionHandler h : _handler) {
				if(h != null) {
					h.dispose();
				}
			}
			_handler = null;
		}
		
		if(_signal != null) {
			_signal.clear();
			_signal = null;
		}	
		_owner = null;
	}
}
