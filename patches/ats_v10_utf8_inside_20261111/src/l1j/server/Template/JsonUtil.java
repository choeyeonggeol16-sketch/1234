package l1j.server.Template;

import java.nio.charset.Charset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import l1j.server.Template.Files.WriteType;

public class JsonUtil {

	
	/**
	 * json 포맷 파일에서 객체로 읽어온다.
	 * UTF8전용
	 * @param path 파일의 경로
	 * @param classOf Class<T> 변환 원하는 객체
	 * @return classOf
	 **/
	public static <T> T fromFile(String path, Class<T> classOf){
		return fromFile(path, classOf, Encoding.UTF8);
	}
	
	
	
	
	/**
	 * json 포맷 파일에서 객체로 읽어온다.
	 * @param path 파일의 경로
	 * @param classOf Class<T> 변환 원하는 객체
	 * @param MJEncoding 파일의 인코딩
	 * @return classOf
	 **/
	public static <T> T fromFile(String path, Class<T> classOf, Charset MJEncoding){
		String text = Files.readAllText(path, MJEncoding);
		return IoString.isNullOrEmpty(text) ? null : fromJson(text, classOf);
	}
	
	
	
	
	/**
	 * json 포맷 문자열에서 객체로 읽어온다.
	 * @param json 포맷 문자열
	 * @param classOf Class<T> 변환 원하는 객체
	 * @return classOf
	 **/
	public static <T> T fromJson(String json, Class<T> classOf){
		Gson gson = new Gson();
		return gson.fromJson(json, classOf);
	}
	
	
	
	
	/**
	 * 객체를 json으로 변환하여 파일에 쓴다.
	 * @param path 저장될 파일 경로
	 * @param object json 문자열로 변환할 객체
	 * @param MJEncoding 저장 인코딩
	 * @param prettyPrinting true로 설정한다면 개행 및 들여쓰기가 적용됨, false 개행 및 들여쓰기 적용안함. 최적화
	 * @return classOf
	 **/
	public static void toFile(String path, Object object, Charset MJEncoding, boolean prettyPrinting){
		String json = toJson(object, prettyPrinting);
		Files.writeAllText(path, json, MJEncoding, WriteType.OVERWRITE);
	}
	
	
	
	
	/**
	 * 객체를 json으로 변환하여 파일에 쓴다.
	 * UTF8
	 * @param path 저장될 파일 경로
	 * @param object json 문자열로 변환할 객체
	 * @param prettyPrinting true로 설정한다면 개행 및 들여쓰기가 적용됨, false 개행 및 들여쓰기 적용안함. 최적화
	 * @return classOf
	 **/
	public static void toFile(String path, Object object, boolean prettyPrinting){
		toFile(path, object, Encoding.UTF8, prettyPrinting);
	}
	
	
	
	
	/**
	 * 객체에서 json 포맷 문자열로 읽어온다.
	 * @param object json 문자열로 변환할 객체
	 * @param prettyPrinting true로 설정한다면 개행 및 들여쓰기가 적용됨, false 개행 및 들여쓰기 적용안함. 최적화
	 * @return classOf
	 **/
	public static String toJson(Object object, boolean prettyPrinting){
		Gson gson = prettyPrinting ? 
				new GsonBuilder().setPrettyPrinting().create() : new Gson();
		return gson.toJson(object);
	}	
	
	
	
	public static abstract class MJToJsonable{
		public String toJson(boolean prettyPrinting){
			return JsonUtil.toJson(this, prettyPrinting);
		}
	}
}
