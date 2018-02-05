package dcraft.db.proc;

public class ExpressionResult {
	static final public ExpressionResult ACCEPTED = ExpressionResult.accepted();
	static final public ExpressionResult REJECTED = ExpressionResult.rejected();
	static final public ExpressionResult HALT = ExpressionResult.halt();
	
	static public ExpressionResult accepted() {
		return new ExpressionResult();
	}
	
	static public ExpressionResult rejected() {
		ExpressionResult result = new ExpressionResult();
		result.accepted = false;
		return result;
	}
	
	static public ExpressionResult halt() {
		ExpressionResult result = new ExpressionResult();
		result.resume = false;
		result.accepted = false;
		return result;
	}
	
	public boolean accepted = true;
	public boolean resume = true;
}
