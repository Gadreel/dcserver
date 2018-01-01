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

import dcraft.util.TimeUtil;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

import dcraft.hub.ResourceHub;
import dcraft.util.HexUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

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
public class HubLog {
    static protected DebugLevel globalLevel = DebugLevel.Info;
    static protected boolean debugEnabled = false;
    static protected Long maxLogSize = null;
    static protected File logfile = null;
    
    // typically task logging is handled by a service on the bus, but on occasions
    // we want it to log to the file as well, from settings change this to 'true' 
    static protected boolean toFile = true;
    static protected boolean toConsole = true;
    
    static protected PrintWriter logWriter = null;
    static protected ReentrantLock writeLock = new ReentrantLock();  
    static protected long filestart = 0;
    static protected long filelinecnt = 0;
    
    static protected ILogHandler handler = null;
    
    static protected XElement config = null;
    
    static public DebugLevel getGlobalLevel() {
        return HubLog.globalLevel; 
    }
    
    static public void setGlobalLevel(DebugLevel v) {
        HubLog.globalLevel = v; 
        
        HubLog.debugEnabled = ((v == DebugLevel.Trace) || (v == DebugLevel.Debug));
    }
    
    static public void setLogHandler(ILogHandler v) {
    	HubLog.handler = v;
    }
    
    static public void setToConsole(boolean v) {
    	HubLog.toConsole = v;
    }
    
    /*
     * return true if debugging is even an option on this setup, 
     * if not this saves a lot of overhead on the Logger.debug and Logger.trace calls
     */
    static public boolean getDebugEnabled() {
    	return HubLog.debugEnabled;
    }
   
    /**
     * Called from Hub.start this method configures the logging features.
     * 
     * @param config xml holding the configuration
     */
    static public boolean init(XElement config) {
    	HubLog.config = config;
    	
    	// TODO load levels, path etc
    	// include a setting for startup logging - if present set the TC log level directly
    	
		HubLog.startNewLogFile();
    	
		// set by operation context init 
    	//Logger.locale = LocaleUtil.getDefaultLocale();
		
		// From here on we can use netty and so we need the logger setup
		
		InternalLoggerFactory.setDefaultFactory(new dcraft.log.netty.LoggerFactory());
    	
    	if (HubLog.config != null) {
    		// set by operation context init 
    		//if (Logger.config.hasAttribute("Level"))
    	    //	Logger.globalLevel = DebugLevel.parse(Logger.config.getAttribute("Level"));

			if (HubLog.config.hasAttribute("Level")) 
				HubLog.setGlobalLevel(DebugLevel.parse(HubLog.config.getAttribute("Level")));

			if (HubLog.config.hasAttribute("EnableDebugger")) 
				HubLog.debugEnabled = HubLog.config.getAttributeAsBooleanOrFalse("EnableDebugger");
    		
    		if (HubLog.config.hasAttribute("NettyLevel")) {
    			ResourceLeakDetector.setLevel(Level.valueOf(HubLog.config.getAttribute("NettyLevel")));
    			
    			Logger.debug("Netty Level set to: " + ResourceLeakDetector.getLevel());    			
    		}
    		else if (! "none".equals(System.getenv("dcnet"))) {
    			// TODO anything more we should do here?  maybe paranoid isn't helpful?
    		}

			if (HubLog.config.hasAttribute("MaxLogSize")) {
				HubLog.maxLogSize = StringUtil.parseInt(HubLog.config.getAttribute("MaxLogSize"));
				
				if (HubLog.maxLogSize != null)
					HubLog.maxLogSize *= 1024 * 1024;		
			}
			
    		// set by operation context init 
    		//if (Logger.config.hasAttribute("Locale"))
    	    //	Logger.locale = Logger.config.getAttribute("Locale");
    	}
    	
    	return true;
    }
    
    static protected void startNewLogFile() {
    	try {
    		HubLog.logfile = new File("./logs/" 
					+ DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmmss").format(ZonedDateTime.now(ZoneId.of("UTC")))
					+ ".log"); 
    		
    		if (!HubLog.logfile.getParentFile().exists())
    			if (!HubLog.logfile.getParentFile().mkdirs())
    				Logger.error("Unable to create logs folder.");
    		
    		HubLog.logfile.createNewFile();

    		if (HubLog.logWriter != null) {
    			HubLog.logWriter.flush();
    			HubLog.logWriter.close();
    		}
    		
    		Logger.trace("Opening log file: " + HubLog.logfile.getCanonicalPath());
    		
			HubLog.logWriter = new PrintWriter(HubLog.logfile, "utf-8");
			
			HubLog.filestart = System.currentTimeMillis();
			HubLog.filelinecnt = 0;
		} 
    	catch (Exception x) {
    		Logger.error("Unable to create log file: " + x);
		}
    }
    
    /*
     *  In a distributed setup, DivConq may route logging to certain Hubs and
     *  bypass the local log file.  During shutdown logging returns to local
     *  log file so that the dcBus can shutdown and stop routing the messages.
     * @param or 
     */
    static public void stop() {
    	HubLog.toFile = true;		// go back to logging to file
    }
    
    /*
     * Insert a (string) translated message into the log
     * 
     * @param ctx context for log settings, null for none
     * @param level message level
     * @param code to translate
     * @param params for the translation
     */
    static public void logTr(String opid, DebugLevel level, long code, Object... params) {
    	// do not log, is being filtered
    	if (HubLog.globalLevel.getCode() < level.getCode())
    		return;
    	
    	HubLog.logWr(opid, level, code, ResourceHub.trSys("_code_" + code, params));
    }
    
    /*
     * Insert a (string) message into the log
     * 
     * @param ctx context for log settings, null for none
     * @param level message level
     * @param message text to store in log
     * @param tags searchable values associated with the message, key-value pairs can be created by putting two tags adjacent
     */
    static public void log(String opid, DebugLevel level, long code, String message, String... tags) {
    	// do not log, is being filtered
    	if (HubLog.globalLevel.getCode() < level.getCode())
    		return;
    	
    	HubLog.logWr(opid, level, code, message, tags);
    }

    /*
     * Insert a (string) translated message into the log
     * 
     * @param ctx context for log settings, null for none
     * @param level message level
     * @param code to translate
     * @param params for the translation
     */
    /*
    static public void logWr(String opid, DebugLevel level, long code, Object... params) {
    	HubLog.logWr(opid, level, code, Tr.tr("_code_" + code, params));
    }
    */
    
    /*
     * don't check, just write
     *  
     * @param taskid
     * @param level
     * @param message
     * @param tags
     */
    static public void logWr(String opid, DebugLevel level, long code, String message, String... tags) {
    	String indicate = "M" + level.getIndicator();

    	// write to file if not a Task or if File Tasks is flagged
    	if (HubLog.toFile || HubLog.toConsole) {
    		if (message != null)
    			message = message.replace("\n", "\n\t");		// tab sub-lines
	
	        HubLog.write(opid, indicate, code, message, tags);
    	}
    }

    /*
     * A boundary delineates in section of a task log from another, making it
     * easier for a log viewer to organize the content.  Boundary's are treated
     * like "info" messages, if only errors or warnings are being logged then 
     * the boundary entry will be skipped.
     *  
     * @param ctx context for log settings, null for none
     * @param tags searchable values associated with the message, key-value pairs can be created by putting two tags adjacent
     */
    static public void boundary(String opid, long code, String... tags) {
    	// do not log, is being filtered
    	if (HubLog.globalLevel.getCode() < DebugLevel.Info.getCode())
    		return;
    	
    	HubLog.boundaryWr(opid, code, tags);
    }
    
    /*
     * Don't check, just write 
     * 
     * @param taskid
     * @param tags
     */
    static public void boundaryWr(String opid, long code, String... tags) {
        HubLog.write(opid, "B  ", code, "", tags);
    }

    /*
     * Insert a chunk of hex encoded memory into the log
     * 
     * @param ctx context for log settings, null for none
     * @param level message level
     * @param data memory to hex encode and store
     * @param tags searchable values associated with the message, key-value pairs can be created by putting two tags adjacent
     */
    static public void log(String opid, DebugLevel level, long code, Memory data, String... tags) {
    	// do not log, is being filtered
    	if (HubLog.globalLevel.getCode() < level.getCode())
    		return;
    	
    	String indicate = "H" + level.getIndicator();

    	// write to file if not a Task or if File Tasks is flagged
    	if (HubLog.toFile || HubLog.toConsole) 
	        HubLog.write(opid, indicate, code, HexUtil.bufferToHex(data), tags);
    }
    
    static protected void write(String opid, String indicator, long code, String message, String... tags) {
    	if (opid == null)
    		opid = "000000000000000";
    	
    	ZonedDateTime occur = ZonedDateTime.now(ZoneId.of("UTC"));
    	String tagvalue = "";
    	
		// don't record 0, 1 or 2 - no generic codes
		if (code > 2) 
			tagvalue = "|Code|" + code + "|";
    	
		if ((tags != null) && tags.length > 0) {
			if (StringUtil.isEmpty(tagvalue))
				tagvalue = "|";

	        for (String tag : tags) 
	        	tagvalue += tag + "|";
		}
  
		String occurout = TimeUtil.stampFmt.format(occur);
		
		if (HubLog.handler != null)
			HubLog.handler.write(occurout, opid, indicator, tagvalue, message);
		
		if (tagvalue.length() > 0)
			tagvalue += " ";
		
        HubLog.write(occurout  + " " + opid + " " + indicator + " " + tagvalue +  message);
    }
    
    static protected void write(String msg) {
    	if (HubLog.toConsole)
    		System.out.println(msg);
    	
    	if (!HubLog.toFile || (HubLog.logWriter == null))
    		return;
    	
    	HubLog.writeLock.lock();
    	
    	HubLog.filelinecnt++;
    	
    	// if there is a max size, check every 1000 msgs if we have exceeded the max
    	if ((HubLog.maxLogSize != null) && (HubLog.filelinecnt % 1000 == 0) && (HubLog.logfile.length() > HubLog.maxLogSize))
    		HubLog.startNewLogFile();
    	
    	// start a new log file every 24 hours
    	if (System.currentTimeMillis() - HubLog.filestart > 86400000)
    		HubLog.startNewLogFile();
    	
        try {
        	HubLog.logWriter.println(msg);
        	HubLog.logWriter.flush();
        }
        catch (Exception x) {
            // ignore, logger is broken  
        }
        
        HubLog.writeLock.unlock();
    }
}
