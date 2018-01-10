package dcraft.db.proc;

public class FilterResult {
	static public FilterResult accepted() {
		return new FilterResult();
	}
	
	static public FilterResult rejected() {
		FilterResult result = new FilterResult();
		result.accepted = false;
		return result;
	}
	
	static public FilterResult halt() {
		FilterResult result = new FilterResult();
		result.resume = false;
		result.accepted = false;
		return result;
	}
	
	public boolean accepted = true;
	public boolean resume = true;
}
