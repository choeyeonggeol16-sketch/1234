package l1j.server.Template.Frame;

/** 
 * MJFrameElement
 * made by mjsoft, 2017.
 **/
public class FrameElement {
	private static final double BASIC_MILLIS = 1000.0D;
	
	public static FrameElement fromRate(double amount, double rate){
		return fromRPS(amount, calcRps(rate));
	}
	
	public static FrameElement fromRate(double amount, double basic_millis, double rate){
		return fromRPS(amount, calcRps(basic_millis, rate));
	}
	
	public static FrameElement fromRPS(double amount, double rps){
		return create(amount, amount * rps);
	}
	
	public static FrameElement create(double amount, double fps){
		FrameElement element = new FrameElement();
		element.frameAmount = amount;
		element.framePerSecond = fps;
		return element;
	}
	
	public static double calcRps(double basic_millis, double rate){
		return basic_millis / rate;
	}
	
	public static double calcRps(double rate){
		return calcRps(BASIC_MILLIS, rate);
	}
	
	private Double frameAmount;
	private Double framePerSecond;
	private FrameElement(){}
	
	public Double getFrameAmount(){
		return frameAmount;
	}
	
	public Double getFramePerSeocnd(){
		return framePerSecond;
	}
	
	public Double getFramePerSecond(Double rps){
		return frameAmount * rps;
	}	
}
