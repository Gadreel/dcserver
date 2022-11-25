package dcraft.web.ui.inst.cms;

import dcraft.cms.util.GalleryImageConsumer;
import dcraft.cms.util.GalleryUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.inst.Var;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.*;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.W3Closed;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlToJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class LogosWidget extends Base implements ICMSAware {
	static public LogosWidget tag() {
		LogosWidget el = new LogosWidget();
		el.setName("dcm.LogosWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return LogosWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		long maximgs = StackUtil.intFromSource(state,"Max", 24);
		
		XElement template = this.selectFirst("Template");
		
		List<XElement> images = this.selectAll("Image");
		
		this.clearChildren();
		
		String path = StackUtil.stringFromSource(state,"Path");
		String missing = StackUtil.stringFromSource(state,"Missing");

		int currimg = 0;

		for (XElement image : images) {
			currimg++;

			if (currimg > maximgs)
				break;

			RecordStruct img = RecordStruct.record()
					.with("Alias", image.getAttribute("Alias"))
					.with("Path", path)
					.with("Position", currimg)
					.with("Element", XmlToJson.convertXml(image,true, true));

			String lpath = path + "/" + image.getAttribute("Alias");

			Path imgpath = OperationContext.getOrThrow().getSite().findSectionFile("files", lpath,
					OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

			boolean found = false;

			if ((imgpath == null) || ! Files.exists(imgpath)) {
				if (StringUtil.isNotEmpty(missing)) {
					lpath = path + "/" + missing;

					imgpath = OperationContext.getOrThrow().getSite().findSectionFile("galleries", lpath,
							OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

					if ((imgpath != null) && Files.exists(imgpath)) {
						found = true;
					}
				}
			}
			else {
				found = true;
			}

			if (found) {
				try {
					FileTime fileTime = Files.getLastModifiedTime(imgpath);

					img.with("Path", "/files" + lpath + "?dc-cache=" + TimeUtil.stampFmt.format(LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.of("UTC"))));
				}
				catch (IOException | NullPointerException x) {
					Logger.warn("Problem finding image file: " + lpath);
					img.with("Path", "/files" + lpath);
				}
			}
			else {
				Logger.warn("Problem finding image file: " + lpath);
				img.with("Path", "/files" + lpath);
			}

			// TODO we could support something like this with a MetaRecord
			//img.with("Data", GalleryUtil.getImageMeta(cpath + ".v"));

			try {
				// setup image for expand
				StackUtil.addVariable(state, "image-" + currimg, img);

				// switch images during expand
				XElement setvar = Var.tag()
						.withAttribute("Name", "Image")
						.withAttribute("SetTo", "$image-" + currimg);

				LogosWidget.this.with(setvar);

				XElement setvarnew = Var.tag()
						.withAttribute("Name", "_Image")
						.withAttribute("SetTo", "$image-" + currimg);

				LogosWidget.this.with(setvarnew);

				// add nodes using the new variable
				XElement entry = template.deepCopy();

				for (XNode node : entry.getChildren())
					LogosWidget.this.with(node);
			}
			catch (OperatingContextException x) {
				Logger.warn("Could not reference image data: " + x);
			}
		}

		if (images.size() < 2)
			this.withClass("single");

		UIUtil.markIfEditable(state, this, "widget");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("pure-g", "dc-widget", "dc-widget-logos")
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName())
			.withAttribute("data-property-editor", StackUtil.stringFromSource(state,"PropertyEditor"))
			.withAttribute("data-path", StackUtil.stringFromSource(state,"Path"));

		String size = StackUtil.stringFromSource(state,"Size", "1-3");
		
		if (this.children != null) {
			for (XNode node : this.children) {
				if (node instanceof XElement) {
					XElement ui = (XElement) node;
					
					String colsize = StackUtil.stringFromElement(state,  ui,"Column.Size", size);
					
					if (ui instanceof Base)
						((Base) ui).withClass("pure-u", "pure-u-" + colsize);
					else
						ui.setAttribute("class", ui.getAttribute("class", "") + " pure-u pure-u-" + colsize);
				}
			}
		}
		
		this.setName("div");
	}
	
	@Override
	public void canonicalize() throws OperatingContextException {
		XElement template = this.selectFirst("Template");
		
		if (template == null) {
			// set default
			this.with(Base.tag("Template").with(
					W3.tag("a")
							.withAttribute("href", "#")
							.withAttribute("data-image-alias", "{$Image.Alias}")
							.withAttribute("data-image-info", "{$Image.Data}")
							.with(
									W3Closed.tag("img")
											.withClass("pure-img-inline")
											.withAttribute("src", "{$Image.Path}")
							)
			));
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
			
			List<XElement> children = this.selectAll("Image");

			// remove all images
			for (XElement el : children)
				this.remove(el);
			
			// add images back in new order
			for (int i = 0; i < neworder.size(); i++) {
				int pos = neworder.getItemAsInteger(i).intValue();

				if (pos >= children.size()) {
					Logger.warn("bad gallery positions");
					break;
				}

				part.with(children.get(pos));
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
			
			if ("SetImage".equals(area)) {
				String alias = params.getFieldAsString("Alias");
				XElement fnd = null;
				
				for (XElement xel : this.selectAll("Image")) {
					if (alias.equals(xel.getAttribute("Alias"))) {
						fnd = xel;
						break;
					}
				}
				
				if (fnd == null) {
					fnd = XElement.tag("Image");
					
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
				
				return true;
			}
			
			if ("RemoveImage".equals(area)) {
				String alias = params.getFieldAsString("Alias");
				XElement fnd = null;
				
				for (XElement xel : this.selectAll("Image")) {
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

			if ("ReplaceImage".equals(area)) {
				String alias = params.getFieldAsString("Alias");

				for (XElement xel : this.selectAll("Image")) {
					if (alias.equals(xel.getAttribute("Alias"))) {
						xel.attr("Alias", params.getFieldAsString("NewAlias"));
						break;
					}
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
