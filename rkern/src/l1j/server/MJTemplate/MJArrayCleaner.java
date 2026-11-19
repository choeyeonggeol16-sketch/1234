package l1j.server.MJTemplate;

public class MJArrayCleaner {
	public static void cleanup(Object[] arr){
		if(arr != null){
			for(int i = arr.length - 1; i>=0; --i) 
				arr[i] = null;
			arr = null;
		}
	}
}
