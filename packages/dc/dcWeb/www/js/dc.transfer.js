if (!dc.transfer)
	dc.transfer = {};

dc.transfer = {
	// from http://stackoverflow.com/questions/10420352/converting-file-size-in-bytes-to-human-readable#answer-22023833
	fmtFileSize: function(bytes) {
		var exp = Math.log(bytes) / Math.log(1024) | 0;
		var result = (bytes / Math.pow(1024, exp)).toFixed(2);

		return result + ' ' + (exp == 0 ? 'bytes': 'KMGTPEZY'[exp - 1] + 'B');
	},

	Vault: function(options) {
		var defaults = {
			Service: 'dcCoreServices',
			Feature: 'Vaults',
			Vault: 'Default',
			Callback: null,
			Progress: null
		};

		this.settings = $.extend( {}, defaults, options );

		this.data = {
			binding: null,
			file: null,
			path: null,
			token: null,
			params: null,
			lastxhr: null,
			canclick: false,
			chunk: 0,
			//maxchunksize: 16 * 1024 *1024,		// 16 MB
			maxchunksize: 4 * 1024 * 1024 *1024,		// 4 GB
			ftotal: 0,
			famt: 0,
			aamt: 0,
			finalsent: false
		};

		// ask the backend Uploader for a token to upload with
		this.uploadToken = function(params, cb) {
			console.log(new Date() + ': Upload Token');

			var tmsg = {
				Service: this.settings.Service,
				Feature: this.settings.Feature,
				Op: 'AllocateUploadToken',
				Body: {
					Vault: this.settings.Vault,
					Params: params
				}
			};

			dc.comm.sendMessage(tmsg, function(rmsg) {
				if (cb)
					cb(rmsg);
			});
		};

		// ask the backend Uploader for a channel to connect to
		this.upload = function(blob, path, token, ovrwrt, params, tx) {
			console.log(new Date() + ': Requesting');

			this.data.file = blob;
			this.data.path = path;
			this.data.token = token;
			this.data.tx = tx;
			this.data.params = params;

			var tmsg = {
				Service: this.settings.Service,
				Feature: this.settings.Feature,
				Op: 'StartUpload',
				Body: {
					Vault: this.settings.Vault,
					Path: this.data.path,
					Token: this.data.token,
					TransactionId: this.data.tx,
					Size: this.data.file.size,
					Overwrite: ovrwrt ? true : false,
					Params: this.data.params
				}
			};

			var buck = this;

			dc.comm.sendMessage(tmsg, function(rmsg) {
				//console.log('start: ' +  rmsg.Result);

				if (rmsg.Result == 0) {
					console.log(new Date() + ': Streaming');

					// now Gateway and Uploader can talk to each other, so start sending the file
					buck.data.binding = rmsg.Body;
					buck.data.ftotal = buck.data.file.size;
					buck.data.famt = buck.data.binding.Size ? buck.data.binding.Size : 0;

					buck.sendNextChunk();
				}
				else {
					dc.pui.Popup.alert('Error requesting upload channel.');
				}
			});
		};

		// each chunk (16MB) starts in a fresh call stack - here
		this.sendNextChunk = function() {
			var buck = this;

			// we are done uploading so do a verify
			if ((this.data.famt == this.data.ftotal) && this.data.finalsent) {
				// don't finish the progress bar until verify is done
				if (buck.settings.Progress)
					buck.settings.Progress(98, 'Verifying');

				this.verifyFile();
				return;
			}

			console.log(new Date() + ': Sending chunk: ' + this.data.chunk);

			// chunk at 16MB or less each with offset...
			var chunksize = this.data.ftotal - this.data.famt;
			var lastchunk = false;

			if (chunksize > this.data.maxchunksize)
				chunksize = this.data.maxchunksize;
			else
				this.data.lastchunk = true;

			var content = null;

			// we aren't going for support with very old browsers, so start with slice
			// and only fall back to the webkit or moz versions if not supported
			// browsers with the very old type of slice will not work, but likely don't
			// support upload progress anyway
			if (this.data.file.slice)
				content = this.data.file.slice(this.data.famt, this.data.famt + chunksize);
			else if (this.data.file.webkitSlice)
				content = this.data.file.webkitSlice(this.data.famt, this.data.famt + chunksize);
			else if (this.data.file.mozSlice)
				content = this.data.file.mozSlice(this.data.famt, this.data.famt + chunksize);

			//var content = file.slice(famt, famt + chunksize);

			var xhr = lastxhr = new XMLHttpRequest();

			var uri = '/dcdyn/xfer/' + this.data.binding.Channel + (this.data.lastchunk ? '/final' : '/block');

			xhr.open("POST", uri, true);

			xhr.onreadystatechange = function() {
				console.log('state change: ' + xhr.readyState + ' status: ' + xhr.status);

				// do nothing if user clicked cancel
				if (buck.data.canclick)
					return;

				if (xhr.readyState == 4) {
					buck.data.lastxhr = null;

					// if already uploaded something, and get 400 or 0 then you have been cancelled
					if (((xhr.status == 400) || (xhr.status == 0)) && (buck.data.aamt > 0)) {
						dc.pui.Popup.alert('Processed Cancel Request.');
						return;
					}

					if (xhr.status != 200) {
						dc.pui.Popup.alert('Streaming halted with error.');
						return;
					}

					buck.data.famt += chunksize;
					buck.data.chunk++;

					if (buck.data.lastchunk)
						buck.data.finalsent = true;

					// reset call stack by calling next chunk later
					setTimeout(function() { buck.sendNextChunk() }, 1);
				}
			};

			xhr.upload.onprogress = function(e) {
				//console.log('progress: ' +  e.loaded);

				if (e.lengthComputable) {
					buck.data.aamt = buck.data.famt + e.loaded;

					var p1 = (buck.data.aamt * 100 / buck.data.ftotal).toFixed();

					if (p1 > 96)
						p1 = 96;

					if (buck.settings.Progress)
						buck.settings.Progress(p1, dc.transfer.fmtFileSize(buck.data.aamt) + ' of ' + dc.transfer.fmtFileSize(buck.data.ftotal));
				}
			};

			xhr.send(content);
		};

		this.verifyFile = function() {
			console.log(new Date() + ': Verifying');

			var tmsg = {
				Service: this.settings.Service,
				Feature: this.settings.Feature,
				Op: 'FinishUpload',
				Body: {
					Vault: this.settings.Vault,
					Path: this.data.path,
					Token: this.data.token,
					TransactionId: this.data.tx,
					Channel: this.data.binding.Channel,
					TransactionId: this.data.binding.TransactionId,
					Status: 'Success',
					Evidence: {
						Size: this.data.file.size
					},
					Params: this.data.params
				}
			};

			var buck = this;

			dc.comm.sendMessage(tmsg, function(rmsg) {
				console.log('verify: ' +  rmsg.Result);

				if (buck.settings.Callback)
					buck.settings.Callback(buck.data.remotePath);
			});
		};

		this.download = function(path, token, params) {
			console.log(new Date() + ': Requesting');

			this.data.path = path;
			this.data.token = token;
			this.data.params = params;

			dc.util.Cookies.deleteCookie('fileDownload');

			var tmsg = {
				Service: this.settings.Service,
				Feature: this.settings.Feature,
				Op: 'StartDownload',
				Body: {
					Vault: this.settings.Vault,
					Path: this.data.path,
					Token: this.data.token,
					Params: this.data.params
				}
			};

			var buck = this;

			dc.comm.sendMessage(tmsg, function(rmsg) {
				if (rmsg.Result == 0) {
					var binding = rmsg.Body;

					$.fileDownload('/dcdyn/xfer/' + binding.Channel, {
						httpMethod: 'GET',
						successCallback: function(url) {
							// only means that it started, not finished
							console.log('download worked!');
							if (buck.settings.Callback)
								buck.settings.Callback(buck.data.path);
						},
						failCallback: function(html, url) {
							console.log('download failed!');
							if (buck.settings.Callback)
								buck.settings.Callback(buck.data.path);
						}
					});
				}
				else {
					dc.pui.Popup.alert('Error requesting download channel.');
				}
			});
		};

		this.prepDownload = function(path, token, params) {
			console.log(new Date() + ': Requesting');

			this.data.path = path;
			this.data.token = token;
			this.data.params = params;

			dc.util.Cookies.deleteCookie('fileDownload');

			var tmsg = {
				Service: this.settings.Service,
				Feature: this.settings.Feature,
				Op: 'StartDownload',
				Body: {
					Vault: this.settings.Vault,
					Path: this.data.path,
					Token: this.data.token,
					Params: this.data.params
				}
			};

			var buck = this;

			dc.comm.sendMessage(tmsg, function(rmsg) {
				if (rmsg.Result == 0) {
					var binding = rmsg.Body;

					buck.settings.Callback('/dcdyn/xfer/' + binding.Channel, binding);
				}
				else {
					dc.pui.Popup.alert('Error requesting download channel.');
				}
			});
		};

		this.downloadBuffer = function(path, token, params) {
			console.log(new Date() + ': Requesting');

			this.data.path = path;
			this.data.token = token;
			this.data.params = params;

			var tmsg = {
				Service: this.settings.Service,
				Feature: this.settings.Feature,
				Op: 'StartDownload',
				Body: {
					Vault: this.settings.Vault,
					Path: this.data.path,
					Token: this.data.token,
					Params: this.data.params
				}
			};

			var buck = this;

			dc.comm.sendMessage(tmsg, function(rmsg) {
				if (rmsg.Result == 0) {
					var binding = rmsg.Body;

					var xhr = new XMLHttpRequest();

					var uri = '/dcdyn/xfer/' + binding.Channel;

					xhr.responseType = 'arraybuffer';
					xhr.open("GET", uri, true);

					xhr.onreadystatechange = function() {
						console.log('state change: ' + xhr.readyState + ' status: ' + xhr.status);

						if (xhr.readyState == 4) {
							// if already uploaded something, and get 400 or 0 then you have been cancelled
							if (((xhr.status == 400) || (xhr.status == 0)) && (buck.data.aamt > 0)) {
								dc.pui.Popup.alert('Processed Cancel Request.');
								return;
							}

							if (xhr.status != 200) {
								dc.pui.Popup.alert('Streaming halted with error.');
								return;
							}

							if (buck.settings.Callback)
								buck.settings.Callback(this.response);
						}
					};

					xhr.upload.onprogress = function(e) {
						//console.log('progress: ' +  e.loaded);

						if (e.lengthComputable) {
							buck.data.aamt = buck.data.famt + e.loaded;

							var p1 = (buck.data.aamt * 100 / buck.data.ftotal).toFixed();

							if (p1 > 96)
								p1 = 96;

							if (buck.settings.Progress)
								buck.settings.Progress(p1, dc.transfer.fmtFileSize(buck.data.aamt) + ' of ' + dc.transfer.fmtFileSize(buck.data.ftotal));
						}
					};

					xhr.send();
				}
				else {
					dc.pui.Popup.alert('Error requesting download channel.');
				}
			});
		};
	},

	Util: {
		uploadFiles: function(files, vault, token, callback, ovrwrt, params) {
			var steps = [ ];
			var cleanfiles = [ ];
			var usetx = (! token && (files.length > 1));  // tokens may have their own tx

			if (usetx) {
				// commit the file transaction
				steps.push({
					Alias: 'BeginTx',
					Title: 'Start Tx',
					Func: function(step) {
						var task = this;

						dc.transfer.Util.beginTx(vault, null, null, function(rmsg, tx) {
							task.Store.Tx = tx;
							task.resume();
						});
					}
				});
			}

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
						Overwrite: ovrwrt,
						Params: params
					},
					Func: function(step) {
						var task = this;

						// TODO support a callback on fail - do task.kill - handle own alerts
						step.Store.Transfer = new dc.transfer.Vault({
							Vault: vault,
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

						if (task.Store.Token)
							path = '/' + task.Store.Token;

						if (step.Params.Path)
							path += step.Params.Path;

						// start/resume upload (basic token service requires that token be in the path)
						step.Store.Transfer.upload(step.Params.File,
								path + '/' + step.Params.FileName,
								task.Store.Token, step.Params.Overwrite, step.Params.Params, task.Store.Tx);
					}
				});
			}

			if (usetx) {
				// commit the file transaction
				steps.push({
					Alias: 'CommitTx',
					Title: 'Commit Tx',
					Func: function(step) {
						var task = this;

						dc.transfer.Util.commitTx(vault, null, task.Store.Tx, null, function() {
							task.resume();
						});
					}
				});
			}

			var uploadtask = new dc.lang.Task(steps, function(res) {
				if (callback)
					callback(steps, cleanfiles);
			});

			uploadtask.Store = {
				Token: token
			};

			return uploadtask;
		},
		directSaveData: function(fileName, data) {
			var	blob = new Blob([data], {type: "octet/stream"});
			var url = window.URL.createObjectURL(blob);

			var a = document.createElement("a");
			a.style = "display: none";
			a.href = url;
			a.download = fileName;

			document.body.appendChild(a);

			a.click();

			window.URL.revokeObjectURL(url);
		},

		sendDownloadMessage : function(msg, fileName, options) {
			dc.comm.sendMessage(msg, function(res) {
				console.log('Result: '); // + JSON.stringify(res));
				console.dir(res);

				if (dc.util.Struct.isList(res.Body))
					dc.transfer.CVS.writeRecordsToCSV(fileName, res.Body, options);
				else
					dc.transfer.CVS.writeRecordsToCSV(fileName, [ res.Body ], options);
			});
		},

		beginTx : function(vault, token, params, cb) {
			dc.comm.sendMessage({
				Service: 'dcCoreServices',
				Feature: 'Vaults',
				Op: 'BeginTransaction',
				Body: {
					Vault: vault,
					Token: token,
					Params: params
				}
			}, function(rmsg) {
				if (cb)
					cb(rmsg, rmsg.Body ? rmsg.Body.TransactionId : null);
			});
		},

		commitTx : function(vault, token, tx, params, cb) {
			dc.comm.sendMessage({
				Service: 'dcCoreServices',
				Feature: 'Vaults',
				Op: 'CommitTransaction',
				Body: {
					Vault: vault,
					Token: token,
					Params: params,
					TransactionId: tx
				}
			}, function(rmsg) {
				if (cb)
					cb(rmsg);
			});
		}
	},

	CVS: {
		writeRecordsToCSV: function(fileName, recs, options) {
			if (options.Sort)
				recs.sort(dc.util.List.sortObjects(options.Sort));

			var rows = [ ];

			var mhdr = options.Headers ? options.Headers : options.Order;

			var morder = options.Order;

		 	// build the rows

			rows.push(mhdr);

			for (var i = 0; i < recs.length; i++) {
				var row = recs[i];
				var cols = [ ];

				for (var h = 0; h < morder.length; h++) {
					var v = row[morder[h]];

					if (dc.util.Struct.isEmpty(v))
						v = '';

					if (dc.util.Struct.isList(v))
						v = v.join(', ');

					cols.push(v);
				}

				rows.push(cols);
			}

			dc.transfer.CVS.writeCSV(fileName, rows, options);
		},
		writeCSV: function(fileName, rows, options) {
			options = options || {};

			var esc = dc.transfer.CVS.createFormatter(options.Format);
			var data = '';

			for (var i = 0; i < rows.length; i++) {
				if ((i == 0) && options.ClearHeader)
					data += rows[i].join(',') + '\n';
				else
					data += esc(rows[i]) + '\n';
			}

			dc.transfer.Util.directSaveData(fileName, data);
		},
		createFormatter: function(options) {
			options = options || {};

			var delimiter = options.delimiter || ",",
			QUOTE = options.quote || '"',
			ESCAPE = options.escape || '"',
			REPLACE_REGEXP = new RegExp(QUOTE, "g");

			function escapeField(field, index) {
				field = field.replace(/\0/g, '');

				if (field.indexOf(QUOTE) !== -1)
				field = field.replace(REPLACE_REGEXP, ESCAPE + QUOTE);

				return QUOTE + field + QUOTE;
			}

			return function escapeFields(fields) {
				var ret = [];

				for (var i = 0; i < fields.length; i++) {
					var field = fields[i];
					field = (dc.util.Struct.isEmpty(field) ? "" : field) + "";
					ret.push(escapeField(field, i));
				}

				return ret.join(delimiter);
			};
		}
	}
}
