package l1j.server.MJTemplate;

public class MJSimpleRgb {
	private static MJSimpleRgb _red;
	private static MJSimpleRgb _green;
	private static MJSimpleRgb _blue;
	
	public static MJSimpleRgb from_string(String rgb){
		String[] array = rgb.split("\\,");
		return fromRgb(Integer.parseInt(array[0]), Integer.parseInt(array[1]), Integer.parseInt(array[2]));
	}
	
	public static MJSimpleRgb red(){
		if(_red == null)
			_red = fromRgb(255, 0, 0);
		return _red;
	}
	
	public static MJSimpleRgb green(){
		if(_green == null)
			_green = fromRgb(0, 255, 0);
		return _green;
	}
	
	public static MJSimpleRgb blue(){
		if(_blue == null)
			_blue = fromRgb(0, 0, 255);
		return _blue;
	}
	
	public static MJSimpleRgb fromRgb(int r, int g, int b){
		return new MJSimpleRgb(r, g, b);
	}
	
	private byte[] _rgb;
	public MJSimpleRgb(int r, int g, int b){
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
