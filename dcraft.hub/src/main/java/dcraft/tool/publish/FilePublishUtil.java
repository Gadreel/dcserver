package dcraft.tool.publish;

import dcraft.filestore.CommonPath;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.StateWorkStep;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilePublishUtil {
	// relative to the "tenants" folder
	// must be running on the same deployment as the tenant file
	static public void reviewFileUpdate(CommonPath path, FilePublishHelper helper) throws OperatingContextException {
		/*
		String depoly = ApplicationHub.getDeployment();

		Path syncid = Paths.get("./deploy-" + depoly + "/nodes/"
				+ ApplicationHub.getNodeId() + "/cms-sync-local");

		if (Files.exists(syncid)) {
			CharSequence sid = IOUtil.readEntireFile(syncid);

			if (StringUtil.isNotEmpty(sid)) {
				this.lastsync = sid.toString().trim();
			}
		}

		if (StringUtil.isEmpty(this.lastsync)) {
			this.lastsync = "8f2a92f6e3a5c4f437c9692a0189f630809ac315";		// TODO remove someday, just needed until everyone sync's first time
		}

		try {
			Map<String, Map<String, Map<String, List<String>>>> pathindx = new HashMap<>();

			FileRepositoryBuilder builder = new FileRepositoryBuilder();

			Repository repository = builder
					.setGitDir(new File(".git"))
					.findGitDir() // scan up the file system tree
					.build();

			//System.out.println("You are on branch: " + repository.getBranch());

			if (! "master".equals(repository.getBranch())) {
				//System.out.println("Must be on Master branch to continue.");
				repository.close();
				return StateWorkStep.STOP;
			}

			RevWalk rw = new RevWalk(repository);
			ObjectId head1 = repository.resolve(org.eclipse.jgit.lib.Constants.HEAD);
			RevCommit commit1 = rw.parseCommit(head1);

			headsync = head1.name();

			ObjectId rev2 = repository.resolve(lastsync);
			RevCommit parent = rw.parseCommit(rev2);

			DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
			df.setRepository(repository);
			df.setDiffComparator(RawTextComparator.DEFAULT);
			df.setDetectRenames(true);

			// list oldest first or change types are all wrong!!
			List<DiffEntry> diffs = df.scan(parent.getTree(), commit1.getTree());

			for (DiffEntry diff : diffs) {
				String gnpath = diff.getNewPath();
				String gopath = diff.getOldPath();

				Path npath = Paths.get("./" + gnpath);
				Path opath = Paths.get("./" + gopath);

				if (diff.getChangeType() == DiffEntry.ChangeType.DELETE)
					npath = opath;

				// looking for feeds only
				if (npath.getNameCount() < 7) {
					continue;
				}

				String nfilename = npath.getName(1).toString();

				if (nfilename.equals("dcraft.hub") || nfilename.equals("dcraft.third") || nfilename.equals("dcraft.test") || nfilename.equals(".gitignore")
						|| nfilename.equals("matrix.xml")
				)
					continue;

				if (! nfilename.equals("deploy-" + depoly))
					continue;

				if (! npath.getName(2).toString().equals("tenants"))
					continue;

				String tenant = npath.getName(3).toString();
				String site = "root";
				String feed =  npath.getName(5).toString();
				String path = "/" + npath.getName(6).toString();

				if (npath.getNameCount() > 7)
					path = "/" + npath.subpath(6, npath.getNameCount()).toString().replace('\\', '/');

				if (npath.getName(4).toString().equals("sites")) {
					if (! npath.getName(6).toString().equals("feeds") || (npath.getNameCount() < 9))
						continue;

					site =  npath.getName(5).toString();
					feed =  npath.getName(7).toString();
					path = "/" + npath.getName(8).toString();

					if (npath.getNameCount() > 9)
						path = "/" + npath.subpath(8, npath.getNameCount()).toString().replace('\\', '/');
				}
				else if (! npath.getName(4).toString().equals("feeds")) {
					continue;
				}

				if (! pathindx.containsKey(tenant))
					pathindx.put(tenant, new HashMap<>());

				Map<String, Map<String, List<String>>> tenantindx = pathindx.get(tenant);

				if (! tenantindx.containsKey(site))
					tenantindx.put(site, new HashMap<>());

				Map<String, List<String>> feedindx = tenantindx.get(site);

				if (! feedindx.containsKey(feed))
					feedindx.put(feed, new ArrayList<>());

				List<String> lindx = feedindx.get(feed);

				if (lindx.contains(path))
					continue;

				if (diff.getChangeType() == DiffEntry.ChangeType.DELETE) {
					//System.out.println("delete - " + tenant + " - "  + site + " - "  + feed + " - " + path);

					lindx.add("-" + path);
				}
				else if ((diff.getChangeType() == DiffEntry.ChangeType.ADD) || (diff.getChangeType() == DiffEntry.ChangeType.MODIFY) || (diff.getChangeType() == DiffEntry.ChangeType.COPY)) {
					//System.out.println("mod - " + tenant + " - "  + site + " - "  + feed + " - " + path);

					lindx.add("+" + path);
				}
				else if (diff.getChangeType() == DiffEntry.ChangeType.RENAME) {
					//System.out.println("rename new - " + tenant + " - "  + site + " - "  + feed + " - " + path);

					lindx.add("+" + path);

					// TODO support sites too
					if (opath.getName(4).toString().equals("feeds")) {
						path = "/" + opath.getName(6).toString();

						if (opath.getNameCount() > 7)
							path = "/" + opath.subpath(6, opath.getNameCount()).toString().replace('\\', '/');

						//System.out.println("rename old - " + tenant + " - "  + site + " - "  + feed + " - " + path);

						lindx.add("-" + path);
					}
				}
			}

			rw.dispose();

			repository.close();

			// now also get the uncommitted files

			{

				Git git = Git.open(new File("."));

				try {
					Status status = git.status().call();

					List<Path> mods = new ArrayList<>();

					for (String modified : status.getModified()) {
						System.out.println("Modified file: " + modified);
						mods.add(Paths.get("./" + modified));
					}

					for (String modified : status.getAdded()) {
						System.out.println("Added file: " + modified);
						mods.add(Paths.get("./" + modified));
					}

					for (String modified : status.getUntracked()) {
						System.out.println("Untracked file: " + modified);
						mods.add(Paths.get("./" + modified));
					}

					for (Path npath : mods) {
						// looking for feeds only
						if (npath.getNameCount() < 7) {
							continue;
						}

						String nfilename = npath.getName(1).toString();

						if (nfilename.equals("dcraft.hub") || nfilename.equals("dcraft.third") || nfilename.equals("dcraft.test") || nfilename.equals(".gitignore")
								|| nfilename.equals("matrix.xml")
						)
							continue;

						if (! nfilename.equals("deploy-" + depoly))
							continue;

						if (! npath.getName(2).toString().equals("tenants"))
							continue;

						String tenant = npath.getName(3).toString();
						String site = "root";
						String feed = npath.getName(5).toString();
						String path = "/" + npath.getName(6).toString();

						if (npath.getNameCount() > 7)
							path = "/" + npath.subpath(6, npath.getNameCount()).toString().replace('\\', '/');

						if (npath.getName(4).toString().equals("sites")) {
							if (!npath.getName(6).toString().equals("feeds") || (npath.getNameCount() < 9))
								continue;

							site = npath.getName(5).toString();
							feed = npath.getName(7).toString();
							path = "/" + npath.getName(8).toString();

							if (npath.getNameCount() > 9)
								path = "/" + npath.subpath(8, npath.getNameCount()).toString().replace('\\', '/');
						}
						else if (! npath.getName(4).toString().equals("feeds")) {
							continue;
						}

						if (! pathindx.containsKey(tenant))
							pathindx.put(tenant, new HashMap<>());

						Map<String, Map<String, List<String>>> tenantindx = pathindx.get(tenant);

						if (! tenantindx.containsKey(site))
							tenantindx.put(site, new HashMap<>());

						Map<String, List<String>> feedindx = tenantindx.get(site);

						if (! feedindx.containsKey(feed))
							feedindx.put(feed, new ArrayList<>());

						List<String> lindx = feedindx.get(feed);

						if (lindx.contains(path))
							continue;

						lindx.add("+" + path);

						System.out.println("added: " + path);
					}

					/ * TODO
					for (String modified: status.getRemoved()) {
						System.out.println("Removed file: " + modified);
					}
					 * /
				}
				catch (NullPointerException x) {
					System.out.println("Git repo not found");
				}
				catch (GitAPIException x) {
					System.out.println("Git repo read error: " + x);
				}
				finally {
					if (git != null)
						git.close();
				}
			}

			for (Map.Entry<String, Map<String, Map<String, List<String>>>> tenant : pathindx.entrySet()) {
				for (Map.Entry<String, Map<String, List<String>>> site : tenant.getValue().entrySet()) {
					for (Map.Entry<String, List<String>> feed : site.getValue().entrySet()) {
						RecordStruct rec = RecordStruct.record()
								.with("Tenant", tenant.getKey())
								.with("Site", site.getKey())
								.with("Feed", feed.getKey())
								.with("Paths", ListStruct.list(feed.getValue()));

						tenants.addLast(rec);
					}
				}
			}

			return StateWorkStep.NEXT;
		}
		catch (IOException x) {
			Logger.error("Unable to read git repo for feed sync!");
			return StateWorkStep.STOP;
		}

		*/
	}
}
