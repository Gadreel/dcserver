package dcraft.mail.dcc;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceFileInfo;
import dcraft.mail.dcc.inst.EmailComment;
import dcraft.mail.dcc.inst.InjectContent;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.W3Closed;
import dcraft.xml.*;

import java.util.Map;

public class HtmlPrinter extends XmlPrinter {
	/*
	this.compacttags.add("a");
	this.compacttags.add("abbr");
	this.compacttags.add("b");
	this.compacttags.add("button");
	this.compacttags.add("caption");
	this.compacttags.add("cite");
	this.compacttags.add("em");
	this.compacttags.add("i");
	this.compacttags.add("label");
	this.compacttags.add("legend");
	this.compacttags.add("mark");
	this.compacttags.add("q");
	this.compacttags.add("s");
	this.compacttags.add("small");
	this.compacttags.add("span");
	this.compacttags.add("strong");
	this.compacttags.add("title");
	this.compacttags.add("u");
	*/
	
	protected String valueMacro(String value, XElement scope) throws OperatingContextException {
		return StackUtil.resolveValueToString(null, value,true);
	}
	
	@Override
	public void print(XNode root) throws OperatingContextException {
    	this.out.append("<!DOCTYPE html>\n");
		
		super.print(root);
	}
	
	public void printFormatLead(int level) {
		// Add leading newline and spaces, if necessary
		if (formatted && level > 0) {
			this.out.append("\n");
			for (int i = level; i > 0; i--)
				this.out.append("\t");
		}
	}
	
	@Override
	public void print(XNode node, int level, XElement parent, boolean clearvars) throws OperatingContextException {
		if (node instanceof XText) {
			String val = ((XText) node).getRawValue();

			if (clearvars)
				val = this.valueMacro(val, parent);
			
			if (val != null) {
				this.printFormatLead(level);
				this.out.append(val); 
			}
		}
		else if (node instanceof EmailComment) {
			this.printFormatLead(level);

			String comment = ((EmailComment) node).getText();

			this.out.append("<!--" + comment + "-->");

			//System.out.println("include the comment: " + ((XComment) node).getValue());

			// ((XComment) node).toBuilder(this.out);
		}
		else if (node instanceof InjectContent) {
			this.printFormatLead(level);

			String path = ((InjectContent) node).attr("Path");

			ResourceFileInfo fileInfo = ResourceHub.getResources().getComm().findFile(CommonPath.from(path));

			if (fileInfo != null) {
				CharSequence content = IOUtil.readEntireFile(fileInfo.getActualPath());

				if (StringUtil.isNotEmpty(content))
					this.out.append(content);
			}

			//System.out.println("include the comment: " + ((XComment) node).getValue());

			// ((XComment) node).toBuilder(this.out);
		}
		else if ((node instanceof XElement) && "Style".equals(((XElement)node).getName()) && "head".equals(parent.getName())) {
			this.printFormatLead(level);

			String path = ((XElement) node).attr("Path");

			if (StringUtil.isNotEmpty(path)) {
				ResourceFileInfo fileInfo = ResourceHub.getResources().getComm().findFile(CommonPath.from(path));

				if (fileInfo != null) {
					CharSequence content = IOUtil.readEntireFile(fileInfo.getActualPath());

					if (StringUtil.isNotEmpty(content)) {
						this.out.append("<style>\n");
						this.out.append(content);
						this.out.append("\n</style>\n");
					}
				}
			}
			else {
				CharSequence content = ((XElement)node).getText();

				if (StringUtil.isNotEmpty(content)) {
					this.out.append("<style>\n");
					this.out.append(content);
					this.out.append("\n</style>\n");
				}
			}

			//System.out.println("include the comment: " + ((XComment) node).getValue());

			// ((XComment) node).toBuilder(this.out);
		}
		else if (node instanceof XElement) {
			if ((node instanceof Base) && ((Base)node).isExclude())
				return;

			this.printFormatLead(level);
			
			XElement el = (XElement) node;

			if (el.hasAttribute("dc:unresolvedvars"))
				clearvars = ! el.getAttributeAsBooleanOrFalse("dc:unresolvedvars");

			String name = el.getName();
			
			// skip upper case "parameter" style tags
			if (StringUtil.isEmpty(name) || ! Character.isLowerCase(name.charAt(0)))
				return;
			
			if (name.contains(".")) {
				this.out.append("<!-- found unsupported tag " + name + " -->");
				
				if (formatted)
					this.out.append("\n");
				
				return;
			}				
			
			// Put the opening tag out
			this.out.append("<" + name);
	
			// Write the attributes out
			if (el.hasAttributes()) 
				for (Map.Entry<String, String> entry : el.getAttributes().entrySet()) {
					String aname = entry.getKey();
					
					// remove all uppercase led attrs
					if (Character.isLowerCase(aname.charAt(0))) {
						this.out.append(" " + aname + "=");
						
						// note that we do not officially support and special entities in our code
						// except for the XML standards of &amp; &lt; &gt; &quot; &apos;
						// while entities - including &nbsp; - may work in text nodes we aren't supporting
						// them in attributes and suggest the dec/hex codes - &spades; should be &#9824; or &#x2660;
						String normvalue = XNode.unquote(entry.getValue());
						String expandvalue = clearvars ? this.valueMacro(normvalue, el) : normvalue;
						
						this.out.append("\"" + XNode.quote(expandvalue) + "\"");		
					}
				}
	
			// write out the closing tag or other elements
			boolean formatThis = formatted;
			boolean fndelement = false;
			
			if (! el.hasChildren() && (el instanceof W3Closed)) {
				this.out.append(" /> ");
			} 
			else if (! el.hasChildren()) {
				this.out.append(">");
				this.out.append("</" + name + "> ");
			} 
			else {
				this.out.append(">");
				
				for (XNode cnode : el.getChildren()) {
					if (cnode instanceof XText)
						formatThis = false;
					else
						fndelement = true;		
				}
				
				// Add leading newline and spaces, if necessary
				if ((formatThis || fndelement) && formatted) {
					for (XNode cnode : el.getChildren()) 
						this.print(cnode, level + 1, el, clearvars);
					
					this.out.append("\n");
					
					for (int i = level; i > 0; i--)
						this.out.append("\t");
				}
				else {
					for (XNode cnode : el.getChildren()) 
						this.print(cnode, 0, el, clearvars);
				}
				
				// Now put the closing tag out
				this.out.append("</" + name + "> ");
			}
		}
	}
}
