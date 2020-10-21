// Copyright (c) 2012-2014, David H. Hovemeyer <david.hovemeyer@gmail.com>
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package dcraft.daemon.cloudcoder;

import dcraft.daemon.IDaemon;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Launch a daemon process in the background and create a FIFO
 * used to send commands to the daemon.
 * 
 * @author David Hovemeyer
 */
public class DaemonLauncher {
	static public DaemonLauncher of(Options options) {
		DaemonLauncher launcher = new DaemonLauncher();
		launcher.options = options;
		return launcher;
	}

	protected Options options = null;
	
	protected DaemonLauncher() { }

	/**
	 * Launch the daemon as a background process (with a FIFO for communication).
	 * 
	 * @throws DaemonException
	 */
	public void launch() throws DaemonException {
		// Check to see if the instance is already running
		Integer pid;
		pid = Util.readPid(options.getInstanceName());
		if (pid != null) {
			// Is the instance still running?
			if (Util.isRunning(pid)) {
				throw new DaemonException("Process " + pid + " is still running");
			}
			
			// Instance is not still running, so delete pid file and FIFO
			Util.deleteFile(Util.getPidFileName(options.getInstanceName()));
			Util.deleteFile(Util.getFifoName(options.getInstanceName(), pid));
		}
		
		// Start the process
		String codeBase = Util.findCodeBase(this.getClass());
		//System.out.println("Codebase is " + codeBase);
		
		// Build a classpath in which the codebase of this class is first.
		StringBuilder classPathBuilder = new StringBuilder();
		//classPathBuilder.append(codeBase);
		//classPathBuilder.append(File.pathSeparator);

		if (options.getJvmOptions() != null) {
			classPathBuilder.append(options.getClasspath());
		}

		classPathBuilder.append(System.getProperty("java.class.path"));

		String classPath = classPathBuilder.toString();
		
		if (Util.hasShellMetaCharacters(classPath)) {
			throw new IllegalArgumentException("Classpath has shell metacharacters");
		}

		System.out.println("luanching " + options.getDaemonClass());

		List<String> cmd = new ArrayList<String>();
		cmd.add(Util.SH_PATH);
		cmd.add("-c");
		
		// Generate the shell command that will launch the DaemonLauncher main method
		// as a background process
		StringBuilder launchCmdBuilder = new StringBuilder();
		launchCmdBuilder.append("( exec '");
		launchCmdBuilder.append(Util.getJvmExecutablePath());
		launchCmdBuilder.append("' -classpath '");
		launchCmdBuilder.append(classPath);
		launchCmdBuilder.append("' ");
		if (options.getJvmOptions() != null) {
			launchCmdBuilder.append(options.getJvmOptions());
			launchCmdBuilder.append(" ");
		}
		launchCmdBuilder.append("'" + DaemonLauncher.class.getName() + "' ");
		launchCmdBuilder.append(options.getInstanceName());
		launchCmdBuilder.append(" '");
		launchCmdBuilder.append(options.getDaemonClass());
		launchCmdBuilder.append("' < /dev/null >> '");
		launchCmdBuilder.append(options.getStdoutLogFileName());
		launchCmdBuilder.append("' 2>&1 ) &");
		String launchCmd = launchCmdBuilder.toString();
		//System.out.println("launchCmd=" + launchCmd);

		cmd.add(launchCmd);
		
		int exitCode = Util.exec(cmd.toArray(new String[cmd.size()]));
		if (exitCode != 0) {
			throw new DaemonException("Error launching daemon: shell exited with code " + exitCode);
		}
	}

	/**
	 * This is the main method invoked by the shell command used
	 * to start the background process.  This should not be called
	 * directly.
	 * 
	 * @param args arguments
	 * @throws IOException 
	 */
	public static void main(String[] args) throws Exception {
		String instanceName = args[0];
		String daemonClassName = args[1];
		
		// Find out our pid
		Integer pid = Util.getPid();
		
		// Write pid file
		Util.writePid(instanceName, pid);
		
		// Create FIFO
		Util.exec(Util.MKFIFO_PATH, Util.getFifoName(instanceName, pid));
		
		// Instantiate the daemon
		Class<?> daemonClass = Class.forName(daemonClassName);
		IDaemon daemon = (IDaemon) daemonClass.newInstance();
		
		// Start the daemon!
		// Note that exceptions are treated as fatal, and will result
		// in a message to stdout (which should be captured in the stdout
		// log) and cleanup of the FIFO and pid file.
		try {
			daemon.start(instanceName);
		} catch (Throwable e) {
			System.out.println("Exception starting daemon");
			e.printStackTrace(System.out);
			System.out.flush();
			Util.deleteFile(Util.getFifoName(instanceName, pid));
			Util.deleteFile(Util.getPidFileName(instanceName));
			System.exit(1);
		}
		
		// Read commands (issued by the DaemonController) from the FIFO
		String fifoName = Util.getFifoName(instanceName, pid);
		BufferedReader reader = null;
		boolean shutdown = false;
		while (!shutdown) {
			if (reader == null) {
				// open the FIFO: will block until a process writes to it
				reader = new BufferedReader(new FileReader(fifoName));
			}
			
			// Read a command from the FIFO
			String line = reader.readLine();
			
			if (line == null) {
				// EOF on FIFO
				IOUtil.closeQuietly(reader);
				reader = null;
			} else {
				// Process the command
				line = line.trim();
				if (!line.equals("")) {
					if (line.equals("shutdown")) {
						shutdown = true;
						IOUtil.closeQuietly(reader);
						reader = null;
					} else {
						// have the daemon handle the command
						daemon.handleCommand(line);
					}
				}
			}
		}
		
		// Shut down the daemon
		daemon.shutdown();
	}
}
