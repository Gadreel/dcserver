package dcraft.web.ui.inst.cms;

import dcraft.cms.util.GalleryUtil;
import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
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
import dcraft.stream.record.ListSourceStream;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.W3Closed;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class StoreCategoryWidget extends Base implements ICMSAware {
	static public StoreCategoryWidget tag() {
		StoreCategoryWidget el = new StoreCategoryWidget();
		el.setName("dcm.StoreCategoryWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return StoreCategoryWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		long maximgs = StackUtil.intFromSource(state,"Max", 24);
		String missing = StackUtil.stringFromSource(state,"Missing");

		XElement template = this.selectFirst("Template");
		
		XElement data = this.selectFirst("Data");

		if (data == null) {
			return;
		}

		ListStruct categories = Struct.objectToList(data.getText());
		
		// supports a custom query in the control - parse like LoadRecord does...
		
		SelectFields fields = SelectFields.select()
				.with("Id")
				.with("dcmAlias", "Alias")
				.with("dcmTitle", "Title")
				.with("dcmMode", "Mode")
				.with("dcmDescription", "Description")
				.withComposer("dcmStoreImage", "Image");

		//		<Table Id="dcmCategory">
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
		String path = StackUtil.stringFromSource(state,"Path", "/store/category");
		
		RecordStruct meta = (RecordStruct) GalleryUtil.getMeta(path,
				OperationContext.getOrThrow().selectAsString("Controller.Request.View"));

		AtomicLong currimg = new AtomicLong();
		
		BasicRequestContext requestContext = BasicRequestContext.ofDefaultDatabase();
		TablesAdapter db = TablesAdapter.ofNow(requestContext);

		categories = fillLevel(state, db, categories, fields, meta, vari, path, missing);

		for (int c = 0; c < categories.size(); c++) {
			RecordStruct category = categories.getItemAsRecord(c);

			long cidx = currimg.incrementAndGet();
			
			if (cidx > maximgs)
				break;

			try {
				// setup image for expand
				StackUtil.addVariable(state, "category-" + cidx, category);
				
				// switch Products during expand
				XElement setvar = Var.tag()
						.withAttribute("Name", "Category")
						.withAttribute("SetTo", "$category-" + cidx);
				
				StoreCategoryWidget.this.with(setvar);
				
				// add nodes using the new variable
				XElement entry = template.deepCopy();
				
				for (XNode node : entry.getChildren()) {
					StoreCategoryWidget.this.with(node);

					// if only showing because of CMS, mark it hidden
					if (category.getFieldAsBooleanOrFalse("CMSOnly") && (node instanceof Base)) {
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
			.withAttribute("data-path", StackUtil.stringFromSource(state,"Path", "/store/category"));
		
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

	protected ListStruct fillLevel(InstructionWork state, TablesAdapter db, ListStruct categories, SelectFields fields, RecordStruct meta, String vari, String path, String missing) throws OperatingContextException {
		ListStruct result = ListStruct.list();

		boolean editable = UIUtil.isEditReady(state, this);

		for (int c = 0; c < categories.size(); c++) {
			RecordStruct category = categories.getItemAsRecord(c);

			String alias = category.getFieldAsString("Alias");
			boolean showprod = true;

			String id = Struct.objectToString(db.firstInIndex("dcmCategory", "dcmAlias", alias, true));

			if (StringUtil.isEmpty(id)) {
				if (! editable)
					continue;

				showprod = false;
			}

			if (! Struct.objectToBooleanOrFalse(db.getStaticScalar("dcmCategory", id, "dcmShowInStore"))){
				if (! editable)
					continue;

				showprod = false;
			}

			category.copyFields(TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmCategory", id, fields));

			if (category.isNotFieldEmpty("Subs")) {
				ListStruct subs = category.getFieldAsList("Subs");

				category.with("Subs", fillLevel(state, db, subs, fields, meta, vari, path, missing));
			}

			String ext = meta.getFieldAsString("Extension", "jpg");

			String lpath = category.getFieldAsString("Image") + ".v/" + vari + "." + ext;

			Path imgpath = OperationContext.getOrThrow().getSite().findSectionFile("galleries", lpath,
					OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

			boolean found = false;

			if ((imgpath == null) || ! Files.exists(imgpath)) {
				if (StringUtil.isNotEmpty(missing)) {
					lpath = "/store/category/" + missing + ".v/" + vari + "." + ext;

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
					category.with("Path", "/galleries" + lpath + "?dc-cache=" + TimeUtil.stampFmt.format(LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.of("UTC"))));
				}
				catch (IOException x) {
					Logger.warn("Problem finding image file: " + lpath);
					category.with("Path", "/galleries" + lpath);
				}
			}
			else {
				Logger.warn("Problem finding image file: " + lpath);
				category.with("Path", "/galleries" + lpath);
			}

			if (! showprod)
				category.with("CMSOnly", "true");

			if (showprod || editable)
				result.with(category);
		}

		return result;
	}

	@Override
	public void canonicalize() throws OperatingContextException {
		XElement template = this.selectFirst("Template");
		
		if (template == null) {
			// set default
			this.with(Base.tag("Template").with(
					W3.tag("a")
							.withAttribute("href", "#")
							.withAttribute("data-image-alias", "{Category.Alias}")
							.withAttribute("data-image-info", "{$Category.Data}")
							.with(
									W3Closed.tag("img")
											.withClass("pure-img-inline")
											.withAttribute("src", "{$Category.Image.Path}")
							)
			));
		}
	}

	@Override
	public boolean applyCommand(CommonPath path, XElement root, RecordStruct command) throws OperatingContextException {
		XElement part = this;
		
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

			if ("Data".equals(area)) {
				ListStruct targetcontent = params.getFieldAsList("Data");

				XElement datatag = this.selectFirst("Data");

				if (datatag == null) {
					datatag = XElement.tag("Data");
					this.with(datatag);
				}

				datatag.clearChildren();
				datatag.withText(targetcontent.toPrettyString());

				return true;
			}

			/*
			if ("AddCategories".equals(area)) {
				XElement datatag = this.selectFirst("Data");

				if (datatag == null) {
					datatag = XElement.tag("Data");
					this.with(datatag);
				}

				ListStruct currlist = Struct.objectToList(datatag.getText());

				if (currlist == null)
					currlist = ListStruct.list();

				ListStruct ids = params.getFieldAsList("CategoryIds");

				if (ids != null) {
					BasicRequestContext requestContext = BasicRequestContext.ofDefaultDatabase();
					TablesAdapter db = TablesAdapter.ofNow(requestContext);

					for (int i = 0; i < ids.size(); i++) {
						String cid = ids.getItemAsString(i);

						String calias = Struct.objectToString(db.getStaticScalar("dcmCategory", cid, "dcmAlias"));

						//-- already present?

						if (StringUtil.isNotEmpty(calias)) {
							currlist.with(RecordStruct.record()
									.with("Alias", calias)
							);
						}
					}
				}

				datatag.withText(currlist.toPrettyString());

				return true;
			}
			 */
		}

		return false;
	}
}
