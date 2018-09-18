package dcraft.hub.resource;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.CompositeStruct;
import dcraft.struct.IPartSelector;
import dcraft.struct.PathPart;
import dcraft.struct.Struct;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.Arrays;

public class ResourceBase extends CompositeStruct {
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
	
	@Override
	public Struct select(PathPart... path) {
		if (path.length == 0)
			return this;
		
		PathPart part = path[0];
		
		if (! part.isField()) {
			Logger.warnTr(504, this);
			
			return null;
		}
		
		String fld = part.getField();
		
		Struct o = null;
		
		if ("Name".equals(fld)) {
			o = StringStruct.of(this.name);
		}
		else if ("Tier".equals(fld)) {
			o = this.tier;
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
	public boolean checkLogic(IParentAwareWork stack, XElement source) {
		return false;
	}
	
	@Override
	public boolean isEmpty() {
		return false;
	}
	
	@Override
	protected void doCopy(Struct n) {
		super.doCopy(n);
		
		((ResourceBase) n).name = this.name;
		((ResourceBase) n).tier = this.tier;
	}
	
	@Override
	public Struct deepCopy() {
		ResourceBase t = new ResourceBase();
		this.doCopy(t);
		return t;
	}
	
	@Override
	public void clear() {
		this.cleanup();
	}
	
	@Override
	public void toBuilder(ICompositeBuilder builder) {
		// TODO ?
	}
}
