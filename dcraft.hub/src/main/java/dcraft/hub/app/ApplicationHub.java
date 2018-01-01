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
package dcraft.hub.app;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import dcraft.db.work.DbStartWork;
import dcraft.db.work.DbStopWork;
import dcraft.filestore.CommonPath;
import dcraft.filevault.work.DepositStartWork;
import dcraft.filevault.work.DepositStopWork;
import dcraft.hub.ResourceHub;
import dcraft.hub.config.HubStartBeginWork;
import dcraft.hub.config.HubStartFinalWork;
import dcraft.hub.config.HubStopBeginWork;
import dcraft.hub.config.HubStopFinalWork;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.ResourceTier;
import dcraft.script.work.ScriptStartWork;
import dcraft.script.work.ScriptStopWork;
import dcraft.service.work.ServiceStartWork;
import dcraft.service.work.ServiceStopWork;
import dcraft.session.work.SessionStartWork;
import dcraft.session.work.SessionStopWork;
import dcraft.sql.work.SqlStartWork;
import dcraft.sql.work.SqlStopWork;
import dcraft.struct.RecordStruct;
import dcraft.task.*;
import dcraft.task.run.WorkTopic;
import dcraft.task.work.StandardTaskStart;
import dcraft.task.work.StandardTaskStop;
import dcraft.tenant.work.TenantStartWork;
import dcraft.tenant.work.TenantStopWork;
import dcraft.util.pgp.KeyRingCollection;
import dcraft.xml.XElement;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import dcraft.hub.clock.Clock;
import dcraft.log.Logger;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.io.IFileWatcher;

/**
 * Hub is the center of activity for DivConq applications.  Most of the built-in resources/features are available via the Hub.
 * The Hub must be initialized by creating a HubResource and passing it to "start".  When the application quits it is best
 * to call "stop".
 * 
 * There is only one Hub object per (JVM) process 
 *  
 * @author Andy White
 *
 */
public class ApplicationHub {
	static protected String deployment = "default";
	static protected String nodeid = "00001";		// reserved for utilities and stand alone - 00000 reserved for system/core
	static protected String runid = TimeUtil.stampFmt.format(ZonedDateTime.now(ZoneId.of("UTC")));

	static protected boolean isproduction = false;
	static protected String role = "local";			// local and remote are the core roles, but can be expanded
	
	static protected long starttime = System.currentTimeMillis();
	static protected Clock clock = new Clock();

	// resources
	static protected ByteBufAllocator bufferAllocator = PooledByteBufAllocator.DEFAULT;
	static protected EventLoopGroup eventLoopGroup = null;

	// PGP keys
	static protected KeyRingCollection keyring = null;
	
	// api session managers
	// TODO move to ResourceTier - static protected ConcurrentHashMap<String, IApiSessionFactory> apimans = new ConcurrentHashMap<>();
	
	// file tracking
	static protected WatchService watcher = null;
	static protected Map<WatchKey, IFileWatcher> filewatchdata = new HashMap<>();
	
	// hub events
	static protected Set<IEventSubscriber> subscribers = new HashSet<>();
	static protected HubState state = HubState.Booting;
	static protected boolean stateflag = false;
	
	static protected IWork configloader = null;
	
	static protected Map<CommonPath,Claim> claims = new HashMap<>();
	
	static protected RecordStruct variables = null;
	
	/* TODO Read
	 *	http://blog.sokolenko.me/2014/11/javavm-options-production.html
	 * 
	 */
	static {
		// not all JVM's set this correctly, works with JVM 7+
		java.security.Security.setProperty("networkaddress.cache.ttl" , "60");		
	}
	
	static public boolean isValidNodeId(String id) {
		if (StringUtil.isEmpty(id) || (id.length() != 5))
			return false;
		
		for (int i = 0; i < 5; i++) 
			if (!Character.isDigit(id.charAt(i))) 
				return false;
		
		if ("00000".equals(id))
			return false;
		
		return true;
	}	
	
  	/**
  	 * NodeId is a 5 digit (zero padded) number that uniquely identifies this Hub (process) in
  	 * the distributed network of Nodes (processes) in your Project (application).
  	 * 
  	 * @return NodeId
  	 */
	static public String getNodeId() {
		return ApplicationHub.nodeid;
	}
	/**
  	 * NodeId is a 5 digit (zero padded) number that uniquely identifies this Hub (process) in
  	 * the distributed network of Hubs (processes) in your Project (application). 
	 * 
	 * You should only set the NodeId once per run, Nodes are not designed to change Ids mid run.
	 * 
	 * @param v NodeId
	 */
	static public void setNodeId(String v) {
		if (! ApplicationHub.isValidNodeId(v))
			throw new IllegalArgumentException("Node id must be 5 digits, zero padded. Id 00000 is reserved.");
		
		ApplicationHub.nodeid = v;
	}

	static public String getRunId() {
		return ApplicationHub.runid;
	}

	static public HubState getState() {
		return ApplicationHub.state;
	}
	
	static public boolean isProduction() {
		return ApplicationHub.isproduction;
	}
	
	static public void setProduction(boolean v) {
		ApplicationHub.isproduction = v;
	}
	
	static public String getRole() {
		return ApplicationHub.role;
	}
	
	static public void setRole(String v) {
		ApplicationHub.role = v;
	}
	
	static public String getDeployment() {
		return ApplicationHub.deployment;
	}
	
	static public void setDeployment(String v) {
		ApplicationHub.deployment = v;
	}
	
	static public Path getDeploymentPath() {
		return Paths.get("./deploy-" + ApplicationHub.deployment);
	}
	
	static public Path getDeploymentNodePath() {
		return Paths.get("./deploy-" + ApplicationHub.deployment + "/nodes/" +
				ApplicationHub.nodeid);
	}
	
	static public Path getDeploymentRoleePath() {
		return Paths.get("./deploy-" + ApplicationHub.deployment + "/roles/" +
				ApplicationHub.nodeid);
	}
	
	static public Path getDeploymentTenantsPath() {
		return Paths.get("./deploy-" + ApplicationHub.deployment + "/tenants");
	}
	
	public static void setState(HubState v) {
		if (ApplicationHub.state == v)
			return;
		
		ApplicationHub.state = v;
		
		Logger.info("Hub entered " + v.name() +" state");
		
		ApplicationHub.fireEvent(HubEvents.HubState, v);
	}
	
	public static void setWatcher(WatchService v) {
		ApplicationHub.watcher = v;
	}
	
	static public boolean isStopping() {
		return (ApplicationHub.state == HubState.Stopping) || (ApplicationHub.state == HubState.Stopped); 
	}
	
	static public boolean isIdled() {
		return (ApplicationHub.state == HubState.Idle); 
	}
	
	static public boolean isRunning() {
		return (ApplicationHub.state == HubState.Running); 
	}
	
	static public boolean isNotRunning() {
		return (ApplicationHub.state != HubState.Running);
	}
	
	static public boolean isOperational() {
		return (ApplicationHub.state == HubState.Idle) || (ApplicationHub.state == HubState.Running);
	}
	
	static public ByteBufAllocator getBufferAllocator() {
		return ApplicationHub.bufferAllocator;
	}
	
	static public EventLoopGroup getEventLoopGroupIfPresent() {
		return ApplicationHub.eventLoopGroup;
	}
	
	static public EventLoopGroup getEventLoopGroup() {
		if (ApplicationHub.eventLoopGroup == null)
			ApplicationHub.eventLoopGroup = new NioEventLoopGroup();
		
		return ApplicationHub.eventLoopGroup;
	}

	/**
	 * The clock tracks the application's time (time zone and date time).  The application's time
	 * can be altered or even sped up.  There are also some scheduling methods in Clock but consider
	 * using Scheduler over Clock, see "getScheduler".
	 * 
	 * @return the clock manager
	 */
	static public Clock getClock() {
		return ApplicationHub.clock;
	}
	
	/**
	 * @return time the Hub started in ms since 1970 
	 */
	static public long getStartTime() {
		return ApplicationHub.starttime;
	}

	static public void init(String deployment, String nodeid) {
		if (StringUtil.isNotEmpty(deployment))
			ApplicationHub.deployment = deployment;
		
		if (StringUtil.isNotEmpty(nodeid))
			ApplicationHub.setNodeId(nodeid);
	}
	
	public static boolean startServer(IWork configloader) {
		ApplicationHub.configloader = configloader;
		
		/*
		 * Basic hub features working
		 */
		
		ApplicationHub.minStart();
		
		CountDownLatch cdl  = new CountDownLatch(1);
		AtomicBoolean errflag = new AtomicBoolean();
		
		/*
		 * Load the configuration for the example
		 */
		Task task0 = Task.ofHubRoot()
				.withTitle("Start Server")
				.withTopic(WorkTopic.SYSTEM)
				.withNextId("APP")
				.withWork(
					ChainWork
						.of(configloader)
						.then(ApplicationHub::standardStartChain)
				)
				.withParams(RecordStruct.record()
					.with("Tier", ResourceHub.getTopResources())
				);
		
		TaskHub.submit(task0, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				errflag.set(task.hasExitErrors());
				cdl.countDown();
			}
		});
		
		/*
		 * wait for tasks to finish
		 */
		
		try {
			cdl.await();
		}
		catch (InterruptedException x) {
			// don't care
		}
		
		// mark server as running, state listeners will know what to do
		if (! errflag.get())
			ApplicationHub.setState(HubState.Running);
		
		return ! errflag.get();
	}
	
	public static void stopServer() {
		Logger.info("Stopping Application Server");
		
		/*
		 * Application shutdown tasks
		 */
		CountDownLatch cdl  = new CountDownLatch(1);
		
		IWork xw = ApplicationHub.standardStopChain();
		
		Task task0 = Task.ofHubRoot()
				.withTitle("Stop Server")
				.withTopic(WorkTopic.SYSTEM)
				.withNextId("APP")
				.withParams(RecordStruct.record()
						.with("OldTier", ResourceHub.getTopResources())
				)
				.withWork(xw);
		
		TaskHub.submit(task0, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				//System.out.println("Why!");
				cdl.countDown();
			}
		});
		
		/*
		 * wait for tasks to finish
		 */
		
		try {
			cdl.await();
		}
		catch (InterruptedException x) {
			// don't care
		}
		
		Logger.info("Application hub shutdown started");
		
		/*
		 * Basic hub features disabled - cannot run this inside a task
		 */
		
		ApplicationHub.minStop();
		
		Logger.info("Application hub shutdown completed");
	}
	
	public static void restartServer(OperationOutcomeStruct callback) throws OperatingContextException {
		/*
		 * Load the configuration for the example
		 */
		Task task0 = Task.ofSubtask("Restart Server", "APP")
				.withTopic(WorkTopic.SYSTEM)
				.withWork(ChainWork
						.of(configloader)
						.then(ApplicationHub::standardStartChain)
						.then(taskctx -> {
							ResourceTier tier = (ResourceTier) taskctx.getTask().getParamsAsRecord().getFieldAsAny("Tier");
							
							ResourceHub.setTopResources(tier);
							
							taskctx.returnEmpty();
						})
						.then(ApplicationHub::standardStopChain)
				)
				.withParams(RecordStruct.record()
						.with("Tier", ResourceTier.top())		// a new tier to load
						.with("OldTier", ResourceHub.getTopResources())	// old for cleanup
				);
		
		TaskHub.submit(task0, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				callback.returnEmpty();
			}
		});
	}
	
	public static void minStart() {
		/*
		 * For the scheduler we'll also need the application clock running
		 */
		ApplicationHub.getClock().minStart();
		
		/*
		 * Initialize the WorkHub as before.
		 */
		TaskHub.minStart();
	}
	
	public static void minStop() {
		/*
		 * not necessary but polite
		 */
		TaskHub.minStop();
		
		/*
		 * stop the application click, necessary for a clean exit
		 */
		ApplicationHub.getClock().minStop();
		
		// find and list any threads from our pools that linger beyond the shut down
		Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
		Thread[] threadArray = threadSet.toArray(new Thread[threadSet.size()]);
		
		for (Thread t : threadArray) {
			if ((t.getThreadGroup() == null) || !"main".equals(t.getThreadGroup().getName()))
				continue;
			
			boolean fnd = false;
			
			if (t.getName().startsWith("WorkPool"))
				fnd = true;
			
			if (fnd) {
				Logger.info("Lingering Thread: " + t.getName());
				
				StackTraceElement[] g = t.getStackTrace();
				
				if (g.length > 3) {
					Logger.info(" - " + g[0]);
					Logger.info(" - " + g[1]);
					Logger.info(" - " + g[2]);
					Logger.info(" - " + g[3]);
				}
			}
		}
	}
	
	// assumes config loader work has run first!
	public static IWork standardStartChain() {
		return ChainWork.of(HubStartBeginWork::new)
				.then(StandardTaskStart::new)
				.then(ControlWork.dieOnError("TaskHub did not start"))
				.then(DbStartWork::new)
				.then(ControlWork.dieOnError("DatabaseHub did not start"))
				.then(SqlStartWork::new)
				.then(ControlWork.dieOnError("SqlHub did not start"))
				.then(ScriptStartWork::new)
				.then(ControlWork.dieOnError("ScriptHub did not start"))
				.then(DepositStartWork::new)
				.then(ControlWork.dieOnError("DepositHub did not start"))
				.then(TenantStartWork::new)
				.then(ControlWork.dieOnError("TenantHub did not start"))
				.then(SessionStartWork::new)
				.then(ControlWork.dieOnError("SessionHub did not start"))
				.then(ServiceStartWork::new)
				.then(ControlWork.dieOnError("ServiceHub did not start"))
				.then(HubStartFinalWork::new);
	}
	
	public static IWork standardStopChain() {
		return ChainWork.of(HubStopBeginWork::new)
				.then(ServiceStopWork::new)
				.then(SessionStopWork::new)
				.then(TenantStopWork::new)
				.then(DepositStopWork::new)
				.then(ScriptStopWork::new)
				.then(SqlStopWork::new)
				.then(DbStopWork::new)
				.then(StandardTaskStop::new)
				.then(HubStopFinalWork::new);
	}

	// return null if not claimed
	static synchronized public String makeLocalClaim(CommonPath path, int timeout) {
		Claim fnd = ApplicationHub.claims.get(path);
		
		if ((fnd != null) && fnd.isClaimValid())
			return null;
		
		Claim claim = Claim.of(path, timeout);
		
		ApplicationHub.claims.put(path, claim);
		
		return claim.getId();
	}
	
	static synchronized public boolean updateLocalClaim(CommonPath path, int timeout, String claimid) {
		Claim fnd = ApplicationHub.claims.get(path);
		
		if (fnd != null)
			return fnd.updateClaim(claimid, timeout);
		
		// if our claim has been swept away then it is no longer valid
		
		return false;
	}
	
	static synchronized public boolean releaseLocalClaim(CommonPath path, String claimid) {
		Claim fnd = ApplicationHub.claims.remove(path);
		
		return  (fnd != null);
	}
	
	static public void subscribeToEvents(IEventSubscriber sub) {
		ApplicationHub.subscribers.add(sub);
	}
	
	static public void unsubscribeFromEvents(IEventSubscriber sub) {
		ApplicationHub.subscribers.remove(sub);
	}
	
	static public void fireEvent(Integer event, Object e) {
		if (Logger.isDebug())
			Logger.debug("Hub Event fired: " + event + " with " + e);		
		
		// to array to be thread safe
		for (IEventSubscriber sub : ApplicationHub.subscribers) {
			try {
				sub.eventFired(event, e);
			}
			catch (Exception x) {
				Logger.warn("Event subscriber threw an error: " + x);
			}
		}
	}
	
	static public XElement getCatalogSettings(String name) {
		return ApplicationHub.getCatalogSettings(name, null);
	}
	
	static public XElement getCatalogSettings(String name, String alternate) {
		XElement cat = ResourceHub.getResources().getConfig().getCatalog(name, alternate);
		
		if (cat != null)
			return cat.find("Settings");
		
		return null;
	}
	
	/* TODO
	static public String getLibraryPath(String libraryName, String alias) {
		if (Hub.libpaths == null) {
			Hub.libpaths = System.getProperty("java.class.path").split(";");
			
			// if this is UNIX/Linux then split on ':' instead
			if (Hub.libpaths.length == 1) 
				Hub.libpaths = System.getProperty("java.class.path").split(":");
		}
		
		String retpath = null;
		
		for (String path : Hub.libpaths) {
			if (path.contains(File.separatorChar + libraryName + ".jar")) {
				retpath = path;
				break;
			}
			
			if (path.contains(File.separatorChar + libraryName + File.separatorChar)) {
				retpath = path + "/";
				break;
			}
		}
		
		if (retpath == null) {
			try {
				// try some predictable places
				File proj = new File("./" + libraryName + "/bin");
				
				if (proj.exists()) 
					retpath = proj.getCanonicalPath() + "/";
				else {
					File jar = new File("./lib/" + libraryName + ".jar");
					
					if (jar.exists()) 
						retpath = jar.getCanonicalPath();
				}
			}
			catch (Exception x) {
				
			}
		}
		
		if (retpath != null)
			retpath = retpath.replace("\\", "/");
		
		return retpath;
	}
	*/

	/* resource hub/tier
	static public ApiSession createLocalApiSession(String domain) {
		IApiSessionFactory man = ApplicationHub.apimans.get("_local");
		
		if (man == null) {
			man = (IApiSessionFactory) ApplicationHub.getInstance("dcraft.api.LocalSessionFactory");
			ApplicationHub.apimans.put("_local", man);						
		}
		
		return man.create(XElement.tag("ApiSession").withAttribute("Domain", domain));
	}
	
	static public ApiSession createApiSession(String name) {
		IApiSessionFactory man = ApplicationHub.apimans.get(name);
		
		if (man == null) {
			ConfigResource config = ResourceHub.getResources().getConfig();

			for (XElement mel : config.getTagListDeep("ApiSession")) {
				if (mel.getAttribute("Name").equals(name)) {
					String cls = mel.getAttribute("Class");
					
					if (StringUtil.isEmpty(cls))
						break;
					
					man = (IApiSessionFactory) ApplicationHub.getInstance(cls);
				
					if (man != null) {
						man.init(mel);
						ApplicationHub.apimans.put(name, man);						
					}
					
					break;
				}
			}
		}
		
		if (man != null)
			return man.create();
		
		return null;
	}
	*/
	
	static public WatchKey registerFileWatcher(IFileWatcher fwatcher, Path path) {
		try {
			WatchKey watchID = path.register(ApplicationHub.watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			
			ApplicationHub.filewatchdata.put(watchID, fwatcher);
		    
		    //System.out.println("register - Watch key count: " + Hub.filewatchdata.size());
			
			return watchID;
		}
		catch (Exception x) {
			Logger.error("Hub file watcher error: " + x);
		}
		
		return null;
	}
	
	static public void unregisterFileWatcher(WatchKey key) {
		if (key == null)
			return;
		
		key.cancel();
		
		ApplicationHub.filewatchdata.remove(key);
	    
	    //System.out.println("unregister - Watch key count: " + Hub.filewatchdata.size());
	}
	
	// TODO add hub info/detail collector service
	// System.out.println("Boss Threads: " + Hub.getBossGroup().isShutdown() + " - " + Hub.getBossGroup().isShuttingDown() + " - " + Hub.getBossGroup().isTerminated());
	
	static public RecordStruct getVariables() {
		if (ApplicationHub.variables == null)
			ApplicationHub.variables = RecordStruct.record()
				.with("Id", nodeid)
				.with("Deployment", deployment)
				.with("Role", role)
				.with("RunId", runid)
				.with("IsProduction", isproduction)
		;
		
		
		return ApplicationHub.variables;
	}
}
