package dcraft.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import dcraft.hub.app.ApplicationHub;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.log.count.CountHub;

public class SessionUtil {

	static public void recordCounters() {
		long totalTasks = 0;
		long totalIncompleteTasks = 0;
		
		Collection<Session> lsessions = SessionHub.sessions.values();
		HashMap<String,Long> tagcount = new HashMap<>();
		
		for (Session sess : lsessions) {
			totalTasks += SessionUtil.countTasks(sess);
			totalIncompleteTasks += SessionUtil.countIncompleteTasks(sess);
			
			SessionUtil.countTags(sess, tagcount);
		}
		
		CountHub.allocateSetNumberCounter("dcSessionCount", lsessions.size());
		CountHub.allocateSetNumberCounter("dcSessionTaskCount", totalTasks);
		CountHub.allocateSetNumberCounter("dcSessionTaskIncompleteCount", totalIncompleteTasks);
		
		for (Entry<String, Long> tagentity : tagcount.entrySet())
			CountHub.allocateSetNumberCounter("dcSessionTag_" + tagentity.getKey() + "_Count", tagentity.getValue());
	}

	static public List<TaskContext> collectTasks(String... tags) {
		List<TaskContext> matches = new ArrayList<TaskContext>();
		
		for (Session sess : SessionHub.sessions.values()) 
			SessionUtil.collectTasks(sess, matches, tags);
		
		return matches;
	}
	
	static public int countTasks(String... tags) {
		int num = 0;
		
		for (Session sess : SessionHub.sessions.values()) 
			num += SessionUtil.countTasks(sess, tags);
		
		return num;
	}
	
	static public int countIncompleteTasks(String... tags) {
		int num = 0;
		
		for (Session sess : SessionHub.sessions.values()) 
			num += SessionUtil.countIncompleteTasks(sess, tags);
		
		return num;
	}

	// collect all tasks, filter by tags if any
	static public void collectTasks(Session sess, List<TaskContext> bucket, String... tags) {
		for (TaskContext task : sess.tasks.values()) 
			if ((tags.length == 0) || task.getTask().isTagged(tags))
				bucket.add(task);
	}

	static public void countTags(Session sess, Map<String, Long> tagcount) {
		for (TaskContext task : sess.tasks.values()) {
			ListStruct tags = task.getTask().getTags();
			
			if ((tags == null) || (tags.size() == 0)) {
				long cnt = tagcount.containsKey("[none]") ? tagcount.get("[none]") : 0;
				
				cnt++;
				
				tagcount.put("[none]", cnt);
			}
			else {
				for (BaseStruct stag : tags.items()) {
					String tag = stag.toString();
					
					long cnt = tagcount.containsKey(tag) ? tagcount.get(tag) : 0;
					
					cnt++;
					
					tagcount.put(tag, cnt);
				}
			}
		}
	}

	// count all tasks, filter by tags if any
	static public int countTasks(Session sess, String... tags) {
		int num = 0;
		
		for (TaskContext task : sess.tasks.values()) 
			if ((tags.length == 0) || task.getTask().isTagged(tags))
				num++;
		
		return num;
	}

	// count all tasks, filter by tags if any
	static public int countIncompleteTasks(Session sess, String... tags) {
		int num = 0;
		
		for (TaskContext task : sess.tasks.values()) 
			if (!task.isComplete() && ((tags.length == 0) || task.getTask().isTagged(tags)))
				num++;
		
		return num;
	}
	
	static public RecordStruct toStatusReport(Session sess) {
		RecordStruct rec = new RecordStruct();
		
		rec.with("Id", sess.id);
		rec.with("Key", sess.key);
		
		if (sess.lastAccess != 0)
			rec.with("LastAccess", TimeUtil.stampFmt.format(Instant.ofEpochMilli(sess.lastAccess)));
		
		if (sess.user != null)
			rec.with("UserContext", sess.user);
		
		if (sess.level != null)
			rec.with("DebugLevel", sess.level.toString());
		
		if (StringUtil.isNotEmpty(sess.originalOrigin))
			rec.with("Origin", sess.originalOrigin);
		
		ListStruct tasks = new ListStruct();
		
		for (TaskContext t : sess.tasks.values())
			tasks.withItem(t.toStatusReport());
		
		rec.with("Tasks", tasks);
		
		return rec;
	}
	
}
