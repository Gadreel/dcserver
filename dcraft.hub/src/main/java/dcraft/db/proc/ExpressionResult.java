package dcraft.db.proc;

public class ExpressionResult {
	static final public ExpressionResult ACCEPTED = ExpressionResult.accepted();
	static final public ExpressionResult REJECTED = ExpressionResult.rejected();
	static final public ExpressionResult HALT = ExpressionResult.halt();
	static final public ExpressionResult FOUND = ExpressionResult.found();

	static public ExpressionResult accepted() {
		return new ExpressionResult(true, true);
	}
	
	static public ExpressionResult rejected() {
		return new ExpressionResult(false, true);
	}
	
	static public ExpressionResult halt() {
		return new ExpressionResult(false, false);
	}

	static public ExpressionResult found() {
		return new ExpressionResult(true, false);
	}

	static public ExpressionResult of(boolean accepted, boolean resume) {
		return new ExpressionResult(accepted, resume);
	}

	final public boolean accepted;
	final public boolean resume;

	protected ExpressionResult(boolean accepted, boolean resume) {
		this.resume = resume;
		this.accepted = accepted;
	}
}
