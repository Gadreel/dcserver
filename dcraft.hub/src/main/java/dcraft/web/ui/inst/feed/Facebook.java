package dcraft.web.ui.inst.feed;

import dcraft.cms.util.GalleryImageConsumer;
import dcraft.cms.util.GalleryUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Console;
import dcraft.script.inst.If;
import dcraft.script.inst.Var;
import dcraft.script.inst.doc.Out;
import dcraft.script.work.InstructionWork;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;
import dcraft.util.TimeUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.TextWidget;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.W3Closed;
import dcraft.web.ui.inst.cms.GalleryWidget;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlUtil;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Facebook extends Base {
	static public Facebook tag() {
		Facebook el = new Facebook();
		el.setName("dcm.FacebookWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Facebook.tag();
	}
	
	/*
{
  "data": [
    {
      "id": "41474727650_10154879725572651",
      "full_picture": "https://scontent.xx.fbcdn.net/v/t1.0-9/p720x720/24174523_10154879723662651_1950709644219068056_n.jpg?oh=7a1094e413987a5616d9d5fc609008c0&oe=5A8AD59D",
      "created_time": "2017-11-28T16:38:02+0000",
      "from": {
        "name": "Orange Tree Imports",
        "id": "41474727650"
      },
      "type": "photo",
      "message": "Show your love for Wisconsin even when far from home with these exclusive travel totes from Rilos & Mimi. A new supply just arrived!",
      "icon": "https://www.facebook.com/images/icons/photo.gif",
      "link": "https://www.facebook.com/orangetreeimports/photos/a.82398602650.91619.41474727650/10154879723662651/?type=3",
      "object_id": "10154879723662651",
      "picture": "https://scontent.xx.fbcdn.net/v/t1.0-0/p130x130/24174523_10154879723662651_1950709644219068056_n.jpg?oh=1ac306de40cf84e1c143922053295d0f&oe=5A9CD790",
      "permalink_url": "https://www.facebook.com/orangetreeimports/posts/10154879725572651"
    },
    {
      "id": "41474727650_10154875786467651",
      "full_picture": "https://scontent.xx.fbcdn.net/v/t1.0-0/p180x540/23905438_10154875782992651_1752219508318871049_n.jpg?oh=20f0315d5819a529eeb0ec086f49cbc3&oe=5A93C55D",
      "created_time": "2017-11-26T16:32:50+0000",
      "from": {
        "name": "Orange Tree Imports",
        "id": "41474727650"
      },
      "type": "photo",
      "message": "What fun for us to have Orange Tree Imports featured in this Small Business Saturday news spot by Grace Choi on Channel 27! https://tinyurl.com/yczvlhw4 Special thanks to the customers who were willing to go on camera saying nice things about our shop, and everyone who came to Monroe Street for Small Business Saturday!",
      "icon": "https://www.facebook.com/images/icons/photo.gif",
      "link": "https://www.facebook.com/orangetreeimports/photos/a.82398602650.91619.41474727650/10154875782992651/?type=3",
      "object_id": "10154875782992651",
      "picture": "https://scontent.xx.fbcdn.net/v/t1.0-0/s130x130/23905438_10154875782992651_1752219508318871049_n.jpg?oh=a693dd414586210ad85f3aa4d33a2908&oe=5A952F29",
      "permalink_url": "https://www.facebook.com/orangetreeimports/posts/10154875786467651"
    },
    {
      "id": "41474727650_10154874895352651",
      "full_picture": "https://external.xx.fbcdn.net/safe_image.php?d=AQCrf8IiI4FFrRKg&url=http%3A%2F%2Fwkow.images.worldnow.com%2Fimages%2F15492447_G.jpg&_nc_hash=AQAwPnsXg2IXd3nJ",
      "created_time": "2017-11-26T04:08:59+0000",
      "from": {
        "name": "Orange Tree Imports",
        "id": "41474727650"
      },
      "type": "link",
      "message": "Loved having Grace Choi from WKOW stop in at Orange Tree Imports during Small Business Saturday!",
      "caption": "wkow.com",
      "description": "MADISON (WKOW) -- Holiday shoppers supported their locally-ran stores for Small Business Saturday. Monroe Street, which is popular for its many specialty shops, was filled with shoppers on a beauti...",
      "icon": "https://www.facebook.com/images/icons/post.gif",
      "link": "http://www.wkow.com/story/36924703/2017/11/25/monroe-street-attracts-shoppers-on-small-business-saturday#.Who908429h4.facebook",
      "name": "Monroe Street attracts shoppers on Small Business Saturday",
      "picture": "https://external.xx.fbcdn.net/safe_image.php?d=AQDeJhaNf5BZLvnc&w=130&h=130&url=http%3A%2F%2Fwkow.images.worldnow.com%2Fimages%2F15492447_G.jpg&cfs=1&_nc_hash=AQClsVTdHdI5xNLn",
      "permalink_url": "https://www.facebook.com/orangetreeimports/posts/10154874895352651"
    },
    {
      "id": "41474727650_10154872757437651",
      "full_picture": "https://scontent.xx.fbcdn.net/v/t1.0-1/p200x200/18664569_10156207167801982_4446936749166185803_n.png?oh=08815fba079830f70fdae0b07002b404&oe=5ACE3A88",
      "created_time": "2017-11-25T04:01:01+0000",
      "from": {
        "name": "Orange Tree Imports",
        "id": "41474727650"
      },
      "type": "link",
      "message": "Thanks so much for sharing these great gift ideas, Isthmus!",
      "caption": "isthmus.com",
      "icon": "https://www.facebook.com/images/icons/post.gif",
      "link": "https://isthmus.com/isthmus-giving-magazine/goodies-and-gadgets-for-the-cook/",
      "name": "Gifts for the cook",
      "picture": "https://scontent.xx.fbcdn.net/v/t1.0-1/p200x200/18664569_10156207167801982_4446936749166185803_n.png?oh=08815fba079830f70fdae0b07002b404&oe=5ACE3A88",
      "story": "Orange Tree Imports shared Isthmus's post.",
      "story_tags": [
        {
          "id": "41474727650",
          "name": "Orange Tree Imports",
          "type": "page",
          "offset": 0,
          "length": 19
        },
        {
          "id": "19710366981",
          "name": "Isthmus",
          "type": "page",
          "offset": 27,
          "length": 7
        }
      ],
      "permalink_url": "https://www.facebook.com/orangetreeimports/posts/10154872757437651"
    },
    {
      "id": "41474727650_10154872635567651",
      "full_picture": "https://scontent.xx.fbcdn.net/v/t1.0-0/p180x540/23795319_10154872633602651_3474809385923963727_n.jpg?oh=7ccae83a24c08adae7c7402fc67e35ee&oe=5A939977",
      "created_time": "2017-11-25T02:21:47+0000",
      "from": {
        "name": "Orange Tree Imports",
        "id": "41474727650"
      },
      "type": "photo",
      "message": "Looking forward to welcoming you to Small Business Saturday on Monroe Street! Come enter our prize drawing for one of five Shop Small cloth tote bags, each with a $10 gift card to a neighboring shop or restaurant.",
      "icon": "https://www.facebook.com/images/icons/photo.gif",
      "link": "https://www.facebook.com/orangetreeimports/photos/a.82398602650.91619.41474727650/10154872633602651/?type=3",
      "object_id": "10154872633602651",
      "picture": "https://scontent.xx.fbcdn.net/v/t1.0-0/s130x130/23795319_10154872633602651_3474809385923963727_n.jpg?oh=3f8dc1fc07ced1698f476c245e2d15fe&oe=5A970803",
      "permalink_url": "https://www.facebook.com/orangetreeimports/posts/10154872635567651"
    },
    {
      "id": "41474727650_10154872094367651",
      "full_picture": "https://scontent.xx.fbcdn.net/v/t1.0-9/p720x720/23795671_1937663912917672_8218562197223412302_n.jpg?oh=0d884a8dd97f8b3034b61433610a69b2&oe=5ACEF17D",
      "created_time": "2017-11-24T20:23:10+0000",
      "from": {
        "name": "Orange Tree Imports",
        "id": "41474727650"
      },
      "type": "photo",
      "message": "We are so proud to be part of the vibrant Monroe Street business community!",
      "description": "After a restful thanksgiving, Monroe Street is ready for Small Business Saturday!
Show your love and #shopsmall ❤️
.
.
#monroestreetmadison #wisconsin #smallbusinesssaturday",
      "link": "https://www.facebook.com/monroestreetmadison/photos/a.159446600739421.37679.159433974074017/1937663912917672/?type=3",
      "name": "Monroe Street Madison",
      "object_id": "1937663912917672",
      "parent_id": "159433974074017_1937663912917672",
      "picture": "https://scontent.xx.fbcdn.net/v/t1.0-0/p130x130/23795671_1937663912917672_8218562197223412302_n.jpg?oh=5cefd04fd57fe8139b5c1d2c558aa5ab&oe=5ACEC168",
      "story": "Orange Tree Imports shared Monroe Street Madison's photo.",
      "story_tags": [
        {
          "id": "41474727650",
          "name": "Orange Tree Imports",
          "type": "page",
          "offset": 0,
          "length": 19
        },
        {
          "id": "159433974074017",
          "name": "Monroe Street Madison",
          "type": "page",
          "offset": 27,
          "length": 21
        },
        {
          "id": "1937663912917672",
          "name": "",
          "offset": 51,
          "length": 5
        }
      ],
      "permalink_url": "https://www.facebook.com/orangetreeimports/posts/10154872094367651"
    },
    {
      "id": "41474727650_129494844398489",
      "full_picture": "https://scontent.xx.fbcdn.net/v/t1.0-9/c201.0.450.450/23795403_10154871752807651_6300075622269254799_n.jpg?oh=96a8d312ae1a9df6da6161c6858ef7ba&oe=5A993D24",
      "created_time": "2017-11-24T17:16:23+0000",
      "from": {
        "name": "Orange Tree Imports",
        "id": "41474727650"
      },
      "type": "event",
      "message": "We are pleased to feature one of our food lines on Sampling Saturday every month. In December we'll be featuring caramels by Bequet Confections of Montana. These small-batch delicacies, winners of six national awards, are made with all natural ingredients. Come try one!   https://bequetconfections.com",
      "caption": "December Sampling Saturday",
      "description": "We are pleased to feature one of our food lines on Sampling Saturday every month. In December we'll be featuring caramels by Bequet Confections of Montana. These small-batch delicacies, winners of six national awards, are made with all natural ingredients. Come try one!   https://bequetconfections.com",
      "icon": "https://www.facebook.com/images/icons/event.gif",
      "link": "https://www.facebook.com/events/129494844398489/",
      "name": "December Sampling Saturday",
      "object_id": "129494844398489",
      "picture": "https://scontent.xx.fbcdn.net/v/t1.0-0/c58.0.130.130/p130x130/23795403_10154871752807651_6300075622269254799_n.jpg?oh=617d69c6bd38bd9a51c0a16c4b836e9f&oe=5A89AFE9",
      "story": "Orange Tree Imports added an event.",
      "story_tags": [
        {
          "id": "41474727650",
          "name": "Orange Tree Imports",
          "type": "page",
          "offset": 0,
          "length": 19
        },
        {
          "id": "129494844398489",
          "name": "December Sampling Saturday",
          "type": "event",
          "offset": 29,
          "length": 5
        }
      ],
      "permalink_url": "https://www.facebook.com/events/129494844398489/"
    },
    {
      "id": "41474727650_10154871538067651",
      "full_picture": "https://scontent.xx.fbcdn.net/v/t1.0-9/s720x720/23795151_10154871537832651_937149699367278315_n.jpg?oh=c2ff8c899761a671d4ba459bc541802f&oe=5AD2EAC0",
      "created_time": "2017-11-24T14:52:24+0000",
      "from": {
        "name": "Orange Tree Imports",
        "id": "41474727650"
      },
      "type": "photo",
      "icon": "https://www.facebook.com/images/icons/photo.gif",
      "link": "https://www.facebook.com/orangetreeimports/photos/a.82398602650.91619.41474727650/10154871537832651/?type=3",
      "object_id": "10154871537832651",
      "picture": "https://scontent.xx.fbcdn.net/v/t1.0-0/s130x130/23795151_10154871537832651_937149699367278315_n.jpg?oh=d174f23f7aefde69d7da66e11a375b7f&oe=5AD603D5",
      "permalink_url": "https://www.facebook.com/orangetreeimports/posts/10154871538067651"
    },
    {
      "id": "41474727650_10154869560657651",
      "full_picture": "https://scontent.xx.fbcdn.net/v/t1.0-9/s720x720/23843343_10154869560547651_7442210001424635721_n.jpg?oh=bbcdef76e5d7ea64d69e6fd6763042b4&oe=5A98C7B6",
      "created_time": "2017-11-23T16:08:39+0000",
      "from": {
        "name": "Orange Tree Imports",
        "id": "41474727650"
      },
      "type": "photo",
      "icon": "https://www.facebook.com/images/icons/photo.gif",
      "link": "https://www.facebook.com/orangetreeimports/photos/a.82398602650.91619.41474727650/10154869560547651/?type=3",
      "object_id": "10154869560547651",
      "picture": "https://scontent.xx.fbcdn.net/v/t1.0-0/s130x130/23843343_10154869560547651_7442210001424635721_n.jpg?oh=1772d22da58e972fb2ce2237c2522e53&oe=5A9824BB",
      "permalink_url": "https://www.facebook.com/orangetreeimports/posts/10154869560657651"
    },
    {
      "id": "41474727650_182716722283307",
      "full_picture": "https://scontent.xx.fbcdn.net/v/t1.0-9/c168.0.282.282/23844610_10154868112117651_8928357494485765186_n.jpg?oh=2d1b3edf4df86c0db4d1fcd5092fe865&oe=5AA0A0C6",
      "created_time": "2017-11-22T23:00:46+0000",
      "from": {
        "name": "Orange Tree Imports",
        "id": "41474727650"
      },
      "type": "event",
      "message": "Join the shops and restaurants on Monroe Street for a special day of holiday events!  Santa will arrive by convertible in the morning, and in the afternoon the Monroe Street carolers will entertain with songs of the season.  The Monroe Street Library will also hold a special book sale.  Details will be available at www.monroestreetmadison.com soon.",
      "caption": "Holiday Glow on Monroe",
      "description": "Join the shops and restaurants on Monroe Street for a special day of holiday events!  Santa will arrive by convertible in the morning, and in the afternoon the Monroe Street carolers will entertain with songs of the season.  The Monroe Street Library will also hold a special book sale.  Details will be available at www.monroestreetmadison.com soon.",
      "icon": "https://www.facebook.com/images/icons/event.gif",
      "link": "https://www.facebook.com/events/182716722283307/",
      "name": "Holiday Glow on Monroe",
      "object_id": "182716722283307",
      "picture": "https://scontent.xx.fbcdn.net/v/t1.0-0/c77.0.130.130/p130x130/23844610_10154868112117651_8928357494485765186_n.jpg?oh=b7de6d51cedc6c27c9ed11ccf67f9abf&oe=5A9E349B",
      "story": "Orange Tree Imports added an event.",
      "story_tags": [
        {
          "id": "41474727650",
          "name": "Orange Tree Imports",
          "type": "page",
          "offset": 0,
          "length": 19
        },
        {
          "id": "182716722283307",
          "name": "Holiday Glow on Monroe",
          "type": "event",
          "offset": 29,
          "length": 5
        }
      ],
      "permalink_url": "https://www.facebook.com/events/182716722283307/"
    }
  ],
  "paging": {
    "cursors": {
      "before": "Q2c4U1pXNTBYM0YxWlhKNVgzTjBiM0o1WDJsa0R4ODBNVFEzTkRjeU56WTFNRG96TWpJek1qZAzNOelV5TnpRM09EYzNORFE0RHd4aGNHbGZAjM1J2Y25sZAmFXUVBIVFF4TkRjME56STNOalV3WHpFd01UVTBPRGM1TnpJMU5UY3lOalV4RHdSMGFXMWxCbG9ka09vQgZDZD",
      "after": "Q2c4U1pXNTBYM0YxWlhKNVgzTjBiM0o1WDJsa0R4ODBNVFEzTkRjeU56WTFNRG8xTlRJME1USXpOak01TlRjNE16WXpOakkwRHd4aGNHbGZAjM1J2Y25sZAmFXUVBHelF4TkRjME56STNOalV3WHpFNE1qY3hOamN5TWpJNE16TXdOdzhFZAEdsdFpRWmFGZA0dlQVE9PQZDZD"
    },
    "next": "https://graph.facebook.com/v2.11/41474727650/posts?access_token=nnn&pretty=0&fields=id%2Cfull_picture%2Ccreated_time%2Cfrom%2Ctype%2Cmessage%2Cevent%2Ccaption%2Cdescription%2Cicon%2Clink%2Cname%2Cobject_id%2Cparent_id%2Cpicture%2Cplace%2Cproperties%2Csource%2Cstory%2Cstory_tags%2Ctarget%2Cpermalink_url&limit=10&after=Q2c4U1pXNTBYM0YxWlhKNVgzTjBiM0o1WDJsa0R4ODBNVFEzTkRjeU56WTFNRG8xTlRJME1USXpOak01TlRjNE16WXpOakkwRHd4aGNHbGZAjM1J2Y25sZAmFXUVBHelF4TkRjME56STNOalV3WHpFNE1qY3hOamN5TWpJNE16TXdOdzhFZAEdsdFpRWmFGZA0dlQVE9PQZDZD"
  }
}
	 */
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("dcm-fb-listing");
		
		List<XNode> children = this.children;
		
		this.clearChildren();

		// TODO strip spaces
		String[] ftypes = StackUtil.stringFromSource(state, "Types", "photo,link,event,status").split(",");
		
		String alternate = StackUtil.stringFromSource(state, "Alternate");
		
		long count = StackUtil.intFromSource(state, "Max", 8);
		
		XElement fbsettings = ApplicationHub.getCatalogSettings("Social-Facebook", alternate);
		
		if (fbsettings != null) {
			
			String cname = StringUtil.isNotEmpty(alternate) ? "Facebook-" + alternate + "-" + count : "Facebook-" + count;
			
			Site site = OperationContext.getOrThrow().getSite();;
			
			ListStruct list = (ListStruct) site.getCacheEntry(cname);
			
			if (list == null) {
				list = new ListStruct();
				
				try {
					URL url = new URL("https://graph.facebook.com/" + fbsettings.getAttribute("NodeId") +
							"/posts" + "?access_token=" + fbsettings.getAttribute("AccessToken") +
							"&fields=id,full_picture,created_time,from,type,message,permalink_url,object_id,caption,description,name" +
							"&limit=" + count);
					
					RecordStruct res = (RecordStruct) CompositeParser.parseJson(url);
					
					if (res == null) {
						Logger.error("Empty Facebook response");
						return;
					}
					
					ListStruct fblist = res.getFieldAsList("data");
					
					DateTimeFormatter incomingFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
					DateTimeFormatter outgoingFormat = DateTimeFormatter.ofPattern("MMM dd, hh:mm a", java.util.Locale.getDefault());
					
					for (Struct s : fblist.items()) {
						RecordStruct entry = (RecordStruct) s;
						
						String etype = entry.getFieldAsString("type");
						
						// TODO use from attr
						if (! "photo".equals(etype) && ! "link".equals(etype) && ! "event".equals(etype) && ! "status".equals(etype))
							continue;
						
						// TODO only filter if using default fields
						if (entry.isFieldEmpty("message"))
							continue;
						
						TemporalAccessor start = incomingFormat.parse(entry.getFieldAsString("created_time"));
						
						entry
								.with("Created", start)
								.with("CreatedFmt", outgoingFormat.format(start))
								.with("MessageMD", UIUtil.regularTextToMarkdown(entry.getFieldAsString("message")));
						
						list.with(entry);
					}
					
					Logger.info("Facebook cache set for: " + site.getTenant().getAlias() + " - " + site.getAlias());
					
					site.withCacheEntry(cname, list, StringUtil.parseInt(fbsettings.getAttribute("CachePeriod"), 900));
				}
				catch (Exception x) {
					Logger.error("Unable to load Facebook feed.");
					Logger.error("Detail: " + x);
				}
			}
			
			boolean hastemplate = false;
			
			if (children != null) {
				for (int i = 0; i < children.size(); i++) {
					if (children.get(i) instanceof XElement) {
						hastemplate = true;
						break;
					}
				}
			}
			
			/*
			stamp.append('<i>' + dc.util.Date.zToMoment(item.Posted).format('MMM D\\, h:mm a') + '</i>');
			
			 */
			
			if (! hastemplate) {
				children = new ArrayList<>();
				
				children.add(W3.tag("div")
						.withClass("dcm-fb-entry")
						.with(W3.tag("div")
							.withClass("dcm-fb-header")
							.with(W3.tag("div")
								.withClass("dcm-fb-icon")
								.with(W3.tag("img")
									.attr("src", "https://graph.facebook.com/{$Entry.from.id}/picture?type=small")
								),
								W3.tag("div")
									.withClass("dcm-fb-stamp")
									.with(W3.tag("div")
											.with(W3.tag("b")
													.withText("{$Entry.from.name}")
											),
										W3.tag("i")
											.withText("{$Entry.CreatedFmt}")
									)
							),
							W3.tag("div")
									.withClass("dcm-fb-body")
									.with(
											If.tag()
												.attr("Target", "$Entry.message")
												.attr("IsEmpty", "false")
												.with(
														Out.tag().with(
																TextWidget.tag()
																	.withClass("dcm-fb-message")
																	.with(XElement.tag("Tr")
																		.withText("{$Entry.MessageMD}")
																	)
														)
												),
											If.tag()
													.attr("Target", "$Entry.full_picture")
													.attr("IsEmpty", "false")
													.with(
															Out.tag().with(
																	W3.tag("img")
																			.withClass("dcm-fb-picture")
																			.attr("src","{$Entry.full_picture}")
															)
													),
											W3.tag("div")
													.withClass("dcm-fb-link")
													.with(W3.tag("a")
															.attr("target", "_blank")
															.attr("href","{$Entry.permalink_url}")
															//.attr("href","http://www.facebook.com/permalink.php?id=" +
															//		"{$Entry.from.id}&story_fbid={$Entry.object_id}")
															.withText("View on Facebook - Share")
													)
									)
					)
				);
			}
			
			List<XNode> fchildren = children;
			int cidx = 0;

			for (Struct ent : list.items()) {
				RecordStruct entry = (RecordStruct) ent;
				
				// setup image for expand
				StackUtil.addVariable(state, "entry-" + cidx, entry);
				
				// switch images during expand
				XElement setvar = Var.tag()
						.withAttribute("Name", "Entry")
						.withAttribute("SetTo", "$entry-" + cidx);
				
				this.with(setvar);
				
				//this.with(Console.tag().withText("{$Entry}"));
				//this.with(Console.tag().withText("------"));
				
				// add nodes using the new variable
				List<XNode> template = XmlUtil.deepCopyChildren(fchildren);
				
				this.withAll(template);
				
				cidx++;
			}
		}
		else {
			this.withText("Missing FB configuration");
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
    }
}
