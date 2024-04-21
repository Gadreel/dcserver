No reply, just generates a message on the server. Could be useful to check:

1. server is alive
2. current session is a User (this will fail if Guest)

example usage:

```
try {
	await dc.comm.tryRemote('/dc/core/system/test/user/tickle');

	dc.pui.Popup.alert('Tickle succeeded');
}
catch (x) {
	dc.pui.Popup.alert('Tickle failed: ' + x.message);
}
```
