package dcraft.tool.release;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import dcraft.filestore.CommonPath;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeString;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.service.portable.PortableMessageUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.git.GitUtil;
import dcraft.xml.XElement;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class PublishUpdatesWork extends StateWork {
	static public PublishUpdatesWork of(PublishHelper helper, boolean preview) {
		PublishUpdatesWork work = new PublishUpdatesWork();
		work.helper = helper;
		work.preview = preview;
		return work;
	}

	protected StateWorkStep collectCommitNumber = null;
	protected StateWorkStep collectFiles = null;
	protected StateWorkStep transfer = null;
	protected StateWorkStep notify = null;
	protected StateWorkStep setCommitNumber = null;
	protected StateWorkStep done = null;

	protected PublishHelper helper = null;
	protected boolean preview = true;
	protected GitUtil.TransfersResult transfersResult = null;

	protected String lastsync = null;
	protected String finalsync = null;
	protected String headsync = null;
	protected BaseStruct tparams = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.withSteps(
				this.collectCommitNumber = StateWorkStep.of("Collect the remote commit number", this::collectCommitNumber),
				this.collectFiles = StateWorkStep.of("Collect files to update", this::collectFiles),
				this.transfer = StateWorkStep.of("Update files", this::updatefiles),
				this.notify = StateWorkStep.of("Update files", this::notify),
				this.setCommitNumber = StateWorkStep.of("Update commit number", this::setCommitNumber),
				this.done = StateWorkStep.of("Finalize", this::done)
		);
		
		tparams = trun.getTask().getParams();
	}

	public StateWorkStep collectCommitNumber(TaskContext trun) throws OperatingContextException {
		this.lastsync = this.helper.lastCommit();

		if (this.lastsync == null) {
			Logger.error("Unable to collect latest commit number.");
			return this.done;
		}

		return StateWorkStep.NEXT;
	}

	public StateWorkStep collectFiles(TaskContext trun) throws OperatingContextException {
		String deploy = this.helper.getServer().getDeployment();

		try {
			FileRepositoryBuilder builder = new FileRepositoryBuilder();

			Repository repository = builder
					.setGitDir(new File(".git"))
					.findGitDir() // scan up the file system tree
					.build();

			System.out.println("You are on branch: " + repository.getBranch());

			if (! "master".equals(repository.getBranch()) && ! "main".equals(repository.getBranch())) {
				Logger.error("Must be on main (master) branch to continue.");
				repository.close();
				return this.done;
			}

			// get the latest commits (that we pulled)

			{
				RevWalk rw = new RevWalk(repository);
				ObjectId head1 = repository.resolve(org.eclipse.jgit.lib.Constants.HEAD);
				RevCommit commit1 = rw.parseCommit(head1);

				this.finalsync = head1.name();

				headsync = head1.name();

				if (StringUtil.isEmpty(lastsync))
					lastsync = headsync;

				ObjectId rev2 = repository.resolve(lastsync);
				RevCommit parent = rw.parseCommit(rev2);

				DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
				df.setRepository(repository);
				df.setDiffComparator(RawTextComparator.DEFAULT);
				df.setDetectRenames(true);

				// list oldest first or change types are all wrong!!
				List<DiffEntry> diffs = df.scan(parent.getTree(), commit1.getTree());

				this.transfersResult = GitUtil.processDiffTreeForTransfers(diffs, deploy);

				rw.dispose();

				repository.close();
			}

			return StateWorkStep.NEXT;
		}
		catch (IOException x) {
			Logger.error("Unable to read git repo for feed sync!");
			return this.done;
		}
	}

	public StateWorkStep updatefiles(TaskContext trun) throws OperatingContextException {
		if ((this.transfersResult.updates.size() == 0) && (this.transfersResult.deletes.size() == 0)) {
			System.out.println("No files to update or delete");
			return StateWorkStep.NEXT;
		}

		trun.touch();

		if (this.preview) {
			for (GitUtil.TransferDesc desc : this.transfersResult.updates) {
				System.out.println("update: " + desc.local);
			}

			for (GitUtil.TransferDesc desc : this.transfersResult.deletes) {
				System.out.println("delete: " + desc.local);
			}

			return StateWorkStep.NEXT;
		}

		try {
			helper.sftp.cd(helper.server.findDeployment().getAttribute("ServerPath", "/dcserver"));

			for (GitUtil.TransferDesc desc : this.transfersResult.updates) {
				System.out.println("update: " + desc.local);

				String rpath = desc.remote.toString().substring(1).replace('\\', '/');

				helper.server.makeDirSftp(helper.sftp, desc.remote.getParent());

				helper.sftp.put(desc.local.toString().replace('\\', '/'), rpath, ChannelSftp.OVERWRITE);
				helper.sftp.chmod(rpath.endsWith(".sh") ? 484 : 420, rpath);		// 644 octal = 420 dec, 744 octal = 484 dec

				trun.touch();
			}

			for (GitUtil.TransferDesc desc : this.transfersResult.deletes) {
				System.out.println("delete: " + desc.local);

				String rpath = desc.remote.toString().substring(1).replace('\\', '/');

				try {
					helper.sftp.stat(rpath);
				}
				catch (Exception x) {
					// ignore - we get an exception if file is not present
					continue;
				}

				helper.sftp.rm(rpath);

				trun.touch();
			}

			return StateWorkStep.NEXT;
		}
		catch (SftpException x) {
			System.out.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
			System.out.println("Sftp Error: " + x);
			System.out.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
		}

		return this.done;
	}

	public StateWorkStep notify(TaskContext trun) throws OperatingContextException {
		if ((this.transfersResult.noticeResult.updates.size() == 0) && (this.transfersResult.noticeResult.deletes.size() == 0))
			return StateWorkStep.NEXT;

		trun.touch();

		RecordStruct message = RecordStruct.record()
				.with("Op", "dcCoreServices.Management.ProcessTenantFileUpdates")
				.withConditional("Body", RecordStruct.record()
						.with("Updates", this.transfersResult.noticeResult.updates)
						.with("Deletes", this.transfersResult.noticeResult.deletes)
				);

		System.out.println("tenant files notice: " + message);

		if (this.preview)
			return StateWorkStep.NEXT;

		PortableMessageUtil.buildMessageForServiceQueue(message, this.helper.getServer().getDeployment(), "root", null, new OperationOutcomeString() {
			@Override
			public void callback(String result) throws OperatingContextException {
				if (StringUtil.isNotEmpty(result)) {
					CommonPath remotePath = CommonPath.from("/deploy-" + PublishUpdatesWork.this.helper.getServer().getDeployment() + "/nodes/"
						+ PublishUpdatesWork.this.helper.getServer().findDeployment().getAttribute("Id") + "/messages/triggers");

					StateWorkStep next = StateWorkStep.NEXT;

					try (InputStream in = new ByteArrayInputStream(Utf8Encoder.encode(result))) {
						if (!PublishUpdatesWork.this.helper.put(in, remotePath.resolve(PortableMessageUtil.buildServiceQueueFilename())))
							next = PublishUpdatesWork.this.done;
					}
					catch (IOException x) {
						Logger.error("Unable to write service queue trigger");
						next = PublishUpdatesWork.this.done;
					}

					PublishUpdatesWork.this.transition(trun, next);
				}
			}
		});

		return StateWorkStep.WAIT;
	}

	public StateWorkStep setCommitNumber(TaskContext trun) throws OperatingContextException {
		if (this.preview)
			return StateWorkStep.NEXT;

		if (! this.helper.setCommit(this.finalsync)) {
			Logger.error("Unable to update sync file remotely.");
			return this.done;
		}

		RecordStruct cmd1 = RecordStruct.record()
				.with("Op", "dcCoreServices.Management.ReloadTenants")
				.with("Body", ListStruct.list(this.transfersResult.noticeResult.tenantalias));

		RecordStruct cmd2 = RecordStruct.record()
				.with("Op", "dcCoreServices.Management.CheckServiceQueue");

		SshHelper ph = SshHelper.of(this.helper.server);

		if (ph == null) {
			System.out.println("Bad publisher");
		}
		else {
			try {
				ph.connect();

				ph.exec("cd /dcserver && ./server.sh '" + cmd1 + "'");

				// wait for tenant reload
				Thread.sleep(1000);

				ph.exec("cd /dcserver && ./server.sh '" + cmd2+ "'");
			}
			catch (InterruptedException e) {
				Logger.error("Skipped final server notice");
			}
			finally {
				ph.disconnect();
			}
		}

		return StateWorkStep.NEXT;
	}

	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		this.helper.disconnect();

		System.out.println("Publish task completed");

		return StateWorkStep.STOP;
	}
}
