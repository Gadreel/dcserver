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

		empty: function() {
			this._cart = {
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
