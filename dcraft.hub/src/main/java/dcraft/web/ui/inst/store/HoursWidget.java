package dcraft.web.ui.inst.store;

import dcraft.db.Constants;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.service.ServiceHub;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.web.ui.inst.TextWidget;
import dcraft.xml.XElement;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class HoursWidget extends Base {
	static public HoursWidget tag() {
		HoursWidget el = new HoursWidget();
		el.setName("dcm.HoursWidget");
		return el;
	}

	protected RecordStruct hoursdata = null;
	protected boolean hoursloaded = false;
	protected boolean readydone = false;

	@Override
	public XElement newNode() {
		return HoursWidget.tag();
	}

	/*
				{
					"Widget": {
						"Open": {
							"en": "We are Open\n{Hours.Open} to {Hours.Close}\nToday"
						},
						"Closed": {
							"en": "We are Closed\nToday"
						}
					},
					"Days": {
						"mon": { Open: 10, Close: 20 },
						"tue": { Open: 10, Close: 20 },
						"wed": { Open: 10, Close: 20 },
						"thu": { Open: 10, Close: 20 },
						"fri": { Open: 10, Close: 20 },
						"sat": { Open: 10, Close: "17:30" },
						"sun": { Open: 12, Close: 16 }
					},
					"ShortHours": {
						"Locale": {
							"en": "Sunday Noon - 4 pm\nMonday - Thursday 10 am - 8 pm\nFriday - Saturday 10 am - 5:30 pm"
						}
					}
				}

	 */
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		if ((this.hoursdata != null) && (this.hoursdata.isNotFieldEmpty("Widget"))) {
			LocalDate date = LocalDate.now();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE");

			String dow = date.format(formatter).toLowerCase();

			RecordStruct hdata = this.hoursdata.getFieldAsRecord("Days").getFieldAsRecord(dow);

			if (hdata != null) {
				DateTimeFormatter timeFormatter1 = DateTimeFormatter.ofPattern("h:mm");
				DateTimeFormatter timeFormatter2 = DateTimeFormatter.ofPattern("h");

				String open = hdata.getFieldAsString("Open");

				int opos = open.indexOf(':');
				String hour = (opos == -1) ? open : open.substring(0, opos);
				String minute = (opos == -1) ? "0" : open.substring(opos + 1);

				int ihour = Integer.parseInt(hour);
				int iminute = Integer.parseInt(minute);

				ZonedDateTime opendate = ZonedDateTime.now()
						.withHour(ihour)
						.withMinute(iminute);

				hdata.with("Open", (iminute == 0) ? timeFormatter2.format(opendate) : timeFormatter1.format(opendate));

				String close = hdata.getFieldAsString("Close");

				int cpos = close.indexOf(':');
				hour = (cpos == -1) ? close : close.substring(0, cpos);
				minute = (cpos == -1) ? "0" : close.substring(cpos + 1);

				ihour = Integer.parseInt(hour);
				iminute = Integer.parseInt(minute);

				ZonedDateTime closedate = ZonedDateTime.now()
						.withHour(ihour)
						.withMinute(iminute);

				hdata.with("Close", (iminute == 0) ? timeFormatter2.format(closedate) : timeFormatter1.format(closedate));

				StackUtil.addVariable(state, "Hours", hdata);
			}

			// TODO checks
			String msg = this.hoursdata.getFieldAsRecord("Widget").getFieldAsRecord("Open").getFieldAsString("en");

			this.with(TextWidget.tag()
					.with(
							XElement.tag("Tr").withText(msg)
					)
			);
		}
		else {
			this.with(TextWidget.tag()
					.with(
							XElement.tag("Tr").withText("Missing Hours\nOf Operation\nConfiguration")
					)
			);
		}

		/*
		RecordStruct img = RecordStruct.record();
		
		String path = StackUtil.stringFromSource(state, "Path");
		
		RecordStruct meta = (RecordStruct) GalleryUtil.getMeta(CommonPath.from(path).getParent().toString(),
				OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
		
		boolean usesrcset = false;
		
		if (meta != null) {
			String ext = meta.getFieldAsString("Extension", "jpg");
			String vari = StackUtil.stringFromSource(state,"Variant", "full");

			RecordStruct vdata = GalleryUtil.findVariation(meta, vari);

			// TODO support alt variations and alt ext (from the gallery meta.json)
			img.with("Path", "/galleries" + path + ".v/" + vari + "." + ext);
			img.with("Gallery", meta);
			img.with("Variant", vdata);
			
			RecordStruct imgmeta = (RecordStruct) GalleryUtil.getMeta(path + ".v",
					OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
			
			img.with("Data", imgmeta);
			
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

			if (imgmeta != null)
				this.withAttribute("data-dc-image-data", imgmeta.toString());
		}
		
		StackUtil.addVariable(state, "Image", img);
		
		// look for a template, if found then skip default
		if (this.children != null) {
			for (int i = 0; i < this.children.size(); i++) {
				if (this.children.get(i) instanceof XElement)
					return;
			}
		}
		
		// set default
		
		this.with(W3.tag("div")
				.withClass("dc-media-box", "dc-media-image")
				.with(W3.tag("img")
						.withClass("pure-img-inline")
						.withAttribute("src", "{$Image.Path}")
						.withAttribute("srcset", usesrcset ? "{$Image.SourceSet}" : null)
				)
		);
		*/
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (! this.hoursloaded) {
			this.hoursloaded = true;

			String subid = StackUtil.stringFromSource(state, "Alternate", "default");

			ServiceHub.call(LoadRecordRequest.of("dcTenant")
					.withId(Constants.DB_GLOBAL_ROOT_RECORD)
					.withSelect(SelectFields.select()
							.withSubField("dcHoursOfOperation", subid, "HOO")
					)
					.toServiceRequest()
					.withOutcome(new OperationOutcomeStruct() {
						@Override
						public void callback(Struct result) throws OperatingContextException {
							if (! this.hasErrors() && (result instanceof RecordStruct))
								HoursWidget.this.hoursdata = ((RecordStruct) result).getFieldAsRecord("HOO");

							OperationContext.getAsTaskOrThrow().resume();
						}
					})
			);

			return ReturnOption.AWAIT;
		}

		if (! this.readydone) {
			state.setState(ExecuteState.READY);
			this.readydone = true;
		}

		return super.run(state);
	}

	@Override
	public void renderAfterChildren(InstructionWork state) {
		this
				.withClass("dc-widget", "dc-widget-store-hours")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
    }
}
