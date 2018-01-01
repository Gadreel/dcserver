package dcraft.web.ui.inst.cms;

public class SlidesWidget {

/* extends Section {
	static public SlidesSection tag() {
		SlidesSection el = new SlidesSection();
		el.setName("dc.SlidesSection");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SlidesSection.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		RecordStruct page = (RecordStruct) StackUtil.queryVariable(state, "Page");
		
		String gallery = this.getAttribute("Gallery");
		String alias = this.getAttribute("Show");
		Long pathpart = Struct.objectToInteger(this.getAttribute("PathPart"));
		
		if (pathpart != null) {
			CommonPath fpath = CommonPath.from(page.getFieldAsString("Path"));
			
			if (! fpath.isRoot()) {
				String name = fpath.getName(pathpart.intValue() - 1);
				
				if (StringUtil.isNotEmpty(name))
					alias = name;
			}
		}
		
		//System.out.println("using show: " + alias);

		this
			.withClass("dc-no-select")
			.withAttribute("data-dcm-period", this.getAttribute("Period"))
			.withAttribute("data-dcm-gallery", gallery)
			.withAttribute("data-dcm-show", alias);
		
		RecordStruct meta = (RecordStruct) GalleryUtil.getGalleryMeta(gallery,  page.getFieldAsString("View"));
		
		RecordStruct showmeta = GalleryUtil.findShow(meta, alias);
		
		if (showmeta != null) {
			this
				.withAttribute("data-dcm-centering", showmeta.getFieldAsString("Centering"));
			
			boolean defpreload = showmeta.getFieldAsBooleanOrFalse("Preload");
			boolean preloadenabled = this.hasNotEmptyAttribute("Preload") 
					? "true".equals(this.getAttribute("Preload").toLowerCase())
					: defpreload;

			String variname = showmeta.getFieldAsString("Variation");
			ListStruct images = showmeta.getFieldAsList("Images");
			
			if (StringUtil.isEmpty(variname))
				variname = "full";
			
			if ((images != null) && ! images.isEmpty()) {
				W3 vwrap = W3.tag("div");
				
				vwrap
					.withClass("dcm-basic-carousel-wrap");
				
				W3 viewer = W3.tag("img");
				
				viewer
					.withClass("dcm-basic-carousel-img");
				
				W3 fader = W3.tag("img");
				
				fader
					.withClass("dcm-basic-carousel-fader");
				
				
				// TODO add a randomize option
				
				// TODO support a separate preload image, that is not a variation but its own thing
				// such as checkered logos in background
				
				RecordStruct topimg = images.getItemAsRecord(0);
				
				if (preloadenabled) {
					Path preload = OperationContext.getOrThrow().getSite().findSectionFile("galleries", gallery + "/" +
							topimg.getFieldAsString("Alias") + ".v/preload.jpg", page.getFieldAsString("View"));
					
					if (preload != null) {
						Memory mem = IOUtil.readEntireFileToMemory(preload);
						
						String data = Base64.encodeToString(mem.toArray(), false);
						
						viewer.withAttribute("src", "data:image/jpeg;base64," + data);
					}
				}
				
				Base list = W3.tag("div").withClass("dcm-basic-carousel-list");
				
				for (Struct simg : images.items()) {
					RecordStruct img = (RecordStruct) simg;
					
					String extrapath = img.isNotFieldEmpty("Path") ? img.getFieldAsString("Path") + "/" : "";
					
					W3 iel = W3.tag("img");
					
					iel
						.withAttribute("src", "/galleries" + gallery + "/" + extrapath + img.getFieldAsString("Alias") + ".v/" + variname + ".jpg")
						.withAttribute("data-dcm-img", img.toString());
					
					list.with(iel);
				}
				
				vwrap.with(fader).with(viewer).with(list);
				this.with(vwrap);
			}
			
			this.with(Button.tag("dcmi.GalleryButton")
					.withClass("dcuiPartButton", "dcuiCmsi")
					.withAttribute("Icon", "fa-pencil")
				);
		}
		
		if (this.hasNotEmptyAttribute("Title"))
			this.add(0, W3.tag("h2").withText(this.getAttribute("Title")));
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withClass("dcm-basic-carousel", "dcm-cms-editable")
			.withAttribute("data-dccms-edit", this.getAttribute("AuthTags", "Editor,Admin,Developer"))
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName())
			.withAttribute("data-dccms-plugin", "Gallery")
			.withAttribute("data-variant", this.getAttribute("Variant"))
			.withAttribute("data-ext", this.getAttribute("Extension"))
			.withAttribute("data-path", this.getAttribute("Path"))
			.withAttribute("data-show", this.getAttribute("Show"));
		
		super.renderAfterChildren(state);
	}
	*/
}
