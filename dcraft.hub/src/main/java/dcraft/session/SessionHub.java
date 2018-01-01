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

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import dcraft.service.IService;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.clock.ISystemWork;
import dcraft.hub.clock.SysReporter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class SessionHub {
	static public String nextSessionId() {
		  return new BigInteger(130, RndUtil.random).toString(32);
	}	
	
	static protected ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
	
	static public Collection<Session> list() {
		return SessionHub.sessions.values();
	}
	
	static public void loadCleanup() {
		// remember that sys workers should not use OperationContext
		ISystemWork sessioncleanup = new ISystemWork() {
			@Override
			public void run(SysReporter reporter) {
				reporter.setStatus("Reviewing session plans");
				
				if (!ApplicationHub.isStopping()) {
					// guest sessions only last 1 minute, users 5 minutes
					long clearGuest = System.currentTimeMillis() - (75 * 1000);		// TODO configure - 1 minute, 15 secs
					long clearUser = System.currentTimeMillis() - (195 * 1000);		// TODO config - 3 minutes, 15 secs 
					
					for (Session sess : SessionHub.sessions.values()) {
						if (! sess.reviewPlan(clearGuest, clearUser)) {
							SessionHub.sessions.remove(sess.getId());
							
							Logger.info("Killing inactive session: " + sess.getId());
							
							sess.end();
						}
					}
				}
				
				reporter.setStatus("After reviewing session plans");
			}

			@Override
			public int period() {
				return 60;	// TODO configure?
			}
		};
		
		ApplicationHub.getClock().addSlowSystemWorker(sessioncleanup);	
	}
	
	static public void register(Session sess) {
		SessionHub.sessions.put(sess.getId(), sess);
		
		Logger.info("Session registered", "SessId", sess.getId());
	}

	static public Session lookup(String sessionid) {
		if (StringUtil.isEmpty(sessionid))
			return null;
		
		return SessionHub.sessions.get(sessionid);
	}
	
	static public void terminate(String id) {
		if (StringUtil.isEmpty(id))
			return;

		Session s = SessionHub.sessions.remove(id);

		if (s != null)
			s.end();
	}
}
