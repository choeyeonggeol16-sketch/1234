package l1j.server.Template;

import java.util.Random;

public class LORnd {
	private static final Random _rnd = new Random(System.nanoTime());
	
	public static Random rnd() {
		return _rnd;
	}
	
	public static int next(int whole){
		return _rnd.nextInt(whole);
	}
	
	public static int next(int min, int max){
		return _rnd.nextInt(max - min + 1) + min;
	}
	
	public static boolean isWinning(int whole, int dice){
		return _rnd.nextInt(whole - 1) + 1 <= dice;
	}
	
	public static boolean isBoolean(){
		return _rnd.nextBoolean();
	}
	public static double next_double(){
		return _rnd.nextDouble();
	}
	public static double next_double(double min, double max){
		double val = next((int)(min * 100), (int)(max * 100));
		return val * 0.01; 
		
	}
	
	public static byte[] next_bytes(int size){
		byte[] buff = new byte[size];
		_rnd.nextBytes(buff);
		return buff;
	}
}
