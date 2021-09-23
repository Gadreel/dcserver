package dcraft.util.php;

import com.caucho.quercus.env.*;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.*;

import java.util.Iterator;
import java.util.Map;

public class PhpUtil {
    static public Value structToValue(Env env, Struct source) {
        if ((source == null) || (source instanceof NullStruct))
            return NullValue.NULL;

        if (source instanceof ListStruct) {
            ListStruct list = (ListStruct) source;

            ArrayValueImpl result = new ArrayValueImpl();

            for (int i = 0; i < list.size(); i++) {
                result.append(PhpUtil.structToValue(env, list.getItem(i)));
            }

            return result;
        }

        if (source instanceof RecordStruct) {
            RecordStruct rec = (RecordStruct) source;

            ObjectExtValue result = new ObjectExtValue(env, env.getQuercus().getStdClass());

            for (FieldStruct field : rec.getFields()) {
                result.putField(env, (StringValue) StringValue.create(field.getName()), PhpUtil.structToValue(env, field.getValue()));
            }

            return result;
        }

        if (source instanceof StringStruct) {
            StringStruct in = (StringStruct) source;

            return StringValue.create(in.getValue());
        }

        if (source instanceof DecimalStruct) {
            DecimalStruct in = (DecimalStruct) source;

            return DoubleValue.create(in.getValue());
        }

        if (source instanceof IntegerStruct) {
            IntegerStruct in = (IntegerStruct) source;

            return LongValue.create(in.getValue());
        }

        if (source instanceof BooleanStruct) {
            BooleanStruct in = (BooleanStruct) source;

            return BooleanValue.create(in.getValue());
        }

        return NullValue.NULL;
    }

    static public Struct valueToStruct(Env env, Value source) {
        if ((source == null) || (source instanceof NullValue))
            return NullStruct.instance;

        //System.out.println("- " + source.getClass().getCanonicalName());

        if (source instanceof Var) {
            source = ((Var) source).toValue();
        }

        if (source instanceof ArrayValue) {
            ListStruct result = ListStruct.list();

            ArrayValue s = (ArrayValue) source;

            // TODO detect if this is a list or a keyed array (hash)

            for (Value v : s.values()) {
                result.with(PhpUtil.valueToStruct(env, v));
            }

            return result;
        }

        if (source instanceof ObjectExtValue) {
            RecordStruct result = RecordStruct.record();

            ObjectExtValue s = (ObjectExtValue) source;

            Iterator<java.util.Map.Entry<Value, Value>> iter = s.getIterator(env);

            while(iter.hasNext()) {
                java.util.Map.Entry<Value, Value> entry = iter.next();
                Value key = entry.getKey();
                Value value = entry.getValue();

                result.with(((StringValue) key).toString(), PhpUtil.valueToStruct(env, value));
            }

            return result;
        }

        if (source instanceof StringValue) {
            return StringStruct.of(((StringValue)source).toString(env));
        }

        if (source instanceof BooleanValue) {
            return BooleanStruct.of(((BooleanValue)source).toBoolean());
        }

        if (source instanceof DoubleValue) {
            return DecimalStruct.of(((DoubleValue)source).toDouble());
        }

        if (source instanceof LongValue) {
            return IntegerStruct.of(((LongValue)source).toLong());
        }

        return NullStruct.instance;
    }
}
