package dcraft.interchange.google;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.ups.UpsUtil;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLEncoder;

public class RecaptchaUtil {
	static final public String PROD_ENDPOINT = "https://www.google.com/recaptcha/api/siteverify";

	static public void siteVerify(String response, String alt, OperationOutcomeRecord callback) {
		XElement gsettings = ApplicationHub.getCatalogSettings("Google", alt);

		if (gsettings == null) {
			Logger.error("Missing Google settings.");
			callback.returnEmpty();
			return;
		}

		XElement rsettings = gsettings.find("reCAPTCHA");

		if (rsettings == null) {
			Logger.error("Missing Google reCAPTCHA settings.");
			callback.returnEmpty();
			return;
		}

		String secretKey = rsettings.getAttribute("SecretKey");

		if (StringUtil.isEmpty(secretKey)) {
			Logger.error("Missing Google reCAPTCHA key.");
			callback.returnEmpty();
			return;
		}

		try {
			String endpoint = RecaptchaUtil.PROD_ENDPOINT;

			String body = "secret=" + URLEncoder.encode(secretKey, "UTF-8")
					+ "&response=" + URLEncoder.encode(response, "UTF-8");

			if (ApplicationHub.isProduction()) {
				//	add	remoteip

				String remote = OperationContext.getOrThrow().getOrigin();

				if (remote.startsWith("http:")) {
					body += "&remoteip=" + remote.substring(5);
				}
			}

			OperationContext.getOrThrow().touch();

			URL url = new URL(endpoint);

			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "dcServer/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

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
					Logger.error("Error processing reCAPTCHA: Google sent an incomplete response.");
					callback.returnEmpty();
					return;
				}

				RecordStruct recresp = (RecordStruct) resp;

				//System.out.println("reCAPTCHA Resp:\n" + resp.toPrettyString());

				if (! recresp.getFieldAsBooleanOrFalse("success")) {
					Logger.error("reCAPTCHA failed: " + recresp.toString());
				}

				callback.returnValue(recresp);

				return;
			}
			else {
				Logger.error("Error processing reCAPTCHA: Problem with verify gateway.");
			}
		}
		catch (Exception x) {
			Logger.error("Error processing reCAPTCHA: Unable to connect to verify gateway.");
		}

		callback.returnEmpty();
	}
}
