package dcraft.hub;

import dcraft.hub.resource.ResourceTier;

public class TopResourceTier extends ResourceTier {
	static public TopResourceTier tier() {
		TopResourceTier srt = new TopResourceTier();
		return srt;
	}
	
	@Override
	public ResourceTier getParent() {
		return null;
	}
	
	protected TopResourceTier() {
	}
}
