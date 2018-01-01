package dcraft.hub.resource;

public class ResourceBase {
	protected String name = null;
	protected ResourceTier tier = null;
	
	public String getName() {
		return this.name;
	}
	
	public void setName(String v) {
		this.name = v;
	}
	
	public ResourceTier getTier() {
		return this.tier;
	}
	
	public void setTier(ResourceTier v) {
		this.tier = v;
	}
	
	public void cleanup() {
	}
}
