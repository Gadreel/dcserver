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
package dcraft.log;

import dcraft.hub.op.OperationContext;

/**
 * When logging messages to the debug log each message has a debug level.
 * The logger has a filter level and messages of lower priority than the 
 * current debug level will not be logged.
 * 
 * Note that 99% of the time the "current" debug level is determined by
 * the current TaskContext.  The preferred way to log messages is through 
 * the TaskContext or through an OperationResult.  Ultimately a filter
 * is used to determine what should go in the log.  
 * 
 * In fact, when you call "void error(String message, String... tags)"
 * and other logging methods, theses methods will lookup the current
 * task context.  So it is more efficient to work directly with task
 * context, however, occasional calls to these global logger methods
 * are fine.
 * 
 * @author Andy
 *
 */
public class Logger {
    static public boolean isDebug() {
    	// fast fail if debugging not enabled on Hub
    	if (! HubLog.debugEnabled)
    		return false;
    	
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	DebugLevel setlevel = (ctx != null) ? ctx.getController().getLevel() : HubLog.globalLevel;
    	
    	return (setlevel.getCode() >= DebugLevel.Debug.getCode());
    }

    static public boolean isTrace() {
    	// fast fail if debugging not enabled on Hub
    	if (! HubLog.debugEnabled)
    		return false;
    	
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	DebugLevel setlevel = (ctx != null) ? ctx.getController().getLevel() : HubLog.globalLevel;
    	
    	return (setlevel.getCode() >= DebugLevel.Trace.getCode());
    }
    
    /*
     * Insert a (string) message into the log
     * 
     * @param message error text
     * @param tags searchable values associated with the message, key-value pairs can be created by putting two tags adjacent
     */
    static public void error(String message, String... tags) {
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.log(DebugLevel.Error, 1, message, tags);
    		HubLog.log(ctx.getController().getSeqNumber(), DebugLevel.Error, 1, message, tags);
    	}
    	else {
    		HubLog.log(null, DebugLevel.Error, 1, message, tags);
    	}
    }
    
    static public void error(long code, String message, String... tags) {
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.log(DebugLevel.Error, code, message, tags);
    		HubLog.log(ctx.getController().getSeqNumber(), DebugLevel.Error, code, message, tags);
    	}
    	else {
    		HubLog.log(null, DebugLevel.Error, code, message, tags);
    	}
    }
    
    /*
     * Insert a (string) message into the log
     * 
     * @param message warning text
     * @param tags searchable values associated with the message, key-value pairs can be created by putting two tags adjacent
     */
    static public void warn(String message, String... tags) {
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.log(DebugLevel.Warn, 2, message, tags);
    		HubLog.log(ctx.getController().getSeqNumber(), DebugLevel.Warn, 2, message, tags);
    	}
    	else {
    		HubLog.log(null, DebugLevel.Warn, 2, message, tags);
    	}
    }
    
    static public void warn(long code, String message, String... tags) {
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.log(DebugLevel.Warn, code, message, tags);
    		HubLog.log(ctx.getController().getSeqNumber(), DebugLevel.Warn, code, message, tags);
    	}
    	else {
    		HubLog.log(null, DebugLevel.Warn, code, message, tags);
    	}
    }
    
    /*
     * Insert a (string) message into the log
     * 
     * @param message info text
     * @param tags searchable values associated with the message, key-value pairs can be created by putting two tags adjacent
     */
    static public void info(String message, String... tags) {
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.log(DebugLevel.Info, 0, message, tags);
    		HubLog.log(ctx.getController().getSeqNumber(), DebugLevel.Info, 0, message, tags);
    	}
    	else {
    		HubLog.log(null, DebugLevel.Info, 0, message, tags);
    	}
    }
    
    static public void info(long code, String message, String... tags) {
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.log(DebugLevel.Info, code, message, tags);
    		HubLog.log(ctx.getController().getSeqNumber(), DebugLevel.Info, code, message, tags);
    	}
    	else {
    		HubLog.log(null, DebugLevel.Info, code, message, tags);
    	}
    }
    
    /*
     * Insert a (string) message into the log
     * 
     * @param accessCode to translate
     * @param locals for the translation
     */
    static public void debug(String message, String... tags) {
    	// fast fail if debugging not enabled on Hub
    	if (! HubLog.debugEnabled)
    		return;
    	
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.log(DebugLevel.Debug, 0, message, tags);
    		HubLog.log(ctx.getController().getSeqNumber(), DebugLevel.Debug, 0, message, tags);
    	}
    	else {
    		HubLog.log(null, DebugLevel.Debug, 0, message, tags);
    	}
    }
    
    static public void debug(long code, String message, String... tags) {
    	// fast fail if debugging not enabled on Hub
    	if (! HubLog.debugEnabled)
    		return;
    	
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.log(DebugLevel.Debug, code, message, tags);
    		HubLog.log(ctx.getController().getSeqNumber(), DebugLevel.Debug, code, message, tags);
    	}
    	else {
    		HubLog.log(null, DebugLevel.Debug, code, message, tags);
    	}
    }
    
    /*
     * Insert a (string) message into the log
     * 
     * @param accessCode to translate
     * @param locals for the translation
     */
    static public void trace(String message, String... tags) {
    	// fast fail if debugging not enabled on Hub
    	if (! HubLog.debugEnabled)
    		return;
    	
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.log(DebugLevel.Trace, 0, message, tags);
    		HubLog.log(ctx.getController().getSeqNumber(), DebugLevel.Trace, 0, message, tags);
    	}
    	else {
    		HubLog.log(null, DebugLevel.Trace, 0, message, tags);
    	}
    }
    
    static public void trace(long code, String message, String... tags) {
    	// fast fail if debugging not enabled on Hub
    	if (! HubLog.debugEnabled)
    		return;
    	
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.log(DebugLevel.Trace, code, message, tags);
    		HubLog.log(ctx.getController().getSeqNumber(), DebugLevel.Trace, code, message, tags);
    	}
    	else {
    		HubLog.log(null, DebugLevel.Trace, code, message, tags);
    	}
    }
    
    /*
     * Insert a (string) message into the log
     * 
     * @param code to translate
     * @param params for the translation
     */
    static public void errorTr(long code, Object... params) {
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.logTr(DebugLevel.Error, code, params);
    		HubLog.logTr(ctx.getController().getSeqNumber(), DebugLevel.Error, code, params);
    	}
    	else {
    		HubLog.logTr(null, DebugLevel.Error, code, params);
    	}
    }
    
    /*
     * Insert a (string) message into the log
     * 
     * @param code to translate
     * @param params for the translation
     */
    static public void warnTr(long code, Object... params) {
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.logTr(DebugLevel.Warn, code, params);
    		HubLog.logTr(ctx.getController().getSeqNumber(), DebugLevel.Warn, code, params);
    	}
    	else {
    		HubLog.logTr(null, DebugLevel.Warn, code, params);
    	}
    }
    
    /*
     * Insert a (string) message into the log
     * 
     * @param code to translate
     * @param params for the translation
     */
    static public void infoTr(long code, Object... params) {
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.logTr(DebugLevel.Info, code, params);
			HubLog.logTr(ctx.getController().getSeqNumber(), DebugLevel.Info, code, params);
		}
		else {
			HubLog.logTr(null, DebugLevel.Info, code, params);
		}
    }
    
    /*
     * Insert a (string) message into the log
     * 
     * @param code to translate
     * @param params for the translation
     */
    static public void debugTr(long code, Object... params) {
    	// fast fail if debugging not enabled on Hub
    	if (!HubLog.debugEnabled)
    		return;
    	
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.logTr(DebugLevel.Debug, code, params);
    		HubLog.logTr(ctx.getController().getSeqNumber(), DebugLevel.Debug, code, params);
    	}
    	else {
    		HubLog.logTr(null, DebugLevel.Debug, code, params);
    	}
    }
    
    /*
     * Insert a (string) message into the log
     * 
     * @param code to translate
     * @param params for the translation
     */
    static public void traceTr(long code, Object... params) {
    	// fast fail if debugging not enabled on Hub
    	if (!HubLog.debugEnabled)
    		return;
    	
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.logTr(DebugLevel.Trace, code, params);
    		HubLog.logTr(ctx.getController().getSeqNumber(), DebugLevel.Trace, code, params);
    	}
    	else {
    		HubLog.logTr(null, DebugLevel.Trace, code, params);
    	}
    }
    
    static public void boundary(String... tags) {
    	OperationContext ctx = OperationContext.getOrNull();
    	
    	if (ctx != null) {
    		ctx.boundary(tags);
   			HubLog.boundary(ctx.getController().getSeqNumber(), 0, tags);
    	}
    	else {
			HubLog.boundary(null, 0, tags);
    	}
    }
    
    
}
