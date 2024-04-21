package dcraft.util.git;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import dcraft.filestore.CommonPath;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class GitUtil {
	static public class TransfersResult {
		public List<TransferDesc> updates = new ArrayList<>();
		public List<TransferDesc> deletes = new ArrayList<>();
		public NoticeResult noticeResult = null;
	}

	static public class TransferDesc {
		static TransferDesc of(Path local) {
			TransferDesc transferDesc = new TransferDesc();
			transferDesc.local = local;
			transferDesc.remote = CommonPath.from(local.toString().substring(1).replace('\\', '/'));
			return transferDesc;
		}

		public Path local = null;
		public CommonPath remote = null;
	}

	static public TransfersResult processDiffTreeForTransfers(List<DiffEntry> diffs, String deploy) {
		TransfersResult result = new TransfersResult();

		result.noticeResult = GitUtil.processDiffTreeForNotices(diffs, deploy);

		for (DiffEntry diff : diffs) {
			if ((diff.getChangeType() == DiffEntry.ChangeType.DELETE) || (diff.getChangeType() == DiffEntry.ChangeType.RENAME))
			{
				Path opath = Paths.get("./" + diff.getOldPath());

				if (GitUtil.repoPathToDeployPath(opath, deploy, result.deletes)) {
					if (opath.getName(1).toString().equals("lib"))
						System.out.println("- *** MANUAL *** " + diff.getChangeType().name() + " - " + opath);
					else
						System.out.println("- " + diff.getChangeType().name() + " - " + opath);
				}
			}

			if ((diff.getChangeType() == DiffEntry.ChangeType.ADD) || (diff.getChangeType() == DiffEntry.ChangeType.MODIFY)
					|| (diff.getChangeType() == DiffEntry.ChangeType.COPY) || (diff.getChangeType() == DiffEntry.ChangeType.RENAME))
			{
				Path npath = Paths.get("./" + diff.getNewPath());

				if (GitUtil.repoPathToDeployPath(npath, deploy, result.updates)) {
					System.out.println("+ " + diff.getChangeType().name() + " - " + npath);
				}
			}
		}

		return result;
	}

	static public boolean repoPathToDeployPath(Path npath, String deployment, Collection<TransferDesc> list) {
		if (npath.getNameCount() < 1)
			return false;

		if (npath.getName(1).toString().equals("server.sh") || npath.getName(1).toString().equals("foreground.sh")) {
			list.add(TransferDesc.of(npath));
			return true;
		}

		if (npath.getNameCount() < 2)
			return false;

		String topfolder = npath.getName(1).toString();

		if (topfolder.equals("lib")) {
			TransferDesc transferDesc = new TransferDesc();
			transferDesc.local = npath;
			transferDesc.remote = CommonPath.from("/ext/" + npath.subpath(2, npath.getNameCount()));
			list.add(transferDesc);
			return true;
		}

		if (topfolder.equals("ext") || topfolder.equals("packages") || topfolder.equals("deploy-" + deployment)) {
			list.add(TransferDesc.of(npath));
			return true;
		}

		return false;
	}

	static public class NoticeResult {
		public ListStruct updates = ListStruct.list();
		public ListStruct deletes = ListStruct.list();

		// all the tenant aliases touched
		public Set<String> tenantalias = new HashSet<>();

		public void addNotices(NoticeResult... results) {
			for (NoticeResult result : results) {
				this.tenantalias.addAll(result.tenantalias);
				this.deletes.withCollection(result.deletes);
				this.updates.withCollection(result.updates);
			}
		}
	}

	static public NoticeResult processDiffTreeForNotices(List<DiffEntry> diffs, String deploy) {
		NoticeResult result = new NoticeResult();

		for (DiffEntry diff : diffs) {
			String gnpath = diff.getNewPath();
			String gopath = diff.getOldPath();

			Path npath = Paths.get("./" + gnpath);
			Path opath = Paths.get("./" + gopath);

			if (diff.getChangeType() == DiffEntry.ChangeType.DELETE) {
				GitUtil.repoPathToTenantPath(opath, deploy, result.deletes, result.tenantalias);
			}
			else if ((diff.getChangeType() == DiffEntry.ChangeType.ADD) || (diff.getChangeType() == DiffEntry.ChangeType.MODIFY) || (diff.getChangeType() == DiffEntry.ChangeType.COPY)) {
				GitUtil.repoPathToTenantPath(npath, deploy, result.updates, result.tenantalias);
			}
			else if (diff.getChangeType() == DiffEntry.ChangeType.RENAME) {
				GitUtil.repoPathToTenantPath(opath, deploy, result.deletes, result.tenantalias);
				GitUtil.repoPathToTenantPath(npath, deploy, result.updates, result.tenantalias);
			}
		}

		return result;
	}

	static public NoticeResult processCurrentForNotices(Status status, String deploy) {
		NoticeResult result = new NoticeResult();

		for (String modified : status.getModified()) {
			//System.out.println("Modified file: " + modified);
			GitUtil.repoPathToTenantPath(Paths.get("./" + modified), deploy, result.updates, result.tenantalias);
		}

		for (String modified : status.getChanged()) {
			//System.out.println("Modified file: " + modified);
			GitUtil.repoPathToTenantPath(Paths.get("./" + modified), deploy, result.updates, result.tenantalias);
		}

		for (String modified : status.getAdded()) {
			//System.out.println("Added file: " + modified);
			GitUtil.repoPathToTenantPath(Paths.get("./" + modified), deploy, result.updates, result.tenantalias);
		}

		for (String modified : status.getUntracked()) {
			//System.out.println("Untracked file: " + modified);
			GitUtil.repoPathToTenantPath(Paths.get("./" + modified), deploy, result.updates, result.tenantalias);
		}

		for (String modified: status.getRemoved()) {
			//System.out.println("Removed file: " + modified);
			GitUtil.repoPathToTenantPath(Paths.get("./" + modified), deploy, result.deletes, result.tenantalias);
		}

		for (String modified: status.getMissing()) {
			//System.out.println("Removed file: " + modified);
			GitUtil.repoPathToTenantPath(Paths.get("./" + modified), deploy, result.deletes, result.tenantalias);
		}

		return result;
	}

	static public void repoPathToTenantPath(Path npath, String deployment, ListStruct list, Set<String> tenantalias) {
		if (npath.getNameCount() < 4)
			return;

		if (! npath.getName(1).toString().equals("deploy-" + deployment))
			return;

		if (! npath.getName(2).toString().equals("tenants"))
			return;

		tenantalias.add(npath.getName(3).toString());

		String path = "/" + npath.subpath(3, npath.getNameCount());

		list.with(path);
	}
}
