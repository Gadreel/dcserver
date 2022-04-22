package dcraft.web.adapter;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.Value;
import com.caucho.vfs.ReadStream;
import dcraft.filestore.CommonPath;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.quercus.DCQuercusEngine;
import dcraft.quercus.DCQuercusResult;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.GzipStream;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.Memory;
import dcraft.util.php.PhpUtil;
import dcraft.util.php.QuercusUtil;
import dcraft.web.HttpDestStream;
import dcraft.web.IOutputWork;
import dcraft.web.Response;
import dcraft.web.WebController;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.codec.Charsets;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public class PHPOutputAdapter extends ChainWork implements IOutputWork {
	public CommonPath webpath = null;
	public Path file = null;

	@Override
	public CommonPath getPath() {
		return this.webpath;
	}

	@Override
	public void init(Site site, Path file, CommonPath web, String view) {
		this.webpath = web;
		this.file = file;
	}

	@Override
	public void init(TaskContext taskctx) throws OperatingContextException {
		WebController wctrl = (WebController) taskctx.getController();

		RecordStruct request = wctrl.getFieldAsRecord("Request");

		String postdata = request.getFieldAsString("PostData");
		RecordStruct params = request.getFieldAsRecord("Parameters");

		DCQuercusEngine engine = QuercusUtil.getEngine();

		Response resp = wctrl.getResponse();

		resp.setDateHeader("Date", System.currentTimeMillis());
		resp.setHeader("X-UA-Compatible", "IE=Edge,chrome=1");
		resp.setHeader("Cache-Control", "no-cache");

		String output = "error";

		try (ReadStream filein = QuercusUtil.fromPath(this.file)) {
			DCQuercusResult result = engine.dc_execute(filein, new Consumer<Env>() {
				@Override
				public void accept(Env env) {
					env.setGlobalValue("dc_parameters", PhpUtil.structToValue(env, params));
					env.setGlobalValue("dc_post_raw", env.createString(postdata));
					env.setGlobalValue("dc_test_unicode", env.createString("a 🦝 z"));
					env.setGlobalValue("dc_workingpath", env.createString(taskctx.getTenant().resolvePath("/www").toString()));
				}
			}, new Consumer<Env>() {
				@Override
				public void accept(Env env) {
					Value location = env.getGlobalValue("dc_location");

					if ((location != null) && ! (location instanceof NullValue))
						resp.setHeader("Location", location.toString());

					//System.out.println("location: " + env.getGlobalValue("dc_location"));
				}
			});

			//System.out.println("output: " + result.output);

			output = result.output;
		}
		catch (IOException x) {
			Logger.error("error with PHP code: " + x);

			output = RecordStruct.record()
					.with("Result", "1")
					.with("Message", x.toString())
					.toString();
		}

		if (resp.getFieldAsRecord("Headers").isNotFieldEmpty("Location")) {
			resp.setStatus(HttpResponseStatus.FOUND);
			wctrl.send();
			return;
		}

		// not a redirect

		resp.setHeader("Content-Encoding", "gzip");
		resp.setHeader("Content-Type", "text/html; charset=utf-8");

		wctrl.sendStart(0);

		// it seems that memory store file call to "with" string results in an UTF-8 encoding that leaves out some unicode characters - this is not desirable...
		// temp solution is to make the memory ourselves
		byte[] outbytes = output.getBytes(Charsets.UTF_8);
		Memory outmem = new Memory(outbytes);

		StreamFragment fsource = MemoryStoreFile.of(webpath)
				.with(outmem).allocStreamSrc();

		HttpDestStream dest = HttpDestStream.dest();
		dest.setHeaderSent(true);

		fsource.withAppend(GzipStream.create(), dest);

		// the stream work should happen after `resume` in decoder above
		this.then(StreamWork.of(fsource));
	}
}
