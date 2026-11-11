package l1j.server.Template;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * <b>간단하게 ReentrantReadWriteLock의 Read/Write Lock을 사용할 때 도움을 주는 헬퍼</b>
 * @author mjsoft
 * @see ReentrantReadWriteLock
 * @see ReentrantReadWriteLock#readLock()
 * @see ReentrantReadWriteLock#writeLock()
 **/
public class ReadWriteLock {
	private final ReadLock readLock;
	private final WriteLock writeLock;
	public ReadWriteLock(){
		ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock();
		this.writeLock = rwlock.writeLock();
		this.readLock = rwlock.readLock();
	}
	
	
	
	
	/**
	 * 쓰기를 잠근다.
	 **/
	public void writeLock(){
		writeLock.lock();
	}
	
	
	
	
	/**
	 * 쓰기 잠금을 해제한다.
	 **/
	public void writeUnlock(){
		writeLock.unlock();
	}
	
	
	
	
	/**
	 * 쓰기를 잠근다. 대기상태에 빠지지 않는다.
	 * @return boolean 잠금 획득 시 true반환
	 **/
	public boolean writeTryLock(){
		return writeLock.tryLock();
	}
	
	
	
	
	/**
	 * 쓰기를 잠근다. 지정한 밀리초 만큼만 대기에 빠진다.
	 * @param timeoutMillis 대기에 빠질 밀리초
	 * @return boolean 잠금 획득 시 true반환
	 **/
	public boolean writeTryLock(long timeoutMillis) throws InterruptedException{
		return writeLock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS);
	}
	
	
	
	
	/**
	 * 읽기를 잠근다.
	 **/
	public void readLock(){
		readLock.lock();
	}
	
	
	
	
	/**
	 * 읽기 잠금을 해제한다.
	 **/
	public void readUnlock(){
		readLock.unlock();
	}
	
	
	
	
	/**
	 * 읽기를 잠근다. 대기상태에 빠지지 않는다.
	 * @return boolean 잠금 획득 시 true반환
	 **/
	public boolean readTryLock(){
		return readLock.tryLock();
	}
	
	
	
	
	/**
	 * 읽기를 잠근다. 지정한 밀리초 만큼만 대기에 빠진다.
	 * @param timeoutMillis 대기에 빠질 밀리초
	 * @return boolean 잠금 획득 시 true반환
	 **/
	public boolean readTryLock(long timeoutMillis) throws InterruptedException{
		return readLock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS);
	}
	
	
	
	
	/**
	 * 읽기 쓰기를 동시에 잠근다.
	 * <p>쓰기 -> 읽기 순</p>
	 **/
	public void fullyLock(){
		writeLock();
		readLock();
	}
	
	
	
	
	/**
	 * 읽기 쓰기를 동시에 잠금 해제한다.
	 * <p>읽기 -> 쓰기 순</p>
	 **/
	public void fullyUnlock(){
		readUnlock();
		writeUnlock();
	}
}