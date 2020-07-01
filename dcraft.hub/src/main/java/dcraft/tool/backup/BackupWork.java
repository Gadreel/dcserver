package dcraft.tool.backup;

import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Main;
import dcraft.script.inst.Sleep;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.tool.certs.CertCheckWork;
import dcraft.tool.certs.RenewCertsWork;

public class BackupWork extends ChainWork {
	@Override
	protected void init(TaskContext taskctx) {
		Logger.info("Start nightly BATCH");
		
		Main sleeper = Main.tag();
		sleeper.with(Sleep.tag(20));
		
		this
				.then(new CounterWork())		// before all the activity
				.then(new DatabaseWork())
				.then(new LogsWork())
				.then(new RenewCertsWork())
				.then(									// wait for renews to finish before backup
						StackUtil.of(sleeper)
				)
				.then(new CertCheckWork())
				.then(new TempCleaner())
				.then(									// wait for deposits to finish before backup
						StackUtil.of(sleeper)
				)
				.then(new DailyAllTenants())
				.then(new BackupShellWork())
		;
	}
}
