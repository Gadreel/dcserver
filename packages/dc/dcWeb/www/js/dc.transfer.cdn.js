if (! dc.transfer)
	dc.transfer = {};

dc.transfer.cdn = {
	Upload: function(options) {
		var defaults = {
			Callback: null,
			Progress: null
		};

		this.settings = $.extend( {}, defaults, options);

		this.data = {
			presign: null,
			file: null,
			path: null,
			params: null,
			canclick: false,
			bytessent: 0
		};

		// ask the backend Uploader for a channel to connect to
		this.upload = function(blob, path, params, tx) {
			console.log(new Date() + ': Requesting');

			var buck = this;

			buck.data.file = blob;
			buck.data.path = path;
			buck.data.params = params;

			dc.comm.call('dcmServices.CDN.PresignUpload', { Path: buck.data.path }, function(rmsg) {
				//console.log('start: ' +  rmsg.Result);

				if (rmsg.Code == 0) {
					console.log(new Date() + ': Streaming');

					// now Gateway and Uploader can talk to each other, so start sending the file
					buck.data.presign = rmsg.Result;

					buck.send();
				}
				else {
					dc.pui.Popup.alert('Error requesting upload channel.');
				}
			});
		};

		this.send = function() {
			var buck = this;

			console.log(new Date() + ': Sending: ' + buck.data.path);

			var xhr = new XMLHttpRequest();

			xhr.open("PUT", buck.data.presign.Url, true);

			xhr.setRequestHeader('Content-Type', buck.data.presign.ContentType);
			xhr.setRequestHeader('X-Amz-Date', buck.data.presign.Stamp);

			xhr.onreadystatechange = function() {
				console.log('state change: ' + xhr.readyState + ' status: ' + xhr.status);

				// do nothing if user clicked cancel
				if (buck.data.canclick)
					return;

				if (xhr.readyState == 4) {
					// if already uploaded something, and get 400 or 0 then you have been cancelled
					if (((xhr.status == 400) || (xhr.status == 0)) && (buck.data.bytessent > 0)) {
						dc.pui.Popup.alert('Processed Cancel Request.');
						return;
					}

					if (xhr.status != 200) {
						console.log('response: ' + xhr.response);
						dc.pui.Popup.alert('Streaming halted with error.');
						return;
					}

					if (buck.settings.Progress)
						buck.settings.Progress(96, 'Verifying');

					buck.verifyFile();
				}
			};

			xhr.upload.onprogress = function(e) {
				//console.log('progress: ' +  e.loaded);

				if (e.lengthComputable) {
					buck.data.bytessent = e.loaded;

					var p1 = (buck.data.bytessent * 100 / buck.data.file.size).toFixed();

					if (p1 > 96)
						p1 = 96;

					if (buck.settings.Progress)
						buck.settings.Progress(p1, dc.transfer.fmtFileSize(buck.data.bytessent) + ' of ' + dc.transfer.fmtFileSize(buck.data.ftotal));
				}
			};

			xhr.send(buck.data.file);
		};

		this.verifyFile = function() {
			console.log(new Date() + ': Verifying');

			var buck = this;

			dc.comm.call('dcmServices.CDN.FileInfo', { Path: buck.data.path }, function(rmsg) {
				//console.log('start: ' +  rmsg.Result);

				if (rmsg.Code == 0) {
					console.log('verify: ' +  rmsg.Result.Size);

					if (rmsg.Result.Size != buck.data.file.size) {
						dc.pui.Popup.alert('File size does not match, the upload may be corrupted.', function() {
							if (buck.settings.Callback)
								buck.settings.Callback(buck.data.path);
						});
					}
					else {
						if (buck.settings.Callback)
							buck.settings.Callback(buck.data.path);
					}
				}
				else {
					dc.pui.Popup.alert('Error requesting upload channel.');
				}
			});
		};
	},
	UploadVideo: function(options) {
		var defaults = {
			Callback: null,
			Progress: null
		};

		this.settings = $.extend( {}, defaults, options);

		this.data = {
			presign: null,
			file: null,
			path: null,
			params: null,
			canclick: false,
			bytessent: 0
		};

		// ask the backend Uploader for a channel to connect to
		this.upload = function(blob, path, params, tx) {
			console.log(new Date() + ': Requesting');

			var buck = this;

			buck.data.file = blob;
			buck.data.path = path;
			buck.data.params = params;

			dc.comm.call('dcmServices.CDN.VideoPresignUpload', { Path: buck.data.path }, function(rmsg) {
				//console.log('start: ' +  rmsg.Result);

				if (rmsg.Code == 0) {
					console.log(new Date() + ': Streaming');

					// now Gateway and Uploader can talk to each other, so start sending the file
					buck.data.presign = rmsg.Result;

					buck.send();
				}
				else {
					dc.pui.Popup.alert('Error requesting upload channel.');
				}
			});
		};

		this.send = function() {
			var buck = this;

			console.log(new Date() + ': Sending: ' + buck.data.path);

			var xhr = new XMLHttpRequest();

			xhr.open("PUT", buck.data.presign.Url, true);

			xhr.setRequestHeader('Content-Type', buck.data.presign.ContentType);
			xhr.setRequestHeader('X-Amz-Date', buck.data.presign.Stamp);

			xhr.onreadystatechange = function() {
				console.log('state change: ' + xhr.readyState + ' status: ' + xhr.status);

				// do nothing if user clicked cancel
				if (buck.data.canclick)
					return;

				if (xhr.readyState == 4) {
					// if already uploaded something, and get 400 or 0 then you have been cancelled
					if (((xhr.status == 400) || (xhr.status == 0)) && (buck.data.bytessent > 0)) {
						dc.pui.Popup.alert('Processed Cancel Request.');
						return;
					}

					if (xhr.status != 200) {
						console.log('response: ' + xhr.response);
						dc.pui.Popup.alert('Streaming halted with error.');
						return;
					}

					if (buck.settings.Progress)
						buck.settings.Progress(96, 'Verifying');

						if (buck.settings.Callback)
							buck.settings.Callback(buck.data.path);
				}
			};

			xhr.upload.onprogress = function(e) {
				//console.log('progress: ' +  e.loaded);

				if (e.lengthComputable) {
					buck.data.bytessent = e.loaded;

					var p1 = (buck.data.bytessent * 100 / buck.data.file.size).toFixed();

					if (p1 > 96)
						p1 = 96;

					if (buck.settings.Progress)
						buck.settings.Progress(p1, dc.transfer.fmtFileSize(buck.data.bytessent) + ' of ' + dc.transfer.fmtFileSize(buck.data.ftotal));
				}
			};

			xhr.send(buck.data.file);
		};
	},

	Util: {
		// allow only these: - _ .
		toCleanFilename: function(name) {
			name = dc.util.File.toLegalFilename(name);

			if (! name)
				return null;

			name = name.replace(new RegExp(" ", 'g'), "-").replace(new RegExp("%", 'g'), "_").replace(new RegExp("@", 'g'), "_")
					.replace(new RegExp("#", 'g'), "_").replace(new RegExp(",", 'g'), "_")
					.replace(new RegExp("~", 'g'), "_").replace(new RegExp("`", 'g'), "_").replace(new RegExp("!", 'g'), "_")
					.replace(new RegExp("\\$", 'g'), "_").replace(new RegExp("\\^", 'g'), "_").replace(new RegExp("&", 'g'), "_")
					.replace(new RegExp("&", 'g'), "_").replace(new RegExp("=", 'g'), "_").replace(new RegExp("\\+", 'g'), "-")
					.replace(new RegExp("{", 'g'), "_").replace(new RegExp("}", 'g'), "_").replace(new RegExp("\\[", 'g'), "_")
					.replace(new RegExp("\\]", 'g'), "_").replace(new RegExp(";", 'g'), "_").replace(new RegExp("'", 'g'), "_")
					.replace(new RegExp("()", 'g'), "_").replace(new RegExp(")", 'g'), "_")
					.replace(new RegExp("<", 'g'), "_").replace(new RegExp(">", 'g'), "_");

			var fname = '';
			var skipon = false;

			for (var i = 0; i < name.length; i++) {
				var c = name.charAt(i);

				if ((c == '-') || (c =='_')) {
					if (skipon)
						continue;

					skipon = true;
				}
				else {
					skipon = false;
				}

				fname += c;
			}

			return fname;
		},
		uploadFiles: function(files, callback, params) {
			var steps = [ ];
			var cleanfiles = [ ];

			for (var i = 0; i < files.length; i++) {
				var file = files[i];

				if (! file.File)
					file = {
						File: file
					};

				if (! file.Name)
					file.Name = dc.transfer.cdn.Util.toCleanFilename(file.File.name);

				cleanfiles.push(file);

				steps.push({
					Alias: 'UploadFile',
					Title: 'Upload File',
					Params: {
						File: file.File,
						Path: file.Path,
						FileName: file.Name,
						Params: params
					},
					Func: function(step) {
						var task = this;

						// TODO support a callback on fail - do task.kill - handle own alerts
						step.Store.Transfer = new dc.transfer.cdn.Upload({
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

						var path = '';

						if (step.Params.Path)
							path += step.Params.Path;

						step.Store.Transfer.upload(step.Params.File, path + '/' + step.Params.FileName, step.Params.Params);
					}
				});
			}

			var uploadtask = new dc.lang.Task(steps, function(res) {
				if (callback)
					callback(steps, cleanfiles);
			});

			return uploadtask;
		},
		uploadVideos: function(files, callback, params) {
			var steps = [ ];
			var cleanfiles = [ ];

			for (var i = 0; i < files.length; i++) {
				var file = files[i];

				if (! file.File)
					file = {
						File: file
					};

				if (! file.Name)
					file.Name = dc.util.File.toCleanFilename(file.File.name);

				cleanfiles.push(file);

				steps.push({
					Alias: 'UploadFile',
					Title: 'Upload File',
					Params: {
						File: file.File,
						Path: file.Path,
						FileName: file.Name,
						Params: params
					},
					Func: function(step) {
						var task = this;

						// TODO support a callback on fail - do task.kill - handle own alerts
						step.Store.Transfer = new dc.transfer.cdn.UploadVideo({
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

						var path = '';

						if (step.Params.Path)
							path += step.Params.Path;

						step.Store.Transfer.upload(step.Params.File, path + '/' + step.Params.FileName, step.Params.Params);
					}
				});
			}

			var uploadtask = new dc.lang.Task(steps, function(res) {
				if (callback)
					callback(steps, cleanfiles);
			});

			return uploadtask;
		}
	}
}
