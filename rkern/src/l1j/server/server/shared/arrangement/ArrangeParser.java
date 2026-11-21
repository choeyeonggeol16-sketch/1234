package l1j.server.server.shared.arrangement;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** 
 * ArrangeParser
 * made by mjsoft, 2017.
 **/
public class ArrangeParser {
	public static ArrangeParsee<?> parsing(String s, String tok, ArrangeParsee<?> parsee){
		if(s == null || s.equals(""))
			return null;
		
		String[] arr 	= s.split(tok);
		int size		= arr.length;
		parsee.ready(size);
		for(int i=size - 1; i>=0; --i){
			try{
				parsee.parse(i, arr[i]);
			}catch(Exception e){
				break;
			}
		}
		
		return parsee;
	}
	
	public static ArrangeParsee<?> parsing(NodeList nodes, ArrangeParsee<?> parsee){
		int size = nodes.getLength();
		parsee.ready(size);
		for(int i=size - 1; i>=0; --i){
			try{
				parsee.parse(i, ((Element)nodes.item(i)).getTextContent());
			}catch(Exception e){
				break;
			}
		}
		return parsee;
	}
}
