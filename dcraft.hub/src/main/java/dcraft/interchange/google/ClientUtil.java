package dcraft.interchange.google;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeList;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.MemorySourceStream;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.util.Base64;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class ClientUtil {
	static public void refreshTokenToAccessToken(String alt, OperationOutcomeRecord callback) {
		XElement gsettings = ApplicationHub.getCatalogSettings("Google", alt);

		if (gsettings == null) {
			Logger.error("Missing Google settings.");
			callback.returnEmpty();
			return;
		}

		XElement dsettings = gsettings.find("DriveAPI");

		if (dsettings == null) {
			Logger.error("Missing Google drive settings.");
			callback.returnEmpty();
			return;
		}

		String clientId = dsettings.getAttribute("ClientId");

		if (StringUtil.isEmpty(clientId)) {
			Logger.error("Missing Google drive client id.");
			callback.returnEmpty();
			return;
		}

		String clientSecret = dsettings.getAttribute("ClientSecret");

		if (StringUtil.isEmpty(clientSecret)) {
			Logger.error("Missing Google drive client secret.");
			callback.returnEmpty();
			return;
		}

		String refreshToken = dsettings.getAttribute("RefreshToken");

		if (StringUtil.isEmpty(refreshToken)) {
			Logger.error("Missing Google drive refresh token.");
			callback.returnEmpty();
			return;
		}

		ClientUtil.refreshTokenToAccessToken(clientId, clientSecret, refreshToken, callback);
	}

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

			String endpoint = "https://www.googleapis.com/oauth2/v4/token";

			String post = "client_id=" + URLEncoder.encode(clientid, "UTF-8")
					+ "&client_secret=" + URLEncoder.encode(clientsecret, "UTF-8")
					+ "&refresh_token=" + URLEncoder.encode(refreshtoken, "UTF-8")
					+ "&grant_type=refresh_token";

			HttpRequest.Builder builder = HttpRequest.newBuilder()
					.uri(URI.create(endpoint))
					.header("User-Agent", "dcServer/2019.1 (Language=Java/11)")
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(post));

			// Send post request
			HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
					.thenAcceptAsync(response -> {
						callback.useContext();		// restore our operation context

						int responseCode = response.statusCode();

						CompositeStruct resp = CompositeParser.parseJson(response.body());

						if (resp == null) {
							Logger.error("Error processing request: Google sent an incomplete response: " + responseCode);
							callback.returnEmpty();
							return;
						}

						System.out.println("Google Resp: " + responseCode + "\n" + resp.toPrettyString());

						callback.returnValue((RecordStruct) resp);
					});
		}
		catch (Exception x) {
			Logger.error("Error processing shipping: Unable to connect to shipping gateway. Error: " + x);
			callback.returnEmpty();
		}
	}

	static public void fileListing(String accesstoken, OperationOutcomeList callback) {
		/*
		GET /drive/v3/files HTTP/1.1
		Host: www.googleapis.com
		Content-length: 0
		Authorization: Bearer ....

		may need scope https://www.googleapis.com/auth/drive
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

		may need scope https://www.googleapis.com/auth/drive
				https://www.googleapis.com/auth/script.scriptapp
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
