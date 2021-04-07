package dcraft.web.ui.inst.cms;

import dcraft.cms.util.GalleryUtil;
import dcraft.db.BasicRequestContext;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.schema.Load;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.inst.Var;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.*;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class HighlightWidget extends Base implements ICMSAware {
	static public HighlightWidget tag() {
		HighlightWidget el = new HighlightWidget();
		el.setName("dcm.HighlightWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return HighlightWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		XElement template = this.selectFirst("Template");

		if (template == null) {
			this.canonicalize();
			template = this.selectFirst("Template");
		}
		
		List<XElement> entries = this.selectAll("Entry");

		XElement left = this.selectFirst("Left");
		XElement right = this.selectFirst("Right");

		this.clearChildren();

		String display = StackUtil.stringFromSource(state,"Display", "None").toLowerCase();

		// select just one from the list
		if ("random".equals(display)) {
			int selected = RndUtil.random.nextInt(entries.size());

			entries = entries.subList(selected, selected + 1);
		}

		if (left != null) {
			left.setName("div");
			this.with(left);
		}
		else if ("lrcontrol".equals(display)) {
			this.with(W3.tag("div")
					.withClass("dc-widget-highlight-ctrl dc-widget-highlight-ctrl-left")
					.attr("aria-hidden", "true")
					.with(Link.tag()
							.attr("aria-label", "previous item")
							.with(Icon.tag()
									.attr("Path", StackUtil.stringFromSource(state,"LeftIcon", "fas/chevron-left"))
							)
					)
			);
		}

		XElement list =  W3.tag("div")
				.withClass("dc-widget-highlight-list", "pure-g");

		this.with(list);

		if (right != null) {
			right.setName("div");
			this.with(right);
		}
		else if ("lrcontrol".equals(display)) {
			this.with(W3.tag("div")
					.withClass("dc-widget-highlight-ctrl dc-widget-highlight-ctrl-right")
					.attr("aria-hidden", "true")
					.with(Link.tag()
							.attr("aria-label", "next item")
							.with(Icon.tag()
									.attr("Path", StackUtil.stringFromSource(state,"RightIcon", "fas/chevron-right"))
							)
					)
			);
		}

		boolean safemode = "safe".equals(StackUtil.stringFromSource(state,"ContentMode", "Unsafe").toLowerCase());
		
		AtomicLong currimg = new AtomicLong();
		
		for (XElement entry : entries) {
			String alias = entry.getAttribute("Alias");
			
			XElement render = UIUtil.translate(state, entry, safemode);
			
			long cidx = currimg.incrementAndGet();
			
			// setup image for expand
			StackUtil.addVariable(state, "entry-" + cidx, RecordStruct.record()
					.with("Element", entry)
					.with("Alias", alias)
					.with("Rendered", render)
			);
			
			// switch Products during expand
			XElement setvar = Var.tag()
					.withAttribute("Name", "Entry")
					.withAttribute("SetTo", "$entry-" + cidx);
			
			list.with(setvar);
			
			// add nodes using the new variable
			XElement entryxml = template.deepCopy();
			
			for (XNode node : entryxml.getChildren())
				list.with(node);
		}
		
		UIUtil.markIfEditable(state, this, "widget");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("dc-widget", "dc-widget-highlight")
				.withAttribute("data-property-editor", StackUtil.stringFromSource(state,"PropertyEditor"))
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());

		String display = StackUtil.stringFromSource(state,"Display", "None").toLowerCase();
		
		if ("lrcontrol".equals(display)) {
			this.withClass("dc-widget-highlight-lr");
		}
		
		this.setName("div");
	}
	
	@Override
	public void canonicalize() throws OperatingContextException {
		XElement template = this.selectFirst("Template");
		
		if (template == null) {
			// set default
			this.with(Base.tag("Template").with(
					W3.tag("div")
						.withClass("dc-widget-highlight-entry")
						.attr("data-alias", "{$Entry.Alias}")
						.with(
								IncludeParam.tag().attr("Ref", "$Entry.Rendered")
						)
			));
		}
	}
	
	public void canonicalizeEntries() throws OperatingContextException {
		String deflocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();
		
		for (XElement xel : this.selectAll("Entry")) {
			for (int i = 0; i < xel.getChildren().size(); i++) {
				XNode cn = xel.getChild(i);
				
				if (!(cn instanceof XElement))
					continue;
				
				XElement cel = (XElement) cn;
				
				if (!"Tr".equals(cel.getName()))
					continue;
				
				String locale = LocaleUtil.normalizeCode(cel.getAttribute("Locale"));
				
				if (StringUtil.isEmpty(locale))
					cel.attr("Locale", deflocale);
			}
		}
	}

	@Override
	public boolean applyCommand(CommonPath path, XElement root, RecordStruct command) throws OperatingContextException {
		XElement part = this;
		
		String cmd = command.getFieldAsString("Command");
		
		if ("Reorder".equals(cmd)) {
			ListStruct neworder = command.selectAsList("Params.Order");
			
			if (neworder == null) {
				Logger.error("New order is missing");
				return true;		// command was handled
			}
			
			List<XElement> children = this.selectAll("Entry");

			// remove all images
			for (XElement el : children)
				this.remove(el);
			
			// add images back in new order
			for (int i = 0; i < neworder.size(); i++) {
				String alias = neworder.getItemAsString(i);
				boolean fnd = false;

				for (int n = 0; n < children.size(); n++) {
					XElement child = children.get(n);
					String ealias = child.attr("Alias");

					if (StringUtil.isEmpty(ealias))
						continue;

					if (! ealias.equals(alias))
						continue;

					part.with(child);

					break;
				}

				if (! fnd)
					Logger.warn("bad gallery positions");
			}
			
			return true;		// command was handled
		}
		
		if ("UpdatePart".equals(cmd)) {
			// TODO check that the changes made are allowed - e.g. on TextWidget
			RecordStruct params = command.getFieldAsRecord("Params");
			String area = params.selectAsString("Area");
			
			if ("Props".equals(area)) {
				RecordStruct props = params.getFieldAsRecord("Properties");
				
				if (props != null) {
					for (FieldStruct fld : props.getFields()) {
						if (fld.getValue() != null)
							this.attr(fld.getName(), Struct.objectToString(fld.getValue()));
						else
							this.removeAttribute(fld.getName());
					}
				}
				
				return true;
			}
			
			if ("SetEntry".equals(area)) {
				this.canonicalizeEntries();
				
				String alias = params.getFieldAsString("Alias");
				XElement fnd = null;
				
				for (XElement xel : this.selectAll("Entry")) {
					if (alias.equals(xel.getAttribute("Alias"))) {
						fnd = xel;
						break;
					}
				}
				
				if (fnd == null) {
					fnd = XElement.tag("Entry");
					
					if (! params.getFieldAsBooleanOrFalse("AddTop"))
						this.with(fnd);
					else
						this.add(0, fnd);
				}
				
				fnd.clearAttributes();
				
				// rebuild attrs
				fnd.attr("Alias", alias);
				
				RecordStruct props = params.getFieldAsRecord("Properties");
				
				if (props != null) {
					for (FieldStruct fld : props.getFields()) {
						if (fld.getValue() != null)
							fnd.attr(fld.getName(), Struct.objectToString(fld.getValue()));
						else
							fnd.removeAttribute(fld.getName());
					}
				}
				
				String targetcontent = params.getFieldAsString("Content");
				String targetlocale = params.getFieldAsString("Locale");
				
				if (StringUtil.isEmpty(targetlocale))
					targetlocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();
				else
					targetlocale = LocaleUtil.normalizeCode(targetlocale);
				
				for (int i = 0; i < fnd.getChildren().size(); i++) {
					XNode cn = fnd.getChild(i);
					
					if (! (cn instanceof XElement))
						continue;
					
					XElement cel = (XElement) cn;
					
					if (! "Tr".equals(cel.getName()))
						continue;
					
					// will have one, see canonicalize above
					String locale = LocaleUtil.normalizeCode(cel.getAttribute("Locale"));
					
					if (locale.equals(targetlocale)) {
						cel.value(targetcontent);
						return true;
					}
				}
				
				fnd.with(
						XElement.tag("Tr")
								.attr("Locale", targetlocale)
								.withCData(targetcontent)
				);
				
				return true;
			}
			
			if ("RemoveEntry".equals(area)) {
				String alias = params.getFieldAsString("Alias");
				XElement fnd = null;
				
				for (XElement xel : this.selectAll("Entry")) {
					if (alias.equals(xel.getAttribute("Alias"))) {
						fnd = xel;
						break;
					}
				}
				
				if (fnd != null) {
					this.remove(fnd);
				}
				
				return true;
			}
			
			if ("Template".equals(area)) {
				this.canonicalize();    // so all Tr's have a Locale
				
				String targetcontent = params.getFieldAsString("Template");
				
				String template = "<Template>" + targetcontent + "</Template>";
				
				try (OperationMarker om = OperationMarker.clearErrors()) {
					XElement txml = ScriptHub.parseInstructions(template);
					
					if (!om.hasErrors() && (txml != null)) {
						XElement oldtemp = this.selectFirst("Template");
						
						if (oldtemp != null)
							this.remove(oldtemp);
						
						this.with(txml);
					} else {
						Logger.warn("Keeping old template, new one is not valid.");
					}
				}
				catch (Exception x) {
				}
				
				return true;
			}
		}
		
		return false;
	}
}
