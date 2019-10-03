/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.api;

import dcraft.filevault.VaultUtil;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.interchange.stripe.StripeUtil;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.session.ISessionAdapter;
import dcraft.session.Session;
import dcraft.session.SessionHub;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.MemorySourceStream;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.tenant.Site;
import dcraft.tenant.TenantHub;
import dcraft.util.Base64;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class HttpSession extends ApiSession {
	static public HttpSession http(String domain, String tenant, String site) {
		HttpSession api = new HttpSession();
		api.init(domain, tenant, site);
		return api;
	}
	
	//protected Session session = null;
	protected String tenant = null;
	protected String site = null;
	protected String secret = null;
	protected String token = null;
	protected String lang = "eng";
	protected int port = 443;

	/* TODO
	public void init(XElement config) {
		Session sess = Session.of(config.getAttribute("Tenant"),
				config.getAttribute("Site", "root"), "hub:");

		this.init(sess);
	}
	*/
	
	public void init(String domain, String tenant, String site) {
		this.domain = domain;
		this.tenant = tenant;
		this.site = site;

		// TODO
		//this.session
		//	.withAdatper(new HttpSessionAdatper());
	}

	// TODO
	public class HttpSessionAdatper implements ISessionAdapter {
		@Override
		public void kill() {
		}

		@Override
		public void userChanged(UserContext user) {
			// don't care
		}

		@Override
		public boolean isAlive() {
			return true;
		}
	}
	
	@Override
	public void stopped() {
		// TODO SessionHub.terminate(this.session.getId());
	}
	
	@Override
	public void call(ServiceRequest request) throws OperatingContextException {
		RecordStruct msg = RecordStruct.record()
				.with("Service", request.getName())
				.with("Feature", request.getFeature())
				.with("Op", request.getOp())
				.with("Body", request.getData());

		String json = msg.toString();

		HttpRequest req = this.buildBasicRequest("application/json; charset=utf-8",
				HttpRequest.BodyPublishers.ofString(json)).build();

		OperationOutcomeStruct callback = request.requireOutcome();

		// Send post request
		HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofString())
				.thenAcceptAsync(response -> {
					callback.useContext();		// restore our operation context

					try {
						List<String> cookies = response.headers().allValues("set-cookie");

						for (String cookie : cookies) {
							if (cookie.startsWith("dcSessionId=")) {
								String id = cookie.substring(12, cookie.indexOf(';'));

								int u1 = id.indexOf('_');
								int u2 = id.indexOf('_', u1 + 1);

								this.nodeid = id.substring(0, u1);
								this.sessionid = id.substring(u1 + 1, u2);
								this.secret = id.substring(u2 + 1);
							}
						}

						int responseCode = response.statusCode();
						String respBody = response.body();

						if (StringUtil.isNotEmpty(respBody)) {
							RecordStruct resp = Struct.objectToRecord(respBody);

							if (resp == null) {
								Logger.error("Error calling service: Incomplete or bad response..");
								callback.returnEmpty();
							} else {
								OperationController oconn = callback.getOperationContext().getController();

								ListStruct messages = resp.getFieldAsList("Messages");

								for (int i = 0; i < messages.size(); i++) {
									RecordStruct logmsg = messages.getItemAsRecord(i);

									oconn.log(callback.getOperationContext(), logmsg);
								}

								callback.returnValue(resp.getField("Body"));
							}
						} else {
							Logger.error("Error calling service: No response.");
							Logger.error("Service Response: " + responseCode);
							callback.returnEmpty();
						}
					}
					catch (OperatingContextException x) {
						Logger.error("Missing operation context");
						callback.returnEmpty();
					}
				});
	}
	
	@Override
	public void transfer(String channel, StreamFragment source, StreamFragment dest, OperationOutcomeEmpty callback) {
		if (dest != null) {
			HttpRequest req = this.buildBasicRequest(null, null).build();

			// Send post request
			HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
					.thenAcceptAsync(response -> {
						callback.useContext();		// restore our operation context

						try {
							List<String> cookies = response.headers().allValues("set-cookie");

							for (String cookie : cookies) {
								if (cookie.startsWith("dcSessionId=")) {
									String id = cookie.substring(12, cookie.indexOf(';'));

									int u1 = id.indexOf('_');
									int u2 = id.indexOf('_', u1 + 1);

									this.nodeid = id.substring(0, u1);
									this.sessionid = id.substring(u1 + 1, u2);
									this.secret = id.substring(u2 + 1);
								}
							}

							int responseCode = response.statusCode();
							byte[] respBody = response.body();

							// TODO make this into a real async - one buffer at a time - download
							if (respBody != null) {
								Task task = Task.ofSubtask("Save single file", "Log")
										.withWork(StreamWork.of(
												StreamFragment.of(MemorySourceStream.fromBinary(respBody)),
												dest
										));

								TaskHub.submit(task, new TaskObserver() {
									@Override
									public void callback(TaskContext subtask) {
										if (subtask.hasExitErrors())
											System.out.println("Failed to store the file.");
										else
											System.out.println("Stored the file.");

										callback.returnEmpty();
									}
								});
							}
							else {
								Logger.error("Error calling transfer: No response.");
								Logger.error("Server Response: " + responseCode);
								callback.returnEmpty();
							}
						}
						catch (OperatingContextException x) {
							Logger.error("Missing operation context");
							callback.returnEmpty();
						}
					});
		}

	}

	public HttpRequest.Builder buildBasicRequest(String contenttype, HttpRequest.BodyPublisher publisher) {
		return this.buildBasicRequest("/dcdyn/rpc", contenttype, publisher);
	}

	public HttpRequest.Builder buildBasicRequest(String path, String contenttype, HttpRequest.BodyPublisher publisher) {
		String endpoint = "https://" + this.domain + ":" + this.port + path;

		String cookie = "dcLang=" + this.lang + "; ";

		if (StringUtil.isNotEmpty(this.secret))
			cookie += "dcSessionId=" + this.nodeid + "_" + this.sessionid + "_" + this.secret + "; ";

		if (StringUtil.isNotEmpty(this.token))
			cookie += "dcAuthToken=" + this.token + "; ";

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.header("Cookie", cookie)
				.header("User-Agent", "dcServer/2019.1 (Language=Java/11)");

		if (StringUtil.isEmpty(contenttype))
			return builder.GET();

		return builder
				.header("Content-Type", contenttype)
				.PUT(publisher);
	}

}
