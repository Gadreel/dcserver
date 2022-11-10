package dcraft.struct.format;

import dcraft.struct.ListStruct;

abstract public class ListAwareFormatter implements IFormatter {
    // typically treat null as a formatting fail and just skip it,
    // however in some cases we may want null to be a potential output
    // for those cases the subclass should set this to false
    protected boolean ignoreIfFail = true;

    @Override
    public FormatResult format(Object value, String op, String format) {
        // formatters are reused so don't provide a pre-process option
        //this.initializeFormat(op, format);

        if (value instanceof ListStruct) {
            ListStruct list = (ListStruct) value;
            ListStruct newlist = ListStruct.list();

            for (int i = 0; i < list.size(); i++) {
                Object val = this.formatInternal(list.getItem(i), op, format);

                if ((! this.ignoreIfFail) || (val != null))
                    newlist.with(val);
            }

            return FormatResult.result(newlist);
        }
        else {
            // note that with most formatters
            Object val = this.formatInternal(value, op, format);

            if ((! this.ignoreIfFail) || (val != null))
                value = val;
        }

        return FormatResult.result(value);
    }

    abstract Object formatInternal(Object value, String op, String format);
}
