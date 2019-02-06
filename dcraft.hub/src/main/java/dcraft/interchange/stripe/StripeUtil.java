package dcraft.interchange.stripe;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.Base64;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLEncoder;

public class StripeUtil {
	static public void confirmCharge(String alt, BigDecimal amount, String currency, String token, String desc, OperationOutcomeRecord callback) {
		XElement stripe = ApplicationHub.getCatalogSettings("CMS-Stripe", alt);
		
		if (stripe == null) {
			Logger.error("Missing Stripe settings.");
			callback.returnEmpty();
			return;
		}

		String auth = stripe.getAttribute("AuthPlain");
		
		if (StringUtil.isEmpty(auth)) {
			Logger.error("Missing Stripe auth.");
			callback.returnEmpty();
			return;
		}
		
		try {
			String endpoint = "https://api.stripe.com/v1/charges";
			
			
			String authString = auth + ":";
			//System.out.println("auth string: " + authString);
			String authStringEnc = Base64.encodeToString(Utf8Encoder.encode(authString), false);
			//System.out.println("Base64 encoded auth string: " + authStringEnc);
			
			URL url = new URL(endpoint);
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			con.setRequestProperty("Authorization", "Basic " + authStringEnc);
			
			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "dcServer/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			
			// switch to cents
			String body = "amount=" + URLEncoder.encode(amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString(), "UTF-8")
					+ "&currency=" + URLEncoder.encode(currency, "UTF-8")
					+ "&source=" + URLEncoder.encode(token, "UTF-8")
					+ "&description=" + URLEncoder.encode(desc, "UTF-8");

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
					Logger.error("Error processing payment: Stripe sent an incomplete response.");
					callback.returnEmpty();
					return;
				}

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

				if (((RecordStruct) resp).hasField("error"))
					Logger.error("Unable to confirm payment token: " + ((RecordStruct) resp).selectAsString("error.message"));

				// TODO remove sout
				//System.out.println("Stripe Resp:\n" + resp.toPrettyString());
				
				callback.returnValue((RecordStruct) resp);
				
				return;
			}
			else {
				Logger.error("Error processing payment: Problem with Stripe gateway.");
			}
			
		}
		catch (Exception x) {
			Logger.error("Error calling service, Stripe error: " + x);
		}
		
		callback.returnEmpty();
	}
}
