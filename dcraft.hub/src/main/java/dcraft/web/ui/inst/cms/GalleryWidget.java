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
import dcraft.script.work.InstructionWork;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.script.inst.doc.Base;
import dcraft.struct.Struct;
import dcraft.struct.builder.JsonStreamBuilder;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.io.OutputWrapper;
import dcraft.web.ui.JsonPrinter;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.W3Closed;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlToJson;
import dcraft.xml.XmlToJsonPrinter;
import dcraft.xml.XmlUtil;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class GalleryWidget extends Base implements ICMSAware {
	static public GalleryWidget tag() {
		GalleryWidget el = new GalleryWidget();
		el.setName("dcm.GalleryWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return GalleryWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		long maximgs = StackUtil.intFromSource(state,"Max", 24);
		
		XElement template = this.selectFirst("Template");
		
		List<XElement> images = this.selectAll("Image");
		
		this.clearChildren();
		
		String vari = StackUtil.stringFromSource(state,"Variant", "full");
		String path = StackUtil.stringFromSource(state,"Path");
		String show = StackUtil.stringFromSource(state,"Show");
		String missing = StackUtil.stringFromSource(state,"Missing");

		RecordStruct meta = (RecordStruct) GalleryUtil.getMeta(path,
				OperationContext.getOrThrow().selectAsString("Controller.Request.View"));
		
		RecordStruct vdata = GalleryUtil.findVariation(meta, vari);

		boolean usesrcset = ((vdata != null) && vdata.isNotFieldEmpty("Density"));
		
		AtomicLong currimg = new AtomicLong();
		
		GalleryImageConsumer galleryImageConsumer = new GalleryImageConsumer() {
			@Override
			public void accept(RecordStruct meta, RecordStruct show, RecordStruct img) throws OperatingContextException {
				long cidx = currimg.incrementAndGet();
				
				if (cidx > maximgs)
					return;
				
				String cpath = img.getFieldAsString("Path");

				// TODO support alt ext (from the gallery meta.json)
				String ext = meta.getFieldAsString("Extension", "jpg");

				String lpath = cpath + ".v/" + vari + "." + ext;

				Path imgpath = OperationContext.getOrThrow().getSite().findSectionFile("galleries", lpath,
						OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

				boolean found = false;

				if ((imgpath == null) || ! Files.exists(imgpath)) {
					if (StringUtil.isNotEmpty(missing)) {
						cpath = path + "/" + missing;

						lpath = cpath + ".v/" + vari + "." + ext;

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

						img.with("Path", "/galleries" + lpath + "?dc-cache=" + TimeUtil.stampFmt.format(LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.of("UTC"))));
					}
					catch (IOException x) {
						Logger.warn("Problem finding image file: " + lpath);
						img.with("Path", "/galleries" + lpath);
					}
				}
				else {
					Logger.warn("Problem finding image file: " + lpath);
					img.with("Path", "/galleries" + lpath);
				}

				//img.with("Path", "/galleries" + cpath + ".v/" + vari + "." + ext);
				img.with("Gallery", meta);
				img.with("Variant", vdata);
				img.with("Show", show);
				img.with("Position", cidx);
				img.with("BasePath", cpath);

				RecordStruct imgmeta = (RecordStruct) GalleryUtil.getMeta(cpath + ".v",
						OperationContext.getOrThrow().selectAsString("Controller.Request.View"));
				
				// lookup the default locale for this site
				if (imgmeta != null)
					imgmeta = imgmeta.getFieldAsRecord(OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale());
				
				// TODO find overrides to the default and merge them into imgmeta
				
				img.with("Data", (imgmeta != null) ? imgmeta : RecordStruct.record());
				
				if (usesrcset) {
					StringBuilder srcset = new StringBuilder();
					boolean first = true;
					
					for (Struct lvl : vdata.getFieldAsList("Density").items()) {
						RecordStruct rlvl = (RecordStruct) lvl;
						String amt = rlvl.getFieldAsString("Level");
						
						if (StringUtil.isEmpty(amt))
							continue;
						
						if (! first)
							srcset.append(", ");
						else
							first = false;
						
						srcset.append("/galleries" + cpath + ".v/" + vari
								+ "-" + amt.replace('.', '-') + "." + ext + " " + amt + "x");
					}
					
					// srcset="image-2x.png 2x, image-3x.png 3x, image-4x.png 4x"
					img.with("SourceSet", srcset);
				}
				
				try {
					// setup image for expand
					StackUtil.addVariable(state, "image-" + cidx, img);
					
					// switch images during expand
					XElement setvar = Var.tag()
							.withAttribute("Name", "Image")
							.withAttribute("SetTo", "$image-" + cidx);
					
					GalleryWidget.this.with(setvar);
					
					// add nodes using the new variable
					XElement entry = template.deepCopy();
					
					for (XNode node : entry.getChildren())
						GalleryWidget.this.with(node);
				}
				catch (OperatingContextException x) {
					Logger.warn("Could not reference image data: " + x);
				}
			}
		};
		
		if (StringUtil.isNotEmpty(show)) {
			GalleryUtil.forEachGalleryShowImage(meta, path, show, galleryImageConsumer);
		}
		else {
			RecordStruct showrec = RecordStruct.record()
					.with("Title", "Default")
					.with("Alias", "default")
					.with("Variation", vari);
			
			for (XElement img : images) {
				galleryImageConsumer.accept(meta, showrec, RecordStruct.record()
						.with("Alias", img.getAttribute("Alias"))
						.with("Path", path + "/" + img.getAttribute("Alias"))
						.with("Element", XmlToJson.convertXml(img,true))
				);
			}

			if (images.size() < 2)
				this.withClass("single");
		}
		
		UIUtil.markIfEditable(state, this, "widget");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("pure-g", "dc-widget", "dc-widget-gallery")
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName())
			.withAttribute("data-property-editor", StackUtil.stringFromSource(state,"PropertyEditor"))
			.withAttribute("data-variant", StackUtil.stringFromSource(state,"Variant"))
			.withAttribute("data-ext", StackUtil.stringFromSource(state,"Extension"))
			.withAttribute("data-path", StackUtil.stringFromSource(state,"Path"))
			.withAttribute("data-show", StackUtil.stringFromSource(state,"Show"));

		String xvari = StackUtil.stringFromSource(state,"ExpandVariant");

		if (StringUtil.isNotEmpty(xvari))
			this.attr("data-dc-expanded", xvari);

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
											//.withAttribute("srcset", usesrcset ? "{$Image.SourceSet}" : null)
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
