package dcraft.hub.config;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.app.HubState;
import dcraft.hub.resource.ConfigResource;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.HubLog;
import dcraft.log.Logger;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;
import io.netty.channel.epoll.Epoll;
import io.netty.handler.ssl.OpenSsl;
import io.netty.util.internal.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.util.*;

/**
 */
public class HubStartBeginWork extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		ApplicationHub.setState(HubState.Booting);
		
		Security.addProvider(new BouncyCastleProvider());
		
		ConfigResource config = tier.getConfig();
		
		// change to overrides if find config
		XElement logger = config.getTag("Logger");
		
		// prepare the logger - use files, use custom log writer
		if (! HubLog.init(logger)) {
			Logger.error("Unable to initialize Logger");
			taskctx.returnEmpty();
			return;
		}
		
		taskctx.withDebugLevel(HubLog.getGlobalLevel());
		
		Logger.boundary("Origin", "hub:", "Op", "Start");
		
		// TODO use translation codes for all start up messages after dictionaries are loaded
		Logger.info( "Hub deployment: " + ApplicationHub.getDeployment());
		Logger.info( "Hub role: " + ApplicationHub.getRole());
		Logger.info( "Hub id: " + ApplicationHub.getNodeId());
		Logger.info( "Is hub production: " + ApplicationHub.isProduction());
		
		Logger.info( "Java version: " + System.getProperty("java.version"));
		Logger.info( "Java vendor: " + System.getProperty("java.vendor"));
		Logger.info( "Java vm: " + System.getProperty("java.vm.name"));
		
		if (Logger.isDebug()) {
			Logger.debug( "Java class path: " + System.getProperty("java.class.path"));
			Logger.debug( "Java home: " + System.getProperty("java.home"));
			Logger.debug("OS: " + System.getProperty("os.name"));
			Logger.debug( "OS Ver: " + System.getProperty("os.version"));
			Logger.debug( "OS Arch: " + System.getProperty("os.arch"));
			Logger.debug("User: " + System.getProperty("user.name"));
			Logger.debug("User working dir: " + System.getProperty("user.dir"));
		}

		Logger.info("/dev/epoll: " + (Epoll.isAvailable() ? "yes" : "no (" + Epoll.unavailabilityCause() + ')'));
		Logger.info("OpenSSL: " + (OpenSsl.isAvailable() ? "yes (" + OpenSsl.versionString() + ", " + OpenSsl.version() + ')' : "no (" + OpenSsl.unavailabilityCause()) + ')');

		if (Logger.isDebug()) {
			String os = PlatformDependent.normalizedOs();
			String arch = PlatformDependent.normalizedArch();
			Set<String> libNames = new LinkedHashSet(5);
			String staticLibName = "netty_tcnative";
			if ("linux".equals(os)) {
				Set<String> classifiers = PlatformDependent.normalizedLinuxClassifiers();
				Iterator var5 = classifiers.iterator();

				while (var5.hasNext()) {
					String classifier = (String) var5.next();
					libNames.add(staticLibName + "_" + os + '_' + arch + "_" + classifier);
				}

				libNames.add(staticLibName + "_" + os + '_' + arch);
				libNames.add(staticLibName + "_" + os + '_' + arch + "_fedora");
			}
			else {
				libNames.add(staticLibName + "_" + os + '_' + arch);
			}

			libNames.add(staticLibName + "_" + arch);
			libNames.add(staticLibName);

			System.out.println("trying native libs: ");

			for (String libname : libNames)
				System.out.println(" - " + libname);

			boolean pass = false;

			for (String name : libNames) {
				System.out.println("try loaded library with name: " + name);

				try {
					load(name, ResourceHub.getTopResources().getClassLoader().getClassLoader());

					System.out.println("Loaded library with name: " + name);
					pass = true;
					break;
				}
				catch (Throwable x) {
					System.out.println("Failed loaded library with name: " + name + " - " + x);
				}
			}

			if (! pass)
				System.out.println("no native libraries found");

			//NativeLibraryLoader.loadFirstAvailable(PlatformDependent.getClassLoader(SSLContext.class), (String[]) libNames.toArray(EmptyArrays.EMPTY_STRINGS));
		}

		// TODO prepare the basics, then do a prep task, then done booting
		
		try {
			ApplicationHub.setWatcher(FileSystems.getDefault().newWatchService());
		}
		catch (IOException x) {
			Logger.error("Cannot create file watcher service: " + x);
			taskctx.returnEmpty();
			return;
		}
        
        taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		Logger.boundary("Origin", "hub:", "Op", "Reload");
	
		taskctx.returnEmpty();
	}

	// remove later
	public static void load(String originalName, ClassLoader loader) {
		String mangledPackagePrefix = calculateMangledPackagePrefix();
		System.out.println("mangled: " + mangledPackagePrefix);

		String name = mangledPackagePrefix + originalName;
		System.out.println("qualified: " + name);

		File workdir = PlatformDependent.tmpdir();

		try {
			loadLibrary(loader, name, false);
		}
		catch (Throwable var26) {
			System.out.println("failed load library: " + name + " - " + var26);

			String libname = System.mapLibraryName(name);
			String path = "META-INF/native/" + libname;
			InputStream in = null;
			OutputStream out = null;
			File tmpFile = null;
			URL url = getResource(path, loader);

			try {
				if (url == null) {
					System.out.println("file resource not found: " + name + " - " + url);

					if (!PlatformDependent.isOsx()) {
						throw new RuntimeException("failed");
					}

					String fileName = path.endsWith(".jnilib") ? "META-INF/native/lib" + name + ".dynlib" : "META-INF/native/lib" + name + ".jnilib";
					url = getResource(fileName, loader);

					if (url == null) {
						System.out.println("osx file resource not found: " + name + " - " + url);
						throw new RuntimeException("failed");
					}
				}

				System.out.println("file resource found: " + name + " - " + url);

				int index = libname.lastIndexOf(46);
				String prefix = libname.substring(0, index);
				String suffix = libname.substring(index);
				tmpFile = PlatformDependent.createTempFile(prefix, suffix, workdir);
				in = url.openStream();
				out = new FileOutputStream(tmpFile);
				byte[] buffer = new byte[8192];

				int length;
				while((length = in.read(buffer)) > 0) {
					out.write(buffer, 0, length);
				}

				out.flush();

				closeQuietly(out);
				out = null;

				System.out.println("load written file: " + tmpFile.getPath());

				loadLibrary(loader, tmpFile.getPath(), true);
			}
			catch (UnsatisfiedLinkError var23) {
				try {
					if (tmpFile != null && tmpFile.isFile() && tmpFile.canRead() && ! NoexecVolumeDetector.canExecuteExecutable(tmpFile)) {
						System.out.println(tmpFile.getPath() + " exists but cannot be executed even when execute permissions set; check volume for \"noexec\" flag; use -D{}=[path] to set native working directory separately: " + workdir);
					}
				}
				catch (Throwable var22) {
					System.out.println("Error checking if is on a file store mounted with noexec: " + tmpFile + " : " + var22);
				}

				System.out.println("failing load 5");
				throw new RuntimeException("failed");
			}
			catch (Exception var24) {
				System.out.println("failing load 6: unknown Unsatisfied Link: " + var24);
				throw new RuntimeException("failed");
			}
			finally {
				closeQuietly(in);
				closeQuietly(out);
				/* restore ?
				if (tmpFile != null && !tmpFile.delete()) {
					tmpFile.deleteOnExit();
				}
				 */
			}
		}
	}

	// maybe remove later?
	private static String calculateMangledPackagePrefix() {
		String maybeShaded = NativeLibraryLoader.class.getName();
		String expected = "io!netty!util!internal!NativeLibraryLoader".replace('!', '.');
		if (!maybeShaded.endsWith(expected)) {
			throw new UnsatisfiedLinkError(String.format("Could not find prefix added to %s to get %s. When shading, only adding a package prefix is supported", expected, maybeShaded));
		} else {
			return maybeShaded.substring(0, maybeShaded.length() - expected.length()).replace("_", "_1").replace('.', '_');
		}
	}

	private static void loadLibrary(ClassLoader loader, String name, boolean absolute) {
		Object suppressed = null;

		try {
			try {
				System.out.println("trying load library");

				Class<?> newHelper = tryToLoadClass(loader, ApplicationHub.class);
				loadLibraryByHelper(newHelper, name, absolute);
				System.out.println("Successfully loaded the library: " + name);
				return;
			}
			catch (UnsatisfiedLinkError var5) {
				System.out.println("failed link 1st: " + var5);
			}
			catch (Exception var6) {
				System.out.println("failed x 1st: " + var6);
			}

			System.out.println("first attempts to load the library failed: " + name);

			loadLibrary(name, absolute);
			System.out.println("Successfully loaded the library: " + name);
		}
		catch (NoSuchMethodError var7) {
			System.out.println("failed to load the library - no such method: " + name + " - " +  var7);
			throw new RuntimeException("failed");
		}
		catch (UnsatisfiedLinkError var8) {
			System.out.println("failed to load the library - UnsatisfiedLinkError: " + name + " - " +  var8);
			throw new RuntimeException("failed");
		}
	}
	private static URL getResource(String path, ClassLoader loader) {
		System.out.println("attempt to load resource: " + path);

		Enumeration urls;
		try {
			if (loader == null) {
				urls = ClassLoader.getSystemResources(path);
			}
			else {
				urls = loader.getResources(path);
			}
		}
		catch (IOException var11) {
			System.out.println("An error occurred while getting the resources for: " + path + " - " + var11);
			throw new RuntimeException("An error occurred while getting the resources for " + path, var11);
		}

		List<URL> urlsList = Collections.list(urls);
		int size = urlsList.size();
		switch(size) {
			case 0:
				return null;
			case 1:
				return (URL)urlsList.get(0);
			default:
				System.out.println("Multiple resources found for '" + path + "' with different content: " + urlsList + ". Please fix your dependency graph.");
				throw new RuntimeException("failed");
		}
	}

	private static void closeQuietly(Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (IOException var2) {
			}
		}

	}

	private static final class NoexecVolumeDetector {
		@SuppressJava6Requirement(
				reason = "Usage guarded by java version check"
		)
		private static boolean canExecuteExecutable(File file) throws IOException {
			if (PlatformDependent.javaVersion() < 7) {
				return true;
			} else if (file.canExecute()) {
				return true;
			} else {
				Set<PosixFilePermission> existingFilePermissions = Files.getPosixFilePermissions(file.toPath());
				Set<PosixFilePermission> executePermissions = EnumSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE);
				if (existingFilePermissions.containsAll(executePermissions)) {
					return false;
				} else {
					Set<PosixFilePermission> newPermissions = EnumSet.copyOf(existingFilePermissions);
					newPermissions.addAll(executePermissions);
					Files.setPosixFilePermissions(file.toPath(), newPermissions);
					return file.canExecute();
				}
			}
		}

		private NoexecVolumeDetector() {
		}
	}


	private static Class<?> tryToLoadClass(final ClassLoader loader, final Class<?> helper) throws ClassNotFoundException {
		try {
			return Class.forName(helper.getName(), false, loader);
		} catch (ClassNotFoundException var7) {
			if (loader == null) {
				throw var7;
			} else {
				try {
					final byte[] classBinary = classToByteArray(helper);
					return (Class) AccessController.doPrivileged(new PrivilegedAction<Class<?>>() {
						public Class<?> run() {
							try {
								Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE);
								defineClass.setAccessible(true);
								return (Class)defineClass.invoke(loader, helper.getName(), classBinary, 0, classBinary.length);
							} catch (Exception var2) {
								throw new IllegalStateException("Define class failed!", var2);
							}
						}
					});
				} catch (ClassNotFoundException var4) {
					ThrowableUtil.addSuppressed(var4, var7);
					throw var4;
				} catch (RuntimeException var5) {
					ThrowableUtil.addSuppressed(var5, var7);
					throw var5;
				} catch (Error var6) {
					ThrowableUtil.addSuppressed(var6, var7);
					throw var6;
				}
			}
		}
	}

	private static byte[] classToByteArray(Class<?> clazz) throws ClassNotFoundException {
		String fileName = clazz.getName();
		int lastDot = fileName.lastIndexOf(46);
		if (lastDot > 0) {
			fileName = fileName.substring(lastDot + 1);
		}

		URL classUrl = clazz.getResource(fileName + ".class");
		if (classUrl == null) {
			throw new ClassNotFoundException(clazz.getName());
		} else {
			byte[] buf = new byte[1024];
			ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
			InputStream in = null;

			try {
				in = classUrl.openStream();

				int r;
				while((r = in.read(buf)) != -1) {
					out.write(buf, 0, r);
				}

				byte[] var13 = out.toByteArray();
				return var13;
			} catch (IOException var11) {
				throw new ClassNotFoundException(clazz.getName(), var11);
			} finally {
				closeQuietly(in);
				closeQuietly(out);
			}
		}
	}


	private static void loadLibraryByHelper(final Class<?> helper, final String name, final boolean absolute) throws UnsatisfiedLinkError {
		Object ret = AccessController.doPrivileged(new PrivilegedAction<Object>() {
			public Object run() {
				try {
					Method method = helper.getMethod("loadLibrary", String.class, Boolean.TYPE);
					method.setAccessible(true);
					return method.invoke((Object)null, name, absolute);
				} catch (Exception var2) {
					return var2;
				}
			}
		});
		if (ret instanceof Throwable) {
			Throwable t = (Throwable)ret;

			assert !(t instanceof UnsatisfiedLinkError) : t + " should be a wrapper throwable";

			Throwable cause = t.getCause();
			if (cause instanceof UnsatisfiedLinkError) {
				throw (UnsatisfiedLinkError)cause;
			} else {
				UnsatisfiedLinkError ule = new UnsatisfiedLinkError(t.getMessage());
				ule.initCause(t);
				throw ule;
			}
		}
	}

	public static void loadLibrary(String libName, boolean absolute) {
		if (absolute) {
			System.out.println("trying absolute: " + libName);
			System.load(libName);
			System.out.println("after");
		}
		else {
			System.out.println("trying relative: " + libName);
			System.loadLibrary(libName);
			System.out.println("after");
		}
	}
}
