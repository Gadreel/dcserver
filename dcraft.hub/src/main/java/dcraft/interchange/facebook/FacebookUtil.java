package dcraft.interchange.facebook;

import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.RecordStruct;

import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class FacebookUtil {
	static public RecordStruct fbSignIn(String token) { //, String secret) {
		try {
			URL url = null;

			//if (StringUtil.isEmpty(secret)) {
				url = new URL("https://graph.facebook.com/v3.1/me?fields=email,first_name,last_name&access_token=" + URLEncoder.encode(token, "UTF-8"));
				/*
			}
			else {
				Mac mac = Mac.getInstance("HmacSHA256");

				mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));

				String verify = HexUtil.bufferToHex(mac.doFinal(token.getBytes()));

				//System.out.println("verify: " + verify);

				url = new URL("https://graph.facebook.com/v2.2/me?access_token=" + URLEncoder.encode(token, "UTF-8")
						+ "&appsecret_proof=" + URLEncoder.encode(verify, "UTF-8"));

				//System.out.println("url: " + url);
			}
			*/

			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

			con.setRequestProperty("User-Agent", "DivConq/1.0 (Language=Java/8)");

			int responseCode = con.getResponseCode();

			/*
					{
					  "id": "2190568591187598",
					  "email": "andy@andywhitewebworks.com",
					  "first_name": "Andy",
					  "last_name": "White"
					}
			 */

			if (responseCode == 200)
				return (RecordStruct) CompositeParser.parseJson(con.getInputStream());

			Logger.error("FB Response Code : " + responseCode);
		}
		catch (Exception x) {
			Logger.error("FB error: " + x);
		}

		return null;
	}
}
