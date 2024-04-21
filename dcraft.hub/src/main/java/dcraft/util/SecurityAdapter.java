package dcraft.util;

import com.samstevens.totp.exceptions.CodeGenerationException;
import com.samstevens.totp.exceptions.QrGenerationException;
import com.samstevens.totp.util.TotpUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.TaskContext;
import dcraft.util.map.MapUtil;
import dcraft.xml.XElement;
import org.mindrot.BCrypt;

import java.io.IOException;

public class SecurityAdapter extends RecordStruct {
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("GenerateTotpSecret".equals(code.getName())) {
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			String secret = TotpUtil.generateSecret();

			if (StringUtil.isNotEmpty(handle)) {
				if (StringUtil.isEmpty(secret))
					StackUtil.addVariable(stack, handle, NullStruct.instance);
				else
					StackUtil.addVariable(stack, handle, StringStruct.of(secret));
			}

			return ReturnOption.CONTINUE;
		}

		if ("GenerateTotpQRCode".equals(code.getName())) {
			String label = Struct.objectToString(StackUtil.refFromElement(stack, code, "Label", true));
			String issuer = Struct.objectToString(StackUtil.refFromElement(stack, code, "Issuer", true));
			String secret = Struct.objectToString(StackUtil.refFromElement(stack, code, "Secret", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			try {
				String qrcode = TotpUtil.generateQRCode(secret, label, issuer);

				if (StringUtil.isNotEmpty(handle)) {
					if (StringUtil.isEmpty(qrcode))
						StackUtil.addVariable(stack, handle, NullStruct.instance);
					else
						StackUtil.addVariable(stack, handle, StringStruct.of(qrcode));
				}
			}
			catch (IOException x) {
				if (StringUtil.isNotEmpty(handle)) {
					StackUtil.addVariable(stack, handle, NullStruct.instance);
				}

				// TODO warn with code?

				System.out.println("qr generation io error: " + x);
			}
			catch (QrGenerationException x) {
				if (StringUtil.isNotEmpty(handle)) {
					StackUtil.addVariable(stack, handle, NullStruct.instance);
				}

				// TODO warn with code?

				System.out.println("qr generation error: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("GenerateTotpAuthCode".equals(code.getName())) {
			String secret = Struct.objectToString(StackUtil.refFromElement(stack, code, "Secret", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			try {
				String authCode = TotpUtil.generateAuthCode(secret);

				if (StringUtil.isNotEmpty(handle)) {
					if (StringUtil.isEmpty(authCode))
						StackUtil.addVariable(stack, handle, NullStruct.instance);
					else
						StackUtil.addVariable(stack, handle, StringStruct.of(authCode));
				}
			}
			catch (CodeGenerationException x) {
				if (StringUtil.isNotEmpty(handle)) {
					StackUtil.addVariable(stack, handle, NullStruct.instance);
				}

				// TODO warn with code?

				System.out.println("auth generation error: " + x);
			}

			return ReturnOption.CONTINUE;
		}

		if ("ValidateTotpAuthCode".equals(code.getName())) {
			String secret = Struct.objectToString(StackUtil.refFromElement(stack, code, "Secret", true));
			String authCode = Struct.objectToString(StackUtil.refFromElement(stack, code, "AuthCode", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			boolean valid = TotpUtil.isValidAuthCode(secret, authCode);

			if (StringUtil.isNotEmpty(handle)) {
				StackUtil.addVariable(stack, handle, BooleanStruct.of(valid));
			}

			return ReturnOption.CONTINUE;
		}

		if ("HashBCryptPassword".equals(code.getName())) {
			String password = Struct.objectToString(StackUtil.refFromElement(stack, code, "Password", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			String hashpw = BCrypt.hashpw(password, BCrypt.gensalt());

			if (StringUtil.isNotEmpty(handle)) {
				if (StringUtil.isEmpty(hashpw))
					StackUtil.addVariable(stack, handle, NullStruct.instance);
				else
					StackUtil.addVariable(stack, handle, StringStruct.of(hashpw));
			}

			return ReturnOption.CONTINUE;
		}

		if ("CheckBCryptPassword".equals(code.getName())) {
			String password = Struct.objectToString(StackUtil.refFromElement(stack, code, "Password", true));
			String hashed = Struct.objectToString(StackUtil.refFromElement(stack, code, "Hashed", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			boolean valid = BCrypt.checkpw(password, hashed);

			if (StringUtil.isNotEmpty(handle)) {
				StackUtil.addVariable(stack, handle, BooleanStruct.of(valid));
			}

			return ReturnOption.CONTINUE;
		}

		if ("EncryptString".equals(code.getName())) {
			String value = Struct.objectToString(StackUtil.refFromElement(stack, code, "Value", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			String secureValue = TaskContext.getOrThrow().getTenant().getObfuscator().encryptStringToHex(value);

			if (StringUtil.isNotEmpty(handle)) {
				StackUtil.addVariable(stack, handle, StringStruct.of(secureValue));
			}

			return ReturnOption.CONTINUE;
		}

		if ("DecryptString".equals(code.getName())) {
			String value = Struct.objectToString(StackUtil.refFromElement(stack, code, "Value", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			String secureValue = TaskContext.getOrThrow().getTenant().getObfuscator().decryptHexToString(value);

			if (StringUtil.isNotEmpty(handle)) {
				StackUtil.addVariable(stack, handle, StringStruct.of(secureValue));
			}

			return ReturnOption.CONTINUE;
		}

		if ("HashString".equals(code.getName())) {
			String value = Struct.objectToString(StackUtil.refFromElement(stack, code, "Value", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			String hashValue = TaskContext.getOrThrow().getTenant().getObfuscator().hashStringToHex(value);

			if (StringUtil.isNotEmpty(handle)) {
				StackUtil.addVariable(stack, handle, StringStruct.of(hashValue));
			}

			return ReturnOption.CONTINUE;
		}

		return super.operation(stack, code);
	}
}
