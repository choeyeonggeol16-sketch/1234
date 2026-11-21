package l1j.server.server.shared.arrangement;
/** 
 * ArrangeHelper
 * made by mjsoft, 2017.
 **/
public class ArrangeHelper {
	public static void cleanup(Object[] arr){
		if(arr != null){
			for(int i = arr.length - 1; i>=0; --i) 
				arr[i] = null;
			arr = null;
		}
	}
	
	public static <T> void setArrayValues(T[] arr, int start, int end, T val){
		for(int i=start; i<=end; ++i)
			arr[i] = val;
	}
}
