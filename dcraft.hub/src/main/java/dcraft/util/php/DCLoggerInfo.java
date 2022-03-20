package dcraft.util.php;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.log.Logger;

public class DCLoggerInfo extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        if (args.length > 0) {
            if (args[0] instanceof StringValue) {
                //System.out.println("debugger: " + ((StringValue)args[0]).toString());
                Logger.info(((StringValue)args[0]).toString());
            }
        }

        return NullValue.NULL;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getName() + "]";
    }
}
