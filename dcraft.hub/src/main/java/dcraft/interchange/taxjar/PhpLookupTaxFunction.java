package dcraft.interchange.taxjar;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.interchange.authorize.AuthUtil;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.php.PhpUtil;

public class PhpLookupTaxFunction extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        if (args.length > 0) {
            RecordStruct tx = Struct.objectToRecord(PhpUtil.valueToStruct(env, args[0]));

            if (tx != null) {
                return PhpUtil.structToValue(env, TaxJarUtil.lookupTaxSync(tx, null));
            }
        }

        return NullValue.NULL;
    }
}