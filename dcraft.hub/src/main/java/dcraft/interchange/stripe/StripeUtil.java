package dcraft.interchange.stripe;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.Base64;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class StripeUtil {
	/* example

	{
	"error": {
	"message": "It looks like you're making a cross-origin request from a web page or browser extension. You'll need to use Bearer authentication instead of basic authentication (which we're deprecating). Basically, you need to set the header 'Authorization' to 'Bearer <API_KEY>'. If you're having trouble with this, email support@stripe.com",
	"type": "invalid_request_error"
	}
	}

	or

	{
	"balance_transaction": "txn_1DNNNNNNN",
	"metadata":  {

	} ,
	"livemode": false,
	"destination": null,
	"description": "test charge x1",
	"failure_message": null,
	"fraud_details":  {

	} ,
	"source":  {
	"address_zip_check": "pass",
	"country": "US",
	"last4": "4242",
	"funding": "credit",
	"metadata":  {

	} ,
	"address_country": null,
	"address_state": null,
	"exp_month": 8,
	"exp_year": 2021,
	"address_city": null,
	"tokenization_method": null,
	"cvc_check": null,
	"address_line2": null,
	"address_line1": null,
	"fingerprint": "0XNYNNNNNNNN",
	"name": "Ted NNNNNNN",
	"id": "card_1DNNNNNNNNN",
	"address_line1_check": null,
	"address_zip": "77777",
	"dynamic_last4": null,
	"brand": "Visa",
	"object": "card",
	"customer": null
	} ,
	"amount_refunded": 0,
	"refunds":  {
	"data":  [

	] ,
	"total_count": 0,
	"has_more": false,
	"url": "\/v1\/charges\/ch_1DNNNNN\/refunds",
	"object": "list"
	} ,
	"statement_descriptor": null,
	"shipping": null,
	"review": null,
	"captured": true,
	"currency": "usd",
	"refunded": false,
	"id": "ch_1DNNNNNN",
	"outcome":  {
	"reason": null,
	"risk_level": "normal",
	"risk_score": 47,
	"seller_message": "Payment complete.",
	"network_status": "approved_by_network",
	"type": "authorized"
	} ,
	"order": null,
	"dispute": null,
	"amount": 234,
	"failure_code": null,
	"transfer_group": null,
	"on_behalf_of": null,
	"created": 1541543834,
	"source_transfer": null,
	"receipt_number": null,
	"application": null,
	"receipt_email": null,
	"paid": true,
	"application_fee": null,
	"payment_intent": null,
	"invoice": null,
	"object": "charge",
	"customer": null,
	"status": "succeeded"
	}

	*/
	static public void confirmCharge(String alt, BigDecimal amount, String currency, String token, String desc, OperationOutcomeRecord callback) {
		try {
			// switch amount to cents
			String body = "amount=" + URLEncoder.encode(amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString(), "UTF-8")
					+ "&currency=" + URLEncoder.encode(currency, "UTF-8")
					+ "&source=" + URLEncoder.encode(token, "UTF-8")
					+ "&description=" + URLEncoder.encode(desc, "UTF-8");

			HttpRequest req = StripeUtil.buildBasicRequest(alt, "/charges", body).build();

			// Send post request
			HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofString())
					.thenAcceptAsync(response -> {
						callback.useContext();		// restore our operation context

						int responseCode = response.statusCode();
						String respBody = response.body();

						if (StringUtil.isNotEmpty(respBody)) {
							RecordStruct resp = Struct.objectToRecord(respBody);

							if (resp == null) {
								Logger.error("Error processing payment: Stripe sent an incomplete response.");

								callback.returnValue(
										new RecordStruct()
												.with("Code", responseCode)
												.with("Message", "Incomplete response ")
								);
							}
							else {
								// TODO remove sout
								///System.out.println("Stripe Resp: \n" + resp.toPrettyString());

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
							Logger.error("Error processing payment: Problem with Stripe gateway.");
							Logger.error("Stripe Response: " + responseCode);

							callback.returnValue(
									new RecordStruct()
											.with("Code", responseCode)
											.with("Message", "Failed to process payment ")
							);
						}
					});
		}
		catch (Exception x) {
			Logger.error("Error calling service, Stripe error: " + x);

			callback.returnValue(
					new RecordStruct()
							.with("Code", 1)
							.with("Message", "Failed to call service")
			);
		}
	}

	/* returns
	{
	  "id": "re_1GkXSD2eZvKYlo2CWrwiwmma",
	  "object": "refund",
	  "amount": 100,
	  "balance_transaction": null,
	  "charge": "ch_1GkXSC2eZvKYlo2Cto42czMm",
	  "created": 1589902445,
	  "currency": "usd",
	  "metadata": {},
	  "payment_intent": null,
	  "reason": null,
	  "receipt_number": null,
	  "source_transfer_reversal": null,
	  "status": "succeeded",
	  "transfer_reversal": null
	}
	 */
	static public void refundCharge(String alt, String chargeid, BigDecimal amount, OperationOutcomeRecord callback) {
		try {
			// switch amount to cents
			String body = "charge=" + URLEncoder.encode(chargeid, "UTF-8");

			if (amount != null)
				body += "&amount=" + URLEncoder.encode(amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString(), "UTF-8");

			HttpRequest req = StripeUtil.buildBasicRequest(alt, "/refunds", body).build();

			// Send post request
			HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofString())
					.thenAcceptAsync(response -> {
						callback.useContext();		// restore our operation context

						int responseCode = response.statusCode();
						String respBody = response.body();

						if (StringUtil.isNotEmpty(respBody)) {
							RecordStruct resp = Struct.objectToRecord(respBody);

							if (resp == null) {
								Logger.error("Error processing refund: Stripe sent an incomplete response.");
								callback.returnEmpty();
							}
							else {
								// TODO remove sout
								//System.out.println("Stripe Resp:\n" + resp.toPrettyString());

								if (resp.hasField("error")) {
									Logger.error("Unable to confirm refund: " + resp.selectAsString("error.message"));
									callback.returnEmpty();
								}
								else {
									callback.returnValue(resp);
								}
							}
						}
						else {
							Logger.error("Error processing refund: Problem with Stripe gateway.");
							Logger.error("Stripe Response: " + responseCode);
							callback.returnEmpty();
						}
					});
		}
		catch (Exception x) {
			Logger.error("Error calling service, Stripe error: " + x);
			callback.returnEmpty();
		}
	}

	static public HttpRequest.Builder buildBasicRequest(String alt, String path, String post) {
		XElement stripe = ApplicationHub.getCatalogSettings("CMS-Stripe", alt);

		if (stripe == null) {
			Logger.error("Missing Stripe settings.");
			return null;
		}

		String auth = stripe.getAttribute("AuthPlain");

		if (StringUtil.isEmpty(auth)) {
			Logger.error("Missing Stripe auth.");
			return null;
		}

		String endpoint = "https://api.stripe.com/v1" + path;

		String authStringEnc = Base64.encodeToString(Utf8Encoder.encode(auth + ":"), false);

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
