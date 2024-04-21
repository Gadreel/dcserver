package dcraft.interchange.lightspeed;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.CompositeStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.net.JSONSubscriber;
import dcraft.xml.XElement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

public class PosXUtil {
	static public void productList(String alt, String from, OperationOutcomeRecord callback) throws OperatingContextException {
		XElement mc = ApplicationHub.getCatalogSettings("CMS-LightspeedVend", alt);

		if (mc == null) {
			Logger.error("Missing Lightspeed POS X settings.");

			if (callback != null)
				callback.returnEmpty();

			return;
		}

		String subdomain = mc.attr("Subdomain");
		String accessToken = mc.attr("PersonalToken");

		PosXUtil.productList(subdomain, accessToken, from, callback);
	}

	static public void productList(String subdomain, String accessToken, String from, OperationOutcomeRecord callback) throws OperatingContextException {
		String path = "2.0/products?";

		if (StringUtil.isNotEmpty(from))
			path += "after=" + from + "&";

		path += "page_size=200";

		PosXUtil.buildSendRequest(subdomain, accessToken, path, "GET", null, callback);
	}

	static public void buildSendRequest(String subdomain, String accesstoken, String path, String method, CompositeStruct post, OperationOutcomeRecord callback) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		try {
			HttpClient
					.newHttpClient()
					.sendAsync(PosXUtil.buildRequest(subdomain, accesstoken, path, method, post).build(), new JSONSubscriber())
					.thenAcceptAsync(new LightspeedJSONConsumer(callback));
		}
		catch (Exception x) {
			Logger.error("Error processing: Unable to connect to Lightspeed. Error: " + x);
			callback.returnEmpty();
		}
	}

	static public HttpRequest.Builder buildRequest(String subdomain, String accesstoken, String path, String method, CompositeStruct post) {
		String endpoint = "https://" + subdomain + ".vendhq.com/api/" + path;

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.header("User-Agent", "dcServer/2021.7 (Language=Java/11)")
				.header("Authorization", "Bearer " + accesstoken)
				.header("Accept", "application/json");

		System.out.println("- " + accesstoken + " - " + endpoint);

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

	static public class LightspeedJSONConsumer implements Consumer<HttpResponse<CompositeStruct>> {
		protected OperationOutcomeRecord callback = null;

		public LightspeedJSONConsumer(OperationOutcomeRecord callback) {
			this.callback = callback;
		}

		@Override
		public void accept(HttpResponse<CompositeStruct> response) {
			int responseCode = response.statusCode();

			if ((responseCode < 200) || (responseCode > 299))
				Logger.error("Error processing request: Lightspeed POS X sent an unexpected response code: " + responseCode);

			if (response.body() == null) {
				Logger.error("Error processing request: Lightspeed POS X sent an incomplete response: " + responseCode);
				callback.returnEmpty();
			}
			else {

				//System.out.println("- got: " + response.body());

				callback.returnValue(Struct.objectToRecord(response.body()));
			}
		}
	}
}
