
### Product Sync ideas

var prodaliases = [
	'face-mask',  
	'60-hikes-within-60-miles',  
	'madison-folk-art-puzzle',  
	'national-park-puzzle',  
	'jellyfish-puzzle',  
	'bees-and-honey-puzzle',  
	'cacti-and-succulents-puzzle',  
	'constellations-puzzle',  
	'celestial-chart-puzzle',  
	'national-parks-map-puzzle',  
	'botanic-garden-puzzle',  
	'butterflies-puzzle',  
	'house-plants-puzzle',  
	'frasier-fir-poured-candle',  
	'mineralogy-puzzle',  
	'wisconsin-cribbage-board',  
	'cotton-face-mask',  
	'shower-steamer-signature-set',  
	'wisconsin-dish-towel',  
	'zodiac-puzzle',  
	'whimsical-village-puzzle',  
	'coral-reef-puzzle',  
	'moon-dance-puzzle',  
	'mushrooms-and-butterflies-puzzle',  
	'positivity-puzzle',  
	'triangle-pattern-puzzle',  
	'new-york-city-life-puzzle',  
	'bee-s-wrap-food',  
	'capitol-ornament',  
	'frank-lloyd-wright-coaster-set',  
	'frank-lloyd-wright',  
	'frank-lloyd-wright-hoffmantrivet',  
	'frank-lloyd-wright-march-balloons-coaster-set',  
	'frank-lloyd-wright-trivet',  
	'frank-lloyd-wright-waterlilies-trivet',  
	'hand-painted-wood-wisconsin-ornaments',  
	'hand-creme-trio-gift-set',  
	'i-love-trader-joe-s-cookbook',  
	'thymes-body-lotion'
];



var prods = [ ];

prodaliases.forEach(alias => {
	dc.comm.sendMessage({
		Service: 'dcCoreServices',
		Feature: 'Vaults',
		Op: 'ListFiles',
		Body: {
			Vault: 'Galleries',
			Path: '/store/product/' + alias
		}
	}, function(resp) {
		// display
		for (var i = 0; i < resp.Body.length; i++) {
			var item = resp.Body[i];

			if (item.IsFolder)
				continue;

			console.log('found: ' + item.FileName + ' for ' + alias);

			prods.push({
				Alias: alias,
				Image: item.FileName
			});

			break;
		}
	});
});


prods.forEach(prod => {
	dc.comm.sendMessage({
		Service: 'dcmStoreServices',
		Feature: 'Product',
		Op: 'Add',
		Body: {
			Title: prod.Alias,
			Alias: prod.Alias,
			Sku: prod.Alias,
			Image: prod.Image,
			ShowInStore: true,
			Categories: [ ]
		}
	})
});


## Async dc.app.js migration

### 'on' handling

- reserved for events
- no result is ever used (no await, no value returned)
- if always scripted by server as sync code, for things like destroy which are order dependent, but it is okay to launch an async section from within


### Review in dc.app.js

- Recaptcha control needs to support async callPageFunc


// TODO should we provide alternative handling  for async

//place to add functions to tags
dc.pui.TagFuncs = {


// TODO reconsider this concept

// this returns a promise - either undefined or from a function
callTagFuncAsync: function(selector, method) {


// TODO reconsider this concept

// this returns a promise - either undefined or from a function
callInputFunc: function(field, method) {
