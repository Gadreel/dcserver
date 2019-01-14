package dcraft.tool.backup;

import dcraft.log.Logger;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;

public class BackupWork extends ChainWork {
	@Override
	protected void init(TaskContext taskctx) {
		Logger.info("Start nightly BATCH");
		
		this
				.then(new CounterWork())		// before all the activity
				.then(new DatabaseWork())
				.then(new LogsWork())
				.then(new CertCheckWork())
				.then(new BackupShellWork())
		;
	}
}
