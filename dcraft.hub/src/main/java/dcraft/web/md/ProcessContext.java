package dcraft.web.md;

public class ProcessContext {
	static public ProcessContext of(Configuration config) {
		ProcessContext pc = new ProcessContext();
		pc.config = config;
		return pc;
	}

	protected Configuration config = null;
	protected String lang = "eng";		// default
	
	public Configuration getConfig() {
		return this.config;
	}
	
	public void setConfig(Configuration v) {
		this.config = v;
	}

	public String getLang() {
		return this.lang;
	}

	protected ProcessContext() {
	}
}
