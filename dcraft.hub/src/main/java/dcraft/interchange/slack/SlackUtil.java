package dcraft.interchange.slack;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.net.URL;

public class SlackUtil {
	// TODO replace with a real event system and notices
	static public void serverEvent(String alt, String msg, OperationOutcomeEmpty callback) throws OperatingContextException {
		RecordStruct data = RecordStruct.record()
				.with("text", msg);
		
		XElement domainwebconfig = ResourceHub.getResources().getConfig().getTag("Web");
		
		if ((domainwebconfig != null) && domainwebconfig.hasNotEmptyAttribute("IndexUrl")) {
			String indexurl = domainwebconfig.getAttribute("IndexUrl");
			
			data.with("icon_url", indexurl + "imgs/logo152.png");
		}
		
		SlackUtil.serverEvent(alt, data, callback);
	}
	
	static public void serverEvent(String alt, RecordStruct data, OperationOutcomeEmpty callback) throws OperatingContextException {
		XElement eventsettings = ApplicationHub.getCatalogSettings("Server-Event", alt);

		// no action or error if not configured
		if ((eventsettings == null) || ! "Slack".equals(eventsettings.attr("Catalog"))) {
			if (callback != null)
				callback.returnEmpty();
			
			return;
		}
		
		try (OperationMarker om = OperationMarker.clearErrors()) {
			SlackUtil.messageToWebHook(alt, data, callback);
		}
	}
	
	static public void messageToWebHook(String alt, RecordStruct msg, OperationOutcomeEmpty callback) {
		XElement auth = ApplicationHub.getCatalogSettings("Server-Notice-Slack", alt);

		if (auth == null) {
			Logger.error("Missing Slack settings.");
			
			if (callback != null)
				callback.returnEmpty();
			
			return;
		}

		String etoken = auth.getAttribute("TokenPlain");

		if (StringUtil.isEmpty(etoken)) {
			Logger.error("Missing Slack token.");
			
			if (callback != null)
				callback.returnEmpty();
			
			return;
		}

		String token = etoken;		// TODO add encrypted option
		
		/*
		String token = ApplicationHub.getClock().getObfuscator().decryptHexToString(etoken);

		if (StringUtil.isEmpty(token)) {
			Logger.error("Invalid Slack token setting.");

			if (callback != null)
				callback.returnEmpty();
				
			return;
		}
		*/

		/*
       {
          "text": "message",
          "icon_url": "path to icon",
          "channel": "override channel name"
       }
		 */

		try {
			OperationContext.getOrThrow().touch();

			URL url = new URL("https://hooks.slack.com/services/" + token);

			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "DivConq/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/json");

			String body = msg.toString();

			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(body);
			wr.flush();
			wr.close();

			int responseCode = con.getResponseCode();

			if (responseCode != 200) {
				Logger.error("Error processing payment: Unable to connect to Slack gateway.");
			}
		}
		catch (Exception x) {
			Logger.error("Error processing payment: Unable to connect to Slack gateway.");
		}
		
		if (callback != null)
			callback.returnEmpty();
	}
}
