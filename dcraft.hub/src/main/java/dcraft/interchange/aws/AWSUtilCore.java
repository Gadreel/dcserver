package dcraft.interchange.aws;

import dcraft.aws.Util;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.*;
import dcraft.util.HashUtil;
import dcraft.util.HexUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;
import org.apache.commons.codec.net.URLCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AWSUtilCore {
	// great doc - https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-query-string-auth.html
	static public byte[] HmacSHA256(String data, byte[] key) throws GeneralSecurityException, UnsupportedEncodingException {
		String algorithm="HmacSHA256";
		Mac mac = Mac.getInstance(algorithm);
		mac.init(new SecretKeySpec(key, algorithm));
		return mac.doFinal(data.getBytes("UTF-8"));
	}
	
	static public byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws GeneralSecurityException, UnsupportedEncodingException {
		byte[] kSecret = ("AWS4" + key).getBytes("UTF-8");
		byte[] kDate = HmacSHA256(dateStamp, kSecret);
		byte[] kRegion = HmacSHA256(regionName, kDate);
		byte[] kService = HmacSHA256(serviceName, kRegion);
		byte[] kSigning = HmacSHA256("aws4_request", kService);
		return kSigning;
	}

	/*
		Dual-stack endpoints support both IPv4 and IPv6 traffic. Dual-stack endpoints are available for in the following Regions only:

		us-east-1—US East (Northern Virginia)
		us-east-2—US East (Ohio)
		us-west-2—US West (Oregon)
		eu-west-1—Europe (Ireland)
		ap-south-1—Asia Pacific (Mumbai)
		sa-east-1—South America (São Paulo)

		When you make a request to a dual-stack endpoint, the endpoint URL resolves to an IPv6 or an IPv4 address, depending on the protocol used by your network and client.

		Amazon EC2 supports only regional dual-stack endpoints, which means that you must specify the Region as part of the endpoint name. Dual-stack endpoint names use the following naming convention:

		api.service.region.aws
	 */
	static public boolean isHostDualStack(String region) {
		return  ("us-east-1".equals(region) || "us-east-2".equals(region) || "us-west-2".equals(region) ||
				"eu-west-1".equals(region) || "ap-south-1".equals(region) || "sa-east-1".equals(region));
	}

	// https://docs.aws.amazon.com/general/latest/gr/sigv4-signed-request-examples.html
	// https://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html

	static public String presignRequest(XElement connection, RecordStruct options) {
		String access_key = connection.getAttribute("KeyId");

		String amz_date = options.getFieldAsString("Stamp");

		if (StringUtil.isEmpty(amz_date)) {
			amz_date = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(TimeUtil.now());
			options.with("Stamp", amz_date);
		}

		String date_stamp = amz_date.substring(0, 8);   // Date w/o time, used in credential scope

		String region = options.getFieldAsString("Region", "us-east-1");
		String service = options.getFieldAsString("Service");

		RecordStruct params = options.getFieldAsRecord("Params");

		if (params == null) {
			params = RecordStruct.record();
			options.with("Params", params);
		}

		params.with("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
		params.with("X-Amz-Date", amz_date);
		params.with("Content-Type", options.getFieldAsString("ContentType"));
		params.with("X-Amz-SignedHeaders", "host;x-amz-date");
		params.with("X-Amz-Expires", options.getFieldAsString("Expires", "2000"));		// 33 minutes

		String signingCredentials = access_key + "/" + date_stamp + "/" + region + "/" + service + "/" + "aws4_request";

		params.with("X-Amz-Credential", signingCredentials);

		options.with("PayloadHash", "UNSIGNED-PAYLOAD");

		// fill in the Options with Signature
		AWSUtilCore.buildRequest(connection, options);

		params.with("X-Amz-Signature", options.getFieldAsString("RequestSignature"));

		// url with updated with signature param added

		return AWSUtilCore.generateUrl(options);
	}

	/*
		options = {
			// in

			Service: 'ec2|sqs|s3|sms|etc"
			Region: 'x'
			Host: host name
			Params: query string params - as a record that may contrain a list
			Path: if any
			PayloadHash: if posting / putting
			Method: if other than GET
			Stamp:	'yyyyMMdd'T'HHmmss'Z''    - only in special cases

			// out

			RequestSignature: hex of the signature
			RequestParams: parameter string
			RequestPath: url up to the params
		}
	 */

	static public HttpRequest.Builder buildRequest(XElement connection, RecordStruct options) {
		if ((connection == null) || (options == null)) {
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
		String host = options.getFieldAsString("Host") ;

		// POST requests use a content type header. For DynamoDB,
		// the content is JSON.
		//String content_type = "application/x-amz-ejson-1.0";
		
		String request_parameters = AWSUtilCore.generateRequestParams(options);

		// a date for headers and the credential string
		String amz_date = options.getFieldAsString("Stamp");		// this would be for testing or presign algorthium

		if (StringUtil.isEmpty(amz_date)) {
			amz_date = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(TimeUtil.now());
		}
		
		String date_stamp = amz_date.substring(0, 8);   // Date w/o time, used in credential scope
		
		//System.out.println("1: " + date_stamp);
		
		// ************* TASK 1: CREATE A CANONICAL REQUEST *************
		// http://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html
		
		// Step 1 is to define the verb (GET, POST, etc.)--already done.
		
		// Step 2: Create canonical URI--the part of the URI from domain to query
		// string (use "/" if no path)
		String canonical_uri = options.getFieldAsString("Path", "/");

		// Step 3: Create the canonical query string. In this example, request
		// parameters are passed in the body of the request and the query string
		// is blank.
		String canonical_querystring = request_parameters;
		
		//System.out.println("2: " + canonical_querystring);

		// Step 4: Create the canonical headers. Header names must be trimmed
		// and lowercase, and sorted in code point order from low to high.
		// Note that there is a trailing \n.
		String canonical_headers = "host:" + host + "\n"
				+ "x-amz-date:" + amz_date + "\n";
		
		//System.out.println("3: " + canonical_headers);
		
		// Step 5: Create the list of signed headers. This lists the headers
		// in the canonical_headers list, delimited with ";" and in alpha order.
		// Note: The request can include any headers; canonical_headers and
		// signed_headers include those that you want to be included in the
		// hash of the request. "Host" and "x-amz-date" are always required.
		String signed_headers = "host;x-amz-date";
		
		// Step 6: Create payload hash (hash of the request body content). For GET
		// requests, the payload is an empty string (""). hardcoded the empty hash
		//String payload = "";
		
		//String payload_hash = HashUtil.getSha256(canonical_querystring);

		//String payload_hash = StringUtil.isNotEmpty(payload)
		//		? HashUtil.getSha256(payload) : "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

		String payload_hash = options.getFieldAsString("PayloadHash", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

		//System.out.println("4: " + payload_hash);
		
		// Step 7: Combine elements to create create canonical request
		String canonical_request = options.getFieldAsString("Method", "GET") + "\n"
				+ canonical_uri + "\n"
				+ canonical_querystring + "\n"
				+ canonical_headers + "\n"
				+ signed_headers + "\n"
				+ payload_hash;
		
		//System.out.println("5: " + canonical_request);
		//System.out.println("5: " + HexUtil.encodeHex(canonical_request));

		// ************* TASK 2: CREATE THE STRING TO SIGN *************
		// Match the algorithm to the hashing algorithm you use, either SHA-1 or
		// SHA-256 (recommended)
		String algorithm = "AWS4-HMAC-SHA256";
		String credential_scope = date_stamp + "/" + region + "/" + service + "/" + "aws4_request";
		String string_to_sign = algorithm + "\n"
				+  amz_date + "\n"
				+  credential_scope + "\n"
				+  HashUtil.getSha256(canonical_request);

		//System.out.println("6: " + credential_scope);
		
		//System.out.println("7: " + string_to_sign);
		//System.out.println("7: " + HexUtil.encodeHex(string_to_sign));

		try {
			// ************* TASK 3: CALCULATE THE SIGNATURE *************
			// Create the signing key using the function defined above.
			byte[] signing_key = getSignatureKey(secret_key, date_stamp, region, service);
			
			// Sign the string_to_sign using the signing_key
			String signature = HexUtil.bufferToHex(HmacSHA256(string_to_sign, signing_key));
			
			//System.out.println("8: " + signature);

			options.with("RequestSignature", signature);
			
			// ************* TASK 4: ADD SIGNING INFORMATION TO THE REQUEST *************
			// Put the signature information in a header named Authorization.
			String authorization_header = algorithm + " " + "Credential=" + access_key + "/" + credential_scope + ","
					+ "SignedHeaders=" + signed_headers + "," + "Signature=" + signature;
			
			//System.out.println("9: " + authorization_header);

			String endpoint = AWSUtilCore.generateEndpoint(options);

			return HttpRequest.newBuilder(URI.create(endpoint))
					//.header("Content-Type", content_type)
					.header("x-amz-date", amz_date)
					.header("x-amz-content-sha256", payload_hash)
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

	static public String generateUrl(RecordStruct options) {
		AWSUtilCore.generateRequestParams(options);		// for the params - automatically passed on
		return AWSUtilCore.generateEndpoint(options);
	}

	static public String generateEndpoint(RecordStruct options) {
		String host = options.getFieldAsString("Host") ;
		String canonical_uri = options.getFieldAsString("Path", "/");

		String endpoint = "https://" + host + canonical_uri;

		options.with("RequestPath", endpoint);

		String canonical_querystring = options.getFieldAsString("RequestParams");

		if (StringUtil.isNotEmpty(canonical_querystring))
			endpoint += "?" + canonical_querystring;

		return endpoint;
	}

	static public String generateRequestParams(RecordStruct options) {
		String host = options.getFieldAsString("Host") ;

		// POST requests use a content type header. For DynamoDB,
		// the content is JSON.
		//String content_type = "application/x-amz-ejson-1.0";

		String request_parameters = "";

		BaseStruct paraminfo = options.getField("Params");

		if (paraminfo != null) {
			if (paraminfo instanceof RecordStruct) {
				RecordStruct paramrec = Struct.objectToRecord(paraminfo);

				TreeMap<String,String> expandedparams = new TreeMap<>();

				AWSUtilCore.expandRequestParams(paramrec, "", expandedparams);

				for (String name : expandedparams.keySet()) {
					if (request_parameters.length() > 0)
						request_parameters += "&";

					// TODO get a better URI encoder so we don't need to do a replace, the java lib is for forms not url parameters
					request_parameters += name + "=" + URLEncoder.encode(expandedparams.get(name), StandardCharsets.UTF_8).replace("+", "%20");
				}
			}
			else {
				request_parameters = paraminfo.toString();
			}
		}

		options.with("RequestParams", request_parameters);

		return request_parameters;
	}

	static public void expandRequestParams(RecordStruct paramrec, String prefix, TreeMap<String,String> dest) {
		for (FieldStruct fld : paramrec.getFields()) {
			if (fld.isEmpty()) {
				dest.put(prefix + fld.getName(), "");
			}
			else {
				BaseStruct value = fld.getValue();

				if (value instanceof ScalarStruct) {
					dest.put(prefix + fld.getName(), value.toString());
				}
				else if (value instanceof RecordStruct) {
					AWSUtilCore.expandRequestParams((RecordStruct) value, prefix + fld.getName() + ".", dest);
				}
				else if (value instanceof ListStruct) {
					AWSUtilCore.expandRequestParams((ListStruct) value, prefix + fld.getName() + ".", dest);
				}
			}
		}
	}

	static public void expandRequestParams(ListStruct paramlist, String prefix, TreeMap<String,String> dest) {
		for (int i = 0; i < paramlist.size(); i++) {
			int num = i + 1;
			BaseStruct value = paramlist.getItem(i);

			if ((value == null) || value.isEmpty()) {
				dest.put(prefix + num, "");
			}
			else if (value instanceof ScalarStruct) {
				dest.put(prefix + num, value.toString());
			}
			else if (value instanceof RecordStruct) {
				AWSUtilCore.expandRequestParams((RecordStruct) value, prefix + num + ".", dest);
			}
			else if (value instanceof ListStruct) {
				AWSUtilCore.expandRequestParams((ListStruct) value, prefix + num + ".", dest);
			}
		}
	}

	/*
		public final class SignerConstants {

    public static final String LINE_SEPARATOR = "\n";

    public static final String AWS4_TERMINATOR = "aws4_request";

    public static final String AWS4_SIGNING_ALGORITHM = "AWS4-HMAC-SHA256";

    // Seconds in a week, which is the max expiration time Sig-v4 accepts
	public static final long PRESIGN_URL_MAX_EXPIRATION_SECONDS = 60 * 60 * 24 * 7;

	public static final String X_AMZ_SECURITY_TOKEN = "X-Amz-Security-Token";

	public static final String X_AMZ_CREDENTIAL = "X-Amz-Credential";

	public static final String X_AMZ_DATE = "X-Amz-Date";

	public static final String X_AMZ_EXPIRES = "X-Amz-Expires";

	public static final String X_AMZ_SIGNED_HEADER = "X-Amz-SignedHeaders";

	public static final String X_AMZ_CONTENT_SHA256 = "x-amz-content-sha256";

	public static final String X_AMZ_SIGNATURE = "X-Amz-Signature";

	public static final String X_AMZ_ALGORITHM = "X-Amz-Algorithm";

	public static final String AUTHORIZATION = "Authorization";

	public static final String HOST = "Host";
}



	 */

	static public boolean checkResponse(Throwable x, int statusCode, HttpHeaders headers) {
		// if there was an exception
		if (x != null) {
			Logger.error("Bad Response code: " + statusCode);     // must be an error so callback gets an error
			Logger.error("Bad Response exception: " + x);     // must be an error so callback gets an error
		}
		else if (headers != null) {
			if (statusCode >= 400) {
				Logger.error("Bad Response code: " + statusCode);     // must be an error so callback gets an error
			}
			else {
				Logger.info("Response code: " + statusCode);
				return true;
			}
		}
		else {
			Logger.error("No response or exception");
		}

		return false;
	}
}
