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
      },
      arrayBufferToDataUrl: async function(buffer) {
        var blob = new Blob([ buffer ], { type:'application/octet-binary' });

        return await dc.async.util.File.dataUrlReader(blob);
      }
    },
    Image: {
      loadImage: async function(blob) {
        return new Promise((resolve, reject) => {
          try {
            const img = new Image();
						img.crossOrigin = 'anonymous';
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
    call: dc.comm.remote
  }
};
