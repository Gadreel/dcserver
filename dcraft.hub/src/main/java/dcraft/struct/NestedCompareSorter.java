package dcraft.struct;

import java.util.Comparator;

public class NestedCompareSorter implements Comparator<BaseStruct> {
    static public NestedCompareSorter of(ListStruct list) {
        if (list.size() < 1)
            return null;

        RecordStruct rec = list.getItemAsRecord(0);

        NestedCompareSorter root = NestedCompareSorter.of(rec.getFieldAsString("Field"), rec.getFieldAsBooleanOrFalse("Descending"));
        NestedCompareSorter current = root;

        for (int i = 1; i < list.size(); i++) {
            rec = list.getItemAsRecord(i);

            NestedCompareSorter next = NestedCompareSorter.of(rec.getFieldAsString("Field"), rec.getFieldAsBooleanOrFalse("Descending"));

            current.withNested(next);
            current = next;
        }

        return root;
    }

    static public NestedCompareSorter of(RecordStruct rec) {
        return NestedCompareSorter.of(rec.getFieldAsString("Field"), rec.getFieldAsBooleanOrFalse("Descending"));
    }

    static public NestedCompareSorter of(String fieldname, boolean descending) {
        NestedCompareSorter compareSorter = new NestedCompareSorter();

        compareSorter.fieldname = fieldname;
        compareSorter.descending = descending;

        return compareSorter;
    }

    protected String fieldname = null;
    protected boolean descending = false;
    protected NestedCompareSorter nested = null;

    public NestedCompareSorter withNested(NestedCompareSorter v) {
        this.nested = v;
        return this;
    }

    @Override
    public int compare(BaseStruct o1, BaseStruct o2) {
        BaseStruct fld1 = ((RecordStruct)o1).getField(this.fieldname);
        BaseStruct fld2 = ((RecordStruct)o2).getField(this.fieldname);

        int result = 0;

        if ((fld1 == null) && (fld2 == null)) {
            result = 0;
        }
        else if (fld1 == null) {
            result = this.descending ? -1 : 1;
        }
        else if (fld2 == null) {
            result = this.descending ? 1 : -1;
        }
        else if ((fld1 instanceof ScalarStruct) && (fld2 instanceof ScalarStruct)) {
            result = ((ScalarStruct) fld1).compareToIgnoreCase(fld2);

            if (descending)
                result = -result;
        }

        if ((result == 0) && (this.nested != null))
            return this.nested.compare(o1, o2);

        return result;
    }
}
