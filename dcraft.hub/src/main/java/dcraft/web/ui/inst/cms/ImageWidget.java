package dcraft.web.ui.inst.cms;

import dcraft.cms.util.GalleryUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class ImageWidget extends Base {
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
		String xvari = StackUtil.stringFromSource(state,"ExpandVariant");
		String description = StackUtil.stringFromSource(state,"Description");
		String ext = "jpg";
		
		RecordStruct meta = (RecordStruct) GalleryUtil.getMeta(CommonPath.from(path).getParent().toString(),
				OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
		
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

				for (Struct lvl : vdata.getFieldAsList("Density").items()) {
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
		
		int apos = path.lastIndexOf('/') + 1;
		
		img.with("Path", "/galleries" + path + ".v/" + vari + "." + ext);
		img.with("Alias", path.substring(apos));
		img.with("Description", description);
		
		RecordStruct imgmeta = (RecordStruct) GalleryUtil.getMeta(path + ".v",
				OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
		
		// lookup the default locale for this site
		if (imgmeta != null)
			imgmeta = imgmeta.getFieldAsRecord(OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale());
		
		// TODO find overrides to the default and merge them into imgmeta
		
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
		
		boolean fndtemplate = false;
		
		// look for a template, if found then skip default
		if (this.children != null) {
			for (int i = 0; i < this.children.size(); i++) {
				if (this.children.get(i) instanceof XElement) {
					fndtemplate = true;
					break;
				}
			}
		}
		
		if (! fndtemplate) {
			// set default
			
			this
					.withClass("dc-media-box", "dc-media-image")
					.with(W3.tag("img")
							.withClass("pure-img-inline")
							.withAttribute("alt", "{$Image.Description}")
							.withAttribute("src", "{$Image.Path}")
							.withAttribute("srcset", usesrcset ? "{$Image.SourceSet}" : null)
					);
		}
		
		// TODO check for parent with data-cms-feed
		if (this.hasNotEmptyAttribute("id") && OperationContext.getOrThrow().getUserContext().isTagged("Admin", "Editor")) {
			this
					.withAttribute("data-cms-editable", "true")
					.with(
							EditButton.tag().attr("title", "CMS - edit previous image")
					);
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this
				.withClass("dc-widget", "dc-widget-image")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
    }
}
