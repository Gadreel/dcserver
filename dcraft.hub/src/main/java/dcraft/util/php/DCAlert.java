package dcraft.util.php;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.tool.backup.BackupUtil;

public class DCAlert extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        if (args.length > 0) {
            if (args[0] instanceof StringValue) {
                try {
                    BackupUtil.notifyProgress(((StringValue)args[0]).toString());
                }
                catch (OperatingContextException e) {
                    // NA
                }
            }
        }

        return NullValue.NULL;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getName() + "]";
    }
}
