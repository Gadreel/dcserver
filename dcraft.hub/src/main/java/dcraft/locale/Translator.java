package dcraft.locale;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.struct.IPartSelector;
import dcraft.struct.PathPart;
import dcraft.struct.Struct;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.Arrays;

public class Translator extends Struct implements IPartSelector {
	static public Translator of(ResourceTier resources) {
		Translator tr = new Translator();
		
		while (resources != null) {
			tr.resource = resources.getLocale();
			
			if (tr.resource != null)
				break;
			
			resources = resources.getParent();
		}
		
		return tr;
	}
	
	protected LocaleResource resource = null;
	
	@Override
	public Struct select(String path) {
		return this.select(PathPart.parse(path));
	}
	
	@Override
	public Struct select(PathPart... path) {
		if (path.length == 0)
			return this;

		PathPart part = path[0];

		String token = part.isField() ? part.getField() : "_code_" + String.valueOf(part.getIndex());

		Object[] params = new Object[path.length - 1];

		for (int i = 1; i < path.length; i++) {
			part = path[i];

			params[i - 1] = part.isField() ? part.getField() : String.valueOf(part.getIndex());
		}

		String out = this.resource.tr(token, (Object[]) params);

		if (out != null)
			return StringStruct.of(out);

		Logger.warnTr(503, token);
		return null;
	}
	
	@Override
	protected void doCopy(Struct n) {
		super.doCopy(n);
		
		Translator nn = (Translator)n;
		
		nn.resource = this.resource;
	}
	
	@Override
	public Struct deepCopy() {
		Translator cp = new Translator();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public boolean isNull() {
		return false;
	}

	@Override
	public String toString() {
		return "_Tr";
	}
}
