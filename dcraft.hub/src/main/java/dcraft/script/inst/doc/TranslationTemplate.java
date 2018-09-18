package dcraft.script.inst.doc;

import dcraft.cms.util.FeedUtil;
import dcraft.db.DbServiceRequest;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.inst.OperationsInstruction;
import dcraft.script.work.*;
import dcraft.service.ServiceHub;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class TranslationTemplate extends Instruction {
	static public TranslationTemplate tag() {
		TranslationTemplate el = new TranslationTemplate();
		el.setName("dcs.TranslationTemplate");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return TranslationTemplate.tag();
	}
	
	/*
	<dcs.TranslationTemplate Name="Title">
		<Tr Locale="eng" Value="Apprentice Hired - {$Data.User.DisplayName}  Needs EC" />
	</dcs.TranslationTemplate>
	
	 */
	
	// TODO add error checking


	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String name = StackUtil.stringFromSource(state, "Name");

			Translator var = new Translator();

			var.src = this;

			StackUtil.addVariable(state, name, var);
		}

		return ReturnOption.CONTINUE;
	}

	public class Translator extends RecordStruct {
		protected XElement src = null;

		@Override
		public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
			if ("Translate".equals(code.getName())) {
				String defloc = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

				// TODO try to take the recipient's preferred locale into consideration

				XElement tr = FeedUtil.bestMatch(this.src, defloc, defloc);

				if (tr != null) {
					// clean yes, because the way this will be used in CMS
					String out = StackUtil.resolveValueToString(state, tr.getValue(), true);

					//System.out.println("cool: " + out);

					String name = StackUtil.stringFromElement(state, code, "Result");

					if (StringUtil.isNotEmpty(name))
						StackUtil.addVariable(state, name, StringStruct.of(out));
				}

				return ReturnOption.CONTINUE;
			}

			return super.operation(state, code);
		}
	}
}
