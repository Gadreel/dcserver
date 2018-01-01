package dcraft.hub.app;

import dcraft.filestore.CommonPath;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;
import java.util.UUID;

public class Claim {
	static public Claim of(CommonPath path, int claimtimeout) {
		Claim claim = new Claim();
		claim.path = path;
		claim.claimtimeout = claimtimeout;
		return claim;
	}
	
	protected String id = UUID.randomUUID().toString().replace("-", "");
	protected CommonPath path = null;
	protected ZonedDateTime addstamp = TimeUtil.now();
	protected ZonedDateTime claimstamp = TimeUtil.now();
	protected int claimtimeout = 5;
	
	public String getId() {
		return this.id;
	}
	
	public void setPath(CommonPath v) {
		this.path = v;
	}
	
	public CommonPath getPath() {
		return this.path;
	}
	
	public ZonedDateTime getAddstamp() {
		return this.addstamp;
	}
	
	public ZonedDateTime getClaimstamp() {
		return this.claimstamp;
	}
	
	public void setClaimstamp(ZonedDateTime v) {
		this.claimstamp = v;
	}
	
	public Claim withClaimstamp(ZonedDateTime v) {
		this.claimstamp = v;
		return this;
	}
	
	public int getClaimTimeout() {
		return this.claimtimeout;
	}
	
	public void setClaimTimeout(int v) {
		this.claimtimeout = v;
	}
	
	public Claim withClaimTimeout(int v) {
		this.claimtimeout = v;
		return this;
	}
	
	public boolean isClaimValid() {
		return ! TimeUtil.now().isAfter(this.claimstamp.plusMinutes(this.claimtimeout));
	}
	
	public boolean updateClaim(String claimid, int claimtimeout) {
		if (this.id.equals(claimid)) {
			this.claimtimeout = claimtimeout;
			this.claimstamp = TimeUtil.now();
			return true;
		}
		
		return false;
	}
}
