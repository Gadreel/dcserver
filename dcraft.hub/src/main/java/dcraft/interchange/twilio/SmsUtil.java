package dcraft.interchange.twilio;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.Base64;
import dcraft.util.Base64Alt;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

public class SmsUtil {
	static public void sendText(String alt, String number, String msg, OperationOutcomeRecord callback) {
		XElement twilio = ApplicationHub.getCatalogSettings("CMS-SMS-Twilio", alt);
		
		if (twilio == null) {
			Logger.error("Missing Twilio settings.");
			callback.returnEmpty();
			return;
		}
		
		String account = twilio.getAttribute("Account");
		
		if (StringUtil.isEmpty(account)) {
			Logger.error("Missing Twilio account.");
			callback.returnEmpty();
			return;
		}
		
		String fromPhone = twilio.getAttribute("FromPhone");
		
		if (StringUtil.isEmpty(fromPhone)) {
			Logger.error("Missing Twilio phone.");
			callback.returnEmpty();
			return;
		}
		
		String auth = twilio.getAttribute("AuthPlain");
		
		if (StringUtil.isEmpty(auth)) {
			Logger.error("Missing Twilio auth.");
			callback.returnEmpty();
			return;
		}
		
		try {
			String endpoint = "https://api.twilio.com/2010-04-01/Accounts/" + account + "/Messages.json";
			
			
			String authString = account + ":" + auth;
			//System.out.println("auth string: " + authString);
			String authStringEnc = Base64.encodeToString(Utf8Encoder.encode(authString), false);
			//System.out.println("Base64 encoded auth string: " + authStringEnc);
			
			URL url = new URL(endpoint);
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			con.setRequestProperty("Authorization", "Basic " + authStringEnc);
			
			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "dcServer/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			
			String body = "To=" + URLEncoder.encode(number, "UTF-8")
					+ "&From=" + URLEncoder.encode(fromPhone, "UTF-8")
					+ "&Body=" + URLEncoder.encode(msg, "UTF-8");

			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(body);
			wr.flush();
			wr.close();
			
			/* example
{
   "account_sid": "AC60b410d73ce21eecdc24119e2b4aca39",
   "api_version": "2010-04-01",
   "body": "Let's grab lunch at Milliways tomorrow!",
   "num_segments": "1",
   "num_media": "1",
   "date_created": "Wed, 18 Aug 2010 20:01:40 +0000",
   "date_sent": null,
   "date_updated": "Wed, 18 Aug 2010 20:01:40 +0000",
   "direction": "outbound-api",
   "error_code": null,
   "error_message": null,
   "from": "+14158141829",
   "price": null,
   "sid": "MM90c6fc909d8504d45ecdb3a3d5b3556e",
   "status": "queued",
   "to": "+15558675310",
   "uri": "/2010-04-01/Accounts/AC60b410d73ce21eecdc24119e2b4aca39/Messages/MM90c6fc909d8504d45ecdb3a3d5b3556e.json"
}			*/
			
			int responseCode = con.getResponseCode();
			
			if (responseCode == 201) {
				// parse and close response stream
				CompositeStruct resp = CompositeParser.parseJson(con.getInputStream());
				
				if (resp == null) {
					Logger.error("Error processing text: Twilio sent an incomplete response.");
					callback.returnEmpty();
					return;
				}
				
				System.out.println("Twilio Resp:\n" + resp.toPrettyString());
				
				callback.returnValue((RecordStruct) resp);
				
				return;
			}
			else {
				Logger.error("Error processing text: Problem with Twilio gateway.");
			}
			
		}
		catch (Exception x) {
			Logger.error("Error calling text, Twilio error: " + x);
		}
		
		callback.returnEmpty();
	}
}
