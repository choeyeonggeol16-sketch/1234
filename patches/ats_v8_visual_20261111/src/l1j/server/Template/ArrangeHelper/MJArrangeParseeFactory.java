package l1j.server.Template.ArrangeHelper;

import l1j.server.Template.Frame.FrameElement;

/** 
 * MJArrangeParseeFactory
 * made by mjsoft, 2017.
 **/
public class MJArrangeParseeFactory {
	public static MJArrangeParsee<Integer> createIntArrange(){
		return new MJAbstractArrangeDefaultParsee<Integer>(){
			@Override
			public void parse(int idx, String data) {
				_datas[idx] = Integer.parseInt(data.trim());
			}
		}.init(Integer.class);
	}
	
	public static MJArrangeParsee<Double> createDoubleArrange(){
		return new MJAbstractArrangeDefaultParsee<Double>(){
			@Override
			public void parse(int idx, String data) {
				_datas[idx] = Double.parseDouble(data.trim());
			}
		};
	}
	
	public static MJArrangeParsee<String> createStringArrange(){
		return new MJAbstractArrangeDefaultParsee<String>(){
			@Override
			public void parse(int idx, String data) {
				_datas[idx] = new String(data.trim());
			}
		}.init(String.class);
	}
	
	public static MJArrangeParsee<Double> createFrameRateArrange(){
		return new MJAbstractArrangeDefaultParsee<Double>(){
			@Override
			public void parse(int idx, String data) {
				_datas[idx] = FrameElement.calcRps(Double.parseDouble(data.trim()));
			}
		}.init(Double.class);
	}
}
