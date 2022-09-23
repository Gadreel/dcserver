package dcraft.interchange.authorize;

import java.io.DataOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.util.function.Consumer;

import javax.net.ssl.HttpsURLConnection;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.*;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.util.cb.TimeoutPlan;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;

public class AuthUtilXml {
	static public final String AUTH_TEST_ENDPOINT = "https://apitest.authorize.net/xml/v1/request.api";
	static public final String AUTH_LIVE_ENDPOINT = "https://api.authorize.net/xml/v1/request.api";
	
	static public void authXCard(String authalt, String refid, RecordStruct order, OperationOutcomeRecord callback) throws OperatingContextException {
		if (! order.validate("dcmOrderInfo")) {
			callback.returnValue(
					new RecordStruct()
							.with("Code", "dc1")
							.with("Message", "Invalid order info")
			);
			return;
		}

		if (order.isFieldEmpty("PaymentInfo")) {
			Logger.error("Missing payment details.");
			callback.returnValue(
					new RecordStruct()
							.with("Code", "dc2")
							.with("Message", "Missing payment details")
			);
			return;
		}

		XElement auth = ApplicationHub.getCatalogSettings("CMS-Authorize", authalt);

		if (auth == null) {
			Logger.error("Missing store Authorize settings.");
			callback.returnValue(
					new RecordStruct()
							.with("Code", "dc3")
							.with("Message", "Missing Authorize settings")
			);
			return;
		}

		String authid = auth.getAttribute("LoginId");
		String key = auth.getAttribute("TransactionKey");

		String authkey = ApplicationHub.getClock().getObfuscator().decryptHexToString(key);

		boolean live = auth.getAttributeAsBooleanOrFalse("Live");

		RecordStruct paymentinfo = (RecordStruct) order.removeField("PaymentInfo").getValue();
		RecordStruct custinfo = order.getFieldAsRecord("CustomerInfo");		// required
		RecordStruct billinginfo = order.getFieldAsRecord("BillingInfo");	// not required
		RecordStruct shipinfo = order.getFieldAsRecord("ShippingInfo");	// not required
		RecordStruct calcinfo = order.getFieldAsRecord("CalcInfo");	// not required in schema

		/*
		if (paymentinfo.isFieldEmpty("CardNumber") || paymentinfo.isFieldEmpty("Expiration") || paymentinfo.isFieldEmpty("Code")) {
			Logger.error("Missing payment details.");
			callback.returnEmpty();
			return;
		}
		*/
		
		if (billinginfo == null) {
			Logger.error("Missing billing details.");
			callback.returnValue(
					new RecordStruct()
							.with("Code", "dc4")
							.with("Message", "Missing billing details")
			);
			return;
		}
		
		if (calcinfo == null) {
			Logger.error("Missing billing computations.");
			callback.returnValue(
					new RecordStruct()
							.with("Code", "dc5")
							.with("Message", "Missing payment calculations")
			);
			return;
		}
		
	    XElement root = XElement.tag("createTransactionRequest")
	    		.withAttribute("xmlns", "AnetApi/xml/v1/schema/AnetApiSchema.xsd")
				.with(
					XElement.tag("merchantAuthentication")
						.with(
								XElement.tag("name").withText(authid),
								XElement.tag("transactionKey").withText(authkey)
			    		),
					XElement.tag("refId")
							.withText(refid.replace("_", ""))
				);
	    
	    BigDecimal tax = calcinfo.getFieldAsDecimal("TaxTotal");
	    BigDecimal ship = calcinfo.getFieldAsDecimal("ShipTotal");
	    BigDecimal total = calcinfo.getFieldAsDecimal("GrandTotal");
		
	    XElement payel = null;
	    
	    if (paymentinfo.isNotFieldEmpty("CardNumber")) {
	    	payel = XElement.tag("creditCard")
					.with(
							XElement.tag("cardNumber").withText(paymentinfo.getFieldAsString("CardNumber")),
							XElement.tag("expirationDate").withText(paymentinfo.getFieldAsString("Expiration")),
							XElement.tag("cardCode").withText(paymentinfo.getFieldAsString("Code"))
					);
		}
		else if (paymentinfo.isNotFieldEmpty("Token1")) {
			payel = XElement.tag("opaqueData")
					.with(
							XElement.tag("dataDescriptor").withText(paymentinfo.getFieldAsString("Token1")),
							XElement.tag("dataValue").withText(paymentinfo.getFieldAsString("Token2"))
					);
		}
		
	    XElement txreq = XElement.tag("transactionRequest")
				.with(
					XElement.tag("transactionType").withText("authCaptureTransaction"),		// or authOnlyTransaction
					XElement.tag("amount").withText(new DecimalFormat("0.00").format(total)),
					XElement.tag("payment").with(payel),
					XElement.tag("order").with(
							XElement.tag("invoiceNumber").withText("ORD-" + refid.substring(13))
					)
				);
	    
		ListStruct items = order.getFieldAsList("Items");
		
		if ((items != null) && (items.getSize() > 0)) {
			XElement ilist = XElement.tag("lineItems");
			
			for (BaseStruct i : items.items()) {
				RecordStruct itm = (RecordStruct) i;
				
				BigDecimal price = itm.isFieldEmpty("SalePrice")
						? itm.getFieldAsDecimal("Price") : itm.getFieldAsDecimal("SalePrice");
						
				if (price == null)
					price = BigDecimal.ZERO;
				
				String title = itm.getFieldAsString("Title");
				
				if (StringUtil.isEmpty(title))
					title = "[unknown]";
				
				if (title.length() > 31)
					title = title.substring(0, 31);
				
				//String desc = ASCIIFoldingFilter.foldToASCII(itm.getFieldAsString("Description"));
				
				//if (StringUtil.isEmpty(desc))
				//	desc = "[not availale]";
				
				//if (desc.length() > 255)
				//	desc = desc.substring(0, 255);
				
				XElement iline = XElement.tag("lineItem");
				
				if (itm.hasField("Sku"))
					iline.with(XElement.tag("itemId").withText(ASCIIFoldingFilter.foldToASCII(itm.getFieldAsString("Sku"))));
				
				iline.with(XElement.tag("name").withText(ASCIIFoldingFilter.foldToASCII(title)));
				//iline.add(new XElement("description").withText(desc));
				iline.with(XElement.tag("quantity").withText(itm.getFieldAsString("Quantity")));
				iline.with(XElement.tag("unitPrice").withText(new DecimalFormat("0.00").format(price)));
				
				ilist.with(iline);
			}
			
		    txreq.with(ilist);
		}

		txreq.with(
			XElement.tag("tax")
				.with(
					XElement.tag("amount").withText(new DecimalFormat("0.00").format(tax)),
					XElement.tag("name").withText(billinginfo.getFieldAsString("State"))
				)
		);

		if (shipinfo != null) {
			txreq.with(
					XElement.tag("shipping")
							.with(
									XElement.tag("amount").withText(new DecimalFormat("0.00").format(ship)),
									XElement.tag("name").withText(shipinfo.getFieldAsString("State"))
							)
			);
		}

		if (paymentinfo.isNotFieldEmpty("PONumber"))
			txreq.with(
					XElement.tag("poNumber").withText(ASCIIFoldingFilter.foldToASCII(paymentinfo.getFieldAsString("PONumber")))
			);

	    if (!custinfo.isFieldEmpty("CustomerId"))
		    txreq.with(
					XElement.tag("customer")
						.with(
							XElement.tag("id").withText(custinfo.getFieldAsString("CustomerId").replace("_", "")),
							XElement.tag("email").withText(ASCIIFoldingFilter.foldToASCII(custinfo.getFieldAsString("Email")))
						)
		    );
	    else
		    txreq.with(
		    		XElement.tag("customer")
						.with(
							XElement.tag("email").withText(ASCIIFoldingFilter.foldToASCII(custinfo.getFieldAsString("Email")))
			    		)
		    );

		txreq.with(
	    		XElement.tag("billTo")
					.with(
						XElement.tag("firstName").withText(ASCIIFoldingFilter.foldToASCII(billinginfo.getFieldAsString("FirstName"))),
						XElement.tag("lastName").withText(ASCIIFoldingFilter.foldToASCII(billinginfo.getFieldAsString("LastName"))),
						XElement.tag("address").withText(ASCIIFoldingFilter.foldToASCII(billinginfo.getFieldAsString("Address"))),
						XElement.tag("city").withText(ASCIIFoldingFilter.foldToASCII(billinginfo.getFieldAsString("City"))),
						XElement.tag("state").withText(billinginfo.getFieldAsString("State")),
						XElement.tag("zip").withText(billinginfo.getFieldAsString("Zip")),
						XElement.tag("country").withText("USA"),			// TODO add international support
						XElement.tag("phoneNumber").withText(custinfo.getFieldAsString("Phone"))
					)
	    );
	    
	    if (shipinfo != null)
		    txreq.with(
		    		XElement.tag("shipTo")
						.with(
							XElement.tag("firstName").withText(ASCIIFoldingFilter.foldToASCII(shipinfo.getFieldAsString("FirstName"))),
							XElement.tag("lastName").withText(ASCIIFoldingFilter.foldToASCII(shipinfo.getFieldAsString("LastName"))),
							XElement.tag("address").withText(ASCIIFoldingFilter.foldToASCII(shipinfo.getFieldAsString("Address"))),
							XElement.tag("city").withText(ASCIIFoldingFilter.foldToASCII(shipinfo.getFieldAsString("City"))),
							XElement.tag("state").withText(shipinfo.getFieldAsString("State")),
							XElement.tag("zip").withText(shipinfo.getFieldAsString("Zip")),
							XElement.tag("country").withText("USA")		// TODO add international support
						)
		    );
	    
	    String origin = OperationContext.getOrThrow().getOrigin();
	    
	    // track web customers
	    if (StringUtil.isNotEmpty(origin) && origin.startsWith("http:")) 
	    	txreq.with(new XElement("customerIP", origin.substring(5)));
	    
	    /* TODO possible enhancements
    	XElement settings = new XElement("transactionSettings");

    	if (test)
	    	settings.add(
	    		new XElement("setting",
	    			new XElement("settingName", "testRequest"),
	    			new XElement("settingValue", "true")
	    		)
	    	);
    	
    	settings.add(
	    		new XElement("setting",
	    			new XElement("settingName", "emailCustomer"),
	    			new XElement("settingValue", "false")
	    		)
	    	);
	    
      	txreq.add(settings);
    	*/
      
	    root.with(txreq);

	    // Auth documentation:
	    // http://developer.authorize.net/api/reference/
	    
	    try {
	    	OperationContext.getOrThrow().touch();
	    	
			URL url = new URL(live ? AUTH_LIVE_ENDPOINT : AUTH_TEST_ENDPOINT);
		    
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			 
			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "DivConq/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "text/xml");
	 
			String body = root.toString();
		
			//System.out.println("I: " + body);

			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(body);
			wr.flush();
			wr.close();
	 
			int responseCode = con.getResponseCode();
	
			if (responseCode == 200) {
				// parse and close response stream
				XElement resp = XmlReader.parse(con.getInputStream(), false, true);
				
				System.out.println("X: " + resp.toPrettyString());

				if (resp == null) {
					Logger.error("Error processing payment: Gateway sent an incomplete response.");

					callback.returnValue(
							new RecordStruct()
									.with("Code", "dc6")
									.with("Message", "incomplete response")
					);

					return;
				}

				XElement tr = resp.find("transactionResponse");
				
				if (tr == null) {
					Logger.error("Error processing payment: Gateway sent an incomplete response.");

					callback.returnValue(
							new RecordStruct()
									.with("Code", "dc7")
									.with("Message", "incomplete response")
					);

					return;
				}

				XElement trc = tr.find("responseCode");
				XElement trid = tr.find("transId");

				if ((trc == null) || (trid == null)) {
					Logger.error("Error processing payment: Gateway sent an incomplete transaction response.");

					callback.returnValue(
							new RecordStruct()
									.with("Code", "dc8")
									.with("Message", "incomplete response: " + (trc == null ? "no code" : trc.getText()))
					);

					return;
				}

				String rcodeout = trc.getText();
				String txidout = trid.getText();

				if (! "1".equals(rcodeout)) {
					Logger.error("Payment was rejected by gateway");

					callback.returnValue(
							new RecordStruct()
									.with("Code", rcodeout)
									.with("TxId", txidout)
									.with("Message", "rejected")
					);
				}
				else {
					callback.returnValue(
							new RecordStruct()
									.with("Code", rcodeout)
									.with("TxId", txidout)
									.with("Message", "accepted")
					);
				}

				return;
			}
			else {
				Logger.error("Error processing payment: Unable to connect to payment gateway.");
			}
	    }
	    catch (Exception x) {
			Logger.error("Error processing payment: Unable to connect to payment gateway. Error: " + x);
	    }

		callback.returnValue(
				new RecordStruct()
						.with("Code", "dc9")
						.with("Message", "Failed to process payment")
		);
	}

	static public void paymentTransaction(IParentAwareWork stack, XElement txbody, String refid, String authalt, OperationOutcome<XElement> callback) throws OperatingContextException {
		XElement auth = ApplicationHub.getCatalogSettings("CMS-Authorize", authalt);

		if (auth == null) {
			Logger.error("Missing store Authorize settings.");
			callback.returnEmpty();
			return;
		}

		String authid = auth.getAttribute("LoginId");
		String key = auth.getAttribute("TransactionKey");

		String authkey = ApplicationHub.getClock().getObfuscator().decryptHexToString(key);

		boolean live = auth.getAttributeAsBooleanOrFalse("Live");

	    XElement root = XElement.tag("createTransactionRequest")
	    		.withAttribute("xmlns", "AnetApi/xml/v1/schema/AnetApiSchema.xsd")
				.with(
					XElement.tag("merchantAuthentication")
						.with(
								XElement.tag("name").withText(authid),
								XElement.tag("transactionKey").withText(authkey)
			    		)
				);

		if (StringUtil.isNotEmpty(refid))
			root.with(
				XElement.tag("refId")
						.withText(refid.replace("_", ""))
			);

		root.with(txbody);

		// Auth documentation:
	    // http://developer.authorize.net/api/reference/

	    try {
	    	OperationContext.getOrThrow().touch();

			String endpoint = ! live ? AuthUtilXml.AUTH_TEST_ENDPOINT : AuthUtilXml.AUTH_LIVE_ENDPOINT;

			String xml = root.toPrettyString();

			if (stack != null)
				xml = StackUtil.resolveValueToString(stack, xml, true);

			System.out.println("in: " + xml);

			HttpRequest.Builder builder = HttpRequest.newBuilder()
					.uri(URI.create(endpoint))
					.header("User-Agent", "dcServer/2019.1 (Language=Java/11)")
					.header("Accept", "application/json")
					.header("Content-Type", "text/xml")
					.POST(HttpRequest.BodyPublishers.ofString(xml));

			// Send post request
			HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
					.thenAcceptAsync(new AuthUtilXml.AuthHttpResponse() {
						@Override
						public void callback(XElement result) throws OperatingContextException {
							if (result == null) {
								callback.returnEmpty();
							}
							else if (! this.hasErrors()) {
								XElement resp = result.selectFirst("transactionResponse");

								if (resp != null) {
									Long code = Struct.objectToInteger(resp.selectFirstText("responseCode"));

									if ((code != null) && (code == 1)) {
										callback.returnValue(resp);
									}
									else {
										Logger.error("Card declined");
										callback.returnEmpty();
									}
								}
								else {
									Logger.error("Card declined");
									callback.returnEmpty();
								}
							}
							else {
								Logger.error("Card declined");
								callback.returnEmpty();
							}
						}
					});
	    }
	    catch (Exception x) {
			Logger.error("Error processing payment: Unable to connect to payment gateway. Error: " + x);

			callback.returnEmpty();
	    }
	}

	static abstract public class AuthHttpResponse extends OperationOutcome<XElement> implements Consumer<HttpResponse<String>> {
		public AuthHttpResponse() throws OperatingContextException {
			super();
		}

		public AuthHttpResponse(TimeoutPlan plan) throws OperatingContextException {
			super(plan);
		}

		@Override
		public void accept(HttpResponse<String> response) {
			this.useContext();

			this.returnValue(processResponse(response));
		}
	}

	static protected XElement processResponse(HttpResponse<String> response) {
		int responseCode = response.statusCode();
		XElement respBody = null;

		if ((responseCode < 200) || (responseCode > 299)) {
			Logger.error("Error processing request: Auth sent an error response code: " + responseCode);
		}
		else {
			String respraw = response.body();

			if (StringUtil.isNotEmpty(respraw)) {
				respBody = XmlReader.parse(respraw, false, true);

				if (respBody == null) {
					Logger.error("Error processing request: Auth sent an incomplete response: " + responseCode);
				}
				else {
					System.out.println("Auth Resp: " + responseCode + "\n" + respBody.toPrettyString());

					if (!"Ok".equals(respBody.selectFirstText("messages/resultCode"))) {
						Logger.error("Error processing auth request: " + responseCode + " : " + respBody.selectFirstText("messages/message/text"));
					}
				}
			}
		}

		return respBody;
	}
}
