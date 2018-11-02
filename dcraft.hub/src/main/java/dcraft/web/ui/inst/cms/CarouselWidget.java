package dcraft.web.ui.inst.cms;

import java.nio.file.Path;
import java.util.List;

import dcraft.cms.util.GalleryUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.Base64;
import dcraft.util.IOUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.W3Closed;
import dcraft.xml.XElement;

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
		String gallery = StackUtil.stringFromSource(state,"Gallery");
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

		String variname = "full";
		
		if (this.hasNotEmptyAttribute("Variation"))
			variname = StackUtil.stringFromSource(state, "Variation");
		
		boolean preloadenabled = this.hasNotEmptyAttribute("Preload")
				? "true".equals(StackUtil.stringFromSource(state,"Preload").toLowerCase())
				: false;
		
		//System.out.println("using show: " + alias);

		this
			.withClass("dc-no-select")
			.withAttribute("data-dcm-period", StackUtil.stringFromSource(state,"Period"))
			.withAttribute("data-dcm-gallery", gallery)
			.withAttribute("data-dcm-show", alias);
		
		RecordStruct meta = (RecordStruct) GalleryUtil.getMeta(gallery, OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
		
		//RecordStruct vdata = GalleryUtil.findVariation(meta, variname);
		
		RecordStruct showmeta = GalleryUtil.findShow(meta, alias);
		
		if (showmeta != null) {
			ListStruct images = showmeta.getFieldAsList("Images");
			
			if ((images != null) && ! images.isEmpty() && StringUtil.isNotEmpty(variname)) {
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
				
				// TODO add a randomize option
				
				// TODO support a separate preload image, that is not a variation but its own thing
				// such as checkered logos in background
				
				String topimg = images.getItemAsString(0);
				
				if (preloadenabled) {
					Path preload = OperationContext.getOrThrow().getSite().findSectionFile("galleries", gallery + "/" +
							topimg + ".v/preload.jpg", OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
					
					if (preload != null) {
						Memory mem = IOUtil.readEntireFileToMemory(preload);
						
						String data = Base64.encodeToString(mem.toArray(), false);
						
						viewer.withAttribute("src", "data:image/jpeg;base64," + data);
					}
				}
				
				String ext = meta.getFieldAsString("Extension", "jpg");
				
				Base list = W3.tag("div").withClass("dcm-widget-carousel-list");
				
				list.withAttribute("role", "list");
				
				for (Struct simg : images.items()) {
					String img = simg.toString();
					
					RecordStruct imgmeta = (RecordStruct) GalleryUtil.getMeta(gallery + "/" + img + ".v",
							OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

					// TODO use a utility function for this, take default local fields, then override
					// TODO with current locale
					// lookup the default locale for this site
					if (imgmeta != null)
						imgmeta = imgmeta.getFieldAsRecord(OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale());
					
					// TODO find overrides to the default and merge them into imgmeta
					
					/*
					RecordStruct data = RecordStruct.record()
							.with("Alias", img)
							.with("Path", "/galleries" + gallery + ".v/" + img + "." + ext)
							//.with("Gallery", meta)
							//.with("Variant", vdata)
							.with("Show", alias)
							.with("Data", (imgmeta != null) ? imgmeta : RecordStruct.record());
					*/
					
					RecordStruct data = (imgmeta != null) ? imgmeta : RecordStruct.record();
					
					// TODO use templates
					
					Base iel = W3Closed.tag("img");
					
					iel
							.withAttribute("role", "listitem")
						.withAttribute("src", "/galleries" + gallery + "/" + img + ".v/" + variname + "." + ext)
						.withAttribute("alt", data.selectAsString("Description"))
						.withAttribute("data-dc-image-data", data.toString());
					
					list.with(iel);
				}
				
				this.with(fader).with(viewer).with(list);
			}
		}
		
		UIUtil.markIfEditable(state, this, "widget");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		// TODO edit is conditional to user
		this
			.withClass("dc-widget", "dcm-widget-carousel")
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
    	
		this.setName("div");
    }
	
	
	@Override
	public void canonicalize() throws OperatingContextException {
		XElement template = this.selectFirst("Template");
		
		if (template == null) {
			// set default
			/* create a template system
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
			*/
		}
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
						this.attr(fld.getName(), Struct.objectToString(fld.getValue()));
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
