package dcraft.util.php;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;

public class DCTouch extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        OperationContext.getOrNull().touch();

        return NullValue.NULL;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getName() + "]";
    }
}
