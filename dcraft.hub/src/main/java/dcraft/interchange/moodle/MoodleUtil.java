package dcraft.interchange.moodle;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeList;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.Base64;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.net.URL;
import java.net.URLEncoder;

public class MoodleUtil {
	static public void findUserByEmail(String alt, String email, OperationOutcomeRecord callback) {
		try {
			String body = "criteria[0][key]=" + URLEncoder.encode("email", "UTF-8")
					+ "&criteria[0][value]=" + URLEncoder.encode(email, "UTF-8");

			/* example
{
  "users": [
    {
      "id": 3,
      "username": "andy",
      "firstname": "Andy",
      "lastname": "White",
      "fullname": "Andy White",
      "email": "andy@designcraftadvertising.com",
      "department": "",
      "firstaccess": 1507825546,
      "lastaccess": 1536785329,
      "auth": "manual",
      "suspended": false,
      "confirmed": true,
      "lang": "en",
      "theme": "",
      "timezone": "99",
      "mailformat": 1,
      "description": "",
      "descriptionformat": 1,
      "city": "Madison",
      "country": "US",
      "profileimageurlsmall": "https://secure.gravatar.com/avatar/b49565d9ab7265e02af1696710fbc5ae?s=35&d=mm",
      "profileimageurl": "https://secure.gravatar.com/avatar/b49565d9ab7265e02af1696710fbc5ae?s=100&d=mm"
    }
  ],
  "warnings": []
}
			*/

			// parse and close response stream
			CompositeStruct resp = MoodleUtil.execute(alt, "core_user_get_users", body);

			if (resp == null) {
				Logger.error("Error processing text: Moodle sent an incomplete response.");
				callback.returnEmpty();
				return;
			}

			//System.out.println("Moodle Resp:\n" + resp.toPrettyString());

			callback.returnValue(resp.selectAsRecord("users.0"));

			return;
		}
		catch (Exception x) {
			Logger.error("Error calling service, Moodle error: " + x);
		}

		callback.returnEmpty();
	}

	static public void findUserByUsername(String alt, String uname, OperationOutcomeRecord callback) {
		try {
			String body = "criteria[0][key]=" + URLEncoder.encode("username", "UTF-8")
					+ "&criteria[0][value]=" + URLEncoder.encode(uname, "UTF-8");

			// parse and close response stream
			CompositeStruct resp = MoodleUtil.execute(alt, "core_user_get_users", body);

			if (resp == null) {
				Logger.error("Error processing text: Moodle sent an incomplete response.");
				callback.returnEmpty();
				return;
			}

			//System.out.println("Moodle Resp:\n" + resp.toPrettyString());

			callback.returnValue(resp.selectAsRecord("users.0"));

			return;
		}
		catch (Exception x) {
			Logger.error("Error calling service, Moodle error: " + x);
		}

		callback.returnEmpty();
	}

	static public void findUsersByLastName(String alt, String lastname, OperationOutcomeList callback) {
		try {
			String body = "criteria[0][key]=" + URLEncoder.encode("lastname", "UTF-8")
					+ "&criteria[0][value]=" + URLEncoder.encode(lastname, "UTF-8");

			// parse and close response stream
			CompositeStruct resp = MoodleUtil.execute(alt, "core_user_get_users", body);

			if (resp == null) {
				Logger.error("Error processing text: Moodle sent an incomplete response.");
				callback.returnEmpty();
				return;
			}

			//System.out.println("Moodle Resp:\n" + resp.toPrettyString());

			callback.returnValue(resp.selectAsList("users"));

			return;
		}
		catch (Exception x) {
			Logger.error("Error calling service, Moodle error: " + x);
		}

		callback.returnEmpty();
	}

	/*
		user fields: username, password, firstname, lastname, email, city and (optional) idnumber

		return fields: id, username
	 */

	static public void createUser(String alt, RecordStruct user, OperationOutcomeRecord callback) {
		try {
				/*
			users[0][username]= string
			users[0][password]= string
			users[0][firstname]= string
			users[0][lastname]= string
			users[0][email]= string
			users[0][idnumber]= string
			users[0][city]= string

				core_user_create_users
				 */

			StringBuilder body = new StringBuilder();

			for (FieldStruct fld : user.getFields()) {
				if (body.length() > 0)
					body.append('&');

				body.append("users[0][" + URLEncoder.encode(fld.getName(), "UTF-8")
						+ "]=" + URLEncoder.encode(fld.getValue().toString(), "UTF-8"));
			}

			// parse and close response stream
			CompositeStruct resp = MoodleUtil.execute(alt, "core_user_create_users", body.toString());

			if (resp == null) {
				Logger.error("Error processing text: Moodle sent an incomplete response.");
				callback.returnEmpty();
				return;
			}

			//System.out.println("Moodle Resp:\n" + resp.toPrettyString());

			callback.returnValue(resp.selectAsRecord("0"));

			return;
		}
		catch (Exception x) {
			Logger.error("Error calling service, Moodle error: " + x);
		}

		callback.returnEmpty();
	}

	static public void assignRole(String alt, String user, String role, OperationOutcomeEmpty callback) {
		try {
			String body = "assignments[0][roleid]=" + role + "&assignments[0][userid]=" + user
					+ "&assignments[0][contextlevel]=course";  //system&assignments[0][contextid]=1";

			/*
			assignments[0][roleid]= int
			assignments[0][userid]= int

			core_role_assign_roles
			 */

			// parse and close response stream
			MoodleUtil.execute(alt, "core_role_assign_roles", body);

			callback.returnEmpty();

			return;
		}
		catch (Exception x) {
			Logger.error("Error calling service, Moodle error: " + x);
		}

		callback.returnEmpty();
	}

	static private CompositeStruct execute(String alt, String function, String body) {
		XElement twilio = ApplicationHub.getCatalogSettings("Moodle-Services", alt);

		if (twilio == null) {
			Logger.error("Missing Moodle settings.");
			return null;
		}

		String domain = twilio.getAttribute("Domain");

		if (StringUtil.isEmpty(domain)) {
			Logger.error("Missing Moodle domain.");
			return null;
		}

		String token = twilio.getAttribute("Token");

		if (StringUtil.isEmpty(token)) {
			Logger.error("Missing Moodle token.");
			return null;
		}

		try {
			String endpoint = "https://" + domain + "/webservice/rest/server.php?wstoken=" + token
					+ "&wsfunction=" + function + "&moodlewsrestformat=json";

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
				return CompositeParser.parseJson(con.getInputStream());
			}
			else {
				Logger.error("Error processing text: Problem with Moodle gateway.");
			}

		}
		catch (Exception x) {
			Logger.error("Error calling service, Moodle error: " + x);
		}

		return null;
	}
}
