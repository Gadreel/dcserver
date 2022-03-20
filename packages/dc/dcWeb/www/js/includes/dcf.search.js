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

dc.pui.Tags['dcf.Search'] = function(entry, node) {
	var input = new dc.pui.controls.Search(entry, node);
};

dc.pui.controls.Search = function(entry, node) {
	this.init(entry, node);
};

dc.pui.controls.Search.prototype = new dc.pui.controls.Input();

dc.pui.controls.Search.prototype.init = function(entry, node) {
	dc.pui.controls.Input.prototype.init.call(this, entry, node);

	var input = this;

	input.Store = { };

	input.Store.CompletePosition = -1;
	input.Store.CompleteOpen = false;
	input.Store.BubbleId = dc.util.Uuid.create().replaceAll('-','');
	input.Store.ResultHandler = null;
	input.Store.QueryHandler = null;
	input.Store.ListSizingHandler = null;

	var bubble = $('<div>')
		.attr('id', input.Store.BubbleId)
		.addClass('dcf-search-bubble');

	$(entry.Layer.ContentShell)
		.dcappend(bubble);

	$(node).closest('.dc-control').find('span.open').click(function(e) {
		input.search(entry, node);
	});

	$(node).closest('.dc-control').find('span.close').click(function(e) {
		input.close(entry, node);
	});

	$(node).keyup(function(e) {
		if ((e.keyCode || e.which) != '9') 		// don't validate on tab key
			input.validate();
	});

	/*
	$(node).closest('.dc-field').find('.dc-input-hint').click(function(e) {
		input.setValue($(this).text());
	});
	*/

	$(node).keypress(function(e) {
			var keycode = (event.keyCode ? event.keyCode : event.which);

			if (keycode == '13') {
				if (input.Store.CompletePosition > -1) {
					input.select(entry, node);
				}
				else {
					input.search(entry, node);
				}

				e.preventDefault();
				return false;
			}

			return true;
	});

	$(node).keydown(function(e) {
			var keycode = (event.keyCode ? event.keyCode : event.which);

			if (keycode == '40') {
				input.Store.CompletePosition++;

				input.updateList(entry, node);

				e.preventDefault();
				return false;
			}
			else if (keycode == '38') {
				input.Store.CompletePosition--;

				input.updateList(entry, node);

				e.preventDefault();
				return false;
			}
			else if (keycode == '27') {
				input.close(entry, node);

				e.preventDefault();
				return false;
			}
			else if ((keycode == '8') || (keycode == '37')) {
				input.close(entry, node);
			}

			return true;
	});

	input.updatePosition(entry, node);
};

dc.pui.controls.Search.prototype.open = function(entry, node, list) {
	var input = this;

	var bubble = $('#' + input.Store.BubbleId);

	input.Store.CompletePosition = 0;
	input.Store.CompleteOpen = true;

	var clck = function(e) {
		input.Store.CompletePosition =  $(e.target).index();

		input.select(entry, node);

		e.preventDefault();
		return false;
	};

	$(list).click(clck);
	$($(list).find('li').get(0)).addClass('selected');

	bubble
		.empty()
		.dcappend(list)
		.show();

	bubble.get(0).scrollTop = 0;
};

dc.pui.controls.Search.prototype.openEmpty = function(entry, node, content) {
	var input = this;

	var bubble = $('#' + input.Store.BubbleId);

	input.Store.CompletePosition = -1;
	input.Store.CompleteOpen = true;

	if (! content)
		content = $('<div>').addClass('no-match').text('no matches found');

	bubble
		.empty()
		.dcappend(content)
		.show();

	bubble.get(0).scrollTop = 0;
};

dc.pui.controls.Search.prototype.close = function(entry, node) {
	var input = this;

	var bubble = $('#' + input.Store.BubbleId);

	bubble
		.empty()
		.hide();

	input.Store.CompletePosition = -1;
	input.Store.CompleteOpen = false;
};

dc.pui.controls.Search.prototype.updatePosition = function(entry, node) {
	var input = this;

	var bubble = $('#' + input.Store.BubbleId);
	var popover = bubble.get(0);

	// TODO enhance here to suppot other layers
	var sctop = $('html').scrollTop();
	var targetRect = node.getBoundingClientRect();
	var popoverRect = popover.getBoundingClientRect();

	popover.style.top = (sctop + targetRect.top + targetRect.height) + 'px';
	popover.style.left = targetRect.left + 'px';
	popover.style.width = targetRect.width + 'px';

	if (input.Store.ListSizingHandler)
		input.Store.ListSizingHandler(popover);
};

dc.pui.controls.Search.prototype.updateList = function(entry, node) {
	var input = this;

	var bubble = $('#' + input.Store.BubbleId);

	var li = bubble.find('ul li');

	li.removeClass('selected');

	if (input.Store.CompletePosition >= li.length)
		input.Store.CompletePosition = li.length - 1;

	if (input.Store.CompletePosition < -1)
		input.Store.CompletePosition = -1;

	if (input.Store.CompletePosition >= 0) {
		var parent = bubble.get(0);
		var child = li.get(input.Store.CompletePosition);

		$(child).addClass('selected');

		var parentRect = parent.getBoundingClientRect();

		var childRect = child.getBoundingClientRect();

		var isViewable = (childRect.top >= parentRect.top) && (childRect.bottom <= parentRect.top + parent.clientHeight);

		if (! isViewable) {
			const scrollTop = childRect.top - parentRect.top;
			const scrollBot = childRect.bottom - parentRect.bottom;

			if (Math.abs(scrollTop) < Math.abs(scrollBot)) {
					// we're near the top of the list
					parent.scrollTop += scrollTop;
			}
			else {
					// we're near the bottom of the list
					parent.scrollTop += scrollBot;
			}
		}
	}
};

dc.pui.controls.Search.prototype.select = function(entry, node) {
	var input = this;

	var bubble = $('#' + input.Store.BubbleId);

	var li = bubble.find('ul li');

	var value = $(li.get(input.Store.CompletePosition)).text();

	console.log('selected: ' + value);

	if (input.Store.ResultHandler)
		input.Store.ResultHandler(input.Store.CompletePosition, value);

	input.close(entry, node);

	//$(node).val('').focus();
};

dc.pui.controls.Search.prototype.withResultHandler = function(entry, node, handler) {
	var input = this;

	input.Store.ResultHandler = handler;
}

dc.pui.controls.Search.prototype.withQueryHandler = function(entry, node, handler) {
	var input = this;

	input.Store.QueryHandler = handler;
}

dc.pui.controls.Search.prototype.withListSizingHandler = function(entry, node, handler) {
	var input = this;

	input.Store.ListSizingHandler = handler;

	input.updatePosition(entry, node);
}

dc.pui.controls.Search.prototype.search = function(entry, node) {
	var input = this;

	var term = $(node).val();

	if (! term || (term.length < 3)) {
		input.close(entry, node);
		return;
	}

	if (input.Store.QueryHandler)
		input.Store.QueryHandler(term.trim());
};
