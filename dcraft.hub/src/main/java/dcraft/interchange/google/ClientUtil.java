package dcraft.interchange.google;

import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeList;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.net.URL;
import java.net.URLEncoder;

public class ClientUtil {
	static public void refreshTokenToAccessToken(String clientid, String clientsecret, String refreshtoken, OperationOutcomeRecord callback) {
		/*
		POST /oauth2/v4/token HTTP/1.1
		Host: www.googleapis.com
		Content-Type: application/x-www-form-urlencoded

		client_id=<your_client_id>&
				client_secret=<your_client_secret>&
				refresh_token=<refresh_token>&
				grant_type=refresh_token
		*/


		try {
			OperationContext.getOrThrow().touch();

			URL url = new URL("https://www.googleapis.com/oauth2/v4/token");

			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "dcServer/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			String body = "client_id=" + URLEncoder.encode(clientid, "UTF-8")
					+ "&client_secret=" + URLEncoder.encode(clientsecret, "UTF-8")
					+ "&refresh_token=" + URLEncoder.encode(refreshtoken, "UTF-8")
					+ "&grant_type=refresh_token";

			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(body);
			wr.flush();
			wr.close();

			int responseCode = con.getResponseCode();

			if (responseCode == 200) {
				// parse and close response stream
				CompositeStruct resp = CompositeParser.parseJson(con.getInputStream());

				if (resp == null) {
					Logger.error("Error processing request: Google sent an incomplete response.");
					callback.returnEmpty();
					return;
				}

				System.out.println("Google Resp:\n" + resp.toPrettyString());

				callback.returnValue((RecordStruct) resp);

				return;
			}
			else {
				Logger.error("Error processing request: Problem with Google gateway.");
			}
		}
		catch (Exception x) {
			Logger.error("Error processing shipping: Unable to connect to shipping gateway.");
		}

		callback.returnEmpty();
	}

	static public void fileListing(String accesstoken, OperationOutcomeList callback) {
		/*
		GET /drive/v3/files HTTP/1.1
		Host: www.googleapis.com
		Content-length: 0
		Authorization: Bearer ....
		*/


		try {
			OperationContext.getOrThrow().touch();

			URL url = new URL("https://www.googleapis.com/drive/v3/files");

			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", "dcServer/1.0 (Language=Java/8)");
			con.setRequestProperty("Authorization", "Bearer " + accesstoken);

			int responseCode = con.getResponseCode();

			if (responseCode == 200) {
				// parse and close response stream
				CompositeStruct resp = CompositeParser.parseJson(con.getInputStream());

				if (resp == null) {
					Logger.error("Error processing request: Google sent an incomplete response.");
					callback.returnEmpty();
					return;
				}

				System.out.println("Google Resp:\n" + resp.toPrettyString());

				callback.returnValue(((RecordStruct) resp).getFieldAsList("files"));

				return;
			}
			else {
				Logger.error("Error processing request: Problem with Google gateway.");
			}
		}
		catch (Exception x) {
			Logger.error("Error processing request: Unable to connect to Google gateway.");
		}

		callback.returnEmpty();
	}

	static public void runScript(String accesstoken, String scriptid, String func, ListStruct params, OperationOutcomeRecord callback) {
		/*
		GET /drive/v3/files HTTP/1.1
		Host: www.googleapis.com
		Content-length: 0
		Authorization: Bearer ....
		*/


		try {
			OperationContext.getOrThrow().touch();

			URL url = new URL("https://script.googleapis.com/v1/scripts/" + scriptid + ":run");

			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "dcServer/1.0 (Language=Java/8)");
			con.setRequestProperty("Authorization", "Bearer " + accesstoken);
			con.setRequestProperty("Content-Type", "application/json");

			RecordStruct body = RecordStruct.record()
					.with("function", func)
					.with("parameters", params);

			String bodystr = body.toPrettyString();

			System.out.println(bodystr);

			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(bodystr);
			wr.flush();
			wr.close();

			//int responseCode = con.getResponseCode();

			//if (responseCode == 200) {
				// parse and close response stream
				CompositeStruct resp = CompositeParser.parseJson(con.getInputStream());

				if (resp == null) {
					Logger.error("Error processing request: Google sent an incomplete response.");
					callback.returnEmpty();
					return;
				}

				System.out.println("Google Resp:\n" + resp.toPrettyString());

				callback.returnValue(((RecordStruct) resp).getFieldAsRecord("response"));

				return;
				/*
			}
			else {
				Logger.error("Error processing request: Problem with Google gateway.");
			}
			*/
		}
		catch (Exception x) {
			Logger.error("Error processing request: Unable to connect to Google gateway.");
		}

		callback.returnEmpty();
	}

}
