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

if (!dc.cms)
	dc.cms = {};

dc.cms.Loader = {
	Enabled: true,
	Mode: 'Widget',		// Widget | LayoutEdit | LayoutMove | BandEdit | BandMove
	Sortable: null,
	init: function(options) {
		// TODO lookup options from dc.handler

		// on web page load always start with Widget mode, but detect if cms is enabled
		var enabled = localStorage.getItem('dc-cms-enabled');

		if (enabled)
			dc.cms.Loader.Enabled = (enabled == 'true');

		if (! options) {
			options = {
				AuthTags: [ 'Editor', 'Clerk', 'Admin', 'Developer' ],
				Tab: 'Dashboard',
				Menu: 'dcmGeneral',
				Params: { }
			};
		}

		if (options.AuthTags && ! dc.user.isAuthorized(options.AuthTags))
			return;

		dc.pui.Loader.addExtraLibs([ '/js/vendor/sortable.min.js' ], function() {
			var entry = dc.pui.Loader.currentLayer().Current;

			$('#dcuiMain').dcappend(
				$('<div>')
				 	.attr('id', 'dcuiAppTab')
					.dcappend(
						$('<i>').addClass('fa fa-angle-double-right')
					)
					.click(function (e) {
						if (dc.cms.Loader.Enabled) {
							dc.pui.Popup.menu('dcmCms');
						}
						else {
							dc.pui.Popup.menu('dcmMain');
						}

						e.preventDefault();
						return false;
					}),
				$('<div>')
				 	.attr('id', 'dcuiCmsSaveTab')
					.dcappend(
						$('<i>').addClass('fa fa-save')
					)
					.click(function (e) {
						// TODO support nested feeds, different feeds and different paths - for now just handle top pages feed
						var pel = $('#dcuiMain div[data-dc-tag="dcm.IncludeFeed"][data-cms-editable="true"][data-cms-feed="pages"]').get(0);

						if (! pel)
							return;

						var commands = [ ];

						var list = entry.callTagFunc(pel, 'doCmsGetCommands');

						if (list) {
							for (var i = 0; i < list.length; i++) {
								commands.push(list[i]);
							}
						}

						$(pel).find('*[data-cms-editable="true"]').each(function() {
							var list = entry.callTagFunc(this, 'doCmsGetCommands');

							if (list) {
								for (var i = 0; i < list.length; i++) {
									commands.push(list[i]);
								}
							}
						});

						var afterSave = function() {
							dc.cms.Loader.Mode = 'Widget',
							dc.cms.Loader.showMode();

							dc.pui.Dialog.loadPage('/dcm/cms/page-save', {
								Path: $(pel).attr('data-cms-path'),
								Feed: 'pages',
								Callback: function(ret) {
									entry.Layer.refreshPage();
								}
							});
						};

						if (commands.length > 0) {
							dc.comm.sendMessage({
								Service: 'dcmServices',
								Feature: 'Feed',
								Op: 'AddCommandHistory',
								Body: {
									Path: $(pel).attr('data-cms-path'),
									Feed: 'pages',
									Commands: commands
								}
							}, function(resp) {
								if (resp.Result > 0) {
									dc.pui.Popup.alert(resp.Message);
									return;
								}

								afterSave();
							});
						}
						else {
							afterSave();
						}

						e.preventDefault();
						return false;
					})
			);

			// TODO support nested feeds, different feeds and different paths - for now just handle top pages feed
			var pel = $('#dcuiMain div[data-dc-tag="dcm.IncludeFeed"][data-cms-editable="true"][data-cms-feed="pages"]').get(0);

			if (pel && ($(pel).attr('data-cms-draft') == 'true'))
				$('#dcuiCmsSaveTab').show();

			dc.cms.Loader.showMode();
		});
	},
	showMode: function() {
		$('#dcuiMain')
			.removeClass('dcuiCmsWidgetEnable')
			.removeClass('dcuiCmsLayoutEditEnable')
			.removeClass('dcuiCmsLayoutMoveEnable')
			.removeClass('dcuiCmsBandEditEnable')
			.removeClass('dcuiCmsBandMoveEnable');

		// TODO while this generally works, sometimes when switching from BandMove to LayoutMove to Widget
		// still has Sortable enabled despite the following
		if (dc.cms.Loader.Sortable) {
			dc.cms.Loader.Sortable.destroy();	// remove the old one
			dc.cms.Loader.Sortable = null;
		}

		if (dc.cms.Loader.Enabled) {
			$('#dcuiMain')
				.addClass('dcuiCms' + dc.cms.Loader.Mode + 'Enable');

			var entry = dc.pui.Loader.currentLayer().Current;

			if (dc.cms.Loader.Mode == 'BandMove') {
				// data-cms-editable is not set for areas you cannot edit
				/* TODO copy idea from LayoutMove
				$('#dcuiMain div[data-dc-tag="dcm.IncludeFeed"][data-cms-editable="true"]').each(function() {
					dc.cms.Loader.Sortable = Sortable.create(this, {
						onEnd: function (evt) {
							$('#dcuiCmsSaveTab').show();
						}
					});
				});
				*/
			}

			if (dc.cms.Loader.Mode == 'LayoutMove') {
				// look for Id's - include band (parent) must have id
				$('#dcuiMain div[data-dc-tag="dcm.IncludeFeed"][data-cms-editable="true"] div[data-dc-tag="dc.Band"][data-cms-editable="true"][id]:not([id=""])').each(function() {
					entry.callTagFunc(this, 'doCmsInitLayout');
				});
			}

			if (dc.cms.Loader.Mode == 'Widget') {
				$('*[data-cms-editable="true"]').each(function() {
					entry.callTagFunc(this, 'doCmsInitWidget');
				});
			}
		}
	},
	createEditToolBar: function(buttons, cssclass) {
		var toolbar = $('<div>')
			.attr('role', 'list')
			.attr('aria-label', 'CMS toolbar for previous content')
			.addClass('dcuiCmsToolbar dc-unsortable ' + (cssclass ? cssclass : ''));

		if (buttons) {
			for (var i = 0; i < buttons.length; i++) {
				var opt = buttons[i];

				if (opt.Auth && ! dc.user.isAuthorized(opt.Auth))
					continue;

				toolbar.dcappend(
					$('<div>')
						.attr('role', 'listitem')
						.dcappend(
							$('<a>')
								.attr('href', '#')
								.attr('role', 'button')
								.attr('aria-label', opt.Title)
								.addClass(opt.Kind)
								.dcappend(
									$('<i>')
									 	.addClass('fa ' + opt.Icon),
								)
								.click(opt, function(e) {
									//entry.Layer.back();

									if (e.data.Op)
										e.data.Op(e);

									// TODO support other layers - via Params
									//dc.pui.App.loadTab(e.data.Alias);

									e.preventDefault();
									return false;
								})
						)
				);
			}
		}


		//		Path: '/dcm/cms/gallery-widget-list'			// TODO add feed

		//	dc.cms.Loader.createEditButton('', 'CMS - edit previous gallery')

		return toolbar;
	},
	createEditButton: function(cssclass, label, onclick) {
		return $('<a>')
			.attr('href', '#')
			.attr('aria-label', label)
			.addClass('dcuiCmsButton dcuiCmsi dc-link ' + (cssclass ? cssclass : ''))
			.click(function(e) {
				var entry = dc.pui.Loader.currentLayer().Current;

				entry.LastFocus = $(this);

				var pel = $(this).closest('[data-cms-editable="true"]').get(0);

				if (! pel)
					return;

				if (onclick) {
					onclick(e, entry, pel);
				}
				else {
					var func = $(pel).attr('data-cms-func');

					entry.callTagFunc(pel, func ? func : 'doCmsEdit');
				}

				e.preventDefault();
				return false;
			})
			.dcappend(
				$('<span>')
					.attr('aria-hidden', 'true')
					.addClass('fa-stack fa-lg')
					.dcappend(
						$('<i>')
						 	.addClass('fa fa-square fa-stack-2x dc-icon-background'),
						$('<i>')
							.addClass('fa fa-pencil fa-stack-1x dc-icon-foreground')
					)
			);
	}
};

dc.pui.Apps.Menus.dcmEmpty = {
	Options: [
	]
};

dc.pui.Apps.Menus.dcmMain = {
	Options: [
		{
			Title: 'Edit Page',
			Op: function(e) {
				localStorage.setItem('dc-cms-enabled', 'true');

				// TODO if there is no draft we could optimize and just do this
				//dc.cms.Loader.Enabled = true;
				//dc.cms.Loader.Mode = 'Widget',
				//dc.cms.Loader.showMode();

				dc.util.Cookies.setCookie('dcmMode', 'now');
				dc.pui.Loader.MainLayer.refreshPage()
			}
		},
		{
			Title: 'CMS Dashboard',
			Auth: [ 'Admin', 'Editor' ],
			Op: function(e) {
				dc.pui.App.startTab({
					Tab: 'Dashboard',
					Menu: 'dcmGeneral'
				});
			}
		},
		{
			Title: 'Store',
			Auth: [ 'Admin', 'Clerk' ],
			Op: function(e) {
				dc.pui.App.startTab({
					Tab: 'Dashboard',
					Menu: 'dcmStore'
				});
			}
		},
		{
			Title: 'System',
			Auth: [ 'Developer' ],
			Op: function(e) {
				dc.pui.App.startTab({
					Tab: 'Dashboard',
					Menu: 'dcmSystem'
				});
			}
		},
		{
			Title: 'My Account',
			Op: function(e) {
				dc.pui.Dialog.loadPage('/dcw/user/edit-self');
			}
		},
		/*
		{
			Title: 'Accessibility',
			Op: function(e) {
				dc.pui.Dialog.loadPage('/dcw/user/edit-access');
			}
		},
		*/
		{
			Title: 'Sign Out',
			Op: function(e) {
				dc.pui.Loader.signout();
			}
		}
	]
};

dc.pui.Apps.Menus.dcmCms = {
	Options: [
		{
			Title: 'Edit Page Properties',		// the feed with Meta
			Auth: [ 'Admin', 'Editor' ],
			Op: function(e) {
				var entry = dc.pui.Loader.currentLayer().Current;

				// TODO check editable
				var pel = $('#dcuiMain div[data-dc-tag="dcm.IncludeFeed"][data-cms-editable="true"]').get(0);

				if (! pel)
					return;

				var func = $(pel).attr('data-cms-func');

				entry.callTagFunc(pel, func ? func : 'doCmsEdit');
			}
		},
		{
			Title: 'Edit Feed Code',
			Auth: [ 'Developer' ],
			Op: function(e) {
				/* TODO select a feed, defaulting to the page's main feed
				var entry = dc.pui.Loader.currentLayer().Current;

				var pel = $('#dcuiMain div[data-dc-tag="dcm.IncludeFeed"][data-cms-editable="true"]').get(0);

				if (! pel)
					return;

				var func = $(pel).attr('data-cms-func');

				entry.callTagFunc(pel, func ? func : 'doCmsEdit');
				*/
			}
		},
		{
			Title: 'Edit Widgets Mode',
			// Auth: [ 'Admin', 'Editor' ], - if you can see this menu then you can click this
			Op: function(e) {
				dc.cms.Loader.Mode = 'Widget',
				dc.cms.Loader.showMode();
			}
		},
		/*
		{
			Title: 'Edit Layouts Mode',
			Auth: [ 'Admin', 'Editor' ],		// editors can do this?
			Op: function(e) {
				dc.cms.Loader.Mode = 'LayoutEdit',
				dc.cms.Loader.showMode();
			}
		},
		*/
		{
			Title: 'Move Layouts Mode',
			Auth: [ 'Admin' ],			// move should only be admin
			Op: function(e) {
				dc.cms.Loader.Mode = 'LayoutMove',
				dc.cms.Loader.showMode();
			}
		},
		/*
		{
			Title: 'Edit Bands Mode',		// hide/show - all bands load if $_CMSEditable is true, so hide the hidden ones
			Auth: [ 'Admin' ],			// bands should only be admin
			Op: function(e) {
				dc.cms.Loader.Mode = 'BandEdit',
				dc.cms.Loader.showMode();
			}
		},
		*/
		{
			Title: 'Move Bands Mode',
			Auth: [ 'Admin' ],			// bands should only be admin
			Op: function(e) {
				dc.cms.Loader.Mode = 'BandMove',
				dc.cms.Loader.showMode();
			}
		},
		{
			Title: 'Stop Editing Page',
			// Auth - if you can see this menu then you can do this
			Op: function(e) {
				localStorage.setItem('dc-cms-enabled', 'false');

				// TODO if there is no draft or command queue we could optimize and just do this
				//dc.cms.Loader.Enabled = false;
				//dc.cms.Loader.showMode();

				dc.util.Cookies.setCookie('dcmMode', 'off');
				dc.pui.Loader.MainLayer.refreshPage()
			}
		},
		{
			Title: 'Main Menu',
			// Auth - if you can see this menu then you can do this
			Op: function(e) {
				dc.pui.Popup.menu('dcmMain');
			}
		}
	]
};

dc.pui.Apps.Menus.dcmGeneral = {
	Tabs: [
  		{
			Alias: 'Dashboard',
			Title: 'Dashboard',
			Path: '/dcm/dashboard'
		},
  		{
			Alias: 'Pages',
			Title: 'Pages',
			Path: '/dcm/feeds/list-feed/pages'
		},
		{
			Alias: 'Galleries',
			Title: 'Galleries',
			Path: '/dcm/galleries/browser'
		},
		{
			Alias: 'Files',
			Title: 'Files',
			Path: '/dcm/files/browser'
		},
		{
			Alias: 'Vaults',
			Title: 'Vaults',
			Auth: [ 'Developer' ],
			Path: '/dcm/files/vaults'
		},
		{
			Alias: 'Users',
			Title: 'Users',
			Auth: [ 'Admin' ],
			Path: '/dcw/user/list-users'
		}
	],
	Options: [
		{
			Title: 'Main Menu',
			Auth: [ 'Admin', 'Staff' ],
			Op: function(e) {
				dc.pui.Popup.menu('dcmMain');
			}
		}
	]
};

dc.pui.Apps.Menus.dcmSystem = {
	Tabs: [
  		{
			Alias: 'Dashboard',
			Title: 'Dashboard',
			Path: '/dcr/dashboard'
		},
  		{
			Alias: 'Database',
			Title: 'Database',
			Path: '/dcr/database'
		}
	],
	Options: [
		{
			Title: 'Globals',
			Auth: [ 'SysAdmin' ],
			Op: function(e) {
				dc.pui.App.startTab({
					Tab: 'Explorer',
					Menu: 'dcmGlobals'
				});
			}
		},
		{
			Title: 'Main Menu',
			Auth: [ 'Admin', 'Staff' ],
			Op: function(e) {
				dc.pui.Popup.menu('dcmMain');
			}
		}
	]
};

dc.pui.Apps.Menus.dcmStore = {
	Tabs: [
		{
			Alias: 'Dashboard',
			Title: 'Orders',	// TODO change to Dashboard when more features
			Auth: [ 'Admin', 'Clerk' ],
			Path: '/dcm/store/dashboard'
		},
		{
			Alias: 'Products',
			Title: 'Products',
			Auth: [ 'Admin', 'Clerk' ],
			Path: '/dcm/store/categories'
		},
		{
			Alias: 'GiftRegistry',
			Title: 'Gift Registry',
			Auth: [ 'Admin', 'Clerk' ],
			Path: '/dcm/store/registry'
		}
	],
	Options: [
		{
			Title: 'Main Menu',
			Auth: [ 'Admin', 'Staff' ],
			Op: function(e) {
				dc.pui.Popup.menu('dcmMain');
			}
		}
	]
};

dc.pui.Apps.Menus.dcmGlobals = {
	Tabs: [
		{
			Alias: 'Explorer',
			Title: 'Explorer',
			Auth: [ 'SysAdmin' ],
			Path: '/dcr/global-explorer'
		}
	],
	Options: [
		{
			Title: 'Main Menu',
			Auth: [ 'Admin', 'Staff' ],
			Op: function(e) {
				dc.pui.Popup.menu('dcmMain');
			}
		}
	]
};

// ------------------- end Tags -------------------------------------------------------

if (! dc.pui.TagFuncs['dc.TextWidget'])
	dc.pui.TagFuncs['dc.TextWidget'] = { };

dc.pui.TagFuncs['dc.TextWidget']['doCmsInitWidget'] = function(entry, node) {
	var widget = this;

	// after so we don't get drag and drop
	$(node).dcappend(
		dc.cms.Loader.createEditToolBar([
			{
				Icon: 'fa-pencil',
				Title: 'Content',
				Auth: [ 'Admin', 'Editor' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');
					dc.pui.SimpleApp.loadPage('/dcm/cms/text-widget-content/' + params.Feed, params);
				}
			},
			{
				Icon: 'fa-cog',
				Title: 'Properties',
				Auth: [ 'Developer' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');
					dc.pui.Dialog.loadPage('/dcm/cms/text-widget-props/' + params.Feed, params);
				}
			}
		])
	);
};

dc.pui.TagFuncs['dc.TextWidget']['getParams'] = function(entry, node) {
	var pel = $(node).closest('*[data-cms-feed]').get(0);

	if (! pel)
		return null;

	return {
		Feed: $(pel).attr('data-cms-feed'),
		Path: $(pel).attr('data-cms-path'),
		Id: $(node).attr('id')
	};
};

if (! dc.pui.TagFuncs['dcm.ImageWidget'])
	dc.pui.TagFuncs['dcm.ImageWidget'] = { };

dc.pui.TagFuncs['dcm.ImageWidget']['doCmsInitWidget'] = function(entry, node) {
	var widget = this;

	// after so we don't get drag and drop
	$(node).dcappend(
		dc.cms.Loader.createEditToolBar([
			{
				Icon: 'fa-picture-o',
				Title: 'Properties',
				Auth: [ 'Admin', 'Editor' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');

					dc.comm.sendMessage({
						Service: 'dcmServices',
						Feature: 'Feed',
						Op: 'LoadPart',
						Body: {
							Feed: params.Feed,
							Path: params.Path,
							PartId: params.Id
						}
					}, function(rmsg1) {
						if (rmsg1.Result > 0) {
							dc.pui.Popup.alert(rmsg1.Message);
							return;
						}

						var widget = dc.util.Xml.toJQuery(rmsg1.Body.Part);
						var path = widget.attr('Path');

						var pos = path ? path.lastIndexOf('/') : -1;

						if (pos != -1)
							path = path.substr(0, pos);
						else
							path = '/';

						dc.pui.Dialog.loadPage('/dcm/galleries/chooser', {
							Path: path,
							Callback: function(res) {
								if (res.Images && res.Images.length) {
									var fh = res.Images[0];

									var newpath = fh.FullPath.substr(0, fh.FullPath.indexOf('.v'));

									widget.attr('Path', newpath);

									dc.comm.sendMessage({
										Service: 'dcmServices',
										Feature: 'Feed',
										Op: 'AddCommandHistory',
										Body: {
											Feed: params.Feed,
											Path: params.Path,
											Commands: [
												{
													Command: 'SavePart',
													Params: {
														PartId: params.Id,
														Part: dc.util.Xml.toString(widget)
													}
												}
											]
										}
									}, function(rmsg2) {
											if (rmsg2.Result > 0) {
												dc.pui.Popup.alert(rmsg2.Message);
												return;
											}

											dc.pui.Loader.MainLayer.refreshPage()
									});
								}
							}
						});
					});
				}
			},
			{
				Icon: 'fa-file-text-o',
				Title: 'Template',
				Auth: [ 'Developer' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');
					dc.pui.SimpleApp.loadPage('/dcm/cms/image-widget-template/' + params.Feed, params);
				}
			},
			{
				Icon: 'fa-cog',
				Title: 'Properties',
				Auth: [ 'Developer' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');
					dc.pui.Dialog.loadPage('/dcm/cms/image-widget-props/' + params.Feed, params);
				}
			}
		])
	);
};

dc.pui.TagFuncs['dcm.ImageWidget']['getParams'] = function(entry, node) {
	var pel = $(node).closest('*[data-cms-feed]').get(0);

	if (! pel)
		return null;

	return {
		Feed: $(pel).attr('data-cms-feed'),
		Path: $(pel).attr('data-cms-path'),
		Id: $(node).attr('id')
	};
};

if (! dc.pui.TagFuncs['dcm.YouTubeWidget'])
	dc.pui.TagFuncs['dcm.YouTubeWidget'] = { };

dc.pui.TagFuncs['dcm.YouTubeWidget']['doCmsInitWidget'] = function(entry, node) {
	// after so we don't get drag and drop
	$(node).dcappend(
		dc.cms.Loader.createEditToolBar([
			{
				Icon: 'fa-cog',
				Title: 'Properties',
				Auth: [ 'Admin', 'Editor' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');
					dc.pui.Dialog.loadPage('/dcm/cms/you-tube-widget-props/' + params.Feed, params);
				}
			}
		])
	);
};

dc.pui.TagFuncs['dcm.YouTubeWidget']['getParams'] = function(entry, node) {
	var pel = $(node).closest('*[data-cms-feed]').get(0);

	if (! pel)
		return null;

	return {
		Feed: $(pel).attr('data-cms-feed'),
		Path: $(pel).attr('data-cms-path'),
		Id: $(node).attr('id')
	};
};

if (! dc.pui.TagFuncs['dcm.GalleryWidget'])
	dc.pui.TagFuncs['dcm.GalleryWidget'] = { };

dc.pui.TagFuncs['dcm.GalleryWidget']['doCmsInitWidget'] = function(entry, node) {
	var widget = this;

	// after so we don't get drag and drop
	$(node).dcappend(
		dc.cms.Loader.createEditToolBar([
			{
				Icon: 'fa-plus',
				Title: 'Add',
				Auth: [ 'Admin', 'Editor' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');

					dc.comm.sendMessage({
						Service: 'dcmServices',
						Feature: 'Feed',
						Op: 'LoadPart',
						Body: {
							Feed: params.Feed,
							Path: params.Path,
							PartId: params.Id
						}
					}, function(rmsg1) {
						if (rmsg1.Result > 0) {
							dc.pui.Popup.alert(rmsg1.Message);
							return;
						}

						var widget = dc.util.Xml.toJQuery(rmsg1.Body.Part);
						var path = widget.attr('Path');

						dc.pui.Dialog.loadPage('/dcm/galleries/chooser', {
							Path: path,
							Callback: function(res) {
								if (res.Images && res.Images.length) {
									var fh = res.Images[0];

									var newpath = fh.FullPath.substring(0, fh.FullPath.indexOf('.v'));
									newpath = newpath.substring(newpath.lastIndexOf('/') + 1);

									widget.prepend($('<Image>', widget).attr('Alias', newpath));

									dc.comm.sendMessage({
										Service: 'dcmServices',
										Feature: 'Feed',
										Op: 'AddCommandHistory',
										Body: {
											Feed: params.Feed,
											Path: params.Path,
											Commands: [
												{
													Command: 'SavePart',
													Params: {
														PartId: params.Id,
														Part: dc.util.Xml.toString(widget)
													}
												}
											]
										}
									}, function(rmsg2) {
											if (rmsg2.Result > 0) {
												dc.pui.Popup.alert(rmsg2.Message);
												return;
											}

											dc.pui.Loader.MainLayer.refreshPage();
									});
								}
							}
						});
					});
				}
			},
			{
				Icon: 'fa-file-text-o',
				Title: 'Template',
				Auth: [ 'Developer' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');
					dc.pui.SimpleApp.loadPage('/dcm/cms/gallery-widget-template/' + params.Feed, params);
				}
			},
			{
				Icon: 'fa-cog',
				Title: 'Properties',
				Auth: [ 'Developer' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');
					dc.pui.Dialog.loadPage('/dcm/cms/gallery-widget-props/' + params.Feed, params);
				}
			}
		])
	);

	// look for Id's - include band (parent) must have id
	if (! $(node).attr('data-cms-reorder-enabled')) {
		console.log('reorder enable');

		var pos = 0;

		$(node).find('> *:not(.dcuiCmsToolbar)').each(function() {
			var imgnode = this;

			$(imgnode)
				.attr('data-cms-img-pos', pos + '')
				.dcappend($('<div>')
					.attr('role', 'list')
					.addClass('dcuiCmsToolbar dcuiCmsImageProps')
					.dcappend(
						$('<div>')
							.attr('role', 'listitem')
							.dcappend(
								$('<a>')
									.attr('href', '#')
									.attr('role', 'button')
									//.attr('aria-label', opt.Title)
									//.addClass('opt.Kind')
									.dcappend(
										$('<i>')
										 	.addClass('fa fa-pencil'),  // + opt.Icon),
									)
									.click(function(e) {
										var imgalias = $(imgnode).attr('data-dcm-alias');

										if (! imgalias) {
											dc.pui.Popup.alert('Missing alias, cannot edit.');
										}
										else {
											var params = entry.callTagFunc(widget, 'getParams');

											dc.comm.sendMessage({
												Service: 'dcmServices',
												Feature: 'Feed',
												Op: 'LoadPart',
												Body: {
													Feed: params.Feed,
													Path: params.Path,
													PartId: params.Id
												}
											}, function(rmsg1) {
												if (rmsg1.Result > 0) {
													dc.pui.Popup.alert(rmsg1.Message);
													return;
												}

												var widget = dc.util.Xml.toJQuery(rmsg1.Body.Part);
												var path = widget.attr('Path');

												dc.cms.image.Loader.loadGallery(path, function(gallery, resp) {
													//entry.Store.Gallery = gallery;

													if (resp.Result > 0) {
														dc.pui.Popup.alert(resp.Message);
														return;
													}

													//console.log('properties: ' + gallery.Meta.PropertyEditor);

													var editor = gallery.Meta.PropertyEditor
														? gallery.Meta.PropertyEditor : '/dcm/galleries/edit-image';

													dc.pui.Dialog.loadPage(editor, {
														Feed: params.Feed,
														Path: params.Path,
														PartId: params.Id,
														Image: imgalias,
														Gallery: gallery,
														Callback: function() {
															dc.pui.Loader.MainLayer.refreshPage();
														}
													});
												});
											});
										}

										e.preventDefault();
										return false;
									})
							),
						$('<div>')
							.attr('role', 'listitem')
							.dcappend(
								$('<a>')
									.attr('href', '#')
									.attr('role', 'button')
									//.attr('aria-label', opt.Title)
									//.addClass('opt.Kind')
									.dcappend(
										$('<i>')
										 	.addClass('fa fa-times'),  // + opt.Icon),
									)
									.click(function(e) {
										var imgalias = $(imgnode).attr('data-dcm-alias');

										if (! imgalias) {
											dc.pui.Popup.alert('Missing alias, cannot edit.');
										}
										else {
											var params = entry.callTagFunc(widget, 'getParams');

											dc.comm.sendMessage({
												Service: 'dcmServices',
												Feature: 'Feed',
												Op: 'LoadPart',
												Body: {
													Feed: params.Feed,
													Path: params.Path,
													PartId: params.Id
												}
											}, function(rmsg1) {
												if (rmsg1.Result > 0) {
													dc.pui.Popup.alert(rmsg1.Message);
													return;
												}

												var widget = dc.util.Xml.toJQuery(rmsg1.Body.Part);
												var path = widget.attr('Path');

												widget.find('Image[Alias="' + imgalias + '"]').remove();

												dc.comm.sendMessage({
													Service: 'dcmServices',
													Feature: 'Feed',
													Op: 'AddCommandHistory',
													Body: {
														Feed: params.Feed,
														Path: params.Path,
														Commands: [
															{
																Command: 'SavePart',
																Params: {
																	PartId: params.Id,
																	Part: dc.util.Xml.toString(widget)
																}
															}
														]
													}
												}, function(rmsg2) {
														if (rmsg2.Result > 0) {
															dc.pui.Popup.alert(rmsg2.Message);
															return;
														}

														dc.pui.Loader.MainLayer.refreshPage();
												});
											});
										}

										e.preventDefault();
										return false;
									})
								)

					)
				);
			pos++;
		});

		$(node).attr('data-cms-reorder-enabled', 'true');
	}

	// TODO provide for destroy - dc.cms.Loader.Sortable = Sortable.create

	Sortable.create(node, {
		filter: ".dc-unsortable",
		onEnd: function (evt) {
			$(node).attr('data-cms-reordered', 'true');

			/*
			var order = entry.callTagFunc(widget, 'doCmsGetCommands');
			debugger;
			console.log('o: ' + JSON.stringify(order, null, '\t'));
			*/

			$('#dcuiCmsSaveTab').show();
		}
	});
};

dc.pui.TagFuncs['dcm.GalleryWidget']['getParams'] = function(entry, node) {
	var pel = $(node).closest('*[data-cms-feed]').get(0);

	if (! pel)
		return null;

	return {
		Feed: $(pel).attr('data-cms-feed'),
		Path: $(pel).attr('data-cms-path'),
		Id: $(node).attr('id')
	};
};

dc.pui.TagFuncs['dcm.GalleryWidget']['doCmsGetPositions'] = function(entry, node) {
 	return $(node).find('> *[data-cms-img-pos]').map(function() { return $(this).attr('data-cms-img-pos'); }).get();
};

dc.pui.TagFuncs['dcm.GalleryWidget']['doCmsGetCommands'] = function(entry, node) {
	var widget = this;
	var cmds = [ ];

	if ($(node).attr('data-cms-reordered')) {
		cmds.push({
			Command: 'Reorder',
			Params: {
				PartId: $(this).attr('id'),
				Order: entry.callTagFunc(widget, 'doCmsGetPositions')
			}
		});
	}

	return cmds;
};

if (! dc.pui.TagFuncs['dcm.CarouselWidget'])
	dc.pui.TagFuncs['dcm.CarouselWidget'] = { };

dc.pui.TagFuncs['dcm.CarouselWidget']['doCmsInitWidget'] = function(entry, node) {
	var widget = this;

	// after so we don't get drag and drop
	$(node).dcappend(
		dc.cms.Loader.createEditToolBar([
			{
				Icon: 'fa-plus',
				Title: 'Add',
				Auth: [ 'Admin', 'Editor' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');
					//dc.pui.Dialog.loadPage('/dcm/cms/carousel-widget-list/' + params.Feed, params);
				}
			},
			{
				Icon: 'fa-file-text-o',
				Title: 'Template',
				Auth: [ 'Developer' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');
					dc.pui.SimpleApp.loadPage('/dcm/cms/carousel-widget-template/' + params.Feed, params);
				}
			},
			{
				Icon: 'fa-cog',
				Title: 'Properties',
				Auth: [ 'Admin', 'Editor' ],
				Op: function(e) {
					var params = entry.callTagFunc(widget, 'getParams');
					dc.pui.Dialog.loadPage('/dcm/cms/carousel-widget-props/' + params.Feed, params);
				}
			}
		])
	);
};

dc.pui.TagFuncs['dcm.CarouselWidget']['getParams'] = function(entry, node) {
	var pel = $(node).closest('*[data-cms-feed]').get(0);

	if (! pel)
		return null;

	return {
		Feed: $(pel).attr('data-cms-feed'),
		Path: $(pel).attr('data-cms-path'),
		Id: $(node).attr('id')
	};
};

if (! dc.pui.TagFuncs['dcm.IncludeFeed'])
	dc.pui.TagFuncs['dcm.IncludeFeed'] = { };

dc.pui.TagFuncs['dcm.IncludeFeed']['doCmsEdit'] = function(entry, node) {
	var pel = $(this).closest('*[data-cms-feed]').get(0);

	if (! pel)
		return;

	dc.pui.SimpleApp.loadPage('/dcm/feeds/edit-feed/' + $(pel).attr('data-cms-feed'), {
		Feed: $(pel).attr('data-cms-feed'),
		Path: $(pel).attr('data-cms-path')
	});
};

dc.pui.TagFuncs['dcm.IncludeFeed']['doCmsGetCommands'] = function(entry, node) {
	var cmds = [ ];

	if ($(node).attr('data-cms-reordered')) {
		cmds.push({
			Command: 'Reorder',
			Params: {
				PartId: $(this).attr('id'),
				Order: [ 1, 0 ]
			}
		});
	}

	return cmds;
};

if (! dc.pui.TagFuncs['dcm.ProductWidget'])
	dc.pui.TagFuncs['dcm.ProductWidget'] = { };

dc.pui.TagFuncs['dcm.ProductWidget']['doCmsEdit'] = function(entry, node) {
	dc.pui.Dialog.loadPage('/dcm/store/product-entry', {
		Id: $(node).attr('data-id')
	});
};

if (! dc.pui.TagFuncs['dc.Band'])
	dc.pui.TagFuncs['dc.Band'] = { };

dc.pui.TagFuncs['dc.Band']['doCmsGetCommands'] = function(entry, node) {
	var cmds = [ ];

	if ($(node).attr('data-cms-reordered')) {
		// TODO check final order - if 0, 1, 2, 3... then don't save

		cmds.push({
			Command: 'Reorder',
			Params: {
				PartId: $(this).attr('id'),
				Order: $(node).find('> div.dc-band-wrapper > *').map(function() { return $(this).attr('data-cms-band-pos'); }).get()
			}
		});
	}

	return cmds;
};

dc.pui.TagFuncs['dc.Band']['doCmsInitLayout'] = function(entry, node) {
	if (! $(node).attr('data-cms-reorder-enabled')) {
		console.log('reorder enable');

		var pos = 0;

		$(node).find('> div.dc-band-wrapper > *').each(function() {
			$(this).attr('data-cms-band-pos', pos + '');
			pos++;
		});

		$(node).attr('data-cms-reorder-enabled', 'true');
	}

	dc.cms.Loader.Sortable = Sortable.create($(node).find('> div.dc-band-wrapper').get(0), {
		onEnd: function (evt) {
			$(node).attr('data-cms-reordered', 'true');

			$('#dcuiCmsSaveTab').show();
		}
	});
};


// --------- utilities ---------


dc.cms.image = {
	// TODO static methods of image

	Loader: {
		loadGallery: function(galleryPath, callback, search) {
			dc.comm.sendMessage({
				Service: 'dcCoreServices',
				Feature: 'Vaults',
				Op: 'Custom',
				Body: {
					Vault: 'Galleries',
					Command: 'LoadMeta',
					Path: galleryPath,
					Params: {
						Search: search ? true : false
					}
				}
			}, function(resp) {
				var gallery = null;

				if ((resp.Result > 0) || ! resp.Body || ! resp.Body.Extra)
					gallery = new dc.cms.image.Loader.defaultGallery(galleryPath);
				else
					gallery = new dc.cms.image.Gallery(galleryPath, resp.Body.Extra);

				var thv = gallery.findVariation('thumb');

				// make sure there is always a thumb variation
				if (! thv)
					gallery.updateVariation({
						"ExactWidth": 152,
						"ExactHeight": 152,
						"Alias": "thumb",
						"Name": "Thumbnail"
					 });

				// TODO make sure there is always an automatic upload plan too

				callback(gallery, resp);
			});
		},
		defaultGallery: function(galleryPath) {
			return new dc.cms.image.Gallery(galleryPath, {
				"UploadPlans":  [
					 {
						"Steps":  [
							 {
								"Op": "AutoSizeUpload",
								"Variations":  [
									"full",
									"thumb"
								 ]
							 }
						 ] ,
						"Alias": "default",
						"Title": "Automatic"
					 }
				 ] ,
				"Variations":  [
					 {
						"MaxWidth": 2000,
						"MaxHeight": 2000,
						"Alias": "full",
						"Name": "Full"
					 } ,
					 {
						"ExactWidth": 152,
						"ExactHeight": 152,
						"Alias": "thumb",
						"Name": "Thumbnail"
					 }
				 ]
			 });
		}
	},
	Util: {
		formatVariation: function(vari) {
			if (!vari)
				return 'missing';

			var desc = 'Width ';

			if (vari.ExactWidth)
				desc += vari.ExactWidth;
			else if (vari.MaxWidth && vari.MinWidth)
				desc += 'between ' + vari.MinWidth + ' and ' + vari.MaxWidth;
			else if (vari.MaxWidth)
				desc += 'no more than ' + vari.MaxWidth;
			else if (vari.MinWidth)
				desc += 'at least ' + vari.MinWidth;
			else
				desc += 'unrestricted';

			desc += ' x Height ';

			if (vari.ExactHeight)
				desc += vari.ExactHeight;
			else if (vari.MaxHeight && vari.MinHeight)
				desc += 'between ' + vari.MinHeight + ' and ' + vari.MaxHeight;
			else if (vari.MaxHeight)
				desc += 'no more than ' + vari.MaxHeight;
			else if (vari.MinHeight)
				desc += 'at least ' + vari.MinHeight;
			else
				desc += 'unrestricted';

			return desc;
		},
		formatVariationSummary: function(vari) {
			if (!vari)
				return 'missing';

			var desc = '';

			if (vari.ExactWidth)
				desc += vari.ExactWidth;
			else if (vari.MaxWidth && vari.MinWidth)
				desc += '>=' + vari.MinWidth + ' <=' + vari.MaxWidth;
			else if (vari.MaxWidth)
				desc += '<=' + vari.MaxWidth;
			else if (vari.MinWidth)
				desc += '>=' + vari.MinWidth;
			else
				desc += 'any';

			desc += ' x ';

			if (vari.ExactHeight)
				desc += vari.ExactHeight;
			else if (vari.MaxHeight && vari.MinHeight)
				desc += '>=' + vari.MinHeight + ' <=' + vari.MaxHeight;
			else if (vari.MaxHeight)
				desc += '<=' + vari.MaxHeight;
			else if (vari.MinHeight)
				desc += '>=' + vari.MinHeight;
			else
				desc += 'any';

			return desc;
		}
	}
};

dc.cms.image.Gallery = function(path, meta) {
	this.Path = path;
	this.Meta = meta;
	this.Transfer = null;   // current transfer bucket
};

dc.cms.image.Gallery.prototype.topVariation = function(reqname) {
	var vari = this.findVariation(reqname);

	if (vari)
		return vari;

	vari = this.findVariation('full');

	if (vari)
		return vari;

	vari = this.findVariation('original');

	if (vari)
		return vari;

	if (this.Meta.Variations)
		return this.Meta.Variations[0];

	return null;
};

dc.cms.image.Gallery.prototype.findVariation = function(alias) {
	if (this.Meta.Variations) {
		for (var i = 0; i < this.Meta.Variations.length; i++) {
			var v = this.Meta.Variations[i];

			if (v.Alias == alias)
				return v;
		}
	}

	return null;
};

// replace or add the variation (unique alias)
dc.cms.image.Gallery.prototype.updateVariation = function(data) {
	if (! this.Meta.Variations)
		this.Meta.Variations = [ ];

	for (var i = 0; i < this.Meta.Variations.length; i++) {
		var v = this.Meta.Variations[i];

		if (v.Alias == data.Alias) {
			this.Meta.Variations[i] = data;
			return;
		}
	}

	this.Meta.Variations.push(data);
};

dc.cms.image.Gallery.prototype.removeVariation = function(alias) {
	if (! this.Meta.Variations)
		return;

	for (var i1 = 0; i1 < this.Meta.Variations.length; i1++) {
		var v = this.Meta.Variations[i1];

		if (v.Alias == alias) {
			this.Meta.Variations.splice(i1, 1);
			return;
		}
	}
};

dc.cms.image.Gallery.prototype.findShow = function(alias) {
	if (this.Meta.Shows) {
		for (var i = 0; i < this.Meta.Shows.length; i++) {
			var v = this.Meta.Shows[i];

			if (v.Alias == alias)
				return v;
		}
	}

	return null;
};

// replace or add the show (unique alias)
dc.cms.image.Gallery.prototype.updateShow = function(data) {
	if (! this.Meta.Shows)
		this.Meta.Shows = [ ];

	for (var i = 0; i < this.Meta.Shows.length; i++) {
		var v = this.Meta.Shows[i];

		if (v.Alias == data.Alias) {
			this.Meta.Shows[i] = data;
			return;
		}
	}

	this.Meta.Shows.push(data);
};

dc.cms.image.Gallery.prototype.removeShow = function(alias) {
	if (! this.Meta.Shows)
		return;

	for (var i1 = 0; i1 < this.Meta.Shows.length; i1++) {
		var v = this.Meta.Shows[i1];

		if (v.Alias == alias) {
			this.Meta.Shows.splice(i1, 1);
			return;
		}
	}
};

dc.cms.image.Gallery.prototype.findPlan = function(alias, enablegeneric) {
	if (this.Meta.UploadPlans) {
		for (var i = 0; i < this.Meta.UploadPlans.length; i++) {
			var v = this.Meta.UploadPlans[i];

			if (v.Alias == alias)
				return v;
		}
	}

	if (enablegeneric && (alias == 'default'))
		return this.addGenericPlan();

	return null;
};

dc.cms.image.Gallery.prototype.addGenericPlan = function() {
	if (!this.Meta.UploadPlans)
		this.Meta.UploadPlans = [ ];

	var varis = [ ];
	var plan = {
		"Steps":  [
			 {
				"Op": "AutoSizeUpload",
				"Variations": varis
			 }
		 ] ,
		"Alias": "default",
		"Title": "Automatic"
	};

	this.Meta.UploadPlans.push(plan);

	if (this.Meta.Variations) {
		for (var i = 0; i < this.Meta.Variations.length; i++) {
			var v = this.Meta.Variations[i];
			varis.push(v.Alias);
		}
	}

	return plan;
};

dc.cms.image.Gallery.prototype.formatVariation = function(alias) {
	var vari = this.findVariation(alias);

	return dc.cms.image.Util.formatVariation(vari);
};

dc.cms.image.Gallery.prototype.imageDetail = function(name, cb) {
	dc.comm.sendMessage({
		Service: 'dcCoreServices',
		Feature: 'Vaults',
		Op: 'Custom',
		Body: {
			Vault: 'Galleries',
			Command: 'ImageDetail',
			Path: this.Path + '/' + name + '.v'
		}
	}, function(resp) {
		if (cb)
			cb(resp);
	});
};
dc.cms.image.Gallery.prototype.save = function(cb) {
	dc.comm.sendMessage({
		Service: 'dcCoreServices',
		Feature: 'Vaults',
		Op: 'Custom',
		Body: {
			Vault: 'Galleries',
			Command: 'SaveMeta',
			Path: this.Path,
			Params: this.Meta
		}
	}, function(resp) {
		if (cb)
			cb(resp);
	});
};

dc.cms.image.Gallery.prototype.createProcessUploadTask = function(blobs, plan, token, bucket) {
	var or = new dc.lang.OperationResult();

	var steps = [ ];

	steps.push({
		Alias: 'ProcessImages',
		Title: 'Process Images',
		Params: {
		},
		Func: function(step) {
			var task = this;

			step.TotalAmount = 20;

			var pres = task.Store.Gallery.createProcessTask(blobs, plan);

			if (pres.hasErrors()) {
				task.error('Unable to process task');
				task.resume();
				return;
			}

			pres.Result.Observers.push(function(ctask) {
				task.Result = ctask.Result;
				task.resume();
			});

			pres.Result.ParentTask = task;
			pres.Result.ParentStep = step;

			step.Tasks = [ pres.Result ];

			pres.Result.run();
		}
	});

	steps.push({
		Alias: 'UploadImages',
		Title: 'Upload Images',
		Params: {
		},
		Func: function(step) {
			var task = this;

			var pres = task.Store.Gallery.createUploadTask(task.Result);

			if (pres.hasErrors()) {
				task.error('Unable to upload task');
				task.resume();
				return;
			}

			pres.Result.Observers.push(function(ctask) {
				task.resume();
			});

			pres.Result.ParentTask = task;
			pres.Result.ParentStep = step;

			step.Tasks = [ pres.Result ];

			pres.Result.run();
		}
	});

	var processtask = new dc.lang.Task(steps);

	processtask.Store = {
		Plan: plan,
		Vault: bucket,
		Gallery: this,
		Token: token
	};

	processtask.Result = [ ];

	or.Result = processtask;

	return or;
};

dc.cms.image.Gallery.prototype.createThumbsTask = function(path, plan, token, bucket) {
	var or = new dc.lang.OperationResult();

	var steps = [ ];

	// === DETAILS ===

	var funcDetailStep = function(task, fname) {
		task.Steps.push({
			Alias: 'ListImages',
			Title: 'List Images',
			Params: {
				FileName: fname
			},
			Func: function(step) {
				var task = this;

				step.Store = { };

				task.Store.Gallery.imageDetail(step.Params.FileName, function(rmsg) {
					if (rmsg.Result != 0) {
						dc.pui.Popup.alert('Error loading image details.');
						return;
					}

					var details = rmsg.Body.Extra.Files;
					var tfnd = false;
					var tvar = null;

					//console.log('detail for: ' + step.Params.FileName + ' --- ' + JSON.stringify(details, null, '\t'));

					for (var i = 0; i < details.length; i++) {
						var item = details[i];

						if (item.Alias == 'thumb') {
							tfnd = true;
						}
						else if (item.Alias == 'original') {
							tvar = 'original';
						}
						else if ((item.Alias == 'full') && (tvar == null)) {
							tvar = 'full';
						}
					}

					if (! tfnd && tvar) {
						var fullpath = '/galleries' + task.Store.Gallery.Path + '/' + step.Params.FileName + '.v/' + tvar + '.jpg'

						dc.util.File.loadBlob(fullpath, function(blob) {
							//console.log('blob for: ' + fullpath + ' --- ' + blob);

							var varis = [ task.Store.Gallery.findVariation('thumb') ];

							var ctaskres = dc.image.Tasks.createVariationsTask(blob, varis,
								task.Store.Gallery.Meta.Extension, task.Store.Gallery.Meta.Quality);

							if (ctaskres.hasErrors()) {
								task.error('Unable to create variations.');
								task.resume();
								return;
							}

							ctaskres.Result.Observers.push(function(ctask) {
								task.Result = ctask.Result;

								//console.log('blob variant for: ' + fullpath + ' --- ' + task.Result);

								// TODO support a callback on fail - do task.kill - handle own alerts
								step.Store.Transfer = new dc.transfer.Vault({
									Vault: task.Store.Vault,
									Progress: function(amt, title) {
										step.Amount = amt - 0;		// force numeric

										//console.log('# ' + amt + ' - ' + title);
									},
									Callback: function(e) {
										//console.log('callback done!');

										delete step.Store.Transfer;

										task.resume();
									}
								});

								var thpath = task.Store.Gallery.Path + '/' + step.Params.FileName
									+ '.v/thumb.jpg'

								step.Store.Transfer.upload(task.Result[0].Blob, thpath, task.Store.Token);

								//task.resume();
							});

							ctaskres.Result.ParentTask = task;
							ctaskres.Result.ParentStep = step;

							step.Tasks = [ ctaskres.Result ];

							ctaskres.Result.run();
						});
					}
					else {
						task.resume();
					}
				});
			}
		});
	};

	// === LISTING ===

	steps.push({
		Alias: 'ListImages',
		Title: 'List Images',
		Params: {
			Folder: path
		},
		Func: function(step) {
			var task = this;

			dc.comm.sendMessage({
				Service: 'dcCoreServices',
				Feature: 'Vaults',
				Op: 'ListFiles',
				Body: {
					Vault: 'Galleries',
					Path: step.Params.Folder
				}
			}, function(resp) {
				if (resp.Result > 0) {
					dc.pui.Popup.alert(resp.Message);	// TODO do we send an alert in task progress?
					return;
				}

				var items = resp.Body;

				for (var i = 0; i < items.length; i++) {
					var item = items[i];

					if (item.IsFolder)
						continue;

					funcDetailStep(task, item.FileName);
				}

				task.resume();
			});
		}
	});

	/*
	for (var i = 0; i < files.length; i++) {
		var file = files[i];



	}
	*/

	var thumbtask = new dc.lang.Task(steps);

	thumbtask.Store = {
		Path: path,
		Vault: bucket ? bucket : 'Galleries',
		Gallery: this,
		Token: token,
		Plan: plan
	};

	or.Result = thumbtask;

	return or;
};

dc.cms.image.Gallery.prototype.createUploadTask = function(files, token, bucket) {
	var or = new dc.lang.OperationResult();

	var steps = [ ];

	for (var i = 0; i < files.length; i++) {
		var file = files[i];

		for (var n = 0; n < file.Variants.length; n++) {
			var vari = file.Variants[n];

			if ((n == 0) && ! file.Name && file.Blob && file.Blob.name)
				file.Name = dc.util.File.toCleanFilename(file.Blob.name);

			steps.push({
				Alias: 'UploadImage',
				Title: 'Upload Image',
				Params: {
					File: file,
					Variant: vari
				},
				Func: function(step) {
					var task = this;

					// TODO support a callback on fail - do task.kill - handle own alerts
					step.Store.Transfer = new dc.transfer.Vault({
						Vault: task.Store.Vault,
						Progress: function(amt, title) {
							step.Amount = amt - 0;		// force numeric

							//console.log('# ' + amt + ' - ' + title);
						},
						Callback: function(e) {
							//console.log('callback done!');

							delete step.Store.Transfer;

							task.resume();
						}
					});

					var path = task.Store.Gallery.Path + '/' + step.Params.File.Name
						+ '.v/' + step.Params.Variant.FileName;

					step.Store.Transfer.upload(step.Params.Variant.Blob, path, task.Store.Token);
				}
			});
		}
	}

	var uploadtask = new dc.lang.Task(steps);

	uploadtask.Store = {
		Vault: bucket ? bucket : 'Galleries',
		Gallery: this,
		Token: token,
		Blob: null
	};

	or.Result = uploadtask;

	return or;
};


dc.cms.image.Gallery.prototype.createProcessTask = function(blobs, plan) {
	var or = new dc.lang.OperationResult();

	var steps = [ ];

	for (var i = 0; i < blobs.length; i++) {
		var blob = blobs[i];

		var bname = blob.Name ? blob.Name : null;

		if (! bname) {
			bname = 'unknown';

			if (blob.name) {
				bname = dc.util.File.toCleanFilename(blob.name);

				// remove the extension
				var bpos = bname.lastIndexOf('.');

				if (bpos)
					bname = bname.substr(0, bpos);
			}
		}

		steps.push({
			Alias: 'ProcessImage',
			Title: 'Process Image',
			Params: {
				Blob: blob,
				Name: bname
			},
			Func: function(step) {
				var task = this;

				var pres = task.Store.Gallery.createPlanTask(step.Params.Blob, step.Params.Name, plan);

				if (pres.hasErrors()) {
					task.error('Unable to create plan');
					task.resume();
					return;
				}

				pres.Result.Observers.push(function(ctask) {
					if (ctask.Result) {
						task.Result.push({
							Name: step.Params.Name,
							Variants: ctask.Result
						});
					}

					task.resume();
				});

				pres.Result.ParentTask = task;
				pres.Result.ParentStep = step;

				step.Tasks = [ pres.Result ];

				pres.Result.run();
			}
		});
	}

	var processtask = new dc.lang.Task(steps);

	processtask.Store = {
		Plan: plan,
		Gallery: this
	};

	processtask.Result = [ ];

	or.Result = processtask;

	return or;
};


dc.cms.image.Gallery.prototype.createPlanTask = function(blob, name, plan) {
	var or = new dc.lang.OperationResult();

	if (!plan)
		plan = 'default';

	var planrec = this.findPlan(plan, true);

	if (!planrec) {
		or.error('Missing upload plan.');
		return or;
	}

	var steps = [ ];

	for (var i = 0; i < planrec.Steps.length; i++) {
		var sinfo = planrec.Steps[i];

		if (sinfo.Op == "AutoSizeUpload") {
			steps.push({
				Alias: 'Resize',
				Title: 'Resize and Scale Image',
				Params: {
					StepInfo: sinfo
				},
				Func: function(step) {
					var task = this;

					var vlist = step.Params.StepInfo.Variations;
					var varis = [];

					for (var n = 0; n < vlist.length; n++)
						varis.push(task.Store.Gallery.findVariation(vlist[n]));

					var ctaskres = dc.image.Tasks.createVariationsTask(task.Store.Blob, varis,
						task.Store.Gallery.Meta.Extension, task.Store.Gallery.Meta.Quality);

					if (ctaskres.hasErrors()) {
						task.error('Unable to create variations.');
						task.resume();
						return;
					}

					ctaskres.Result.Observers.push(function(ctask) {
						task.Result = ctask.Result;
						task.resume();
					});

					ctaskres.Result.ParentTask = task;
					ctaskres.Result.ParentStep = step;

					step.Tasks = [ ctaskres.Result ];

					ctaskres.Result.run();
				}
			});
		}
		// TODO add other operations
		else {
			or.error('Unknown step: ' + step.Op);
			return or;
		}
	}

	var plantask = new dc.lang.Task(steps);

	plantask.Store = {
		Blob: blob,
		Name: name,
		Plan: planrec,
		Gallery: this
	};

	plantask.Result = [ ];

	or.Result = plantask;

	return or;
};
