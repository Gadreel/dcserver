package dcraft.interchange.mailchimp;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MailChimpUtil {

	static public void list(String alt, OperationOutcomeRecord callback) {
		XElement mc = ApplicationHub.getCatalogSettings("Mailchimp", alt);

		if (mc == null) {
			Logger.error("Missing Mailchimp settings.");

			if (callback != null)
				callback.returnEmpty();

			return;
		}

		MailChimpUtil.list(mc.attr("APIKey"), mc.attr("DefaultList"), callback);
	}

	static public void list(String apikey, String listid, OperationOutcomeRecord callback) {
		try {
			OperationContext.getOrThrow().touch();

			HttpRequest.Builder builder = MailChimpUtil.buildRequest(apikey, "lists/" + listid + "/members", null);

			// Send post request
			HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
					.thenAcceptAsync(response -> {
						callback.useContext();		// restore our operation context

						int responseCode = response.statusCode();

						if ((responseCode < 200) || (responseCode > 299))
							Logger.error("Error processing request: MailChimp sent an unexpected response code: " + responseCode);

						CompositeStruct resp = CompositeParser.parseJson(response.body());

						if (resp == null) {
							Logger.error("Error processing request: MailChimp sent an incomplete response: " + responseCode);
							callback.returnEmpty();
							return;
						}

						System.out.println("MailChimp Resp: " + responseCode + "\n" + resp.toPrettyString());

						callback.returnValue((RecordStruct) resp);
					});
		}
		catch (Exception x) {
			Logger.error("Error processing listing: Unable to connect to mailchimp. Error: " + x);
			callback.returnEmpty();
		}
	}

	static public void subscribe(String alt, String email, String firstname, String lastname, OperationOutcomeRecord callback) {
		XElement mc = ApplicationHub.getCatalogSettings("Mailchimp", alt);

		if (mc == null) {
			Logger.error("Missing Mailchimp settings.");

			if (callback != null)
				callback.returnEmpty();

			return;
		}

		MailChimpUtil.subscribe(mc.attr("APIKey"), mc.attr("DefaultList"), email, firstname, lastname, callback);
	}

	static public void subscribe(String apikey, String listid, String email, String firstname, String lastname, OperationOutcomeRecord callback) {
		try {
			OperationContext.getOrThrow().touch();

			RecordStruct signup = RecordStruct.record()
				.with("email_address", email)
				.with(	"status", "subscribed")
				.with(	"merge_fields", RecordStruct.record()
					.with("FNAME", firstname)
					.with("LNAME", lastname)
				);

			HttpRequest.Builder builder = MailChimpUtil.buildRequest(apikey, "lists/" + listid + "/members/", signup);

			// Send post request
			HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
					.thenAcceptAsync(response -> {
						callback.useContext();		// restore our operation context

						int responseCode = response.statusCode();

						if ((responseCode < 200) || (responseCode > 299))
							Logger.error("Error processing request: MailChimp sent an unexpected response code: " + responseCode);

						CompositeStruct resp = CompositeParser.parseJson(response.body());

						if (resp == null) {
							Logger.error("Error processing request: MailChimp sent an incomplete response: " + responseCode);
							callback.returnEmpty();
							return;
						}

						System.out.println("MailChimp Resp: " + responseCode + "\n" + resp.toPrettyString());

						callback.returnValue((RecordStruct) resp);
					});
		}
		catch (Exception x) {
			Logger.error("Error processing subscription: Unable to connect to MailChimp. Error: " + x);
			callback.returnEmpty();
		}
	}

	static public HttpRequest.Builder buildRequest(String apikey, String method, CompositeStruct post) {
		String server = apikey.substring(apikey.lastIndexOf('-') + 1);
		String endpoint = "https://" + server + ".api.mailchimp.com/3.0/" + method;

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.header("User-Agent", "dcServer/2019.1 (Language=Java/11)")
				.header("Authorization", "apikey " + apikey);

		if (post != null)
			builder
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(post.toString()));
		else
			builder.GET();

		return builder;
	}
}
