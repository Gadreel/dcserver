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

// TODO test all callPageFunc for try / catch and async functioning

dc.pui = {
};

// just a place to store uniquely keyed data
dc.pui.Store = {
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

		// async, but we don't care at this point
		this.loadPageAdv(options);
	},
	// call with separate args or with a single "options" record
	loadPageAsync: async function(page, params, replaceState, urlparams) {
		return new Promise(async function(resolve, reject) {
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
				options.Callback = function() {
					resolve();
				};

				if (hpage.length)
					options.TargetElement = hpage[1];
			}

			await this.loadPageAdv(options);
		});
	},
	loadPageAdv: async function(options) {
		const currLayer = this;

		if (! options || ! options.Name)
			return;

		const loader = dc.pui.Loader;

		// layer history
		const oldentry = currLayer.Current;

		if (oldentry) {
			const oldpage = loader.Pages[oldentry.Name];

			if (oldpage.Async)
				await oldentry.freezeAsync();
			else
				oldentry.freeze();

			currLayer.History.push(oldentry);
		}

		options.Layer = currLayer;

		const entry = new dc.pui.PageEntry(options);
		loader.LoadPageId = entry.Id;

		// if page is already loaded then show it
		if (loader.Pages[options.Name] && ! loader.Pages[options.Name].NoCache && ! loader.StalePages[options.Name]) {
			loader.resumePageLoad();	// async, but no followup needed
			return;
		}

		delete loader.StalePages[options.Name];		// no longer stale

		let ssrc = options.Name + '?_dcui=dyn';

		if (options.UrlParams)
			ssrc += '&' + options.UrlParams;

		const script = document.createElement('script');

		if (options.Name.endsWith('_mjs'))
			script.type = 'module';

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

			if (this == dc.pui.Loader.MainLayer) {
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
		var page = loader.Pages[layer.Current.Name];

		if (firstload) {
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

		const pageLoadedCallback = function() {
			if (layer.Current.Callback)
				layer.Current.Callback.call(layer.Current);
		};

		if (page.Async)
			layer.Current.onLoadAsync().then(pageLoadedCallback);
		else
			layer.Current.onLoad(pageLoadedCallback);
	},

	refreshPage: function(clean) {
		var options = this.Current;

		options.Loaded = false;

		if (clean) {
			options.onDestroy();

			options.Callback = null;
			options.Store = { };
			options.Forms = { };
		}

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
				else if (! dc.pui.Loader.Layers[i].ManualVisibility) {
					$(dc.pui.Loader.Layers[i].ContentShell).attr('aria-hidden', 'true');
					$(dc.pui.Loader.Layers[i].ContentShell).attr('inert', 'true');
				}
			}

			if (need)
				dc.pui.Loader.addLayer(this);

			if (! this.ManualVisibility) {
				$(this.ContentShell).attr('aria-hidden', 'false');
				$(dc.pui.Loader.MainLayer.Content).attr('aria-hidden', 'true');

				$(this.ContentShell).removeAttr('inert');
				$(dc.pui.Loader.MainLayer.Content).attr('inert', 'true');

				$(this.ContentShell).show();
				$('body').addClass('dc-hide-scroll');
			}
		}
		else {
			if (! this.ManualVisibility) {
				$(this.Content).removeAttr('inert');

				$(this.Content).attr('aria-hidden', 'false');
				$('body').removeClass('dc-hide-scroll');
			}
		}

		//if (this.LastFocus)
		//	this.LastFocus.focus();
	},

	close: function() {
		this.clearHistory();

		if (this != dc.pui.Loader.MainLayer) {
			if (! this.ManualVisibility) {
				$(this.ContentShell).attr('aria-hidden', 'true');
				$(this.ContentShell).attr('inert', 'true');
				$(this.ContentShell).hide();
			}

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

dc.pui.layer.Main.prototype.loadPageAdv = async function(options) {
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

dc.pui.layer.Main.prototype.initPage = async function() {
	var entry = new dc.pui.PageEntry({
		Name: decodeURI(window.location.pathname),
		Href: decodeURI(window.location.href),
		Layer: this
	});

	dc.pui.Loader.LoadPageId = entry.Id;
	await dc.pui.Loader.resumePageLoad();
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
		$('body').append('<div id="dcuiDialog" role="dialog" aria-modal="true" class="dcuiLayerShell"><div id="dcuiDialogPane"></div></div>');
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
		var dbox = $('<div id="dcuiAlert" role="dialog" aria-modal="true" class="dcuiLayerShell"></div>');

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

dc.pui.layer.Widget = function(uiid, uiidin) {
	this.ManualVisibility = true;
	this.init(uiid, uiidin);
};

dc.pui.layer.Widget.prototype = new dc.pui.layer.Base();

dc.pui.layer.Widget.prototype.open = function() {
	//var del = $(this.ContentShell).show().removeAttr('inert');

	dc.pui.layer.Base.prototype.open.call(this);

	//$(dc.pui.Loader.MainLayer.Content).attr('inert', 'true');
	$('body').removeClass('dc-hide-scroll');
};

dc.pui.layer.Widget.prototype.close = function() {
	var del = $(this.ContentShell).hide();

	dc.pui.layer.Base.prototype.close.call(this);
};

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
			.attr('role', 'dialog')
			.attr('aria-modal', 'true')
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
	}

	dc.pui.layer.Base.prototype.open.call(this);
};

dc.pui.layer.App.prototype.loadTab = async function(tabalias, params) {
	var tinfo = this.getTabInfo(tabalias);

	if (! tinfo)
		return;

	if (this.Current) {
		const oldpage = dc.pui.Loader.Pages[this.Current.Name];

		if (oldpage.Async)
			await this.Current.freezeAsync();
		else
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
		$('body').append('<div id="dcuiSimpleApp" role="dialog" aria-modal="true" class="dcuiLayerShell"><div id="dcuiSimpleAppPane"></div></div>');
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
		var dbox = $('<div id="dcuiFullScreen" role="dialog" aria-modal="true" class="dcuiLayerShell dcuiContentLayer"></div>');

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

	init: async function() {
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

		document.addEventListener('keydown', function(e) {
	    if (e.key === 'Escape' || e.keyCode === 27) {
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

		if (dc.handler && dc.handler.settings && dc.handler.settings.fbpixel)
			loadFBPixel();

		await dc.comm.init();

		if (dc.handler && dc.handler.init)
			dc.handler.init();

		if (dc.handler && dc.handler.initAsync)
			await dc.handler.initAsync();

		if (dc.util.Web.isTouchDevice())
			$('html > head').append('<style>#dcuiDialogPane { margin-top: 36px; }</style>');

		if (dc.user.isVerified()) {
			await dc.pui.Loader.MainLayer.initPage();
			return;
		}

		var creds = dc.user.loadRemembered();

		if (! creds) {
			await dc.pui.Loader.MainLayer.initPage();
			return;
		}

		// TODO figure out how this works with sign in handler - probably need to move the reload below into the default handler and make it go after initPage
		await dc.user.signinAsync( { Username: creds.Username, Password: creds.Password }, true);

		if (dc.user.isVerified()) {
			window.location.reload(true);
			return;
		}

		await dc.pui.Loader.MainLayer.initPage();
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
	resumePageLoad: async function(pagename) {
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
			if (! l.Current || l.ManualVisibility)
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

	alertAsync: async function(msg, title) {
		return new Promise((resolve, reject) => {
			dc.pui.Alert.loadPage('/dcw/alert', { Message: msg, Callback: function() { resolve(); }, Title: title })
		});
	},

	await: function(msg, callback, title, task) {
		dc.pui.Alert.loadPage('/dcw/await', { Message: msg, Callback: callback, Title: title, Task: task })
	},

	awaitAsync: async function(msg, title, task) {
		return new Promise((resolve, reject) => {
			dc.pui.Alert.loadPage('/dcw/await', { Message: msg, Callback: function(ctask) { resolve(ctask); }, Title: title, Task: task })
		});
	},

	confirm: function(msg, callback, title) {
		dc.pui.Alert.loadPage('/dcw/confirm', { Message: msg, Callback: callback, Title: title })
	},

	confirmAsync: async function(msg, title) {
		return new Promise((resolve, reject) => {
			dc.pui.Alert.loadPage('/dcw/confirm', { Message: msg, Callback: function(confirm) { resolve(confirm); }, Title: title })
		});
	},

	input: function(msg, callback, title, label, value) {
		dc.pui.Alert.loadPage('/dcw/input', { Message: msg, Callback: callback, Title: title, Label: label, Value: value })
	},

	inputAsync: async function(msg, title, label, value) {
		return new Promise((resolve, reject) => {
			dc.pui.Alert.loadPage('/dcw/input', { Message: msg, Callback: function(value) { resolve(value); }, Title: title, Label: label, Value: value })
		});
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

	onLoadAsync: async function() {
		console.log('using async page loader');

		try {
			const page = dc.pui.Loader.Pages[this.Name];
			const entry = this;

			// PRE-LOADS

			const loadFuncs = [ ];

			for (let i = 0; i < page.LoadFunctions.length; i++) {
				const func = page.LoadFunctions[i];

				if (dc.util.String.isString(func))
					loadFuncs.push(entry.callPageFunc(func));
				else
					loadFuncs.push(func.call(entry, event));
			}

			// the first fail will cease all the promises and throw an Exception
			await Promise.all(loadFuncs);

			// LOAD

			/*
			entry.LastFocus = code can see and change the focus
			*/

			await entry.callPageFunc(entry.Loaded ? 'Thaw' : 'Load');

			//await entry.callPageFunc('LoadTest');

			// FOCUS

			try {
				const cs = $(entry.Layer.ContentShell).get(0);
				const x = cs.scrollLeft;
				const y = cs.scrollTop;

				if (entry.LastFocus)
					entry.LastFocus.focus();
				else if (entry.Layer.Content != '#dcuiMain')
					entry.query('main').focus();

				$(cs).scrollTop(y);
			}
			catch (x) {
				// do nothing if focus fails
			}

			// LOAD FORMS

			const loadFormFuncs = [ ];

			for (let formname of Object.keys(entry.Forms)) {
				loadFormFuncs.push(entry.Forms[formname][entry.Loaded ? 'thawAsync' : 'loadAsync']());
			}

			// the first fail will cease all the promises and throw an Exception
			await Promise.all(loadFormFuncs);

			// DONE page load

			entry.Loaded = true;
			entry.Frozen = false;

			console.log('async page loader success');
		}
		catch(x) {
			console.log('async page loader failure: ' + x);

			if (x.stack)
				console.log(x.stack);

			await dc.pui.Popup.alertAsync(x.message);
		}
	},

	reload: function() {
		this.Loaded = false;

		var page = dc.pui.Loader.Pages[this.Name];

		// return the promise if asnyc
		if (page.Async)
			return this.onLoadAsync();

		this.onLoad();
	},

	hasPageFunc: function(method) {
		var page = dc.pui.Loader.Pages[this.Name];
		return (page && page.Functions[method]);
	},

	callPageFunc: function(method) {
		const page = dc.pui.Loader.Pages[this.Name];

		// if this is an async function, then you are returning a promise
		// if not then you are returning a value from the function (if any)
		// note that 'on*' functions are always coded as sync and should never return anything
		if (page && page.Functions[method])
			return page.Functions[method].apply(this, Array.prototype.slice.call(arguments, 1));

		return page.Async ? Promise.resolve(undefined) : null;		// for sync should have been return undefined;
	},

	allocatePageFuncTimeout: function() {
		const entry = this;
		const loadtracker = new dc.lang.promise.TimeoutPromise();

		loadtracker.TimeoutAllocAdapter = function(period, code) {
			this.Tid = entry.allocateTimeout({
				Op: code,
				Period: period
			});

			return this.Tid;
		};

		return loadtracker;
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
		const fnames = Object.getOwnPropertyNames(this.Forms);

		if (fnames)
			return this.Forms[fnames[0]];

		return null;
	},

	formQuery: function(name) {
		const frm = this.form(name);

		if (frm)
			return $('#' + frm.Id);

		return $('#__unreal');
	},

	freeze: function() {
		var entry = this;
		var page = dc.pui.Loader.Pages[entry.Name];

		entry.FreezeTop = $(entry.Layer.ContentShell).scrollTop();

		entry.callPageFunc('Freeze');

		for (let formname of Object.keys(entry.Forms)) {
			entry.Forms[formname].freeze();
		}

		entry.Frozen = true;
	},

	freezeAsync: async function() {
		const entry = this;
		const page = dc.pui.Loader.Pages[entry.Name];

		entry.FreezeTop = $(entry.Layer.ContentShell).scrollTop();

		await entry.callPageFunc('Freeze');

		for (let formname of Object.keys(entry.Forms)) {
			entry.Forms[formname].freeze();
		}

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

		console.log('destroy: ' + this.Name);

		// clear the old timers
		for (var x = 0; x < this.Timers.length; x++) {
			var tim = this.Timers[x];

			if (!tim)
				continue;

			console.log('timeout cleared on destroy in dc.app: ' + tim.Tid);

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

				console.log('timeout cleared and called in dc.app: ' + options.Tid);

				options.Op.call(this, options.Data);
			},
			options.Period);

		this.Timers.push(options);

		console.log('timeout allocated in dc.app: ' + options.Tid);

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
		const entry = this;
		const id = $(el).closest('form').attr('id');

		for (let formname of Object.keys(entry.Forms)) {
			if (entry.Forms[formname]?.Id == id)
				return entry.Forms[formname];
		}

		return null;
	},

	// list of results from validateForm
	validate: function() {
		const entry = this;
		const res = [ ];

		for (let formname of Object.keys(entry.Forms)) {
			res.push(entry.Forms[formname].validate());
		}

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

	callInputFunc: function(field, method) {
		var form = this;

		if (form.Inputs[field]) {
			var ret = null;
			var args = Array.prototype.slice.call(arguments, 2);

			args.unshift(form.Inputs[field].getNode());
			args.unshift(form.PageEntry);

			form.Inputs[field][method].apply(form.Inputs[field], args);
		}

		return ret;
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

	loadAsync: async function() {
		const form = this;

		if (! form.RecordOrder)
			return;

		// ALL LOADS - collect data phase

		const loadPromises = [ ];

		for (let i = 0; i < form.RecordOrder.length; i++) {
			const rec = form.RecordOrder[i];

			form.initChanges(rec);

			loadPromises.push(form.raiseEvent('LoadRecord', { Form: form, Record: rec }));
		}

		const values = await Promise.all(loadPromises);

		// ALL LOADS - present data phase

		for (let i = 0; i < form.RecordOrder.length; i++) {
			const rec = form.RecordOrder[i];
			const data = values[i];		// should be object

			if (data != undefined) {
				form.loadRecord({
					Record: rec,
					Data: data,
					AsNew: form.AsNew[rec]
				});
			}
		}

		// AFTER LOAD

		await form.raiseEvent('AfterLoad');
	},

	loadDefaultRecord: function(data, asNew) {
		return this.loadRecord( {
			Record: 'Default',
			Data: data,
			AsNew: asNew
		});
	},

	loadRecord: function(info) {
		const form = this;

		if (info.AsNew)
			form.AsNew[info.Record] = true;

		if (! info.Data)
			return;

		for (let inputname of Object.keys(form.Inputs)) {
			const iinfo = form.Inputs[inputname];

			if ((iinfo.Record == info.Record) && info.Data.hasOwnProperty(iinfo.Field)) {
				iinfo.setValue(info.Data[iinfo.Field]);
				iinfo.OriginalValue = info.Data[iinfo.Field];
			}
		}
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

	thawAsync: async function(callback) {
		const form = this;

		if (! form.RecordOrder)
			return;

		// ALL THAWS - collect data phase

		const thawPromises = [ ];

		for (let i = 0; i < form.RecordOrder.length; i++) {
			const rec = form.RecordOrder[i];

			const event = {
				Record: rec,
				Data: form.FreezeInfo[rec].Values,
				Originals: form.FreezeInfo[rec].Originals
			};

			thawPromises.push(form.raiseEvent('ThawRecord', event));
		}

		const values = await Promise.all(thawPromises);

		// ALL THAWS - present data phase

		for (let i = 0; i < form.RecordOrder.length; i++) {
			const rec = form.RecordOrder[i];
			const data = values[i] || form.FreezeInfo[rec].Values;		// should be object

			if (data != undefined) {
				form.thawRecord({
					Record: rec,
					Data: data,
					Originals: form.FreezeInfo[rec].Originals
				});
			}
		}

		await form.raiseEvent('AfterThaw');
	},

	thawRecord: function(info) {
		const form = this;

		if (! info.Data)
			return;

		for (let inputname of Object.keys(form.Inputs)) {
			const iinfo = form.Inputs[inputname];

			if ((iinfo.Record == info.Record) && info.Data.hasOwnProperty(iinfo.Field)) {
				if (iinfo.thawValue)
					iinfo.thawValue(info.Data[iinfo.Field]);
				else
					iinfo.setValue(info.Data[iinfo.Field]);

				iinfo.OriginalValue = info.Originals[iinfo.Field];
			}
		}
	},

	freeze: function() {
		const form = this;

		form.FreezeInfo = { };

		if (! form.RecordOrder)
			return;

		for (var i = 0; i < form.RecordOrder.length; i++)
			form.freezeRecord(form.RecordOrder[i]);
	},

	freezeRecord: function(recname) {
		const form = this;

		form.FreezeInfo[recname] = {
				Originals: { },
				Values: { }
		};

		for (let inputname of Object.keys(form.Inputs)) {
			const iinfo = form.Inputs[inputname];

			if (iinfo.Record == recname) {
				form.FreezeInfo[recname].Originals[iinfo.Field] = iinfo.OriginalValue

				if (iinfo.freezeValue)
					form.FreezeInfo[recname].Values[iinfo.Field] = iinfo.freezeValue();
				else
					form.FreezeInfo[recname].Values[iinfo.Field] = iinfo.getValue();
			}
		}
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

						task.Store.Form.raiseEvent('SaveRecordManaged', event);

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
	saveAsync: async function() {
		const form = this;

		if (! form.RecordOrder)
			return;

		let anyChanged = await form.raiseEvent('BeforeSave') || false;

		// ALL SAVES - send data phase

		const savePromises = [ ];
		const saveRecords = [ ];

		for (let i = 0; i < form.RecordOrder.length; i++) {
			const rec = form.RecordOrder[i];

			if (! await form.isChanged(rec))
				continue;

			saveRecords.push(rec);
			anyChanged = true;

			const event = {
				Form: form,
				Record: rec,
				Data: form.getChanges(rec)
			};

			if (form.Managed) {
				event.Title = '';

				for (let n = 0; n < form.TitleFields.length; n++) {
					const fname = form.TitleFields[n];

					if (event.Data[fname]) {
						if (event.Title.length != 0)
							event.Title += ' - ';

						event.Title += event.Data[fname];
					}
				}

				// this will block, but should not generally be used for long calls
				await form.raiseEvent('SaveRecordManaged', event);

				// TODO figure out the task.Store.Captcha equivalent
				let captcha = undefined;  // task.Store.Captcha ? task.Store.Captcha : 'xyz';

				if (! captcha && form.Captcha)
					captcha = form.Captcha.Token;

				savePromises.push(dc.comm.tryRemote('dcCoreServices.ManagedForm.Submit', {
					Form: form.Name,
					Captcha: captcha,
					Title: event.Title,
					Data: event.Data
				}));
			}
			else {
				savePromises.push(form.raiseEvent('SaveRecord', event));
			}
		}

		/* each should return object - containing data returned from SaveRecord
			Data: data
		*/
		const afterDataValues = await Promise.all(savePromises);

		// ALL SAVES - protect data phase

		for (let i = 0; i < saveRecords.length; i++) {
			const rec = saveRecords[i];

			form.clearChanges(rec);
		}

		// AFTER SAVE RECORDS

		const afterSavePromises = [ ];

		for (let i = 0; i < saveRecords.length; i++) {
			const rec = saveRecords[i];
			const data = afterDataValues[i] || { };		// should be object

			if (data != undefined) {
				var event = {
					Form: form,
					Record: rec,
					Data: data
				};

				// do before save record event
				afterSavePromises.push(form.raiseEvent('AfterSaveRecord', event));
			}
		}

		await Promise.all(afterSavePromises);

		// AFTER SAVE CONTROLS

		const afterSaveCtrlsPromises = [ ];

		for (let i = 0; i < saveRecords.length; i++) {
			const rec = saveRecords[i];
			const data = afterDataValues[i] || { };		// should be object

			for (let inputname of Object.keys(form.Inputs)) {
				const iinfo = form.Inputs[inputname];

				if ((iinfo.Record == rec) && iinfo.onAfterSaveAsync) {
					var event = {
						Form: form,
						Record: rec,
						Data: data
					};

					// control has to be async - return a promise
					afterSaveCtrlsPromises.push(iinfo.onAfterSaveAsync(event));
				}
			}
		}

		await Promise.all(afterSaveCtrlsPromises);

		// RECORDS COMPLETED

		const completePromises = [ ];

		for (let i = 0; i < saveRecords.length; i++) {
			const rec = saveRecords[i];
			const data = afterDataValues[i] || { };		// should be object

			if (data != undefined) {
				var event = {
					Form: form,
					Record: rec,
					Data: data
				};

				if (form.Managed) {
					// this will block, but should not generally be used for long calls
					await form.raiseEvent('AfterCompleteRecordManaged', event);

					if (event.Data.Token && event.Data.Uuid) {
						savePromises.push(dc.comm.tryRemote('dcCoreServices.ManagedForm.Complete', {
							Form: form.Name,
							Token: event.Data.Token,
							Uuid: event.Data.Uuid
						}));
					}
				}
				else {
					completePromises.push(form.raiseEvent('AfterCompleteRecord', event));
				}
			}
		}

		await Promise.all(completePromises);

		// AFTER SAVE

		var event = {
			Changed: anyChanged,
			DefaultSaved: false
		};

		if (form.Managed) {
			form.query('a[data-dc-tag="dcf.SubmitButton"],a[data-dc-tag="dcf.SubmitCaptchaButton"]').addClass('pure-button-disabled');
			event.DefaultSavedMessage = 'Form successfully submitted.';
			event.DefaultSaved = true;

			gtag('event', 'Submit', {
				'event_category': 'Form',
				'event_label': form.Name
			});
		}

		await form.raiseEvent('AfterSave', event);

		if (event.DefaultSaved) {
			dc.pui.Apps.Busy = false;

			var msg = anyChanged ? 'Saved' : 'No changes, nothing to save.';

			if (event.DefaultSavedMessage)
				msg = event.DefaultSavedMessage;

			await dc.pui.Popup.alertAsync(msg);
		}
	},

	initChanges: function(recname) {
		const form = this;

		for (let inputname of Object.keys(form.Inputs)) {
			const iinfo = form.Inputs[inputname];

			if (iinfo.Record == recname) {
				iinfo.setValue(iinfo.DefaultValue);
				iinfo.OriginalValue = iinfo.DefaultValue;
			}
		}
	},

	isDefaultChanged: function() {
		return this.isChanged('Default');
	},

	isChanged: function(recname) {
		const form = this;

		if (form.AsNew[recname] || form.AlwaysNew)
			return true;

		for (let inputname of Object.keys(form.Inputs)) {
			const iinfo = form.Inputs[inputname];

			if ((iinfo.Record == recname) && iinfo.isChanged())
				return true;
		}

		var page = dc.pui.Loader.Pages[form.PageEntry.Name];

		var event = {
			Record: recname,
			Changed: false
		};

		// return a promise if async
		if (page.Async)
			return form.raiseEvent('IsRecordChanged', event);

		// sync approach - set a flag in event
		form.raiseEvent('IsRecordChanged', event);

		return event.Changed;
	},

	getDefaultChanges: function() {
		return this.getChanges('Default');
	},

	getChanges: function(recname) {
		const form = this;
		const changes = { };
		const asNew = (form.AsNew[recname] || form.AlwaysNew);

		for (let inputname of Object.keys(form.Inputs)) {
			const iinfo = form.Inputs[inputname];

			if (iinfo.Record != recname)
				continue;

			if (asNew || iinfo.isChanged())
				changes[iinfo.Field] = iinfo.getValue();
		}

		return changes;
	},

	clearDefaultChanges: function() {
		this.clearChanges('Default');
	},

	clearChanges: function(recname) {
		const form = this;

		form.AsNew[recname] = false;
		form.FreezeInfo = null;

		for (let inputname of Object.keys(form.Inputs)) {
			const iinfo = form.Inputs[inputname];

			if (iinfo.Record == recname)
				iinfo.OriginalValue = iinfo.getValue();
		}
	},

	raiseEvent: function(name, event) {
		// return promise if Async page, otherwise return undefined because sync events don't return values
		if (event?.Record && (event?.Record != 'Default'))
			return this.PageEntry.callPageFunc(this.Prefix ? this.Prefix + '-' + name + '-' + event.Record : name + '-' + event.Record, event);
		else
			return this.PageEntry.callPageFunc(this.Prefix ? this.Prefix + '-' + name : name, event);
	},

	/*
	this cannot be async - it happens often and needs to happen fast

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
	},
	'dcm.ImageWidget': {
		'centerBanner': function(entry, node, fimg, simg) {
			if (! fimg || fimg.length == 0)
				return;

	 		var centerEnable = $(node).attr('data-dcm-centering');

	 		if (! centerEnable || (centerEnable.toLowerCase() != 'true'))
	 			return;

			$(fimg).css({
				marginLeft: '0'
			});

			fimg = fimg.get(0);

			var centerHint = $(simg).attr('data-dc-center-hint')
				? $(simg).attr('data-dc-center-hint') : $(node).attr('data-dcm-center-hint');

			var ch = centerHint ? centerHint : (fimg.naturalWidth / 2);
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

				if (currWidth > availWidth)
					xoff /= 2;
			}

			$(fimg).css({
			     marginLeft: (0 - xoff) + 'px'
			 });
		}
	},
	'dcm.SliderWidget': {
		'getCurrentSlide': function(entry, node) {
			var currimg = dc.util.Number.toNumberStrict($(node).attr('data-dc-current-slide'));

			var imagelist = $(node).find('.dcm-widget-slider-list img');

			return $(imagelist.get(currimg));
		},
		'getCurrentSlideData': function(entry, node) {
			var currimg = dc.pui.TagFuncs['dcm.SliderWidget']['getCurrentSlide'].apply(this, [ entry, node ]);

			var data = $(currimg).attr('data-dc-image-data');

			return data ? JSON.parse(data) : null;
		},
		'getCurrentSlideElement': function(entry, node) {
			var currimg = dc.pui.TagFuncs['dcm.SliderWidget']['getCurrentSlide'].apply(this, [ entry, node ]);

			var data = $(currimg).attr('data-dc-image-element');

			return data ? JSON.parse(data) : null;
		},
		'pauseSlides': function(entry, node) {
			var imgel = $(node).find('img.dcm-widget-slider-img');

			$(imgel).removeClass('autoplay');
		}
	}
};

dc.pui.Tags = {
	'dc.PagePanel': function(entry, node) {
		$(node).find('a').click(function(e) {
			var processed = false;

			// TODO we need to hide the <li> not just the <a> for the sake of screen readers

			if ($(this).hasClass('dcui-pagepanel-close')) {
				try {
					if (entry.hasPageFunc('onClose'))
						entry.callPageFunc('onClose', e);
					else
						entry.Layer.close();
				}
				catch (x) {
					// ???
				}

				processed = true;
			}
			else if ($(this).hasClass('dcui-pagepanel-back')) {
				try {
					if (entry.hasPageFunc('onBack'))
						entry.callPageFunc('onBack', e);
					else
						entry.Layer.back();
				}
				catch (x) {
					// ???
				}

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
	'dcm.Button': function(entry, node) {
		dc.pui.Tags['dc.Link'](entry, node);
	},
	'dcm.Link': function(entry, node) {
		dc.pui.Tags['dc.Link'](entry, node);
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

		const cmsfunc = async function() {
			if ($(node).hasClass('dcm-cms-edit-mode')) {
				const confirm = await dc.pui.Popup.confirmAsync('Do you want to edit the button properties?');

				if (confirm) {
					entry.callTagFunc(node, 'edit');
					return true;
				}
			}

			return false;
		};

		if (click || page) {
			var clickfunc = async function(e, ctrl) {
				if (! $(node).hasClass('pure-button-disabled') && ! dc.pui.Apps.busyCheck()) {
					entry.LastFocus = $(node);

					if (await cmsfunc()) {
						// no op
					}
					else if (click) {
						const pagedef = dc.pui.Loader.Pages[entry.Name];

						try {
							if (pagedef.Async)
								await entry.callPageFunc(click, e, ctrl);
							else
								entry.callPageFunc(click, e, ctrl);
						}
						catch (x) {
							dc.pui.Popup.alert(x.message);
						}
					}
					else if (page) {
						entry.Layer.loadPage(page);
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

				var clickfunc = async function(e, ctrl) {
					if (await cmsfunc()) {
						// no op
					}
					else if ($(e.currentTarget).attr('target') == '_blank') {
						window.open(e.data, '_blank').focus();
					}
					else if (linkto) {
						window.location = e.data;		// want to reload
					}
					else {
						entry.Layer.loadPage(e.data);
					}
				};

				if (ext && ext.indexOf('/') == -1)
					hasext = true;

				if (! hasext || (ext == '.html')) {
					$(node).click(link, function(e) {
						entry.LastFocus = $(node);

						clickfunc(e, this);

						e.preventDefault();
						return false;
					});
				}
				else if (! $(node).attr('target')) {
					$(node).attr('target', '_blank');

					// we really only need to intercept if in edit mode, otherwise a normal click will do

					if ($(node).hasClass('dcm-cms-edit-mode')) {
						$(node).click(link, function(e) {
							entry.LastFocus = $(node);

							clickfunc(e, this);

							e.preventDefault();
							return false;
						});
					}
				}
			}
		}
		// TODO revise and update BID
		else if (link && dc.handler && dc.handler.Protocols) {
			var proto = link.substr(0, link.indexOf(':'));

			if (dc.handler.Protocols[proto]) {
				$(node).click(link, function(e) {
					entry.LastFocus = $(node);

					//if (await cmsfunc()) {
						// no op
					//}
					//else {
						dc.handler.Protocols[proto].call(e);		// custom handler
					//}

					e.preventDefault();
					return false;
				});
			}
		}
		// we really only need to intercept if in edit mode, otherwise a normal click will do
		else if ($(node).hasClass('dcm-cms-edit-mode')) {
			var clickfunc = async function(e, ctrl) {
				if (await cmsfunc()) {
					// no op
				}
				else if ($(e.currentTarget).attr('target') == '_blank') {
					window.open(e.data, '_blank').focus();
				}
			};

			$(node).click(link, function(e) {
				entry.LastFocus = $(node);

				clickfunc(e, this);

				e.preventDefault();
				return false;
			});
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
						// handle async and try / catch
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
								// handle async and try / catch
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
		const fname = $(node).attr('data-dcf-name');
		const page = dc.pui.Loader.Pages[entry.Name];

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
							// handle async and try / catch
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

			const clearBusy = function(task) {
				dc.pui.Apps.Busy = false;
			};

			if (vpass) {
				if (page.Async)
					form.saveAsync()
						.then(clearBusy)
						.catch(function(x) {
							clearBusy();

							console.log('page save failure: ' + x);

							if (x.stack)
								console.log(x.stack);

							dc.pui.Popup.alert(x.message);
						});
				else
					form.save(clearBusy);
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
		const skey = $(node).attr('data-dc-sitekey');
		const action = $(node).attr('data-dc-action');
		const page = dc.pui.Loader.Pages[entry.Name];

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
								// handle async and try / catch
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

				const clearBusy = function(task) {
					if (task && task.hasErrors())
						$(node).removeClass('pure-button-disabled');

					dc.pui.Apps.Busy = false;
				};

				const setTokenSave = function(token) {
					form.Captcha = {
						Token: token,
						Action: action
					};

					if (page.Async)
						form.saveAsync()
							.then(clearBusy)
							.catch(function(x) {
								console.log('page save failure: ' + x);

								if (x.stack)
									console.log(x.stack);

								dc.pui.Popup.alert(x.message);
							});
					else
						form.save(clearBusy);
				};

				if (vres.Pass) {
					$(node).addClass('pure-button-disabled');

					if (skey) {
						grecaptcha.ready(function() {
							grecaptcha
								.execute(skey, { action: action })
								.then(setTokenSave);
						});
					}
					else {
						setTokenSave('0123456789');
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
				// handle async and try / catch
				entry.callPageFunc(rfunc);
			}

			var widgetid = grecaptcha.render(node, {
				sitekey: skey,
				hl: (dc.util.Cookies.getCookie('dcLang') == 'spa') ? 'es' : 'en',
				callback: function(response) {
					$(node).attr('data-response', response);

					var func = $(node).attr('data-func');

					if (func) {
						// handle async and try / catch
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
		var listlr = $(node).find('.dc-widget-highlight-lr');
		var list = $(node).find('.dc-widget-highlight-list');

		if ((list.length == 0) && (listlr.length == 0))
			return;

		if (list.find('.dc-widget-highlight-entry').length < 2)
			return;

		var funcUpdateArrows = function() {
			if ($(list).scrollLeft() == 0) {
				$(node).find('.dc-widget-highlight-ctrl-left').addClass('dc-widget-highlight-ctrl-lr-disable');
			}
			else {
				$(node).find('.dc-widget-highlight-ctrl-left').removeClass('dc-widget-highlight-ctrl-lr-disable');
			}

			var pos = list.get(0).scrollWidth; // + $(list).innerWidth();

			var width = $(node).find('.dc-widget-highlight-entry').outerWidth();

			if (pos == $(list).scrollLeft() + width) {
				$(node).find('.dc-widget-highlight-ctrl-right').addClass('dc-widget-highlight-ctrl-lr-disable');
			}
			else {
				$(node).find('.dc-widget-highlight-ctrl-right').removeClass('dc-widget-highlight-ctrl-lr-disable');
			}
		};

		$(node).find('.dc-widget-highlight-ctrl-left a').click(function(e) {
			var width = $(node).find('.dc-widget-highlight-entry').outerWidth();

			$(list).scrollLeft($(list).scrollLeft() - width);

			funcUpdateArrows();

			e.preventDefault();
			return false;
		});

		$(node).find('.dc-widget-highlight-ctrl-right a').click(function(e) {
			var width = $(node).find('.dc-widget-highlight-entry').outerWidth();

			$(list).scrollLeft($(list).scrollLeft() + width);

			funcUpdateArrows();

			e.preventDefault();
			return false;
		});

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

				funcUpdateArrows();
			}
		};

		list.get(0).addEventListener('mousedown', lock, false);
		list.get(0).addEventListener('touchstart', lock, false);

		list.get(0).addEventListener('mousemove', drag, false);
		list.get(0).addEventListener('touchmove', drag, false);

		list.get(0).addEventListener('mouseup', move, false);
		list.get(0).addEventListener('touchend', move, false);

		funcUpdateArrows();
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

		var centerfunc = function(e) {
			var fimg = $(node).find('.dcm-widget-banner-img');

			dc.pui.TagFuncs['dcm.ImageWidget']['centerBanner'].apply(this, [ entry, node, fimg, fimg ]);
		};

		entry.registerResize(centerfunc);

		var img = new Image();
		img.onload = centerfunc;
		img.src = $(node).find('img').attr('src');
	},

	'dcm.SliderWidget': function(entry, node) {
		var currimg = 0;
		var fcurrimg = 0;
		var targetsrc = null;

		var imagelist = $(node).find('.dcm-widget-slider-list img');

		var imgel = $(node).find('img.dcm-widget-slider-img');
		var fadel = $(node).find('img.dcm-widget-slider-fader');

		var selectInGallery = function() {
			$(node).attr('data-dc-current-slide', currimg);

			//var fimg = $(imagelist.get(currimg));
			//var data = $(fimg).attr('data-dc-image-data');

			//data = JSON.parse(data) || { };

			//$(node).attr('data-dcm-center-hint', data.CenterHint);

			var gallery = $('#' + $(node).attr('data-target'));

			if (gallery.length == 1) {
				var gallist = $(gallery).find('a[data-dcm-alias]');

				gallist.removeClass('auto-select');

				if (currimg < gallist.length)
					$(gallist.get(currimg)).addClass('auto-select');
			}

			$(node).trigger('dcm:slideswitched');
		}

		var debugCenter = function(title) {
			//console.log(title + ' - ' + currimg + ' : ' + $(imgel).css('margin-left') + ' : ' + $(fadel).css('margin-left'));
		}

		var centerfunc = function(e) {
			debugCenter('a');
			dc.pui.TagFuncs['dcm.ImageWidget']['centerBanner'].apply(this, [ entry, node, imgel, $(imagelist.get(currimg)) ]);
		};

		var centerfunc2 = function(e) {
			debugCenter('b');
			dc.pui.TagFuncs['dcm.ImageWidget']['centerBanner'].apply(this, [ entry, node, fadel, $(imagelist.get(fcurrimg)) ]);
		};

		var switchFromGallery = function() {
			var src = targetsrc;

			if (src) {
				var variant = $(node).attr('data-dc-variant');
				var ext = $(node).attr('data-dc-ext');

				var pos1 = src.lastIndexOf('/');
				var pos2 = src.lastIndexOf('?');

				var newsrc = src.substr(0, pos1 + 1) + variant + '.' + ext + src.substr(pos2);

				//console.log('switch src a');
				centerfunc();

				$(imgel).attr('src', newsrc);
			}

			selectInGallery();
		};

		var startSwitchCurrent = function(manual) {
			if (manual) {
				opset = false;
				$(imgel).removeClass('autoplay');
			}

			targetsrc = $(imagelist.get(currimg)).attr('src');

			if (manual) {
				$(imgel).addClass('manual');

				$(imgel).css("opacity", 0);
			}
		};

		var prevFromGallery = function(manual) {
			if (imagelist && imagelist.length) {
				currimg--;

				if (currimg < 0)
					currimg = imagelist.length - 1;

				startSwitchCurrent(manual);
			}
		};

		var nextFromGallery = function(manual) {
			if (imagelist && imagelist.length) {
				currimg++;

				if (currimg >= imagelist.length)
					currimg = 0;

				startSwitchCurrent(manual);
			}
		};

		$(node).find('.dcm-widget-slider-ctrl-left a').click(function(e) {
			prevFromGallery(true);

			e.preventDefault();
			return false;
		});

		$(node).find('.dcm-widget-slider-ctrl-right a').click(function(e) {
			nextFromGallery(true);

			e.preventDefault();
			return false;
		});

		var gallery = $('#' + $(node).attr('data-target'));

		if (imagelist.length > 1) {
			$(node).removeClass('single');
			$(gallery).removeClass('single');
		}
		else {
			$(node).addClass('single');
			$(gallery).addClass('single');
		}

		if (gallery.length == 1) {
			// link clicks in the gallery to this control - TODO should be optional
			$(gallery).find('a[data-dcm-alias]').click(function(e) {
				currimg = $(e.currentTarget).index();
				startSwitchCurrent(true);

				e.preventDefault();
				return false;
			});
		}

		var cx = 0, x0 = 0, locked = false;

		function unify(e) {	return e.changedTouches ? e.changedTouches[0] : e };

		function lock(e) {
			x0 = unify(e).clientX;
			locked = true;
		};

		function drag(e) {
			e.preventDefault();
		};

		function move(e) {
			if(locked) {
				var dx = x0 - unify(e).clientX;

				if (dx > 64) {
					nextFromGallery(true);
				}
				else if (dx < -64) {
					prevFromGallery(true);
				}

				locked = false;
			}
		};

		$(node).get(0).addEventListener('mousedown', lock, false);
		$(node).get(0).addEventListener('touchstart', lock, false);

		$(node).get(0).addEventListener('mousemove', drag, false);
		// $(node).get(0).addEventListener('touchmove', drag, false);

		$(node).get(0).addEventListener('mouseup', move, false);
		$(node).get(0).addEventListener('touchend', move, false);

		var opset = false;
		var opcurr = 1;
		var viewperiod = dc.util.Number.toNumberStrict($(node).attr('data-dcm-period'));

		if (! viewperiod)
			viewperiod = 2000;

		entry.registerResize(function() {
			//console.log('resize');
			centerfunc();
			centerfunc2();
		});

		$(imgel).on('load', function() {
			//console.log('load a');
			centerfunc();
		} );
		$(fadel).on('load', function() {
			//console.log('load b');
			centerfunc2();
		});

		$(imgel).on('transitionend', function() {
			if (opset) {
				if ($(imgel).hasClass('autoplay')) {
					setTimeout(function() {
						if ($(imgel).hasClass('autoplay')) 	// check if still has autoplay
							$(imgel).css("opacity", 0);
					}, viewperiod);
				}
				else {
					//debugger;
				}

				fcurrimg = currimg;

				$(fadel).css('margin-left', $(imgel).css('margin-left'));
				//console.log('switch src b');
				$(fadel).attr('src', $(imgel).attr('src'));
			}
			else {
				if ($(imgel).hasClass('autoplay')) {
					nextFromGallery(false);
				}

				switchFromGallery();

				$(imgel).css("opacity", 1);
			}

			opset = ! opset;
		});

		selectInGallery();

		if (imagelist.length > 1) {
			setTimeout(function() {
				if ($(imgel).hasClass('autoplay')) {
					$(imgel).css("opacity", 0);
				}
			}, viewperiod);
		}
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
	},
	'dcm.VideoWidget': function(entry, node) {
		var vjs = $(node).find('video-js');

		if (vjs.length)
			videojs(vjs.get(0), { autoplay: false });
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
	getNode: function() {
		return $('#' + this.Id).get(0);
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
		value = '0';

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
		$('#' + this.Id + '-' + dc.util.Hex.toHex(values[i] + '')).prop('checked',true);
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
		.on('contextmenu', function(e) {
			e.preventDefault();
			e.stopPropagation();

			navigator.clipboard.read()
				.then(async function(clipboardItems) {
					let t = moment.utc();
					let selectPastedFiles = [ ];

					for (let clipboardItem of clipboardItems) {
						const imageType = clipboardItem.types.find(type => type.startsWith('image/png'));

						if (imageType) {
							const blob = await clipboardItem.getType(imageType);

							blob.name = t.format('YYYYMMDDTHHmmssSSS') + 'Z.png';

							console.log('name: ' + blob.name);

							selectPastedFiles.push(blob);
						}
					}

					ctrl.addFiles(selectPastedFiles);
				})
				.catch(function(x) {
					console.error(x.name, x.message);
				});

			return false;
		})
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

	$('#fld' + this.Id + ' .dc-uploader-listing tbody').empty();
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

	//console.log('onAfterSave: ' + this.Field)

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

dc.pui.controls.Uploader.prototype.onAfterSaveAsync = async function(e) {
	return new Promise((resolve, reject) => {
		if (! e.Form.Managed) {
			resolve();
			return;
		}

		var files = [ ];

		for (var i = 0; i < this.Files.length; i++) {
			files.push({
				File: this.Files[i],
				Name: this.Values[i]
			});
		}

		var uploadtask = dc.transfer.Util.uploadFiles(files, 'ManagedForms', e.Token);

		dc.pui.Popup.await(dc.lang.Dict.tr('_code_900'), function() {
			resolve();
		}, dc.lang.Dict.tr('_code_901'), uploadtask);
	});
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

	var accepts = $('#fld' + this.Id + ' input').attr('accept');

	if (accepts) {
		var acceptlist = accepts.split(',');

		for (var i = 0; i < acceptlist.length; i++) {
			acceptlist[i] = acceptlist[i].trim();
		}

		for (var i = 0; i < values.length; i++) {
			if (! acceptlist.includes(values[i].type)) {
				dc.pui.Popup.alert('One or more of the files selected is not a valid type.');
				return;
			}
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
	var ids = dc.handler.settings.ga.split(';');

	for (var g = 0; g < ids.length; g++)
		gtag('config', ids[g]);

	var script = document.createElement('script');
	script.src = 'https://www.googletagmanager.com/gtag/js?id=' +
		ids[0];
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

function loadFBPixel() {
	var pixelid = dc.handler.settings.fbpixel;

	//console.log('loading fbq general: ' + pixelid);

	var script = document.createElement('script');
	script.src = 'https://connect.facebook.net/en_US/fbevents.js';
	script.async = true;
	script.defer = true;
	document.head.appendChild(script);

	// someday this can go into global, after converting existing websites to use this setting
	window.fbq = window._fbq = function(){ fbq.callMethod ? fbq.callMethod.apply(fbq, arguments) : fbq.queue.push(arguments); }

	fbq.push = fbq;
	fbq.loaded = !0;
	fbq.version = '2.0';
	fbq.queue = [];

	fbq('init', pixelid);
	fbq('track', 'PageView');
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
