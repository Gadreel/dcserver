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
package dcraft.hub.op;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicLong;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.resource.ResourceTier;
import dcraft.locale.LocaleDefinition;
import dcraft.locale.LocaleResource;
import dcraft.locale.Translator;
import dcraft.log.DebugLevel;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.session.Session;
import dcraft.session.SessionHub;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

/**
 * Almost all code that executes after Hub.start should have a context.  The context
 * tells the code who the user responsible for the task is, what their access levels
 * are (at a high level), what language/locale/chronology(timezone) they use, how to log the 
 * debug messages for the task, and whether or not the user has been authenticated or not.
 * 
 * Although the task context is associated with the current thread, it is the task that the
 * context belongs to, not the thread.  If a task splits into multiple threads there is still
 * one TaskContext, even if the task makes a remote call on DivConq's bus that remote call
 * executes on the TaskContext.
 * 
 * As long as you use the built-in features - work pool, scheduler, bus, database - the task context
 * will smoothly come along with no effort from the app developer.
 *   
 * A quick guide to what context to use where:
 * 
 * Root Context - useNewRoot()
 * 
 * 		Root context is the same identity as Hub, but with useNewRoot() you get a new log id.  
 * 		Use this with code running batch tasks that belong to the system rather than a specific
 * 		user.
 * 
 * Guest Context - useNewGuest()
 * 
 * 		Guest context is for use by an anonymous user.  For example a user through the interchange
 * 		(HTTP, FTP, SFTP, EDI, etc).
 * 
 *  User Context - new TaskContext + set(tc)
 *  
 *  	When a user signs-in create and set a new context.  No need to authenticate against the database
 *  	that will happen automatically (as long as you follow DivConq development guidelines) so think
 *  	of creating the user Task Context as information gathering not authentication. 
 * 
 * @author Andy
 *
 */
public class OperationContext extends RecordStruct implements IVariableProvider {
	static protected ThreadLocal<OperationContext> context = new ThreadLocal<>();
	static protected AtomicLong nextid = new AtomicLong();
	
	/**
	 * @return context of the current thread, if any
	 *         otherwise the guest context
	 */
	static public OperationContext getOrGuest() {
		OperationContext tc = OperationContext.context.get();
		
		if (tc == null) {
			tc = OperationContext.context(UserContext.guestUser());
			OperationContext.set(tc);
		}
		
		return tc;
	}
	
	static public OperationContext getOrThrow() throws OperatingContextException {
		OperationContext tc = OperationContext.context.get();
		
		if (tc == null) 
			throw new OperatingContextException("Operating context required, but not found.");
		
		return tc;
	}
	
	static public TaskContext getAsTaskOrThrow() throws OperatingContextException {
		OperationContext tc = OperationContext.context.get();
		
		if (tc == null) 
			throw new OperatingContextException("Operating context required, but not found.");
		
		if (tc instanceof TaskContext)
			return (TaskContext) tc;
		
		throw new OperatingContextException("Task context required, but operation context found.");
	}
	
	static public OperationContext getOrNull() {
		return OperationContext.context.get();
	}
	
	// does the current thread have a context?
	static public boolean hasContext() {
		return (OperationContext.context.get() != null);
	}
	
	/**
	 * @param v context for current thread to use
	 */
	static public void set(OperationContext v) {
		OperationContext.context.set(v);
	}
	
	/*
	 * create contexts
	 */
	
	static public OperationContext context(UserContext user) {
		OperationContext ctx = new OperationContext();
		
		ctx.with("Id", OperationContext.allocateOpId());
		ctx.with("User", user);
		ctx.init();

		return ctx;
	}
	
	static public OperationContext context(UserContext user, OperationController ctrl) {
		OperationContext ctx = new OperationContext();
		
		ctx.with("Id", ctrl.getOpId());
		ctx.with("User", user);
		ctx.with("Controller", ctrl);
		ctx.init();

		return ctx;
	}
	
	static public OperationContext context(UserContext user, String id) {
		OperationContext ctx = new OperationContext();
		
		ctx.with("Id", id);
		ctx.with("User", user);
		ctx.init();

		return ctx;
	}

	/*
	static public OperationContext context(RecordStruct rec) {
		return OperationContext.context(rec.getFieldAsRecord("User"), rec.deepCopyExclude("User"));
	}
	
	static public OperationContext context(RecordStruct user, RecordStruct rec) {
		if (rec == null)
			rec = new RecordStruct();
		
		if (rec.isFieldEmpty("Id"))
			rec.with("Id", OperationContext.allocateOpId());
		
		OperationContext ctx = new OperationContext();
		
		ctx.opcontext = rec;
		ctx.userctx = UserContext.user(user);
		ctx.init();

		return ctx;
	}
	
	static public OperationContext context(UserContext user, RecordStruct rec) {
		if (rec == null)
			rec = new RecordStruct();
		
		if (rec.isFieldEmpty("Id"))
			rec.with("Id", OperationContext.allocateOpId());
		
		OperationContext ctx = new OperationContext();
		
		ctx.opcontext = rec;
		ctx.userctx = user;
		ctx.init();

		return ctx;
	}
	*/
	
	static synchronized public String allocateOpId() {
		long num = OperationContext.nextid.getAndIncrement();
		
		if (num > 999999999999999L) {
			OperationContext.nextid.set(0);
			num = 0;
		}
		
		String opid = ApplicationHub.getNodeId()
			+ "-" + ApplicationHub.getRunId()
			+ "-" + StringUtil.leftPad(num + "", 15, '0');
		
		
		Logger.info("New operation context", "OpId", opid);
		
		return opid;
	}
	
	// instance code
	
	/*
		<Record Id="OpContext">
			<Field Name="Id" Type="dcTinyString" />
			<Field Name="User" Type="UserContext" />
			<Field Name="Locale" Type="dcSmallString" />
			<Field Name="Chronology" Type="dcSmallString" />
			<Field Name="SessionId" Type="dcTinyString" />
			<Field Name="Origin" Type="dcSmallString" />
			<Field Name="DebugLevel" Type="dcTinyString" />
		</Record>
	 */
	
	//protected UserContext userctx = null;
	//protected OperationController controller = null;
    
	protected ResourceTier resources = null;
	
	//HubLog.getGlobalLevel()protected DebugLevel level = HubLog.getGlobalLevel();
    
    // this tracks time stamp of signs of life from the job writing to the log/progress tracks
    // volatile helps keep threads on same page - issue found in code testing and this MAY have helped 
    volatile protected long lastactivity = System.currentTimeMillis();
    
    public void touch() {
    	this.lastactivity = System.currentTimeMillis();
    	
   		this.getController().touch(this);
    }
    
    public long getLastActivity() {
		return this.getController().getLastActivity();
	}
    
    public long getLastContextActivity() {
		return this.lastactivity;
	}
    
	/**
	 * @return a unique task id - unique across all deployed hub, across runs of a hub
	 */
	public String getOpId() {
		return this.getController().getOpId();
	}

	public OperationController getController() {
		return (OperationController) this.getFieldAsRecord("Controller");
	}

	/**
	 * @return the the user context for this task (user context may be shared with other tasks)
	 */
	public UserContext getUserContext() {
		return (UserContext) this.getFieldAsRecord("User");
	}
	
	public Tenant getTenant() {
		return this.getUserContext().getTenant();
	}
	
	public Site getSite() {
		return this.getUserContext().getSite();
	}

	/**
	 * not all tasks will have a session, but if there is a session here it is. 
	 *
	 * @return the id of the session that spawned this task
	 * 
	 */
	public String getSessionId() {
		return this.getFieldAsString("SessionId");
	}
	
	public void setSessionId(String v) {
		this.with("SessionId", v);
	}
	
	public OperationContext withSessionId(String v) {
		this.setSessionId(v);
		return this;
	}

	/**
	 * not all tasks will have a session, but if there is a session here it is.  sessions are local 
	 * to a hub and are not transfered to another hub with the rest of the task info when calling
	 * a remote service.
	 * 
	 * @return the session for this task (user context may be shared with other tasks)
	 */
	public Session getSession() {
		return SessionHub.lookup(this.getSessionId());
	}
	
	/**
	 * Origin indicates where this task originated from.  "hub:" means it was started by
	 * the a hub (task id gives away which hub).  "http:[ip address]" means the task
	 * was started in response to a web request.  "ws:[ip address]" means the task
	 * was started in response to a web scoket request.  "ftp:[ip address]" means the task
	 * was started in response to a ftp request.  Etc.
	 * 
	 * @return origin string 
	 */
	public String getOrigin() {
		return this.getFieldAsString("Origin");
	}
	
	public void setOrigin(String v) {
		this.with("Origin", v);
	}
	
	public OperationContext withOrigin(String v) {
		this.setOrigin(v);
		return this;
	}
	
	/*
	 * Debug level
	 */
	public DebugLevel getDebugLevel() {
		return this.getController().getLevel();
	}
	
	public void setDebugLevel(DebugLevel v) {
		this.with("DebugLevel", v.toString());
		
		this.getController().setLevel(v);
	}
	
	public OperationContext withDebugLevel(DebugLevel v) {
		this.setDebugLevel(v);
		return this;
	}
	
	/*
	 * Operating Locale
	 */
	public String getLocale() {
		if (this.isNotFieldEmpty("Locale"))
			return this.getFieldAsString("Locale");
		
		return this.getResources().getLocale().getDefaultLocale();
	}
	
	public LocaleDefinition getLocaleDefinition() {
		return this.getResources().getLocale().getLocaleDefinition(this.getLocale());
	}
	
	public void setLocale(String v) {
		this.with("Locale", v);
		this.getOrCreateResources().getOrCreateTierLocale().setDefaultLocale(v);
	}
	
	public OperationContext withLocale(String v) {
		this.setLocale(v);
		return this;
	}
	
	/*
	 * Operating Chronology
	 */
	public String getChronology() {
		if (this.isNotFieldEmpty("Chronology"))
			return this.getFieldAsString("Chronology");
		
		return this.getResources().getLocale().getDefaultChronology();
	}
	
	public void setChronology(String v) {
		this.with("Chronology", v);
		this.getOrCreateResources().getOrCreateTierLocale().setDefaultChronology(v);
	}
	
	public OperationContext withChronology(String v) {
		this.setChronology(v);
		return this;
	}
	
	// only for subclasses like TaskContext
	protected OperationContext() {
		this.with("Variables", RecordStruct.record());
	}
	
	protected void init() {
		// useful for TaskContext - general fall back
		if (this.isFieldEmpty("Id")) {
			if (this.getController() != null)
				this.with("Id", this.getController().getOpId());
			else
				this.with("Id", OperationContext.allocateOpId());
		}
		
		if (this.isNotFieldEmpty("Locale"))
			this.setLocale(this.getFieldAsString("Locale"));
		
		if (this.isNotFieldEmpty("Chronology"))
			this.setChronology(this.getFieldAsString("Chronology"));
		
		if (this.getController() == null)
			this.with("Controller", new OperationController(this.getFieldAsString("Id")));
		
		if (this.isNotFieldEmpty("DebugLevel"))
			this.getController().setLevel(DebugLevel.parse(this.getFieldAsString("DebugLevel")));
	}
	
	public TaskContext asTaskRun() {
		if (this instanceof TaskContext)
			return (TaskContext) this;
		
		return null;
	}
	
	public ResourceTier getOrCreateResources() {
		if (this.resources == null)
			this.resources = OperationResourceTier.tier(this.getSessionId(), this.getUserContext().getTenantAlias(), this.getUserContext().getSiteAlias());
		
		return this.resources;
	}
	
	public ResourceTier getResources() {
		ResourceTier tr = this.resources;
		
		if (tr != null) 
			return tr;

		Session sess = this.getSession();

		if (sess != null)
			return sess.getResources();

		Site si = this.getSite();
		
		if (si != null)
			return si.getResources();
		
		return ResourceHub.getTopResources();
	}

	@Override
	public OperationContext deepCopy() {
		OperationContext cp = new OperationContext();
		cp.resources = this.resources;
		cp.lastactivity = this.lastactivity;
		this.doCopy(cp);
		return cp;
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

		if ("_Context".equals(name) || "_Global".equals(name))
			return this;

		if ("_Controller".equals(name))
			return this.getController();

		if ("_User".equals(name))
			return this.getUserContext();
		
		if ("_Tr".equals(name)) {
			return Translator.of(this.getResources());
		}
		
		if ("_Session".equals(name))
			return this.getSession();

		if ("_Site".equals(name))
			return this.getSite();

		if ("_Tenant".equals(name))
			return this.getTenant();
		
		if ("_Resources".equals(name))
			return this.getResources();
		
		if ("_Node".equals(name))
			return ApplicationHub.getVariables();

		BaseStruct ret = this.getFieldAsRecord("Variables").getField(name);

		if (ret != null)
			return ret;

		ret = this.getController().queryVariable(name);

		if (ret != null)
			return ret;

		Session sess = this.getSession();

		if (sess != null)
			ret = sess.queryVariable(name);

		if (ret != null)
			return ret;

		ret = this.getSite().queryVariable(name);

		if (ret != null)
			return ret;

		return null;
	}

	@Override
	public String toString() {
		return this.toPrettyString();    // TODO review, maybe filter some fields?
	}
	
	/**
	 * @param lvl level of message
	 * @param code for message
	 * @param msg text of message
	 * @param tags of message
	 */
	public void log(DebugLevel lvl, long code, String msg, String... tags) {
		// must be some sort of message
		if (StringUtil.isEmpty(msg))
			return;
		
		RecordStruct entry = new RecordStruct()
			.with("Occurred", ZonedDateTime.now(ZoneId.of("UTC")))
			.with("Level", lvl.toString())
			.with("Code", code)
			.with("Message", msg);
		
		if (tags.length > 0)
			entry.with("Tags", ListStruct.list((Object[])tags));
		
		this.getController().log(this,entry, lvl);
	}
	
	/**
	 * @param lvl level of message
	 * @param code for message
	 * @param params parameters to the message string
	 */
	public void logTr(DebugLevel lvl, long code, Object... params) {
		String msg = this.tr("_code_" + code, params);
		
		RecordStruct entry = new RecordStruct()
			.with("Occurred", ZonedDateTime.now(ZoneId.of("UTC")))
			.with("Level", lvl.toString())
			.with("Code", code)
			.with("Message", msg);
		
		this.getController().log(this, entry, lvl);
	}
    
    /**
     * Add a logging boundary, delineating a new section of work for this task
     * 
     * @param tags identity of this boundary
     */
    public void boundary(String... tags) {
		RecordStruct entry = new RecordStruct()
			.with("Occurred", ZonedDateTime.now(ZoneId.of("UTC")))
			.with("Level", DebugLevel.Info.toString())
			.with("Code", 0)
			.with("Tags", ListStruct.list((Object[])tags));
		
		this.getController().log(this, entry, DebugLevel.Info);
    }
	
    /* TODO restore once we understand better - override in TaskContext?
	public void logResult(RecordStruct v) {
		ListStruct h = v.getFieldAsList("Messages");
		
		if (h != null) {
			for (Struct st : h.getItems()) 
				this.controller.log(this, (RecordStruct) st);
		}
	}
	*/

	public boolean isLevel(DebugLevel debug) {
		return (this.getController().getLevel().getCode() >= debug.getCode());
	}

	public String tr(String token, Object... params) {
		ResourceTier tr = this.getResources();

		if (tr == null)
			return token;

		LocaleResource lr = tr.getLocale();
		
		if (lr == null)
			return token;

		return lr.tr(token, params);
	}

	public String trp(String pluraltoken, String singulartoken, Object... params) {
		ResourceTier tr = this.getResources();

		if (tr == null)
			return singulartoken;

		LocaleResource lr = tr.getLocale();
		
		if (lr == null)
			return singulartoken;

		return lr.trp(pluraltoken, singulartoken, params);
	}
}
