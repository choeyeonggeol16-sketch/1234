package l1j.server.Template;

/**
 * <b>각종 밸류들의 유효성을 검사할 때 사용되는 헬퍼 클래스</b>
 * @author mjsoft
 **/
public class Validate {
	
	
	
	
	/**
	 * <b>null을 체크한다.</b>
	 * @throws NullPointerException null이라면 예외발생
	 **/
	public static void checkNull(Object obj){
		if(obj == null){
			throw new NullPointerException();
		}
	}
	
	
	
	
	/**
	 * <b>null을 체크한다.</b>
	 * @param message 예외 메시지
	 * @throws NullPointerException null이라면 예외발생
	 **/
	public static void checkNull(Object obj, String message){
		if(obj == null){
			throw new NullPointerException(message);
		}
	}
	
	
	
	
	/**
	 * <b>true인지 체크한다.</b>
	 * @param b 검사할 boolean
	 * @throws IllegalArgumentException false라면 예외 발생
	 **/
	public static void checkTrue(boolean b){
		if(!b){
			throw new IllegalArgumentException();
		}
	}
	
	
	
	
	/**
	 * <b>true인지 체크한다.</b>
	 * @param b 검사할 boolean
	 * @param message 예외 메시지
	 * @throws IllegalArgumentException false라면 예외 발생
	 **/
	public static void checkTrue(boolean b, String message){
		if(!b){
			throw new IllegalArgumentException(message);
		}
	}
	
	
	
	
	/**
	 * <b>false인지 체크한다.</b>
	 * @param b 검사할 boolean
	 * @throws IllegalArgumentException true라면 예외 발생
	 **/
	public static void checkFalse(boolean b){
		checkTrue(!b);
	}
	
	
	
	
	/**
	 * <b>false인지 체크한다.</b>
	 * @param b 검사할 boolean
	 * @param message 예외 메시지
	 * @throws IllegalArgumentException true라면 예외 발생
	 **/
	public static void checkFalse(boolean b, String message){
		checkTrue(!b, message);
	}
	
	
	
	
	
	/**
	 * <b>버퍼 size를 검사한다.</b>
	 * @param off 오프셋
	 * @param len 버퍼 길이
	 * @param size 검사할 크기
	 * @throws IndexOutOfBoundsException
	 **/
	public static void checkBounds(int off, int len, int size){
		if ((off | len | (off + len) | (size - (off + len))) < 0){
            throw new IndexOutOfBoundsException();
		}
	}
}