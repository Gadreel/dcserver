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


dc.pui = {
};

dc.pui.layer = {
};


dc.pui.layer.Base = function(contentshell, content, options) {
	this.init(contentshell, content, options);
};

dc.pui.layer.Base.prototype = {
	init: function(contentshell, content, options) {
		$.extend(this, {
			Name: '[unknown]',
			Params: { },
			Current: null,
			Context: null,
			Store: null,
			History: [ ],
			Observers: { },
			ContentShell: contentshell,
			Content: content,
		}, options);

		// TODO add layer Timers	- try to work with Velocity instead of making our own timers
	},

	// TODO define use of observer - more than just "onload"
	setObserver: function(name, observer) {
		this.Observers[name] = observer;
	},

	deleteObserver: function(name, observer) {
		delete this.Observers[name];
	},

	setContentSelector: function(v) {
		this.Content = v;
	},

	// call with separate args or with a single "options" record
	loadPage: function(page, params, replaceState, callback, urlparams) {
		var options = {};

		if (dc.util.Struct.isRecord(page)) {
			options = page;
		}
		else {
			var hpage = page.split('#');

			options.Name = hpage[0];
			options.Params = params;
			options.UrlParams = urlparams;
			options.ReplaceState = replaceState;
			options.Callback = callback;

			if (hpage.length)
				options.TargetElement = hpage[1];
		}

		this.loadPageAdv(options);
	},
	loadPageAdv: function(options) {
		if (! options || ! options.Name)
			return;

		// layer history
		var oldentry = this.Current;

		if (oldentry) {
			oldentry.freeze();
			this.History.push(oldentry);
		}

		options.Layer = this;

		var entry = new dc.pui.PageEntry(options);
		var loader = dc.pui.Loader;

		loader.LoadPageId = entry.Id;

		// if page is already loaded then show it
		if (loader.Pages[options.Name] && ! loader.Pages[options.Name].NoCache && ! loader.StalePages[options.Name]) {
			loader.resumePageLoad();
			return;
		}

		delete loader.StalePages[options.Name];		// no longer stale

		var ssrc = options.Name + '?_dcui=dyn';

		if (options.UrlParams)
			ssrc += '&' + options.UrlParams;

		var script = document.createElement('script');
		script.src = ssrc + '&nocache=' + dc.util.Crypto.makeSimpleKey();

		script.id = 'page' + options.Name.replace(/\//g,'.');
		script.async = false;

		document.head.appendChild(script);
	},

	manifestPage: function(entry, frompop) {
		var layer = this;
		var loader = dc.pui.Loader;

		if (!entry)
			return;

		if (layer.Current)
			layer.Current.onDestroy({ Clear: false });

		layer.Current = entry;

		this.open();

		var page = loader.Pages[entry.Name];

		$(layer.Content).empty().append(page.Layout).promise().then(function() {
			var lclass = page.PageClass + ' dcuiContentLayer';

			if (layer.History.length)
				lclass += ' dcuiHistory';

			if (layer.Content == layer.ContentShell)
				lclass += ' dcuiLayerPane';

			$(layer.Content).attr('class', lclass);

			if (entry.Loaded && entry.FreezeTop)
				$(layer.ContentShell).scrollTop(entry.FreezeTop);
			else if (entry.TargetElement)
				$(layer.ContentShell).scrollTop($('#' + entry.TargetElement).get(0).getBoundingClientRect().top + $(layer.ContentShell).scrollTop());
			else
				$(layer.ContentShell).scrollTop(0);

			layer.enhancePage(false);

			if (! frompop) {
				// use window.location.pathname so that a Refresh loads the current page, not the dialog/app
				if (entry.ReplaceState)
					history.replaceState(
						{ Id: loader.LoadPageId, Params: entry.Params },
						page.Meta.Title,
						window.location.pathname);
				else
					history.pushState(
						{ Id: loader.LoadPageId, Params: entry.Params },
						page.Meta.Title,
						window.location.pathname);
			}

			Object.getOwnPropertyNames(layer.Observers).forEach(function(name) {
				layer.Observers[name]();
			});
		});
	},

	enhancePage: function(firstload) {
		var layer = this;
		var loader = dc.pui.Loader;

		if (firstload) {
			var page = loader.Pages[layer.Current.Name];

			var lclass = page.PageClass + ' dcuiContentLayer';

			$(layer.Content).attr('class', lclass);
		}

		// simple place to load code overrides
		layer.Current.callPageFunc('onInit');

		$(layer.Content + ' *[data-dc-enhance="true"]').each(function() {
			var tag = $(this).attr('data-dc-tag');

			if (!tag || !dc.pui.Tags[tag])
				return;

			dc.pui.Tags[tag](layer.Current, this);
		});

		layer.Current.onLoad(function() {
			if (layer.Current.Callback)
				layer.Current.Callback.call(layer.Current);
		});
	},

	refreshPage: function() {
		var options = this.Current;

		options.Loaded = false;

		delete dc.pui.Loader.StalePages[options.Name];		// no longer stale

		dc.pui.Loader.LoadPageId = options.Id;

		var script = document.createElement('script');
		script.src = options.Name + '?_dcui=dyn&nocache=' + dc.util.Crypto.makeSimpleKey();
		script.id = 'page' + options.Name.replace(/\//g,'.');
		script.async = false;

		document.head.appendChild(script);
	},

	query: function(selector) {
		if (selector)
			return $(this.Content).find(selector);

		return $(this.Content);
	},

	closePage: function(opts) {
		if (opts)
			this.loadPage(opts.Path, opts.Params);
		else
			this.back();
	},

	getHistory: function() {
		return this.History;
	},

	clearHistory: function() {
		var layer = this;

		if (this != dc.pui.Loader.MainLayer) {
			if (layer.Current)
				layer.Current.onDestroy({ Clear: true });

			layer.Current = null;

			$(layer.Content).empty();

			var entry = this.History.pop();

			while (entry) {
				entry.onDestroy({ Clear: true });
				entry = this.History.pop();
			}
		}
	},

	back: function() {
		var layer = this;

		if (layer.Current)
			layer.Current.onDestroy({ Clear: true });

		layer.Current = null;

		$(layer.Content).empty();

		var entry = this.History.pop();

		if (entry) {
			this.manifestPage(entry);
		}
		else {
			this.Current = null;
			this.close();
		}
	},

	open: function() {
		if (this != dc.pui.Loader.MainLayer) {
			var need = true;

			for (var i = 0; i < dc.pui.Loader.Layers.length; i++) {
				if (dc.pui.Loader.Layers[i] == this) {
					need = false;
				}
				else {
					$(dc.pui.Loader.Layers[i].ContentShell).css('visibility', 'hidden');
				}
			}

			if (need)
				dc.pui.Loader.addLayer(this);

			$(this.ContentShell).show().css('visibility', 'visible');
			$(dc.pui.Loader.MainLayer.Content).css('visibility', 'hidden');
			$('body').addClass('dc-hide-scroll');
		}
		else {
			$(this.Content).css('visibility', 'visible');
			$('body').removeClass('dc-hide-scroll');
		}

		//if (this.LastFocus)
		//	this.LastFocus.focus();
	},

	close: function() {
		this.clearHistory();

		/* TODO some sort of event
		if (!dmode)
			dc.pui.Loader.currentPageEntry().callPageFunc('dcwDialogEvent', { Name: 'Close', Target: 'DialogPane' });
		*/

		if (this != dc.pui.Loader.MainLayer) {
			$(this.ContentShell).hide().css('visibility', 'hidden');

			dc.pui.Loader.exposeTopLayer();
		}
		else {
			dc.pui.Loader.MainLayer.Current.callPageFunc('onClose');
		}
	},

	current: function() {
		return this.Current;
	},

	scrollPage: function(selector) {
		var entry = this.Current;

		if (entry)
			entry.scrollPage(selector);
	},

	onResize: function(e) {
		var entry = this.Current;

		if (entry)
			entry.onResize(e);
	},

	onDestroy: function(e) {
		var entry = this.Current;		// TODO shouldn't this operate on all history?

		if (entry)
			entry.onDestroy(e);

		$(this.ContentShell).remove();
	},

	onFrame: function(e) {
		var entry = this.Current;

		if (entry)
			entry.onFrame(e);
	}
};


dc.pui.layer.Main = function() {
	this.init('body', '#dcuiMain');
};

dc.pui.layer.Main.prototype = new dc.pui.layer.Base();

dc.pui.layer.Main.prototype.init = function(contentshell, content, options) {
	dc.pui.layer.Base.prototype.init.call(this, contentshell, content, options);

	this.FirstLoad = true;
}

dc.pui.layer.Main.prototype.back = function() {
	window.history.back();
};

dc.pui.layer.Main.prototype.loadPageAdv = function(options) {
	if (! options || ! options.Name)
		return;

	var loader = dc.pui.Loader;

	// clear Layers so that destroy does not get into infinite loop
	var dlayers = loader.Layers;
	loader.Layers = [ ];

	// signal all layers
	for (var i = 0; i < dlayers.length; i++)
		dlayers[i].onDestroy({ Clear: true });

	/* TODO add support for
		options.Params = params;
		options.UrlParams = urlparams;
	*/

	if (options.ReplaceState)
		window.location.replace(options.Name + (options.TargetElement ? '#' + options.TargetElement : ''));
	else
		window.location.assign(options.Name + (options.TargetElement ? '#' + options.TargetElement : ''));
};

dc.pui.layer.Main.prototype.initPage = function() {
	var entry = new dc.pui.PageEntry({
		Name: window.location.pathname,
		Href: window.location.href,
		Layer: this
	});

	dc.pui.Loader.LoadPageId = entry.Id;
	dc.pui.Loader.resumePageLoad();
},

dc.pui.layer.Main.prototype.manifestPage = function(entry, frompop) {
	var layer = this;
	var loader = dc.pui.Loader;

	if (!entry)
		return;

	var page = loader.Pages[entry.Name];

	if (page.NoCache) {
		jQuery(window).bind("unload", function() {
			// forces the page to reload from server after clicking back
			console.log('unloaded');
		});
	}

	layer.FirstLoad = false;
	layer.Current = entry;
	layer.enhancePage(true);
};

dc.pui.layer.Main.prototype.enhancePage = function(firstload) {
	dc.pui.layer.Base.prototype.enhancePage.call(this, firstload);

	var layer = this;
	var loader = dc.pui.Loader;
	var entry = layer.Current;
	var page = loader.Pages[entry.Name];
};

dc.pui.layer.Main.prototype.refreshPage = function() {
	var layer = this;
	var entry = layer.Current;

	window.location.reload(true);
	//window.location.href = entry.Href;
};


dc.pui.layer.Dialog = function() {
	this.init('#dcuiDialog', '#dcuiDialogPane');
};

dc.pui.layer.Dialog.prototype = new dc.pui.layer.Base();

dc.pui.layer.Dialog.prototype.open = function() {
	var dialog = this;
	var del = $('#dcuiDialog');

	if (! del.length) {
		$('body').append('<div id="dcuiDialog" class="dcuiLayerShell"><div id="dcuiDialogPane"></div></div>');
	}

	dc.pui.layer.Base.prototype.open.call(this);
};

// Dialog feature (singleton)
dc.pui.Dialog = new dc.pui.layer.Dialog();


dc.pui.layer.Alert = function() {
	this.init('#dcuiAlert', '#dcuiAlertPane');
};

dc.pui.layer.Alert.prototype = new dc.pui.layer.Base();

dc.pui.layer.Alert.prototype.open = function() {
	var dialog = this;
	var del = $('#dcuiAlert');

	if (! del.length) {
		var dbox = $('<div id="dcuiAlert" class="dcuiLayerShell"></div>');

		$(dbox).click(function (e) {
			dialog.close();

			e.preventDefault();
			return false;
		});

		var dbpane = $('<div id="dcuiAlertPane"></div>');

		$(dbpane).click(function (e) {
			// do nothing if interior clicked

			//e.preventDefault();
			e.stopPropagation();
			//e.stopImmediatePropagation();
		});

		dbox.append(dbpane);

		$('body').append(dbox);
	}

	dc.pui.layer.Base.prototype.open.call(this);
};

// Alert feature (singleton)
dc.pui.Alert = new dc.pui.layer.Alert();


dc.pui.layer.App = function() {
	this.init('#dcuiApp', '#dcuiAppPane');
};

dc.pui.layer.App.prototype = new dc.pui.layer.Base();

dc.pui.layer.App.prototype.init = function(contentshell, content, options) {
	dc.pui.layer.Base.prototype.init.call(this, contentshell, content, options);

	this.TabHistory = { };
	this.TabFocus = null;
}

dc.pui.layer.App.prototype.open = function() {
	var dialog = this;
	var del = $('#dcuiApp');

	if (! del.length) {
		var dbox = $('<div>')
			.attr('id', 'dcuiApp')
			.attr('class', 'dcuiLayerShell dcuiMenu')
			.dcappend(
				$('<div>')
					.attr('id', 'dcuiAppMenu')
					.dcappend(
						$('<section>')
						 	.attr('class', 'dc-panel dc-panel-primary dc-panel-page')
							//.attr('role', 'region')
							//.attr('aria-labelledby', 'dcuiAppMenuHeading')
							//.attr('tabindex', '-1')
							.dcappend(
								$('<div>')
								 	.attr('class', 'dc-panel-heading')
									.dcappend(
										$('<h1>')
											.attr('id', 'dcuiAppMenuHeading')
											.text('Tabs / Options')
									),
								$('<nav>')
								 	.attr('id', 'dcuiAppMenuBody')
									.attr('class', 'dc-panel-body')
									.attr('aria-label', 'Primary')
							)
					),
		 		$('<div>')
				 	.attr('id', 'dcuiAppPane')
					.attr('class', 'dcuiContentLayer')
			);

		$('body').append(dbox);

		/* TODO ??
		pane.setObserver('CMSPane', function() {
			dc.cms.ui.Loader.loadMenu();
		});
		*/
	}

	dc.pui.layer.Base.prototype.open.call(this);
};

dc.pui.layer.App.prototype.loadTab = function(tabalias, params) {
	var tinfo = this.getTabInfo(tabalias);

	if (! tinfo)
		return;

	if (this.Current) {
		this.Current.freeze();
		this.History.push(this.Current);
		this.Current = null;
	}

	this.TabFocus = tabalias;

	if (this.TabHistory[tabalias]) {
		this.History = this.TabHistory[tabalias];
		this.back();
	}
	else {
		this.TabHistory[tabalias] = [];
		this.History = this.TabHistory[tabalias];

		if (tinfo.Op)
			tinfo.Op.call(tinfo);
		else
			this.loadPage(tinfo.Path, params);
	}
};

dc.pui.layer.App.prototype.start = function(context) {
	var app = this;
	app.Context = context;

	this.clearHistory();

	if (context.Tab)
		app.loadTab(context.Tab, context.Params);
	else
		app.loadPage(context.Page, context.Params);
};

dc.pui.layer.App.prototype.startTab = function(context) {
	var app = this;
	app.Context = context;

	this.clearHistory();

	app.loadTab(context.Tab, context.Params);
};

dc.pui.layer.App.prototype.manifestPage = function(entry, frompop) {
	var app = this;

	dc.pui.layer.Base.prototype.manifestPage.call(this, entry);

	app.loadMenu();
};

dc.pui.layer.App.prototype.getTabInfo = function(tabalias) {
	var app = this;
	var area = this.getMenuArea();

	var amenu = dc.pui.Apps.Menus[area];

	if (amenu && amenu.Tabs) {
		for (var i = 0; i < amenu.Tabs.length; i++) {
			if (amenu.Tabs[i].Alias == tabalias)
				return amenu.Tabs[i];
		}
	}

	return null;
};

dc.pui.layer.App.prototype.getMenuArea = function() {
	var app = this;

	var area = null;

	// get area from current pane, if any
	if (app.Current && app.Current.Params._Menu)
		area = app.Current.Params._Menu;

	if (!area && app.Context && app.Context.Menu)
		area = app.Context.Menu;

	if (!area)
		area = 'dcSignIn';

	return area;
};

dc.pui.layer.App.prototype.loadMenu = function() {
	var app = this;

	var area = this.getMenuArea();

	var $opts = $('<ul>');

	$('#dcuiAppMenuBody').empty().dcappend($opts);

	// if any page is loaded, use back
	//var hist = app.getHistory();

	var addbTab = function(mnu) {
		if (mnu.Auth && ! dc.user.isAuthorized(mnu.Auth))
			return;

		$opts.dcappend(
			$('<li>')
				.dcappend(
					$('<a>')
					 	.attr('href', '#')
						.attr('class', 'pure-button')
						.addClass(mnu.Kind)
						.addClass(mnu.Alias == app.TabFocus ? 'pure-button-selected' : '')
						.text(mnu.Title)
						.click(mnu, function(e) {
							if (! dc.pui.Apps.busyCheck())
								app.loadTab(e.data.Alias);

							// TODO support LastFocus

							e.preventDefault();
							return false;
						})
				)
		);
	};

	var addbMenu = function(mnu) {
		if (mnu.Auth && ! dc.user.isAuthorized(mnu.Auth))
			return;

		$opts.dcappend(
			$('<li>')
				.dcappend(
					$('<a>')
					 	.attr('href', '#')
						.attr('class', 'pure-button')
						.addClass(mnu.Kind)
						.text(mnu.Title)
						.click(mnu, function(e) {
							if (! dc.pui.Apps.busyCheck())
								e.data.Op.call(app, e);

							// TODO support LastFocus

							e.preventDefault();
							return false;
						})
				)
		);
	};

	var amenu = dc.pui.Apps.Menus[area];

	if (amenu && amenu.Tabs) {
		for (var i = 0; i < amenu.Tabs.length; i++)
			addbTab(amenu.Tabs[i]);
	}

	if (amenu && amenu.Tabs && amenu.Options)
		$opts.dcappend(
			$('<li>')
				.attr('aria-label', 'Dialog options')
				.dcappend(
					$('<hr>')
					 	.attr('aria-hidden', 'true')
				)
		);

	if (amenu && amenu.Options) {
		for (var i = 0; i < amenu.Options.length; i++)
			addbMenu(amenu.Options[i]);
	}
};

dc.pui.layer.App.prototype.clearHistory = function() {
	this.TabHistory = { };
	this.TabFocus = null;

	dc.pui.layer.Base.prototype.clearHistory.call(this);
};

// App feature (singleton)
dc.pui.App = new dc.pui.layer.App();


dc.pui.layer.SimpleApp = function() {
	this.init('#dcuiSimpleApp', '#dcuiSimpleAppPane');
};

dc.pui.layer.SimpleApp.prototype = new dc.pui.layer.Base();

dc.pui.layer.SimpleApp.prototype.open = function() {
	var dialog = this;
	var del = $('#dcuiSimpleApp');

	if (! del.length) {
		$('body').append('<div id="dcuiSimpleApp" class="dcuiLayerShell"><div id="dcuiSimpleAppPane"></div></div>');
	}

	dc.pui.layer.Base.prototype.open.call(this);
};

// Dialog feature (singleton)
dc.pui.SimpleApp = new dc.pui.layer.SimpleApp();


dc.pui.layer.FullScreen = function() {
	this.init('#dcuiFullScreen', '#dcuiFullScreen');
};

dc.pui.layer.FullScreen.prototype = new dc.pui.layer.Base();

dc.pui.layer.FullScreen.prototype.open = function() {
	var dialog = this;
	var del = $('#dcuiFullScreen');

	if (! del.length) {
		var dbox = $('<div id="dcuiFullScreen" class="dcuiLayerShell dcuiContentLayer"></div>');

		$('body').append(dbox);
	}

	dc.pui.layer.Base.prototype.open.call(this);
};

// Dialog feature (singleton)
dc.pui.FullScreen = new dc.pui.layer.FullScreen();


// ------------------- end Layer -------------------------------------------------------


dc.pui.Loader = {
	LoadPageId: null,
	Ids: { },
	Pages: { },
	StalePages: { },
	Libs: { },		// TODO fill this with all "Global" scripts on first load
	Styles: { },		// TODO fill this with all "Global" styles on first load
	RequireCallback: null,
	FrameRequest: false,
	MainLayer: null,
	Layers: [],
	TextSize: 'default',

	init: function() {
		var loader = this;

		// stop with Googlebot.  Googlebot may load page and run script, but no further than this so indexing is correct (index this page)
		if (navigator.userAgent.indexOf('Googlebot') > -1)
			return;

		var dmode = dc.util.Web.getQueryParam('_dcui');

		if ((dmode == 'html') || (dmode == 'print'))
			return;

		loader.MainLayer = new dc.pui.layer.Main();

		$(window).on('popstate', function(e) {
			if (e.originalEvent.state != null) {
				dc.pui.Loader.currentLayer().back();
			}
		});

		// watch for orientation change or resize events
		$(window).on('orientationchange resize', function (e) {
			loader.MainLayer.onResize(e);

			for (var i = 0; i < loader.Layers.length; i++)
				loader.Layers[i].onResize(e);

			loader.requestFrame();
		});

		if (document.fonts) {
			document.fonts.onloadingdone = function(e) {
				// force all canvas updates that may be using loaded forms
				loader.requestFrame();
			}
		}

		document.addEventListener('keydown', function(event) {
		    if (event.key === 'Escape' || event.keyCode === 27) {
				var layer = loader.currentLayer();

				if (layer)
					layer.close();
		    }

			// TODO support layer key events
		});

		if (dc.handler && dc.handler.settings && dc.handler.settings.ga)
			loadGA();

		if (dc.handler && dc.handler.settings && dc.handler.settings.captcha)
			loadCaptcha();

		dc.comm.init(function() {
			if (dc.handler && dc.handler.init)
				dc.handler.init();

			if (dc.util.Web.isTouchDevice())
				$('html > head').append('<style>#dcuiDialogPane { margin-top: 36px; }</style>');

			if (dc.user.isVerified()) {
				dc.pui.Loader.MainLayer.initPage();
				return;
			}

			var creds = dc.user.loadRemembered();

			if (! creds) {
				dc.pui.Loader.MainLayer.initPage();
 				return;
			}

			dc.user.signin(creds.Username, creds.Password, true, function(msg) {
				if (dc.user.isVerified()) {
					window.location.reload(true);
				}
				else {
					dc.pui.Loader.MainLayer.initPage();
				}
			});
		});
	},
	signout: function() {
		dc.user.signout(function() {
			window.location = '/';
		});
	},
	loadPage: function(page, params, replaceState, callback) {
		this.MainLayer.loadPage(page, params, replaceState, callback);
	},
	addPageDefinition: function(name, def) {
		var loader = this;

		loader.Pages[name] = def;
	},
	resumePageLoad: function(pagename) {
		var loader = this;

		var entry = loader.Ids[loader.LoadPageId];

		if (!entry)
			return;

		if (pagename)
			entry.Name = pagename;

		var page = loader.Pages[entry.Name];

		dc.schema.Manager.load(page.RequireType);

		dc.lang.Dict.load(page.RequireTr);

		var needWait = false;

		if (! entry.Layer.FirstLoad && page.RequireStyles) {
			for (var i = 0; i < page.RequireStyles.length; i++) {
				var path = page.RequireStyles[i];

				if (loader.Styles[path])
					continue;

				//$('head').append('<link rel="stylesheet" type="text/css" href="' + path + '?nocache=' + dc.util.Crypto.makeSimpleKey() + '" />');
				$('head').append('<link rel="stylesheet" type="text/css" href="' + path + '" />');

				loader.Styles[path] = true;		// not really yet, but as good as we can reasonably get
				needWait = true;
			}
		}

		if (page.RequireLibs) {
			for (var i = 0; i < page.RequireLibs.length; i++) {
				var path = page.RequireLibs[i];

				if (loader.Libs[path])
					continue;

				var script = document.createElement('script');
				script.src = path;  // + '?nocache=' + dc.util.Crypto.makeSimpleKey();
				script.id = 'req' + path.replace(/\//g,'.');
				script.async = false;  	// needed when loading additional libraries, we can inject a final fake script that echos
										// a param (e.g. ?opid=3345) to us saying that it is loaded and hence all preceding scripts are also loaded

				document.head.appendChild(script);

				loader.Libs[path] = true;		// not really yet, but as good as we can reasonably get
				needWait = true;
			}
		}

		if (needWait) {
			loader.RequireCallback = function() {
				entry.Layer.manifestPage(entry);
			};

			var key = dc.util.Crypto.makeSimpleKey();

			var script = document.createElement('script');
			script.src = '/js/dc.require.js?nocache=' + key;
			script.id = 'lib' + key;
			script.async = false;  	// needed when loading additional libraries, we can inject a final fake script that echos
									// a param (e.g. ?opid=3345) to us saying that it is loaded and hence all preceding scripts are also loaded

			document.head.appendChild(script);

			return;
		}

		entry.Layer.manifestPage(entry);
	},
	finializePageLoad: function() {
		var loader = this;

		var entry = loader.Ids[loader.LoadPageId];

		if (entry)
			entry.Layer.manifestPage(entry);
	},
	clearPageCache: function(page) {
		var loader = this;

		if (page)
			loader.StalePages[page] = true;
	},
	failedPageLoad: function(reason) {
		var loader = this;

		dc.pui.Popup.alert('Failed to load page, you may not have permissions for it.');
	},
	requireCallback: function() {
		var loader = this;

		if (loader.RequireCallback)
			loader.RequireCallback();
	},
	addExtraLibs: function(scripts, cb) {
		var loader = this;

		var needWait = false;

		loader.RequireCallback = cb;

		for (var i = 0; i < scripts.length; i++) {
			var path = scripts[i];

			if (loader.Libs[path])
				continue;

			var script = document.createElement('script');
			script.src = path;  // + '?nocache=' + dc.util.Crypto.makeSimpleKey();
			script.id = 'req' + path.replace(/\//g,'.');
			script.async = false;  	// needed when loading additional libraries, we can inject a final fake script that echos
									// a param (e.g. ?opid=3345) to us saying that it is loaded and hence all preceding scripts are also loaded

			document.head.appendChild(script);

			loader.Libs[path] = true;		// not really yet, but as good as we can reasonably get
			needWait = true;
		}

		if (needWait) {
			var key = dc.util.Crypto.makeSimpleKey();

			var script = document.createElement('script');
			script.src = '/js/dc.require.js?nocache=' + key;
			script.id = 'lib' + key;
			script.async = false;  	// needed when loading additional libraries, we can inject a final fake script that echos
									// a param (e.g. ?opid=3345) to us saying that it is loaded and hence all preceding scripts are also loaded

			document.head.appendChild(script);
		}
		else {
			if (loader.RequireCallback)
				loader.RequireCallback();
		}
	},
	addExtraStyles: function(styles, cb) {
		var loader = this;

		var needWait = false;

		loader.RequireCallback = cb;

		for (var i = 0; i < styles.length; i++) {
			var path = styles[i];

			if (loader.Styles[path])
				continue;

			//$('head').append('<link rel="stylesheet" type="text/css" href="' + path + '?nocache=' + dc.util.Crypto.makeSimpleKey() + '" />');
			$('head').append('<link rel="stylesheet" type="text/css" href="' + path + '" />');

			loader.Styles[path] = true;		// not really yet, but as good as we can reasonably get
			needWait = true;
		}

		if (needWait) {
			var key = dc.util.Crypto.makeSimpleKey();

			var script = document.createElement('script');
			script.src = '/js/dc.require.js?nocache=' + key;
			script.id = 'lib' + key;
			script.async = false;  	// needed when loading additional libraries, we can inject a final fake script that echos
									// a param (e.g. ?opid=3345) to us saying that it is loaded and hence all preceding scripts are also loaded

			document.head.appendChild(script);
		}
		else {
			if (loader.RequireCallback)
				loader.RequireCallback();
		}
	},
	requestFrame: function() {
		var loader = this;

		if (!loader.FrameRequest) {
			window.requestAnimationFrame(loader.buildFrame);
			loader.FrameRequest = true;
		}
	},
	buildFrame: function(e) {
		var loader = dc.pui.Loader;		// this doesn't work due to how requestFrame above calls it

		loader.FrameRequest = false;

		var entry = loader.currentLayer().Current;

		if (entry)
			entry.onFrame(e);

		if (!loader.Layers)
			return;

		for (var i = 0; i < loader.Layers.length; i++)
			loader.Layers[i].onFrame(e);
	},
	getLayer: function(name) {
		var loader = this;

		for (var i = 0; i < loader.Layers.length; i++) {
			var l = loader.Layers[i];

			if (l.Name == name)
				return l;
		}

		return null;
	},
	addLayer: function(layer) {
		var loader = this;

		loader.Layers.push(layer);
	},
	currentLayer: function() {
		var loader = this;

		var maxz = -1;
		var maxidx = -1;

		for (var i = 0; i < loader.Layers.length; i++) {
			var l = loader.Layers[i];

			// skip if hidden or empty
			if (! l.Current)
				continue;

			var z = $(l.ContentShell).css('z-index');

			if (z == 'auto')
				z = 0;

			if (z > maxz) {
				maxz = z;
				maxidx = i;
			}
		}

		return (maxidx > -1) ? loader.Layers[maxidx] : dc.pui.Loader.MainLayer;
	},
	exposeTopLayer: function() {
		var layer = this.currentLayer();

		layer.open();

		if (layer.Current.LastFocus) {
			var cs = 'html';

			var y = $(cs).scrollTop();

			if (y == 0) {
				cs = 'body';		// one of those should work cross browser
				y = $(cs).scrollTop();
			}

			layer.Current.LastFocus.focus();
			$(cs).scrollTop(y);
		}
	}
};

dc.pui.Apps = {
	Busy: false,				// dcui is busy and not accepting new clicks right now - especially for submits

	sessionChanged: function() {
		dc.user.updateUser(function() {
			// TODO maybe change page if not auth tags? or if became guest
		});

		if (dc.handler && dc.handler.sessionChanged)
			dc.handler.sessionChanged();
	},
	busyCheck: function() {
		if (this.Busy) {		// protect against user submit such as Enter in a TextField
			console.log('click denied, dcui is busy');			// TODO if we have been busy for more than 2 seconds show a message screen...obviously someone is trying to click while nothing appears to be happening, hide the screen after load is done - unless someone else updated it
			return true;
		}

		return false;
	},
	activateCms: function(options, cb) {
		if (! dc.user.isAuthorized(['Editor', 'Admin', 'Clerk']))
			return;

		dc.pui.Apps.loadCms(function() {
			dc.cms.Loader.init(options);

			if (cb)
				cb();
		});
	},
	loadCms: function(cb) {
		dc.pui.Loader.addExtraStyles([ '/css/dcm.cms.css' ], function() {
			dc.pui.Loader.addExtraLibs([ '/js/dcm.cms.js' ], function() {
				if (cb)
					cb();
			});
		});
	},
	Menus: {
		dcSignIn: {
			Options: [
				{
					Title: 'Welcome',
					Kind: 'Option',
					Op: function(e) {
						dc.pui.App.loadPage('/dcw/welcome');
					}
				},
				{
					Title: 'Sign In',
					Kind: 'Option',
					Op: function(e) {
						dc.pui.App.loadPage('/dcw/user/sign-in');
					}
				},
				{
					Title: 'Password Reset',
					Kind: 'Option',
					Op: function(e) {
						dc.pui.App.loadPage('/dcw/user/reset');
					}
				},
				{
					Title: 'My Account',
					Auth: [ 'User' ],
					Op: function(e) {
						dc.pui.Dialog.loadPage('/dcw/user/edit-self');
					}
				},
				{
					Title: 'Sign Out',
					Auth: [ 'User' ],
					Kind: 'Option',
					Op: function(e) {
						dc.pui.Loader.signout();
					}
				}
			]
		}
	}
};

dc.pui.Popup = {
	alert: function(msg, callback, title) {
		dc.pui.Alert.loadPage('/dcw/alert', { Message: msg, Callback: callback, Title: title })
	},

	await: function(msg, callback, title, task) {
		dc.pui.Alert.loadPage('/dcw/await', { Message: msg, Callback: callback, Title: title, Task: task })
	},

	confirm: function(msg, callback, title) {
		dc.pui.Alert.loadPage('/dcw/confirm', { Message: msg, Callback: callback, Title: title })
	},

	input: function(msg, callback, title, label, value) {
		dc.pui.Alert.loadPage('/dcw/input', { Message: msg, Callback: callback, Title: title, Label: label, Value: value })
	},

	menu: function(menu) {
		if (dc.util.String.isString(menu))
			menu = dc.pui.Apps.Menus[menu];

		dc.pui.Alert.loadPage('/dcw/menu', { Menu: menu })
	}
};

// ------------------- end Loader/Popup -------------------------------------------------------

dc.pui.PageEntry = function(options) {
	$.extend(this, {
			Name: '/na',
			Params: { },
			TargetElement: null,
			Callback: null,
			Layer: null,		// layer entry belongs to, null for default
			Store: { },
			Forms: { },
			Timers: [],
			_onResize: [],
			_onFrame: [],
			_onDestroy: []
		}, options);

	var id = this.Name + '@' + dc.util.Uuid.create().replace(/-/g,'');

	this.Id = id;

	dc.pui.Loader.Ids[id] = this;
}

dc.pui.PageEntry.prototype = {
	getDefinition: function() {
		return dc.pui.Loader.Pages[this.Name];
	},
	onLoad: function(callback) {
		var page = dc.pui.Loader.Pages[this.Name];
		var entry = this;

		var steps = [ ];

		for (var i = 0; i < page.LoadFunctions.length; i++) {
			steps.push({
				Alias: 'LoadPageFunc' + i,
				Title: 'Load page function: ' + i,
				Params: {
					Idx: i
				},
				Func: function(step) {
					var event = { Wait: false, Task: this };

					var func = page.LoadFunctions[step.Params.Idx];

					if (dc.util.String.isString(func))
						entry.callPageFunc(func, event);
					else
						func.call(entry, event);

					if (! event.Wait)
						this.resume();
				}
			});
		}

		steps.push({
			Alias: 'MainLoadPage',
			Title: 'Main load page function',
			Func: function(step) {
				var event = {
					Wait: false,
					Task: this,
					Thaw: entry.Loaded,
					Focus: entry.LastFocus
				};

				entry.callPageFunc('Load', event);

				entry.LastFocus = event.Focus;

				if (! event.Wait)
					this.resume();
			}
		});

		steps.push({
			Alias: 'MainFocusPage',
			Title: 'Main page focus',
			Func: function(step) {
				var cs = $(entry.Layer.ContentShell).get(0);
				var x = cs.scrollLeft, y = cs.scrollTop;

				if (entry.LastFocus) {
					try {
						entry.LastFocus.focus();
					}
					catch (x) {
					}
				}
				else if (entry.Layer.Content != '#dcuiMain') {
					entry.query('main').focus();
				}

				$(cs).scrollTop(y);

				this.resume();
			}
		});

		// now load forms, if any
		Object.getOwnPropertyNames(entry.Forms).forEach(function(name) {
			steps.push({
				Alias: 'LoadFormFunc' + name,
				Title: 'Load form function: ' + name,
				Params: {
					Name: name
				},
				Func: function(step) {
					var task = this;

					entry.Forms[step.Params.Name][entry.Loaded ? 'thaw' : 'load'](function() {
						task.resume();
					});
				}
			});
		});

		var loadtask = new dc.lang.Task(steps, function(res) {
			//console.log('observer: ' + res.Code);

			entry.Loaded = true;
			entry.Frozen = false;

			if (callback)
				callback.call(entry);
		});

		loadtask.run();
	},

	reload: function() {
		this.Loaded = false;
		this.onLoad();
	},

	hasPageFunc: function(method) {
		var page = dc.pui.Loader.Pages[this.Name];
		return (page && page.Functions[method]);
	},

	callPageFunc: function(method) {
		var page = dc.pui.Loader.Pages[this.Name];

		if (page && page.Functions[method])
			return page.Functions[method].apply(this, Array.prototype.slice.call(arguments, 1));

		return null;
	},

	callTagFunc: function(selector, method) {
		var entry = this;
		var ret = null;
		var args = Array.prototype.slice.call(arguments, 2);

		args.unshift(null);
		args.unshift(entry);

		$(selector).each(function() {
			args[1] = this;
			var tag = $(this).attr('data-dc-tag');

			if (tag && dc.pui.TagFuncs[tag] && dc.pui.TagFuncs[tag][method])
				ret = dc.pui.TagFuncs[tag][method].apply(this, args);
		});

		return ret;
	},

	query: function(selector) {
		return this.Layer.query(selector);
	},

	form: function(name) {
		if (name)
			return this.Forms[name];

		// if no name then return the first we find
		var fnames = Object.getOwnPropertyNames(this.Forms);

		if (fnames)
			return this.Forms[fnames[0]];

		return null;
	},

	formQuery: function(name) {
		var frm = this.form(name);

		if (frm)
			return $('#' + frm.Id);

		return $('#__unreal');
	},

	freeze: function() {
		var entry = this;
		var page = dc.pui.Loader.Pages[entry.Name];

		entry.FreezeTop = $(entry.Layer.ContentShell).scrollTop();

		entry.callPageFunc('Freeze');

		Object.getOwnPropertyNames(entry.Forms).forEach(function(name) {
			entry.Forms[name].freeze();
		});

		entry.Frozen = true;
	},

	onResize: function(e) {
		var page = dc.pui.Loader.Pages[this.Name];

		this.callPageFunc('onResize', e);

		for (var i = 0; i < this._onResize.length; i++)
			this._onResize[i].call(this, e);
	},

	registerResize: function(callback) {
		this._onResize.push(callback);
	},

	onFrame: function(e) {
		var page = dc.pui.Loader.Pages[this.Name];
	    var now = Date.now();

		this.callPageFunc('onFrame', e);

		for (var i = 0; i < this._onFrame.length; i++) {
			var render = this._onFrame[i];

		    var delta = now - render.Then;

		    if (delta > render.Interval) {
		        // adjust so lag time is removed, produce even rendering
		    	render.Then = now - (delta % render.Interval);

				render.Run.call(this, e);
		    }
		    else {
				dc.pui.Loader.requestFrame();	// keep calling until we don't skip
		    }
		}
	},

	registerFrame: function(render) {
		render.Then = Date.now();
		render.Interval = 1000 / render.Fps;

		this._onFrame.push(render);
	},

	onDestroy: function(e) {
		var page = dc.pui.Loader.Pages[this.Name];

		// clear the old timers
		for (var x = 0; x < this.Timers.length; x++) {
			var tim = this.Timers[x];

			if (!tim)
				continue;

			if (tim.Tid)
				window.clearTimeout(tim.Tid);
			else if (tim.Iid)
				window.clearInterval(tim.Iid);
		}

		for (var i = 0; i < this._onDestroy.length; i++)
			this._onDestroy[i].call(this, e);

		this._onResize = [];
		this._onDestroy = [];
		this._onFrame = [];
		this.Timers = [];

		// run the destroy on old page
		this.callPageFunc('onDestroy');
	},

	registerDestroy: function(callback) {
		this._onDestroy.push(callback);
	},

	allocateTimeout: function(options) {
		var entry = this;

		var pos = this.Timers.length;

		options.Tid = window.setTimeout(function () {
				window.clearTimeout(options.Tid);		// no longer need to clear this later, it is called
				entry.Timers[pos] = null;

				options.Op.call(this, options.Data);
			},
			options.Period);

		this.Timers.push(options);

		return options.Tid;
	},

	allocateInterval: function(options) {
		var entry = this;

		options.Iid = window.setInterval(function () {
				options.Op.call(this, options.Data);
			},
			options.Period);

		entry.Timers.push(options);

		return options.Iid;
	},

	formForInput: function(el) {
		var entry = this;
		var res = null;
		var fel = $(el).closest('form');
		var id = $(fel).attr('id');

		Object.getOwnPropertyNames(entry.Forms).forEach(function(name) {
			var f = entry.Forms[name];

			if (f.Id == id)
				res = f;
		});

		return res;
	},

	// list of results from validateForm
	validate: function() {
		var entry = this;
		var res = [ ];

		Object.getOwnPropertyNames(entry.Forms).forEach(function(name) {
			res.push(entry.Forms[name].validate());
		});

		return res;
	}
};

// ------------------- end PageEntry -------------------------------------------------------

dc.pui.Form = function(pageEntry, node) {
	$.extend(this, {
			Name: '[unknown]',
			PageEntry: pageEntry,		// page entry that form belongs to, null for default
			Inputs: { },
			InputOrder: [ ],
			Prefix: null,
			RecordOrder: [ "Default" ],
			RecordMap: { }, 		 // map records to data types
			AsNew: { },
			AlwaysNew: false,
			Managed: false,
			Loaded: false,
			Focus: null,
			TitleFields: [ ],
			FreezeInfo: null		//  { [recordname]: { Originals: { [fld]: [value] }, Values: { [fld]: [value] } }, [records]... }
		});

	this.Id = $(node).attr('id');

	this.Name = $(node).attr('data-dcf-name');

	var recorder = $(node).attr('data-dcf-record-order');
	var rectype = $(node).attr('data-dcf-record-type');

	var focus = $(node).attr('data-dcf-focus');
	var prefix = $(node).attr('data-dcf-prefix');
	var alwaysnew = $(node).attr('data-dcf-always-new');
	var managed = $(node).attr('data-dcf-managed');
	var titles = $(node).attr('data-dcf-title-fields');

	recorder = dc.util.String.isString(recorder) ? recorder.split(',') : [ 'Default' ];
	rectype = dc.util.String.isString(rectype) ? rectype.split(',') : [ '' ];

	this.RecordOrder = recorder;

	for (var im = 0; (im < recorder.length) && (im < rectype.length); im++)
		this.RecordMap[recorder[im]] = dc.schema.Manager.resolveType(rectype[im]);

	if (dc.util.String.isString(alwaysnew))
		this.AlwaysNew = (alwaysnew == 'true');

	if (dc.util.String.isString(managed))
		this.Managed = (managed == 'true');

	if (dc.util.String.isString(focus))
		this.Focus = focus;

	if (dc.util.String.isString(prefix))
		this.Prefix = prefix;

	if (dc.util.String.isString(titles))
		this.TitleFields = titles.split(',');
}

dc.pui.Form.prototype = {
	input: function(name) {
		return this.Inputs[name];
	},

	query: function(selector) {
		if (selector)
			return $('#' + this.Id).find(selector);

		return $('#' + this.Id);
	},

	inputQuery: function(name) {
		var inp = this.Inputs[name];		// TODO use Input's "query" - return container control if more than one control

		return $('#' + (inp ? inp.Id : '__unreal'));
	},

	submit: function() {
		return $('#' + this.Id).submit();
	},

	// prefer form level get and set over control level
	// at least for now they reflect the frozen state better
	getValue: function(field) {
		var form = this;

		if (form.Inputs[field]) {
			if (this.PageEntry.Frozen)
				return form.FreezeInfo[form.Inputs[field].Record].Values[field];
			//else if (! form.Loaded)
			//	return form.Inputs[field].DefaultValue;
			else
				return form.Inputs[field].getValue();
		}

		return null;
	},

	setValue: function(field, value) {
		var form = this;

		if (form.Inputs[field]) {
			if (this.PageEntry.Frozen)
				form.FreezeInfo[form.Inputs[field].Record].Values[field] = value;
			//else if (! form.Loaded)
			//	form.Inputs[field].DefaultValue = value;
			else
				form.Inputs[field].setValue(value);
		}
	},

	getValues: function(recname) {
		var form = this;

		var values = { };

		Object.getOwnPropertyNames(form.Inputs).forEach(function(name) {
			var iinfo = form.Inputs[name];

			if (recname && (iinfo.Record != recname))
				return;

			values[iinfo.Field] = iinfo.getValue();
		});

		return values;
	},

	load: function(callback) {
		var form = this;

		if(!form.RecordOrder) {
			if (callback)
				callback();
			return;
		}

		var steps = [ ];

		steps.push( {
			Alias: 'BeforeLoad',
			Title: 'Before Load',
			Func: function(step) {
				var task = this;

				var event = {
					Task: task,
					Wait: false,
					Stop: false
				};

				// do before load record event
				task.Store.Form.raiseEvent('BeforeLoad', event);

				if (event.Stop) 			// quit the task
					task.kill();
				else if (! event.Wait) 			//  continue the task, unless told to wait
					task.resume();
			}
		} );

		// TODO add before load controls

		for (var i = 0 ; i < form.RecordOrder.length; i++) {
			var rec = form.RecordOrder[i];

			steps.push( {
				Alias: 'LoadRecord',
				Title: 'Load Record',
				Params: {
					Record: rec
				},
				Func: function(step) {
					var task = this;

					task.Store.Current = {
						Result: 0,
						Message: null,
						Record: step.Params.Record,
						Data: { },
						AsNew: false
					};

					task.Store.Form.initChanges(step.Params.Record);

					var event = {
						Task: task,
						Wait: false,
						Stop: false,
						Record: step.Params.Record,
						Data: { },
						AsNew: false
					};

					// do before save record event
					task.Store.Form.raiseEvent('LoadRecord', event);

					if (event.Stop) {			// quit the task
						task.kill();
						return;
					}

					if (event.Wait)
						return;

					// if you use Wait then set task.Store.Current before resume
					// otherwise set the result into event Data
					task.Store.Current.Data = event.Data;
					task.Store.Current.AsNew = event.AsNew;

					if (event.Alert) {
						dc.pui.Popup.alert(event.Alert, function() {
							task.kill();
						});

						return;
					}

					if (event.Message) {
						dc.comm.sendMessage(event.Message, function (e) {
							if (e.Result != 0) {
								var ignore = false;

								if (event.IgnoreResults) {
									for (var i = 0; i < event.IgnoreResults.length; i++)
										if (event.IgnoreResults[i] == e.Result) {
											ignore = true;
											break;
										}
								}

								if (!ignore) {
									dc.pui.Popup.alert(e.Message);
									task.kill();
									return;
								}
							}

							task.Store.Current.Data = e.Body;
							task.Store.Current.Result = e.Result;
							task.Store.Current.Message = e.Message;

							task.resume();
						});
					}
					else {
						task.resume();
					}
				}
			} );

			steps.push( {
				Alias: 'AfterLoadRecord',
				Title: 'After Load Record',
				Params: {
					Record: rec
				},
				Func: function(step) {
					var task = this;

					var event = {
						Task: task,
						Wait: false,
						Stop: false,
						Record: step.Params.Record,
						Data: task.Store.Current.Data,
						AsNew: task.Store.Current.AsNew
					};

					// do after load record event
					task.Store.Form.raiseEvent('AfterLoadRecord', event);

					if (event.Stop) {			// quit the task
						task.kill();
						return;
					}

					if (event.Wait)
						return;

					// if you use Wait then set task.Store.Current before resume
					// otherwise set the result into event Data
					task.Store.Current.Data = event.Data;
					task.Store.Current.AsNew = event.AsNew;

					if (event.Alert) {
						dc.pui.Popup.alert(event.Alert, function() {
							task.kill();
						});

						return;
					}

					task.resume();
				}
			} );

			steps.push( {
				Alias: 'FillRecordInputs',
				Title: 'Fill Record Inputs',
				Params: {
					Record: rec
				},
				Func: function(step) {
					var task = this;

					task.Store.Form.loadRecord(task.Store.Current);

					task.resume();
				}
			} );
		}

		// TODO AfterLoadCtrls

		steps.push( {
			Alias: 'AfterLoad',
			Title: 'After Load',
			Func: function(step) {
				var task = this;

				task.Store.Current = {
					Result: 0,
					Message: null,
					Record: null,
					Data: { },
					AsNew: false
				};

				var event = {
					Task: task,
					Wait: false,
					Stop: false
				};

				// do before save record event
				task.Store.Form.raiseEvent('AfterLoad', event);

				if (event.Stop) {			// quit the task
					task.kill();
					return;
				}

				if (event.Wait)
					return;

				if (event.Alert) {
					dc.pui.Popup.alert(event.Alert, function() {
						task.kill();
					});

					return;
				}

				task.resume();
			}
		} );

		var loadtask = new dc.lang.Task(steps, function(res) {
			//console.log('observer: ' + res.Code);
			if (callback)
				callback();
		});

		loadtask.Store = {
			Form: form,
			Current: {
				Result: 0,
				Message: null,
				Record: null,
				Data: { },
				AsNew: false
			}
		};

		loadtask.run();
	},

	loadDefaultRecord: function(data, asNew) {
		return this.loadRecord( {
			Record: 'Default',
			Data: data,
			AsNew: asNew
		});
	},

	loadRecord: function(info) {
		var form = this;

		if (info.AsNew)
			form.AsNew[info.Record] = true;

		if (!info.Data)
			return;

		Object.getOwnPropertyNames(form.Inputs).forEach(function(name) {
			var iinfo = form.Inputs[name];

			if ((iinfo.Record == info.Record) && info.Data.hasOwnProperty(iinfo.Field)) {
				iinfo.setValue(info.Data[iinfo.Field]);
				iinfo.OriginalValue = info.Data[iinfo.Field];
			}
		});
	},

	thaw: function(callback) {
		var form = this;

		if(!form.RecordOrder) {
			if (callback)
				callback();
			return;
		}

		var steps = [ ];

		steps.push( {
			Alias: 'BeforeThaw',
			Title: 'Before Thaw',
			Func: function(step) {
				var task = this;

				var event = {
					Task: task,
					Wait: false,
					Stop: false
				};

				// do before load record event
				task.Store.Form.raiseEvent('BeforeThaw', event);

				if (event.Stop) 			// quit the task
					task.kill();
				else if (! event.Wait) 			//  continue the task, unless told to wait
					task.resume();
			}
		} );

		// TODO add before thaw controls

		for (var i = 0 ; i < form.RecordOrder.length; i++) {
			var rec = form.RecordOrder[i];

			steps.push( {
				Alias: 'ThawRecord',
				Title: 'Thaw Record',
				Params: {
					Record: rec
				},
				Func: function(step) {
					var task = this;

					task.Store.Current = {
						Result: 0,
						Message: null,
						Data: { },
						AsNew: false,
						Record: step.Params.Record,
						Data: task.Store.Form.FreezeInfo[step.Params.Record].Values,
						Originals: task.Store.Form.FreezeInfo[step.Params.Record].Originals
					};

					var event = {
						Task: task,
						Wait: false,
						Stop: false,
					};

					// do before thaw record event, set task.Store.Current before resume
					task.Store.Form.raiseEvent('ThawRecord', event);

					if (event.Stop) {			// quit the task
						task.kill();
						return;
					}

					if (event.Wait)
						return;

					if (event.Alert) {
						dc.pui.Popup.alert(event.Alert, function() {
							task.kill();
						});

						return;
					}

					task.resume();
				}
			} );

			steps.push( {
				Alias: 'FillRecordInputs',
				Title: 'Fill Record Inputs',
				Params: {
					Record: rec
				},
				Func: function(step) {
					var task = this;

					task.Store.Form.thawRecord(task.Store.Current);

					task.resume();
				}
			} );
		}

		// TODO AfterThawCtrls

		steps.push( {
			Alias: 'AfterThaw',
			Title: 'After Thaw',
			Func: function(step) {
				var task = this;

				task.Store.Current = {
					Result: 0,
					Message: null,
					Record: null,
					Data: { },
					AsNew: false
				};

				var event = {
					Task: task,
					Wait: false,
					Stop: false
				};

				// do before save record event
				task.Store.Form.raiseEvent('AfterThaw', event);

				if (event.Stop) {			// quit the task
					task.kill();
					return;
				}

				if (event.Wait)
					return;

				if (event.Alert) {
					dc.pui.Popup.alert(event.Alert, function() {
						task.kill();
					});

					return;
				}

				task.resume();
			}
		} );

		var thawtask = new dc.lang.Task(steps, function(res) {
			//console.log('observer: ' + res.Code);
			form.FreezeInfo = null;

			if (callback)
				callback();
		});

		thawtask.Store = {
			Form: form,
			Current: {
				Result: 0,
				Message: null,
				Record: null,
				Data: { },
				AsNew: false
			}
		};

		thawtask.run();
	},

	thawRecord: function(info) {
		var form = this;

		if (!info.Data)
			return;

		Object.getOwnPropertyNames(form.Inputs).forEach(function(name) {
			var iinfo = form.Inputs[name];

			if ((iinfo.Record == info.Record) && info.Data.hasOwnProperty(iinfo.Field)) {
				iinfo.setValue(info.Data[iinfo.Field]);
				iinfo.OriginalValue = info.Originals[iinfo.Field];
			}
		});
	},

	freeze: function() {
		var form = this;

		form.FreezeInfo = { };

		if(!form.RecordOrder)
			return;

		for (var i = 0; i < form.RecordOrder.length; i++)
			form.freezeRecord(form.RecordOrder[i]);
	},

	freezeRecord: function(recname) {
		var form = this;

		form.FreezeInfo[recname] = {
				Originals: { },
				Values: { }
		};

		Object.getOwnPropertyNames(form.Inputs).forEach(function(name) {
			var iinfo = form.Inputs[name];

			if (iinfo.Record == recname) {
				form.FreezeInfo[recname].Originals[iinfo.Field] = iinfo.OriginalValue;
				form.FreezeInfo[recname].Values[iinfo.Field] = iinfo.getValue();
			}
		});
	},

	loadInSession: function() {
		var form = this;

		var vstr = sessionStorage.getItem('dcFormStore-' + form.PageEntry.Name);

		if (! vstr)
			return false;

		var fnd = false;
		var values = JSON.parse(vstr);

		Object.getOwnPropertyNames(form.Inputs).forEach(function(name) {
			var iinfo = form.Inputs[name];

			if (values.hasOwnProperty(iinfo.Field)) {
				iinfo.setValue(values[iinfo.Field]);
				fnd = true;
			}
		});

		return fnd;
	},

	storeInSession: function() {
		var form = this;

		var values = form.getValues();

		sessionStorage.setItem('dcFormStore-' + form.PageEntry.Name, JSON.stringify(values));
	},

	save: function(callback) {
		var form = this;

		if (!form.RecordOrder) {
			callback();
			return;
		}

		var steps = [ ];

		steps.push( {
			Alias: 'BeforeSave',
			Title: 'Before Save',
			Func: function(step) {
				var task = this;

				var event = {
					Task: task,
					Wait: false,
					Stop: false,
					Changed: false
				};

				try {
					// do before save record event
					task.Store.Form.raiseEvent('BeforeSave', event);

					if (event.Changed)
						task.Store.AnyChanged = true;

					if (event.Stop) 			// quit the task
						task.kill();
					else if (! event.Wait) 			//  continue the task, unless told to wait
						task.resume();
				}
				catch (x) {
					console.log('error: ' + x);
					dc.pui.Apps.Busy = false;

					dc.pui.Popup.alert('Unexpected error.', function() {
						task.kill();
					});
				}
			}
		} );

		// TODO add before save controls

		for (var i = 0 ; i < form.RecordOrder.length; i++) {
			var rec = form.RecordOrder[i];

			steps.push( {
				Alias: 'SaveRecord',
				Title: 'Save Record',
				Params: {
					Record: rec
				},
				Func: function(step) {
					var task = this;

					task.Store.Current = {
						Result: 0,
						Message: null,
						Record: step.Params.Record,
						Data: task.Store.Form.getChanges(step.Params.Record),
						AnyChanged: false,		// for current record
						AfterData: null,
						AfterFlag: false
					};

					if (! task.Store.Form.isChanged(step.Params.Record) && ! task.Store.AnyChanged) {
						task.resume();
						return;
					}

					task.Store.AnyChanged = true;
					task.Store.Current.AnyChanged = true;

					var event = {
						Task: task,
						Wait: false,
						Stop: false,
						Record: task.Store.Current.Record,
						Data: task.Store.Current.Data,
						Form: task.Store.Form
					};

					if (task.Store.Form.Managed) {
						var title = '';

						for (var n = 0; n < task.Store.Form.TitleFields.length; n++) {
							var fname = task.Store.Form.TitleFields[n];

							if (event.Data[fname]) {
								if (title.length != 0)
									title += ' - ';

								title += event.Data[fname];
							}
						}

						var captcha = task.Store.Captcha ? task.Store.Captcha : 'xyz';

						if (! captcha && task.Store.Form.Captcha)
							captcha = task.Store.Form.Captcha.Token;

						// form the save message
						event.Message = {
							"Service":"dcCoreServices",
							"Feature":"ManagedForm",
							"Op":"Submit",
							Body: {
								Form: task.Store.Form.Name,
								Captcha: captcha,
								Title: title,
								Data: event.Data
							}
						};
					}
					else {
						// do before save record event
						task.Store.Form.raiseEvent('SaveRecord', event);
					}

					if (event.Stop) {			// quit the task
						task.kill();
						return;
					}

					if (event.Wait)
						return;

					// if you use Wait then set task.Store.Current before resume
					// otherwise set the result into event Data
					task.Store.Current.Data = event.Data;

					if (event.Alert) {
						dc.pui.Apps.Busy = false;

						dc.pui.Popup.alert(event.Alert, function() {
							task.kill();
						});

						return;
					}

					if (event.Message) {
						dc.comm.sendMessage(event.Message, function (e) {
							if (e.Result != 0) {
								dc.pui.Apps.Busy = false;

								if (event.ErrorHandler)
									event.ErrorHandler(e);
								else
									dc.pui.Popup.alert(e.Message);

								task.kill();
								return;
							}

							task.Store.Current.AfterFlag = true;
							task.Store.Current.AfterData = e.Body;
							task.Store.Current.Result = e.Result;
							task.Store.Current.Message = e.Message;

							task.Store.Form.clearChanges(step.Params.Record);

							task.resume();
						});
					}
					else {
						task.Store.Form.clearChanges(step.Params.Record);

						task.resume();
					}
				}
			} );

			steps.push( {
				Alias: 'AfterSaveRecord',
				Title: 'After Save Record',
				Params: {
					Record: rec
				},
				Func: function(step) {
					var task = this;

					if (! task.Store.Current.AnyChanged) {
						task.resume();
						return;
					}

					var event = {
						Task: task,
						Wait: false,
						Stop: false,
						Record: step.Params.Record,
						Data: task.Store.Current.AfterData
					};

					// do before save record event
					task.Store.Form.raiseEvent('AfterSaveRecord', event);

					if (event.Stop) {			// quit the task
						task.kill();
						return;
					}

					if (event.Wait)
						return;

					// if you use Wait then set task.Store.Current before resume
					// otherwise set the result into event Data
					task.Store.Current.AfterData = event.Data;

					if (event.Alert) {
						dc.pui.Apps.Busy = false;

						dc.pui.Popup.alert(event.Alert, function() {
							task.kill();
						});

						return;
					}

					task.resume();
				}
			} );

			// AfterSaveCtrls

			Object.getOwnPropertyNames(form.Inputs).forEach(function(name) {
				var iinfo = form.Inputs[name];

				if (iinfo.Record != rec)
					return;

				if (iinfo.onAfterSave) {
					//changes[name] = iinfo.getValue();

					steps.push( {
						Alias: 'AfterSaveControl',
						Title: 'After Save Control',
						Params: {
							Record: rec,
							Input: iinfo
						},
						Func: function(step) {
							var task = this;

							if (! task.Store.Current.AnyChanged) {
								task.resume();
								return;
							}

							var event = {
								Task: task,
								Step: step,
								Record: step.Params.Record,
								Data: task.Store.Current.AfterData
							};

							// control has to resume/kill/etc
							step.Params.Input.onAfterSave(event);
						}
					} );
				}
			});

			if (form.Managed) {
				steps.push( {
					Alias: 'AfterSaveManaged',
					Title: 'After Save Managed Event',
					Params: {
						Record: rec
					},
					Func: function(step) {
						var task = this;

						if (! task.Store.Current.AnyChanged) {
							task.resume();
							return;
						}

						dc.comm.sendMessage({
							Service: "dcCoreServices",
							Feature: "ManagedForm",
							Op: "Complete",
							Body: {
								Form: task.Store.Form.Name,
								Token: task.Store.Current.AfterData.Token,
								Uuid: task.Store.Current.AfterData.Uuid
							}
						},
						function (e) {
							if (e.Result != 0) {
								dc.pui.Apps.Busy = false;

								dc.pui.Popup.alert(e.Message);
								task.kill();
								return;
							}

							task.resume();
						});
					}
				} );
			}
		}

		steps.push( {
			Alias: 'AfterSave',
			Title: 'After Save',
			Func: function(step) {
				var task = this;

				var event = {
					Task: task,
					Wait: false,
					Stop: false,
					Changed: task.Store.AnyChanged,
					DefaultSaved: false
				};

				if (task.Store.Form.Managed) {
					task.Store.Form.query('a[data-dc-tag="dcf.SubmitButton"],a[data-dc-tag="dcf.SubmitCaptchaButton"]').addClass('pure-button-disabled');
					event.DefaultSavedMessage = 'Form successfully submitted.';
					event.DefaultSaved = true;

					gtag('event', 'Submit', {
						'event_category': 'Form',
						'event_label': task.Store.Form.Name
					});
				}

				// do before save record event
				task.Store.Form.raiseEvent('AfterSave', event);

				if (event.Stop) {			// quit the task
					task.kill();
					return;
				}

				if (event.Wait)
					return;

				task.Store.Current = {
					Result: 0,
					Message: null,
					Record: null,
					Data: { },
					AnyChanged: false,		// for current record
					AfterData: null,
					AfterFlag: false,
				};

				if (event.Alert) {
					dc.pui.Apps.Busy = false;

					dc.pui.Popup.alert(event.Alert, function() {
						task.kill();
					});

					return;
				}

				if (event.DefaultSaved) {
					dc.pui.Apps.Busy = false;

					var msg = task.Store.AnyChanged ? 'Saved' : 'No changes, nothing to save.';

					if (event.DefaultSavedMessage)
						msg = event.DefaultSavedMessage;

					dc.pui.Popup.alert(msg, function() { task.resume(); });

					return;
				}

				task.resume();
			}
		} );

		var savetask = new dc.lang.Task(steps, function(res) {
			//console.log('observer: ' + res.Code);
			callback(res);
		});

		savetask.Store = {
			Form: form,
			AnyChanged: false,
			Current: {
				Result: 0,
				Message: null,
				Record: null,
				Data: { },
				AnyChanged: false,		// for current record
				AfterData: null,
				AfterFlag: false,
			}
		};

		savetask.run();
	},

	initChanges: function(recname) {
		var form = this;

		Object.getOwnPropertyNames(form.Inputs).forEach(function(name) {
			var iinfo = form.Inputs[name];

			if (iinfo.Record == recname) {
				iinfo.setValue(iinfo.DefaultValue);
				iinfo.OriginalValue = iinfo.DefaultValue;
			}
		});
	},

	isDefaultChanged: function() {
		return this.isChanged('Default');
	},

	isChanged: function(recname) {
		var form = this;

		if (form.AsNew[recname] || form.AlwaysNew)
			return true;

		var changed = false;

		Object.getOwnPropertyNames(form.Inputs).forEach(function(name) {
			var iinfo = form.Inputs[name];

			if ((iinfo.Record == recname) && iinfo.isChanged())
				changed = true;
		});

		if (changed)
			return true;

		var event = {
			Changed: false,
			Record: recname
		};

		form.raiseEvent('IsRecordChanged', event);

		return event.Changed;
	},

	getDefaultChanges: function() {
		return this.getChanges('Default');
	},

	getChanges: function(recname) {
		var form = this;
		var changes = { };

		var asNew = (form.AsNew[recname] || form.AlwaysNew);

		Object.getOwnPropertyNames(form.Inputs).forEach(function(name) {
			var iinfo = form.Inputs[name];

			if (iinfo.Record != recname)
				return;

			if (asNew || iinfo.isChanged())
				changes[iinfo.Field] = iinfo.getValue();
		});

		return changes;
	},

	clearDefaultChanges: function() {
		return this.clearChanges('Default');
	},

	clearChanges: function(recname) {
		var form = this;

		form.AsNew[recname] = false;
		form.FreezeInfo = null;

		Object.getOwnPropertyNames(form.Inputs).forEach(function(name) {
			var iinfo = form.Inputs[name];

			if (iinfo.Record == recname)
				iinfo.OriginalValue = iinfo.getValue();
		});
	},

	raiseEvent: function(name, event) {
		if (event && event.Record && (event.Record != 'Default'))
			this.PageEntry.callPageFunc(this.Prefix ? this.Prefix + '-' + name + '-' + event.Record : name + '-' + event.Record, event);
		else
			this.PageEntry.callPageFunc(this.Prefix ? this.Prefix + '-' + name : name, event);
	},

	/*
	// return results on all controls

	{
		Form: [form obj],
		Pass: t/f,
		Inputs: [ {
			Input: [input record],
			Code: 0 or err num,
			Message: [message]
		} ]
	}
	*/

	validate: function() {
		var form = this;

		var res = {
			Form: form,
			Pass: true,
			Inputs: [ ]
		};

		for (var n = 0; n < form.InputOrder.length; n++) {
			var mr = form.input(form.InputOrder[n]).validateInput();

			res.Inputs.push(mr);

			if (mr.Code != 0)
				res.Pass = false;
		}

		return res;
	},

	validateControl: function(iinfo) {
		var form = this;

		// validate entire field, even if multiple inputs are in it
		var fld = $('#' + iinfo.Id).closest('div.dc-field');

		return form.validateField(fld);
	},

	validateField: function(fld) {
		var form = this;

		if (dc.util.String.isString(fld))
			fld = $('#' + form.Id).find('div.dc-field[data-dcf-mapname=' + fld + ']');

		if (!fld)
			return;

		var res = {
			Form: form,
			Pass: true,
			Inputs: [ ]
		};

		// find all inputs in field and validate them
		$(fld).find('*[data-dcf-mapname]').each(function() {
			var mr = form.input($(this).attr('data-dcf-mapname')).validateInput();

			res.Inputs.push(mr);

			if (mr.Code != 0)
				res.Pass = false;
		});

		// update the messages
		form.updateMessages(res);

		return res;
	},

	updateMessages: function(formresults) {
		var form = this;

		for (var i = 0; i < formresults.Inputs.length; i++) {
			var mr = formresults.Inputs[i];
			var fld = $('#' + mr.Input.Id).closest('div.dc-field');

			if (fld)
				$(fld).removeClass('dc-invalid');
		}

		// start at end so that multi-text gets the first message if there are multiple
		for (var i = formresults.Inputs.length - 1; i >= 0; i--) {
			var mr = formresults.Inputs[i];
			var fld = $('#' + mr.Input.Id).closest('div.dc-field');

			if (fld && mr.Code) {
				$(fld).addClass('dc-invalid');

				// if not using a fixed message then show specific message
				$(fld).find('div.dc-message-danger:not(.dc-fixed-message)').text(mr.Message);
			}
		}
	}
};

// ------------------- end Form -------------------------------------------------------

dc.pui.TagCache = { };   // place to cache data that spans instances of a tag

//place to add functions to tags
dc.pui.TagFuncs = {
	'dcf.Range': {
		'update': function(entry, node) {
			$(node).closest('div').find('.dc-addon-info').text(node.value);
		}
	},
	'dc.MenuWidget': {
		'toggleShow': function(entry, node) {
			$(node).find('> .dc-menu-list').toggleClass('dc-menu-mobile-disable');
		}
	},
	'dc.MenuBarWidget': {
		'toggleShow': function(entry, node) {
			$(node).find('> .dc-menu-list').toggleClass('dc-menu-mobile-disable');
		}
	},
	'dc.PagePanel': {
		'setTitle': function(entry, node, title) {
			$(node).find('> div:first-child h1').text(title);
		}
	},
	'dc.Panel': {
		'setTitle': function(entry, node, title) {
			$(node).find('> div:first-child h2').text(title);
		}
	},
	'dc.Recaptcha': {
		'execute': function(entry, node) {
			grecaptcha.execute();
		},
		'reset': function(entry, node) {
			grecaptcha.reset();
		}
	}
};

dc.pui.Tags = {
	'dc.PagePanel': function(entry, node) {
		$(node).find('a').click(function(e) {
			var processed = false;

			if ($(this).hasClass('dcui-pagepanel-close')) {
				if (entry.hasPageFunc('onClose'))
					entry.callPageFunc('onClose', e);
				else
					entry.Layer.close();

				processed = true;
			}
			else if ($(this).hasClass('dcui-pagepanel-back')) {
				if (entry.hasPageFunc('onBack'))
					entry.callPageFunc('onBack', e);
				else
					entry.Layer.back();

				processed = true;
			}
			/* TODO restore
			else if ($(this).hasClass('dcui-pagepanel-help')) {
				var hpage = entry.Name + "-help";

				dc.pui.Alert.loadPage(hpage);
				processed = true;
			}
			*/
			else if ($(this).hasClass('dcui-pagepanel-menu')) {
				if (! entry.Layer.getMenuArea)
					return;

				var area = entry.Layer.getMenuArea();

				if (area)
					dc.pui.Popup.menu(area);
				processed = true;
			}

			if (processed)
				e.preventDefault();

			return ! processed;
		});
	},
	'dc.Button': function(entry, node) {
		dc.pui.Tags['dc.Link'](entry, node);
	},
	'dc.Link': function(entry, node) {
		var click = $(node).attr('data-dc-click');
		var page = $(node).attr('data-dc-page');
		var linkto = $(node).attr('data-dc-to');
		var link = $(node).attr('href');

		if (link && ! $(node).attr('target') && (dc.util.String.startsWith(link, 'http:') || dc.util.String.startsWith(link, 'https:') || dc.util.String.startsWith(link, 'mailto:') || dc.util.String.endsWith(link, '.pdf')))
			$(node).attr('target', '_blank');

		if (click || page) {
			var clickfunc = function(e, ctrl) {
				if (! $(node).hasClass('pure-button-disabled') && ! dc.pui.Apps.busyCheck()) {
					entry.LastFocus = $(node);

					if (click)
						entry.callPageFunc(click, e, ctrl);
					else if (page)
						entry.Layer.loadPage(page);
				}
			};

			$(node).click(function(e) {
				clickfunc(e, this);

				e.preventDefault();
				return false;
			});

			if (node.nodeName == 'SPAN') {
				$(node).keypress(function(e) {
					var keycode = (e.keyCode ? e.keyCode : e.which);
					if (keycode == '13') {
						clickfunc(e, this);

						e.preventDefault();
						return false;
					}
				});
			}
		}
		// TODO review, maybe not on the main layer
		else if (link && (link.length > 1) && (link.charAt(0) == '#')) {
			$(node).click(link, function(e) {
				entry.scrollPage(e.data);
				e.preventDefault();
				return false;
			});
		}
		else if (link && (link.charAt(0) == '/')) {
			if ((link.length == 1) || (link.charAt(1) != '/')) {
				var dpos = link.lastIndexOf('.');
				var ext = (dpos == -1) ? '' : link.substr(dpos);
				var hasext = false;

				if (ext && ext.indexOf('/') == -1)
					hasext = true;

				if (! hasext || (ext == '.html')) {
					$(node).click(link, function(e) {
						entry.LastFocus = $(node);

						if ($(e.currentTarget).attr('target') == '_blank') {
							window.open(e.data, '_blank').focus();
						}
						else if (linkto) {
							window.location = e.data;		// want to reload
						}
						else {
							entry.Layer.loadPage(e.data);
						}

						e.preventDefault();
						return false;
					});
				}
				else if (! $(node).attr('target')) {
					$(node).attr('target', '_blank');
				}
			}
		}
		// TODO revise and update BID
		else if (link && dc.handler && dc.handler.Protocols) {
			var proto = link.substr(0, link.indexOf(':'));

			if (dc.handler.Protocols[proto]) {
				$(node).click(link, function(e) {
					entry.LastFocus = $(node);

					dc.handler.Protocols[proto].call(e);		// custom handler

					e.preventDefault();
					return false;
				});
			}
		}
	},
	'dc.CaptchaButton': function(entry, node) {
		var click = $(node).attr('data-dc-click');
		var skey = $(node).attr('data-dc-sitekey');
		var action = $(node).attr('data-dc-action');

		if (click && skey) {
			var clickfunc = function(e, ctrl) {
				if (! $(node).hasClass('pure-button-disabled') && ! dc.pui.Apps.busyCheck()) {
					entry.LastFocus = $(node);

					var clickprep = $(node).attr('data-dc-click-prep');

					if (clickprep) {
						var flagok = entry.callPageFunc(clickprep, e, ctrl);

						if (! flagok) {
							$(node).removeClass('pure-button-disabled');
							return;
						}
					}

					grecaptcha.ready(function() {
						grecaptcha
							.execute(skey, { action: action })
							.then(function(token) {
								entry.callPageFunc(click, e, token, ctrl);
							});
					});
				}
			};

			$(node).click(function(e) {
				clickfunc(e, this);

				e.preventDefault();
				return false;
			});

			if (node.nodeName == 'SPAN') {
				$(node).keypress(function(e) {
					var keycode = (e.keyCode ? e.keyCode : e.which);
					if (keycode == '13') {
						clickfunc(e, this);

						e.preventDefault();
						return false;
					}
				});
			}
		}
	},
	'dc.MenuBarWidget': function(entry, node) {
		dc.pui.Tags['dc.MenuWidget'](entry, node);

		// TODO call js
	},
	'dc.MenuWidget': function(entry, node) {
		$(node).find('a.dc-menu-open').click(function(e) {
			$(node).find('> .dc-menu-list').toggleClass('dc-menu-mobile-disable');

			entry.callPageFunc("onMenuWidgetToggle", e, node, 'open');

			e.preventDefault();
			return false;
		});

		$(node).find('a.dc-menu-close').click(function(e) {
			$(node).find('> .dc-menu-list').toggleClass('dc-menu-mobile-disable');

			entry.callPageFunc("onMenuWidgetToggle", e, node, 'close');

			e.preventDefault();
			return false;
		});
	},
	'dcf.MD': function(entry, node) {
		/* TODO review	*/
	},
	'dcf.ManagedForm': function(entry, node) {
		dc.pui.Tags['dcf.Form'](entry, node);
	},
	'dcf.Form': function(entry, node) {
		var fname = $(node).attr('data-dcf-name');

		$(node).attr('novalidate', 'novalidate'); 	// for HTML5

		// don't add form info on a thaw, reuse but reset the inputs
		if (entry.Forms[fname])
			entry.Forms[fname].Inputs = { };
		else
			entry.Forms[fname] = new dc.pui.Form(entry, node);

		$(node).submit(function(e) {
			if (dc.pui.Apps.busyCheck()) 		// proect against user submit such as Enter in a TextField
				return false;

			dc.pui.Apps.Busy = true;

			var form = entry.Forms[fname];

			//	validator.settings.submitHandler.call( validator, validator.currentForm, e );
			var vres = form.validate();

			form.updateMessages(vres);

			var vpass = true;

			for (var i = 0; i < vres.Inputs.length; i++) {
				var inp = vres.Inputs[i];

				if (inp.Code != 0) {
					dc.pui.Popup.alert(inp.Message, function() {
						if (form.OnFocus) {
							form.PageEntry.callPageFunc(form.OnFocus, inp.Input);
						}
						else {
							var fmname = (inp.Input.Record != 'Default') ? inp.Input.Record + '.' + inp.Input.Field : inp.Input.Field;
							form.inputQuery(fmname).focus();
						}
					});

					vpass = false;
					break;
				}
			}

			if (vpass) {
				form.save(function() {
					dc.pui.Apps.Busy = false;
				});
			}
			else {
				dc.pui.Apps.Busy = false;
			}

			e.preventDefault();
			return false;
		});
	},
	'dcf.Text': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.TextInput(entry, node);
	},
	'dcf.Password': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.TextInput(entry, node);
	},
	'dcf.TextArea': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.TextInput(entry, node);
	},
	'dcf.MultiText': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.TextInput(entry, node);
	},
	'dcf.Number': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.TextInput(entry, node);
	},
	'dcf.Range': function(entry, node) {
		var input = new dc.pui.controls.Range(entry, node);
	},
	'dcf.Hidden': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.TextInput(entry, node);
	},
	'dcf.Label': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.LabelInput(entry, node);
	},
	'dcf.Select': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.Select(entry, node);
	},
	'dcf.Checkbox': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.Checkbox(entry, node);
	},
	'dcf.CheckGroup': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.CheckGroup(entry, node);
	},
	'dcf.HorizCheckGroup': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.CheckGroup(entry, node);
	},
	'dcf.RadioGroup': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.RadioGroup(entry, node);
	},
	'dcf.HorizRadioGroup': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.RadioGroup(entry, node);
	},
	'dcf.YesNo': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.YesNo(entry, node);
	},
	'dcf.Uploader': function(entry, node) {
		// input self registers so don't do anything with it
		var input = new dc.pui.controls.Uploader(entry, node);
	},
	'dcf.ValidateButton': function(entry, node) {
		$(node).on("click", function(e) {
			var fnode = $(node).closest('form');

			if (!fnode)
				return;

			var fname = $(fnode).attr('data-dcf-name');

			if (!fname)
				return;

			var form = entry.form(fname);

			if (!form)
				return null;

			var fld = $(node).closest('div.dc-field');

			if (!fld)
				return null;

			var vres = form.validateField(fld);

			if (vres.Pass)
				dc.pui.Popup.alert($(fld).find('div.dc-message-info').text());		// TODO if no message then say "all good"
			else
				dc.pui.Popup.alert($(fld).find('div.dc-message-danger').text());

			e.preventDefault();
			return false;
		});
	},
	'dcf.SubmitCaptchaButton': function(entry, node) {
		var skey = $(node).attr('data-dc-sitekey');
		var action = $(node).attr('data-dc-action');

		var clickfunc = function(e, ctrl) {
			var fnode = $(node).closest('form');

			if (fnode && ! $(node).hasClass('pure-button-disabled') && ! dc.pui.Apps.busyCheck()) {
				dc.pui.Apps.Busy = true;

				entry.LastFocus = $(node);

				var form = entry.form(fnode.attr('data-dcf-name'));
				var vres = form.validate();

				form.updateMessages(vres);

				for (var i = 0; i < vres.Inputs.length; i++) {
					var inp = vres.Inputs[i];

					if (inp.Code != 0) {
						dc.pui.Popup.alert(inp.Message, function() {
							if (form.OnFocus) {
								form.PageEntry.callPageFunc(form.OnFocus, inp.Input);
							}
							else {
								var fmname = (inp.Input.Record != 'Default') ? inp.Input.Record + '.' + inp.Input.Field : inp.Input.Field;
								form.inputQuery(fmname).focus();
							}
						});

						break;
					}
				}

				if (vres.Pass) {
					$(node).addClass('pure-button-disabled');

					if (skey) {
						grecaptcha.ready(function() {
							grecaptcha
								.execute(skey, { action: action })
								.then(function(token) {
									form.Captcha = {
										Token: token,
										Action: action
									};

									form.save(function(task) {
										if (task && task.hasErrors())
											$(node).removeClass('pure-button-disabled');

										dc.pui.Apps.Busy = false;
									});
								});
						});
					}
					else {
						form.Captcha = {
							Token: '0123456789',
							Action: action
						};

						form.save(function(task) {
							if (task && task.hasErrors())
								$(node).removeClass('pure-button-disabled');

							dc.pui.Apps.Busy = false;
						});
					}
				}
				else {
					dc.pui.Apps.Busy = false;
				}
			}
		};

		$(node).click(function(e) {
			clickfunc(e, this);

			e.preventDefault();
			return false;
		});

		if (node.nodeName == 'SPAN') {
			$(node).keypress(function(e) {
				var keycode = (e.keyCode ? e.keyCode : e.which);
				if (keycode == '13') {
					clickfunc(e, this);

					e.preventDefault();
					return false;
				}
			});
		}
	},
	'dcf.SubmitButton': function(entry, node) {
		$(node).on("click", function(e) {
			if (! $(node).hasClass('pure-button-disabled') && !dc.pui.Apps.busyCheck()) {
				var fnode = $(node).closest('form');

				if (fnode)
					$(fnode).submit();
			}

			e.preventDefault();
			return false;
		});
	},
	'dc.Recaptcha': function(entry, node) {
		var key = $(node).attr('data-sitekey');

		if (! key)
			return;

		// TODO for now assume Google, later add other types

		var captchaCallback = function() {
			var skey = $(node).attr('data-sitekey');

			if (! skey)
				return;

			var rfunc = $(node).attr('data-ready-func');

			if (rfunc) {
				entry.callPageFunc(rfunc);
			}

			var widgetid = grecaptcha.render(node, {
				sitekey: skey,
				hl: (dc.util.Cookies.getCookie('dcLang') == 'spa') ? 'es' : 'en',
				callback: function(response) {
					$(node).attr('data-response', response);

					var func = $(node).attr('data-func');

					if (func) {
						entry.callPageFunc(func, response);
					}
					else {
						console.log('Recaptcha: ' + response);
					}

					// TODO reset captcha
				}
			});

			$(node).attr('data-widgetid', widgetid);
		};

		loadCaptcha($(node).attr('data-service'), key, captchaCallback);
	},
	'dcm.GalleryWidget': function(entry, node) {
		$(node).find('img').on("click", function(e) {
			var expanded = $(node).attr('data-dc-expanded');

			if (expanded) {
				var itemlevel = $(this).closest('.dc-image-item').get(0);

				if (! itemlevel)
					itemlevel = $(this).get(0);

				var show = {
					Path: $(node).attr('data-path'),
					Show: $(node).attr('data-show'),
					Variant: expanded,
					Extension: $(node).attr('data-ext'),
					StartPos: $(itemlevel).index(),
					Images: []
				};

				$(node).find('.dc-image-item').each(function() {
					var idata = $(this).attr('data-dc-image-data');

					if (!idata)
						return;

					var ii = JSON.parse(idata);

					show.Images.push(ii);
				});

				if (show.Images.length > 0) {
					dc.pui.FullScreen.loadPage('/dcw/view-image', {
						View: show
					});

					e.preventDefault();
					return false;
				}
			}

			return true;
		});
	},
	'dcm.HighlightWidget': function(entry, node) {
		$(node).find('.dc-widget-highlight-ctrl-left a').click(function(e) {
			var width = $(node).find('.dc-widget-highlight-entry').outerWidth();

			$(node).find('.dc-widget-highlight-list').scrollLeft($(node).find('.dc-widget-highlight-list').scrollLeft() - width);

			e.preventDefault();
			return false;
		});

		$(node).find('.dc-widget-highlight-ctrl-right a').click(function(e) {
			var width = $(node).find('.dc-widget-highlight-entry').outerWidth();

			$(node).find('.dc-widget-highlight-list').scrollLeft($(node).find('.dc-widget-highlight-list').scrollLeft() + width);

			e.preventDefault();
			return false;
		});

		var list = $(node).find('.dc-widget-highlight-list');

		if (list.length == 0)
			return;

		var cx = 0, x0 = 0, locked = false;

		function unify(e) {	return e.changedTouches ? e.changedTouches[0] : e };

		function lock(e) {
			x0 = unify(e).clientX;
			locked = true;
		};

		function drag(e) {
			e.preventDefault();

			/*
			if (locked) {
				var dx = x0 - unify(e).clientX;
				list.scrollLeft(cx + dx);
			}
			*/
		};

		function move(e) {
			if(locked) {
				var dx = x0 - unify(e).clientX;

				var width = list.find('.dc-widget-highlight-entry').outerWidth();

				//console.log('dx: ' + dx)

				if (dx > 64)
					list.scrollLeft(list.scrollLeft() + width);
				else if (dx < -64)
					list.scrollLeft(list.scrollLeft() - width);

				locked = false;
				//cx = list.scrollLeft();
			}
		};

		list.get(0).addEventListener('mousedown', lock, false);
		list.get(0).addEventListener('touchstart', lock, false);

		list.get(0).addEventListener('mousemove', drag, false);
		list.get(0).addEventListener('touchmove', drag, false);

		list.get(0).addEventListener('mouseup', move, false);
		list.get(0).addEventListener('touchend', move, false);
	},
	'dcm.ImageWidget': function(entry, node) {
		$(node).on("click", function(e) {
			var expanded = $(node).attr('data-dc-expanded');

			if (expanded) {
				var path = $(node).attr('data-dc-path');
				var ppos = path.lastIndexOf('/');

				var show = {
					Path: path.substr(0, ppos),
					Variant: expanded,
					Extension: $(node).attr('data-dc-ext'),
					StartPos: 0,
					Images: [ { Alias: path.substr(ppos + 1) } ]
				};

				dc.pui.FullScreen.loadPage('/dcw/view-image', {
					View: show
				});

				// only prevent if expanded
				e.preventDefault();
				return false;
			}

			return true;
		});
	},
	'dc.MediaSection': function(entry, node) {
		$(node).find('.dc-section-gallery-list > *').each(function() {
			var kind = $(this).attr('data-dc-kind');

			if (kind == 'video') {
				var pbtn = $('<div></div>');

				pbtn.addClass('dc-media-video');

				$(this).append(pbtn);
			}
		});

		$(node).find('.dc-section-gallery-list > *').on("click", function(e) {
			//console.log('img: ' + $(this).attr('data-image') + " show: "
			//	+ $(node).attr('data-show') + " path: " + $(node).attr('data-path'));

			var sposanchor = $(this).closest('.dc-gallery-image-anchor').get(0);
			var spos = sposanchor ? $(sposanchor).index() : $(this).index();

			var show = {
				StartPos: spos,
				AutoAdvance: false,
				Images: []
			};

			$(node).find('.dc-section-gallery-list img').each(function() {
				var idata = $(this).attr('data-dc-media');

				if (!idata)
					return;

				var ii = JSON.parse(idata);

				show.Images.push(ii);
			});

			if (dc.handler && dc.handler.tags && dc.handler.tags.ViewMedia && dc.handler.tags.ViewMedia.open)
				dc.handler.tags.ViewMedia.open(entry, node, show);
			else
				dc.pui.FullScreen.loadPage('/dcw/ViewMedia', {
					View: show
				});

			e.preventDefault();
			return false;
		});
	}
};

// ------------------- end Tags -------------------------------------------------------

dc.pui.controls = { };

dc.pui.controls.Input = function(entry, node) {
	this.init(entry, node);
}

dc.pui.controls.Input.prototype = {
	init: function(entry, node) {
		$.extend(this, {
			Id: null,
			Field: null,
			Record: 'Default',
			DataType: 'String',
			Form: null,
			Label: null,
			DefaultValue: null,
			OriginalValue: null,
			Required: false,
			Pattern: null,
			InvalidMessage: null
		});

		var fnode = $(node).closest('form');

		if (!fnode)
			return;

		var frmname = $(fnode).attr('data-dcf-name');

		if (!frmname)
			return;

		var form = entry.form(frmname);

		if (!form)
			return null;

		this.Form = form;

		var id = $(node).attr('id');

		if (!id)
			return;

		this.Id = id;

		var val = $(node).attr('value');

		if (! dc.util.String.isEmpty(val)) {
			this.DefaultValue = val;
		}
		else {
			val = $(node).attr('data-value');

			if (! dc.util.String.isEmpty(val))
				this.DefaultValue = val;
		}

		this.Id = id;

		var fname = $(node).attr('data-dcf-name');

		if (! fname)
			return;

		var rec = $(node).attr('data-dcf-record');

		if (! rec)
			rec = 'Default';

		// non-default records have the record name in the Input map
		var fmname = (rec != 'Default') ? rec + '.' + fname : fname;

		this.Field = fname;
		this.Record = rec;

		$(node).attr('data-dcf-mapname', fmname);

		form.Inputs[fmname] = this;
		form.InputOrder.push(fmname);

		var dtype = $(node).attr('data-dcf-data-type');

		if (dtype)
			this.DataType = dtype;

		var lbl = $(node).attr('data-dcf-label');

		if (!lbl) {
			var fld = $(node).closest('div.dc-field');

			if (fld)
				lbl = $(fld).attr('data-dcf-label');
		}

		if (!lbl)
			lbl = fname;

		this.Label = lbl;
		this.Required = ($(node).attr('data-dcf-required') == 'true');
		this.Pattern = $(node).attr('data-dcf-pattern');

		if (this.Pattern)
			this.PatternExp = new RegExp('^(?:' + this.Pattern + ')$');

		this.Schema = dc.schema.Manager.resolveType(this.DataType);

		if (!dtype && form.RecordMap[rec] && form.RecordMap[rec].Fields[fname]) {
			this.Schema = form.RecordMap[rec].Fields[fname];
			this.Data = fname;		// TODO - can we get the name from Schema?  this makes sure subclasses know a type has been assigned, not just default
		}

		if (!this.Required && this.Schema && (this.Schema.Required == 1))
			this.Required = true;

		if (this.Required)
			$(node).closest('div.dc-field')
				.addClass('dc-required');

		$(node).on('focusin', this, function(e) {
			entry.LastFocus = $(this);
		});
	},
	validate: function() {
		if (this.Form)
			this.Form.validateControl(this);
	},
	flag: function(msg) {
		this.InvalidMessage = msg;
		this.validate();
	},
	unflag: function() {
		this.InvalidMessage = null;
		this.validate();
	},
	setValue: function(value) {
		value = dc.util.String.toString(value);

		if (!value)
			value = '';

		$('#' + this.Id).val(value);
	},
	getValue: function() {
		var val = $('#' + this.Id).val();

		if (dc.util.Struct.isEmpty(val))
			val = null;

		return val;
	},
	validateInput: function() {
		var mr = new dc.lang.OperationResult();

		mr.Input = this;

		var value = this.getValue();
		var vempty = dc.util.Struct.isEmpty(value);

		if (this.InvalidMessage) {
			mr.error(1, this.InvalidMessage);
		}
		else if (this.Required && vempty) {
			mr.errorTr(424, [ value, this.Label ]);
		}
		else if (!vempty) {
			if (this.PatternExp && !this.PatternExp.test(value))
				mr.errorTr(447, [ value, this.Label ]);
			else if (this.Schema && (this.Schema instanceof dc.schema.FieldOption))
				this.Schema.validate(true, value, mr);		// present
			else if (this.Schema)
				this.Schema.validate(value, mr);
			else
				mr.errorTr(436);
		}

		return mr;
	},
	isChanged: function() {
		return (this.OriginalValue != this.getValue());
	}
};


dc.pui.controls.TextInput = function(entry, node) {
	this.init(entry, node);
};

dc.pui.controls.TextInput.prototype = new dc.pui.controls.Input();

dc.pui.controls.TextInput.prototype.init = function(entry, node) {
	dc.pui.controls.Input.prototype.init.call(this, entry, node);

	var input = this;

	$(node).on("keyup", function(e) {
		if ((e.keyCode || e.which) != '9') 		// don't validate on tab key
			input.validate();
	});

	$(node).closest('.dc-field').find('.dc-input-hint').click(function(e) {
		input.setValue($(this).text());
	});
};


dc.pui.controls.Range = function(entry, node) {
	this.init(entry, node);
};

dc.pui.controls.Range.prototype = new dc.pui.controls.Input();

dc.pui.controls.Range.prototype.init = function(entry, node) {
	dc.pui.controls.Input.prototype.init.call(this, entry, node);

	var input = this;

	$(node).on("keyup", function(e) {
		if ((e.keyCode || e.which) != '9') 		// don't validate on tab key
			input.validate();
	});

	$(node).on('input change', function(e) {
		$(this).closest('div').find('.dc-addon-info').text(this.value);
	});
};

dc.pui.controls.Range.prototype.setValue = function(value) {
	value = dc.util.String.toString(value);

	if (!value)
		value = '';

	$('#' + this.Id).val(value);

	$('#' + this.Id).closest('div').find('.dc-addon-info').text(value);
};

dc.pui.controls.LabelInput = function(entry, node) {
	this.init(entry, node);
};

dc.pui.controls.LabelInput.prototype = new dc.pui.controls.Input();

dc.pui.controls.LabelInput.prototype.setValue = function(value) {
	value = dc.util.String.toString(value);

	if (!value)
		value = '';

	$('#' + this.Id).html(value);
};

dc.pui.controls.LabelInput.prototype.getValue = function() {
	var val = $('#' + this.Id).text();

	if (dc.util.Struct.isEmpty(val))
		val = null;

	return val;
};


dc.pui.controls.Select = function(entry, node) {
	this.init(entry, node);
};

dc.pui.controls.Select.prototype = new dc.pui.controls.Input();

dc.pui.controls.Select.prototype.init = function(entry, node) {
	dc.pui.controls.Input.prototype.init.call(this, entry, node);

	$(node).on("click focusout keyup", this, function(e) { e.data.validate(); });
};

dc.pui.controls.Select.prototype.setValue = function(value) {
	value = dc.util.String.toString(value);

	if (!value)
		value = 'NULL';

	$('#' + this.Id).val(value);

	this.LastValue = value;
};

dc.pui.controls.Select.prototype.getValue = function() {
	var val = $('#' + this.Id).val();

	if ((val == 'NULL') || dc.util.Struct.isEmpty(val))
		val = null;

	return val;
};

dc.pui.controls.Select.prototype.add = function(values, sel) {
	for (var i = 0; i < values.length; i++) {
		var opt = values[i];
		var val = dc.util.Struct.isEmpty(opt.Value) ? 'NULL' : opt.Value;
		var oval = (val == 'NULL') ? null : val;

		var $on = $('<option>').attr('value', val).text(opt.Label);

		if (sel) {
			if (sel == oval)
				$on.attr('selected', 'selected');
		}
		else {
			if (this.LastValue) {
				if (this.LastValue == oval)
					$on.attr('selected', 'selected');
			}
			else if (this.OriginalValue == oval) {
				$on.attr('selected', 'selected');
			}
		}

		$('#' + this.Id).append($on);
	}
};


dc.pui.controls.Checkbox = function(entry, node) {
	this.init(entry, node);
};

dc.pui.controls.Checkbox.prototype = new dc.pui.controls.Input();

dc.pui.controls.Checkbox.prototype.init = function(entry, node) {
	dc.pui.controls.Input.prototype.init.call(this, entry, node);

	this.DefaultValue = dc.util.Boolean.toBoolean($(node).find('input').attr('data-checked'));

	// override the default type
	if (this.DataType == 'String') {
		this.DataType = 'Boolean';
		this.Schema = dc.schema.Manager.resolveType(this.DataType);
	}

	$(node).find('input').on("click focusout keyup", this, function(e) { e.data.validate(); });
};

dc.pui.controls.Checkbox.prototype.setValue = function(value) {
	value = dc.util.Boolean.toBoolean(value);

	$('#' + this.Id).find('input').prop('checked',value);
};

dc.pui.controls.Checkbox.prototype.getValue = function() {
	return $('#' + this.Id).find('input').prop('checked');
};


dc.pui.controls.YesNo = function(entry, node) {
	this.init(entry, node);
};

dc.pui.controls.YesNo.prototype = new dc.pui.controls.Checkbox();

dc.pui.controls.YesNo.prototype.init = function(entry, node) {
	dc.pui.controls.Checkbox.prototype.init.call(this, entry, node);

	this.DefaultValue = dc.util.Boolean.toBoolean($(node).closest('div.dc-field').attr('value'));
};

dc.pui.controls.YesNo.prototype.setValue = function(value) {
	value = dc.util.Boolean.toBoolean(value) ? 'true' : 'false';

	$('#' + this.Id + '-' + dc.util.Hex.toHex(value)).prop('checked',true);
};

dc.pui.controls.YesNo.prototype.getValue = function() {
	return ($('#' + this.Id).find('input:checked').val() === 'true');
};


dc.pui.controls.RadioGroup = function(entry, node) {
	this.init(entry, node);
};

dc.pui.controls.RadioGroup.prototype = new dc.pui.controls.Input();

dc.pui.controls.RadioGroup.prototype.init = function(entry, node) {
	dc.pui.controls.Input.prototype.init.call(this, entry, node);

	this.DefaultValue = $(node).closest('div.dc-field').attr('value');

	if (! this.DefaultValue)
		this.DefaultValue = $(node).find('input[data-checked="true"]').val();  // first

	$(node).find('input').on("click focusout keyup", this, function(e) { e.data.validate(); });
};

dc.pui.controls.RadioGroup.prototype.setValue = function(value) {
	value = dc.util.String.toString(value);

	if (!value)
		value = 'NULL';

	$('#' + this.Id + '-' + dc.util.Hex.toHex(value)).prop('checked',true);
};

dc.pui.controls.RadioGroup.prototype.getValue = function() {
	var val = $('#' + this.Id).find('input:checked').val();

	if ((val == 'NULL') || dc.util.Struct.isEmpty(val))
		val = null;

	return val;
};

dc.pui.controls.RadioGroup.prototype.removeAll = function() {
	$('#' + this.Id).empty();
};

dc.pui.controls.RadioGroup.prototype.add = function(values) {
	for (var i = 0; i < values.length; i++) {
		var opt = values[i];
		var val = dc.util.Struct.isEmpty(opt.Value) ? 'NULL' : opt.Value;
		var tval = (val == 'NULL') ? null : val;
		var id = this.Id + '-' + dc.util.Hex.toHex(val);

		var $ctrl = $('<div class="dc-radio"></div>');

		var $cbox = $('<input type="radio" id="' + id + '" value="' + value + '" name="' + this.Field + '" />');

		if (this.OriginalValue == tval)
			$cbox.prop('checked', true);

		$cbox.on("click focusout keyup", this, function(e) { e.data.validate(); });

		$ctrl.append($cbox);

		if (opt.Label) {
			var $clbl = $('<label for="' + id +
					'" class="dc-input-button"><i aria-hidden="true" class="fa fa-circle"></i> <i aria-hidden="true" class="fa fa-check"></i> ' +
					opt.Label + '</label>');

			$ctrl.append($clbl);
		}

		$('#' + this.Id).append($ctrl);
	}
}


dc.pui.controls.ListInput = function(entry, node) {
	this.init(entry, node);
};

dc.pui.controls.ListInput.prototype = new dc.pui.controls.Input();

dc.pui.controls.ListInput.prototype.init = function(entry, node) {
	dc.pui.controls.Input.prototype.init.call(this, entry, node);

	this.DefaultValue = [ ];
	this.OriginalValue = [ ];

	// override the default type
	if (this.DataType == 'String') {
		this.DataType = 'AnyList';
		this.Schema = dc.schema.Manager.resolveType(this.DataType);
	}
};

dc.pui.controls.ListInput.prototype.isChanged = function() {
	var values = this.getValue();

	if (values.length != (this.OriginalValue ? this.OriginalValue.length : 0))
		return true;

	for (var i = 0; i < values.length; i++) {
		var val = values[i];
		var contains = false;

		for (var i2 = 0; i2 < this.OriginalValue.length; i2++) {
			var val2 = this.OriginalValue[i2];

			if (val2 == val) {
				contains = true;
				break;
			}
		}

		if (!contains)
			return true;
	}

	return false;
}


dc.pui.controls.CheckGroup = function(entry, node) {
	this.init(entry, node);
};

dc.pui.controls.CheckGroup.prototype = new dc.pui.controls.ListInput();

dc.pui.controls.CheckGroup.prototype.init = function(entry, node) {
	dc.pui.controls.ListInput.prototype.init.call(this, entry, node);

	$(node).find('input').on("click focusout keyup", this, function(e) { e.data.validate(); });
};

dc.pui.controls.CheckGroup.prototype.setValue = function(values) {
	if (!dc.util.Struct.isList(values))
		values = [];

	for (var i = 0; i < values.length; i++)
		$('#' + this.Id + '-' + dc.util.Hex.toHex(values[i])).prop('checked',true);
};

dc.pui.controls.CheckGroup.prototype.getValue = function() {
	return $('#' + this.Id).find('input[name=' + this.Field + ']:checked').map(function() { return this.value; }).get();
};

dc.pui.controls.CheckGroup.prototype.removeAll = function() {
	$('#' + this.Id).empty();
};

dc.pui.controls.CheckGroup.prototype.add = function(values) {
	for (var i = 0; i < values.length; i++) {
		var opt = values[i];
		var id = this.Id + '-' + dc.util.Hex.toHex(opt.Value);

		var $ctrl = $('<div class="dc-checkbox"></div>');

		var $cbox = $('<input type="checkbox" id="' + id + '" value="' + opt.Value + '" name="' + this.Field + '" />');

		if (this.OriginalValue) {
			for (var o = 0; o < this.OriginalValue.length; o++) {
				if (this.OriginalValue[o] == opt.Value) {
					$cbox.prop('checked', true);
					break;
				}
			}
		}

		$cbox.on("click focusout keyup", this, function(e) { e.data.validate(); });

		$ctrl.append($cbox);

		if (opt.Label) {
			var $clbl = $('<label for="' + id +
					'" class="dc-input-button"><i aria-hidden="true" class="fa fa-square"></i> <i aria-hidden="true" class="fa fa-check"></i> ' +
					opt.Label + '</label>');

			$ctrl.append($clbl);
		}

		$('#' + this.Id).append($ctrl);
	}
}


dc.pui.controls.Uploader = function(entry, node) {
	this.init(entry, node);

	this.Values = [ ];
	this.Files = [ ];
};

dc.pui.controls.Uploader.prototype = new dc.pui.controls.ListInput();

dc.pui.controls.Uploader.prototype.init = function(entry, node) {
	var ctrl = this;

	dc.pui.controls.ListInput.prototype.init.call(this, entry, node);

	$(node)
		.on("click focusout keyup", this, function(e) { e.data.validate(); })
		.on("change", this, function(e) {
			if (! $(node).attr('multiple'))
				ctrl.removeAll();

			ctrl.addFiles(this.files);

			e.data.validate();
		});

	var upctrl = $(node).closest('.dc-uploader');
	var uparea = upctrl.find('.dc-uploader-list-area');

	uparea
		.on('dragstart dragover dragend', function(e) {
			e.preventDefault();
			e.stopPropagation();
		})
		.on('dragenter', function(e) {
			$(upctrl).addClass('dragenter');
			e.preventDefault();
			e.stopPropagation();
		})
		.on('dragleave', function(e) {
			$(upctrl).removeClass('dragenter');

			e.preventDefault();
			e.stopPropagation();
		})
		.on('drop', function(e) {
			var droppedFiles = e.originalEvent.dataTransfer.files;

			$(upctrl).removeClass('dragenter');

			if ($(node).attr('multiple')) {
				ctrl.addFiles(droppedFiles);
			}
			else {
				ctrl.removeAll();
				ctrl.addFiles([ droppedFiles[0] ]);
			}

			e.preventDefault();
			e.stopPropagation();
		});

	var uplist = upctrl.find('.dc-uploader-listing');

	uplist.dcappend(
		$('<table>')
			.addClass('dc-table-break-wide dc-table dc-table-reflow dc-table-stripe')
			.dcappend(
				$('<tbody>')
			)
	);
};

dc.pui.controls.Uploader.prototype.setValue = function(values) {
	// do nothing - only file chooser can do this
};

dc.pui.controls.Uploader.prototype.getValue = function() {
	return this.Values;
};

dc.pui.controls.Uploader.prototype.remove = function(pos) {
	this.Values.splice(pos, 1);
	this.Files.splice(pos, 1);

	$('#fld' + this.Id + ' .dc-uploader-listing tbody tr').eq(pos).remove();
};

dc.pui.controls.Uploader.prototype.renameFile = function(pos, fname) {
	fname = dc.util.File.toCleanFilename(fname);

	this.Values[pos] = fname;

	$('#fld' + this.Id + ' .dc-uploader-listing tbody tr').eq(pos).find('td').eq(0).text(fname);
};

dc.pui.controls.Uploader.prototype.removeAll = function() {
	this.Values = [ ];
	this.Files = [ ];

	$('#fld' + this.Id + ' .dc-uploader-listing').empty();
};

dc.pui.controls.Uploader.prototype.getUploads = function(pre) {
	if (! pre)
		pre = '';

	var files = [ ];

	for (var i = 0; i < this.Files.length; i++) {
		files.push({
			File: this.Files[i],
			Name: pre + this.Values[i]
		});
	}

	return files;
};

dc.pui.controls.Uploader.prototype.onAfterSave = function(e) {
	var task = e.Task;

	if (! e.Task.Store.Form.Managed) {
		task.resume();
		return;
	}

	console.log('onAfterSave: ' + this.Field)

	// TODO progress bars

	var files = [ ];

	for (var i = 0; i < this.Files.length; i++) {
		files.push({
			File: this.Files[i],
			Name: this.Values[i]
		});
	}

	var uploadtask = dc.transfer.Util.uploadFiles(files, 'ManagedForms', e.Data.Token);

	uploadtask.ParentTask = task;
	uploadtask.ParentStep = e.Step;

	if (! e.Step.Tasks)
		e.Step.Tasks = [ ];

	e.Step.Tasks.push(uploadtask);

	//uploadtask.run();

	dc.pui.Popup.await(dc.lang.Dict.tr('_code_900'), function() {
		task.resume();		// resume the Save task
	}, dc.lang.Dict.tr('_code_901'), uploadtask);

};

dc.pui.controls.Uploader.prototype.addFiles = function(values) {
	var ctrl = this;

	var maxsize = dc.util.Number.toNumberStrict($('#fld' + this.Id).attr('data-max-size'));
	var calcsize = 0;

	// 0 means no limit
	if (maxsize > 0) {
		for (var i = 0; i < ctrl.Files.length; i++) {
			calcsize += ctrl.Files[i].size;
		}

		for (var i = 0; i < values.length; i++) {
			calcsize += values[i].size;
		}

		if (calcsize > maxsize) {
			dc.pui.Popup.alert(dc.lang.Dict.tr('_code_902'));
			return;
		}
	}

	for (var i = 0; i < values.length; i++) {
		var file = values[i];

		var fname = dc.util.File.toCleanFilename(file.name);

		ctrl.Files.push(file);
		ctrl.Values.push(fname);

		var $actions = $('<td>').append(
			$('<a>')
				.attr('href', '#')
				.attr('aria-label', 'edit name')
				.dcappend(dc.util.Icon.use('fas-pencil-alt')
					.addClass('fa5-lg')
				)
				.click(function(e) {
					var idx = $(e.currentTarget).closest('tr').index();

					dc.pui.Popup.input(dc.lang.Dict.tr('_code_903'), function(newname) {
						if (newname) {
							ctrl.renameFile(idx, newname);
						}
					}, dc.lang.Dict.tr('_code_904'), dc.lang.Dict.tr('_code_905'), ctrl.Values[idx]);

					e.preventDefault();
					return false;
				}),
			' &nbsp; ',
			$('<a>')
				.attr('href', '#')
				.attr('aria-label', 'remove')
				.dcappend(dc.util.Icon.use('fas-times')
					.addClass('fa5-lg')
				)
				.click(function(e) {
					ctrl.remove($(e.currentTarget).closest('tr').index());

					e.preventDefault();
					return false;
				}),
			' &nbsp; '
		);

		if (dc.util.String.endsWith(fname, '.jpg') || dc.util.String.endsWith(fname, '.jpg') || dc.util.String.endsWith(fname, '.png')) {
			$actions.dcappend(
				$('<a>')
					.attr('href', '#')
					.attr('aria-label', 'preview')
					.dcappend(dc.util.Icon.use('fas-eye')
						.addClass('fa5-lg')
					)
					.click(function(e) {
						var idx = $(e.currentTarget).closest('tr').index();

						var fileReader = new FileReader()

						fileReader.addEventListener("load", function () {
							var img = new Image();
							img.src = fileReader.result;

							var show = {
								Path: '/',
								StartPos: 0,
								Images: [ { Source: img } ]
							};

							dc.pui.FullScreen.loadPage('/dcw/view-image', {
								View: show
							});

						}, false);

						fileReader.readAsDataURL(ctrl.Files[idx]);

						e.preventDefault();
						return false;
					})
			);
		}

		$('#fld' + this.Id + ' .dc-uploader-listing tbody').dcappend(
			$('<tr>')
				.addClass('dc-uploader-entry')
				.dcappend(
					$('<td>').text(fname),
					$actions
				)
		);
	}
}

// ------------------- end Controls -------------------------------------------------------

dc.pui.FormManager = {
	Forms: { },
	openWizard: function(name) {
		var script = document.createElement('script');
		script.src = '/dcm/forms/loadwiz.js?form=' + name + '&nocache=' + dc.util.Crypto.makeSimpleKey();
		script.id = 'wiz' + name.replace(/\//g,'.');
		script.async = true;
		document.head.appendChild(script);
	},
	registerForm: function(name, menu) {
		var form = new dc.pui.FormStore();
		form.init(name, menu);
		this.Forms[name] = form;
	},
	getForm: function(name) {
		return this.Forms[name];
	}
};

dc.pui.FormStore = function(name) {
	this.init(name);
}

dc.pui.FormStore.prototype = {
	init: function(name, menu) {
		$.extend(this, {
			Name: name,
			Loaded: false,
			Menu: menu,
			DataType: menu ? dc.pui.Apps.Menus[menu].DataType : null,
			Data: { }
		});

		this.load();
	},
	getSection: function(name) {
		return this.Data[name];
	},
	setSection: function(name, data) {
		this.Data[name] = data;
		this.save();
	},
	setData: function(data) {
		this.Data = data;
		this.save();
	},
	empty: function() {
		this.Data = { };

		return this.Data;
	},

	load: function() {
		// load once per page
		if (this.Loaded)
			return;

		this.empty();

		// load from localStorage
		try {
			var plain = localStorage.getItem(this.Name);

			if (plain) {
				this.Data = JSON.parse(plain);
			}
		}
		catch (x) {
		}

		this.Loaded = true;

		return this.Data;
	},

	// store the cart info temporarily, used from page to page during session
	save: function() {
		try {
			var plain = JSON.stringify( this.Data );
			localStorage.setItem(this.Name, plain);
		}
		catch (x) {
		}
	},

	// store the cart info temporarily, used from page to page during session
	clear: function() {
		this.empty();

		try {
			localStorage.removeItem(this.Name);
		}
		catch (x) {
		}
	},
	validate: function() {
		if (dc.schema.Manager.validate(this.Data, this.DataType).Code) {
			dc.pui.Popup.alert('Missing or invalid data on form.');

			/* TODO
			dc.pui.Popup.alert('Missing or invalid data on ' + tabs[i].Title + ' form.', function() {
				dc.pui.App.loadTab(tabs[i].Alias);
			});
			*/

			return false;
		}

		return true;
	}
};

// ------------------- end Forms -------------------------------------------------------

window.dataLayer = window.dataLayer || [];
function gtag(){dataLayer.push(arguments);}
gtag('js', new Date());

function loadGA() {
	gtag('config', dc.handler.settings.ga);

	var script = document.createElement('script');
	script.src = 'https://www.googletagmanager.com/gtag/js?id=' +
		dc.handler.settings.ga
	script.async = true;
	script.defer = true;
	document.head.appendChild(script);
}

function loadCaptcha(service, sitekey, callback) {
	if (! sitekey && dc.handler && dc.handler.settings && dc.handler.settings.captcha)
		sitekey = dc.handler.settings.captcha;

	if (! callback)
		callback = function() { };

	window.dcCaptchaCallback = callback;

	var rpath = (service == 'hCAPTCHA')
			? 'https://hcaptcha.com/1/api.js'
			: 'https://www.google.com/recaptcha/api.js';

	sitekey = (service == 'hCAPTCHA')
			? 'explicit'
			: sitekey;

	if (dc.pui.Loader.Libs[rpath]) {
		window.dcCaptchaCallback();
		return;
	}

	var script = document.createElement('script');
	script.src = rpath + '?onload=dcCaptchaCallback&render=' + sitekey;
	script.async = true;
	script.defer = true;
	document.head.appendChild(script);
}

/*
$.fn.dcVal = function() {
    var v = this.attr('Value');

    if (!v)
    	v = this.attr('value');

    if (!v)
    	v = this.text();

    return v;
};

$.fn.dcMDUnsafe = function(txt) {
	marked.setOptions({
	  renderer: new marked.Renderer(),
	  gfm: true,
	  tables: true,
	  breaks: true,
	  pedantic: false,
	  sanitize: false,
	  smartLists: true,
	  smartypants: false
	});

	this.html(marked(txt));
}

$.fn.dcMDSafe = function(txt) {
	marked.setOptions({
	  renderer: new marked.Renderer(),
	  gfm: true,
	  tables: true,
	  breaks: true,
	  pedantic: false,
	  sanitize: true,
	  smartLists: true,
	  smartypants: false
	});

	this.html(marked(txt));
}
*/
