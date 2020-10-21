package dcraft.daemon.cloudcoder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Command line options handling class.
 * DaemonController implementations may define their own
 * subclasses which override methods in this class.
 */
public class Options {
    private String command;
    private String instanceName;
    private String stdoutLogFileName;
    private String jvmOptions;
    private String daemonClass;
    private String classpath;
    private boolean development;

    /**
     * Constructor.
     */
    public Options() {

    }

    /**
     * Parse command line arguments.
     *
     * @param args the command line arguments to parse
     */
    public void parse(String[] args) {
        int i;
        for (i = 0; i < args.length; i++) {
            String arg = args[i];

            if (!arg.startsWith("--")) {
                break;
            }

            handleOption(arg);
        }

        if (i >= args.length) {
            throw new IllegalArgumentException("no command");
        }

        command = args[i];

        if (i != args.length - 1) {
            throw new IllegalArgumentException("Extra arguments");
        }
    }

    /**
     * Handle a single option.  Subclasses may override
     * to handle their own specific options.  Subclasses
     * should delegate to this method for general options.
     *
     * @param arg the option argument, which will begin with "--"
     */
    protected void handleOption(String arg) {
        if (arg.startsWith("--instance=")) {
            instanceName = arg.substring("--instance=".length());
        }
        else if (arg.startsWith("--stdoutLog=")) {
            stdoutLogFileName = arg.substring("--stdoutLog=".length());
        }
        else if (arg.startsWith("--jvmOptions=")) {
            jvmOptions = arg.substring("--jvmOptions=".length());
        }
        else {
            throw new IllegalArgumentException("Unknown option: " + arg);
        }
    }

    public String getCommand() {
        return command;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public String getStdoutLogFileName() {
        return stdoutLogFileName;
    }

    public String getJvmOptions() {
        return jvmOptions;
    }

    public String getDaemonClass() {
        return this.daemonClass;
    }

    public String getClasspath() {
        return this.classpath;
    }

    public boolean isDevelopment() {
        return this.development;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public void setStdoutLogFileName(String stdoutLogFileName) {
        this.stdoutLogFileName = stdoutLogFileName;
    }

    public void setJvmOptions(String jvmOptions) {
        this.jvmOptions = jvmOptions;
    }

    public void setDaemonClass(String daemonClass) {
        this.daemonClass = daemonClass;
    }

    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }

    public void setDevelopment(boolean development) {
        this.development = development;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * Returns true if the <code>poke</code> command should
     * generate output.  The default implementation returns <code>true</code>.
     * Subclasses may disable poke output by returning false.
     * If output is enabled, the poke command prints detailed information
     * about whether or not the process is running or needs to be
     * restarted, and if a restart was necessary, whether the restart
     * was successful.  This information is very useful to capture in a
     * log for diagnostic purposes.
     *
     * @return true if poke output should be enabled, false otherwise
     */
    public boolean isPokeOutputEnabled() {
        return true;
    }

    /**
     * Print a message about poking an instance.
     * Does nothing if poke output is disabled.
     *
     * @param instanceName the instance name
     * @param msg          the message to print
     */
    public void pokeMessage(String instanceName, String msg) {
        if (!isPokeOutputEnabled()) {
            return;
        }
        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date now = new Date();
        StringBuilder buf = new StringBuilder();
        buf.append(fmt.format(now));
        buf.append(": Poking instance ");
        buf.append(instanceName);
        buf.append(": ");
        buf.append(msg);
        System.out.println(buf.toString());
    }
}
