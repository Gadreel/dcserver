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
	init: function(options) {
		// TODO lookup options from dc.handler

		if (! options) {
			options = {
				AuthTags: [ 'Editor', 'Clerk', 'Admin', 'Developer' ],
				Tab: 'Dashboard',
				Menu: 'dcmGeneral',
				Params: { }
			};
		}

		var itab = $('<div id="dcuiAppTab"><i class="fa fa-angle-double-right"></i></div>');

		itab.click(function (e) {
			//dc.pui.App.start(options);

			dc.pui.Popup.menu('dcmMain');

			e.preventDefault();
			return false;
		});

		$('*[data-dccms-edit]').each(function() {
			var auth = $(this).attr('data-dccms-edit');

			if (auth && dc.user.isAuthorized(auth.split(',')))
				$(this).addClass('dcuiCms');
		});

		if (! options.AuthTags || dc.user.isAuthorized(options.AuthTags))
			$('#dcuiMain').append(itab);
	}
};

dc.pui.Apps.Menus.dcmEmpty = {
	Options: [
	]
};

dc.pui.Apps.Menus.dcmMain = {
	Options: [
		{
			Title: 'CMS',
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
			Auth: [ 'Admin' ],
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


dc.pui.Apps.Menus.dcmTextWidget = {
	Tabs: [
		{
			Alias: 'Content',
			Title: 'Content',
			Auth: [ 'Admin', 'Editor' ],
			Path: '/dcm/cms/text-widget-content'			// TODO add feed
		},
		{
			Alias: 'Properties',
			Title: 'Properties',
			Auth: [ 'Developer' ],
			Path: '/dcm/cms/text-widget-props'			// TODO add feed
		}
	]
};

if (! dc.pui.TagFuncs['dc.TextWidget'])
	dc.pui.TagFuncs['dc.TextWidget'] = { };

dc.pui.TagFuncs['dc.TextWidget']['doCmsEdit'] = function(entry, node) {
	var pel = $(this).closest('*[data-cms-feed]').get(0);

	if (! pel)
		return;

	// TODO make it so we can pass the tabs in here and configure them to the feed name (to support overrides)
	dc.pui.App.startTab({
		Tab: 'Content',
		Menu: 'dcmTextWidget',
		Params: {
			Feed: $(pel).attr('data-cms-feed'),
			Path: $(pel).attr('data-cms-path'),
			Id: $(node).attr('id')
		}
	});
};

dc.pui.Apps.Menus.dcmImageWidget = {
	Tabs: [
		{
			Alias: 'Properties',
			Title: 'Properties',
			Auth: [ 'Admin', 'Editor' ],
			Path: '/dcm/cms/image-widget-props'			// TODO add feed
		},
		{
			Alias: 'Template',
			Title: 'Template',
			Auth: [ 'Developer' ],
			Path: '/dcm/cms/image-widget-template'			// TODO add feed
		}
	]
};

if (! dc.pui.TagFuncs['dcm.ImageWidget'])
	dc.pui.TagFuncs['dcm.ImageWidget'] = { };

dc.pui.TagFuncs['dcm.ImageWidget']['doCmsEdit'] = function(entry, node) {
	var pel = $(this).closest('*[data-cms-feed]').get(0);

	if (! pel)
		return;

	// TODO make it so we can pass the tabs in here and configure them to the feed name (to support overrides)
	dc.pui.App.startTab({
		Tab: 'Properties',
		Menu: 'dcmImageWidget',
		Params: {
			Feed: $(pel).attr('data-cms-feed'),
			Path: $(pel).attr('data-cms-path'),
			Id: $(node).attr('id')
		}
	});
};

dc.pui.Apps.Menus.dcmGalleryWidget = {
	Tabs: [
		{
			Alias: 'List',
			Title: 'List',
			Auth: [ 'Admin', 'Editor' ],
			Path: '/dcm/cms/gallery-widget-list'			// TODO add feed
		},
		{
			Alias: 'Properties',
			Title: 'Properties',
			Auth: [ 'Developer' ],
			Path: '/dcm/cms/gallery-widget-props'			// TODO add feed
		},
		{
			Alias: 'Template',
			Title: 'Template',
			Auth: [ 'Developer' ],
			Path: '/dcm/cms/gallery-widget-template'			// TODO add feed
		}
	]
};

if (! dc.pui.TagFuncs['dcm.GalleryWidget'])
	dc.pui.TagFuncs['dcm.GalleryWidget'] = { };

dc.pui.TagFuncs['dcm.GalleryWidget']['doCmsEdit'] = function(entry, node) {
	var pel = $(this).closest('*[data-cms-feed]').get(0);

	if (! pel)
		return;

	// TODO make it so we can pass the tabs in here and configure them to the feed name (to support overrides)
	dc.pui.App.startTab({
		Tab: 'List',
		Menu: 'dcmGalleryWidget',
		Params: {
			Feed: $(pel).attr('data-cms-feed'),
			Path: $(pel).attr('data-cms-path'),
			Id: $(node).attr('id'),
			GalleryPath: $(node).attr('data-path'),
			GalleryShow: $(node).attr('data-show')
		}
	});
};

if (! dc.pui.TagFuncs['dcm.IncludeFeed'])
	dc.pui.TagFuncs['dcm.IncludeFeed'] = { };

dc.pui.TagFuncs['dcm.IncludeFeed']['doCmsEdit'] = function(entry, node) {
	var pel = $(this).closest('*[data-cms-feed]').get(0);

	if (! pel)
		return;

	dc.pui.Dialog.loadPage('/dcm/feeds/edit-feed/' + $(pel).attr('data-cms-feed'), {
		Feed: $(pel).attr('data-cms-feed'),
		Path: $(pel).attr('data-cms-path')
	});
};

if (! dc.pui.TagFuncs['dcm.ProductWidget'])
	dc.pui.TagFuncs['dcm.ProductWidget'] = { };

dc.pui.TagFuncs['dcm.ProductWidget']['doCmsEdit'] = function(entry, node) {
	dc.pui.Dialog.loadPage('/dcm/store/product-entry', {
		Id: $(node).attr('data-id')
	});
};



dc.cms.image = {
	// TODO static methods of image

	Loader: {
		loadGallery: function(galleryPath, callback) {
			dc.comm.sendMessage({
				Service: 'dcCoreServices',
				Feature: 'Vaults',
				Op: 'Custom',
				Body: {
					Vault: 'Galleries',
					Command: 'LoadMeta',
					Path: galleryPath
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
