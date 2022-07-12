dc.async = {
  util: {
    File: {
      // TODO see regular loadBlob and make async
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
}
