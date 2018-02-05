package dcraft.interchange.ups;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.shipengine.ShipEngineUtil;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import sun.print.UnixPrintService;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.math.BigDecimal;
import java.net.URL;

public class UpsUtil {
	static final public String TEST_ENDPOINT = "https://wwwcie.ups.com/rest/";
	static final public String PROD_ENDPOINT = "https://onlinetools.ups.com/rest/";
	
	
	static public void createLabel(String alt, RecordStruct order, String shipService, OperationOutcomeRecord callback) {
		XElement auth = ApplicationHub.getCatalogSettings("CMS-Shipping-UPS", alt);
		
		if (auth == null) {
			Logger.error("Missing UPS settings.");
			callback.returnEmpty();
			return;
		}
		
		String etoken = auth.getAttribute("Token");
		
		if (StringUtil.isEmpty(etoken)) {
			Logger.error("Missing UPS token.");
			callback.returnEmpty();
			return;
		}
		
		String token = ApplicationHub.getClock().getObfuscator().decryptHexToString(etoken);
		
		if (StringUtil.isEmpty(token)) {
			Logger.error("Invalid UPS token setting.");
			callback.returnEmpty();
			return;
		}
		
		String pw = auth.getAttribute("Password");
		
		if (StringUtil.isEmpty(pw)) {
			Logger.error("Missing UPS password.");
			callback.returnEmpty();
			return;
		}
		
		String password = ApplicationHub.getClock().getObfuscator().decryptHexToString(pw);
		
		if (StringUtil.isEmpty(password)) {
			Logger.error("Invalid UPS password setting.");
			callback.returnEmpty();
			return;
		}
		
		String username = auth.getAttribute("Username");
		
		if (StringUtil.isEmpty(username)) {
			Logger.error("Missing UPS username.");
			callback.returnEmpty();
			return;
		}
		
		String shipperNumber = auth.getAttribute("ShipperNumber");
		
		if (StringUtil.isEmpty(shipperNumber)) {
			Logger.error("Missing UPS shipper number.");
			callback.returnEmpty();
			return;
		}

		if (StringUtil.isEmpty(shipService))
			shipService = auth.getAttribute("DefaultServiceCode");
		
		XElement store = ApplicationHub.getCatalogSettings("CMS-Store", alt);		// alt applies across storefront
		
		if (store == null) {
			Logger.error("Missing Store settings.");
			callback.returnEmpty();
			return;
		}
		
		XElement shipfrom = store.selectFirst("Shipping/From");
		
		if (shipfrom == null) {
			Logger.error("Missing Store shipping settings.");
			callback.returnEmpty();
			return;
		}
		
		String endpoint = ApplicationHub.isProduction() ? UpsUtil.PROD_ENDPOINT : UpsUtil.TEST_ENDPOINT;
		
		if (store.getAttributeAsBooleanOrFalse("Testing"))
			endpoint = UpsUtil.TEST_ENDPOINT;
		
		RecordStruct cinfo = order.getFieldAsRecord("CustomerInfo");
		RecordStruct sminfo = order.getFieldAsRecord("ShipmentInfo");
		
		RecordStruct shipment = RecordStruct.record()
				.with("UPSSecurity", RecordStruct.record()
						.with("UsernameToken", RecordStruct.record()
								.with("Username", username)
								.with("Password", password)
						)
						.with("ServiceAccessToken", RecordStruct.record()
								.with("AccessLicenseNumber", token)
						)
				)
				.with("ShipmentRequest", RecordStruct.record()
						.with("Request", RecordStruct.record()
								.with("RequestOption", "nonvalidate")
						)
						.with("Shipment", RecordStruct.record()
							.with("Shipper", RecordStruct.record()
									.with("Name", StringUtil.apiClean(shipfrom.getAttribute("Name"), 35))
									.with("Address", RecordStruct.record()
										.with("AddressLine", StringUtil.apiClean(shipfrom.getAttribute("Street1"), 35))
										// TODO review, not in JSON API .with("address_line2", shipfrom.getAttribute("Street2"))
										.with("City", StringUtil.apiClean(shipfrom.getAttribute("City"), 30))
										.with("StateProvinceCode", shipfrom.getAttribute("State"))
										.with("PostalCode", shipfrom.getAttribute("Zip"))
										.with("CountryCode", shipfrom.getAttribute("Country"))
									)
									.with("Phone", RecordStruct.record()
										.with("Number", shipfrom.getAttribute("Phone"))
									)
									.with("ShipperNumber", shipperNumber)
									.with("EMailAddress", shipfrom.getAttribute("Email"))
							)
							.with("ShipTo", RecordStruct.record()
									.with("Name", StringUtil.apiClean(sminfo.selectAsString("FirstName") + " "
											+ sminfo.selectAsString("LastName"), 35))
									.with("Address", RecordStruct.record()
										.with("AddressLine", StringUtil.apiClean(sminfo.selectAsString("Address"), 35))
										// TODO review, not in JSON API .with("address_line2", shipto.selectAsString("Address2"))
										.with("City", StringUtil.apiClean(sminfo.selectAsString("City"), 30))
										.with("StateProvinceCode", sminfo.selectAsString("State"))
										.with("PostalCode", sminfo.selectAsString("Zip"))
										.with("CountryCode", sminfo.selectAsString("Country", "US"))		// TODO review
									)
									.with("Phone", RecordStruct.record()
										.with("Number", cinfo.selectAsString("Phone"))
									)
							)
							.with("PaymentInformation", RecordStruct.record()
									.with("ShipmentCharge", RecordStruct.record()
											.with("Type", "01")
											.with("BillShipper", RecordStruct.record()
													.with("AccountNumber", shipperNumber)
											)
									)
							)
							.with("Service", RecordStruct.record()
									.with("Code", shipService)
							)
							.with("Package", RecordStruct.record()
									.with("Packaging", RecordStruct.record()
											.with("Code", "02")		// customer supplied package - TODO support standard packages like express box, tube, letter
									)
									.with("Dimensions", RecordStruct.record()
											.with("Length", sminfo.getFieldAsString("Depth"))
											.with("Width", sminfo.getFieldAsString("Width"))
											.with("Height", sminfo.getFieldAsString("Height"))
											.with("UnitOfMeasurement", RecordStruct.record()
													.with("Code", "IN")
											)
									)
									.with("PackageWeight", RecordStruct.record()
											.with("Weight", sminfo.getFieldAsDecimal("Weight").divide(BigDecimal.valueOf(16), 1, BigDecimal.ROUND_HALF_EVEN).toPlainString())
											.with("UnitOfMeasurement", RecordStruct.record()
													.with("Code", "LBS")
											)
									)
							)
						)
				)
				.with("LabelSpecification", RecordStruct.record()
						.with("LabelImageFormat", RecordStruct.record()
							.with("Code", "GIF")
						)
						.with("HTTPUserAgent", "Mozilla/4.5")
				);
		
		System.out.println("UPS request:\n" + shipment.toPrettyString());
		
		try {
			OperationContext.getOrThrow().touch();
			
			URL url = new URL(endpoint + "Ship");
			
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			
			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "dcServer/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/json");
			
			String body = shipment.toString();
			
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
					Logger.error("Error processing shipment: ShipEngine sent an incomplete response.");
					callback.returnEmpty();
					return;
				}
				
				System.out.println("UPS Resp:\n" + resp.toPrettyString());
				
				callback.returnValue((RecordStruct) resp);
				
				return;
			}
			else {
				Logger.error("Error processing shipping: Problem with shipping gateway.");
			}
		}
		catch (Exception x) {
			Logger.error("Error processing shipping: Unable to connect to shipping gateway.");
		}
		
		callback.returnEmpty();
	}
}
