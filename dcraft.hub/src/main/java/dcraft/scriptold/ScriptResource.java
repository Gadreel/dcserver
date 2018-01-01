package dcraft.scriptold;

import java.util.HashMap;
import java.util.Map;

import dcraft.hub.resource.ResourceBase;
import dcraft.scriptold.mutator.Substring;
import dcraft.xml.XElement;

public class ScriptResource extends ResourceBase {
	protected Map<String, Class<?>> instsclass = new HashMap<>();
	
	// operator is type specific - [Type][Name] = mutator
	protected Map<String, Map<String, IOperator>> operationExtensions = new HashMap<String, Map<String,IOperator>>();
	
	protected IDebuggerHandler debugger = null;

	public ScriptResource() {
		this.setName("Script");
		
		// TODO move and load from packages/settings
		HashMap<String, IOperator> stringextensions = new HashMap<String, IOperator>();
		stringextensions.put("Substring", new Substring());
		
		this.operationExtensions.put("String", stringextensions);
	}
	
	public ScriptResource getParentResource() {
		if (this.tier == null)
			return null;
		
		/* --- cleanup
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getScripts();
		*/
		
		return null;
	}
	
	public void registerTierDebugger(IDebuggerHandler v) {
		this.debugger = v;
	}
	
	public IDebuggerHandler getDebugger() {
		IDebuggerHandler db = this.debugger;
		
		if (db != null)
			return db;
		
		ScriptResource parent = this.getParentResource();

		if (parent != null)
			return parent.getDebugger();
		
		return null;
	}
	
	public void loadInstruction(XElement config) {
		// TODO
	}
	
	public void loadOperation(XElement config) {
		// TODO
	}

	public IOperator getOperation(String type, String op) {
		Map<String, IOperator> typeextensions = this.operationExtensions.get(type);
		
		if (typeextensions != null) {
			IOperator mut = typeextensions.get(op);
	
			if (mut != null) 
				return mut;
		}
		
		ScriptResource parent = this.getParentResource();

		if (parent != null)
			return parent.getOperation(type, op);
		
		return null;
	}
	
	public Instruction createInstruction(XElement def) {
		return null;
	}
}
