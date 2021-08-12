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
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.W3Closed;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class StoreGalleryWidget extends Base implements ICMSAware {
	static public StoreGalleryWidget tag() {
		StoreGalleryWidget el = new StoreGalleryWidget();
		el.setName("dcm.StoreGalleryWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return StoreGalleryWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		//long maximgs = StackUtil.intFromSource(state,"Max", 24);
		
		XElement template = this.selectFirst("Template");
		
		List<XElement> products = this.selectAll("Product");
		
		// supports a custom query in the control - parse like LoadRecord does...
		
		SelectFields fields = SelectFields.select()
				.with("Id")
				.withComposer("dcmProductPrice", "Price")
				.with("dcmVariablePrice", "VariablePrice")
				.with("dcmTitle", "Title")
				.with("dcmSku", "Sku")
				.with("dcmDescription", "Description");
		
		RecordStruct select = RecordStruct.record();
		
		for (XElement code : this.selectAll("*")) {
			if ("Select".equals(code.getName()) || "SelectSubquery".equals(code.getName()) || "SelectGroup".equals(code.getName()))
				Load.addSelect(select, state, code);
		}
		
		if (! select.isEmpty()) {
			fields = SelectFields.of(select.getFieldAsList("Select"));
		}
		
		this.clearChildren();
		
		String vari = StackUtil.stringFromSource(state,"Variant", "full");
		String path = StackUtil.stringFromSource(state,"Path", "/store/product");

		RecordStruct meta = GalleryUtil.getMeta(path);

		AtomicLong currimg = new AtomicLong();
		
		BasicRequestContext requestContext = BasicRequestContext.ofDefaultDatabase();
		TablesAdapter db = TablesAdapter.of(requestContext);

		// don't hide any entry if editable is on
		boolean editable = UIUtil.isEditReady(state, this);

		for (XElement prod : products) {
			String palias = prod.getAttribute("Alias");
			boolean showprod = true;
			
			String id = Struct.objectToString(db.firstInIndex("dcmProduct", "dcmAlias", palias, true));
			
			if (StringUtil.isEmpty(id)) {
				if (! editable)
					continue;

				showprod = false;
			}
			
			if (Struct.objectToBooleanOrFalse(db.getScalar("dcmProduct", id, "dcmDisabled"))) {
				if (! editable)
					continue;

				showprod = false;
			}


			if (! Struct.objectToBooleanOrFalse(db.getScalar("dcmProduct", id, "dcmShowInStore"))) {
				if (! editable)
					continue;

				showprod = false;
			}

			long cidx = currimg.incrementAndGet();

			// the idea of max doesn't really make sense for this wdiget
			//if (cidx > maximgs)
			//	break;
			
			String ppath = path + "/" + palias;

			RecordStruct img = RecordStruct.record();
			
			RecordStruct product = RecordStruct.record()
					.with("Alias", palias)
					.with("Element", prod)
					.with("Image", img)
					.with("Data", TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmProduct", id, fields));
			
			String ext = meta.getFieldAsString("Extension", "jpg");
			
			String image = Struct.objectToString(db.getScalar("dcmProduct", id, "dcmImage"));

			if (StringUtil.isEmpty(image))
				image = "main";

			ppath = ppath + "/" + image;
			
			img.with("Alias", image);
			img.with("Path", "/galleries" + ppath + ".v/" + vari + "." + ext);

			RecordStruct imgmeta = GalleryUtil.getImageMeta(ppath);

			img.with("Data", imgmeta);
			
			try {
				// setup image for expand
				StackUtil.addVariable(state, "product-" + cidx, product);
				
				// switch Products during expand
				XElement setvar = Var.tag()
						.withAttribute("Name", "Product")
						.withAttribute("SetTo", "$product-" + cidx);
				
				StoreGalleryWidget.this.with(setvar);
				
				// add nodes using the new variable
				XElement entry = template.deepCopy();
				
				for (XNode node : entry.getChildren()) {
					StoreGalleryWidget.this.with(node);

					// if only showing because of CMS, mark it hidden
					if (! showprod && (node instanceof Base)) {
						((Base) node).withClass("dc-widget-hidden");
					}
				}
			}
			catch (OperatingContextException x) {
				Logger.warn("Could not reference product data: " + x);
			}
		}
		
		UIUtil.markIfEditable(state, this, "widget");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("pure-g", "dc-widget", "dc-widget-gallery")
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName())
			.withAttribute("data-variant", StackUtil.stringFromSource(state,"Variant", "full"))
			.withAttribute("data-ext", StackUtil.stringFromSource(state,"Extension", "jpg"))
			.withAttribute("data-path", StackUtil.stringFromSource(state,"Path", "/store/product"));
		
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
							.withAttribute("data-image-alias", "{$Product.Alias}")
							.withAttribute("data-image-info", "{$Product.Data}")
							.with(
									W3Closed.tag("img")
											.withClass("pure-img-inline")
											.withAttribute("src", "{$Product.Image.Path}")
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
			
			List<XElement> children = this.selectAll("Product");

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
			
			if ("SetProduct".equals(area)) {
				String alias = params.getFieldAsString("Alias");
				XElement fnd = null;
				
				for (XElement xel : this.selectAll("Product")) {
					if (alias.equals(xel.getAttribute("Alias"))) {
						fnd = xel;
						break;
					}
				}
				
				if (fnd == null) {
					fnd = XElement.tag("Product");
					
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
			
			if ("RemoveProduct".equals(area)) {
				String alias = params.getFieldAsString("Alias");
				XElement fnd = null;
				
				for (XElement xel : this.selectAll("Product")) {
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
