/* ************************************************************************
#
#  designCraft.io
#
#  https://designcraft.io/
#
#  Copyright:
#    Copyright 2016 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */

dc.pui.Tags['dcm.Facebook'] = function(entry, node) {
	/*
	var alternate = $(node).attr('data-dcm-facebook-alternate');
	var count = $(node).attr('data-dcm-facebook-count');

	dc.comm.sendMessage({
		Service: "dcmSocialMedia",
		Feature: "Facebook",
		Op: "Feed",
		Body: {
			Alternate: alternate,
			Count: count
		}
	}, function (rmsg) {
		if (rmsg.Result != 0) {
			// TODO do something, probably not popup
			// dc.pui.Popup.alert(e.Message);
			return;
		}

		var fbfeed = rmsg.Body;

		// posts

		for (var i = 0; i < fbfeed.length; i++) {
			var item = fbfeed[i];

			var entry = $('<div class="dcm-fb-entry"></div>');
			var hdr = $('<div class="dcm-fb-header"></div>');

			entry.append(hdr);

			var icon = $('<div class="dcm-fb-icon"></div>');

			icon.append('<img src="https://graph.facebook.com/' + item.ById + '/picture?type=small" style="width: 50px;" />');

			hdr.append(icon);

			var stamp = $('<div class="dcm-fb-stamp"></div>');

			stamp.append('<h3>' + item.By + '</h3>');
			stamp.append('<i>' + dc.util.Date.zToMoment(item.Posted).format('MMM D\\, h:mm a') + '</i>');

			hdr.append(stamp);

			var body = $('<div class="dcm-fb-body"></div>');

			if (item.Message)
				body.append('<p>' + item.Message + '</p>');

			if (item.Picture)
				body.append('<img src="' + item.Picture + '" />');

			entry.append(body);

			var link = $('<div class="dcm-fb-link"></div>');

			link.append('<a href="http://www.facebook.com/permalink.php?id='
				+ item.ById + '&story_fbid=' + item.PostId
				+ '" target="_blank">View on Facebook - Share</a>');

			entry.append(link);

			$(node).append(entry);
		}

		if (entry)
			entry.addClass('dcm-fb-entry-last');

		$(node).append('<div style="clear: both;"></div>');
	});
	*/
};

dc.pui.Tags['dcm.Instagram'] = function(entry, node) {
	/*
	var alternate = $(node).attr('data-dcm-instagram-alternate');
	var count = $(node).attr('data-dcm-instagram-count');

	dc.comm.sendMessage({
		Service: "dcmSocialMedia",
		Feature: "Instagram",
		Op: "Feed",
		Body: {
			Alternate: alternate,
			Count: count
		}
	}, function (rmsg) {
		if (rmsg.Result != 0) {
			// TODO do something, probably not popup
			// dc.pui.Popup.alert(e.Message);
			return;
		}

		var igfeed = rmsg.Body;

		// posts

		for (var i = 0; i < igfeed.length; i++) {
			var item = igfeed[i];

			var entry = $('<div class="dcm-ig-entry"></div>');

			var link = $('<a class="dcm-fb-link" href="' + item.Link + '" target="_blank"></a>');

			link.append('<img src="' + item.Picture + '" />');

			entry.append(link);

			$(node).append(entry);
		}

		$(node).append('<div style="clear: both;"></div>');
	});
	*/
};

dc.pui.Tags['dcm.Twitter'] = function(entry, node) {
	/*
	var alternate = $(node).attr('data-dcm-twitter-alternate');
	var count = $(node).attr('data-dcm-twitter-count');

	dc.comm.sendMessage({
		Service: "dcmSocialMedia",
		Feature: "Twitter",
		Op: "Feed",
		Body: {
			Alternate: alternate,
			Count: count
		}
	}, function (rmsg) {
		if (rmsg.Result != 0) {
			// TODO do something, probably not popup
			// dc.pui.Popup.alert(e.Message);
			return;
		}

		var fbfeed = rmsg.Body;

		// posts

		for (var i = 0; i < fbfeed.length; i++) {
			var item = fbfeed[i];

			var entry = $('<div class="dcm-tw-entry"></div>');

			var icon = $('<div class="dcm-tw-icon"></div>');

			icon.append('<img src="https://twitter.com/' + item.ScreenName + '/profile_image?size=normal" />');

			entry.append(icon);

			var body = $('<div class="dcm-tw-body"></div>');

			var info = $('<div class="dcm-tw-info"></div>');

			info.append('<span class="dcm-tw-info-name">' + item.By + '</span>');
			info.append(' ');
			info.append('<span class="dcm-tw-info-user">@' + item.ScreenName + '</span>');
			info.append(' ');
			info.append('<span class="dcm-tw-info-at">' + dc.util.Date.zToMoment(item.Posted).format('MMM D \\a\\t h:mm a') + '</span>');

			body.append(info);

			if (item.Message)
				body.append('<div class="dcm-tw-text">' + item.Html + '</div>');

			entry.append(body);

			$(node).append(entry);
		}

		if (entry)
			entry.addClass('dcm-tw-entry-last');

		$(node).append('<div style="clear: both;"></div>');
	});
	*/
};

dc.pui.Tags['dcm.TwitterTimelineLoader'] = function(entry, node) {
	window.twttr = (function(d, s, id) {
	  var js, fjs = d.getElementsByTagName(s)[0],
	    t = window.twttr || {};
	  if (d.getElementById(id)) return t;
	  js = d.createElement(s);
	  js.id = id;
	  js.src = "https://platform.twitter.com/widgets.js";
	  fjs.parentNode.insertBefore(js, fjs);

	  t._e = [];
	  t.ready = function(f) {
	    t._e.push(f);
	  };

	  return t;
	}(document, "script", "twitter-wjs"));
};

dc.pui.Tags['dcm.CarouselWidget'] = function(entry, node) {
	var period = dc.util.Number.toNumberStrict($(node).attr('data-dcm-period'));

	if (! period)
		period = 3500;

	var gallery = $(node).attr('data-dcm-gallery');
	var show = $(node).attr('data-dcm-show');

	// TODO add this to a standard utility
	var imgLoadedFunc = function(img) { return img && img.complete && (img.naturalHeight !== 0); }
	var ssinit = false;
	var sscurr = -1;
	var switchblock = false;

	var imgPlacement = function(selector, idx) {
		$(node).find(selector).css({
		     marginLeft: '0'
		 });

		var fimg = dc.pui.TagCache['dcm.CarouselWidget'][gallery][show][idx];

		var idata = $(fimg).attr('data-dc-image-data');

		if (!idata)
			return;

		var ii = JSON.parse(idata);

		//$(node).find('.dcm-widget-carousel-caption').text(ii.Description ? ii.Description : '');

		var centerEnable = $(node).attr('data-dcm-centering');

		if (! centerEnable || (centerEnable.toLowerCase() != 'true'))
			return;

		//if (!ii.CenterHint)
		//	return;

		var ch = ii.CenterHint ? ii.CenterHint : (fimg.width / 2);
		var srcWidth = fimg.naturalWidth;
		var srcHeight = fimg.naturalHeight;
		var currWidth = $(node).width();
		var currHeight = $(node).height();

		// stretch whole image, no offset
		//if (currWidth > srcWidth)
		//	return;

		var zoom = currHeight / srcHeight;
		var availWidth = srcWidth * zoom;

		var xoff = (availWidth - currWidth) / 2;

		if (dc.util.Number.isNumber(ch))
			xoff -= ((srcWidth / 2) - ch) * zoom;

		if (currWidth > srcWidth) {
			var minOff = dc.util.Number.toNumberStrict($(node).attr('data-dcm-min-offset'));

			if (xoff < minOff)
				xoff = minOff;
		}
		else {
			if (xoff < 0)
				xoff = 0;
			if (xoff + currWidth > availWidth)
				xoff = availWidth - currWidth;
		}

		$(node).find(selector).css({
		     marginLeft: (0 - xoff) + 'px'
		 });
	};

	entry.registerResize(function(e) {
		imgPlacement('.dcm-widget-carousel-img', sscurr);
	});

	var nextImage = function() {
		var idx = sscurr + 1;

		if (idx >= dc.pui.TagCache['dcm.CarouselWidget'][gallery][show].length)
			idx = 0;

		if (sscurr == idx)
			return -1;

		var fimg = dc.pui.TagCache['dcm.CarouselWidget'][gallery][show][idx];

		if (! imgLoadedFunc(fimg))
			return -1;

		return idx;
	};

	var prevImage = function() {
		var idx = sscurr - 1;

		if (idx < 0)
			idx = dc.pui.TagCache['dcm.CarouselWidget'][gallery][show].length - 1;

		if (sscurr == idx)
			return -1;

		var fimg = dc.pui.TagCache['dcm.CarouselWidget'][gallery][show][idx];

		if (! imgLoadedFunc(fimg))
			return -1;

		return idx;
	};

	var switchImage = function(idx) {
		if (idx == -1)
			return;

		var fimg = dc.pui.TagCache['dcm.CarouselWidget'][gallery][show][idx];

		$(node).addClass('dcm-loaded');

		$(node).find('.dcm-widget-carousel-img').attr('src', $(fimg).attr('src'));

		sscurr = idx;

		imgPlacement('.dcm-widget-carousel-img', sscurr);

		if (dc.handler && dc.handler.tags && dc.handler.tags.CarouselWidget && dc.handler.tags.CarouselWidget.switched)
			dc.handler.tags.CarouselWidget.switched(entry, node, show, idx, fimg);
	};

	if (! dc.pui.TagCache['dcm.CarouselWidget'])
		dc.pui.TagCache['dcm.CarouselWidget'] = { };

	if (! dc.pui.TagCache['dcm.CarouselWidget'][gallery])
		dc.pui.TagCache['dcm.CarouselWidget'][gallery] = { };

	var icache = dc.pui.TagCache['dcm.CarouselWidget'][gallery][show];

	// if not cached, build it
	if (!icache) {
		icache = [];
		dc.pui.TagCache['dcm.CarouselWidget'][gallery][show] = icache;

		$(node).find('.dcm-widget-carousel-list img').each(function() {
			icache.push(this);
		});
	}

	if (dc.handler && dc.handler.tags && dc.handler.tags.CarouselWidget && dc.handler.tags.CarouselWidget.init)
		dc.handler.tags.CarouselWidget.init(entry, node, show, icache, {
			switchImage: function(idx) {
				animatefade(idx);
			},
			nextImage: function() {
				animatefade(nextImage());
			},
			prevImage: function() {
				animatefade(prevImage());
			}
		});

	if (icache.length == 0)
		return;

	switchImage(0);

	// make sure the "placement" code gets run
	entry.allocateTimeout({
		Title: 'Slide Show Controller',
		Period: 1000,
		Op: function() {
			switchImage(0);
		}
	});

	if (icache.length == 1)
		return;

	var animatefade = function(idx) {
		if (! dc.util.Number.isNumber(idx))
			idx = nextImage();

		if (idx == -1) {
			tryAnimate(1000);
			return;
		}

		var fimg = dc.pui.TagCache['dcm.CarouselWidget'][gallery][show][idx];

		$(node).find('.dcm-widget-carousel-fader')
			.css({ opacity: 0 })
			.attr('src', $(fimg).attr('src'));

		imgPlacement('.dcm-widget-carousel-fader', idx);

		var cd = new dc.lang.CountDownCallback(2, function() {
			switchImage(idx);

			$(node).find('.dcm-widget-carousel-fader')
				.css({ opacity: 0 });

			$(node).find('.dcm-widget-carousel-img')
				.css({ opacity: 1 })
				.attr('src', $(fimg).attr('src'));

      		tryAnimate(period);
		});

		$(node).find('.dcm-widget-carousel-img').velocity('stop').velocity({
			opacity: 0.01
		},
		{
			duration: 650,
			complete: function() {
	      		cd.dec();
			}
		});

		$(node).find('.dcm-widget-carousel-fader').velocity('stop').velocity({
	      	opacity: 1
	      },
	      {
	      	duration: 650,
	      	complete: function() {
	      		cd.dec();
	      	}
	      });

		if (dc.handler && dc.handler.tags && dc.handler.tags.CarouselWidget && dc.handler.tags.CarouselWidget.switching)
			dc.handler.tags.CarouselWidget.switching(entry, node, show, idx, fimg);
	};

	var toid = null;

	var tryAnimate = function(ms) {
		//console.log('toid: ' + toid);

		if (toid)
			window.clearTimeout(toid);

		toid = entry.allocateTimeout({
			Title: 'Slide Show Controller',
			Period: ms,
			Op: function() {
				if (switchblock)
					tryAnimate(1000);
				else
					animatefade();
			}
		});
	};

	tryAnimate(period);

	$(node).find('.dcm-widget-carousel-img').click(function(e) {
		if (dc.handler && dc.handler.tags && dc.handler.tags.CarouselWidget && dc.handler.tags.CarouselWidget.click) {
			var fimg = null;

			if (sscurr != -1)
				fimg = dc.pui.TagCache['dcm.CarouselWidget'][gallery][show][sscurr];

			dc.handler.tags.CarouselWidget.click(entry, node, show, sscurr, fimg);
		}

		e.preventDefault();
		return false;
	});

	$(node).on('mouseover', function(e) {
		switchblock = true;
	});

	$(node).on('mouseout', function(e) {
		switchblock = false;
	});
};

dc.pui.Tags['dc.SlidesSection'] = dc.pui.Tags['dcm.CarouselWidget'];

dc.pui.Tags['dcm.BannerWidget'] = function(entry, node) {
	var imgPlacement = function(selector) {
		$(node).find(selector).css({
		     marginLeft: '0'
		 });

 		var centerEnable = $(node).attr('data-dcm-centering');

 		if (! centerEnable || (centerEnable.toLowerCase() != 'true'))
 			return;

		var idata = $(node).attr('data-dc-image-data');

		if (! idata)
			return;

		var ii = JSON.parse(idata);

		var fimg = $(node).find('img');

		if (fimg.length == 0)
			return;

		fimg = fimg.get(0);

		var ch = (ii.Data && ii.Data.CenterHint) ? ii.Data.CenterHint : (fimg.naturalWidth / 2);
		var srcWidth = fimg.naturalWidth;
		var srcHeight = fimg.naturalHeight;
		var currWidth = $(node).width();
		var currHeight = $(node).height();

		// stretch whole image, no offset
		if (currWidth > srcWidth)
			return;

		var zoom = currHeight / srcHeight;
		var availWidth = srcWidth * zoom;

		var xoff = (availWidth - currWidth) / 2;

		if (dc.util.Number.isNumber(ch))
			xoff -= ((srcWidth / 2) - ch) * zoom;

		if (xoff < 0)
			xoff = 0;
		if (xoff + currWidth > availWidth)
			xoff = availWidth - currWidth;

		$(node).find(selector).css({
		     marginLeft: '-' + xoff + 'px'
		 });
	};

	entry.registerResize(function(e) {
		imgPlacement('.dcm-widget-banner-img');
	});

	// make sure the "placement" code gets run
	/*
	entry.allocateTimeout({
		Title: 'Banner Controller',
		Period: 1000,
		Op: function() {
			imgPlacement('.dcm-widget-banner-img');
		}
	});
	*/

	var img = new Image();

	img.onload = function () {
		imgPlacement('.dcm-widget-banner-img');
	};

	img.src = $(node).find('img').attr('src');
};

dc.pui.Tags['dcmi.CmsLink'] = function(entry, node) {
	$(node).click(function(e) {
		entry.LastFocus = $(node);

		var pel = $(this).closest('[data-cms-editable="true"]').get(0);

		if (! pel)
			return;

		var func = $(pel).attr('data-cms-func');

		entry.callTagFunc(pel, func ? func : 'doCmsEdit');

		e.preventDefault();
		return false;
	});
};

// ------------------- end Tags -------------------------------------------------------

dc.pui.Apps.Menus.dcmShop = {
	Tabs: [
		{
			Alias: 'Checkout',
			Title: 'Customer',
			Path: '/dcm/shop/checkout'
		},
		{
			Alias: 'Shipping',
			Title: 'Delivery',
			Path: '/dcm/shop/shipping'
		},
		{
			Alias: 'Totals',
			Title: 'Totals',
			Path: '/dcm/shop/totals'
		},
		{
			Alias: 'Billing',
			Title: 'Billing',
			Path: '/dcm/shop/billing'
		},
		{
			Alias: 'Confirm',
			Title: 'Confirm',
			Path: '/dcm/shop/confirm'
		}
	]
};
