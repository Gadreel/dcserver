package dcraft.web.ui.inst.cms;

import dcraft.cms.util.GalleryImageConsumer;
import dcraft.cms.util.GalleryUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Var;
import dcraft.script.work.InstructionWork;
import dcraft.struct.RecordStruct;
import dcraft.script.inst.doc.Base;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.W3Closed;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class GalleryWidget extends Base {
	static public GalleryWidget tag() {
		GalleryWidget el = new GalleryWidget();
		el.setName("dc.GalleryWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return GalleryWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		long maximgs = StackUtil.intFromSource(state,"Max", 24);
		
		List<XNode> children = this.children;
		
		this.clearChildren();
		
		boolean hastemplate = false;
		
		if (children != null) {
			for (int i = 0; i < children.size(); i++) {
				if (children.get(i) instanceof XElement) {
					hastemplate = true;
					break;
				}
			}
		}
		
		String vari = StackUtil.stringFromSource(state,"Variant", "full");
		String path = StackUtil.stringFromSource(state,"Path");
		String show = StackUtil.stringFromSource(state,"Show");
		
		RecordStruct meta = (RecordStruct) GalleryUtil.getMeta(path,
				OperationContext.getOrThrow().selectAsString("Controller.Request.View"));
		
		RecordStruct vdata = GalleryUtil.findVariation(meta, vari);

		boolean usesrcset = ((vdata != null) && vdata.isNotFieldEmpty("Density"));
		
		if (! hastemplate) {
			children = new ArrayList<>();
			
			children.add(W3.tag("a")
					.withAttribute("href", "#")
					.withAttribute("data-image-alias", "{$Image.Alias}")
					.withAttribute("data-image-info", "{$Image.Data}")
					.with(
							W3Closed.tag("img")
									.withClass("pure-img-inline")
									.withAttribute("src", "{$Image.Path}")
									.withAttribute("srcset", usesrcset ? "{$Image.SourceSet}" : null)
					)
			);
		}
		
		List<XNode> fchildren = children;
		AtomicLong currimg = new AtomicLong();
		
		//RecordStruct page = (RecordStruct) StackUtil.queryVariable(state, "Page");
		
	    GalleryUtil.forEachGalleryShowImage(meta, path, show, new GalleryImageConsumer() {
			@Override
			public void accept(RecordStruct meta, RecordStruct show, RecordStruct img) throws OperatingContextException {
				long cidx = currimg.incrementAndGet();
				
				if (cidx > maximgs)
					return;
				
				String cpath = img.getFieldAsString("Path");
				
				String ext = meta.getFieldAsString("Extension", "jpg");
				
				// TODO support alt ext (from the gallery meta.json)
				img.with("Path", "/galleries" + cpath + ".v/" + vari + "." + ext);
				img.with("Gallery", meta);
				img.with("Variant", vdata);
				img.with("Show", show);
				
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
					List<XNode> template = XmlUtil.deepCopyChildren(fchildren);
					
					GalleryWidget.this.withAll(template);
				}
				catch (OperatingContextException x) {
					Logger.warn("Could not reference image data: " + x);
				}
			}
		});
		
		// TODO check for parent with data-cms-feed
		if (this.hasNotEmptyAttribute("id") && OperationContext.getOrThrow().getUserContext().isTagged("Admin", "Editor")) {
			this
					.withAttribute("data-cms-editable", "true")
					.with(
							EditButton.tag().attr("title", "CMS - edit previous gallery")
					);
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("pure-g", "dc-widget", "dc-widget-gallery")
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName())
			.withAttribute("data-variant", StackUtil.stringFromSource(state,"Variant"))
			.withAttribute("data-ext", StackUtil.stringFromSource(state,"Extension"))
			.withAttribute("data-path", StackUtil.stringFromSource(state,"Path"))
			.withAttribute("data-show", StackUtil.stringFromSource(state,"Show"));
		
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
}
