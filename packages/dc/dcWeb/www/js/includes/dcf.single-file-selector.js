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

dc.pui.Tags['dcf.SingleFileSelector'] = function(entry, node) {
	var input = new dc.pui.controls.SingleFileSelector(entry, node);
};

dc.pui.controls.SingleFileSelector = function(entry, node) {
	this.init(entry, node);
};

dc.pui.controls.SingleFileSelector.prototype = new dc.pui.controls.Input();

dc.pui.controls.SingleFileSelector.prototype.init = function(entry, node) {
	dc.pui.controls.Input.prototype.init.call(this, entry, node);

	var input = this;

	$(node).closest('.dc-control').find('span.open').click(function(e) {
		input.open(entry, node);

		e.preventDefault();
		return false;
	});
};

dc.pui.controls.SingleFileSelector.prototype.open = function(entry, node) {
	var ctrl = this;

	var source = $(node).closest('.dc-field').attr('data-dc-file-source');

	if (! source)
		source = 'Files';

	if (source == 'Files') {
		dc.pui.Dialog.loadPage('/dcm/files/chooser', {
			Callback: function(files) {
				if (files) {
					var path = '/files' + files[0].FullPath;

					ctrl.setValue(path);

					var info = ctrl.Form.FreezeInfo;

					if (info)
						info[ctrl.Record].Values[ctrl.Field] = path;
				}

				$(node).focus();
			}
		});
	}
	else if (source == 'CDNFiles') {
		dc.pui.Dialog.loadPage('/dcm/cdn/browser', {
			SelectMode: true,
			Callback: function(files) {
				if (files) {
					var path = files[0];

					ctrl.setValue(path);

					var info = ctrl.Form.FreezeInfo;

					if (info)
						info[ctrl.Record].Values[ctrl.Field] = path;
				}

				$(node).focus();
			}
		});
	}
	else if (source == 'CDNVideos') {
		dc.pui.Dialog.loadPage('/dcm/cdn/video-browser', {
			SelectMode: true,
			Callback: function(files) {
				if (files) {
					var path = files[0];

					ctrl.setValue(path);

					var info = ctrl.Form.FreezeInfo;

					if (info)
						info[ctrl.Record].Values[ctrl.Field] = path;
				}

				$(node).focus();
			}
		});
	}

};
