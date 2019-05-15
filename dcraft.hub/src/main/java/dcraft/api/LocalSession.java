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

import dcraft.filevault.VaultServiceRequest;
import dcraft.filevault.VaultUtil;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.session.ISessionAdapter;
import dcraft.session.Session;
import dcraft.session.SessionHub;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.xml.XElement;

import java.util.HashMap;

// TODO test to be sure that the user associated with the session is not
// mixed up with the user calling the session, a single task should be able
// to run multiple local sessions, all impersonating different users
public class LocalSession extends ApiSession {
	static public LocalSession local(String domain) {
		Site site = TenantHub.resolveSite(domain);
		
		if (site == null) {
			Logger.error("Invalid domain: " + domain);
			return null;
		}
		
		Session sess = Session.of("hub:", site.getTenant().getAlias(), site.getAlias());
		
		if (sess == null) {
			Logger.error("Unable to create session: " + domain);
			return null;
		}
		
		LocalSession api = new LocalSession();
		api.init(sess, domain);
		
		SessionHub.register(sess);
		
		return api;
	}
	
	protected Session session = null;
	
	/*
	public void init(XElement config) {
		Session sess = Session.of(config.getAttribute("Tenant"),
				config.getAttribute("Site", "root"), "hub:");

		this.init(sess);
	}
	*/
	
	public void init(Session session, String domain) {
		this.session = session;
		this.domain = domain;
		
		this.session
			.withAdatper(new LocalSessionAdatper());
	}
	
	public class LocalSessionAdatper implements ISessionAdapter {			
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
		SessionHub.terminate(this.session.getId());	
	}
	
	@Override
	public void call(ServiceRequest request) {
		OperationContext currctx = OperationContext.getOrNull();
		
		try {
			request.requireOutcome().markStart();		// messages before this won't get logged in outcome
			
			OperationContext ctx = (currctx != null)
					? this.session.allocateContext(currctx.getController())
					: this.session.allocateContext();
		
			// use the session's user
			OperationContext.set(ctx);
			
			// mark this as needing service authentication
			request.withFromRpc();
			
			ServiceHub.call(request);
		}
		catch (OperatingContextException x) {
			Logger.warn("Unexpected error: " + x);
			
			try {
				request.requireOutcome().returnEmpty();
			}
			catch (OperatingContextException x2) {
			}
		}
		finally {
			OperationContext.set(currctx);
		}
	}
	
	@Override
	public void transfer(String channel, StreamFragment source, StreamFragment dest, OperationOutcomeEmpty callback) {
		VaultUtil.transfer(channel, source, dest, callback);

		/*
		OperationContext currctx = OperationContext.getOrNull();
		
		try {
			HashMap<String, Struct> scache = this.session.getCache();
			
			// put the FileStoreFile in cache
			Struct centry = scache.get(channel);
			
			if ((centry == null) || ! (centry instanceof RecordStruct)) {
				Logger.error("Invalid channel number, unable to transfer.");
				callback.returnEmpty();
				return;
			}
			
			Object so = ((RecordStruct)centry).getFieldAsAny("Stream");
			
			if ((so == null) || ! (so instanceof StreamFragment)) {
				Logger.error("Invalid channel number, not a stream, unable to transfer.");
				callback.returnEmpty();
				return;
			}
			
			Task task = Task
					.ofSubtask("API Transfer Stream", "XFR")
					.withWork(StreamWork.of(source, (StreamFragment) so, dest));
			
			TaskHub.submit(task, new TaskObserver() {
				@Override
				public void callback(TaskContext subtask) {
					callback.returnEmpty();
				}
			});
		}
		catch (OperatingContextException x) {
			Logger.warn("Unexpected error: " + x);
			callback.returnEmpty();
		}
		finally {
			OperationContext.set(currctx);
		}
		*/
	}
}
