package l1j.server.server.shared;

public class SimpleRgb {
	private static SimpleRgb _red;
	private static SimpleRgb _green;
	private static SimpleRgb _blue;
	
	public static SimpleRgb from_string(String rgb){
		String[] array = rgb.split("\\,");
		return fromRgb(Integer.parseInt(array[0]), Integer.parseInt(array[1]), Integer.parseInt(array[2]));
	}
	
	public static SimpleRgb red(){
		if(_red == null)
			_red = fromRgb(255, 0, 0);
		return _red;
	}
	
	public static SimpleRgb green(){
		if(_green == null)
			_green = fromRgb(0, 255, 0);
		return _green;
	}
	
	public static SimpleRgb blue(){
		if(_blue == null)
			_blue = fromRgb(0, 0, 255);
		return _blue;
	}
	
	public static SimpleRgb fromRgb(int r, int g, int b){
		return new SimpleRgb(r, g, b);
	}
	
	private byte[] _rgb;
	public SimpleRgb(int r, int g, int b){
		_rgb = new byte[]{
			(byte) r,
			(byte) g,
			(byte) b,
		};
	}
	
	public byte[] get_bytes(){
		return _rgb;
	}
}
