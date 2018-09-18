package dcraft.struct.format;

public class FormatResult {
	static public FormatResult result(Object v) {
		FormatResult res = new FormatResult();
		res.result = v;
		return res;
	}
	
	protected Object result = null;
	protected boolean halt = false;
	
	public Object getResult() {
		return this.result;
	}
	
	public boolean isHalt() {
		return this.halt;
	}
	
	public FormatResult withHalt(boolean v) {
		this.halt = v;
		return this;
	}
}
