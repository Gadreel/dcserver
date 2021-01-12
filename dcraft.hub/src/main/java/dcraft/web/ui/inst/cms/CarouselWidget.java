package dcraft.web.ui.inst.cms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
import dcraft.struct.Struct;
import dcraft.util.*;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.W3Closed;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlToJson;

public class CarouselWidget extends Base implements ICMSAware {
	static public CarouselWidget tag() {
		CarouselWidget el = new CarouselWidget();
		el.setName("dcm.CarouselWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return CarouselWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String gallery = StackUtil.stringFromSource(state,"Path");

		// old code support
		if (StringUtil.isEmpty(gallery))
			gallery = StackUtil.stringFromSource(state,"Gallery");

		String alias = StackUtil.stringFromSource(state,"Show");
		
		RecordStruct page = (RecordStruct) StackUtil.queryVariable(state, "Page");
		
		Long pathpart = Struct.objectToInteger(StackUtil.stringFromSource(state,"PathPart"));
		
		if (pathpart != null) {
			CommonPath fpath = CommonPath.from(page.getFieldAsString("Path"));
			
			if (! fpath.isRoot()) {
				String name = fpath.getName(pathpart.intValue() - 1);
				
				if (StringUtil.isNotEmpty(name))
					alias = name;
			}
		}
		
		if (this.hasNotEmptyAttribute("Centering"))
			this
				.withAttribute("data-dcm-centering", StackUtil.stringFromSource(state, "Centering"));

		String vari = StackUtil.stringFromSource(state,"Variant", "full");
		
		boolean preloadenabled = this.hasNotEmptyAttribute("Preload")
				? "true".equals(StackUtil.stringFromSource(state,"Preload").toLowerCase())
				: false;

		String ext = StackUtil.stringFromSource(state, "Extension", "jpg");

		String imgcache = StackUtil.stringFromSource(state, "ImageCache", "Max").toLowerCase();

		XElement ariatemplate = this.selectFirst("AriaTemplate");

		this.remove(ariatemplate);

	 	W3 arialist = (W3) W3.tag("div").withClass("dc-element-hidden")
				.attr("role", "list").attr("aria-label", "banner images");

		AtomicBoolean ariatemplateused = new AtomicBoolean(false);

		List<XElement> images = this.selectAll("Image");

		// clear so not in html output
		for (XElement img : images)
			this.remove(img);

		//System.out.println("using show: " + alias);

		this
			.withClass("dc-no-select")
			.withAttribute("data-dcm-period", StackUtil.stringFromSource(state,"Period"))
			.withAttribute("data-dcm-gallery", gallery)
			.withAttribute("data-dcm-show", alias);

		RecordStruct meta = (RecordStruct) GalleryUtil.getMeta(gallery, OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

		Base viewer = W3Closed.tag("img");

		viewer
				.withClass("dcm-widget-carousel-img")
				.attr("alt","")
				.attr("aria-hidden","true");

		Base fader = W3Closed.tag("img");

		fader
				.withClass("dcm-widget-carousel-fader")
				.attr("alt","")
				.attr("aria-hidden","true");

		Base list = W3.tag("div").withClass("dcm-widget-carousel-list");

		list.attr("role", "list")
				.attr("aria-hidden", "true");

		this.with(fader).with(viewer).with(list);

		AtomicLong currimg = new AtomicLong();

		GalleryImageConsumer galleryImageConsumer = new GalleryImageConsumer() {
			@Override
			public void accept(RecordStruct meta, RecordStruct show, RecordStruct img) throws OperatingContextException {
				long cidx = currimg.incrementAndGet();

				String cpath = img.getFieldAsString("Path");

				String ext = meta.getFieldAsString("Extension", "jpg");

				// TODO support alt ext (from the gallery meta.json)

				String lpath = cpath + ".v/" + vari + "." + ext;

				Path imgpath = OperationContext.getOrThrow().getSite().findSectionFile("galleries", lpath,
						OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

				try {
					if ("max".equals(imgcache)) {
						FileTime fileTime = Files.getLastModifiedTime(imgpath);

						img.with("Path", "/galleries" + lpath + "?dc-cache=" + TimeUtil.stampFmt.format(LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.of("UTC"))));
					}
					else {
						img.with("Path", "/galleries" + lpath);
					}
				}
				catch (IOException x) {
					Logger.warn("Problem finding image file: " + lpath);
					img.with("Path", "/galleries" + lpath);
				}

				img.with("Gallery", meta);
				//img.with("Variant", vdata);
				img.with("Show", show);
				img.with("Position", cidx);

				// TODO use a utility function for this, take default local fields, then override
				// TODO with current locale
				// lookup the default locale for this site
				RecordStruct imgmeta = (RecordStruct) GalleryUtil.getMeta(cpath + ".v",
						OperationContext.getOrThrow().selectAsString("Controller.Request.View"));

				// lookup the default locale for this site
				if (imgmeta != null)
					imgmeta = imgmeta.getFieldAsRecord(OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale());

				// TODO find overrides to the default and merge them into imgmeta

				RecordStruct data = (imgmeta != null) ? imgmeta : RecordStruct.record();

				if (img.isNotFieldEmpty("Element")) {
					data.with("Element", img.getField("Element"));

					String centerhint = img.selectAsString("Element.attributes.CenterHint");

					if (StringUtil.isNotEmpty(centerhint))
						data.with("CenterHint", centerhint);
				}

				img.with("Data", data);

				// TODO support a separate preload image, that is not a variation but its own thing
				// such as checkered logos in background

				if ((cidx == 1) && preloadenabled) {
					Path preload = OperationContext.getOrThrow().getSite().findSectionFile("galleries", cpath + ".v/preload.jpg",
							OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

					if (preload != null) {
						Memory mem = IOUtil.readEntireFileToMemory(preload);

						String idata = Base64.encodeToString(mem.toArray(), false);

						viewer.withAttribute("src", "data:image/jpeg;base64," + idata);
					}
				}

				// TODO use aria templates

				Base iel = W3Closed.tag("img");

				iel
					.withAttribute("role", "listitem")
					.withAttribute("src", img.getFieldAsString("Path"))
					.withAttribute("alt", data.selectAsString("Description"))
					.withAttribute("data-dc-image-data", data.toString());

				list.with(iel);

				try {
					// setup image for expand
					StackUtil.addVariable(state, "image-" + cidx, data);

					// switch images during expand
					XElement setvar = Var.tag()
							.withAttribute("Name", "Image")
							.withAttribute("SetTo", "$image-" + cidx);

					arialist.with(setvar);

					if (ariatemplate != null) {
						// add nodes using the new variable
						XElement entry = ariatemplate.deepCopy();

						for (XNode node : entry.getChildren())
							arialist.with(node);

						ariatemplateused.set(true);
					}
				}
				catch (OperatingContextException x) {
					Logger.warn("Could not reference image data: " + x);
				}
			}
		};

		// TODO add a randomize option

		if (StringUtil.isNotEmpty(alias)) {
			GalleryUtil.forEachGalleryShowImage(meta, gallery, alias, galleryImageConsumer);
		}
		else {
			RecordStruct showrec = RecordStruct.record()
					.with("Title", "Default")
					.with("Alias", "default")
					.with("Variation", vari);

			for (XElement img : images) {
				galleryImageConsumer.accept(meta, showrec, RecordStruct.record()
						.with("Alias", img.getAttribute("Alias"))
						.with("Path", gallery + "/" + img.getAttribute("Alias"))
						.with("Element", XmlToJson.convertXml(img,true))
				);
			}
		}

		if (ariatemplateused.get())
			this.with(arialist);

		this
				.withAttribute("data-variant", vari)
				.withAttribute("data-ext", ext)
				.withAttribute("data-path", gallery);

		if (StringUtil.isNotEmpty(alias))
			this.withAttribute("data-show", alias);

		UIUtil.markIfEditable(state, this, "widget");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		// TODO edit is conditional to user
		this
			.withClass("dc-widget", "dcm-widget-carousel")
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName())
			.withAttribute("data-property-editor", StackUtil.stringFromSource(state,"PropertyEditor"));

		this.setName("div");
    }
	
	
	@Override
	public void canonicalize() throws OperatingContextException {
		XElement template = this.selectFirst("AriaTemplate");
		
		if (template == null) {
			// set default
			this.with(Base.tag("AriaTemplate").with(
					W3.tag("div")
							.withAttribute("role", "listitem")
							.withText("{$Image.Title}")
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
						this.attr(fld.getName(), Struct.objectToString(fld.getValue()));
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
						fnd.attr(fld.getName(), Struct.objectToString(fld.getValue()));
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

			if ("AriaTemplate".equals(area)) {
				this.canonicalize();    // so all Tr's have a Locale
				
				String targetcontent = params.getFieldAsString("AriaTemplate");
				
				String template = "<AriaTemplate>" + targetcontent + "</AriaTemplate>";
				
				try (OperationMarker om = OperationMarker.clearErrors()) {
					XElement txml = ScriptHub.parseInstructions(template);
					
					if (!om.hasErrors() && (txml != null)) {
						XElement oldtemp = this.selectFirst("AriaTemplate");
						
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
