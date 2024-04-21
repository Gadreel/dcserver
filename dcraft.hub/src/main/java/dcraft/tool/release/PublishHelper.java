package dcraft.tool.release;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import dcraft.filestore.CommonPath;
import dcraft.log.Logger;
import dcraft.service.portable.PortableMessageUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.chars.CharUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.git.GitUtil;
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

	public ServerHelper getServer() {
		return this.server;
	}

	public Session getSession() {
		return this.session;
	}

	public ChannelSftp getSftp() {
		return this.sftp;
	}

	public boolean connect() {
		if (sftp != null)
			return true;
		
		try {
			session = server.openSession();

			System.out.println("ssh session open: " + session);
			
			Channel channel = session.openChannel("sftp");
			channel.connect();
			sftp = (ChannelSftp) channel;

			System.out.println("ssh channel open: " + channel.getId());

			return true;
		}
		catch (JSchException x) {
			System.out.println("Sftp Error: " + x);
		}
		
		return false;
	}
	
	public void disconnect() {
		if (sftp != null) {

			System.out.println("ssh channel exit: " + sftp.getId());

			if (sftp.isConnected())
				sftp.exit();
			
			sftp = null;

			System.out.println("ssh session disconnect: " + session);

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

		return null;
	}
	
	public boolean setCommit(String commit) {
		XElement deploymentconfig = server.findDeployment();
		
		try {
			if (! this.connect())
				return false;
			
			// go to routines folder
			sftp.cd(deploymentconfig.getAttribute("ServerPath", "/dcserver"));
			
			try (InputStream in = new ByteArrayInputStream(Utf8Encoder.encode(commit))) {
				sftp.put(in, "sync");
			}

			return true;
		}
		catch (Exception x) {
			System.out.println("Sftp Error: " + x);
		}

		return false;
	}
	
	protected String lastSync() throws Exception {
		String lastsync = null;
		
		// get the latest published commit id, e.g. 5c26e5af62607cbcbb15c6fb5f09b941122e3f61
		try (InputStream in = sftp.get("sync")) {
			byte[] bb = new byte[40];
			
			in.read(bb, 0, 40);
			
			lastsync = CharUtil.decode(bb);
			
			if (lastsync.length() != 40)
				throw new Exception("Unable to read the *sync* file correctly.");
		}
		
		System.out.println("Last Sync: " + lastsync);
		
		return lastsync;
	}

	public boolean put(InputStream in, CommonPath dest) {
		XElement deploymentconfig = server.findDeployment();

		try {
			if (! this.connect())
				return false;

			// go to routines folder
			sftp.cd(deploymentconfig.getAttribute("ServerPath", "/dcserver"));

			if (this.server.put(sftp, in, dest))
				return true;
		}
		catch (Exception x) {
			System.out.println("Sftp Error: " + x);
		}

		return false;
	}

	public PublishUpdatesWork toWork(boolean preview) {
		return PublishUpdatesWork.of(this, preview);
	}
}
