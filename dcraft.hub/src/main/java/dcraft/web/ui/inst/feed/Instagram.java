package dcraft.web.ui.inst.feed;

import dcraft.cms.util.GalleryUtil;
import dcraft.db.BasicRequestContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.interchange.bigcommerce.BigCommerceProductListWork;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Var;
import dcraft.script.work.InstructionWork;
import dcraft.struct.*;
import dcraft.task.StateWorkStep;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;
import dcraft.util.TimeUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.IncludeParam;
import dcraft.web.ui.inst.Link;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.cms.GalleryWidget;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Instagram extends Base implements ICMSAware {
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
			if ((lastrefresh != null) && lastrefresh.isAfter(TimeUtil.now().minusMinutes(15))) {
				data = Struct.objectToList(requestContext.getInterface().get(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Data"));
			}
			else {
				Logger.info("Collecting Instagram feed: " + altcache);

				XElement isettings = ApplicationHub.getCatalogSettings("Social-Instagram", alt);

				if (isettings == null) {
					Logger.warn("Missing Instagram settings.");
					return;
				}

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

				requestContext.getInterface().set(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Stamp", TimeUtil.now());
				requestContext.getInterface().set(ctx.getTenant().getAlias(), "dcmInstagramWidget", altcache, "Data", data);
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
}
