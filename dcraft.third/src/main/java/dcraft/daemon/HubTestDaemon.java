package dcraft.daemon;

import java.util.Map;

public class HubTestDaemon implements IDaemon {
    @Override
    public void start(String instanceName) {
        System.out.println("starting");

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

        System.out.println("Deployment: " + env.get("DC_DEPLOYMENT"));
        System.out.println("Node:       " + env.get("DC_NODE"));

        System.out.println("started");
    }

    @Override
    public void handleCommand(String command) {
        System.out.println("got command: " + command);

        if ("env".equals(command)) {
            Map<String, String> env = System.getenv();
            for (Map.Entry<String, String> entry : env.entrySet()) {
                System.out.println(entry.getKey() + "=" + entry.getValue());
            }
        }
    }

    @Override
    public void shutdown() {
        System.out.println("ended");
    }
}
