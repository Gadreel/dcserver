// Copyright (c) 2012-2018, David H. Hovemeyer <david.hovemeyer@gmail.com>
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class is responsible for taking commands issued from
 * the command line and processing them to start the daemon,
 * send administrative commands to the daemon, or shut down the
 * daemon.  You should extend it and override the abstract methods.
 * Your deployable main method should create an instance of
 * your controller subclass and invoke the {@link #exec(String[])}
 * method on it, passing the command line arguments.
 * 
 * @author David Hovemeyer
 */
public abstract class DaemonController {

	/**
	 * Carry out the command described by the command-line arguments.
	 * 
	 * @param args the command-line arguments
	 */
	public void exec(String[] args) {
		try {
			doExec(args);
		} catch (DaemonException e) {
			System.err.println("Error: " + e.getMessage());
		} catch (IllegalArgumentException e) {
			System.err.println("Error: " + e.getMessage());
		}
	}

	private void doExec(String[] args) throws DaemonException {
		Options opts = createOptions(args);

		String command = opts.getCommand();
		
		String instanceName = opts.getInstanceName();

		if (instanceName == null)
			instanceName = "default";

		if (command.equals("start")) {
			doStart(opts, instanceName);
		}
		else if (command.equals("shutdown") || command.equals("stop")) {
			// shutdown command
			Integer pid = getPidOfInstance(instanceName);
			Util.sendCommand(instanceName, pid, "shutdown");
			System.out.print("Waiting for process " + pid + " to finish...");
			System.out.flush();
			Util.waitForExit(pid, new Runnable(){
				@Override
				public void run() {
					System.out.print(".");
					System.out.flush();
				}
			});
			Util.cleanup(instanceName, pid);
		}
		else if (command.equals("check")) {
			// Check pid file
			Integer pid = Util.readPid(instanceName);
			if (pid == null) {
				System.out.println("No pid file found for instance '" + instanceName + "'");
				System.exit(1);
			}
			
			// Is the instance still running?
			if (Util.isRunning(pid)) {
				System.out.println("Instance '" + instanceName + "' is running as process " + pid);
				System.exit(0);
			} else {
				System.out.println("Instance '" + instanceName + "' is no longer running (process " + pid + " does not exist)");
				System.exit(1);
			}
		}
		else if (command.equals("poke")) {
			// poke instance: if it isn't running, restart it
			boolean needStart = false;
			boolean needCleanup = false;
			
			// Check pid file
			Integer pid = Util.readPid(instanceName);
			if (pid == null) {
				// no pid file
				needStart = true;
				opts.pokeMessage(instanceName, "Restarting (no pid file found)");
			} else {
				// Check whether process is running
				if (!Util.isRunning(pid)) {
					needCleanup = true; // remove old pid file and FIFO
					needStart = true;
					opts.pokeMessage(instanceName, "Restarting (process " + pid + " is no longer running)");
				} else {
					opts.pokeMessage(instanceName, "No restart is needed (process " + pid + " is still running)");
				}
			}
			
			// cleanup files from previous execution if necessary
			if (needCleanup) {
				Util.deleteFile(Util.getPidFileName(instanceName));
				Util.deleteFile(Util.getFifoName(instanceName, pid));
				opts.pokeMessage(instanceName, "Restart: deleted pid file and FIFO for process " + pid);
			}
			
			// start if necessary
			if (needStart) {
				doStart(opts, instanceName);
				opts.pokeMessage(instanceName, "Restart: started new process");
				if (opts.isPokeOutputEnabled()) {
					// Loop for a bit to find out pid of new process
					Integer newPid = null;
					for (int count = 0; count < 5; count++) {
						newPid = Util.readPid(instanceName);
						if (newPid != null) {
							break;
						}
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							opts.pokeMessage(instanceName, "Interrupted waiting for new pid file?");
							break;
						}
					}
					if (newPid != null) {
						opts.pokeMessage(instanceName, "New process pid is " + newPid);
					} else {
						opts.pokeMessage(instanceName, "Could not determine pid of new process");
					}
				}
			}
		}
		else {
			// some other command
			Integer pid = getPidOfInstance(instanceName);
			Util.sendCommand(instanceName, pid, command);
		}
	}

	private void doStart(Options opts, String instanceName) throws DaemonException {
		if (Util.hasShellMetaCharacters(opts.getStdoutLogFileName())) {
			throw new IllegalArgumentException("Stdout log file name may not contain shell metacharacters");
		}

		// Make sure that the directory for the stdout log file exists.
		try {
			Files.createDirectories(Path.of(opts.getStdoutLogFileName()).getParent());
		}
		catch (IOException x) {
			System.err.println("Unable to create log folder: " + x);
			throw new DaemonException("Logs folder is required, unable to proceed.");
		}

		DaemonLauncher launcher = DaemonLauncher.of(opts);

		launcher.launch();
	}

	/**
	 * Create the {@link Options} object that will parse the command line
	 * options.  Subclasses may override this to return their own
	 * specific Options object.
	 * 
	 * @return the Options object to use to parse the command line
	 */
	protected Options createOptions(String[] args) {
		Options options = new Options();
		options.parse(args);
		return options;
	}

	private Integer getPidOfInstance(String instanceName) throws DaemonException {
		Integer pid = Util.readPid(instanceName);
		if (pid == null) {
			throw new DaemonException("No pid found for instance " + instanceName);
		}
		if (!Util.isRunning(pid)) {
			throw new DaemonException("Instance " + instanceName + " is not running (old pid=" + pid + ")");
		}
		return pid;
	}
}
