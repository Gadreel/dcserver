package dcraft.util.php;

import com.caucho.quercus.env.BooleanValue;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.util.StringUtil;

public class HasBadge extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        try {
            UserContext user = OperationContext.getOrThrow().getUserContext();

            for (int i = 0; i < args.length; i++) {
                String badge = args[i].toString();

                if (StringUtil.isNotEmpty(badge) && user.isTagged(badge)) {
                    return BooleanValue.TRUE;
                }
            }
        }
        catch (OperatingContextException x) {
        }

        return BooleanValue.FALSE;
    }
}
