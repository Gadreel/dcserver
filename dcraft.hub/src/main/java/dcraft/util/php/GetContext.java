package dcraft.util.php;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.script.StackUtil;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;

public class GetContext extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        try {
            return PhpUtil.structToValue(env, OperationContext.getOrThrow());
        }
        catch (OperatingContextException x) {
            return NullValue.NULL;
        }
    }
}
