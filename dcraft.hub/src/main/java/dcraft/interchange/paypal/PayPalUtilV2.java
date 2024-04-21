package dcraft.interchange.paypal;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.hub.op.OperationOutcomeString;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.Base64;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class PayPalUtilV2 {
	static public final String SandboxEndpoint = "https://api-m.sandbox.paypal.com";
	static public final String LiveEndpoint = "https://api-m.paypal.com";

	static public void checkGetToken(String alt, OperationOutcomeString callback) throws OperatingContextException {
		HttpRequest req = PayPalUtilV2.buildBasicRequestV1(alt, "/oauth2/token", "grant_type=client_credentials").build();

		// Send post request
		HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofString())
				.thenAcceptAsync(response -> {
					callback.useContext();		// restore our operation context

					int responseCode = response.statusCode();
					String respBody = response.body();

					if (StringUtil.isNotEmpty(respBody)) {
						RecordStruct resp = Struct.objectToRecord(respBody);

						if (resp == null) {
							Logger.error("Error getting token: PayPal sent an incomplete response.");

							callback.returnEmpty();
						}
						else {
							// TODO remove sout
							///System.out.println("Stripe Resp: \n" + resp.toPrettyString());

							if (resp.isFieldEmpty("access_token")) {
								Logger.error("Unable to collect paypal access token: " + resp);

								callback.returnEmpty();
							}
							else {
								callback.returnValue(resp.getFieldAsString("access_token"));
							}
						}
					}
					else {
						Logger.error("Error processing payment: Problem with paypal gateway.");
						Logger.error("Paypal Response: " + responseCode);

						callback.returnEmpty();
					}
				});
	}

	static public HttpRequest.Builder buildBasicRequestV1(String alt, String path, String post) {
		XElement stripe = ApplicationHub.getCatalogSettings("CMS-PayPal", alt);

		if (stripe == null) {
			Logger.error("Missing paypal settings.");
			return null;
		}

		String secret = stripe.getAttribute("SecretPlain");
		String clientId = stripe.getAttribute("ClientId");
		boolean islive = stripe.getAttributeAsBooleanOrFalse("Live");

		if (StringUtil.isEmpty(secret) || StringUtil.isEmpty(clientId)) {
			Logger.error("Missing paypal auth.");
			return null;
		}

		String endpoint = islive ? LiveEndpoint + path : SandboxEndpoint + "/v1" + path;

		String authStringEnc = Base64.encodeToString(Utf8Encoder.encode(clientId + ":" + secret), false);

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.header("Authorization", "Basic " + authStringEnc)
				.header("User-Agent", "dcServer/2019.1 (Language=Java/11)")
				.header("Accept", "application/json")
				.header("Accept-Language", "en_US");

		if (StringUtil.isEmpty(post))
			return builder.GET();

		return builder
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(post));
	}

	static public void getOrderInfo(String alt, String token, String orderid, OperationOutcomeRecord callback) {
		try {
			HttpRequest req = PayPalUtilV2.buildBasicRequestV2(alt, "/checkout/orders/" + orderid, token, null).build();

			// Send post request
			HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofString())
					.thenAcceptAsync(response -> {
						callback.useContext();		// restore our operation context

						int responseCode = response.statusCode();
						String respBody = response.body();

						if (StringUtil.isNotEmpty(respBody)) {
							if ((responseCode <  200) || (responseCode > 299)) {
								Logger.error("Unable to get order info: " + responseCode);

								System.out.println(respBody);

								callback.returnValue(
										new RecordStruct()
												.with("Code", responseCode)
												.with("Message", "Failed to get order info")
								);
							}
							else {
								RecordStruct resp = Struct.objectToRecord(respBody);

								if (resp == null) {
									Logger.error("Error getting order info: paypal sent an incomplete response.");

									callback.returnValue(
											new RecordStruct()
													.with("Code", responseCode)
													.with("Message", "Incomplete response ")
									);
								}
								else {
										resp.with("Code", 200);

										callback.returnValue(resp);
								}
							}
						}
						else {
							Logger.error("Error getting order info: Problem with paypal gateway.");
							Logger.error("PayPal Response: " + responseCode);

							callback.returnValue(
									new RecordStruct()
											.with("Code", responseCode)
											.with("Message", "Failed to get order info ")
							);
						}
					});
		}
		catch (Exception x) {
			Logger.error("Error calling service, PayPal error: " + x);

			callback.returnValue(
					new RecordStruct()
							.with("Code", 1)
							.with("Message", "Failed to call service")
			);
		}
	}

	static public void getAuthInfo(String alt, String token, String authid, OperationOutcomeRecord callback) {
		try {
			HttpRequest req = PayPalUtilV2.buildBasicRequestV2(alt, "/payments/authorizations/" + authid, token, null).build();

			// Send post request
			HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofString())
					.thenAcceptAsync(response -> {
						callback.useContext();		// restore our operation context

						int responseCode = response.statusCode();
						String respBody = response.body();

						if (StringUtil.isNotEmpty(respBody)) {
							if ((responseCode <  200) || (responseCode > 299)) {
								Logger.error("Unable to get auth info: " + responseCode);

								System.out.println(respBody);

								callback.returnValue(
										new RecordStruct()
												.with("Code", responseCode)
												.with("Message", "Failed to get auth info")
								);
							}
							else {
								RecordStruct resp = Struct.objectToRecord(respBody);

								if (resp == null) {
									Logger.error("Error getting auth info: paypal sent an incomplete response.");

									callback.returnValue(
											new RecordStruct()
													.with("Code", responseCode)
													.with("Message", "Incomplete response ")
									);
								}
								else {
									resp.with("Code", 200);

									callback.returnValue(resp);
								}
							}
						}
						else {
							Logger.error("Error getting auth info: Problem with paypal gateway.");
							Logger.error("PayPal Response: " + responseCode);

							callback.returnValue(
									new RecordStruct()
											.with("Code", responseCode)
											.with("Message", "Failed to get auth info ")
							);
						}
					});
		}
		catch (Exception x) {
			Logger.error("Error calling service, PayPal error: " + x);

			callback.returnValue(
					new RecordStruct()
							.with("Code", 1)
							.with("Message", "Failed to call service")
			);
		}
	}

	static public void captureAuth(String alt, String token, String authid, OperationOutcomeRecord callback) {
		try {
			// empty object captures all
			HttpRequest req = PayPalUtilV2.buildBasicRequestV2(alt, "/payments/authorizations/" + authid + "/capture", token, "{ }").build();

			// Send post request
			HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofString())
					.thenAcceptAsync(response -> {
						callback.useContext();		// restore our operation context

						int responseCode = response.statusCode();
						String respBody = response.body();

						System.out.println("pp2 resp: " + respBody);

						if (StringUtil.isNotEmpty(respBody)) {
							if ((responseCode <  200) || (responseCode > 299)) {
								Logger.error("Unable to capture auth: " + responseCode);

								System.out.println(respBody);

								callback.returnValue(
										new RecordStruct()
												.with("Code", responseCode)
												.with("Message", "Failed to capture auth")
								);
							}
							else {
								RecordStruct resp = Struct.objectToRecord(respBody);

								if (resp == null) {
									Logger.error("Error capturing auth: paypal sent an incomplete response.");

									callback.returnValue(
											new RecordStruct()
													.with("Code", responseCode)
													.with("Message", "Incomplete response ")
									);
								}
								else {
									resp.with("Code", 200);

									callback.returnValue(resp);
								}
							}
						}
						else {
							Logger.error("Error capturing auth: Problem with paypal gateway.");
							Logger.error("PayPal Response: " + responseCode);

							callback.returnValue(
									new RecordStruct()
											.with("Code", responseCode)
											.with("Message", "Failed to capture auth ")
							);
						}
					});
		}
		catch (Exception x) {
			Logger.error("Error calling service, PayPal error: " + x);

			callback.returnValue(
					new RecordStruct()
							.with("Code", 1)
							.with("Message", "Failed to call service")
			);
		}
	}

	static public void getCaptureInfo(String alt, String token, String captureid, OperationOutcomeRecord callback) {
		try {
			HttpRequest req = PayPalUtilV2.buildBasicRequestV2(alt, "/payments/captures/" + captureid, token, null).build();

			// Send post request
			HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofString())
					.thenAcceptAsync(response -> {
						callback.useContext();		// restore our operation context

						int responseCode = response.statusCode();
						String respBody = response.body();

						if (StringUtil.isNotEmpty(respBody)) {
							if ((responseCode <  200) || (responseCode > 299)) {
								Logger.error("Unable to get capture info: " + responseCode);

								System.out.println(respBody);

								callback.returnValue(
										new RecordStruct()
												.with("Code", responseCode)
												.with("Message", "Failed to get capture info")
								);
							}
							else {
								RecordStruct resp = Struct.objectToRecord(respBody);

								if (resp == null) {
									Logger.error("Error getting capture info: paypal sent an incomplete response.");

									callback.returnValue(
											new RecordStruct()
													.with("Code", responseCode)
													.with("Message", "Incomplete response ")
									);
								}
								else {
									resp.with("Code", 200);

									callback.returnValue(resp);
								}
							}
						}
						else {
							Logger.error("Error getting capture info: Problem with paypal gateway.");
							Logger.error("PayPal Response: " + responseCode);

							callback.returnValue(
									new RecordStruct()
											.with("Code", responseCode)
											.with("Message", "Failed to get capture info ")
							);
						}
					});
		}
		catch (Exception x) {
			Logger.error("Error calling service, PayPal error: " + x);

			callback.returnValue(
					new RecordStruct()
							.with("Code", 1)
							.with("Message", "Failed to call service")
			);
		}
	}


	/*
	JS get this object on auth:

	{
  "id": "88F13080DC306242L",
  "intent": "AUTHORIZE",
  "status": "COMPLETED",
  "purchase_units": [
    {
      "reference_id": "default",
      "amount": {
        "currency_code": "USD",
        "value": "47.44"
      },
      "payee": {
        "email_address": "andy-facilitator@andywhitewebworks.com",
        "merchant_id": "6ZDRHX5MB9PEJ"
      },
      "soft_descriptor": "PAYPAL *FACILITATOR",
      "shipping": {
        "name": {
          "full_name": "Andy White"
        },
        "address": {
          "address_line_1": "3704 Hillcrest",
          "admin_area_2": "Madison",
          "admin_area_1": "WI",
          "postal_code": "53705",
          "country_code": "US"
        }
      },
      "payments": {
        "authorizations": [
          {
            "status": "CREATED",
            "id": "53M26725MY494521S",
            "amount": {
              "currency_code": "USD",
              "value": "47.44"
            },
            "seller_protection": {
              "status": "ELIGIBLE",
              "dispute_categories": [
                "ITEM_NOT_RECEIVED",
                "UNAUTHORIZED_TRANSACTION"
              ]
            }
          }
        ]
      }
    }
  ],
  "payer": {
    "name": {
      "given_name": "Andy",
      "surname": "White"
    },
    "email_address": "andy@andywhitewebworks.com",
    "payer_id": "HFYTSPSKZC6N2",
    "address": {
      "country_code": "US"
    }
  },
  "create_time": "2021-11-05T00:14:53Z",
  "update_time": "2021-11-05T00:15:47Z",
  "links": [
    {
      "href": "https://api.sandbox.paypal.com/v2/checkout/orders/88F13080DC306242L",
      "rel": "self",
      "method": "GET"
    }
  ]
}


capture returns:   // tx id is the id right below

{
  "id": "15979839SR604251F",
  "status": "COMPLETED",
  "links": [
    {
      "href": "https://api.sandbox.paypal.com/v2/payments/captures/15979839SR604251F",
      "rel": "self",
      "method": "GET"
    },
    {
      "href": "https://api.sandbox.paypal.com/v2/payments/captures/15979839SR604251F/refund",
      "rel": "refund",
      "method": "POST"
    },
    {
      "href": "https://api.sandbox.paypal.com/v2/payments/authorizations/53M26725MY494521S",
      "rel": "up",
      "method": "GET"
    }
  ]
}
	 */

	static public void confirmCharge(String alt, String token, String orderid, OperationOutcomeRecord callback) {
		try {
			HttpRequest req = PayPalUtilV2.buildBasicRequestV2(alt, "/checkout/orders/" + orderid + "/capture", token, "").build();

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
									Logger.error("Unable to confirm payment token: "
											+ resp.selectAsString("error.code")
											+ " - "
											+ resp.selectAsString("error.message"));

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

	/* view return

	TODO add get code

	https://api-m.sandbox.paypal.com/v2/checkout/orders/88F13080DC306242L

{
  "id": "88F13080DC306242L",
  "intent": "AUTHORIZE",
  "status": "COMPLETED",
  "purchase_units": [
    {
      "reference_id": "default",
      "amount": {
        "currency_code": "USD",
        "value": "47.44"
      },
      "payee": {
        "email_address": "andy-facilitator@andywhitewebworks.com",
        "merchant_id": "6ZDRHX5MB9PEJ"
      },
      "soft_descriptor": "PAYPAL *FACILITATOR",
      "shipping": {
        "name": {
          "full_name": "Andy White"
        },
        "address": {
          "address_line_1": "3704 Hillcrest",
          "admin_area_2": "Madison",
          "admin_area_1": "WI",
          "postal_code": "53705",
          "country_code": "US"
        }
      },
      "payments": {
        "authorizations": [
          {
            "status": "CREATED",
            "id": "53M26725MY494521S",
            "amount": {
              "currency_code": "USD",
              "value": "47.44"
            },
            "seller_protection": {
              "status": "ELIGIBLE",
              "dispute_categories": [
                "ITEM_NOT_RECEIVED",
                "UNAUTHORIZED_TRANSACTION"
              ]
            },
            "expiration_time": "2021-12-04T00:15:47Z",
            "links": [
              {
                "href": "https://api.sandbox.paypal.com/v2/payments/authorizations/53M26725MY494521S",
                "rel": "self",
                "method": "GET"
              },
              {
                "href": "https://api.sandbox.paypal.com/v2/payments/authorizations/53M26725MY494521S/capture",
                "rel": "capture",
                "method": "POST"
              },
              {
                "href": "https://api.sandbox.paypal.com/v2/payments/authorizations/53M26725MY494521S/void",
                "rel": "void",
                "method": "POST"
              },
              {
                "href": "https://api.sandbox.paypal.com/v2/payments/authorizations/53M26725MY494521S/reauthorize",
                "rel": "reauthorize",
                "method": "POST"
              },
              {
                "href": "https://api.sandbox.paypal.com/v2/checkout/orders/88F13080DC306242L",
                "rel": "up",
                "method": "GET"
              }
            ],
            "create_time": "2021-11-05T00:15:47Z",
            "update_time": "2021-11-05T00:15:47Z"
          }
        ]
      }
    }
  ],
  "payer": {
    "name": {
      "given_name": "Andy",
      "surname": "White"
    },
    "email_address": "andy@andywhitewebworks.com",
    "payer_id": "HFYTSPSKZC6N2",
    "address": {
      "country_code": "US"
    }
  },
  "create_time": "2021-11-05T00:14:53Z",
  "update_time": "2021-11-05T00:15:47Z",
  "links": [
    {
      "href": "https://api.sandbox.paypal.com/v2/checkout/orders/88F13080DC306242L",
      "rel": "self",
      "method": "GET"
    }
  ]
}
	 */

	/* returns
	TODO
	static public void refundCharge(String alt, String token, String chargeid, BigDecimal amount, OperationOutcomeRecord callback) {
		try {
			// switch amount to cents
			String body = "charge=" + URLEncoder.encode(chargeid, "UTF-8");

			if (amount != null)
				body += "&amount=" + URLEncoder.encode(amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString(), "UTF-8");

			HttpRequest req = PayPalUtilV2.buildBasicRequestV2(alt, "/refunds", token, body).build();

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

	 */

	static public HttpRequest.Builder buildBasicRequestV2(String alt, String path, String token, String post) {
		XElement stripe = ApplicationHub.getCatalogSettings("CMS-PayPal", alt);

		if (stripe == null) {
			Logger.error("Missing paypal settings.");
			return null;
		}

		String secret = stripe.getAttribute("SecretPlain");
		String clientId = stripe.getAttribute("ClientId");
		boolean islive = stripe.getAttributeAsBooleanOrFalse("Live");

		if (StringUtil.isEmpty(token) && (StringUtil.isEmpty(secret) || StringUtil.isEmpty(clientId))) {
			Logger.error("Missing paypal auth.");
			return null;
		}

		String endpoint = islive ? LiveEndpoint + "/v2" + path : SandboxEndpoint + "/v2" + path;

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.header("Authorization",  StringUtil.isNotEmpty(token)
						? "Bearer " + token
						: "Basic " + Base64.encodeToString(Utf8Encoder.encode(clientId + ":" + secret), false)
				)
				.header("User-Agent", "dcServer/2019.1 (Language=Java/11)")
				.header("Accept", "application/json")
				.header("Accept-Language", "en_US");

		if (post == null)
			return builder.GET();

		return builder
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(post));
	}
}
