package dcraft.hub.resource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import dcraft.db.DatabaseResource;
import dcraft.hub.op.OperatingContextException;
import dcraft.locale.LocaleResource;
import dcraft.log.Logger;
import dcraft.schema.SchemaResource;
import dcraft.script.ScriptResource;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.service.ServiceResource;
import dcraft.struct.*;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class ResourceTier extends CompositeStruct {
	static public ResourceTier top() {
		return new ResourceTier();
	}
	
	static public ResourceTier tier(ResourceTier parent) {
		ResourceTier tier = new ResourceTier();
		tier.parent = parent;
		return tier;
	}
	
	protected Map<String, ResourceBase> resources = new HashMap<>();
	protected ResourceTier parent = null;
	
	public ResourceTier getParent() {
		return this.parent;
	}
	
	public ResourceTier with(ResourceBase... resources) {
		for (ResourceBase r : resources) {
			r.setTier(this);
			this.resources.put(r.getName(), r);
		}
		
		return this;
	}
	
	public ResourceBase get(String name) {
		ResourceBase r = this.resources.get(name);
		
		if (r != null)
			return r;
		
		ResourceTier parent = this.getParent();
		
		if (parent != null)
			return parent.get(name);
		
		return null;
	}
	
	public ResourceBase getTier(String name) {
		ResourceBase r = this.resources.get(name);
		
		if (r != null)
			return r;
		
		return null;
	}
	
	public LocaleResource getLocale() {
		return (LocaleResource) this.get("Locale");
	}

	synchronized public LocaleResource getOrCreateTierLocale() {
		LocaleResource r = (LocaleResource) this.getTier("Locale");
		
		if (r != null)
			return r;
		
		r = new LocaleResource();
		
		this.with(r);
		
		return r;
	}

	public ConfigResource getConfig() {
		return (ConfigResource) this.get("Config");
	}

	synchronized public ConfigResource getOrCreateTierConfig() {
		ConfigResource r = (ConfigResource) this.getTier("Config");

		if (r != null)
			return r;

		r = new ConfigResource();

		this.with(r);

		return r;
	}
	
	public MimeResource getMime() {
		return (MimeResource) this.get("Mime");
	}

	synchronized public MimeResource getOrCreateTierMime() {
		MimeResource r = (MimeResource) this.getTier("Mime");
		
		if (r != null)
			return r;
		
		r = new MimeResource();
		
		this.with(r);
		
		return r;
	}

	public MarkdownResource getMarkdown() {
		return (MarkdownResource) this.get("Markdown");
	}

	synchronized public MarkdownResource getOrCreateTierMarkdown() {
		MarkdownResource r = (MarkdownResource) this.getTier("Markdown");

		if (r != null)
			return r;

		r = new MarkdownResource();

		this.with(r);

		return r;
	}

	public KeyRingResource getKeyRing() {
		return (KeyRingResource) this.get("KeyRing");
	}
	
	synchronized public KeyRingResource getOrCreateTierKeyRing() {
		KeyRingResource r = (KeyRingResource) this.getTier("KeyRing");
		
		if (r != null)
			return r;
		
		r = new KeyRingResource();
		
		this.with(r);
		
		return r;
	}
	
	public PackageResource getPackages() {
		return (PackageResource) this.get("Packages");
	}
	
	synchronized public PackageResource getOrCreateTierPackages() {
		PackageResource r = (PackageResource) this.getTier("Packages");
		
		if (r != null)
			return r;
		
		r = new PackageResource();
		
		this.with(r);
		
		return r;
	}
	
	public SchemaResource getSchema() {
		return (SchemaResource) this.get("Schema");
	}

	synchronized public SchemaResource getOrCreateTierSchema() {
		SchemaResource r = (SchemaResource) this.getTier("Schema");
		
		if (r != null)
			return r;
		
		r = new SchemaResource();
		
		this.with(r);
		
		return r;
	}

	public ServiceResource getServices() {
		return (ServiceResource) this.get("Service");
	}

	synchronized public ServiceResource getOrCreateTierServices() {
		ServiceResource r = (ServiceResource) this.getTier("Service");
		
		if (r != null)
			return r;
		
		r = new ServiceResource();
		
		this.with(r);
		
		return r;
	}

	public ScriptResource getScripts() {
		return (ScriptResource) this.get("Script");
	}

	synchronized public ScriptResource getOrCreateTierScripts() {
		ScriptResource r = (ScriptResource) this.getTier("Script");
		
		if (r != null)
			return r;
		
		r = new ScriptResource();
		
		this.with(r);
		
		return r;
	}

	public NodeResource getNodes() {
		return (NodeResource) this.get("Node");
	}

	synchronized public NodeResource getOrCreateTierNodes() {
		NodeResource r = (NodeResource) this.getTier("Node");

		if (r != null)
			return r;

		r = new NodeResource();

		this.with(r);

		return r;
	}

	public DatabaseResource getDatabases() {
		return (DatabaseResource) this.get("Database");
	}
	
	synchronized public DatabaseResource getOrCreateTierDatabases() {
		DatabaseResource r = (DatabaseResource) this.getTier("Database");
		
		if (r != null)
			return r;
		
		r = new DatabaseResource();
		
		this.with(r);
		
		return r;
	}
	
	public ClassResource getClassLoader() {
		return (ClassResource) this.get("Class");
	}
	
	synchronized public ClassResource getOrCreateTierClassLoader() {
		ClassResource r = (ClassResource) this.getTier("Class");
		
		if (r != null)
			return r;
		
		r = new ClassResource();
		
		this.with(r);
		
		return r;
	}
	
	public TrustResource getTrust() {
		return (TrustResource) this.get("Trust");
	}
	
	synchronized public TrustResource getOrCreateTierTrust() {
		TrustResource r = (TrustResource) this.getTier("Trust");
		
		if (r != null)
			return r;
		
		r = new TrustResource();
		
		this.with(r);
		
		return r;
	}
	
	@Override
	public BaseStruct select(PathPart... path) {
		if (path.length == 0)
			return this;
		
		PathPart part = path[0];
		
		if (! part.isField()) {
			Logger.warnTr(504, this);
			
			return null;
		}
		
		String fld = part.getField();

		BaseStruct o = null;
		
		if ("Locale".equals(fld)) {
			o = this.get("Locale");
		}
		else if ("Config".equals(fld)) {
			o = this.get("Config");
		}
		else if ("Mime".equals(fld)) {
			o = this.get("Mime");
		}
		else if ("Markdown".equals(fld)) {
			o = this.get("Markdown");
		}
		else if ("KeyRing".equals(fld)) {
			o = this.get("KeyRing");
		}
		else if ("Packages".equals(fld)) {
			o = this.get("Packages");
		}
		else if ("Schema".equals(fld)) {
			o = this.get("Schema");
		}
		else if ("Service".equals(fld)) {
			o = this.get("Service");
		}
		else if ("Script".equals(fld)) {
			o = this.get("Script");
		}
		else if ("Node".equals(fld)) {
			o = this.get("Node");
		}
		else if ("Database".equals(fld)) {
			o = this.get("Database");
		}
		else if ("Class".equals(fld)) {
			o = this.get("Class");
		}
		else if ("Trust".equals(fld)) {
			o = this.get("Trust");
		}
		
		if (path.length == 1)
			return (o != null) ? o : null;
		
		if (o instanceof IPartSelector)
			return ((IPartSelector)o).select(Arrays.copyOfRange(path, 1, path.length));
		
		//Logger.warnTr(503, o);
		
		return null;
	}
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		// TODO
		
		return super.operation(stack, code);
	}
	
	@Override
	public boolean isEmpty() {
		return false;
	}
	
	@Override
	protected void doCopy(BaseStruct n) {
		super.doCopy(n);
		
		((ResourceTier) n).parent = this.parent;
		((ResourceTier) n).resources = this.resources;
	}
	
	@Override
	public BaseStruct deepCopy() {
		ResourceTier t = new ResourceTier();
		this.doCopy(t);
		return t;
	}
	
	@Override
	public void clear() {
	
	}
	
	@Override
	public void toBuilder(ICompositeBuilder builder) {
		// TODO ?
	}
}
