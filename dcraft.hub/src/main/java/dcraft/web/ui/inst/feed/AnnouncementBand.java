package dcraft.web.ui.inst.feed;

import dcraft.db.request.query.*;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.script.StackUtil;
import dcraft.script.inst.Var;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.service.ServiceHub;
import dcraft.struct.*;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlUtil;

import java.util.ArrayList;
import java.util.List;

public class AnnouncementBand extends Base {
	static public AnnouncementBand tag() {
		AnnouncementBand el = new AnnouncementBand();
		el.setName("dcm.AnnouncementBand");
		return el;
	}

	protected ListStruct entries = null;
	protected boolean loaded = false;
	protected boolean readydone = false;
	protected boolean hidden = false;

	@Override
	public XElement newNode() {
		return AnnouncementBand.tag();
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (! this.loaded) {
			this.loaded = true;

			String feed = StackUtil.stringFromSource(state, "Name", "announcements");

			SelectDirectRequest request = SelectDirectRequest.of("dcmFeed")
					.withSelect(SelectFields.select()
							.with("Id")
							.with("dcmAlias", "Alias")
							.with("dcmPath", "IndexPath")
							.with("dcmLocalPath", "Path")
							.with("dcmPublishAt", "PublishAt")
							.with("dcmAnnounceStartAt", "StartAtInternal")
							.with("dcmAnnounceStartAt", "StartAtSortable", "cast:dcMetaString")
							.with("dcmAnnounceTopic", "Topic")
							.with("dcmModified", "Modified")
							.with("dcmTags", "Tags")
							.withSubquery("dcmAuthor","Author", SelectFields.select()
									.with("Id")
									.with("dcLastName", "Last")
									.with("dcFirstName", "First")
							)
							.with("dcmLocaleFields", "LocaleFields", null, true)
							.with("dcmSharedFields", "SharedFields", null, true)
					)
					.withWhere(
							WhereAnd.of(
									WhereAny.of("dcmAnnounceTopic", StackUtil.stringFromSourceClean(state, "Topic", "default")),
									WhereLessThanOrEqual.of("dcmAnnounceStartAt", TimeUtil.now())
							)
					)
					.withCollector(CollectorField.collect()
							.withFunc("dcmScanFeed")
							.withFrom(StackUtil.stringFromSourceClean(state, "From", "now"))
							.withTo(StackUtil.stringFromSourceClean(state, "To", "+"))
							.withExtras(RecordStruct.record()
									.with("Feed", feed)
									.with("LastId", StackUtil.refFromSource(state, "LastId", true))
							)
							.withMax(StackUtil.intFromSource(state, "Max", 0))
					);

			ServiceHub.call(request, new OperationOutcomeStruct() {
				@Override
				public void callback(BaseStruct result) throws OperatingContextException {
					AnnouncementBand.this.entries = (ListStruct)result;

					AnnouncementBand.this.entries.sortRecords("StartAtSortable", false);

					for (int r = 0; r < entries.getSize(); r++) {
						RecordStruct info = entries.getItemAsRecord(r);

						ListStruct sharedFields = info.getFieldAsList("SharedFields");
						ListStruct localeFields = info.getFieldAsList("LocaleFields");

						info.removeField("SharedFields");
						info.removeField("LocaleFields");

						RecordStruct fields = RecordStruct.record();

						// get current locale first
						String myLocale = "." + OperationContext.getOrThrow().getLocale();

						for (int i = 0; i < localeFields.getSize(); i++) {
							RecordStruct full = localeFields.getItemAsRecord(i);

							String fieldName = full.getFieldAsString("SubId");

							if (fieldName.endsWith(myLocale)) {
								fieldName = fieldName.substring(0, fieldName.length() - myLocale.length());

								fields.with(fieldName, full.getField("Data"));
							}
						}

						// get site locale second
						String siteLocale = "." + OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

						if (! myLocale.equals(siteLocale)) {
							for (int i = 0; i < localeFields.getSize(); i++) {
								RecordStruct full = localeFields.getItemAsRecord(i);

								String fieldName = full.getFieldAsString("SubId");

								if (fieldName.endsWith(siteLocale)) {
									fieldName = fieldName.substring(0, fieldName.length() - myLocale.length());

									if (!fields.hasField(fieldName))
										fields.with(fieldName, full.getField("Data"));
								}
							}
						}

						// third - add shared fields only if not overridden by locale fields
						for (int i = 0; i < sharedFields.getSize(); i++) {
							RecordStruct full = sharedFields.getItemAsRecord(i);

							String fieldName = full.getFieldAsString("SubId");

							if (! fields.hasField(fieldName))
								fields.with(fieldName, full.getField("Data"));
						}

						info.with("Fields", fields);
					}

					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		if (! this.readydone) {
			state.setState(ExecuteState.READY);
			this.readydone = true;
		}

		return super.run(state);
	}

	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String feed = StackUtil.stringFromSource(state, "Name", "announcements");

		this.withAttribute("data-dcm-feed", feed);

		XElement tel = this.find("Template");

		this.hidden = this.getAttributeAsBooleanOrFalse("Hidden");

		// start with clean children
		this.children = new ArrayList<>();

		// TODO put in default template

		if ((tel == null) || this.entries.isEmpty()) {
			this.hidden = true;
			return;
		}

		String id = StackUtil.stringFromSource(state,"id");
		
		// Full (aka None), Wide, Medium, Narrow
		String width = StackUtil.stringFromSource(state,"Width", "Wide").toLowerCase();
		
		this.withClass("dc-band");
		
		// None, Small, Medium, Large, Extra
		String pad = StackUtil.stringFromSource(state, "Pad", "none").toLowerCase();

		XElement bodyui = W3.tag("div")
				.withClass( "dc-band-wrapper", "dc-band-width-" + width, "dc-band-pad-" + pad);
		
		if (StringUtil.isNotEmpty(id))
			bodyui.withAttribute("id", id + "Body");

		for (int r = 0; r < this.entries.getSize(); r++) {
			RecordStruct info = this.entries.getItemAsRecord(r);

			StackUtil.addVariable(state, "entry-" + r, info);

			// switch images during expand
			XElement setvar = Var.tag()
					.withAttribute("Name", "Entry")
					.withAttribute("SetTo", "$entry-" + r);

			bodyui.add(setvar);

			//Feed.this.with(W3.tag("p").withText(info.getFieldAsString("Path") + " - " + info.selectAsString("Author.Last")));

			List<XNode> template = XmlUtil.deepCopyChildren(tel.getChildren());

			bodyui.withAll(template);
		}

		this.with(bodyui);
		
		UIUtil.markIfEditable(state, this, "band");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		boolean editable = UIUtil.canEdit(state, this);

		if (this.hidden) {
			this.withClass("dc-band-hidden");
		}

		if (! this.hidden || editable) {
			this
					.withAttribute("data-dc-enhance", "true")
					.withAttribute("data-dc-tag", this.getName());

			this.setName("div");
		}
		else {
			this.clearChildren();
			this.clearAttributes();
			this.withAttribute("data-dc-tag", this.getName());
			this.setName("div");
		}
	}

}
