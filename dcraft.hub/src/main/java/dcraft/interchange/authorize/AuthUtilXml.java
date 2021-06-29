package dcraft.interchange.authorize;

import java.io.DataOutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.text.DecimalFormat;

import javax.net.ssl.HttpsURLConnection;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
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
					XElement.tag("payment").with(payel)
				);
	    
		ListStruct items = order.getFieldAsList("Items");
		
		if ((items != null) && (items.getSize() > 0)) {
			XElement ilist = XElement.tag("lineItems");
			
			for (Struct i : items.items()) {
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
}
