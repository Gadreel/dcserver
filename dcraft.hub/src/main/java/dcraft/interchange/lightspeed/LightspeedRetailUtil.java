package dcraft.interchange.lightspeed;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.struct.*;
import dcraft.util.StringUtil;
import dcraft.util.net.JSONSubscriber;
import dcraft.xml.XElement;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class LightspeedRetailUtil {
	static public void account(String accessToken, OperationOutcomeRecord callback) throws OperatingContextException {
		LightspeedRetailUtil.buildSendRequest(accessToken, "Account.json", "GET", null, callback);
	}

	static public void itemList(String alt, String accessToken, ListStruct relations, int offset, String query, OperationOutcomeRecord callback) throws OperatingContextException {
		XElement mc = ApplicationHub.getCatalogSettings("CMS-LightspeedRetail", alt);

		if (mc == null) {
			Logger.error("Missing Lightspeed Retail settings.");

			if (callback != null)
				callback.returnEmpty();

			return;
		}

		String accountId = mc.attr("AccountId");

		LightspeedRetailUtil.itemList(null, accessToken, accountId, relations, offset, query, callback);
	}

	static public void itemList(String alt, String accessToken, String accountid, ListStruct relations, int offset, String query, OperationOutcomeRecord callback) throws OperatingContextException {
		String path = "Account/" + accountid + "/Item.json?offset=" + (offset * 100);

		if ((relations != null) && (relations.size() > 0))
			path += "&load_relations=" + URLEncoder.encode(relations.toString().replace(" ", ""), StandardCharsets.UTF_8);

		if (StringUtil.isNotEmpty(query))
			path += "&" + query;

		//System.out.println(path);

		LightspeedRetailUtil.buildSendRequest(accessToken, path, "GET", null, callback);
	}

	static public void itemShopUpdate(String alt, String accessToken, String itemid, RecordStruct data, OperationOutcomeRecord callback) throws OperatingContextException {
		XElement mc = ApplicationHub.getCatalogSettings("CMS-LightspeedRetail", alt);

		if (mc == null) {
			Logger.error("Missing Lightspeed Retail settings.");

			if (callback != null)
				callback.returnEmpty();

			return;
		}

		String accountId = mc.attr("AccountId");

		LightspeedRetailUtil.itemShopUpdate(null, accessToken, accountId, itemid, data, callback);
	}

	static public void itemShopUpdate(String alt, String accessToken, String accountid, String itemid, RecordStruct data, OperationOutcomeRecord callback) throws OperatingContextException {
		String path = "Account/" + accountid + "/Item/" + itemid + ".json";

		RecordStruct req = RecordStruct.record()
				.with("ItemShops", RecordStruct.record()
						.with("ItemShop", data)
				);

		System.out.println("path: " + path);

		LightspeedRetailUtil.buildSendRequest(accessToken, path, "PUT", req, callback);
	}

	static public void buildSendRequest(String accesstoken, String path, String method, CompositeStruct post, OperationOutcomeRecord callback) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		try {
			HttpClient
					.newHttpClient()
					.sendAsync(LightspeedRetailUtil.buildRequest(accesstoken, path, method, post).build(), new JSONSubscriber())
					.thenAcceptAsync(new LightspeedJSONConsumer(callback));
		}
		catch (Exception x) {
			Logger.error("Error processing: Unable to connect to Lightspeed. Error: " + x);
			callback.returnEmpty();
		}
	}

	static public HttpRequest.Builder buildRequest(String accesstoken, String path, String method, CompositeStruct post) {
		String endpoint = "https://api.lightspeedapp.com/API/" + path;

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.header("User-Agent", "dcServer/2021.7 (Language=Java/11)")
				.header("Authorization", "Bearer " + accesstoken);

		if (post != null) {
			builder.header("Content-Type", "application/json");

			//System.out.println("Request: " + post.toPrettyString());

			if ("POST".equals(method))
				builder.POST(HttpRequest.BodyPublishers.ofString(post.toString()));
			else if ("PUT".equals(method))
				builder.PUT(HttpRequest.BodyPublishers.ofString(post.toString()));
		}
		else if ("DELETE".equals(method))
			builder.DELETE();
		else
			builder.GET();

		return builder;
	}

	static public void getAccessToken(String alt, OperationOutcomeString callback) throws OperatingContextException {
		XElement mc = ApplicationHub.getCatalogSettings("CMS-LightspeedRetail", alt);

		if (mc == null) {
			Logger.error("Missing Lightspeed Retail settings.");

			if (callback != null)
				callback.returnEmpty();

			return;
		}

		try {
			OperationContext.getOrThrow().touch();

			String clientId = mc.attr("ClientId");
			String clientSecret = ApplicationHub.getClock().getObfuscator().decryptHexToString(mc.attr("ClientSecret"));
			String refreshToken = ApplicationHub.getClock().getObfuscator().decryptHexToString(mc.attr("RefreshToken"));

			HttpRequest.Builder builder = LightspeedRetailUtil.getAccessToken(refreshToken, clientId, clientSecret);

			// Send post request
			HttpClient.newHttpClient().sendAsync(builder.build(), new JSONSubscriber())
					.thenAcceptAsync(
							new LightspeedJSONConsumer(new OperationOutcomeRecord() {
								@Override
								public void callback(RecordStruct result) throws OperatingContextException {
									if (this.isNotEmptyResult())
										callback.returnValue(result.getFieldAsString("access_token"));
									else
										callback.returnEmpty();
								}
							})
					);
		}
		catch (Exception x) {
			Logger.error("Error processing listing: Unable to connect to Lightspeed. Error: " + x);
			callback.returnEmpty();
		}
	}

	static public HttpRequest.Builder getAccessToken(String refreshtoken, String clientid, String clientsecret) {
		String endpoint = "https://cloud.lightspeedapp.com/oauth/access_token.php";

		RecordStruct post = RecordStruct.record()
				.with("refresh_token", refreshtoken)
				.with("client_id", clientid)
				.with("client_secret", clientsecret)
				.with("grant_type", "refresh_token");

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.header("User-Agent", "dcServer/2021.7 (Language=Java/11)")
				.header("Content-Type", "application/json")
				.header("Accept-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(post.toString()));

		return builder;
	}

	static public class LightspeedJSONConsumer implements Consumer<HttpResponse<CompositeStruct>> {
		protected OperationOutcomeRecord callback = null;

		public LightspeedJSONConsumer(OperationOutcomeRecord callback) {
			this.callback = callback;
		}

		@Override
		public void accept(HttpResponse<CompositeStruct> response) {
			int responseCode = response.statusCode();

			if ((responseCode < 200) || (responseCode > 299))
				Logger.error("Error processing request: Lightspeed sent an unexpected response code: " + responseCode);

			if (response.body() == null) {
				Logger.error("Error processing request: Lightspeed sent an incomplete response: " + responseCode);
				callback.returnEmpty();
			}
			else {
				callback.returnValue(Struct.objectToRecord(response.body()));
			}
		}
	}
}
