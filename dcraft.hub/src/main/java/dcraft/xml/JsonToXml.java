package dcraft.xml;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.struct.*;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class JsonToXml {
	static public XElement convertJson(RecordStruct root) throws OperatingContextException {
		XmlReader reader = ScriptHub.instructionsReader();
		
		JsonToXml tool = new JsonToXml();
		tool.reader = reader;
		tool.convert(root);
		
		return reader.getTop();
	}
	
	protected IParseHandler reader = null;
	
	protected String valueMacro(String v, RecordStruct scope) throws OperatingContextException {
		return v;		// no macro by default, override to use
	}
	
	public void convert(RecordStruct root) throws OperatingContextException {
		this.convert(root, 0);
	}
	
	public void convert(RecordStruct node, int level) throws OperatingContextException {
		if (node == null) {
			Logger.error("Invalid json to xml - missing node");
			return;
		}
		
		if ("text".equals(node.getFieldAsString("type"))) {
			this.reader.text(node.getFieldAsString("content"), false, 0, 0);
		}
		else if ("cdata".equals(node.getFieldAsString("type"))) {
			this.reader.text(node.getFieldAsString("content"), true, 0, 0);
		}
		else if ("comment".equals(node.getFieldAsString("type"))) {
			this.reader.comment(node.getFieldAsString("content"), 0, 0);
		}
		else if ("element".equals(node.getFieldAsString("type"))) {
			if (node.isFieldEmpty("name")) {
				Logger.error("Missing element name - json to xml converter");
				return;
			}
			
			Map<String,String> amap = new HashMap<>();
			
			RecordStruct attrs = node.getFieldAsRecord("attributes");
			
			if (attrs != null) {
				for (FieldStruct fld : attrs.getFields()) {
					if (fld.getValue() != null)
						amap.put(fld.getName(), Struct.objectToString(fld.getValue()));
					else
						amap.put(fld.getName(), "");
				}
			}
			
			ListStruct children = node.getFieldAsList("children");
			
			if ((children == null) || (children.size() == 0)) {
				this.reader.element(node.getFieldAsString("name"), amap, 0, 0);
			}
			else {
				this.reader.startElement(node.getFieldAsString("name"), amap, 0, 0);
				
				for (BaseStruct child : children.items()) {
					if (child instanceof RecordStruct) {
						this.convert((RecordStruct) child, level + 1);
					}
				}
				
				this.reader.endElement(node.getFieldAsString("name"));
			}
		}
		else {
			Logger.error("Invalid node type - json to xml converter");
		}
	}
}
