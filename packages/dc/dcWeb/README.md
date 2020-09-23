
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
