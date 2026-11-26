package l1j.server.server.shared.arrangement;

import l1j.server.server.shared.frame.FrameElement;

public class ArrangeParseeFactory {
	public static ArrangeParsee<Integer> createIntArrange(){
		return new AbstractArrangeDefaultParsee<Integer>(){
			@Override
			public void parse(int idx, String data) {
				_datas[idx] = Integer.parseInt(data.trim());
			}
		}.init(Integer.class);
	}

	public static ArrangeParsee<String> createStringArrange() {
		return new AbstractArrangeDefaultParsee<String>(){
		
		@Override
		
			public void parse(int idx, String data) {
				((String[]) this._datas)[idx] = new String(data.trim());
			
		}

	}	.init(String.class);
	}
	public static ArrangeParsee<Double> createDoubleArrange(){
		return new AbstractArrangeDefaultParsee<Double>(){
			@Override
			public void parse(int idx, String data) {
				_datas[idx] = Double.parseDouble(data.trim());
			}
		};
	}
	
	 
	 
	public static ArrangeParsee<Double> createFrameRateArrange(){
		return new AbstractArrangeDefaultParsee<Double>(){
			@Override
			public void parse(int idx, String data) {
				_datas[idx] = FrameElement.calcRps(Double.parseDouble(data.trim()));
			}
		}.init(Double.class);
	}
}
