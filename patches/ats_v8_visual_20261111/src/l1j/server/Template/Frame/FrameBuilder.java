package l1j.server.Template.Frame;
/** 
 * MJFrameBuilder
 * made by mjsoft, 2017.
 **/
public class FrameBuilder {
	private double amount;
	private double rate;
	
	public FrameBuilder setAmount(double amount){
		this.amount = amount;
		return this;
	}
	
	public FrameBuilder setRate(double rate){
		this.rate = rate;
		return this;
	}
	
	public FrameElement build(){
		return FrameElement.fromRate(amount, rate);
	}
}
