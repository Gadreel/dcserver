package dcraft.interchange.authorize;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.php.PhpUtil;

import java.util.ArrayList;
import java.util.List;

public class PhpGetTransactionList extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        if (args.length > 0) {
            String batchid = args[0].toString();

            List<String> filter = new ArrayList<>();

            if (args.length > 1) {
                ListStruct filters = Struct.objectToList(PhpUtil.valueToStruct(env, args[1]));

                if (filters != null) {
                    for (Struct fltr : filters.items()) {
                        if (fltr != null)
                            filter.add(fltr.toString());
                    }
                }
            }

            if (StringUtil.isNotEmpty(batchid)) {
                try {
                    return PhpUtil.structToValue(env, AuthUtil.getTransactionListAllPagesSync(batchid, null, filter));
                }
                catch (OperatingContextException x) {
                    //
                }
            }
        }

        return NullValue.NULL;
    }
}
