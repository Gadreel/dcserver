package dcraft.xml;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.JsonStreamBuilder;
import dcraft.struct.builder.ObjectBuilder;

import java.io.PrintStream;
import java.util.Map.Entry;

public class XmlToJson {
	static public RecordStruct convertXml(XElement root, boolean stripempty) throws OperatingContextException {
		ObjectBuilder builder = new ObjectBuilder();
		
		XmlToJson tool = new XmlToJson();
		tool.stripempty = stripempty;
		tool.jsb = builder;
		tool.convert(root);
		
		return (RecordStruct) builder.getRoot();
	}
	
	protected ICompositeBuilder jsb = null;
	protected boolean stripempty = true;
	
	protected String valueMacro(String v, XElement scope) throws OperatingContextException {
		return v;		// no macro by default, override to use
	}
	
	public void convert(XElement root) throws OperatingContextException {
		this.convert(root, 0, null);
	}
	
	public void convert(XNode node, int level, XElement parent) throws OperatingContextException {
		try {
			if (node instanceof XText) {
				XText text = (XText) node;
				
				if (text.isNotEmpty()) {
					this.jsb.startRecord();
					
					this.jsb.field("type", text.isCData() ? "cdata" : "text");
					this.jsb.field("content", this.valueMacro(text.getValue(), parent));
					
					this.jsb.endRecord();
				}
			}
			else if (node instanceof XComment) {
				XComment comment = (XComment) node;
				
				if (comment.isNotEmpty()) {
					this.jsb.startRecord();
					
					this.jsb.field("type", "comment");
					this.jsb.field("content", comment.getValue());
					
					this.jsb.endRecord();
				}
			}
			else if (node instanceof XElement) {
				XElement el = (XElement) node;
				
				this.jsb.startRecord();
				
				this.jsb.field("type", "element");
				this.jsb.field("name", el.getName());
				
				if (el.hasAttributes()) {
					this.jsb.field("attributes");
					this.jsb.startRecord();
					
					for (Entry<String, String> aentry : el.getAttributes().entrySet())
						this.jsb.field(aentry.getKey(), this.valueMacro(aentry.getValue(), el));
					
					this.jsb.endRecord();
				}
				
				if (el.hasChildren()) {
					this.jsb.field("children");
					this.jsb.startList();
					
					for (XNode child : el.getChildren())
						this.convert(child, level + 1, el);
					
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
