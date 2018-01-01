package dcraft.util.groovy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import dcraft.log.Logger;

/*
 * /buckets
 * /services
 * /www
 * /www-preview
 * 
 * /glib
 * 
 */
public class GCompClassLoader extends ClassLoader {
	/* groovy
import groovy.lang.GroovyObject;

import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
	
	 */
	protected final Lock compilelock = new ReentrantLock();
	
	protected Path target = null;
	protected Path lib = null;
	
	public void init(Path target, Path lib) {
		this.target = target;
		this.lib = lib;
		
		this.compilelock.lock();
		
		try {
			Files.createDirectories(this.target);
			
	/* groovy
			CompilerConfiguration configuration = new CompilerConfiguration();
			
			configuration.setClasspath(lib.toString());
			configuration.setTargetDirectory(target.toFile());
			
			configuration.getOptimizationOptions().put("int", false);
			configuration.getOptimizationOptions().put("indy", true);

			CompilationUnit unit = new CompilationUnit(configuration);
			
			// NOTE: unable to get GroovyC refuses to compile just certain library classes, it is an all or nothing thing
			// for the best since we probably want to clear out the old library class files anyway, if anything goes wrong 
			// with this class loader it is likely to be with bad lib files, not with the gNNN files
			
			if (Files.exists(this.lib)) {
				// create a list of all libraries, check to see if class file is stale or missing
				List<String> libs = new ArrayList<String>();
				AtomicBoolean fndany = new AtomicBoolean(false);
				
				Files.walkFileTree(this.lib, new FileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
						return FileVisitResult.CONTINUE;
					}
	
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						Path rel = lib.relativize(file);
						
						String lname = rel.toString();
						
						if (lname.endsWith(".groovy")) {
							Path cpath = target.resolve(rel).getParent();
							
							cpath = cpath.resolve(rel.getFileName().toString().replace(".groovy", ".class"));
							
							// if source is newer than bin
							if (Files.notExists(cpath) || (Files.getLastModifiedTime(file).toMillis() > Files.getLastModifiedTime(cpath).toMillis())) 
								fndany.set(true);
							
							lname = lname.replace('\\', '.').replace('/', '.');
							
							libs.add(lname.substring(0, lname.length() - 7));
						}
						
						return FileVisitResult.CONTINUE;
					}
	
					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
						return FileVisitResult.CONTINUE;
					}
	
					@Override
					public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
						return FileVisitResult.CONTINUE;
					}
				});
				
				// nothing needs updating
				if (!fndany.get())
					return;
				
				// remove stale .class files for lib 
				Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						Path rel = target.relativize(file);
						
						String lname = rel.getFileName().toString();
						
						if (rel.getNameCount() > 1)
							Files.delete(file);
						else if (!lname.startsWith("g") && !lname.equals(".DS_Store"))
							Files.delete(file);
						
						return FileVisitResult.CONTINUE;
					}
					
					@Override
					public FileVisitResult postVisitDirectory(Path file, IOException x1) throws IOException {
						if (x1 != null)
							throw x1;
						
						Path rel = target.relativize(file);
						
						if (rel.getNameCount() > 1)
							Files.delete(file);
						
						return FileVisitResult.CONTINUE;
					}
				});
				
				// if any file needs to be compiled TODO make this an INFO
				Logger.info("Compiling libs: " + libs.toString());
				
				StringBuilder gcode = new StringBuilder();
				
				for (String l : libs)
					gcode.append("import " + l + "\n");
					
				gcode.append("\n");
				gcode.append("void main() {\n");
				gcode.append("	println \"libraries checked/compiled\"\n");
				gcode.append("}\n");
				
				String hash = HashUtil.getMd5(gcode.toString());
				
				unit.addSource("g" + hash + ".groovy", gcode.toString());
				unit.compile();		
			}
			*/
		}
		catch (Exception x) {
			Logger.error("Unable to prepare libaries for: " + this.lib + " - error: " + x);
		}
		finally {
			this.compilelock.unlock();
		}
	}
	
	/* groovy
	protected String compile(CharSequence code) throws IOException {
		this.compilelock.lock();
		
		try {
			String hash = HashUtil.getMd5(code.toString());
			String fname = "g" + hash + ".class";
			String sname = "g" + hash + ".groovy";
			
			Path classfile = this.target.resolve(fname);
			
			if (Files.exists(classfile))
				return fname;
			
			CompilerConfiguration configuration = new CompilerConfiguration();
			
			//Path glib
			configuration.setClasspath(this.target.toString());
			configuration.setTargetDirectory(this.target.toFile());
			
			configuration.getOptimizationOptions().put("int", false);
			configuration.getOptimizationOptions().put("indy", true);

			CompilationUnit unit = new CompilationUnit(configuration);
			
			unit.addSource(sname, code.toString());
			unit.compile();		
			
			return fname;
		}
		finally {
			this.compilelock.unlock();
		}
	}
	
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		Path cpath = this.target.resolve(name.replace('.', '/') + ".class");
		
		if (Files.exists(cpath)) {
			Memory mem = IOUtil.readEntireFileToMemory(cpath);
			return super.defineClass(name, mem.toArray(), 0, mem.getLength());
		}
		
		return super.findClass(name);
	}

	public Class<?> toClass(Path src) throws FileNotFoundException, ClassNotFoundException, IOException {
		if (Files.notExists(src))
			throw new FileNotFoundException("Missing file: " + src);
		
		CharSequence coderes = IOUtil.readEntireFile(src);
		
		if (coderes == null)
			throw new FileNotFoundException("Missing or unreadable file: " + src);
		
		String code = coderes.toString();
		
		 return this.toClass(code);
	}

	public Class<?> toClass(String code) throws ClassNotFoundException, IOException {
		String hash = HashUtil.getMd5(code);
		
		Path dst = this.target.resolve("g" + hash + ".class");
		
		if (Files.notExists(dst))
			this.compile(code);
		
		 return this.loadClass("g" + hash);
	}

	public void execute(Path src, String method, Object... args) throws FileNotFoundException, ClassNotFoundException, 
		InstantiationException, IllegalAccessException, IOException 
	{
		if (Files.notExists(src))
			throw new FileNotFoundException("Missing file: " + src);
		
		CharSequence coderes = IOUtil.readEntireFile(src);
		
		if (coderes == null)
			throw new FileNotFoundException("Missing or unreadable file: " + src);
		
		String code = coderes.toString();
		
		this.execute(code, method, args);
	}

	public void execute(String code, String method, Object... args) throws ClassNotFoundException, 
		InstantiationException, IllegalAccessException, IOException
	{
		Class<?> groovyClass = this.toClass(code);
		
		GroovyObject script = (GroovyObject) groovyClass.newInstance();
		
		GCompClassLoader.tryExecuteMethodCtx(script, method, args);
	}

	public void executeClass(String name, String method, Object... args) throws ClassNotFoundException, 
		InstantiationException, IllegalAccessException 
	{
		Class<?> groovyClass = this.loadClass(name);
		
		GroovyObject script = (GroovyObject) groovyClass.newInstance();
		
		GCompClassLoader.tryExecuteMethodCtx(script, method, args);
	}
	
	static public boolean tryExecuteMethodCtx(GroovyObject script, String name, Object... params) {
		if (script == null) 
			return false;

		/ --
		OperationContext oc = OperationContext.get();
		
		scriptold.setProperty("_ctx", oc);
		scriptold.setProperty("_usr", oc.getUserContext());
		scriptold.setProperty("_ten", oc.getTenant());
		scriptold.setProperty("_sit", oc.getSite());
		-- /
		
		return GCompClassLoader.tryExecuteMethod(script, name, params);
	}
	
	static public boolean tryExecuteMethod(GroovyObject script, String name, Object... params) {
		if (script == null) 
			return false;
		
		Method runmeth = null;
		
		for (Method m : script.getClass().getMethods()) {
			if (!m.getName().equals(name))
				continue;
			
			runmeth = m;
			break;
		}
		
		if (runmeth == null) 
			return false;
		
		if (params == null)
			params = new Object[] { };
		
		try {
			script.invokeMethod(name, params);
			
			return true;
		}
		catch (Exception x) {
			Logger.error("Unable to execute scriptold!");
			Logger.error("Error: " + x);
		}		
		
		return false;
	}
	*/
}
