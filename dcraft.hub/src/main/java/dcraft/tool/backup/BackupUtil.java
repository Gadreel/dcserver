package dcraft.tool.backup;

import dcraft.hub.op.OperatingContextException;
import dcraft.interchange.slack.SlackUtil;
import dcraft.struct.RecordStruct;

public class BackupUtil {
	static public void notifyProgress(String msg) throws OperatingContextException {
		RecordStruct data = RecordStruct.record()
				.with("text", msg);
			//			data.with("icon_url",indexurl +"imgs/logo152.png");
	
		SlackUtil.serverEvent(null, data, null);
	}
}
