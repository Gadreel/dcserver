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
package dcraft.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import dcraft.filestore.CommonPath;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.DebugLevel;
import dcraft.log.HubLog;
import dcraft.log.Logger;
import dcraft.service.ServiceRequest;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;

public class Session extends RecordStruct implements IVariableProvider {
	static public Session restore(String origin, String tenant, String site, String id, String key) {
		Session sess = new Session();
		
		sess.id = id;
		sess.key = key;
		sess.user = UserContext.guestUser(tenant, site);
		sess.originalOrigin = origin;
		
		return sess;
	}
	
	static public Session of(String origin, String tenant, String site) {
		Session sess = new Session();
			
		sess.id = ApplicationHub.getNodeId() + "_" + SessionHub.nextSessionId();
		sess.key = StringUtil.buildSecurityCode();				// TODO switch to crypto secure
		sess.user = UserContext.guestUser(tenant, site);
		sess.originalOrigin = origin;
		
		return sess;
	}
	
	static public Session of(String origin) {
		Session sess = new Session();
		
		sess.id = ApplicationHub.getNodeId() + "_" + SessionHub.nextSessionId();
		sess.key = StringUtil.buildSecurityCode();				// TODO switch to crypto secure
		sess.user = UserContext.guestUser();
		sess.originalOrigin = origin;
		
		return sess;
	}
	
	static public Session of(String origin, UserContext user) {
		Session sess = new Session();
		
		sess.id = ApplicationHub.getNodeId() + "_" + SessionHub.nextSessionId();
		sess.key = StringUtil.buildSecurityCode();				// TODO switch to crypto secure
		sess.user = user;
		sess.originalOrigin = origin;
		
		return sess;
	}

	// TODO move many of these to Record Fields
	protected String id = null;
	protected String key = null;
	protected long lastAccess = 0;
	protected long lastReauthAccess = System.currentTimeMillis();
	protected UserContext user = null;
    protected List<String> pastauthtokens = new ArrayList<>();

	protected DebugLevel level = null;
	protected String originalOrigin = null;
	protected String opchronology = null;
	protected String oplocale = null;

	protected ResourceTier resources = null;

	protected HashMap<String, BaseStruct> cache = new HashMap<>();
	protected HashMap<String, TaskContext> tasks = new HashMap<>();
	protected ISessionAdapter adapter = null;
		
	public String getId() {
		return this.id;
	}
	
	public HashMap<String, BaseStruct> getCache() {
		return this.cache;
	}

	public String getKey() {
		return this.key;
	}
	
	/**
	 * @return logging level to use with this session (and all sub tasks)
	 */
	public DebugLevel getLevel() {
		return this.level;
	}
	
	/**
	 * @param v logging level to use with this session (and all sub tasks)
	 */
	public Session withLevel(DebugLevel v) {
		this.level = v;
		return this;
	}
	
	public Session withLocale(String v) {
		this.opchronology = v;
		return this;
	}
	
	public Session withChronolog(String v) {
		this.opchronology = v;
		return this;
	}
	
	public UserContext getUser() {
		return this.user;
	}

	public Session withAdatper(ISessionAdapter v) {
		this.adapter = v;
		return this;
	}
	
	public ISessionAdapter getAdapter() {
		return this.adapter;
	}
	
	public Session withOriginalOrigin(String v) {
		this.originalOrigin = v;
		return this;
	}
	
	protected Session() {
		this.level = HubLog.getGlobalLevel();
		this.originalOrigin = "hub:";
		this.lastAccess = System.currentTimeMillis();
		this.with("Variables", RecordStruct.record());
	}

	public ResourceTier getOrCreateResources() {
		if (this.resources == null)
			this.resources = OperationResourceTier.tier(this.user.getTenantAlias(), this.user.getSiteAlias());

		return this.resources;
	}

	public ResourceTier getResources() {
		ResourceTier tr = this.resources;

		if (tr != null)
			return tr;

		Site si = this.user.getSite();

		return si.getResources();
	}

	// used for changing user context
	public void userChanged() {
		String newtoken = this.user.getAuthToken();
		
		this.addKnownToken(newtoken);
		
		if (this.adapter != null)
			this.adapter.userChanged(Session.this.user);
	}
	
	public OperationContext allocateContext() {
		return OperationContext.context(this.user)
				.withOrigin(this.originalOrigin)
				.withDebugLevel(this.level)
				.withSessionId(this.id)
				.withChronology(this.opchronology)
				.withLocale(this.oplocale);
	}
	
	public OperationContext allocateContext(OperationController ctrlr) {
		return OperationContext.context(this.user, ctrlr)
			.withOrigin(this.originalOrigin)
			.withDebugLevel(this.level)
			.withSessionId(this.id)
			.withChronology(this.opchronology)
			.withLocale(this.oplocale);
	}	

	public void addKnownToken(String token) {
		if (StringUtil.isNotEmpty(token))
			this.pastauthtokens.add(token);
		
		// keep only the most recent tokens
		while (this.pastauthtokens.size() > 3)
			this.pastauthtokens.remove(0);
	}
	
	public boolean isKnownAuthToken(String token) {
		if (token == null)
			return true;
		
		String currtoken = this.user.getAuthToken();

		if (Objects.equals(token, currtoken))
			return true;
		
		for (String pasttoken : this.pastauthtokens)
			if (Objects.equals(token, pasttoken))
				return true;
		
		return false;
	}
		
	public void touch() {
		this.lastAccess = System.currentTimeMillis();

		// keep auth token alive by pinging it at least once every hour - TODO configure
		/* TODO rework as keep token alive Task
		if ((this.lastAccess - this.lastReauthAccess > (60 * 60000)) && this.user.isAuthenticated()) {
			OperationContext curr = OperationContext.get();
			
			try {
				// be sure to send the message with the correct context
				this.useContext();
				
				OperationContext.get().verify(null);
			}
			finally {
				OperationContext.set(curr);
			}
			
			// keep this up to date whether we are gateway or not, this way fewer checks
			this.lastReauthAccess = this.lastAccess;
		}
		*/
	}
	
	public void end() {
		//System.out.println("collab session ended: " + this.collabId);
		
		// TODO consider clearing adapter and reply handler too
		if (this.adapter != null)
			this.adapter.kill();
		
		Logger.info("Ending session: " + this.id);
	}
	
	public void registerTask(TaskContext trun) {
		if (trun == null) 
			Logger.errorTr(213, "info");
		else
			this.tasks.put(id, trun);
	}

	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		this.touch();		
		
		if ("TouchMe".equals(request.getOp())) {
			callback.returnEmpty();
			return true;
		}
		else if ("DebugLevelMe".equals(request.getOp())) {
			// session user may alter the debug level for the session, but only if debugging is enabled on system
			if (HubLog.getDebugEnabled()) {
				this.level = DebugLevel.parse(request.getData().toString());
				callback.returnEmpty();
			}
			
			return true;
		}
		else if ("LoadMe".equals(request.getOp())) {
			callback.returnValue(Session.this.user.deepCopyExclude("AuthToken"));
			return true;
		}
		else if ("CheckJob".equals(request.getOp())) {
			/* TODO
			RecordStruct rec = msg.getFieldAsRecord("Body");
			Long jid = rec.getFieldAsInteger("JobId");
			
			TaskContext info = this.tasks.get(jid);
			
			if (info != null) {
				Struct res = info.getResult();
				Message reply = info.toLogMessage();
				
				reply.withField("Body",
					RecordStruct.record()
						.with("AmountCompleted", info.getAmountCompleted())
						.with("Steps", info.getSteps())
						.with("CurrentStep", info.getCurrentStep())
						.with("CurrentStepName", info.getCurrentStepName())
						.with("ProgressMessage", info.getProgressMessage())
						.with("Result", res)
				);
				
				Session.this.reply(reply, msg);
			}
			else {
				Message reply = MessageUtil.error(1, "Job Not Found");
				Session.this.reply(reply, msg);
			}
			*/
			
			return true;
		}
		
		if ("ClearJob".equals(request.getOp())) {
			/*
			RecordStruct rec = msg.getFieldAsRecord("Body");
			Long jid = rec.getFieldAsInteger("JobId");
			
			this.tasks.remove(jid);
			
			Session.this.reply(MessageUtil.success(), msg);
			*/
			
			return true;
		}
		
		if ("KillJob".equals(request.getOp())) {
			/*
			RecordStruct rec = msg.getFieldAsRecord("Body");
			Long jid = rec.getFieldAsInteger("JobId");
			
			// get not remove, because kill should do the remove and we let it do it in the natural way
			TaskContext info = this.tasks.get(jid);
			
			if (info != null)
				info.kill();
			
			Session.this.reply(MessageUtil.success(), msg);
			*/
			
			return true;
		}

		return false;
	}

	// return true if keep session
	public boolean reviewPlan(long clearGuest, long clearUser) {
		// TODO needs a plan system for what to do when session ends/times out/etc 
		// check both tasks and channels for completeness (terminate only on complete, vs on timeout, vs never)
		
		// TODO cleanout old tasks
		
		if (this.isLongRunning())
			return ((this.lastAccess > clearUser) || ((this.adapter != null) && this.adapter.isAlive()));
		
		return ((this.lastAccess > clearGuest) || ((this.adapter != null) && this.adapter.isAlive()));
	}
	
	// user sessions can be idle for a longer time (3 minutes default) than guest sessions (75 seconds default)
	public boolean isLongRunning() {
		return this.user.isTagged("User");
	}


	@Override
	public RecordStruct variables() {
		return this.getFieldAsRecord("Variables");
	}

	@Override
	public void addVariable(String name, BaseStruct var) throws OperatingContextException {
		this.getFieldAsRecord("Variables").with(name, var);

		if (var instanceof AutoCloseable) {
			OperationContext run = OperationContext.getOrThrow();

			if (run != null) {
				run.getController().addObserver(new OperationObserver() {
					@Override
					public void completed(OperationContext ctx) {
						try {
							((AutoCloseable) var).close();
						}
						catch (Exception x) {
							Logger.warn("Script could not close and autoclosable var: " + x);
						}
					}
				});
			}
		}
	}

	@Override
	public void clearVariables() {
		this.getFieldAsRecord("Variables").clear();
	}

	@Override
	public BaseStruct queryVariable(String name) {
		if (StringUtil.isEmpty(name))
			return null;

		return this.getFieldAsRecord("Variables").getField(name);
	}
}
