package dcraft.web.ui;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleResource;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.SchemaHub;
import dcraft.script.StackUtil;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.Html;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlPrinter;
import dcraft.xml.XmlToJsonPrinterOld;

public class JsonPrinter extends XmlToJsonPrinterOld {
	@Override
	public void setOut(PrintStream v) {
		super.setOut(v);
		
		this.jsb.setStreamIndent(1);
	}
	
	@Override
	public void print(XNode root) throws OperatingContextException {
		Html doc = (Html) root;
			
		try {
			this.printPageDef(doc, true);
			
			this.jsb.write("dc.pui.Loader.resumePageLoad();\n\n");
		}
		catch(Exception x) {
			Logger.error("Unable to write page JSON: " + x);
			
			// TODO failed js code
		}
	}
	
	
	public void printPageDef(Html doc, boolean includelayout) throws OperatingContextException {
		RecordStruct page = (RecordStruct) OperationContext.getOrThrow().queryVariable("Page");
		
		this.jsb.write("dc.pui.Loader.addPageDefinition('" + page.getFieldAsString("OriginalPath") + "', ");
		
		try {
			List<XNode> hidden = doc.getHiddenChildren();
			
			this.jsb.startRecord();
			
			// ==============================================
			//  Styles
			// ==============================================

			this.jsb.field("RequireStyles");
			
			this.jsb.startList();
			
			for (XElement func : this.selectAll("Require", hidden)) {
				if (func.hasNotEmptyAttribute("Style"))
					this.jsb.value(func.getAttribute("Style"));
			}
			
			this.jsb.endList();
			
			// ==============================================
			//  Require Types
			// ==============================================

			this.jsb.field("RequireType");
			
			this.jsb.startList();
			
			for (XElement func : this.selectAll("Require", hidden)) {
				if (func.hasNotEmptyAttribute("Types")) {
					String[] types = func.getAttribute("Types").split(",");
					
					for (String name : types) {
						DataType dt = SchemaHub.getType(name);
						
						if (dt != null)
							dt.toJsonDef().toBuilder(this.jsb);        // TODO enhance types to go straight to builder
					}
				}
			}
			
			this.jsb.endList();
			
			// ==============================================
			//  Require Tr
			// ==============================================
			
			this.jsb.field("RequireTr");

			this.jsb.startList();
			
			String locale = OperationContext.getOrThrow().getLocale();
			LocaleResource lres = ResourceHub.getResources().getLocale();
			
			for (XElement func : this.selectAll("Require", hidden)) {
				if (func.hasNotEmptyAttribute("Trs")) {
					String[] trs = func.getAttribute("Trs").split(",");
					
					for (String name : trs) {
						// TODO support ranges - NNN:MMM
						
						if (StringUtil.isDataInteger(name))
							name = "_code_" + name;
						
						this.jsb
								.startRecord()
								.field("Token", name)
								.field("Value", lres.findToken(locale, name))
								.endRecord();
					}
				}
			}
			
			this.jsb.endList();
			
			// ==============================================
			//  Libs
			// ==============================================

			this.jsb.field("RequireLibs");
			
			this.jsb.startList();
			
			for (XElement func : this.selectAll("Require", hidden)) {
				if (func.hasNotEmptyAttribute("Script"))
					this.jsb.value(func.getAttribute("Script"));
			}
			
			this.jsb.endList();
			
			// ==============================================
			//  Functions
			// ==============================================

			this.jsb.field("Functions");
			
			this.jsb.startRecord();
			
			for (XElement func : this.selectAll("Function", hidden)) {
				if (!func.hasNotEmptyAttribute("Name"))
					continue;
				
				this.jsb.field(func.getAttribute("Name"));
				
				StringBuilder sb = new StringBuilder();
				
				sb.append(" function(" + func.getAttribute("Params", "") + ") { ");
				
				String code = valueMacro(func.getText(), func);
				
				code = StringUtil.stripTrailingWhitespace(code);
				
				if (code.charAt(0) != '\n')
					sb.append("\n");
				
				sb.append(code);
				
				sb.append("\n\t\t\t}");
				
				this.jsb.rawValue(sb);
			}
			
			this.jsb.endRecord();
			
			// ==============================================
			//  Load Functions
			// ==============================================

			this.jsb.field("LoadFunctions");
			
			this.jsb.startList();
			
			for (XElement func : this.selectAll("Function", hidden)) {
				if (! "Load".equals(func.getAttribute("Mode")))
					continue;
				
				if (func.hasAttribute("Name")) {
					this.jsb.value(func.getAttribute("Name"));
				}
				else {
					StringBuilder sb = new StringBuilder();
					
					sb.append(" function(" + func.getAttribute("Params", "") + ") { ");
					
					String code = valueMacro(func.getText(), func);
					
					code = StringUtil.stripTrailingWhitespace(code);
					
					if (code.charAt(0) != '\n')
						sb.append("\n");
					
					sb.append(code);
					
					sb.append("\n\t\t\t}");
					
					this.jsb.rawValue(sb);
				}
			}
			
			this.jsb.endList();

			XElement body = doc.findId("dcuiMain");
			
			if ((body != null) && body.hasNotEmptyAttribute("class"))
				this.jsb.field("PageClass", valueMacro(body.getAttribute("class"), doc));
			
			this.jsb.field("Meta", page);

			if (includelayout) {
				this.jsb.field("Layout");
				
				XmlPrinter prt = new HtmlPrinter();
				
				prt.setFormatted(false);
				prt.setOut(this.jsb.startStreamValue());
				
				for (XNode cnode : body.getChildren())
					prt.print(cnode, 0, body);
				
				this.jsb.endStreamValue();
			}

			this.jsb.endRecord();
			
			this.jsb.write(");\n\n");
		}
		catch (Exception x) {
			Logger.error("Unable to write page JSON: " + x);
			
			// TODO failed js code
		}
	}
	
	@Override
	protected String valueMacro(String value, XElement scope) throws OperatingContextException {
		return StackUtil.resolveValueToString(null, value, true);
	}
	
	public List<XElement> selectAll(String path, List<XNode> src) {
		List<XElement> matches = new ArrayList<XElement>();
		this.selectAll(path, src, matches);
		return matches;
	}
	
	/**
	 * Internal, recursive search used by selectAll
	 * 
	 * @param path a backslash delimited string
	 * @param matches list of all matching elements, or empty list if no match
	 */
	protected void selectAll(String path, List<XNode> src, List<XElement> matches) {
		if (src.isEmpty())
			return;
		
		int pos = path.indexOf('/');

		// go back to root not supported
		if (pos == 0)
			return;
		
		String name = null;
		
		if (pos == -1) {
			name = path;
			path = null;
		}
		else { 
			name = path.substring(0, pos);
			path = path.substring(pos + 1);
		}
		
		for (XNode n : src) {
			if (n instanceof XElement) {
				if ("*".equals(name) || ((XElement)n).getName().equals(name)) {
					if (pos == -1) 
						matches.add((XElement)n);
					else  
						((XElement)n).selectAll(path, matches);
				}
			}
		}
	}	
}
