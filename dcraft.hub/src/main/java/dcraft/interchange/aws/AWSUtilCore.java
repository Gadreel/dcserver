package dcraft.interchange.aws;

import dcraft.aws.Util;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.HashUtil;
import dcraft.util.HexUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class AWSUtilCore {
	
	static byte[] HmacSHA256(String data, byte[] key) throws GeneralSecurityException, UnsupportedEncodingException {
		String algorithm="HmacSHA256";
		Mac mac = Mac.getInstance(algorithm);
		mac.init(new SecretKeySpec(key, algorithm));
		return mac.doFinal(data.getBytes("UTF-8"));
	}
	
	static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws GeneralSecurityException, UnsupportedEncodingException {
		byte[] kSecret = ("AWS4" + key).getBytes("UTF-8");
		byte[] kDate = HmacSHA256(dateStamp, kSecret);
		byte[] kRegion = HmacSHA256(regionName, kDate);
		byte[] kService = HmacSHA256(serviceName, kRegion);
		byte[] kSigning = HmacSHA256("aws4_request", kService);
		return kSigning;
	}
	
	static public void regions(XElement connection, OperationOutcome<XElement> callback) {
		RecordStruct options = RecordStruct.record()
				.with("Service", "ec2");
		
		//String host = "ec2.amazonaws.com";
		//String region = "us-east-1";
		//String endpoint = "https://ec2.amazonaws.com";
		String request_parameters = "Action=DescribeRegions&Version=2016-11-15";
		//String request_parameters = "Action=DescribeRegions";
		
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest.Builder req = buildRequest(connection, options, "GET", request_parameters);
		
		httpClient.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
				.thenAcceptAsync(response -> {
					callback.useContext();		// restore context
					
					System.out.println("code: " + response.statusCode());
					//System.out.println("got: " + response.body());
					
					callback.returnValue(Struct.objectToXml(response.body()));
				});
	}
	
	static public void describeVolumes(XElement connection, String region, OperationOutcome<XElement> callback) {
		RecordStruct options = RecordStruct.record()
				.with("Service", "ec2")
				.with("Region", StringUtil.isNotEmpty(region) ? region : connection.attr("Region"));
		
		//String host = "ec2.amazonaws.com";
		//String region = "us-east-1";
		//String endpoint = "https://ec2.amazonaws.com";
		String request_parameters = "Action=DescribeVolumes&Version=2016-11-15";
		
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest.Builder req = buildRequest(connection, options, "GET", request_parameters);
		
		httpClient.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
				.thenAcceptAsync(response -> {
					callback.useContext();		// restore context
					
					System.out.println("code: " + response.statusCode());
					//System.out.println("got: " + response.body());
					
					callback.returnValue(Struct.objectToXml(response.body()));
				});
	}
	
	// https://docs.aws.amazon.com/general/latest/gr/sigv4-signed-request-examples.html
	// https://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html
	
	static public HttpRequest.Builder buildRequest(XElement connection, RecordStruct options, String method, String request) {
		if ((connection == null) || (options == null) || (request == null)) {
			Logger.error("Missing AWS request parameters.");
			return null;
		}
		
		String access_key = connection.getAttribute("KeyId");
		String secret_key = connection.getAttribute("SecretKey");
		String region = options.getFieldAsString("Region", "us-east-1");
		String service = options.getFieldAsString("Service");
		
		if (StringUtil.isEmpty(access_key) || StringUtil.isEmpty(secret_key)) {
			Logger.error("Missing access tokens.");
			return null;
		}
		
		if (StringUtil.isEmpty(region) || StringUtil.isEmpty(service)) {
			Logger.error("Missing AWS post region or service.");
			return null;
		}
		
		// ************* REQUEST VALUES *************
		String host = service + "." + region + ".amazonaws.com";
		//String host = service + ".amazonaws.com";
		String endpoint = "https://" + host + "/";
		
		// POST requests use a content type header. For DynamoDB,
		// the content is JSON.
		//String content_type = "application/x-amz-ejson-1.0";
		
		String request_parameters = request;
		
		// Create a date for headers and the credential string
		ZonedDateTime t = TimeUtil.now();
		String amz_date = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(t);
		String date_stamp = DateTimeFormatter.ofPattern("yyyyMMdd").format(t);  // Date w/o time, used in credential scope
		
		System.out.println("1: " + date_stamp);
		
		// ************* TASK 1: CREATE A CANONICAL REQUEST *************
		// http://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html
		
		// Step 1 is to define the verb (GET, POST, etc.)--already done.
		
		// Step 2: Create canonical URI--the part of the URI from domain to query
		// string (use "/" if no path)
		String canonical_uri = "/";
		
		// Step 3: Create the canonical query string. In this example, request
		// parameters are passed in the body of the request and the query string
		// is blank.
		String canonical_querystring = request_parameters;
		
		System.out.println("2: " + canonical_querystring);
		
		// Step 4: Create the canonical headers. Header names must be trimmed
		// and lowercase, and sorted in code point order from low to high.
		// Note that there is a trailing \n.
		String canonical_headers = "host:" + host + "\n"
				+ "x-amz-date:" + amz_date + "\n";
		
		System.out.println("3: " + canonical_headers);
		
		// Step 5: Create the list of signed headers. This lists the headers
		// in the canonical_headers list, delimited with ";" and in alpha order.
		// Note: The request can include any headers; canonical_headers and
		// signed_headers include those that you want to be included in the
		// hash of the request. "Host" and "x-amz-date" are always required.
		String signed_headers = "host;x-amz-date";
		
		// Step 6: Create payload hash (hash of the request body content). For GET
		// requests, the payload is an empty string (""). hardcoded the empty hash
		String payload = "";
		
		//String payload_hash = HashUtil.getSha256(canonical_querystring);
		
		String payload_hash = StringUtil.isNotEmpty(payload)
			? HashUtil.getSha256(payload) : "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
		
		System.out.println("4: " + payload_hash);
		
		// Step 7: Combine elements to create create canonical request
		String canonical_request = method + "\n"
				+ canonical_uri + "\n"
				+ canonical_querystring + "\n"
				+ canonical_headers + "\n"
				+ signed_headers + "\n"
				+ payload_hash;
		
		System.out.println("5: " + canonical_request);
		
		// ************* TASK 2: CREATE THE STRING TO SIGN *************
		// Match the algorithm to the hashing algorithm you use, either SHA-1 or
		// SHA-256 (recommended)
		String algorithm = "AWS4-HMAC-SHA256";
		String credential_scope = date_stamp + "/" + region + "/" + service + "/" + "aws4_request";
		String string_to_sign = algorithm + "\n"
				+  amz_date + "\n"
				+  credential_scope + "\n"
				+  HashUtil.getSha256(canonical_request);
		
		System.out.println("6: " + credential_scope);
		
		System.out.println("7: " + string_to_sign);
		
		try {
			// ************* TASK 3: CALCULATE THE SIGNATURE *************
			// Create the signing key using the function defined above.
			byte[] signing_key = getSignatureKey(secret_key, date_stamp, region, service);
			
			// Sign the string_to_sign using the signing_key
			String signature = HexUtil.bufferToHex(HmacSHA256(string_to_sign, signing_key));
			
			System.out.println("8: " + signature);
			
			// ************* TASK 4: ADD SIGNING INFORMATION TO THE REQUEST *************
			// Put the signature information in a header named Authorization.
			String authorization_header = algorithm + " " + "Credential=" + access_key + "/" + credential_scope + ", "
					+ "SignedHeaders=" + signed_headers + ", " + "Signature=" + signature;
			
			System.out.println("9: " + authorization_header);
			
			return HttpRequest.newBuilder(URI.create(endpoint + '?' + canonical_querystring))
					// TODO added automatically - .header("Host", host)
					//.header("Content-Type", content_type)
					.header("X-Amz-Date", amz_date)
					.header("Authorization", authorization_header)
					//.header("User-Agent", "dcServer/2019.1 (Language=Java/11)")
					.GET();
		}
		catch (GeneralSecurityException x) {
			Logger.error("AWS signing error: " + x);
		}
		catch (UnsupportedEncodingException x) {
			Logger.error("AWS char encoding error: " + x);
		}
		
		return null;
	}
}
