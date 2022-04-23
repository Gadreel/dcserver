package dcraft.tool.release;

import com.jcraft.jsch.*;
import dcraft.log.Logger;
import dcraft.util.StringBuilder32;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Decoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

public class SshHelper {
	static public SshHelper of(ServerHelper server) {
		SshHelper publishHelper = new SshHelper();
		publishHelper.server = server;
		return publishHelper;
	}
	
	protected ServerHelper server = null;
	protected Session session = null;

	/*
	protected ChannelShell shell = null;

	public ChannelShell getShell() {
		return this.shell;
	}

	 */

	public boolean connect() {
		if (session != null)
			return false;

		try {
			session = server.openSession();

			/*
			Channel channel = session.openChannel("shell");
			//channel.setInputStream();
			//channel.setOutputStream();
			channel.connect();
			shell = (ChannelShell) channel;
			*
			 */
			return true;
		}
		catch (Exception x) {
			System.out.println("SSH Error: " + x);
		}
		
		return false;
	}
	
	public void disconnect() {
		/*
		if (shell != null) {
			if (shell.isConnected())
				shell.disconnect();

			shell = null;
			
			session.disconnect();
		}*/

		if (session != null) {
			session.disconnect();
			session = null;
		}
	}

	public String exec(String command) {
		Logger.trace("ssh exec in: " + command);

		try {
			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);
			channel.setInputStream(null);
			((ChannelExec) channel).setErrStream(System.err);
			InputStream in = channel.getInputStream();

			channel.connect();

			StringBuilder32 sb = new StringBuilder32();

			byte[] tmp = new byte[1024];

			while (true) {
				while (in.available() > 0) {
					int i = in.read(tmp, 0, 1024);

					if (i < 0)
						break;

					//System.out.print(new String(tmp, 0, i));

					sb.append(Utf8Decoder.decode(tmp, i));
				}

				if (channel.isClosed()) {
					//System.out.println("exit-status: " + channel.getExitStatus());
					break;
				}

				try {
					Thread.sleep(250);
				}
				catch (Exception ee) {
				}
			}

			channel.disconnect();

			Logger.trace("ssh exec out: " + sb.toString());

			return sb.toString();
		}
		catch (Exception x) {
			Logger.error("Unable to remote execute: " + x);
			return null;
		}
	}

	// None, Update, Reboot
	public String checkLinuxUpdateStatus() {
		boolean security = false;

		Logger.info("Checking for software updates");

		String out = this.exec("sudo yum check-update");

		try (BufferedReader rdr = new BufferedReader(new StringReader(out))) {
			String line = rdr.readLine();

			while (line != null) {
				if (line.startsWith("Loaded plugins:") || StringUtil.isEmpty(line)) {
					// nothing
				}
				else if (line.contains("|")) {
					Logger.info("Checking Repo: " + line.substring(0, line.indexOf(' ')));
				}
				else if (line.startsWith("Security:")) {
					security = true;
					Logger.info("Reboot may be needed: " + line);
				}
				else {
					Logger.info("Software updates found");
					return "Update";
				}

				line = rdr.readLine();
			}
		}
		catch (IOException x) {
			Logger.error("Unable to check Linux status: " + x);
		}

		return security ? "Reboot" : "None";
	}

	public void reboot() {
		this.exec("sudo reboot");
		this.disconnect();
	}

	public void runLinuxUpdate() {
		Logger.info("Updating software");

		String out = this.exec("sudo yum -y update");

		int state = 0;		// 0 = ignore, 1 = installed, 2 = updated

		try (BufferedReader rdr = new BufferedReader(new StringReader(out))) {
			String line = rdr.readLine();

			while (line != null) {
				if (StringUtil.isEmpty(line)) {
					// nothing
				}
				else if (line.startsWith("Installed:")) {
					state = 1;
				}
				else if (line.startsWith("Updated:")) {
					state = 2;
				}
				else if (state == 1) {
					Logger.info("Installed: " + line);
				}
				else if (state == 2) {
					Logger.info("updated: " + line);
				}

				line = rdr.readLine();
			}

			Logger.info("Software updates completed");
		}
		catch (IOException x) {
			Logger.error("Unable to update Linux software: " + x);
		}
	}

	public void checkInstallPackage(String packagename) {
		if (! this.checkLinuxPackage(packagename)) {
			if (! this.installLinuxPackage(packagename) || ! this.checkLinuxPackage(packagename)) {
				Logger.error("Package " + packagename + " is not installed.");
				return;
			}
		}
	}

	public boolean checkLinuxPackage(String packagename) {
		Logger.info("Check package installed: " + packagename);

		String out = this.exec("sudo yum list installed " + packagename);

		try (BufferedReader rdr = new BufferedReader(new StringReader(out))) {
			String line = rdr.readLine();

			while (line != null) {
				if (line.startsWith(packagename)) {
					Logger.info("Package is present");
					return true;
				}

				line = rdr.readLine();
			}

			Logger.info("Package is not present");
		}
		catch (IOException x) {
			Logger.error("Unable to find Linux package: " + x);
		}

		return false;
	}

	public boolean installLinuxPackage(String packagename) {
		Logger.info("Install package: " + packagename);

		String out = this.exec("sudo yum -y install " + packagename);

		try (BufferedReader rdr = new BufferedReader(new StringReader(out))) {
			String line = rdr.readLine();

			while (line != null) {
				if (line.startsWith("Complete!")) {
					Logger.info("Package is installed");
					return true;
				}

				line = rdr.readLine();
			}

			Logger.error("Package is NOT installed");
		}
		catch (IOException x) {
			Logger.error("Unable to install Linux package: " + x);
		}

		return false;
	}

	public void setWebServerVars(String deployment, String hubid) {
		/*
		export JAVA_HOME=/usr/lib/jvm
		export DC_NAME="dcServer"
		export DC_DEPLOYMENT="gei"
		export DC_NODE="01200"
		export DC_USER="ec2-user"
		 */

		Logger.info("Check web server vars: " + deployment + " - " + hubid);

		String out = this.exec("cat ~/.bashrc");

		boolean fndjava = false;
		boolean fndname = false;
		boolean fnddeployment = false;
		boolean fndnode = false;
		boolean fnduser = false;

		try (BufferedReader rdr = new BufferedReader(new StringReader(out))) {
			String line = rdr.readLine();

			while (line != null) {
				if (line.contains("JAVA_HOME")) {
					Logger.info("JAVA_HOME present");
					fndjava = true;
				}
				else if (line.contains("DC_NAME")) {
					Logger.info("DC_NAME present");
					fndname = true;
				}
				else if (line.contains("DC_DEPLOYMENT")) {
					Logger.info("DC_DEPLOYMENT present");
					fnddeployment = true;
				}
				else if (line.contains("DC_NODE")) {
					Logger.info("DC_NODE present");
					fndnode = true;
				}
				else if (line.contains("DC_USER")) {
					Logger.info("DC_USER present");
					fnduser = true;
				}

				line = rdr.readLine();
			}

			if (! fndjava) {
				Logger.info("JAVA_HOME is not present, adding");

				if (this.executeDone("echo \"export JAVA_HOME=/usr/lib/jvm\" >> ~/.bashrc"))
					Logger.info("JAVA_HOME added");
				else
					Logger.error("JAVA_HOME NOT added");
			}

			if (! fndname) {
				Logger.info("DC_NAME is not present, adding");

				if (this.executeDone("echo \"export DC_NAME=dcServer\" >> ~/.bashrc"))
					Logger.info("DC_NAME added");
				else
					Logger.error("DC_NAME NOT added");
			}

			if (! fnddeployment) {
				Logger.info("DC_DEPLOYMENT is not present, adding");

				if (this.executeDone("echo \"export DC_DEPLOYMENT=" + deployment + "\" >> ~/.bashrc"))
					Logger.info("DC_DEPLOYMENT added");
				else
					Logger.error("DC_DEPLOYMENT NOT added");
			}

			if (! fndnode) {
				Logger.info("DC_NODE is not present, adding");

				if (this.executeDone("echo \"export DC_NODE=" + hubid + "\" >> ~/.bashrc"))
					Logger.info("DC_NODE added");
				else
					Logger.error("DC_NODE NOT added");
			}

			if (! fnduser) {
				Logger.info("DC_USER is not present, adding");

				if (this.executeDone("echo \"export DC_USER=ec2-user\" >> ~/.bashrc"))
					Logger.info("DC_USER added");
				else
					Logger.error("DC_USER NOT added");
			}

		}
		catch (IOException x) {
			Logger.error("Unable to find web server vars: " + x);
		}
	}

	public void setWebPorts(String insecure, String secure) {
		// sudo iptables -t nat -L

		Logger.info("Check web ports: " + insecure + " - " + secure);

		String out = this.exec("sudo iptables -t nat -L");
		boolean fndinsecure = false;
		boolean fndsecure = false;

		try (BufferedReader rdr = new BufferedReader(new StringReader(out))) {
			String line = rdr.readLine();

			while (line != null) {
				if (line.contains("http redir ports " + insecure)) {
					Logger.info("Insecure port present");
					fndinsecure = true;
				}
				else if (line.contains("https redir ports " + secure)) {
					Logger.info("Secure port present");
					fndsecure = true;
				}

				line = rdr.readLine();
			}

			if (! fndinsecure) {
				Logger.info("Insecure is not present, adding");

				if (this.executeDone("sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port " + insecure + " && echo \"DONE\""))
					Logger.info("Insecure added");
				else
					Logger.error("Insecure NOT added");
			}

			if (! fndsecure) {
				Logger.info("Secure is not present, adding");

				if (this.executeDone("sudo iptables -t nat -A PREROUTING -p tcp --dport 443 -j REDIRECT --to-port " + secure + " && echo \"DONE\""))
					Logger.info("Secure added");
				else
					Logger.error("Secure NOT added");
			}
		}
		catch (IOException x) {
			Logger.error("Unable to find web ports: " + x);
		}
	}

	public void prepDisk(String diskname, String path) {
		Logger.info("Preparing disk: " + diskname + " - " + path);

		if (! this.checkLinuxDiskExists(diskname)) {
			Logger.error("Disk " + diskname + " does not exist.");
			return;
		}

		if (! this.checkLinuxDiskInitialized(diskname)) {
			if (! this.initLinuxDisk(diskname) || ! this.checkLinuxDiskInitialized(diskname)) {
				Logger.error("Disk " + diskname + " is not ready, error initializing.");
				return;
			}
		}

		if (! this.checkLinuxFolderExists(path)) {
			if (! this.makeLinuxFolder(path) || ! this.checkLinuxFolderExists(path)) {
				Logger.error("Path " + path + " does not exist, error creating.");
				return;
			}
		}

		if (! this.checkLinuxDiskMounted(diskname, path)) {
			if (! this.mountLinuxDisk(diskname, path) || ! this.checkLinuxDiskMounted(diskname, path)) {
				Logger.error("Disk mount " + diskname + " : " + path + " does not exist, error creating.");
				return;
			}
		}

		Logger.info("Finished disk prep.");
	}

	public boolean checkLinuxFolderExists(String path) {
		Logger.info("Check path exists: " + path);

		if (this.executeDone("[ -d " + path + " ]")) {
			Logger.info("Path is present");
			return true;
		}

		Logger.info("Path is not present");
		return false;
	}

	public boolean makeLinuxFolder(String path) {
		Logger.info("Creating path: " + path);

		if (this.executeDone("sudo mkdir " + path)) {
			Logger.info("Path is created");
			return true;
		}

		Logger.error("Path is NOT created");
		return false;
	}

	public boolean checkLinuxDiskExists(String diskname) {
		Logger.info("Check disk is present: " + diskname);

		String out = this.exec("sudo lsblk");

		try (BufferedReader rdr = new BufferedReader(new StringReader(out))) {
			String line = rdr.readLine();

			while (line != null) {
				if (line.startsWith(diskname)) {
					Logger.info("Disk is present");
					return true;
				}

				line = rdr.readLine();
			}

			Logger.info("Disk is not present");
		}
		catch (IOException x) {
			Logger.error("Unable to find Linux disk: " + x);
		}

		return false;
	}

	public boolean checkLinuxDiskMounted(String diskname, String path) {
		Logger.info("Check that disk is mounted: " + diskname + " - " + path);

		String out = this.exec("sudo lsblk");

		try (BufferedReader rdr = new BufferedReader(new StringReader(out))) {
			String line = rdr.readLine();

			while (line != null) {
				if (line.startsWith(diskname) && line.contains(path)) {
					Logger.info("Disk is mounted");
					return true;
				}

				line = rdr.readLine();
			}

			Logger.info("Disk is not mounted");
		}
		catch (IOException x) {
			Logger.error("Unable to find Linux disk mount: " + x);
		}

		return false;
	}

	public boolean checkLinuxDiskInitialized(String diskname) {
		Logger.info("Check that disk is initialized: " + diskname);

		String out = this.exec("sudo blkid /dev/" + diskname);

		try (BufferedReader rdr = new BufferedReader(new StringReader(out))) {
			String line = rdr.readLine();

			while (line != null) {
				if (line.length() > 0) {
					Logger.info("Disk is initialized");
					return true;
				}

				line = rdr.readLine();
			}

			Logger.info("Disk is not initialized");
		}
		catch (IOException x) {
			Logger.error("Unable to find Linux disk init: " + x);
		}

		return false;
	}

	public boolean mountLinuxDisk(String diskname, String path) {
		Logger.info("Mounting disk " + diskname + " with " + path);

		if (this.executeDone("sudo mount /dev/" + diskname + " " + path)) {
			Logger.info("Disk mounted");

			Logger.info("Setting path ownership: " + path);

			if (this.executeDone("sudo chown -R ec2-user:ec2-user " + path)) {
				Logger.info("Ownership is set");
				return true;
			}

			Logger.error("Ownership is NOT set");
		}
		else  {
			Logger.error("Disk is NOT mounted");
		}

		return false;
	}

	public boolean initLinuxDisk(String diskname) {
		Logger.info("Init disk: " + diskname);

		String out = this.exec("sudo mke2fs -F -t ext4 /dev/" + diskname);

		try (BufferedReader rdr = new BufferedReader(new StringReader(out))) {
			String line = rdr.readLine();

			while (line != null) {
				if (line.startsWith("Writing superblocks")) {
					Logger.info("Disk is initialized");
					return true;
				}

				line = rdr.readLine();
			}

			Logger.error("Disk is NOT initialized");
		}
		catch (IOException x) {
			Logger.error("Unable to find Linux disk init: " + x);
		}

		return false;
	}

	public boolean executeDone(String command) {
		String out = this.exec(command + " && echo \"DONE\"");

		return this.checkDone(out);
	}

	public boolean checkDone(String out) {
		try (BufferedReader rdr = new BufferedReader(new StringReader(out))) {
			String line = rdr.readLine();

			while (line != null) {
				if (line.contains("DONE")) {
					return true;
				}

				line = rdr.readLine();
			}
		}
		catch (IOException x) {
			Logger.error("Unable to read string: " + x);
		}

		return false;
	}
}
