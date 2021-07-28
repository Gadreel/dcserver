package dcraft.web.ui.inst.cms;

import dcraft.cms.util.GalleryUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;
import dcraft.util.TimeUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlReader;
import dcraft.xml.XmlToJson;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

public class ImageWidget extends Base implements ICMSAware {
	static public ImageWidget tag() {
		ImageWidget el = new ImageWidget();
		el.setName("dcm.ImageWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return ImageWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		RecordStruct img = RecordStruct.record();
		
		String path = StackUtil.stringFromSource(state, "Path");
		String vari = StackUtil.stringFromSource(state,"Variant", "full");
		String[] rvari = StackUtil.stringFromSource(state,"ResponsiveVariants", "").split(",");
		String xvari = StackUtil.stringFromSource(state,"ExpandVariant");
		String xresp = StackUtil.stringFromSource(state,"ExpandResponsive");
		String description = StackUtil.stringFromSource(state,"Description");
		String ext = StackUtil.stringFromSource(state,"Extension", "jpg");

		try {
			RecordStruct meta = GalleryUtil.getMeta(CommonPath.from(path).getParent().toString());

			if (meta != null) {
				ext = meta.getFieldAsString("Extension", ext);

				RecordStruct vdata = GalleryUtil.findVariation(meta, vari);

				// TODO support alt ext (from the gallery meta.json)
				//img.with("Gallery", meta);
				//img.with("Variant", vdata);

				StackUtil.addVariable(state, "_Gallery", meta);
				StackUtil.addVariable(state, "_Variant", vdata);

				if (vdata != null)
					ext = vdata.getFieldAsString("Extension", ext);

				if ((rvari.length > 0) && StringUtil.isNotEmpty(rvari[0])) {
					if (!this.hasAttribute("Sizes"))
						this.attr("Sizes", "100vw");

					StringBuilder srcset = new StringBuilder();
					boolean first = true;

					for (int rv = 0; rv < rvari.length; rv++) {
						RecordStruct rvdata = GalleryUtil.findVariation(meta, rvari[rv]);

						String width = rvdata.getFieldAsString("ExactWidth");

						if (StringUtil.isEmpty(width))
							continue;

						if (!first)
							srcset.append(", ");
						else
							first = false;

						srcset.append("/galleries" + path + ".v/" + rvari[rv]
								+ "." + ext + " " + width + "w");
					}

					img.with("SourceSet", srcset);
				}
			}
		}
		catch (IllegalArgumentException x) {
			System.out.println("bad path - try Missing");
		}

		if (this.hasNotEmptyAttribute("Centering") || this.hasNotEmptyAttribute("CenterHint"))
			this
					.withAttribute("data-dcm-centering", StackUtil.stringFromSource(state, "Centering", "true"))
					.withAttribute("data-dcm-center-hint", StackUtil.stringFromSource(state, "CenterHint"));

		int apos = path.lastIndexOf('/') + 1;

		String lpath = path + ".v/" + vari + "." + ext;
		String fpath = "/galleries" + lpath;

		Path imgpath = OperationContext.getOrThrow().getSite().findSectionFile("galleries", lpath,
				OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

		if (imgpath == null) {
			fpath = "/imgs/blank.png";

			imgpath = OperationContext.getOrThrow().getSite().findSectionFile("www", "/imgs/blank.png",
					OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
		}

		try {
			FileTime fileTime = Files.getLastModifiedTime(imgpath);

			img.with("Path", fpath + "?dc-cache=" + TimeUtil.stampFmt.format(LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.of("UTC"))));
		}
		catch (IOException x) {
			Logger.warn("Problem finding image file: " + lpath);
			img.with("Path", "/galleries" + lpath);
		}

		img.with("Alias", path.substring(apos));
		img.with("BasePath", path);  //.substring(0, apos));
		img.with("Position", 1);
		//img.with("Description", description);		// deprecate - prefer $Image.Element.@Description instead
    	img.with("Element", XmlToJson.convertXml(ImageWidget.this,true, true));

		String sizes = StackUtil.stringFromSource(state,"Sizes");
		
		if (StringUtil.isNotEmpty(sizes))
			img.with("Sizes", sizes);
		
		RecordStruct imgmeta = GalleryUtil.getImageMeta(path);

		img.with("Data", imgmeta);
		
		if (imgmeta != null) {
			if (StringUtil.isEmpty(description))
				img.with("Description", imgmeta.getFieldAsString("Description"));
		}
		
		this.withAttribute("data-dc-image-data", img.toString());
		this.attr("data-dc-path", path);
		this.attr("data-dc-ext", ext);
		this.attr("data-dc-variant", vari);
		
		if (StringUtil.isNotEmpty(xvari))
			this.attr("data-dc-expanded", xvari);
		
		// TODO support expand-opts in client side JS
		if (StringUtil.isNotEmpty(xresp))
			this.attr("data-dc-expand-opts", xresp);
		
		StackUtil.addVariable(state, "Image", img);
		StackUtil.addVariable(state, "_Image", img);

		this.canonicalize();

		XElement template = this.selectFirst("Template");

		if (template != null) {
			this.remove(template);

			for (XNode node : template.getChildren())
				this.with(node);
		}

		UIUtil.markIfEditable(state, this, "widget");
	}

	@Override
	public void canonicalize() throws OperatingContextException {
		XElement template = this.selectFirst("Template");

		if (template == null) {
			String display = this.getAttribute("Display", "None").toLowerCase();

			// set default
			this.with(Base.tag("Template").with(
					W3.tag("img")
							.withClass(display.contains("banner") ? "dcm-widget-banner-img" : "pure-img-inline")
							.withAttribute("alt", "{$Image.Element.@Description|ifempty:}")
							.withAttribute("loading", "lazy")
							.withAttribute("src", "{$Image.Path}")
							.withAttribute("srcset", "{$Image.SourceSet|ifempty:}")
							.withAttribute("sizes", "{$Image.Sizes|ifempty:}")
			));
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		String display = StackUtil.stringFromSource(state,"Display", "None").toLowerCase();

		this
				.withClass("dc-widget", "dc-widget-image", "dc-media-box")
				.withAttribute("data-dc-display", display)
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());

		if (display.contains("banner")) {
			this.withClass("dcm-widget-banner");
		}
		else {
			this.withClass("dc-media-image");
		}

		this.setName("div");
    }
	
	@Override
	public boolean applyCommand(CommonPath path, XElement root, RecordStruct command) throws OperatingContextException {
		String cmd = command.getFieldAsString("Command");
		
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
			
			if ("Template".equals(area)) {
				this.canonicalize();	// so all Tr's have a Locale
				
				String targetcontent = params.getFieldAsString("Template");
				
				String template = "<Template>" + targetcontent + "</Template>";
				
				try (OperationMarker om = OperationMarker.clearErrors()) {
					XElement txml = ScriptHub.parseInstructions(template);
					
					if (! om.hasErrors() && (txml != null)) {
						XElement oldtemp = this.selectFirst("Template");
						
						if (oldtemp != null)
							this.remove(oldtemp);
						
						this.with(txml);
					}
					else {
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
