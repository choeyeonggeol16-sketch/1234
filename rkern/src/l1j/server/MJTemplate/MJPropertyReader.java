package l1j.server.MJTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import l1j.server.server.utils.MJCommons;

public class MJPropertyReader {
	private Properties _prop;
	
	public MJPropertyReader(String s){
		try{
			_prop = new Properties();
			InputStream is = new FileInputStream(new File(s));
			_prop.load(is);
			is.close();
		}catch(Exception e){
			e.printStackTrace();
			_prop = null;
		}
	}
	
	public void dispose(){
		if(_prop != null){
			_prop.clear();
			_prop = null;
		}
	}
	
	public short readShort(String name){
		return readShort(name, null);
	}

	public short readShort(String name, String default_val){		
		return Short.parseShort(default_val == null ? _prop.getProperty(name) : _prop.getProperty(name, default_val));
	}

	public int readInt(String name){
		return readInt(name, null);
	}
	
	public int readInt(String name, String default_val){		
		return Integer.parseInt(default_val == null ? _prop.getProperty(name) : _prop.getProperty(name, default_val));
	}
	
	public int[] readIntArray(String name, String default_val, String tok){
		String s = readString(name, default_val);
		return MJCommons.parseToIntArrange(s, tok);
	}
	
	public double[] readDoubleArray(String name, String default_val, String tok){
		String s = readString(name, default_val);
		return MJCommons.parseToDoubleArrange(s, tok);
	}
	
	public boolean readBoolean(String name){
		return readBoolean(name, null);
	}
	
	public boolean readBoolean(String name, String default_val){
		return Boolean.parseBoolean(default_val == null ? _prop.getProperty(name) : _prop.getProperty(name, default_val));
	}
	
	public long readLong(String name){
		return readLong(name, null);
	}
	
	public long readLong(String name, String default_val){
		return Long.parseLong(default_val == null ? _prop.getProperty(name) : _prop.getProperty(name, default_val));
	}
	
	public String readString(String name){
		return readString(name, null);
	}
	
	public String readString(String name, String default_val){
		try {
			return new String((default_val == null ? _prop.getProperty(name) : _prop.getProperty(name, default_val)).getBytes("ISO-8859-1"), "MS949");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public float readFloat(String name){
		return readFloat(name, null);
	}
	
	public float readFloat(String name, String default_val){
		return Float.parseFloat(default_val == null ? _prop.getProperty(name) : _prop.getProperty(name, default_val));
	}
	
	public double readDouble(String name){
		return readDouble(name, null);
	}
	
	public double readDouble(String name, String default_val){
		return Double.parseDouble(default_val == null ? _prop.getProperty(name) : _prop.getProperty(name, default_val));
	}
}
