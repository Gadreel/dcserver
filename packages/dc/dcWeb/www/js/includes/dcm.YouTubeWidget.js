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

if (! window.onYouTubeIframeAPIReady) {
	console.log('setting onYouTubeIframeAPIReady');

	window.onYouTubeIframeAPIReady = function() {
		const ytplayers = $('#sectResourceList div[data-dc-tag="dcm.YouTubeWidget"] iframe');

		for (let i = 0; i < ytplayers.length; i++) {
			const player = new YT.Player(ytplayers.get(i), {
				events: {
					onReady: function(e) {
						const widget = $(e.target.g).closest('.dc-widget').get(0) || undefined;

						if ($(widget).data('dcControl'))
							$(widget).data('dcControl').onReady(e);
					},
					onStateChange: function(e) {
						const widget = $(e.target.g).closest('.dc-widget').get(0) || undefined;

						if ($(widget).data('dcControl'))
							$(widget).data('dcControl').onStateChange(e);
					}
				}
			});
		}
	}
}
else {
	console.log('onYouTubeIframeAPIReady already loaded elsewhere, this impairs the initialization of scripting for the newly loaded page');
}

dc.pui.Tags['dcm.YouTubeWidget'] = function(entry, node) {
	$(node).data('dcControl', {
		onReady: function(e) {
			e.VideoId = e.target.playerInfo.videoData.video_id;

			console.log('player ready: ' + e.VideoId);

			var func = $(node).attr('data-dc-on-ready');

			if (func)
				entry.callPageFunc(func, e, node, this);
		},
		onStateChange: function(e) {
			if (e.data == YT.PlayerState.PLAYING) {
				e.VideoId = e.target.playerInfo.videoData.video_id;

				console.log('video playing: ' + e.VideoId);

				var func = $(node).attr('data-dc-state-change');

				if (func)
					entry.callPageFunc(func, e, node, this);
			}
		}
	});
};
