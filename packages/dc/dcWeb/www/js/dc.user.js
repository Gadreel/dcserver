/* ************************************************************************
#
#  designCraft.io
#
#  https://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */

dc.user = {
	/**
	 * Tracks user info for the current logged in user.
	 * See "Info" property for collected user data.
	 * Here is the structure for that data.
	 *
	 *	{
	 *		UserId: string,
	 *		Username: string,
	 *		FirstName: string,
	 *		LastName: string,
	 *		Email: string,
	 *		Tenant: string,
	 *		Site: string,
	 *		Locale: array of string,
	 *		Chronology: array of string,
	 *		Verified: boolean,			// logged in
	 *		Badges: array of string
	 *	}
	 *
	 * @type object
	 */
	_info: { },
	_signinhandler: null,

	// check to see see if the user info was remembered
	// this is not so secure as we use a hard coded key for that, but at least it is
	// encrypted on disk.  'Remember' should only be used on devices with personal
	// accounts - never shared accounts or public devices.
	loadRemembered : function() {
		var plain = localStorage.getItem("dc.info.remember");

		if (plain)
			return JSON.parse(plain);

		return false;
	},

	/**
	 *  If remember is true then store the current user info.  If not, make sure it is not present on disk.
	 */
	saveRemembered : function(remember, creds) {
		if (! remember) {
			localStorage.removeItem("dc.info.remember");
		}
		else {
			var plain = JSON.stringify(creds);
			localStorage.setItem("dc.info.remember", plain);
		}
	},

	/**
	 *  User is signed in
	 */
	isVerified : function() {
		return (dc.user._info.Verified === true);
	},

	isAuthorized: function(tags) {
		if (!tags)
			return true;

		if (!dc.user._info.Badges)
			return false;

		var ret = false;

		$.each(tags, function(i1, itag) {
			$.each(dc.user._info.Badges, function(i2, htag) {
				if (itag === htag)
					ret = true;
			});
		});

		return ret;
	},

	getUserInfo : function() {
		return dc.user._info;
	},

	setUserInfo : function(info) {
		// copy only select fields for security reasons
		dc.user._info = {
			Verified: ("00000_000000000000002" != info.UserId),	// guest is not treated as verified in client
			UserId: info.UserId,
			Username: info.Username,
			FirstName: info.FirstName,
			LastName: info.LastName,
			Email: info.Email,
			Badges: info.Badges,
			Locale: info.Locale,
			Chronology: info.Chronology
		};

		return dc.user._info;
	},

	setSignInHandler : function(v) {
		dc.user._signinhandler = v;
	},

	signin : function(uname, pass, remember, callback) {
		dc.user.signin2(
			{
				Username: uname,
				Password: pass
			},
			remember,
			callback
		);
	},

	/**
	 * Given the current user info, try to sign in.  Trigger the callback whether sign in works or fails.
	 */
	signin2 : function(creds, remember, callback) {
		if (! dc.comm.isSecure()) {
			dc.pui.Popup.alert('May not sign in on an insecure connection');

			if (callback)
				callback(null);

			return;
		}

		dc.user._info = { };

		// we take what ever Credentials are supplied, so custom Credentials may be used
		var msg = {
			Service: 'dcCoreServices',
			Feature: 'Authentication',
			Op: 'SignIn',
			Body: creds
		};

		dc.comm.sendMessage(msg, function(rmsg) {
			if (rmsg.Result == 0) {
				dc.user.setUserInfo(rmsg.Body);

				// failed login will not wipe out remembered user (could be a server issue or timeout),
				// only set on success - successful logins will save or wipe out depending on Remember
				dc.user.saveRemembered(remember, creds);

				if (dc.user._signinhandler)
					dc.user._signinhandler.call(dc.user._info);
			}

			if (callback)
				callback();
		});
	},

	facebookSignin : function(token, callback) {
		if (! dc.comm.isSecure()) {
			dc.pui.Popup.alert('May not sign in on an insecure connection');

			if (callback)
				callback(null);

			return;
		}

		dc.user._info = { };

		// we take what ever Credentials are supplied, so custom Credentials may be used
		var msg = {
			Service: 'dcCoreServices',
			Feature: 'Authentication',
			Op: 'FacebookSignIn',
			Body: {
				Token: token
			}
		};

		dc.comm.sendMessage(msg, function(rmsg) {
			if (rmsg.Result == 0) {
				dc.user.setUserInfo(rmsg.Body);

				// failed login will not wipe out remembered user (could be a server issue or timeout),
				// only set on success - successful logins will save or wipe out depending on Remember
				dc.user.saveRemembered(false);

				if (dc.user._signinhandler)
					dc.user._signinhandler.call(dc.user._info);
			}

			if (callback)
				callback();
		});
	},

	currentLocale: function() {
		return dc.util.Cookies.getCookie('dcLang');
	},

	/* TODO - for facebook review dcServer 2016 */

	updateUser : function(callback) {
		dc.user._info = { };

		dc.comm.sendMessage({
			Service: 'dcSessions',
			Feature: 'Session',
			Op: 'LoadMe'
		}, function(rmsg) {
			if (rmsg.Result == 0)
				var uinfo = dc.user.setUserInfo(rmsg.Body);

			if (callback)
				callback();
		});
	},

	/**
	 *  Sign out the current user, kill session on server
	 */
	signout : function(callback) {
		dc.user._info = { };
		localStorage.removeItem("dc.info.remember");

		dc.comm.sendMessage({
			Service: 'dcCoreServices',
			Feature: 'Authentication',
			Op: 'SignOut'
		}, function() {
			if (callback)
				callback();
		},
		1000);
	}

}
