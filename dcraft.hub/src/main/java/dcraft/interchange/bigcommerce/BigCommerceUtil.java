package dcraft.interchange.bigcommerce;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeList;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import javax.net.ssl.HttpsURLConnection;
import java.net.URL;

public class BigCommerceUtil {
	// https://developer.bigcommerce.com/api-docs/catalog/categories/category-tree
	static public void loadCategoriesTree(String alt, OperationOutcomeRecord callback) {
		XElement bigCommerce = ApplicationHub.getCatalogSettings("BigCommerce", alt);
		
		if (bigCommerce == null) {
			Logger.error("Missing BigCommerce settings.");
			callback.returnEmpty();
			return;
		}
		
		String bcid = bigCommerce.getAttribute("Id");
		
		if (StringUtil.isEmpty(bcid)) {
			Logger.error("Missing BigCommerce Id.");
			callback.returnEmpty();
			return;
		}
		
		String bcstore = bigCommerce.getAttribute("Store");
		
		if (StringUtil.isEmpty(bcstore)) {
			Logger.error("Missing BigCommerce Store.");
			callback.returnEmpty();
			return;
		}
		
		String bctoken = bigCommerce.getAttribute("Token");
		
		if (StringUtil.isEmpty(bctoken)) {
			Logger.error("Missing BigCommerce Token.");
			callback.returnEmpty();
			return;
		}
		
		try {
			OperationContext.getOrThrow().touch();
			
			URL url = new URL("https://api.bigcommerce.com/stores/" + bcstore + "/v3/catalog/categories/tree");
			
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			
			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", "DivConq/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("X-Auth-Client", bcid);
			con.setRequestProperty("X-Auth-Token", bctoken);
			
			int responseCode = con.getResponseCode();
			
			if (responseCode != 200) {
				Logger.error("Error processing api call: Unable to load categories.");
				callback.returnEmpty();
			}
			else {
				// parse and close response stream
				CompositeStruct resp = CompositeParser.parseJson(con.getInputStream());
				
				if (resp == null) {
					Logger.error("Error processing api call: incomplete response.");
					callback.returnEmpty();
				}
				else {
					callback.returnValue((RecordStruct) resp);
				}
			}
		}
		catch (Exception x) {
			Logger.error("Error processing api call: Unable to connect to gateway.");
			callback.returnEmpty();
		}
	}
	
	// https://developer.bigcommerce.com/api-docs/getting-started/basics/making-requests
	static public void loadAllProducts(String alt, int page, OperationOutcomeRecord callback) {
		XElement bigCommerce = ApplicationHub.getCatalogSettings("BigCommerce", alt);
		
		if (bigCommerce == null) {
			Logger.error("Missing BigCommerce settings.");
			callback.returnEmpty();
			return;
		}
		
		String bcid = bigCommerce.getAttribute("Id");
		
		if (StringUtil.isEmpty(bcid)) {
			Logger.error("Missing BigCommerce Id.");
			callback.returnEmpty();
			return;
		}
		
		String bcstore = bigCommerce.getAttribute("Store");
		
		if (StringUtil.isEmpty(bcstore)) {
			Logger.error("Missing BigCommerce Store.");
			callback.returnEmpty();
			return;
		}
		
		String bctoken = bigCommerce.getAttribute("Token");
		
		if (StringUtil.isEmpty(bctoken)) {
			Logger.error("Missing BigCommerce Token.");
			callback.returnEmpty();
			return;
		}
		
		try {
			OperationContext.getOrThrow().touch();
			
			URL url = new URL("https://api.bigcommerce.com/stores/" + bcstore + "/v3/catalog/products?limit=250&page=" + page);
			
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			
			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", "DivConq/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("X-Auth-Client", bcid);
			con.setRequestProperty("X-Auth-Token", bctoken);
			
			int responseCode = con.getResponseCode();
			
			if (responseCode != 200) {
				Logger.error("Error processing api call: Unable to load categories.");
				callback.returnEmpty();
			}
			else {
				// parse and close response stream
				CompositeStruct resp = CompositeParser.parseJson(con.getInputStream());
				
				if (resp == null) {
					Logger.error("Error processing api call: incomplete response.");
					callback.returnEmpty();
				}
				else {
					callback.returnValue((RecordStruct) resp);
				}
			}
		}
		catch (Exception x) {
			Logger.error("Error processing api call: Unable to connect to gateway.");
			callback.returnEmpty();
		}
	}
	
	// https://developer.bigcommerce.com/api-reference/orders/orders-api/orders/getorders
	// 50 at a time
	static public void loadModifiedOrders(String alt, int page, OperationOutcomeList callback) {
		XElement bigCommerce = ApplicationHub.getCatalogSettings("BigCommerce", alt);
		
		if (bigCommerce == null) {
			Logger.error("Missing BigCommerce settings.");
			callback.returnEmpty();
			return;
		}
		
		String bcid = bigCommerce.getAttribute("Id");
		
		if (StringUtil.isEmpty(bcid)) {
			Logger.error("Missing BigCommerce Id.");
			callback.returnEmpty();
			return;
		}
		
		String bcstore = bigCommerce.getAttribute("Store");
		
		if (StringUtil.isEmpty(bcstore)) {
			Logger.error("Missing BigCommerce Store.");
			callback.returnEmpty();
			return;
		}
		
		String bctoken = bigCommerce.getAttribute("Token");
		
		if (StringUtil.isEmpty(bctoken)) {
			Logger.error("Missing BigCommerce Token.");
			callback.returnEmpty();
			return;
		}
		
		try {
			OperationContext.getOrThrow().touch();
			
			URL url = new URL("https://api.bigcommerce.com/stores/" + bcstore + "/v2/orders?limit=50&sort=date_modified:desc&page=" + page);
			
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			
			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", "DivConq/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("Accept", "application/json");
			con.setRequestProperty("X-Auth-Client", bcid);
			con.setRequestProperty("X-Auth-Token", bctoken);
			
			int responseCode = con.getResponseCode();
			
			if (responseCode == 204) {
				Logger.info("Empty list.");
				callback.returnValue(ListStruct.list());
			}
			else if (responseCode != 200) {
				Logger.error("Error processing api call: Unable to load orders.");
				callback.returnEmpty();
			}
			else {
				// parse and close response stream
				CompositeStruct resp = CompositeParser.parseJson(con.getInputStream());
				
				if (resp == null) {
					Logger.error("Error processing api call: incomplete response.");
					callback.returnEmpty();
				}
				else {
					callback.returnValue((ListStruct) resp);
				}
			}
		}
		catch (Exception x) {
			Logger.error("Error processing api call: Unable to connect to big commerce.");
			callback.returnEmpty();
		}
	}
	
	// https://developer.bigcommerce.com/api-reference/orders/orders-api/order-products/getordersorderidproducts
	static public void loadOrderProducts(String alt, long order, OperationOutcomeList callback) {
		XElement bigCommerce = ApplicationHub.getCatalogSettings("BigCommerce", alt);
		
		if (bigCommerce == null) {
			Logger.error("Missing BigCommerce settings.");
			callback.returnEmpty();
			return;
		}
		
		String bcid = bigCommerce.getAttribute("Id");
		
		if (StringUtil.isEmpty(bcid)) {
			Logger.error("Missing BigCommerce Id.");
			callback.returnEmpty();
			return;
		}
		
		String bcstore = bigCommerce.getAttribute("Store");
		
		if (StringUtil.isEmpty(bcstore)) {
			Logger.error("Missing BigCommerce Store.");
			callback.returnEmpty();
			return;
		}
		
		String bctoken = bigCommerce.getAttribute("Token");
		
		if (StringUtil.isEmpty(bctoken)) {
			Logger.error("Missing BigCommerce Token.");
			callback.returnEmpty();
			return;
		}
		
		try {
			OperationContext.getOrThrow().touch();
			
			URL url = new URL("https://api.bigcommerce.com/stores/" + bcstore + "/v2/orders/" + order + "/products");
			
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			
			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", "DivConq/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("Accept", "application/json");
			con.setRequestProperty("X-Auth-Client", bcid);
			con.setRequestProperty("X-Auth-Token", bctoken);
			
			int responseCode = con.getResponseCode();
			
			if (responseCode != 200) {
				Logger.error("Error processing api call: Unable to load orders.");
				callback.returnEmpty();
			}
			else {
				// parse and close response stream
				CompositeStruct resp = CompositeParser.parseJson(con.getInputStream());
				
				if (resp == null) {
					Logger.error("Error processing api call: incomplete response.");
					callback.returnEmpty();
				}
				else {
					callback.returnValue((ListStruct) resp);
				}
			}
		}
		catch (Exception x) {
			Logger.error("Error processing api call: Unable to connect to big commerce.");
			callback.returnEmpty();
		}
	}
}
