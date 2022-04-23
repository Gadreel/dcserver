package dcraft.tool.release;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.chars.Utf8Decoder;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;
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
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PublishHelper {
	static public PublishHelper of(ServerHelper server) {
		PublishHelper publishHelper = new PublishHelper();
		publishHelper.server = server;
		return publishHelper;
	}
	
	protected ServerHelper server = null;
	protected Session session = null;
	protected ChannelSftp sftp = null;
	
	public boolean connect() {
		if (sftp != null)
			return false;
		
		try {
			session = server.openSession();
			
			Channel channel = session.openChannel("sftp");
			channel.connect();
			sftp = (ChannelSftp) channel;
			return true;
		}
		catch (JSchException x) {
			System.out.println("Sftp Error: " + x);
		}
		
		return false;
	}
	
	public void disconnect() {
		if (sftp != null) {
			if (sftp.isConnected())
				sftp.exit();
			
			sftp = null;
			
			session.disconnect();
		}
	}
	
	public String lastCommit() {
		XElement deploymentconfig = server.findDeployment();
		
		try {
			if (! this.connect())
				return null;
			
			// go to routines folder
			sftp.cd(deploymentconfig.getAttribute("ServerPath", "/dcserver"));
			
			return lastSync();
		}
		catch (Exception x) {
			System.out.println("Sftp Error: " + x);
		}
		finally {
			this.disconnect();
		}
		
		return null;
	}
	
	public void setCommit(String commit) {
		XElement deploymentconfig = server.findDeployment();
		
		try {
			if (! this.connect())
				return;
			
			// go to routines folder
			sftp.cd(deploymentconfig.getAttribute("ServerPath", "/dcserver"));
			
			try (InputStream in = new ByteArrayInputStream(Utf8Encoder.encode(commit))) {
				sftp.put(in, "sync");
			}
		}
		catch (Exception x) {
			System.out.println("Sftp Error: " + x);
		}
		finally {
			this.disconnect();
		}
	}
	
	protected String lastSync() throws Exception {
		String lastsync = null;
		
		// get the latest published commit id, e.g. 5c26e5af62607cbcbb15c6fb5f09b941122e3f61
		try (InputStream in = sftp.get("sync")) {
			byte[] bb = new byte[40];
			
			in.read(bb, 0, 40);
			
			lastsync = Utf8Decoder.decode(bb).toString();
			
			if (lastsync.length() != 40)
				throw new Exception("Unable to read the *sync* file correctly.");
		}
		
		System.out.println("Last Sync: " + lastsync);
		
		return lastsync;
	}
	
	public void publish(boolean preview) throws Exception {
		XElement deploymentconfig = server.findDeployment();
		
		try {
			if (! this.connect())
				return;
			
			// go to routines folder
			sftp.cd(deploymentconfig.getAttribute("ServerPath", "/dcserver"));
			
			FileRepositoryBuilder builder = new FileRepositoryBuilder();
			
			Repository repository = builder
					.setGitDir(new File(".git"))
					.findGitDir() // scan up the file system tree
					.build();
			
			System.out.println("You are on branch: " + repository.getBranch());
			
			if (! "master".equals(repository.getBranch())) {
				System.out.println("Must be on Master branch to continue.");
				repository.close();
				return;
			}
			
			String lastsync = this.lastSync();
			
			RevWalk rw = new RevWalk(repository);
			ObjectId head1 = repository.resolve(org.eclipse.jgit.lib.Constants.HEAD);
			RevCommit commit1 = rw.parseCommit(head1);
			
			//releases.getData("AWWWServer").setField("LastCommitSync", head1.name());
			
			ObjectId rev2 = repository.resolve(lastsync);
			RevCommit parent = rw.parseCommit(rev2);
			//RevCommit parent2 = rw.parseCommit(parent.getParent(0).getId());
			
			DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
			df.setRepository(repository);
			df.setDiffComparator(RawTextComparator.DEFAULT);
			df.setDetectRenames(true);
			
			Set<String> aliasesupdated = new HashSet<>();
			
			// list oldest first or change types are all wrong!!
			List<DiffEntry> diffs = df.scan(parent.getTree(), commit1.getTree());
			
			for (DiffEntry diff : diffs) {
				String gnpath = diff.getNewPath();
				String gopath = diff.getOldPath();
				
				Path npath = Paths.get("./" + gnpath);
				Path opath = Paths.get("./" + gopath);
				
				String nfilename = npath.getName(1).toString();
				
				if (nfilename.equals("dcraft.hub") || nfilename.equals("dcraft.third") || nfilename.equals("dcraft.test") || nfilename.equals(".gitignore")
						|| nfilename.equals("matrix.xml") || nfilename.equals("docs")
				)
					continue;
				
				if (diff.getChangeType() == DiffEntry.ChangeType.DELETE) {
					if (this.checkUpdateFile(opath, server.getDeployment())) {  // inst.containsPathExtended(opath)) {
						String oalias = this.extractAlias(npath);  // inst.containsPathExtendedAlias(opath);
						
						if (oalias != null)
							aliasesupdated.add(oalias);
						
						if (opath.getName(1).toString().equals("lib")) {
							System.out.println("- *** MANUAL *** " + diff.getChangeType().name() + " - " + opath);
							continue;
						}
						
						System.out.println("- " + diff.getChangeType().name() + " - " + opath);
						
						try {
							if (!preview) {
								sftp.rm(opath.toString().replace('\\', '/'));
								
								System.out.println("deleted!!");
							}
						}
						catch (SftpException x) {
							System.out.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
							System.out.println("Sftp Error: " + x);
							System.out.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
						}
					}
					else if (Logger.isDebug()) {
						//System.out.println("/ " + diff.getChangeType().name() + " - " + gopath + " !!!!!!!!!!!!!!!!!!!!!!!!!");
					}
				}
				else if ((diff.getChangeType() == DiffEntry.ChangeType.ADD) || (diff.getChangeType() == DiffEntry.ChangeType.MODIFY) || (diff.getChangeType() == DiffEntry.ChangeType.COPY)) {
					if (this.checkUpdateFile(npath, server.getDeployment())) {  // inst.containsPathExtended(npath)) {
						String nalias = this.extractAlias(npath);  // inst.containsPathExtendedAlias(npath);
						
						if (nalias != null)
							aliasesupdated.add(nalias);
						
						System.out.println("+ " + diff.getChangeType().name() + " - " + npath);
						
						Path dpath = npath;
						
						if (npath.getName(1).toString().equals("lib"))
							dpath = Paths.get("./ext").resolve(npath.subpath(2, npath.getNameCount()));
						
						try {
							if (!preview) {
								server.makeDirSftp(sftp, dpath.getParent());
								
								sftp.put(npath.toString().replace('\\', '/'), dpath.toString().replace('\\', '/'), ChannelSftp.OVERWRITE);
								sftp.chmod(npath.getFileName().toString().endsWith(".sh") ? 484 : 420, dpath.toString().replace('\\', '/'));		// 644 octal = 420 dec, 744 octal = 484 dec
								
								System.out.println("uploaded!!");
							}
						}
						catch (SftpException x) {
							System.out.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
							System.out.println("Sftp Error: " + x);
							System.out.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
						}
					}
					else if (Logger.isDebug()) {
						//System.out.println("> " + diff.getChangeType().name() + " - " + gnpath + " !!!!!!!!!!!!!!!!!!!!!!!!!");
					}
				}
				else if (diff.getChangeType() == DiffEntry.ChangeType.RENAME) {
					// remove the old
					if (this.checkUpdateFile(opath, server.getDeployment())) {  // inst.containsPathExtended(opath)) {
						String oalias = this.extractAlias(npath);  // inst.containsPathExtendedAlias(opath);
						
						if (oalias != null)
							aliasesupdated.add(oalias);
						
						System.out.println("- " + diff.getChangeType().name() + " - " + opath);
						
						try {
							if (!preview) {
								sftp.rm(opath.toString().replace('\\', '/'));
								
								System.out.println("deleted!!");
							}
						}
						catch (SftpException x) {
							System.out.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
							System.out.println("Sftp Error: " + x);
							System.out.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
						}
					}
					else if (Logger.isDebug()) {
						//System.out.println("/ " + diff.getChangeType().name() + " - " + gopath + " !!!!!!!!!!!!!!!!!!!!!!!!!");
					}
					
					// add the new path
					if (this.checkUpdateFile(npath, server.getDeployment())) {  // inst.containsPathExtended(npath)) {
						String nalias = this.extractAlias(npath);  // inst.containsPathExtendedAlias(npath);
						
						if (nalias != null)
							aliasesupdated.add(nalias);
						
						System.out.println("+ " + diff.getChangeType().name() + " - " + npath);
						
						try {
							if (!preview) {
								server.makeDirSftp(sftp, npath.getParent());
								
								sftp.put(npath.toString().replace('\\', '/'), npath.toString().replace('\\', '/'), ChannelSftp.OVERWRITE);
								sftp.chmod(npath.endsWith(".sh") ? 484 : 420, npath.toString().replace('\\', '/'));		// 644 octal = 420 dec, 744 octal = 484 dec
								
								System.out.println("uploaded!!");
							}
						}
						catch (SftpException x) {
							System.out.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
							System.out.println("Sftp Error: " + x);
							System.out.println(" !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
						}
					}
					else if (Logger.isDebug()) {
						//System.out.println("> " + diff.getChangeType().name() + " - " + gnpath + " !!!!!!!!!!!!!!!!!!!!!!!!!");
					}
				}
				else {
					System.out.println("??????????????????????????????????????????????????????????");
					System.out.println(": " + diff.getChangeType().name() + " - " + gnpath + " ?????????????????????????");
					System.out.println("??????????????????????????????????????????????????????????");
				}
			}
			
			rw.dispose();
			
			repository.close();
			
			if (!preview) {
				//releases.saveData();
				try (InputStream in = new ByteArrayInputStream(Utf8Encoder.encode(head1.name()))) {
					sftp.put(in, "sync");
				}
				
				// TODO update the queue concept
				RecordStruct cmd = RecordStruct.record()
						.with("Op", "dcCoreServices.Management.ReloadTenants")
						.with("Body", ListStruct.list(aliasesupdated));

				SshHelper ph = SshHelper.of(this.server);

				if (ph == null) {
					System.out.println("Bad publisher");
				}
				else {
					try {
						ph.connect();

						ph.exec("cd /dcserver && ./server.sh '" + cmd + "'");
					}
					finally {
						ph.disconnect();
					}
				}

					/*
				for (String alias : aliasesupdated) {
					try (InputStream in = new ByteArrayInputStream(Utf8Encoder.encode("REFRESH=" + alias))) {
						sftp.put(in, "public/dcw/_fqueue/CMD-" + alias, ChannelSftp.OVERWRITE);
						
						System.out.println("::: public/dcw/_fqueue/CMD-" + alias);
					}
				}
				 */

			}
		}
		catch (JSchException x) {
			System.out.println("Sftp Error: " + x);
		}
		finally {
			this.disconnect();
		}
	}
	
	public boolean checkUpdateFile(Path path, String deployment) {
		//System.out.println("0 - " + path.getName(0).toString());
		//System.out.println("1 - " + path.getName(1).toString());
		String pathname = path.getName(1).toString();
		
		if (pathname.equals("dcraft.hub"))
			return false;
		
		if (pathname.startsWith("deploy-") && ! pathname.endsWith(deployment))
			return false;
		
		return true;
	}
	
	public String extractAlias(Path path) {
		// okay not to check deploy name since this will only be called on files for the current deployment
		if (path.getName(1).toString().startsWith("deploy-") && path.getName(2).toString().equals("tenants"))
			return path.getName(3).toString();

		return null;
	}
}
