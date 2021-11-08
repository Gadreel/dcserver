package dcraft.web.ui.inst.cms;

import dcraft.cms.util.GalleryUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class BannerWidget extends Base implements ICMSAware {
	static public BannerWidget tag() {
		BannerWidget el = new BannerWidget();
		el.setName("dcm.BannerWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return BannerWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		RecordStruct img = RecordStruct.record();
		
		String path = StackUtil.stringFromSource(state, "Path");
		String vari = StackUtil.stringFromSource(state,"Variant", "full");
		String xvari = StackUtil.stringFromSource(state,"ExpandVariant");
		String description = StackUtil.stringFromSource(state,"Description");
		String ext = "jpg";

		RecordStruct meta = GalleryUtil.getMeta(CommonPath.from(path).getParent().toString());

		boolean usesrcset = false;
		
		if (meta != null) {
			ext = meta.getFieldAsString("Extension", ext);

			RecordStruct vdata = GalleryUtil.findVariation(meta, vari);

			// TODO support alt ext (from the gallery meta.json)
			img.with("Gallery", meta);
			img.with("Variant", vdata);
			
			if ((vdata != null) && vdata.isNotFieldEmpty("Density")) {
				StringBuilder srcset = new StringBuilder();
				boolean first = true;

				for (BaseStruct lvl : vdata.getFieldAsList("Density").items()) {
					RecordStruct rlvl = (RecordStruct) lvl;
					String amt = rlvl.getFieldAsString("Level");

					if (StringUtil.isEmpty(amt))
						continue;

					if (! first)
						srcset.append(", ");
					else
						first = false;

					srcset.append("/galleries" + path + ".v/" + vari
							+ "-" + amt.replace('.', '-') + "." + ext + " " + amt + "x");
				}

				img.with("SourceSet", srcset);
				
				usesrcset = true;
			}

			// srcset="image-2x.png 2x, image-3x.png 3x, image-4x.png 4x"
		}
		
		if (this.hasNotEmptyAttribute("Centering"))
			this
					.withAttribute("data-dcm-centering", StackUtil.stringFromSource(state, "Centering"));
		
		int apos = path.lastIndexOf('/') + 1;

		String lpath = path + ".v/" + vari + "." + ext;

		Path imgpath = OperationContext.getOrThrow().getSite().findSectionFile("galleries", lpath,
				OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

		try {
			FileTime fileTime = Files.getLastModifiedTime(imgpath);

			img.with("Path", "/galleries" + lpath + "?dc-cache=" + TimeUtil.stampFmt.format(LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.of("UTC"))));
		}
		catch (IOException x) {
			Logger.warn("Problem finding image file: " + lpath);
			img.with("Path", "/galleries" + lpath);
		}

		//img.with("Path", "/galleries" + path + ".v/" + vari + "." + ext);

		img.with("Alias", path.substring(apos));
		img.with("Description", description);

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
		
		StackUtil.addVariable(state, "Image", img);

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
			// set default
			this.with(Base.tag("Template").with(
					W3.tag("img")
							.withClass("dcm-widget-banner-img")
							.withAttribute("alt", "{$Image.Description}")
							.withAttribute("src", "{$Image.Path}")
							//.withAttribute("srcset", usesrcset ? "{$Image.SourceSet}" : null)
			));
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this
				.withClass("dc-widget", "dcm-widget-banner")
				.withClass("dc-media-box", "dc-media-image")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
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
