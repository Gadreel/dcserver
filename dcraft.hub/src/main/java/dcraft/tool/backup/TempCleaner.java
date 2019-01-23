package dcraft.tool.backup;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceTier;
import dcraft.hub.resource.SslEntry;
import dcraft.hub.resource.TrustResource;
import dcraft.log.Logger;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.util.FileUtil;
import dcraft.util.KeyUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.stream.Stream;

public class TempCleaner implements IWork {
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		Path directory = Paths.get("./temp");
		long expire = ZonedDateTime.now().minusHours(8).toInstant().toEpochMilli();
		
		if (Files.exists(directory) && Files.isDirectory(directory)) {
			try (Stream<Path> strm = Files.list(directory)) {
				strm.forEach(file -> {
					try {
						FileTime time = Files.getLastModifiedTime(file);
						
						if (time.toMillis() < expire) {
							String fname = file.getFileName().toString();
							
							if (! fname.startsWith("librocksdbjni")) {
								if (Files.isDirectory(file))
									FileUtil.deleteDirectory(file);
								else
									Files.delete(file);
							}
						}
					}
					catch (Exception x) {
						Logger.warn("Unable to access or delete file: " + x);
					}
				});
			}
			catch (IOException x) {
				Logger.warn("Unable to list directory contents: " + x);
			}
		}
		
		taskctx.returnEmpty();
	}
}
