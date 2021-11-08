package dcraft.script;

import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.IVariableProvider;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.SchemaHub;
import dcraft.script.inst.Instruction;
import dcraft.script.inst.Main;
import dcraft.script.work.InstructionWork;
import dcraft.struct.*;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.*;
import dcraft.util.ArrayUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.Arrays;

public class StackUtil {
	static public IWork of(IParentAwareWork parent, Instruction... instructions) {
		if ((instructions.length == 1) && (instructions[0] instanceof Main))
			return instructions[0].createStack(parent);
		
		Main main = Main.tag();
		
		main.with(instructions);
		
		return main.createStack(parent);
	}
	
	static public IWork of(Instruction... instructions) {
		if ((instructions.length == 1) && (instructions[0] instanceof Main))
			return ((Main) instructions[0]).createStack();
		
		Main main = Main.tag();
		
		main.with(instructions);
		
		return main.createStack();
	}

	static public BaseStruct refFromSource(InstructionWork stackWork, String attr) throws OperatingContextException {
		return StackUtil.refFromElement(stackWork, stackWork.getInstruction(), attr, true);
	}
	
	static public BaseStruct refFromSource(InstructionWork stackWork, String attr, boolean cleanrefs) throws OperatingContextException {
		return StackUtil.refFromElement(stackWork, stackWork.getInstruction(), attr, cleanrefs);
	}

	static public BaseStruct refFromElement(IParentAwareWork stackWork, XElement el, String attr) throws OperatingContextException {
		return StackUtil.refFromElement(stackWork, el, attr, true);
	}
	
	static public BaseStruct refFromElement(IParentAwareWork stackWork, XElement el, String attr, boolean cleanrefs) throws OperatingContextException {
		if ((el == null) || StringUtil.isEmpty(attr))
			return null;
		
		return StackUtil.resolveReference(stackWork, el.getAttribute(attr), cleanrefs);
	}

	static public BaseStruct resolveReference(IParentAwareWork stack, String val) throws OperatingContextException {
		return StackUtil.resolveReference(stack, val, true);
	}

	static public BaseStruct resolveReference(IParentAwareWork stack, String val, boolean cleanrefs) throws OperatingContextException {
		if (val == null)
			return null;

		//val = StackUtil.resolveValueToString(stack, val);

		// var flag - return the reference to the variable pointed to
		if (val.startsWith("$"))
			return StackUtil.queryVariable(stack, val.substring(1));

		// literal flag - return the string as is
		if (val.startsWith("`"))
			val = val.substring(1);

		// otherwise just treat this as a string
		return StringStruct.of(StackUtil.resolveValueToString(stack, val, cleanrefs));
	}

	static public String stringFromSource(InstructionWork stackWork, String attr) throws OperatingContextException {
		return StackUtil.stringFromElement(stackWork, stackWork.getInstruction(), attr, null);
	}
	
	static public String stringFromSource(InstructionWork stackWork, String attr, String def) throws OperatingContextException {
		return StackUtil.stringFromElement(stackWork, stackWork.getInstruction(), attr, def);
	}
	
	static public String stringFromElement(IParentAwareWork stackWork, XElement el, String attr) throws OperatingContextException {
		return StackUtil.stringFromElement(stackWork, el, attr, null);
	}
	
	static public String stringFromElement(IParentAwareWork stackWork, XElement el, String attr, String def) throws OperatingContextException {
		if ((el == null) || StringUtil.isEmpty(attr))
			return def;

		String ret = StackUtil.resolveValueToString(stackWork, el.getAttribute(attr));

		if (StringUtil.isNotEmpty(ret))
			return ret;
		
		return def;
	}

	static public String stringFromSourceClean(InstructionWork stackWork, String attr) throws OperatingContextException {
		return StackUtil.stringFromElementClean(stackWork, stackWork.getInstruction(), attr, null);
	}

	static public String stringFromSourceClean(InstructionWork stackWork, String attr, String def) throws OperatingContextException {
		return StackUtil.stringFromElementClean(stackWork, stackWork.getInstruction(), attr, def);
	}

	static public String stringFromElementClean(IParentAwareWork stackWork, XElement el, String attr) throws OperatingContextException {
		return StackUtil.stringFromElementClean(stackWork, el, attr, null);
	}

	static public String stringFromElementClean(IParentAwareWork stackWork, XElement el, String attr, String def) throws OperatingContextException {
		if ((el == null) || StringUtil.isEmpty(attr))
			return def;

		String ret = StackUtil.resolveValueToString(stackWork, el.getAttribute(attr), true);

		if (StringUtil.isNotEmpty(ret))
			return ret;

		return def;
	}

	static public long intFromSource(InstructionWork stackWork, String attr) throws OperatingContextException {
		return StackUtil.intFromElement(stackWork, stackWork.getInstruction(), attr, 0);
	}
	
	static public long intFromSource(InstructionWork stackWork, String attr, int def) throws OperatingContextException {
		return StackUtil.intFromElement(stackWork, stackWork.getInstruction(), attr, def);
	}
	
	static public long intFromElement(IParentAwareWork stackWork, XElement el, String attr) throws OperatingContextException {
		return StackUtil.intFromElement(stackWork, el, attr, 0);
	}
	
	static public long intFromElement(IParentAwareWork stackWork, XElement el, String attr, int def) throws OperatingContextException {
		if ((el == null) || StringUtil.isEmpty(attr))
			return def;
		
		Object ret1 = StackUtil.refFromElement(stackWork, el, attr, true);

		//if (ret1 == null)
		//	ret1 = StackUtil.resolveValueToString(stackWork, el.getAttribute(attr));

		Long ret = Struct.objectToInteger(ret1);
		
		if (ret != null)
			return ret;
		
		return def;
	}
	
	static public boolean boolFromSource(InstructionWork stackWork, String attr) throws OperatingContextException {
		return StackUtil.boolFromElement(stackWork, stackWork.getInstruction(), attr, false);
	}
	
	static public boolean boolFromSource(InstructionWork stackWork, String attr, boolean def) throws OperatingContextException {
		return StackUtil.boolFromElement(stackWork, stackWork.getInstruction(), attr, def);
	}
	
	static public boolean boolFromElement(IParentAwareWork stackWork, XElement el, String attr) throws OperatingContextException {
		return StackUtil.boolFromElement(stackWork, el, attr, false);
	}
	
	static public boolean boolFromElement(IParentAwareWork stackWork, XElement el, String attr, boolean def) throws OperatingContextException {
		if ((el == null) || StringUtil.isEmpty(attr))
			return def;

		Object ret1 = StackUtil.refFromElement(stackWork, el, attr, true);

		//if (ret1 == null)
		//	ret1 = StackUtil.resolveValueToString(stackWork, el.getAttribute(attr));

		return Struct.objectToBoolean(ret1, def);
	}
	
	// stack can be null
	static public String resolveValueToString(IParentAwareWork stack, String val) throws OperatingContextException {
		return StackUtil.resolveValueToString(stack, val, false);
	}

	// stack can be null
	static public String resolveValueToString(IParentAwareWork stack, String val, boolean cleanRefs) throws OperatingContextException {
		if (val == null)
			return "";
		
		OperationContext context = OperationContext.getOrThrow();
		
		// the expansion of variables is per Attribute Value Templates in XSLT
		// http://www.w3.org/TR/xslt#attribute-value-templates
		
		StringBuilder sb = new StringBuilder();

		int lpos = 0;
		int bpos = val.indexOf("{$");
		
		while (bpos != -1) {
			int epos = val.indexOf("}", bpos);
			if (epos == -1)
				break;
			
			sb.append(val.substring(lpos, bpos));
			
			lpos = epos + 1;
			
			String varname = val.substring(bpos + 2, epos);  //.trim();
			
			// TODO add support for formatting - {$varname|op:LeftPad:Size=7:With=*|op}
			//String fmtcmd = null;
			//String fmt = null;
			
			String[] vparts = varname.split("\\|");

			BaseStruct qvar2 = StackUtil.queryVariable(stack, vparts[0]);
			
			if (qvar2 != null) {
				// may want to add ` in front of $ at start, review for evidence of this
				Object qval2 = DataUtil.format(qvar2, Arrays.copyOfRange(vparts, 1, vparts.length));
				
				if (qval2 != null) {
					// do not allow nested variables, that would be a major security issue!!  consider values pulled from user input
					String qval2s = Struct.objectToString(qval2);
					
					if (qval2s != null) {
						sb.append(qval2s.replace("{$", "{`$"));
					}
				}
			}
			else if (! cleanRefs) {
				sb.append(val.substring(bpos, epos + 1));
			}
			else {
				// TODO trace
				//Logger.warnTr(500, varname);
				
				Object qval2 = DataUtil.format(null, Arrays.copyOfRange(vparts, 1, vparts.length));
				
				if (qval2 != null){
					// do not allow nested variables, that would be a major security issue!!  consider values pulled from user input
					String qval2s = Struct.objectToString(qval2);
					
					if (qval2s != null) {
						sb.append(qval2s.replace("{$", "{`$"));
					}
				}
			}
			
			bpos = val.indexOf("{$", epos);
		}
		
		sb.append(val.substring(lpos));
		
		return sb.toString();
	}
	
	static public void addVariableOfType(IParentAwareWork stack, String type, String name) throws OperatingContextException {
		DataType mut = SchemaHub.getTypeOrError(type);
		
		if (mut == null)
			Logger.error("Unable to create variable of type: " + type);
		else
			StackUtil.addVariable(stack, name, mut.create());
	}
	
	static public void addVariable(IParentAwareWork stack, String name, BaseStruct var) throws OperatingContextException {
		if (var == null)
			var = NullStruct.instance;
		
		IVariableProvider vp = StackUtil.queryVarProvider(stack);
		
		if (vp != null)
			vp.addVariable(name, var);
		else
			Logger.errorTr(513, name);
	}
	
	// stack can be null
	static public BaseStruct queryVariable(IParentAwareWork stack, String name) throws OperatingContextException {
		if (StringUtil.isEmpty(name))
			return null;
		
		IVariableAware vp = StackUtil.queryVarAware(stack);

		if (vp == null)
			vp = OperationContext.getOrThrow();

		String[] vparts = name.split("\\.");

		name = "";

		for (String vpart : vparts) {
			if (name.length() == 0) {
				name = vpart;
			}
			else {
				if (vpart.startsWith("$")) {
					BaseStruct qvar = StackUtil.queryVariable(stack, vpart.substring(1));

					if (qvar != null) {
						name += "." + qvar.toString();
						continue;
					}
				}

				name += "." + vpart;
			}
		}

		int dotpos = name.indexOf(".");
		
		if (dotpos > -1) {
			String oname = name.substring(0, dotpos);

			BaseStruct ov = vp.queryVariable(oname);
			
			if (ov == null) {
				//Logger.errorTr(510, oname);
				return null;
			}
			
			if (! (ov instanceof IPartSelector)) {
				//Logger.errorTr(511, oname);
				return null;
			}
			
			return ((IPartSelector)ov).select(name.substring(dotpos + 1));
		}

		return vp.queryVariable(name);
	}
	
	static public IVariableAware queryVarAware(IParentAwareWork stack) throws OperatingContextException {
		while (stack != null) {
			if (stack instanceof IVariableAware)
				return (IVariableAware) stack;
			
			stack = stack.getParent();
		}
		
		return OperationContext.getOrThrow();
	}
	
	static public IVariableProvider queryVarProvider(IParentAwareWork stack) throws OperatingContextException {
		while (stack != null) {
			if (stack instanceof IVariableProvider)
				return (IVariableProvider) stack;
			
			stack = stack.getParent();
		}

		return OperationContext.getOrThrow();
	}

	static public void dumpVariableStack(IParentAwareWork stack) throws OperatingContextException {
		while (stack != null) {
			System.out.println("Level: " + stack.getClass().getCanonicalName() + "-" + stack);

			if (stack instanceof IVariableProvider) {
				IVariableProvider vp = (IVariableProvider) stack;

				RecordStruct vars = vp.variables();

				if (vars != null) {
					for (FieldStruct fld : vars.getFields()) {
						System.out.println("    - " + fld.getName() + " = " + fld.getValue());
					}
				}
			}

			System.out.println("---------------");

			stack = stack.getParent();
		}
	}

	static public void dumpVariableStack(IVariableProvider stack) throws OperatingContextException {
		System.out.println("Level: " + stack.getClass().getCanonicalName() + "-" + stack);

		RecordStruct vars = stack.variables();

		if (vars != null) {
			for (FieldStruct fld : vars.getFields()) {
				System.out.println("    - " + fld.getName() + " = " + fld.getValue());
			}
		}

		System.out.println("---------------");
	}

	static public IProgressAwareWork queryProgAware(IParentAwareWork stack) {
		while (stack != null) {
			if (stack instanceof IProgressAwareWork)
				return (IProgressAwareWork) stack;
			
			stack = stack.getParent();
		}
		
		return null;
	}
	
	static public IResultAwareWork queryResultAware(IParentAwareWork stack) {
		while (stack != null) {
			if (stack instanceof IResultAwareWork)
				return (IResultAwareWork) stack;
			
			stack = stack.getParent();
		}
		
		return null;
	}

	// Result Code and Value
	
	public void setExitCode(IParentAwareWork stack, Long code, String msg) throws OperatingContextException {
		IResultAwareWork vp = StackUtil.queryResultAware(stack);
		
		if (vp != null)
			vp.setExitCode(code, msg);
	}
	
	public void setExitCodeTr(IParentAwareWork stack, Long code, Object... params) throws OperatingContextException {
		IResultAwareWork vp = StackUtil.queryResultAware(stack);
		
		if (vp != null)
			vp.setExitCodeTr(code, params);
	}
	
	public long getExitCode(IParentAwareWork stack) throws OperatingContextException {
		IResultAwareWork vp = StackUtil.queryResultAware(stack);
		
		if (vp != null)
			return vp.getExitCode();
		
		return 0;
	}
	
	public String getExitMessage(IParentAwareWork stack) throws OperatingContextException {
		IResultAwareWork vp = StackUtil.queryResultAware(stack);
		
		if (vp != null)
			return vp.getExitMessage();
		
		return null;
	}
	
	public boolean hasExitError(IParentAwareWork stack) throws OperatingContextException {
		IResultAwareWork vp = StackUtil.queryResultAware(stack);
		
		if (vp != null)
			return vp.hasExitErrors();
		
		return false;
	}
	
	public BaseStruct getExitResult(IParentAwareWork stack) throws OperatingContextException {
		IResultAwareWork vp = StackUtil.queryResultAware(stack);
		
		if (vp != null)
			return vp.getResult();
		
		return null;
	}
	
	public void setExitResult(IParentAwareWork stack, BaseStruct value) throws OperatingContextException {
		IResultAwareWork vp = StackUtil.queryResultAware(stack);
		
		if (vp != null)
			vp.setResult(value);
	}
	
	/* TODO review
	public void setLastResult(OperationOutcomeStruct v) throws OperatingContextException {
		IResultAwareWork vp = StackUtil.queryResultAware(stack);
		
		if (vp != null) {
	        vp.setExitCode(v., );  -- get code and message from OO
	        vp.setResult(v.getResult());
    	}
    }
    */
}
