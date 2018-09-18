package dcraft.web.ui.inst.feed;

import java.util.ArrayList;
import java.util.List;

import dcraft.db.request.query.CollectorField;
import dcraft.db.request.query.SelectDirectRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.script.StackUtil;
import dcraft.script.inst.Var;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.service.ServiceHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlUtil;

public class Feed extends Base {
	static public Feed tag() {
		Feed el = new Feed();
		el.setName("dcm.Feed");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Feed.tag();
	}

	protected ListStruct entries = null;
	protected boolean loaded = false;
	protected boolean readydone = false;

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (! this.loaded) {
			this.loaded = true;

			String feed = StackUtil.stringFromSource(state, "Name", "pages");

			SelectDirectRequest request = SelectDirectRequest.of("dcmFeed")
					.withSelect(SelectFields.select()
							.with("Id")
							.with("dcmAlias", "Alias")
							.with("dcmPath", "IndexPath")
							.with("dcmLocalPath", "Path")
							.with("dcmPublishAt", "PublishAt")
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
					.withCollector(CollectorField.collect()
							.withFunc("dcmScanFeed")
							.withFrom(StackUtil.refFromSource(state, "From", true))
							.withTo(StackUtil.refFromSource(state, "To", true))
							.withExtras(RecordStruct.record()
									.with("Feed", feed)
									.with("LastId", StackUtil.refFromSource(state, "LastId", true))
							)
							.withMax(StackUtil.intFromSource(state, "Max", 0))
					);

			ServiceHub.call(request, new OperationOutcomeStruct() {
				@Override
				public void callback(Struct result) throws OperatingContextException {
					Feed.this.entries = (ListStruct)result;

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
		String feed = StackUtil.stringFromSource(state, "Name", "pages");

		this.withAttribute("data-dcm-feed", feed);

		XElement tel = this.find("Template");
		XElement mtel = this.find("Empty");

		// start with clean children
		this.children = new ArrayList<>();

		// TODO put in defaults
		if ((tel == null) || (mtel == null))
			return;

		if (this.entries.isEmpty()) {
			Feed.this.withAll(mtel.getChildren());
		}
		else {
			for (int r = 0; r < this.entries.getSize(); r++) {
				RecordStruct info = this.entries.getItemAsRecord(r);

				StackUtil.addVariable(state, "entry-" + r, info);

				// switch images during expand
				XElement setvar = Var.tag()
						.withAttribute("Name", "Entry")
						.withAttribute("SetTo", "$entry-" + r);

				Feed.this.with(setvar);

				//Feed.this.with(W3.tag("p").withText(info.getFieldAsString("Path") + " - " + info.selectAsString("Author.Last")));

				List<XNode> template = XmlUtil.deepCopyChildren(tel.getChildren());

				Feed.this.withAll(template);
			}
		}

		//this.with(W3.tag("p").withText("done"));

        // now build up the xml for the content
		/* TODO restore
        StringBuilder out = new StringBuilder();

        out.append("<div>");
		
		work.get().incBuild();
		
		ServiceHub.call(
				new SelectDirectRequest()
					.withTable("dcmFeed")
					.withSelect(new SelectFields()
						.withField("Id")
						.withField("dcmPath", "Path")
						.withField("dcmLocalPath", "LocalPath")
						.withField("dcmFields", "Fields", null, true)
				)
				.withCollector(new CollectorFunc("dcmFeedScan").withExtra(new RecordStruct()
					.withField("Channel", channel)
					.withField("Reverse", reverse)
					.withField("FromDate", fromdate)
					.withField("Max", max)
				))
				.toServiceRequest()
				.withOutcome(
					new OperationOutcomeStruct() {
						@Override
						public void callback(Struct result) throws OperatingContextException {
							if ((result != null) && ! result.isEmpty())  {
								//System.out.println("feed: " + result.toPrettyString());
								
								for (Struct str : ((ListStruct)result).items()) {
									RecordStruct drec = (RecordStruct) str;
									
									FeedParams ftemp = new FeedParams();
									ftemp.setFeedData(drec);
									
									String template = tel.getText();
									
									String value = ftemp.expandMacro(template);
									
									value = value.replace("*![CDATA[", "<![CDATA[").replace("]]*", "]]>");
									
									out.append(value);
								}
							}
							else {
								String template = mtel.getText();
								
								String value = Feed.this.expandMacro(template);
								
								value = value.replace("*![CDATA[", "<![CDATA[").replace("]]*", "]]>");
								
								out.append(value);
							}
	
							out.append("</div>");
	
							XElement lbox = OperationContext.getOrThrow().getSite().getWebsite().parseUI(out);
							
							if (lbox != null) {
								Feed.this.replaceChildren(lbox);
							}
							else {
								// TODO
								//pel.add(new UIElement("div")
								//	.withText("Error parsing section."));
								
								Logger.warn("Error adding feed entries: ");
								OperationContext.getAsTaskOrThrow().clearExitCode();   // TODO look for these and only clear for section covered
							}
							
							Feed.super.build(work);
							
							work.get().decBuild();
						}
					})
			);
			*/

		/*
		this.with(Button.tag("dcmi.AddFeedButton")
				.withClass("dcuiPartButton", "dcuiCmsi")
				.withAttribute("Icon", "fa-plus")
			);
			*/
	}
	
	
	/* MEM events Home
	 * 
	Hub.instance.getDatabase().submit(
		new SelectDirectRequest()
			.withTable("dcmFeed")
			.withSelect(new SelectFields()
				.withField("Id")
				.withField("dcmPath", "Path")
				.withSubField("dcmFields", "Published.en", "Published")
				.withSubField("dcmFields", "StartAt.en", "StartAt")
				.withSubField("dcmFields", "TimeZone.en", "TimeZone")
				.withSubField("dcmFields", "Title.en", "Title")
				.withSubField("dcmFields", "Description.en", "Description")
		)
		.withCollector(new CollectorFunc("dcmFeedScan").withExtra(new RecordStruct()
			.withField("Channel", "Calendar")
			.withField("Reverse", true)
			.withField("FromDate", new LocalDate().minusDays(1))
			.withField("Max", 12)
		)),
		new ObjectResult() {
			public void process(CompositeStruct result) {
				if (result && result.size)  {
					for (def entry : result) {
						col2.add(new XElement("Link")
							.withAttribute("Page", entry.Path)
							.withAttribute("class", "event-entry")
							.with(new XElement("div")
								.withAttribute("class", "event-title")
								.with(new XElement("h3")
									.withCData(entry.Title)
								)
							)
							.with(new XElement("div")
								.withAttribute("class", "event-date")
								.withText(entry.StartAt)
							)
							.with(new XElement("div")
								.withAttribute("class", "event-content")
								.with(new XElement("AdvText")
									.withCData(entry.Description + "  Read more...")
								)
							)
						)
					}
				}
				else {
					col2.add(new XElement("p").withText("No events found"))
				}

				cd.countDown()
			}
		}
	)
		 * 
	 */
	
	/* LWV Home
	 * 

	// blogs
	Hub.instance.getDatabase().submit(
		new SelectDirectRequest()
			.withTable("dcmFeed")
			.withSelect(new SelectFields()
				.withField("Id")
				.withField("dcmPath", "Path")
				.withSubField("dcmFields", "StartAt.en", "StartAt")
				.withSubField("dcmFields", "Image.en", "Image")
				.withSubField("dcmFields", "Title.en", "Title")
				.withSubField("dcmFields", "Slug.en", "Slug")
				.withSubField("dcmFields", "Details.en", "Details")
				.withSubField("dcmFields", "Summary.en", "Summary")
		)
		.withCollector(new CollectorFunc("dcmFeedScan").withExtra(new RecordStruct()
			.withField("Channel", "Blog")
			//.withField("Reverse", true)
			.withField("FromDate", new LocalDate().plusDays(1))
			.withField("Max", 2)
		)),
		new ObjectResult() {
			public void process(CompositeStruct result) {
				def blogel = frag.source.findId("homeBlog")

				if (result && result.size)  {
					for (def entry : result) {						
						blogel.with(new XElement("a")
								.withAttribute("class", "ui-link")
								.withAttribute("href", "/Blog/" + entry.Slug)
								.with(new XElement("h3")
									.withCData(entry.Title)
								)
							)
							.with(new XElement("img")
								.withAttribute("src", entry.Image)
							)
							.with(new XElement("AdvText")
								.withCData(entry.Summary + " [Read More](/Blog/" + entry.Slug + ")")
							)
					}
				}
				else {
					blogel.add(new XElement("div")
						.with(new XElement("p").withText("No blog entries found"))
					)
				}

				cd.countDown()
			}
		}
	)

	// schedules
	Hub.instance.getDatabase().submit(
		new SelectDirectRequest()
			.withTable("dcmFeed")
			.withSelect(new SelectFields()
				.withField("Id")
				.withField("dcmPath", "Path")
				.withSubField("dcmFields", "StartAt.en", "StartAt")
				.withSubField("dcmFields", "EndAt.en", "EndAt")
				.withSubField("dcmFields", "Title.en", "Title")
				.withSubField("dcmFields", "Slug.en", "Slug")
				.withSubField("dcmFields", "Summary.en", "Summary")
		)
		.withCollector(new CollectorFunc("dcmFeedScan").withExtra(new RecordStruct()
			.withField("Channel", "Schedule")
			.withField("Reverse", true)
			.withField("FromDate", new LocalDate().minusDays(0))
			.withField("Max", 5)
		)),
		new ObjectResult() {
			public void process(CompositeStruct result) {
				def schel = frag.source.findId("lstSchedule")

				if (result && result.size)  {
					def zone = DateTimeZone.forID("America/Chicago")
					def parse = DateTimeFormat.forPattern("yyyy-MM-dd").withZone(zone)
					def fmt = DateTimeFormat.forPattern("MMMM").withZone(zone)
					
					for (def entry : result) {		
						def at = parse.parseDateTime(entry.StartAt)
						def yr = at.year().get()
						def day = at.dayOfMonth().get()

						def when = fmt.print(at) + " " + day + " - "
						
						if (entry.EndAt) {
							def eat = parse.parseDateTime(entry.EndAt)
						
							when = fmt.print(at) + " " + day + "-" +
								fmt.print(eat) + " " + eat.dayOfMonth().get() + 
								" - "
						}
						
						schel
							.with(new XElement("p")
								.withCData(when)
								.with(new XElement("nbsp"))
								.with(new XElement("a")
									.withAttribute("href", "/Schedule")
									.withAttribute("class", "ui-link")
									.withCData(entry.Title)
								)
							)
					}
				}
				else {
					schel.add(new XElement("div")
						.with(new XElement("p").withText("No schedule entries found"))
					)
				}
				
				schel
					.with(new XElement("p")
						.with(new XElement("a")
							.withAttribute("href", "/Schedule")
							.withAttribute("class", "ui-link")
							.withCData("More")
						)
					)
					
				cd.countDown()
			}
		}
	)
	
	// news
	Hub.instance.getDatabase().submit(
		new SelectDirectRequest()
			.withTable("dcmFeed")
			.withSelect(new SelectFields()
				.withField("Id")
				.withField("dcmPath", "Path")
				.withSubField("dcmFields", "StartAt.en", "StartAt")
				.withSubField("dcmFields", "Image.en", "Image")
				.withSubField("dcmFields", "Title.en", "Title")
				.withSubField("dcmFields", "Slug.en", "Slug")
				.withSubField("dcmFields", "Details.en", "Details")
				.withSubField("dcmFields", "Summary.en", "Summary")
		)
		.withCollector(new CollectorFunc("dcmFeedScan").withExtra(new RecordStruct()
			.withField("Channel", "News")
			//.withField("Reverse", true)
			.withField("FromDate", new LocalDate().plusDays(1))
			.withField("Max", 2)
		)),
		new ObjectResult() {
			public void process(CompositeStruct result) {
				def blogel = frag.source.findId("homeNewsText")

				if (result && result.size)  {
					def entry = result[0]
					
					blogel.with(new XElement("div")
						.with(new XElement("a")
								.withAttribute("class", "ui-link")
								.withAttribute("href", "/News/" + entry.Slug)
								.with(new XElement("h3")
									.withCData(entry.Title)
								)
							)
							.with(new XElement("AdvText")
								.withCData(entry.Summary + " [Read More](/News/" + entry.Slug + ")")
							)
						)
						
					if (result.size > 1)  {
						entry = result[1]
					
						blogel.with(new XElement("div")
							.with(new XElement("a")
									.withAttribute("class", "ui-link")
									.withAttribute("href", "/News/" + entry.Slug)
									.with(new XElement("h3")
										.withCData(entry.Title)
									)
								)
								.with(new XElement("AdvText")
									.withCData(entry.Summary + " [Read More](/News/" + entry.Slug + ")")
								)
							)
					}
				}
				else {
					blogel.add(new XElement("div")
						.with(new XElement("p").withText("No news entries found"))
					)
				}

				cd.countDown()
			}
		}
	)
		 * 
	 */
	
	/* LWV Blog
	 * 
	 * 
	 */
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withClass("dcm-cms-editable", "dcm-feed")
			//.withAttribute("data-dccms-edit", this.getAttribute("AuthTags", "Editor,Admin,Developer"))
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
	}
}
