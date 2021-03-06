package meghanada;

import meghanada.server.Server;
import meghanada.server.emacs.EmacsServer;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class Main {

    private static Logger log = LogManager.getLogger(Main.class);

    public static String getVersion() throws IOException {
        final Manifest manifest = new Manifest();
        manifest.read(Thread.currentThread().getContextClassLoader().getResourceAsStream("META-INF/MANIFEST.MF"));
        final Attributes mainAttributes = manifest.getMainAttributes();
        return mainAttributes.getValue("Version");
    }

    public static void main(String args[]) throws ParseException, IOException {
        final String version = getVersion();
        System.setProperty("meghanada-server.version", version);

        final Options options = buildOptions();

        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("meghanada server", options);
            return;
        }
        if (cmd.hasOption("version")) {
            System.out.println(version);
            return;
        }

        if (cmd.hasOption("v")) {
            final LoggerContext context = (LoggerContext) LogManager.getContext(false);
            final Configuration configuration = context.getConfiguration();
            final LoggerConfig loggerConfig = configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.setLevel(Level.DEBUG);
            context.updateLoggers();
            System.setProperty("log-level", "DEBUG");
            log.debug("set verbose flag(DEBUG)");
        }
        if (cmd.hasOption("vv")) {
            final LoggerContext context = (LoggerContext) LogManager.getContext(false);
            final Configuration configuration = context.getConfiguration();
            final LoggerConfig loggerConfig = configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.setLevel(Level.TRACE);
            context.updateLoggers();
            System.setProperty("log-level", "TRACE");
            log.debug("set verbose flag(TRACE)");
        }
        if (cmd.hasOption("gradle-version")) {
            final String gradleVersion = cmd.getOptionValue("gradle-version", "");
            if (!version.isEmpty()) {
                System.setProperty("gradle-version", gradleVersion);
            }
        }

        String port = "55555";
        String projectRoot = "./";
        String fmt = "sexp";

        if (cmd.hasOption("p")) {
            port = cmd.getOptionValue("p", port);
        }
        if (cmd.hasOption("r")) {
            projectRoot = cmd.getOptionValue("r", projectRoot);
        }
        if (cmd.hasOption("output")) {
            fmt = cmd.getOptionValue("output", fmt);
        }
        log.debug("set port:{}, projectRoot:{}, output:{}", port, projectRoot, fmt);
        final int portInt = Integer.parseInt(port);

        log.info("Meghanada-Server Version:{}", version);
        final Server server = createServer("127.0.0.1", portInt, projectRoot, fmt);
        server.startServer();
    }

    private static Server createServer(final String host, final int port, final String projectRoot, final String fmt) throws IOException {
        return new EmacsServer(host, port, projectRoot);
    }

    private static Options buildOptions() {
        final Options options = new Options();
        final Option help = new Option("h", "help", false, "show help");
        options.addOption(help);
        final Option version = new Option(null, "version", false, "show version information");
        options.addOption(version);
        final Option port = new Option("p", "port", true, "set server port. default: 55555");
        options.addOption(port);
        final Option project = new Option("r", "project", true, "set project root path. default: current path ");
        options.addOption(project);
        final Option verbose = new Option("v", "verbose", false, "show verbose message (DEBUG)");
        options.addOption(verbose);
        final Option traceVerbose = new Option("vv", "traceVerbose", false, "show verbose message (TRACE)");
        options.addOption(traceVerbose);
        final Option out = new Option(null, "output", true, "output format (sexp, csv, json). default: sexp");
        options.addOption(out);
        final Option gradleVersion = new Option(null, "gradle-version", true, "set use gradle version");
        options.addOption(gradleVersion);
        return options;
    }
}
