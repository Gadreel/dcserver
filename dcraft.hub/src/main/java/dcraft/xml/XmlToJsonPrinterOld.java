package dcraft.xml;

import java.io.PrintStream;
import java.util.Map.Entry;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.builder.JsonStreamBuilder;

public class XmlToJsonPrinterOld extends XmlPrinter {
	protected JsonStreamBuilder jsb = null;
	
	public JsonStreamBuilder getStream() {
		return this.jsb;
	}
	
	@Override
	public void setOut(PrintStream v) {
		super.setOut(v);
		
		this.jsb = new JsonStreamBuilder(v, this.formatted);
	}
	
	protected String valueMacro(String v, XElement scope) throws OperatingContextException {
		return v;		// no macro by default, override to use
	}
	
	@Override
	public void print(XNode node, int level, XElement parent, boolean clearvars) throws OperatingContextException {
		try {
			if (node instanceof XText) {
				this.jsb.value(this.valueMacro(((XText)node).getValue(), parent));
			}
			else if (node instanceof XElement) {
				XElement el = (XElement) node;
				
				this.jsb.startRecord();
				
				for (Entry<String, String> aentry : el.getAttributes().entrySet())
					this.jsb.field(aentry.getKey(), this.valueMacro(aentry.getValue(), el));
				
				this.jsb.field("_name", el.getName());
				
				if (el.hasChildren()) {
					this.jsb.field("_children");
					this.jsb.startList();
					
					for (XNode child : el.getChildren())
						this.print(child, level + 1, el, clearvars);
					
					this.jsb.endList();
				}
				
				this.jsb.endRecord();
			}
		}
		catch (Exception x) {
			System.out.println("bad json printer: " + x);
		}
	}
}
