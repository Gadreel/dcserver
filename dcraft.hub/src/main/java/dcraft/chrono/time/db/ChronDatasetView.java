package dcraft.chrono.time.db;

public class ChronDatasetView {
	protected String id = null;
	protected int multiplier = 1;
	
	public int getMultiplier() {
		return this.multiplier;
	}
	
	public String getId() {
		return this.id;
	}
	
	public ChronDatasetView withId(String id) {
		this.id = id;
		return this;
	}
	
	public ChronDatasetView withMultiplier(int multiplier) {
		this.multiplier = multiplier;
		return this;
	}
}
