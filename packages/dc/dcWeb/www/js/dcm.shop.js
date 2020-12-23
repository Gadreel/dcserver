/* ************************************************************************
#
#  designCraft.io
#
#  https://designcraft.io/
#
#  Copyright:
#    Copyright 2015 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */


dc.pui.Apps.Menus.dcmUserStore = {
	Tabs: [
		{
			Alias: 'Orders',
			Title: 'My Orders',
			Path: '/dcm/shop/orders'
		},
		{
			Alias: 'MyAccount',
			Title: 'My Account',
			Path: '/dcw/user/edit-self'
		}
	],
	Options: [
		{
			Title: 'Sign Out',
			Op: function(e) {
				dc.pui.Loader.signout();
			}
		}
	]
};

if (!dc.cms)
	dc.cms = {};

dc.cms.cart = {
	Order: {
		formatStatus: function(status) {
			if ((status == 'Pending') || (status == 'Completed') || (status == 'Canceled'))
				return status;

			return 'Processing';
		}
	},
	Cart: {
		_isLoaded: false,
		_hookDisplay: null,
		_defaultDelivery: 'Ship',
		_cart: {
			Captcha: null,
			Items: [],
			CustomerInfo: null,
			ShippingInfo: null,
			BillingInfo: null,
			Comment: null,
			Delivery: 'Ship',
			CouponCodes: [ ],
			Discounts: [ ],
			DiscountsSignature: null,
			ShipEstimates: [ ],
			ShipEstimatesSignature: null,
			PaymentInfo: null,
			CalcInfo: {
				ItemCalc: 0,				// total of all items in cart
				ProductDiscount: 0,			// discount toward product
				ItemTotal: 0,					// billing subtotal after product discount

				ShipCalc: 0,				// amount to base shipping off of, if based on $
				ShipAmount: 0,				// shipping charges
				ShipDiscount: 0,			// discount toward shipping
				ShipTotal: 0,				// amount to charge for shipping - do not include in taxes

				TaxCalc: 0,					// amount to base taxes off of, no shipping and no donations
				TaxAt: 0,
				TaxTotal: 0,				// amount to charge for taxes

				GrandTotal: 0				// final amount to charge
			},
			Extra: null
		},

		setDefaultDelivery: function(method) {
			this._defaultDelivery = method;
			this._cart.Delivery = method;
		},

		empty: function() {
			this._cart = {
				Items: [],
				CustomerInfo: null,
				ShippingInfo: null,
				BillingInfo: null,
				Comment: null,
				Delivery: this._defaultDelivery,
				CouponCodes: [ ],
				Discounts: [ ],
				DiscountsSignature: null,
				ShipEstimates: [ ],
				ShipEstimatesSignature: null,
				PaymentInfo: null,
				CalcInfo: {
					ItemCalc: 0,				// total of all items in cart
					ProductDiscount: 0,			// discount toward product
					ItemTotal: 0,					// billing subtotal after product discount

					ShipCalc: 0,				// amount to base shipping off of, if based on $
					ShipAmount: 0,				// shipping charges
					ShipDiscount: 0,			// discount toward shipping
					ShipTotal: 0,				// amount to charge for shipping - do not include in taxes

					TaxCalc: 0,					// amount to base taxes off of, no shipping and no donations
					TaxAt: 0,
					TaxTotal: 0,				// amount to charge for taxes

					GrandTotal: 0				// final amount to charge
				},
				Extra: null
			};

			if (this._hookDisplay)
				this._hookDisplay(this._cart)

			return this._cart;
		},

		addItem: function(item) {
			if (! item.EntryId)
				item.EntryId = dc.util.Uuid.create();

			this._cart.Items.push(item);
			this.updateTotals();

			if (this._hookDisplay)
				this._hookDisplay(this._cart)
		},

		removeItem: function(item) {
			for (var i = 0; i < this._cart.Items.length; i++) {
				if (this._cart.Items[i] == item) {
					this._cart.Items.splice(i, 1);
					this.updateTotals();
					break;
				}
			}

			if (this._hookDisplay)
				this._hookDisplay(this._cart)
		},

		updateDisplay: function() {
			if (this._hookDisplay)
				this._hookDisplay(this._cart)
		},

		lookupItemById: function(id) {
			for (var i = 0; i < this._cart.Items.length; i++) {
				if (this._cart.Items[i].EntryId == id)
					return this._cart.Items[i];
			}

			return null;
		},

		lookupItemByProduct: function(id) {
			for (var i = 0; i < this._cart.Items.length; i++) {
				if (this._cart.Items[i].Product == id)
					return this._cart.Items[i];
			}

			return null;
		},

		lookupItemBySku: function(sku) {
			for (var i = 0; i < this._cart.Items.length; i++) {
				if (this._cart.Items[i].Sku == sku)
					return this._cart.Items[i];
			}

			return null;
		},

		// assumes that estimated Shipping and Tax has been filled in by caller (when appropriate)
		updateTotals: function() {
			var carttotal = 0;

			for (var iidx = 0; iidx < this._cart.Items.length; iidx++) {
				var itm = this._cart.Items[iidx];

				var cost = (itm.SalePrice)
					? itm.Quantity * itm.SalePrice
					: itm.Quantity * itm.Price;

				itm.Total = dc.util.Number.formatMoney(cost) - 0;

				carttotal += itm.Total;
			}

			this._cart.CalcInfo.ItemCalc = this._cart.CalcInfo.ItemTotal = dc.util.Number.formatMoney(carttotal) - 0;

			// reset these to signal change
			this._cart.DiscountsSignature = null;
			this._cart.ShipEstimatesSignature = null;
		},

		calculate: function(callback, checkship) {
			var me = this;

			var clone = { };

			$.extend(clone, me._cart);

			// don't send billing info unless truly submitting
			clone.BillingInfo = null;

			if (checkship)
				clone.ShipEstimateRequest = true;

			dc.comm.sendMessage({
				Service: 'dcmStoreServices',
				Feature: 'Orders',
				Op: 'Calculate',
				Body: clone
			}, function(resp) {
				if (resp.Result > 0) {
					dc.pui.Popup.alert('Unable to update order details: ' + resp.Message);
					return;
				}

				var binfo = me._cart.BillingInfo;

				me._cart = resp.Body;

				me._cart.BillingInfo = binfo;

				if (callback)
					callback();
			});
		},

		submit: function(callback) {
			var me = this;

			var subcomm = function() {
				dc.comm.sendMessage({
					Service: 'dcmStoreServices',
					Feature: 'Orders',
					Op: 'Submit',
					Body: me._cart
				}, function(resp) {
					if (resp.Result > 0) {
						if (callback)
							callback(null, resp);
						else
							dc.pui.Popup.alert('Unable to submit order: ' + resp.Message);

						return;
					}

					me._cart = resp.Body.Cart;

					if (callback)
						callback(resp.Body.Id, resp);
				});
			};

			var log = new dc.lang.OperationResult();

			if (dc.handler && dc.handler.payments && (dc.handler.payments.method == 'Authorize')) {
				var authprocess = function() {
					var pi = me._cart.PaymentInfo;

					Accept.dispatchData({
						authData: {
							clientKey: dc.handler.payments.clientKey,
							apiLoginID: dc.handler.payments.apiLoginID
						},
						cardData: {
							cardNumber: pi.CardNumber,
							month: pi.Expiration.substr(0,2),
							year: '20' + pi.Expiration.substr(2,2),
							cardCode: pi.Code
						}
					}, function(response) {
						if (response.messages.resultCode === "Error") {
							for (i = 0; i < response.messages.message.length; i++) {
								log.error(1,
									response.messages.message[i].code + ": " +
									response.messages.message[i].text
								);

								console.log(
									response.messages.message[i].code + ": " +
									response.messages.message[i].text
								);
							}

							//log.error(1, 'Unable to connect to payment gateway');
							log.Result = 1;		// outdated approach, backward compatible - Code would be better

							callback(null, log);
						}
						else {
							var od = response.opaqueData;

							// replace payment data
							me._cart.PaymentInfo = {
								PaymentMethod: 'CreditCard',
								Token1: od.dataDescriptor,
								Token2: od.dataValue
							};

							subcomm();
						}
					});
				};

				if (window.Accept) {
					authprocess();
				}
				else {
					// poorly named event - when auth async load is done
					$('body').one('handshake', function() {
						window.isReady = true;		// we may run before the accept event handler, all it does is this
						authprocess();
					});

					var lib = dc.handler.payments.live
						? 'https://js.authorize.net/v1/Accept.js'
						: 'https://jstest.authorize.net/v1/Accept.js';

					dc.pui.Loader.addExtraLibs([ lib ], function() {
						console.log('loading accept.js');
					});
				}
			}
			else if (dc.handler && dc.handler.payments && (dc.handler.payments.method == 'Stripe')) {
				var stripeprocess = function() {
					var bi = me._cart.BillingInfo;
					var pi = me._cart.PaymentInfo;

					Stripe.setPublishableKey(dc.handler.payments.publishKey);

					Stripe.card.createToken({
						name: bi.FirstName + ' ' + bi.LastName,
						number: pi.CardNumber,
						cvc: pi.Code,
						exp_month: pi.Expiration.substr(0,2),
						exp_year: '20' + pi.Expiration.substr(2,2),
						address_zip: bi.Zip
					}, function(status, response) {
						if (response.error) { // Problem!
							log.error(1, response.error.message);
							log.Result = 1;

							callback(null, log);
						}
						else {
							// replace payment data
							me._cart.PaymentInfo = {
								PaymentMethod: 'CreditCard',
								Token1: response.id
							};

							subcomm();
						}
					});
				};

				if (window.Stripe) {
					stripeprocess();
				}
				else {
					var lib = 'https://js.stripe.com/v2/';

					dc.pui.Loader.addExtraLibs([ lib ], stripeprocess);
				}
			}
			else {
				subcomm();
			}
		},

		load: function(displayHook) {
			// load once per page
			if (this._isLoaded)
				return;

			if (displayHook)
				this._hookDisplay = displayHook;

			this.empty();

			// load from localStorage
			try {
				var plain = localStorage.getItem("ws.cart");

				if (plain) {
					this._cart = JSON.parse(plain);
				}
			}
			catch (x) {
			}

			this._isLoaded = true;

			if (this._hookDisplay)
				this._hookDisplay(this._cart)

			return this._cart;
		},

		// store the cart info temporarily, used from page to page during session
		save: function() {
			try {
				var plain = JSON.stringify( this._cart );
				localStorage.setItem("ws.cart", plain);
			}
			catch (x) {
			}
		},

		// store the cart info temporarily, used from page to page during session
		clear: function() {
			this.empty();

			try {
				localStorage.removeItem("ws.cart");
			}
			catch (x) {
			}
		}

	}
};
