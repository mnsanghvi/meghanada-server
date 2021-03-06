package meghanada.project;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.google.common.base.MoreObjects;
import com.typesafe.config.ConfigFactory;
import meghanada.compiler.CompileResult;
import meghanada.compiler.SimpleJavaCompiler;
import meghanada.config.Config;
import meghanada.parser.JavaSource;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@DefaultSerializer(ProjectSerializer.class)
public abstract class Project {

    public static final String DEFAULT_PATH = File.separator + "src" + File.separator + "main" + File.separator;
    private static final Logger log = LogManager.getLogger(Project.class);

    private static final String COMPILE_SOURCE_PATH = "compile-source";
    private static final String COMPILE_TARGET_PATH = "compile-target";
    private static final String DEPENDENCIES_PATH = "dependencies";
    private static final String TEST_DEPENDENCIES_PATH = "test-dependencies";
    private static final String SOURCES_PATH = "sources";
    private static final String RESOURCES_PATH = "resources";
    private static final String TEST_SOURCES_PATH = "test-sources";
    private static final String TEST_RESOURCES_PATH = "test-resources";
    private static final String OUTPUT_PATH = "output";
    private static final String TEST_OUTPUT_PATH = "test-output";

    protected File projectRoot;
    protected Set<ProjectDependency> dependencies = new HashSet<>();
    protected Set<File> sources = new HashSet<>();
    protected Set<File> resources = new HashSet<>();
    protected File output;
    protected Set<File> testSources = new HashSet<>();
    protected Set<File> testResources = new HashSet<>();
    protected File testOutput;
    protected String compileSource = "1.8";
    protected String compileTarget = "1.8";
    protected String id;

    private SimpleJavaCompiler javaCompiler;
    private String cachedClasspath;
    private String cachedAllClasspath;

    private String[] prevTest;

    public Project(File projectRoot) throws IOException {
        this.projectRoot = projectRoot;
        System.setProperty("project.root", this.projectRoot.getCanonicalPath());
    }

    public abstract Project parseProject() throws ProjectParseException;

    public Set<File> getAllSources() {
        Set<File> temp = new HashSet<>();
        temp.addAll(this.getSourceDirectories());
        temp.addAll(this.getResourceDirectories());
        temp.addAll(this.getTestSourceDirectories());
        temp.addAll(this.getTestResourceDirectories());
        return temp;
    }

    public Set<File> getSourceDirectories() {
        return this.sources;
    }

    Set<File> getResourceDirectories() {
        return this.resources;
    }

    public File getOutputDirectory() {
        return this.output;
    }

    public Set<File> getTestSourceDirectories() {
        return this.testSources;
    }

    Set<File> getTestResourceDirectories() {
        return this.testResources;
    }

    public File getTestOutputDirectory() {
        return this.testOutput;
    }

    public Set<ProjectDependency> getDependencies() {
        return this.dependencies;
    }

    public String getCompileSource() {
        return compileSource;
    }

    public String getCompileTarget() {
        return compileTarget;
    }

    private SimpleJavaCompiler getJavaCompiler() {
        if (this.javaCompiler == null) {
            this.javaCompiler = new SimpleJavaCompiler(this.compileSource, this.compileTarget, getAllSources());
        }
        return this.javaCompiler;
    }

    public String classpath() {
        if (this.cachedClasspath != null) {
            return this.cachedClasspath;
        }
        List<String> classpath = new ArrayList<>();

        this.dependencies.stream()
                .filter(input -> input.getScope().equals("COMPILE"))
                .map(this::getCanonicalPath)
                .forEach(classpath::add);

        try {
            classpath.add(this.output.getCanonicalPath());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        this.cachedClasspath = String.join(File.pathSeparator, classpath);
        return this.cachedClasspath;
    }

    private String allClasspath() {
        if (this.cachedAllClasspath != null) {
            return this.cachedAllClasspath;
        }

        List<String> classpath = new ArrayList<>();
        this.dependencies.stream()
                .map(this::getCanonicalPath)
                .forEach(classpath::add);

        classpath.add(getCanonicalPath(this.output));
        classpath.add(getCanonicalPath(this.testOutput));
//        if (log.isDebugEnabled()) {
//            classpath.stream().forEach(s -> {
//                log.debug("Classpath:{}", s);
//            });
//        }
        this.cachedAllClasspath = String.join(File.pathSeparator, classpath);
        return this.cachedAllClasspath;
    }

    private String getCanonicalPath(ProjectDependency d) {
        try {
            return d.getFile().getCanonicalPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getCanonicalPath(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<File> getSourceFiles(Set<File> sourceDirs) throws IOException {
        return sourceDirs.parallelStream()
                .filter(File::exists)
                .map(this::getSourceFiles)
                .flatMap(Collection::stream)
                .filter(FileUtils::filterFile)
                .collect(Collectors.toList());
    }

    private List<File> getSourceFiles(File root) {
        if (!root.exists()) {
            return Collections.emptyList();
        }
        try {
            return Files.walk(root.toPath())
                    .map(Path::toFile)
                    .filter(JavaSource::isJavaFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    public CompileResult compileJava(final boolean force) throws IOException {
        List<File> files = this.getSourceFiles(this.getSourceDirectories());
        if (files != null && !files.isEmpty()) {
            return getJavaCompiler().compileFiles(files, this.allClasspath(), this.output.getCanonicalPath(), force);
        }
        return new CompileResult(false);
    }

    public CompileResult compileTestJava(final boolean force) throws IOException {
        List<File> files = this.getSourceFiles(this.getTestSourceDirectories());
        if (files != null && !files.isEmpty()) {
            return getJavaCompiler().compileFiles(files, this.allClasspath(), this.testOutput.getCanonicalPath(), force);
        }
        return new CompileResult(false);
    }

    public CompileResult compileFile(final File file, final boolean force) throws IOException {
        boolean isTest = false;
        String filepath = file.getCanonicalPath();
        for (File source : this.getTestSourceDirectories()) {
            String testPath = source.getCanonicalPath();
            if (filepath.startsWith(testPath)) {
                isTest = true;
                break;
            }
        }
        String output;
        if (isTest) {
            output = this.testOutput.getCanonicalPath();
        } else {
            output = this.output.getCanonicalPath();
        }
        if (FileUtils.filterFile(file)) {
            return getJavaCompiler().compile(file, this.allClasspath(), output, force);
        }
        return new CompileResult(false);
    }

    public CompileResult compileFile(final List<File> files, final boolean force) throws IOException {
        boolean isTest = false;
        // sampling
        String filepath = files.get(0).getCanonicalPath();
        for (File source : this.getTestSourceDirectories()) {
            String testPath = source.getCanonicalPath();
            if (filepath.startsWith(testPath)) {
                isTest = true;
                break;
            }
        }
        String output;
        if (isTest) {
            output = this.testOutput.getCanonicalPath();
        } else {
            output = this.output.getCanonicalPath();
        }

        final List<File> filesList = files.stream()
                .filter(FileUtils::filterFile)
                .collect(Collectors.toList());

        return getJavaCompiler().compileFiles(filesList, this.allClasspath(), output, force);
    }

    public File getProjectRoot() {
        return projectRoot;
    }

    protected File normalize(String src) {
        File file = new File(src);
        if (!file.isAbsolute()) {
            file = new File(this.projectRoot, src);
        }
        return file;
    }

    protected File normalizeFile(File file) {
        if (!file.isAbsolute()) {
            file = new File(this.projectRoot, file.getPath());
        }
        return file;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("projectRoot", projectRoot)
                .add("dependencies", dependencies.size())
                .add("sources", sources)
                .add("resources", resources)
                .add("output", output)
                .add("testSources", testSources)
                .add("testResources", testResources)
                .add("testOutput", testOutput)
                .add("compileSource", compileSource)
                .add("compileTarget", compileTarget)
                .toString();
    }

    protected InputStream runProcess(List<String> cmd) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        processBuilder.directory(this.projectRoot);
        String cmdString = String.join(" ", cmd);

        log.debug("RUN cmd: {}", cmdString);

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        return process.getInputStream();
    }

    public abstract InputStream runTask(List<String> args) throws IOException;

    public InputStream runJUnit(String test) throws IOException {
        return runUnitTest(test);
    }

    private InputStream runUnitTest(String... tests) throws IOException {
        if (tests[0].isEmpty()) {
            tests = this.prevTest;
        }
        log.debug("runUnit test:{} prevTest:{}", tests, prevTest);

        Config config = Config.load();
        List<String> cmd = new ArrayList<>();
        String binJava = "/bin/java".replace("/", File.separator);
        String javaCmd = new File(config.getJavaHomeDir(), binJava).getCanonicalPath();
        cmd.add(javaCmd);
        String cp = this.allClasspath();
        String mainJar = "meghanada.jar";
        String jarPath = new File(config.getHomeDir(), mainJar).getCanonicalPath();
        cp += File.pathSeparator + jarPath;
        cmd.add("-ea");
        cmd.add("-XX:+TieredCompilation");
        cmd.add("-XX:+UseConcMarkSweepGC");
        cmd.add("-XX:SoftRefLRUPolicyMSPerMB=50");
        cmd.add("-Xverify:none");
        cmd.add("-Xms256m");
        cmd.add("-Xmx2G");
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add(String.format("-Dproject.root=%s", this.projectRoot.getCanonicalPath()));
        cmd.add("meghanada.junit.TestRunner");
        Collections.addAll(cmd, tests);

        this.prevTest = tests;

        return this.runProcess(cmd);
    }

    public Project mergeFromProjectConfig() {
        final File configFile = new File(this.projectRoot, Config.MEGHANADA_CONF_FILE);
        if (configFile.exists()) {
            final com.typesafe.config.Config config = ConfigFactory.parseFile(configFile);
            // java.home
            if (config.hasPath("java-home")) {
                String o = config.getString("java-home");
                System.setProperty("java.home", o);
            }
            // java.home
            if (config.hasPath("java-version")) {
                String o = config.getString("java-version");
                System.setProperty("java.specification.version", o);
            }

            // compile-source
            if (config.hasPath(COMPILE_SOURCE_PATH)) {
                this.compileSource = config.getString(COMPILE_SOURCE_PATH);
            }
            // compile-source
            if (config.hasPath(COMPILE_TARGET_PATH)) {
                this.compileTarget = config.getString(COMPILE_TARGET_PATH);
            }

            // dependencies
            if (config.hasPath(DEPENDENCIES_PATH)) {
                config.getStringList(DEPENDENCIES_PATH).stream()
                        .filter(path -> new File(path).exists())
                        .map(path -> {
                            final File file = new File(path);
                            return new ProjectDependency(file.getName(), "COMPILE", "1.0.0", file);
                        }).forEach(p -> this.dependencies.add(p));
            }
            // test-dependencies
            if (config.hasPath(TEST_DEPENDENCIES_PATH)) {
                config.getStringList(TEST_DEPENDENCIES_PATH).stream()
                        .filter(path -> new File(path).exists())
                        .map(path -> {
                            final File file = new File(path);
                            return new ProjectDependency(file.getName(), "TEST", "1.0.0", file);
                        }).forEach(p -> this.dependencies.add(p));
            }

            // sources
            if (config.hasPath(SOURCES_PATH)) {
                config.getStringList(SOURCES_PATH)
                        .stream()
                        .filter(path -> new File(path).exists())
                        .map(File::new)
                        .forEach(file -> this.sources.add(file));
            }
            // sources
            if (config.hasPath(RESOURCES_PATH)) {
                config.getStringList(RESOURCES_PATH)
                        .stream()
                        .filter(path -> new File(path).exists())
                        .map(File::new)
                        .forEach(file -> this.resources.add(file));
            }
            // test-sources
            if (config.hasPath(TEST_SOURCES_PATH)) {
                config.getStringList(TEST_SOURCES_PATH)
                        .stream()
                        .filter(path -> new File(path).exists())
                        .map(File::new)
                        .forEach(file -> this.testSources.add(file));
            }
            // test-resources
            if (config.hasPath(TEST_RESOURCES_PATH)) {
                config.getStringList(TEST_RESOURCES_PATH)
                        .stream()
                        .filter(path -> new File(path).exists())
                        .map(File::new)
                        .forEach(file -> this.testResources.add(file));
            }
            // output
            if (config.hasPath(OUTPUT_PATH)) {
                String o = config.getString(OUTPUT_PATH);
                this.output = new File(o);
            }
            // test-output
            if (config.hasPath(TEST_OUTPUT_PATH)) {
                String o = config.getString(TEST_OUTPUT_PATH);
                this.testOutput = new File(o);
            }

            if (config.hasPath("src-filter")) {
                String o = config.getString("src-filter");
                System.setProperty("src-filter", o);
            }

            final Config mainConfig = Config.load();
            if (config.hasPath("include-file")) {
                final List<String> list = config.getStringList("include-file");
                mainConfig.setIncludeList(list);
            }
            if (config.hasPath("exclude-file")) {
                final List<String> list = config.getStringList("exclude-file");
                mainConfig.setExcludeList(list);
            }
        }
        log.debug("Merged Project:{}", this);
        return this;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
