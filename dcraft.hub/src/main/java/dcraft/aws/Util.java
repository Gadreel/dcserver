package dcraft.aws;

import dcraft.aws.s3.GetResponse;
import dcraft.aws.s3.S3Object;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.RecordStruct;
import dcraft.util.HashUtil;
import dcraft.util.HexUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Util {
	static byte[] HmacSHA256(String data, byte[] key) throws GeneralSecurityException {
		String algorithm="HmacSHA256";
		Mac mac = Mac.getInstance(algorithm);
		mac.init(new SecretKeySpec(key, algorithm));
		return mac.doFinal(Utf8Encoder.encode(data));
	}
	
	static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws GeneralSecurityException {
		byte[] kSecret = Utf8Encoder.encode("AWS4" + key);
		byte[] kDate = HmacSHA256(dateStamp, kSecret);
		byte[] kRegion = HmacSHA256(regionName, kDate);
		byte[] kService = HmacSHA256(serviceName, kRegion);
		byte[] kSigning = HmacSHA256("aws4_request", kService);
		return kSigning;
	}
	
	static public RecordStruct callDynamoDb(XElement connection, String op, RecordStruct request) {
		RecordStruct options = RecordStruct.record()
				.with("Service", "dynamodb")
				// DynamoDB requires an x-amz-target header that has this format:
				// DynamoDB_<API version>.<operationName>
				.with("Target","DynamoDB_20120810." + op);
		
		return Util.callSimplePost(connection, options, request);
	}
	
	static public RecordStruct callSimplePost(XElement connection, RecordStruct options, RecordStruct request) {
		if ((connection == null) || (options == null) || (request == null)) {
			Logger.error("Missing AWS post parameters.");
			return null;
		}
		
		String access_key = connection.getAttribute("KeyId");
		String secret_key = connection.getAttribute("SecretKey");
		String region = connection.getAttribute("Region");
		String service = options.getFieldAsString("Service");
		String amz_target = options.getFieldAsString("Target");
		
		if (StringUtil.isEmpty(access_key) || StringUtil.isEmpty(secret_key)) {
			Logger.error("Missing access tokens.");
			return null;
		}
		
		if (StringUtil.isEmpty(region) || StringUtil.isEmpty(service) || StringUtil.isEmpty(amz_target)) {
			Logger.error("Missing AWS post region, service or target.");
			return null;
		}
		
		// ************* REQUEST VALUES *************
		String method = "POST";
		String host = service + "." + region + ".amazonaws.com";
		String endpoint = "https://" + host + "/";
		
		// POST requests use a content type header. For DynamoDB,
		// the content is JSON.
		String content_type = "application/x-amz-json-1.0";

		String request_parameters = request.toString();
		
		// Create a date for headers and the credential string
		ZonedDateTime t = TimeUtil.now();
		String amz_date = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(t);
		String date_stamp = DateTimeFormatter.ofPattern("yyyyMMdd").format(t);  // Date w/o time, used in credential scope
		
		// ************* TASK 1: CREATE A CANONICAL REQUEST *************
		// http://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html
		
		// Step 1 is to define the verb (GET, POST, etc.)--already done.
		
		// Step 2: Create canonical URI--the part of the URI from domain to query
		// string (use "/" if no path)
		String canonical_uri = "/";
		
		// Step 3: Create the canonical query string. In this example, request
		// parameters are passed in the body of the request and the query string
		// is blank.
		String canonical_querystring = "";
		
		// Step 4: Create the canonical headers. Header names must be trimmed
		// and lowercase, and sorted in code point order from low to high.
		// Note that there is a trailing \n.
		String canonical_headers = "content-type:" + content_type + "\n"
				+ "host:" + host + "\n"
				+ "x-amz-date:" + amz_date + "\n"
				+ "x-amz-target:" + amz_target + "\n";
		
		// Step 5: Create the list of signed headers. This lists the headers
		// in the canonical_headers list, delimited with ";" and in alpha order.
		// Note: The request can include any headers; canonical_headers and
		// signed_headers include those that you want to be included in the
		// hash of the request. "Host" and "x-amz-date" are always required.
		// For DynamoDB, content-type and x-amz-target are also required.
		String signed_headers = "content-type;host;x-amz-date;x-amz-target";
		
		// Step 6: Create payload hash. In this example, the payload (body of
		// the request) contains the request parameters.
		String payload_hash = HashUtil.getSha256(request_parameters);
		
		// Step 7: Combine elements to create create canonical request
		String canonical_request = method + "\n"
				+ canonical_uri + "\n"
				+ canonical_querystring + "\n"
				+ canonical_headers + "\n"
				+ signed_headers + "\n"
				+ payload_hash;

		// ************* TASK 2: CREATE THE STRING TO SIGN*************
		// Match the algorithm to the hashing algorithm you use, either SHA-1 or
		// SHA-256 (recommended)
		String algorithm = "AWS4-HMAC-SHA256";
		String credential_scope = date_stamp + "/" + region + "/" + service + "/" + "aws4_request";
		String string_to_sign = algorithm + "\n"
				+  amz_date + "\n"
				+  credential_scope + "\n"
				+  HashUtil.getSha256(canonical_request);
		
		// For DynamoDB, the request can include any headers, but MUST include "host", "x-amz-date",
		// "x-amz-target", "content-type", and "Authorization". Except for the authorization
		// header, the headers must be included in the canonical_headers and signed_headers values, as
		// noted earlier. Order here is not significant.
		// The "host" header is added automatically by the "requests" library.
		
		try {
			URL url = new URL(endpoint);
			
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			conn.setRequestMethod(method);
			
			conn.addRequestProperty("Host", host);
			conn.addRequestProperty("Content-Type", content_type);
			conn.addRequestProperty("X-Amz-Date", amz_date);
			conn.addRequestProperty("X-Amz-Target", amz_target);
			
			// ************* TASK 3: CALCULATE THE SIGNATURE *************
			// Create the signing key using the function defined above.
			try {
				byte[] signing_key = getSignatureKey(secret_key, date_stamp, region, service);
				
				// Sign the string_to_sign using the signing_key
				String signature = HexUtil.bufferToHex(HmacSHA256(string_to_sign, signing_key));
				
				// ************* TASK 4: ADD SIGNING INFORMATION TO THE REQUEST *************
				// Put the signature information in a header named Authorization.
				String authorization_header = algorithm + " " + "Credential=" + access_key + "/" + credential_scope + ", "
						+ "SignedHeaders=" + signed_headers + ", " + "Signature=" + signature;

				conn.addRequestProperty("Authorization", authorization_header);
			}
			catch (GeneralSecurityException x) {
				Logger.error("AWS signing error: " + x);
				return null;
			}
				
				/*
				System.out.println("Props:");
				Map<String,List<String>> properties = connection.getRequestProperties();
				
				for (String key : properties.keySet()) {
					for (String val : properties.get(key)) {
						System.out.println(key + ": " + val);
					}
				}
				
				System.out.println();
				*/
			
			conn.setDoOutput(true);
			conn.getOutputStream().write(Utf8Encoder.encode(request_parameters));
			
			int code = conn.getResponseCode();

			if (Logger.isDebug())
				Logger.debug("AWS response code: " + code);
			
				System.out.println("Code: " + conn.getResponseMessage());
				
				Map<String,List<String>> headers = conn.getHeaderFields();
				
				for (String key : headers.keySet()) {
					for (String val : headers.get(key)) {
						System.out.println(key + ": " + val);
					}
				}
			
			return (RecordStruct) CompositeParser.parseJson(conn.getInputStream());
		}
		catch (IOException x) {
			Logger.error("AWS POST error: " + x);
		}
		
		return null;
	}
}
