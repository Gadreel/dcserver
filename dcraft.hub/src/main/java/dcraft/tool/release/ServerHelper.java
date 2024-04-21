package dcraft.tool.release;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import dcraft.filestore.CommonPath;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.ignite.IServerHelper;
import dcraft.log.Logger;
import dcraft.util.StringUtil;
import dcraft.util.SysUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

public class ServerHelper implements IServerHelper {
	protected JSch jsch = new JSch();
	protected XElement matrix = null;
	protected String deployment = null;
	protected XElement hostconfig = null;
	protected XElement devconfig = null;
	
	public String getDeployment() {
		return this.deployment;
	}

	public XElement getMatrix() {
		return this.matrix;
	}

	public boolean init() {
		this.matrix = XmlReader.loadFile(Paths.get("./matrix.xml"), false, true);

		if (matrix == null)
			return false;

		return true;
	}

	public boolean init(String deployment) {
		this.deployment = deployment;
		
		try {
			this.matrix = XmlReader.loadFile(Paths.get("./matrix.xml"), false,true);
			
			if (matrix == null)
				return false;
			
			hostconfig = this.findDeployment(deployment);
			
			if (hostconfig == null) {
				Logger.error("Missing deployment in matrix");
				return false;
			}
			
			devconfig = this.findDeveloper(ApplicationHub.getNodeId());
			
			if (devconfig == null) {
				Logger.error("Missing developer in matrix");
				return false;
			}
		}
		catch (Exception x) {
			System.out.println("Error initializing SSH session: " + x);
			return false;
		}
		
		return true;
	}

	public Session openSession() {
		try {
			XElement ssh = devconfig.selectFirst("SSH");
			
			String hostname = hostconfig.getAttribute("Host");
			String username = hostconfig.getAttribute("User");
			String password = hostconfig.getAttribute("Password");
			String keyfile = ssh.getAttribute(SysUtil.isWindows() ? "WinKeyFile" : "KeyFile");
			String pubkeyfile = ssh.getAttribute(SysUtil.isWindows() ? "PublicWinKeyFile" : "PublicKeyFile");
			String passphrase = ssh.getAttribute("Passphrase");
			
			int port = (int) StringUtil.parseInt(hostconfig.getAttribute("Port"), 22);
			
			if (StringUtil.isNotEmpty(password))
				password = ApplicationHub.getClock().getObfuscator().decryptHexToString(password);
			
			String passwordx = password;
			
			if (StringUtil.isNotEmpty(passphrase))
				passphrase = ApplicationHub.getClock().getObfuscator().decryptHexToString(passphrase);
			
			if (StringUtil.isNotEmpty(keyfile))
				this.jsch.addIdentity(keyfile, pubkeyfile, Utf8Encoder.encode(passphrase));
			
			Session session = this.jsch.getSession(username, hostname, port);
			
			if (StringUtil.isNotEmpty(password))
				session.setPassword(password);
			
			session.setUserInfo(new UserInfo() {
				@Override
				public void showMessage(String message) {
					System.out.println("SSH session message: " + message);
				}
				
				@Override
				public boolean promptYesNo(String message) {
					return true;
				}
				
				@Override
				public boolean promptPassword(String message) {
					return false;
				}
				
				@Override
				public boolean promptPassphrase(String message) {
					return false;
				}
				
				@Override
				public String getPassword() {
					return passwordx;
				}
				
				@Override
				public String getPassphrase() {
					return null;
				}
			});
			
			session.connect(30000); // making a connection with timeout.
			session.setTimeout(20000);   // 20 second read timeout

			System.out.println("ssh session open: " + session);

			return session;
		}
		catch (Exception x) {
			System.out.println("Error initializing SSH session: " + x);
		}
		
		return null;
	}

	public boolean put(ChannelSftp sftp, InputStream in, CommonPath dest) {
		try (in) {
			this.makeDirSftp(sftp, dest.getParent());
			sftp.put(in, dest.toString().substring(1));
			return true;
		}
		catch (Exception x) {
			System.out.println("Sftp Error: " + x);
		}

		return false;
	}

	public boolean makeDirSftp(ChannelSftp sftp, CommonPath path) {
		//System.out.println("mkdir: " + path +  "  ------   " + path.getNameCount());
		
		// path "." should be there
		if (path.getNameCount() < 2)
			return true;
		
		//System.out.println("checking");

		String remotepath = path.toString().substring(1).replace('\\', '/');
		
		try {
		    sftp.stat(remotepath);
		    return true;		// path is there 
		} 
		catch (Exception x) {
		}
		
		this.makeDirSftp(sftp, path.getParent());
		
		try {
			sftp.mkdir(remotepath);
    		sftp.chmod(493, remotepath);		// 755 octal = 493 dec
		} 
		catch (Exception x) {
			System.out.println("Failed to create directory: " + x);
			return false;
		}
		
		return true;
	}

	// intended to have a ./ before path
	public boolean makeDirSftp(ChannelSftp sftp, Path path) {
		System.out.println("mkdir: " + path +  "  ------   " + path.getNameCount());

		// path "." should be there
		if (path.getNameCount() < 2)
			return true;

		//System.out.println("checking");

		try {
		    sftp.stat(path.toString().replace('\\', '/'));
		    return true;		// path is there
		}
		catch (Exception x) {
		}

		this.makeDirSftp(sftp, path.getParent());

		try {
			sftp.mkdir(path.toString().replace('\\', '/'));
    		sftp.chmod(493, path.toString().replace('\\', '/'));		// 755 octal = 493 dec
		}
		catch (Exception x) {
			System.out.println("Failed to create directory: " + x);
			return false;
		}

		return true;
	}

	public XElement findDeployment() {
		return this.hostconfig;
	}
	
	public XElement findDeployment(String name) {
		for (XElement hostel : matrix.selectAll("Deployment")) {
			if (name.equals(hostel.attr("Alias"))) {
				return hostel.selectFirst("Node");			// TODO currently only support one production node, first - enhance to support multiple
			}
		}
		
		return null;
	}
	
	public XElement findDeveloper(String nodeid) {
		for (XElement devel : matrix.selectAll("DeveloperNodes/Node")) {
			if (nodeid.equals(devel.attr("Id"))) {
				return devel;
			}
		}
		
		return null;
	}
}