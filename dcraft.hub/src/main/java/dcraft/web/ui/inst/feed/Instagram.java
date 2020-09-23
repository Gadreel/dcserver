package dcraft.web.ui.inst.feed;

import dcraft.cms.util.GalleryUtil;
import dcraft.db.BasicRequestContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeString;
import dcraft.interchange.bigcommerce.BigCommerceProductListWork;
import dcraft.interchange.facebook.InstagramUtil;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Var;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.*;
import dcraft.task.IWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;
import dcraft.util.TimeUtil;
import dcraft.web.ui.inst.*;
import dcraft.web.ui.inst.cms.GalleryWidget;
import dcraft.web.ui.inst.form.ManagedForm;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import z.gei.db.estimator.product.List;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicLong;

public class Instagram extends Base implements ICMSAware, IReviewAware {
	static public Instagram tag() {
		Instagram el = new Instagram();
		el.setName("dcm.InstagramWidget");
		return el;
	}

	@Override
	public XElement newNode() {
		return Instagram.tag();
	}


	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			BasicRequestContext requestContext = BasicRequestContext.ofDefaultDatabase();
			TablesAdapter db = TablesAdapter.ofNow(requestContext);

			String alt = StackUtil.stringFromSource(state,"AltSettings");

			InstagramUtil.checkGetToken(db, alt, new OperationOutcomeString() {
				@Override
				public void callback(String result) throws OperatingContextException {
					state.getStore().with("Token", result);
					state.setState(ExecuteState.RESUME);

					// run as normal
					Instagram.this.renderBeforeChildren(state);
					Instagram.this.gotoTop(state);

					try {
						OperationContext.getAsTaskOrThrow().resume();
					}
					catch (Exception x) {
						Logger.error("Unable to resume after InstagramWidget inst: " + x);
					}
				}
			});

			return ReturnOption.AWAIT;
		}

		return super.run(state);
	}

	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String alt = StackUtil.stringFromSource(state,"AltSettings");
		String altcache = StackUtil.stringFromSource(state,"AltSettings", "default");
		ListStruct data = null;

		this.canonicalize();

		long maximgs = StackUtil.intFromSource(state,"Max", 24);

		XElement template = this.selectFirst("Template");

		this.clearChildren();

		try {
			OperationContext ctx = OperationContext.getOrThrow();
			BasicRequestContext requestContext = BasicRequestContext.ofDefaultDatabase();

			ZonedDateTime lastrefresh = Struct.objectToDateTime(requestContext.getInterface().get(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Stamp"));

			// if less than 15 minutes since last product sku cache, skip reload
			if ((lastrefresh != null) && lastrefresh.isAfter(TimeUtil.now().minusMinutes(60))) {
				data = Struct.objectToList(requestContext.getInterface().get(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Data"));
			}
			else {
				Logger.info("Collecting Instagram feed: " + altcache);

				XElement isettings = ApplicationHub.getCatalogSettings("Social-Instagram", alt);

				if (isettings == null) {
					Logger.warn("Missing Instagram settings.");
					return;
				}

				// try new Basic Display first
				String basictoken = isettings.attr("BasicToken");
				boolean newdata = false;

				if (StringUtil.isNotEmpty(basictoken)) {
					long cache = isettings.getAttributeAsInteger("Cache", 25);
					String userid = isettings.attr("UserId");

					data = ListStruct.list();

					String found = state.getStore().getFieldAsString("Token");

					if (StringUtil.isNotEmpty(found))
						basictoken = found;

					//URL url = new URL("https://graph.instagram.com/me?fields=media&limit=" + cache + "&access_token=" + basictoken);

					URL url = new URL("https://graph.instagram.com/v1.0/" + userid + "/media?fields=media&limit=" + cache + "&access_token=" + basictoken);

					CompositeStruct res = CompositeParser.parseJson(url);

					//if (res != null)
					//	System.out.println("I: " + res.toPrettyString());

					ListStruct mediadata = res.selectAsList("data");

					for (int i = 0; i < mediadata.size(); i++) {
						String mid = mediadata.selectAsString(i + "/id");

						RecordStruct media = Struct.objectToRecord(requestContext.getInterface().get(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Media-" + mid));
						ZonedDateTime ts = Struct.objectToDateTime(requestContext.getInterface().get(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Media-" + mid, "Stamp"));

						ZonedDateTime ex = TimeUtil.now().minusDays(15);

						if ((media == null) || (ts == null) || (ts.isBefore(ex))) {
							url = new URL("https://graph.instagram.com/" + mid
									+ "?fields=media_type,media_url,caption,permalink,thumbnail_url,timestamp&access_token=" + basictoken);

							res = CompositeParser.parseJson(url);

							requestContext.getInterface().set(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Media-" + mid, res);
							requestContext.getInterface().set(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Media-" + mid, "Stamp", TimeUtil.now());

							data.with(res);

							newdata = true;
						}
						else {
							data.with(media);
						}
					}
				}
				// use old IG API if not basic token
				else {
					String token = isettings.attr("Token");

					if (StringUtil.isEmpty(token)) {
						Logger.warn("Missing Instagram token.");
						return;
					}

					URL url = new URL("https://api.instagram.com/v1/users/self/media/recent/?access_token=" + token);

					CompositeStruct res = CompositeParser.parseJson(url);

					//if (res != null)
					//	System.out.println("I: " + res.toPrettyString());

					data = ((RecordStruct) res).getFieldAsList("data");

					newdata = true;
				}

				if (newdata)
					requestContext.getInterface().set(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Data", data);

				requestContext.getInterface().set(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Stamp", TimeUtil.now());
			}

			//System.out.println(data != null ? "present" : "missing");

			for (int i = 0; (i < data.size()) && (i < maximgs); i++) {
				RecordStruct entry = data.getItemAsRecord(i);

				// setup image for expand
				StackUtil.addVariable(state, "entry-" + i, entry);

				// switch images during expand
				XElement setvar = Var.tag()
						.withAttribute("Name", "Entry")
						.withAttribute("SetTo", "$entry-" + i);

				this.with(setvar);

				// add nodes using the new variable
				XElement tentry = template.deepCopy();

				for (XNode node : tentry.getChildren())
					this.with(node);
			}
		}
		catch (Exception x) {
			Logger.error("Unable to read Instagram feed data: " + x);
		}
	}

	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
		
		this.withClass("dcm-ig-listing");
		
		String alternate = this.getAttribute("Alternate");
		
		if (StringUtil.isNotEmpty(alternate))
			this.withAttribute("data-dcm-instagram-alternate", alternate);
		
		String count = this.getAttribute("Count");
		
		if (StringUtil.isNotEmpty(count))
			this.withAttribute("data-dcm-instagram-count", count);
    }

	@Override
	public void canonicalize() throws OperatingContextException {
		XElement template = this.selectFirst("Template");

		if (template == null) {
			// set default
			this.with(Base.tag("Template").with(
					Link.tag()
							.withClass("dcm-widget-instagram-entry")
							.attr("data-id", "{$Entry.id}")
							.attr("To", "{$Entry.link}")
							.with(
									W3.tag("img")
											.withClass("pure-img-inline")
											.withAttribute("alt", "{$Entry.caption.text|ifempty:}")
											.withAttribute("src", "{$Entry.images.low_resolution.url}")
							)
			));
		}
	}


	@Override
	public IWork buildReviewWork(RecordStruct reviewresult) throws OperatingContextException {
		return new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				BasicRequestContext requestContext = BasicRequestContext.ofDefaultDatabase();
				TablesAdapter db = TablesAdapter.ofNow(requestContext);

				String alt = Instagram.this.attr("AltSettings");

				InstagramUtil.checkGetToken(db, alt, new OperationOutcomeString() {
					@Override
					public void callback(String result) throws OperatingContextException {
						ListStruct messages = reviewresult.getFieldAsList("Messages");

						if (messages == null) {
							messages = ListStruct.list();
							reviewresult.with("Messages", messages);
						}

						if (StringUtil.isNotEmpty(result))
							messages.with(RecordStruct.record()
									.with("Level", "Info")
									.with("Message", "Instagram token found, all is well.")
							);
						else
							messages.with(RecordStruct.record()
									.with("Level", "Error")
									.with("Message", "Instagram token not found - this is a problem, see Instagram widget documentation.")
							);

						taskctx.returnEmpty();
					}
				});
			}
		};
	}

}
