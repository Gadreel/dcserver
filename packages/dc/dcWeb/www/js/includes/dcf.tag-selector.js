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

dc.pui.Tags['dcf.TagSelector'] = function(entry, node) {
	var input = new dc.pui.controls.TagSelector(entry, node);
};

dc.pui.controls.TagSelector = function(entry, node) {
	// before init so we don't clear after init if init sets defaults
	this.Values = [ ];
	this.Trees = [ ];

	this.init(entry, node);
};

dc.pui.controls.TagSelector.prototype = new dc.pui.controls.ListInput();

dc.pui.controls.TagSelector.prototype.init = function(entry, node) {
	var ctrl = this;

	dc.pui.controls.ListInput.prototype.init.call(this, entry, node);

	if ($(node).attr('data-dc-tag-trees'))
		ctrl.Trees = $(node).attr('data-dc-tag-trees').split(',');

	$(node).find('.dc-tag-selector-btn a').click(function(e) {
		dc.pui.Dialog.loadPage('/dcr/meta/browse-tag-trees', {
			Trees: ctrl.Trees,
			Selected: ctrl.Values,
			Callback: function(selected) {
				ctrl.Values = selected;

				var info = ctrl.Form.FreezeInfo;

				if (info)
					info[ctrl.Record].Values[ctrl.Field] = selected;

				ctrl.updateList();
			}
		});

		e.preventDefault();
		return false;
	});

	var uplist = $(node).find('.dc-tag-selector-listing');

	uplist.dcappend(
		$('<table>')
			.addClass('dc-table-break-wide dc-table dc-table-reflow dc-table-stripe')
			.dcappend(
				$('<tbody>')
			)
	);
};

dc.pui.controls.TagSelector.prototype.setValue = function(values) {
	var ctrl = this;

	if ((values == null) || (values.length == 0)) {
		ctrl.Values = [ ];
		ctrl.updateList();
		return;
	}

	dc.comm.call('dcmServices.Meta.LoadTagTitles', values, function(rmsg) {
		if (rmsg.Code != 0) {
			// don't alert
			return;
		}

		ctrl.Values = rmsg.Result;
		ctrl.updateList();
	});
};

dc.pui.controls.TagSelector.prototype.thawValue = function(values) {
	var ctrl = this;

	if ((values == null) || (values.length == 0)) {
		ctrl.Values = [ ];
	}
	else {
		ctrl.Values = values;
	}

	ctrl.updateList();
};

dc.pui.controls.TagSelector.prototype.getValue = function() {
	return $(this.Values).map(function() { return this.Path }).get();
};

dc.pui.controls.TagSelector.prototype.freezeValue = function(values) {
	var ctrl = this;

	return ctrl.Values;
};

dc.pui.controls.TagSelector.prototype.updateList = function(values) {
	var ctrl = this;

	var taglist = $('#fld' + this.Id + ' .dc-tag-selector-listing tbody');

	taglist.empty();

	for (var i = 0; i < ctrl.Values.length; i++) {
		var value = ctrl.Values[i];

		taglist.dcappend(
			$('<tr>')
				.addClass('dc-tag-selector-entry')
				.dcappend(
					$('<td>').text(value.Title)
				)
		);
	}
}
