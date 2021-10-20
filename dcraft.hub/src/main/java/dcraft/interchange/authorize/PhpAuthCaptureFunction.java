package dcraft.interchange.authorize;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.php.PhpUtil;
import dcraft.web.md.MarkdownUtil;
import dcraft.xml.XElement;

public class PhpAuthCaptureFunction extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        if (args.length > 0) {
            RecordStruct tx = Struct.objectToRecord(PhpUtil.valueToStruct(env, args[0]));

            try {
                String origin = OperationContext.getOrThrow().getOrigin();

                if (! tx.hasField("IPAddress"))
                    tx.with("IPAddress", origin.substring(origin.indexOf(':') + 1));
            }
            catch (OperatingContextException x) {
                //
            }

            if (tx != null) {
                return PhpUtil.structToValue(env, AuthUtil.authCaptureTransactionSync(tx, null));
            }
        }

        return NullValue.NULL;
    }
}
