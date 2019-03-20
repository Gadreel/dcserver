package dcraft.xml;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.JsonBuilder;
import dcraft.struct.builder.JsonStreamBuilder;

import java.io.PrintStream;
import java.util.Map.Entry;

public class XmlToJsonPrinter extends XmlPrinter {
	protected ICompositeBuilder jsb = null;
	
	@Override
	public void setOut(PrintStream v) {
		super.setOut(v);
		
		this.jsb = new JsonStreamBuilder(v, this.formatted);
	}
	
	protected String valueMacro(String v, XElement scope) throws OperatingContextException {
		return v;		// no macro by default, override to use
	}
	
	@Override
	public void print(XNode node, int level, XElement parent) throws OperatingContextException {
		try {
			if (node instanceof XText) {
				XText text = (XText) node;
				
				this.jsb.startRecord();
				
				this.jsb.field("type", text.isCData() ? "cdata" : "text");
				this.jsb.field("content", this.valueMacro(text.getValue(), parent));
				
				this.jsb.endRecord();
			}
			else if (node instanceof XComment) {
				XComment comment = (XComment) node;
				
				this.jsb.startRecord();
				
				this.jsb.field("type", "comment");
				this.jsb.field("content", comment.getValue());
				
				this.jsb.endRecord();
			}
			else if (node instanceof XElement) {
				XElement el = (XElement) node;
				
				this.jsb.startRecord();
				
				this.jsb.field("type", "element");
				this.jsb.field("name", el.getName());
				
				if (el.hasAttributes()) {
					this.jsb.field("children");
					this.jsb.startRecord();
					
					for (Entry<String, String> aentry : el.getAttributes().entrySet())
						this.jsb.field(aentry.getKey(), this.valueMacro(aentry.getValue(), el));
					
					this.jsb.endRecord();
				}
				
				if (el.hasChildren()) {
					this.jsb.field("children");
					this.jsb.startList();
					
					for (XNode child : el.getChildren())
						this.print(child, level + 1, el);
					
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
