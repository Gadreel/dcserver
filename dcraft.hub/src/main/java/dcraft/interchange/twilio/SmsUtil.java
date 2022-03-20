package dcraft.interchange.twilio;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.stripe.StripeUtil;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
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
import java.math.BigDecimal;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SmsUtil {
	static public void sendText(String alt, String number, String msg, OperationOutcomeRecord callback) {
		SmsUtil.sendText(alt, number, msg, null, callback);
	}

	static public void sendText(String alt, String number, String msg, String callbackurl, OperationOutcomeRecord callback) {
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
		String serviceid = twilio.getAttribute("ServiceSid");

		if (StringUtil.isEmpty(serviceid) && StringUtil.isEmpty(fromPhone)) {
			Logger.error("Missing Twilio sending service or sending phone.");
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
					+ "&Body=" + URLEncoder.encode(msg, "UTF-8");

			if (StringUtil.isNotEmpty(serviceid))
				body += "&MessagingServiceSid=" + URLEncoder.encode(serviceid, "UTF-8");
			else if (StringUtil.isNotEmpty(fromPhone))
				body+= "&From=" + URLEncoder.encode(fromPhone, "UTF-8");

			if (StringUtil.isNotEmpty(callbackurl))
				body += "&StatusCallback=" + URLEncoder.encode(callbackurl, "UTF-8");

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

	static public void sendSms(String alt, String number, String msg, OperationOutcomeRecord callback) {
		SmsUtil.sendSms(alt, number, msg, null, callback);
	}

	static public void sendSms(String alt, String number, String msg, String callbackurl, OperationOutcomeRecord callback) {
		XElement twilio = ApplicationHub.getCatalogSettings("CMS-SMS-Twilio", alt);

		if (twilio == null) {
			Logger.error("Missing Twilio settings.");
			callback.returnEmpty();
			return;
		}

		String fromPhone = twilio.getAttribute("FromPhone");

		if (StringUtil.isEmpty(fromPhone)) {
			Logger.error("Missing Twilio phone.");
			callback.returnEmpty();
			return;
		}

		try {
			String body = "To=" + URLEncoder.encode(number, "UTF-8")
					+ "&From=" + URLEncoder.encode(fromPhone, "UTF-8")
					+ "&Body=" + URLEncoder.encode(msg, "UTF-8");

			if (StringUtil.isNotEmpty(callbackurl))
				body += "&StatusCallback=" + URLEncoder.encode(callbackurl, "UTF-8");

			HttpRequest req = SmsUtil.buildBasicRequest(alt, "/Messages.json", body).build();

			// Send post request
			HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofString())
					.thenAcceptAsync(response -> {
						callback.useContext();		// restore our operation context

						int responseCode = response.statusCode();
						String respBody = response.body();

						if (StringUtil.isNotEmpty(respBody)) {
							RecordStruct resp = Struct.objectToRecord(respBody);

							if (resp == null) {
								Logger.error("Error processing payment: Twilio sent an incomplete response.");

								callback.returnValue(
										new RecordStruct()
												.with("Code", responseCode)
												.with("Message", "Incomplete response ")
								);
							}
							else {
								// TODO remove sout
								System.out.println("Twilio Resp: \n" + resp.toPrettyString());

								if (resp.hasField("error")) {
									Logger.error("Unable to confirm payment token: " + resp.selectAsString("error.message"));

									callback.returnValue(
											new RecordStruct()
													.with("Code", responseCode)
													.with("Message", "Failed to process payment: " + resp.selectAsString("error.message"))
									);
								}
								else {
									resp.with("Code", 200);

									callback.returnValue(resp);
								}
							}
						}
						else {
							Logger.error("Error processing message: Problem with Twilio gateway.");
							Logger.error("Stripe Response: " + responseCode);

							callback.returnValue(
									new RecordStruct()
											.with("Code", responseCode)
											.with("Message", "Failed to send text ")
							);
						}
					});
		}
		catch (Exception x) {
			Logger.error("Error calling service, Twilio error: " + x);

			callback.returnValue(
					new RecordStruct()
							.with("Code", 1)
							.with("Message", "Failed to call service")
			);
		}
	}

	static public HttpRequest.Builder buildBasicRequest(String alt, String path, String post) {
		XElement twilio = ApplicationHub.getCatalogSettings("CMS-SMS-Twilio", alt);

		if (twilio == null) {
			Logger.error("Missing Twilio settings.");
			return null;
		}

		String account = twilio.getAttribute("Account");

		if (StringUtil.isEmpty(account)) {
			Logger.error("Missing Twilio account.");
			return null;
		}

		String auth = twilio.getAttribute("AuthPlain");

		if (StringUtil.isEmpty(auth)) {
			Logger.error("Missing Twilio auth.");
			return null;
		}

		String endpoint = "https://api.twilio.com/2010-04-01/Accounts/" + account + path;

		String authString = account + ":" + auth;
		//System.out.println("auth string: " + authString);
		String authStringEnc = Base64.encodeToString(Utf8Encoder.encode(authString), false);
		//System.out.println("Base64 encoded auth string: " + authStringEnc);

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.header("Authorization", "Basic " + authStringEnc)
				.header("User-Agent", "dcServer/2019.1 (Language=Java/11)");

		if (StringUtil.isEmpty(post))
			return builder.GET();

		return builder
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(post));
	}
}
