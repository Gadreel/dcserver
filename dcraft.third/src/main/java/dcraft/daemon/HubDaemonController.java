package dcraft.daemon;

import dcraft.daemon.cloudcoder.DaemonController;
import dcraft.daemon.cloudcoder.Options;

import java.io.File;
import java.util.Map;

public class HubDaemonController extends DaemonController {
    @Override
    protected Options createOptions(String[] args) {
        Options options = super.createOptions(args);

        if (options.getInstanceName() == null)
            options.setInstanceName("dcServer");

        if (options.getStdoutLogFileName() == null)
            options.setStdoutLogFileName("logs/hub-daemon.log");

        if (options.getDaemonClass() == null)
            options.setDaemonClass("dcraft.hub.Daemon");        // dcraft.daemon.HubTestDaemon

        if (options.getJvmOptions() == null)
            options.setJvmOptions("-Djava.io.tmpdir=./temp -Djava.library.path=./lib -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HToolkit");

        Map<String, String> env = System.getenv();

        if (env.containsKey("DC_DEV"))
            options.setDevelopment("true".equals(env.get("DC_DEV")));

        StringBuilder classpath = new StringBuilder();

        // ext folder

        if (options.isDevelopment()) {
            File lib = new File("./ext");

            File[] jars = lib.listFiles();

            for (File jar : jars) {
                if (! jar.getName().endsWith(".jar"))
                    continue;

                classpath.append("./ext/" + jar.getName());
                classpath.append(File.pathSeparator);
            }
        }

        // lib folder

        {
            File lib = new File("./lib");

            File[] jars = lib.listFiles();

            for (File jar : jars) {
                if (!jar.getName().endsWith(".jar"))
                    continue;

                classpath.append("./lib/" + jar.getName());
                classpath.append(File.pathSeparator);
            }
        }

        options.setClasspath(classpath.toString());

        return options;
    }

    public static void main(String[] args) {
        Map<String, String> env = System.getenv();

        if (! env.containsKey("DC_DEPLOYMENT")) {
            System.err.println("Deployment name is missing");
            System.exit(1);
            return;
        }

        if (! env.containsKey("DC_NODE")) {
            System.err.println("Node id is missing");
            System.exit(1);
            return;
        }

        HubDaemonController controller = new HubDaemonController();
        controller.exec(args);
    }
}
