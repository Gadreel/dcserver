package dcraft.interchange.authorize;

import com.caucho.quercus.env.DoubleValue;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.Struct;
import dcraft.util.php.PhpUtil;

import java.math.BigDecimal;

public class PhpCancelPartialTransaction extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        if (args.length > 2) {
            String refid = args[0].toString();
            String txid = args[1].toString();
            BigDecimal amount = Struct.objectToDecimal(PhpUtil.valueToStruct(env, args[2]));

            if (txid != null) {
                try {
                    return PhpUtil.structToValue(env, AuthUtil.cancelPartialTransactionSync(refid, txid, amount, null));
                }
                catch (OperatingContextException x) {
                }
            }
        }

        return NullValue.NULL;
    }
}
