package dcraft.web;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.session.Session;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;

import javax.management.monitor.CounterMonitor;
import java.util.HashMap;
import java.util.Objects;

public class RequestWork extends ChainWork {
	protected String oldAuthToken = null;
	protected boolean needsverify = false;
	protected boolean firstrun = true;
	
	public String getOldAuthToken() {
		return this.oldAuthToken;
	}
	
	public void setOldAuthToken(String v) {
		this.oldAuthToken = v;
	}
	
	
	public boolean getNeedsVerify() {
		return this.needsverify;
	}
	
	public void setNeedsVerify(boolean v) {
		this.needsverify = v;
	}
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		WebController wctrl = (WebController) taskctx.getController();
		RecordStruct req = wctrl.getFieldAsRecord("Request");
		CommonPath reqpath = CommonPath.from(req.getFieldAsString("Path"));
		String host = req.getFieldAsString("Host");

		if (Logger.isDebug())
			Logger.debug("Web server request h");

		if (this.needsverify) {
			this.needsverify = false;
			
			CountHub.countObjects("dcWebVerifyCount-" + OperationContext.getOrThrow().getTenant().getAlias(), this);
			
			ServiceHub.call(ServiceRequest.of("dcCoreServices", "Authentication", "Verify")
					.withOutcome(new OperationOutcomeStruct() {
						@Override
						public void callback(Struct result) throws OperatingContextException {
							if (this.hasErrors())
								Logger.info("NOT Verified session: " + taskctx.getSessionId() + " on "
										+ reqpath + " for " + taskctx.getOrigin());
							else
								Logger.info("Verified session: " + taskctx.getSessionId() + " on "
										+ reqpath + " for " + taskctx.getOrigin());
							
							// doesn't really matter, token will be correct after verify
							taskctx.clearExitCode();
							
							taskctx.resume();
						}
					}));
			
			return;
		}

		if (this.firstrun) {
			this.firstrun = false;

			if (Logger.isDebug())
				Logger.debug("Web server request i");

			// after we get here we know we have a valid session and a valid user, even if that means that
			// the user session and user session requested has been replaced with Guest

			Session sess = taskctx.getSession();
			
			if (sess == null) {
				wctrl.sendRequestBad();
				taskctx.returnEmpty();
				return;
			}
			
			sess.touch();
			
			String origin = taskctx.getOrigin();
			
			String vauthtoken = taskctx.getUserContext().getAuthToken();
			
			//System.out.println("NOVHOATS final token: " + vauthtoken);
			
			if (! Objects.equals(this.oldAuthToken, vauthtoken)) {
				Cookie authk = new DefaultCookie("dcAuthToken", (vauthtoken != null) ? vauthtoken : "");
				authk.setPath("/");
				authk.setHttpOnly(true);
				
				// help pass security tests if Secure by default when using https
				authk.setSecure(wctrl.isSecure());
				
				wctrl.getResponse().setCookie(authk);
				
				sess.addKnownToken(vauthtoken);
			}
			
			// --------------------------------------------
			// rpc request
			// --------------------------------------------

			// TODO configure how requests are logged
			if (Logger.isDebug())
				Logger.debug("Web request for host: " + host +  " url: "
						+ reqpath + " by: " + origin + " session: " + taskctx.getSessionId());

			// "rpc" is it's own built-in extension.  all requests to rpc are routed through
			// DivConq bus, if the request is valid
			if ((reqpath.getNameCount() > 1) && reqpath.getName(0).equals(ServerHandler.DYN_PATH)) {
				if (reqpath.getName(1).equals(ServerHandler.RPC_PATH)) {
					wctrl.setDecoder(RpcHandler.create(taskctx));

					CountHub.countObjects("dcWebRpcCount-" + taskctx.getTenant().getAlias(), req);

					wctrl.getChannel().read();

					taskctx.returnEmpty();
					return;
				}
				else if (reqpath.getName(1).equals(ServerHandler.TRANSFER_PATH)) {
					String channel = reqpath.getName(2);
					String method = wctrl.getFieldAsRecord("Request").getFieldAsString("Method");

					// collect fragment for upload or download
					Session session = taskctx.getSession();

					HashMap<String, Struct> scache = session.getCache();

					// put the FileStoreFile in cache
					Struct centry = scache.get(channel);

					if ((centry == null) || ! (centry instanceof RecordStruct)) {
						Logger.error("Invalid channel number, unable to transfer.");
						wctrl.sendRequestBad();

						taskctx.returnEmpty();
						return;
					}

					Object so = ((RecordStruct)centry).getFieldAsAny("Stream");

					if ((so == null) || ! (so instanceof StreamFragment)) {
						Logger.error("Invalid channel number, not a stream, unable to transfer.");
						wctrl.sendRequestBad();

						taskctx.returnEmpty();
						return;
					}

					StreamFragment fragment = (StreamFragment) so;

					if ("GET".equals(method)) {
						wctrl.setDecoder(new IContentDecoder() {
							@Override
							public void offer(HttpContent chunk) throws OperatingContextException {
								if (chunk instanceof LastHttpContent) {
									taskctx.resume();  // cause stream work to start
								}
							}

							@Override
							public void release() throws OperatingContextException {
								wctrl.sendRequestBad();
								taskctx.returnEmpty();
							}
						});

						fragment.withAppend(HttpDestStream.dest());

						// the stream work should happen after `resume` in decoder above
						this.then(StreamWork.of(fragment));

						CountHub.countObjects("dcWebDownloadCount-" + taskctx.getTenant().getAlias(), req);

						// don't end task, that happens after decoder
						wctrl.getChannel().read();
						return;
					}
					else if ("PUT".equals(method) || "POST".equals(method)) {
						HttpSourceStream stream = HttpSourceStream.source(taskctx);

						wctrl.setDecoder(stream);

						fragment.withPrepend(stream);

						// the stream work should happen after `resume` in decoder above
						this.then(StreamWork.of(fragment));

						CountHub.countObjects("dcWebUploadCount-" + taskctx.getTenant().getAlias(), req);

						// let the super run so that streamwork requests a read.
					}
				}
			}
			else {
				wctrl.setDecoder(new IContentDecoder() {
					@Override
					public void offer(HttpContent chunk) {
						if (chunk instanceof LastHttpContent)
							wctrl.getChannel().read();
					}

					@Override
					public void release() {

					}
				});

				if (Logger.isDebug())
					Logger.debug("Web server request j");

				// --------------------------------------------
				// regular path/file request
				// --------------------------------------------

				if (Logger.isDebug())
					Logger.debug("Request posted to web domain: " + taskctx.getSessionId());

				IWork xwork = this.worker(taskctx, wctrl);

				// no errors starting page processing, return
				if (xwork == null)
					wctrl.sendNotFound();
				else
					this.then(xwork);
			}
		}

		super.run(taskctx);
	}
	
	public IWork worker(TaskContext taskctx, WebController wctrl) throws OperatingContextException {
		Site webSite = taskctx.getSite();
		RecordStruct req = wctrl.getFieldAsRecord("Request");
		CommonPath path = CommonPath.from(req.getFieldAsString("Path"));

		try (OperationMarker om = OperationMarker.create()) {
			if (Logger.isDebug())
				Logger.debug("Site: " + webSite.getAlias());
			
			if (Logger.isDebug())
				Logger.debug("Translating path: " + path);
			
			if (path.isRoot()) {
				path = webSite.getHomePath();
				req.with("Path", path.toString());
			}
			
			if (Logger.isDebug())
				Logger.debug("Process path: " + path);
			
			// translate above should take us home for root
			if (path.isRoot()) {
				Logger.errorTr(150001);
				return null;
			}
			
			// try with path case as is (should be lowercase anyway)
			IOutputWork output = webSite.webFindFile(path, wctrl.getFieldAsRecord("Request").getFieldAsString("View"));
			
			if (om.hasErrors()) {
				Logger.errorTr(150001);
				return null;
			}
			
			// try with lowercase path
			if (output == null) {
				path = CommonPath.from(path.toString().toLowerCase());
				output = webSite.webFindFile(path, wctrl.getFieldAsRecord("Request").getFieldAsString("View"));
			}
			
			if (om.hasErrors() || (output == null)) {
				Logger.errorTr(150001);
				return null;
			}

			req.with("Path", output.getPath().toString());
			
			if (Logger.isDebug())
				Logger.debug("Executing adapter: " + output.getClass().getName());
			
			return output;
		}
		catch (Exception x) {
			Logger.error("Unable to process web file: " + x);
			return null;
		}
	}
	
	
}
