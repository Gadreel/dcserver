package dcraft.tenant;

import java.nio.file.Path;

import dcraft.filevault.Vault;
import dcraft.hub.op.IVariableProvider;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationObserver;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.groovy.GCompClassLoader;
import dcraft.xml.XElement;

abstract public class Base extends RecordStruct implements IVariableProvider {
	public Struct getCacheEntry(String name) {
		RecordStruct cache = this.getFieldAsRecord("Cache");
		
		if (cache != null) {
			RecordStruct info = cache.getFieldAsRecord(name);
			
			if (info != null) {
				if (System.currentTimeMillis() < info.getFieldAsInteger("Expires", 0))
					return info.getField("Data");
				
				synchronized(this) {
					cache.removeField(name);
				}
			}
		}
		
		return null;
	}
	
	public synchronized Base withCacheEntry(String name, Struct entry, long seconds) {
		RecordStruct cache = this.getFieldAsRecord("Cache");
		
		if (cache == null) {
			cache = RecordStruct.record();
			this.with("Cache", cache);
		}
		
		cache.with(name, RecordStruct.record()
				.with("Data", entry)
				.with("Expires", System.currentTimeMillis() + (seconds * 1000))
		);
		
		return this;
	}
	
	public String getAlias() {
		return this.getFieldAsString("Alias");
	}
	
	public String getTitle() {
		return this.getFieldAsString("Title");
	}
	
	public Base withTitle(String v) {
		this.with("Title", v);
		return this;
	}

	abstract public Path getPath();
	//abstract public GCompClassLoader getScriptLoader();
	abstract Vault getVault(String name);

	protected Base() {
		this.with("Variables", RecordStruct.record());
	}

	public Path resolvePath(String path) {
		Path base = this.getPath();
		
		if (StringUtil.isEmpty(path))
			return base;
		
		if (path.charAt(0) == '/')
			return base.resolve(path.substring(1));
		
		return base.resolve(path);
	}
	
	public String relativize(Path path) {
		if (path == null)
			return null;
		
		path = path.normalize().toAbsolutePath();
		
		if (path == null)
			return null;
		
		String rpath = path.toString().replace('\\', '/');
		
		String dpath = this.getPath().toString().replace('\\', '/');
		
		if (!rpath.startsWith(dpath))
			return null;
		
		return rpath.substring(dpath.length());
	}

	@Override
	public RecordStruct variables() {
		return this.getFieldAsRecord("Variables");
	}

	@Override
	public void addVariable(String name, Struct var) throws OperatingContextException {
		this.getFieldAsRecord("Variables").with(name, var);

		if (var instanceof AutoCloseable) {
			OperationContext run = OperationContext.getOrThrow();

			if (run != null) {
				run.getController().addObserver(new OperationObserver() {
					@Override
					public void completed(OperationContext ctx) {
						try {
							((AutoCloseable) var).close();
						}
						catch (Exception x) {
							Logger.warn("Script could not close and autoclosable var: " + x);
						}
					}
				});
			}
		}
	}

	@Override
	public void clearVariables() {
		this.getFieldAsRecord("Variables").clear();
	}

	@Override
	public Struct queryVariable(String name) {
		if (StringUtil.isEmpty(name))
			return null;

		return this.getFieldAsRecord("Variables").getField(name);
	}
}
