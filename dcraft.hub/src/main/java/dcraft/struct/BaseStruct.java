package dcraft.struct;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.SchemaHub;
import dcraft.script.StackUtil;
import dcraft.script.inst.LogicBlockState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

abstract public class BaseStruct implements IPartSelector {
    protected DataType explicitType = null;

    // override this to return implicit type if no explicit exists
    public DataType getType() {
        return this.explicitType;
    }

    public BaseStruct withType(DataType v) {
        this.explicitType = v;
        return this;
    }

    public BaseStruct withType(String v) {
        this.explicitType = SchemaHub.getTypeOrError(v);
        return this;
    }

    public boolean hasExplicitType() {
        return (this.explicitType != null);
    }

    public BaseStruct() {
    }

    public BaseStruct(DataType type) {
        this.explicitType = type;
    }

    /**
     * A way to select a child or sub child structure similar to XPath but lightweight.
     * Can select composites and scalars.  Use a . or / delimiter.
     *
     * For example: "Toys.3.Name" called on "Person" Record means return the (Struct) name of the
     * 4th toy in this person's Toys list.
     *
     * Cannot go up levels, or back to root.  Do not start with a dot or slash as in ".People".
     *
     * @param path string holding the path to select
     * @return selected structure if any, otherwise null
     */
    @Override
    public BaseStruct select(String path) {
        return this.select(PathPart.parse(path));
    }

    /** _Tr
     * A way to select a child or sub child structure similar to XPath but lightweight.
     * Can select composites and scalars.  Use a . or / delimiter.
     *
     * For example: "Toys.3.Name" called on "Person" Record means return the (Struct) name of the
     * 4th toy in this person's Toys list.
     *
     * Cannot go up levels, or back to root.  Do not start with a dot or slash as in ".People".
     *
     * @param path parts of the path holding a list index or a field name
     * @return selected structure if any, otherwise null
     */
    @Override
    public BaseStruct select(PathPart... path) {
        if (path.length == 0)
            return this;

        return null;
    }

    // just a reminder of the things to override in types

    @Override
    public Object clone() {
        return this.deepCopy();
    }

    @Override
    abstract public String toString();

    protected void doCopy(BaseStruct n) {
        n.explicitType = this.explicitType;
    }

    abstract public BaseStruct deepCopy();

    /**
     * @return true if contains no data or insufficient data to constitute a complete value
     */
    abstract public boolean isEmpty();

    /**
     *
     * @return true if it really is null (scalars only, composites are never null)
     */
    abstract public boolean isNull();

    public boolean validate() {
        return this.validate(this.explicitType);
    }

    public boolean validate(String type) {
        return this.validate(SchemaHub.getTypeOrError(type));
    }

    public boolean validate(DataType type) {
        if (type == null) {
            Logger.errorTr(522);
            return false;
        }

        return type.validate(true, false, this);
    }

    public boolean validateIncomplete() {
        return this.validateIncomplete(this.explicitType);
    }

    public boolean validateIncomplete(String type) {
        return this.validateIncomplete(SchemaHub.getTypeOrError(type));
    }

    public boolean validateIncomplete(DataType type) {
        if (type == null) {
            Logger.errorTr(522);
            return false;
        }

        return type.validate(false, false, this);
    }

    public void checkLogic(IParentAwareWork stack, XElement source, LogicBlockState logicState) throws OperatingContextException {
        if (source.hasAttribute("IsNull")) {
            if (logicState.pass)
                logicState.pass = StackUtil.boolFromElement(stack, source, "IsNull") ? this.isNull() : ! this.isNull();

            logicState.checked = true;
        }

        if (source.hasAttribute("IsEmpty")) {
            if (logicState.pass)
                logicState.pass = StackUtil.boolFromElement(stack, source, "IsEmpty") ? this.isEmpty() : ! this.isEmpty();

            logicState.checked = true;
        }
    }

    public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
        if ("Validate".equals(code.getName()))
            this.validate();
        else
            Logger.error("operation failed, op name not recognized: " + code.getName());

        return ReturnOption.CONTINUE;
    }
}
