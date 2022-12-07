dc.async = {
  util: {
    File: {
      // TODO also make regular loadBlob and make async
      textReader: async function(blob) {
        return new Promise((resolve, reject) => {
          try {
            const fr = new FileReader();
            fr.onload = (e) => { resolve(e.target.result); };
            fr.onerror = (e) => { reject(e.target.error); };
            fr.readAsText(blob);
          }
          catch(x) {
            reject(x);
          }
        });
      },
      dataUrlReader: async function(blob) {
        return new Promise((resolve, reject) => {
          try {
            const fr = new FileReader();
            fr.onload = (e) => { resolve(e.target.result); };
            fr.onerror = (e) => { reject(e.target.error); };
            fr.readAsDataURL(blob);
          }
          catch(x) {
            reject(x);
          }
        });
      }
    },
    Image: {
      loadImage: async function(blob) {
        return new Promise((resolve, reject) => {
          try {
            const img = new Image();
            img.onload = (e) => { resolve(e.target); };
            img.onerror = (e) => { reject('bad image location or data'); };
            img.src = blob;
          }
          catch(x) {
            reject(x);
          }
        });
      },
      blobToImage: async function(blob) {
        const dataUrl = await dc.async.util.File.dataUrlReader(blob);
        return await dc.async.util.Image.loadImage(dataUrl);
      },
      canvasToBlob: async function(canvas, type, quality) {
        return new Promise(function(resolve) {
          canvas.toBlob(resolve, type || 'image/jpeg', quality ?? .9);
        });
      }
    }
  },
  comm: {
    call: async function(service, params, timeout) {
      return new Promise((resolve, reject) => {
    		var sparts = service.split('.');

    		if (sparts.length != 3) {
  				reject({
  					Code: 1,
  					Message: 'Invalid service naming.'
  				});

    			return;
    		}

    		var msg = {
    			Service: sparts[0],
    			Feature: sparts[1],
    			Op: sparts[2]
    		};

    		if (params)
    			msg.Body = params;

        var onsuccess = function(rmsg) {
    			var ee = dc.comm.Messages.findExitEntry(rmsg.Messages);

    			// setup the "result" of the message based on the exit entry
    			if (!ee) {
    				rmsg.Code = 0;
    			}
    			else {
    				rmsg.Code = ee.Code;
    				rmsg.Message = ee.Message;
    			}

          rmsg.Result = rmsg.Body;
  				delete rmsg.Body;

    			dc.comm._session = rmsg.Session;

    			if (rmsg.SessionChanged) {
    				console.log('session changed');

    				if (dc.pui && dc.pui.Loader)
    					dc.pui.Apps.sessionChanged();
    			}

          try {
  			     resolve(rmsg);
           }
           catch (x) {
             console.log('error processing rpc result: ' + x);
           }
    		};

    		var processRequest = function(e) {
    		    if (xhr.readyState == 4) {
    		    	try {
    			    	if (xhr.status == 200) {
    			    		var rmsg = JSON.parse(xhr.responseText);

    			    		onsuccess(rmsg);
    			    	}
    			    	else {
    			    		reject({
      							Result: 1,
      							Message: 'Server responded with an error code.'
    			    		});
    			    	}
    		    	}
    		    	catch (x) {
    		    		reject({
      						Result: 1,
      						Message: 'Server responded with an invalid message.'
    		    		});
    		    	}
    		    }
    		};

        var xhr = new XMLHttpRequest();
    		xhr.open('PUT', '/dcdyn/rpc?nocache=' + dc.util.Crypto.makeSimpleKey(), true);

    		xhr.timeout = timeout ? timeout : 60000;

    		xhr.setRequestHeader('Content-Type', 'application/json; charset=utf-8');

    		xhr.addEventListener("readystatechange", processRequest, false);

    		xhr.addEventListener("ontimeout", function() {
        		reject({
      				Result: 1,
      				Message: 'Server timed out, no response.'
        		});
    		}, false);

    		xhr.send(JSON.stringify(msg));
      });
    }
  }
};

dc.user.signinAsync = async function(uname, pass, remember) {
  if (! dc.comm.isSecure()) {
    dc.pui.Popup.alert('May not sign in on an insecure connection');
    return;
  }

  const creds = {
    Username: uname,
    Password: pass
  };

  dc.user._info = { };

  // we take what ever Credentials are supplied, so custom Credentials may be used
  var msg = {
    Service: 'dcCoreServices.Authentication.SignIn',
    Body: creds
  };

  const resp = await dc.async.comm.call('dcCoreServices.Authentication.SignIn', creds);

  if (resp.Code == 0) {
    dc.user.setUserInfo(resp.Result);

    // failed login will not wipe out remembered user (could be a server issue or timeout),
    // only set on success - successful logins will save or wipe out depending on Remember
    dc.user.saveRemembered(remember, creds);

    if (dc.user._signinhandler)
      await dc.user._signinhandler.call(dc.user._info);
  }
}

/**
 *  Sign out the current user, kill session on server, clears the cookie from browser
 *  (only server can clear the cookie, please wait for reply)
 */
dc.user.signoutAsync = async function() {
  dc.user._info = { };
  localStorage.removeItem("dc.info.remember");

  try {
    await dc.async.comm.call('dcCoreServices.Authentication.SignOut');
  }
  catch (x) {
    // nothing we can do, ignore it
  }
}
