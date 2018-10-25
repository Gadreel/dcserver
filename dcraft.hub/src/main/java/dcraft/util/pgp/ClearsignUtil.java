package dcraft.util.pgp;

import java.io.*;
import java.security.SignatureException;

import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.HexUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.io.CharSequenceReader;
import dcraft.util.io.InputWrapper;
import dcraft.util.io.OutputWrapper;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.util.Strings;

public class ClearsignUtil {
	static public ClearSignResult clearSignMessage(String msg, KeyRingResource keyring, PGPSecretKeyRing signer, char[] passphrase) {
		ClearSignResult res = new ClearSignResult();
		
		// remove trailing whitespace
		BufferedReader b = new BufferedReader(new CharSequenceReader(msg));
		StringBuilder sbmsg = new StringBuilder();
		
		try {
			String readLine = "";
			
			while ((readLine = b.readLine()) != null) {
				sbmsg.append(StringUtil.stripTrailingWhitespace(readLine));
				sbmsg.append('\n');
			}
		}
		catch (IOException x) {
			Logger.error("Unable to read message.");
			return res;
		}
		
		byte[] dataBytes = Utf8Encoder.encode(sbmsg);  // be sure to trim the data - PGP doesn't like tailing whitespace in sig
		
		try {
			PGPSignatureGenerator sGen = new PGPSignatureGenerator(new JcaPGPContentSignerBuilder(signer.getPublicKey().getAlgorithm(), PGPUtil.SHA512).setProvider("BC"));
			PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
			
			sGen.init(PGPSignature.CANONICAL_TEXT_DOCUMENT, keyring.findSecretKey(signer.getPublicKey().getKeyID(), passphrase));
			spGen.setSignerUserID(false,  (String) signer.getPublicKey().getUserIDs().next());
			sGen.setHashedSubpackets(spGen.generate());
			
			Memory mem = new Memory();
			
			try (OutputStream baos = new OutputWrapper(mem)) {
				ArmoredOutputStream aos = new ArmoredOutputStream(baos);
				aos.beginClearText(PGPUtil.SHA512);
				
				sGen.update(dataBytes);
				aos.write(dataBytes);
				aos.write('\n'); // don't include final white space in signature, but must have at least this
				
				aos.endClearText();
				
				BCPGOutputStream bOut = new BCPGOutputStream(aos);
				res.sig = sGen.generate();
				res.sig.encode(bOut);
				
				aos.flush();
				aos.close();
			}
			catch (IOException x) {
				Logger.error("Unable to write sign: " + x);
				return null;
			}
			catch (PGPException x) {
				Logger.error("Unable to sign: " + x);
				return null;
			}
			
			mem.setPosition(0);
	
			res.file = mem.toString();
			return res;
		}
		catch (PGPException x) {
			Logger.error("Unable to prepare sign key: " + x);
			return null;
		}
	}
	
	public static class ClearSignResult {
		public String file = null;
		public PGPSignature sig = null;
	}
	
	private static int readInputLine(ByteArrayOutputStream bOut, InputStream fIn)
			throws IOException
	{
		bOut.reset();
		
		int lookAhead = -1;
		int ch;
		
		while ((ch = fIn.read()) >= 0)
		{
			bOut.write(ch);
			if (ch == '\r' || ch == '\n')
			{
				lookAhead = readPassedEOL(bOut, ch, fIn);
				break;
			}
		}
		
		return lookAhead;
	}
	
	private static int readInputLine(ByteArrayOutputStream bOut, int lookAhead, InputStream fIn)
			throws IOException
	{
		bOut.reset();
		
		int ch = lookAhead;
		
		do
		{
			bOut.write(ch);
			if (ch == '\r' || ch == '\n')
			{
				lookAhead = readPassedEOL(bOut, ch, fIn);
				break;
			}
		}
		while ((ch = fIn.read()) >= 0);
		
		if (ch < 0)
		{
			lookAhead = -1;
		}
		
		return lookAhead;
	}
	
	private static int readPassedEOL(ByteArrayOutputStream bOut, int lastCh, InputStream fIn)
			throws IOException
	{
		int lookAhead = fIn.read();
		
		if (lastCh == '\r' && lookAhead == '\n')
		{
			bOut.write(lookAhead);
			lookAhead = fIn.read();
		}
		
		return lookAhead;
	}
	
	/*
	 * verify a clear text signed file
	 *
	 * meant to be used on smallish file, all in memory
	 *
	 */
	public static boolean verifyFile(InputStream in, KeyRingResource keyIn, StringBuilder content, StringStruct sigvar) {
		try {
			ArmoredInputStream aIn = new ArmoredInputStream(in);
			
			Memory mem = new Memory();
			OutputStream out = new OutputWrapper(mem);
			
			
			//
			// write out signed section using the local line separator.
			// note: trailing white space needs to be removed from the end of
			// each line RFC 4880 Section 7.1
			//
			ByteArrayOutputStream lineOut = new ByteArrayOutputStream();
			int lookAhead = readInputLine(lineOut, aIn);
			byte[] lineSep = getLineSeparator();
			
			if (lookAhead != -1 && aIn.isClearText()) {
				byte[] line = lineOut.toByteArray();
				out.write(line, 0, getLengthWithoutSeparatorOrTrailingWhitespace(line));
				out.write(lineSep);
				
				while (lookAhead != -1 && aIn.isClearText()) {
					lookAhead = readInputLine(lineOut, lookAhead, aIn);
					
					line = lineOut.toByteArray();
					out.write(line, 0, getLengthWithoutSeparatorOrTrailingWhitespace(line));
					out.write(lineSep);
				}
			} else {
				// a single line file
				if (lookAhead != -1) {
					byte[] line = lineOut.toByteArray();
					out.write(line, 0, getLengthWithoutSeparatorOrTrailingWhitespace(line));
					out.write(lineSep);
				}
			}
			
			out.close();
			
			//PGPPublicKeyRingCollection pgpRings = new PGPPublicKeyRingCollection(keyIn, new JcaKeyFingerprintCalculator());
			
			JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(aIn);
			PGPSignatureList p3 = (PGPSignatureList) pgpFact.nextObject();
			PGPSignature sig = p3.get(0);
			
			PGPPublicKey publicKey = keyIn.findPublicKey(sig.getKeyID());
			
			if (publicKey == null) {
				Logger.error("Unable to verify, missing key: " + Long.toHexString(sig.getKeyID()));
				return false;
			}
			
			sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), publicKey);
			
			//
			// read the input, making sure we ignore the last newline.
			//
			
			mem.setPosition(0);
			
			content.append(mem.toString());
			
			mem.setPosition(0);
			
			InputStream sigIn = new BufferedInputStream(new InputWrapper(mem));
			
			lookAhead = readInputLine(lineOut, sigIn);
			
			processLine(sig, lineOut.toByteArray());
			
			if (lookAhead != -1) {
				do {
					lookAhead = readInputLine(lineOut, lookAhead, sigIn);
					
					sig.update((byte) '\r');
					sig.update((byte) '\n');
					
					processLine(sig, lineOut.toByteArray());
				}
				while (lookAhead != -1);
			}
			
			sigIn.close();
			
			if (sig.verify()) {
				Logger.info("signature verified.");
				
				if (sigvar != null)
					sigvar.setValue(HexUtil.bufferToHex(sig.getSignature()));
				
				return true;
			} else {
				Logger.error("signature verification failed.");
				return false;
			}
		}
		catch (IOException x) {
			Logger.error("unable to read sig: " + x);
			Logger.error("signature verification failed.");
			return false;
		}
		catch (PGPException x) {
			Logger.error("unable to verify pgp: " + x);
			Logger.error("signature verification failed.");
			return false;
		}
		catch (SignatureException x) {
			Logger.error("unable to verify sig: " + x);
			Logger.error("signature verification failed.");
			return false;
		}
	}
	
	public static boolean readFile(InputStream in, KeyRingResource keyIn, StringBuilder content) {
		try {
			ArmoredInputStream aIn = new ArmoredInputStream(in);
			
			Memory mem = new Memory();
			OutputStream out = new OutputWrapper(mem);
			
			
			//
			// write out signed section using the local line separator.
			// note: trailing white space needs to be removed from the end of
			// each line RFC 4880 Section 7.1
			//
			ByteArrayOutputStream lineOut = new ByteArrayOutputStream();
			int lookAhead = readInputLine(lineOut, aIn);
			byte[] lineSep = getLineSeparator();
			
			if (lookAhead != -1 && aIn.isClearText()) {
				byte[] line = lineOut.toByteArray();
				out.write(line, 0, getLengthWithoutSeparatorOrTrailingWhitespace(line));
				out.write(lineSep);
				
				while (lookAhead != -1 && aIn.isClearText()) {
					lookAhead = readInputLine(lineOut, lookAhead, aIn);
					
					line = lineOut.toByteArray();
					out.write(line, 0, getLengthWithoutSeparatorOrTrailingWhitespace(line));
					out.write(lineSep);
				}
			} else {
				// a single line file
				if (lookAhead != -1) {
					byte[] line = lineOut.toByteArray();
					out.write(line, 0, getLengthWithoutSeparatorOrTrailingWhitespace(line));
					out.write(lineSep);
				}
			}
			
			out.close();
			
			//PGPPublicKeyRingCollection pgpRings = new PGPPublicKeyRingCollection(keyIn, new JcaKeyFingerprintCalculator());
			
			JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(aIn);
			PGPSignatureList p3 = (PGPSignatureList) pgpFact.nextObject();
			PGPSignature sig = p3.get(0);
			
			PGPPublicKey publicKey = keyIn.findPublicKey(sig.getKeyID());
			
			if (publicKey == null) {
				Logger.error("Unable to verify, missing key: " + Long.toHexString(sig.getKeyID()));
				return false;
			}
			
			sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), publicKey);
			
			//
			// read the input, making sure we ignore the last newline.
			//
			
			mem.setPosition(0);
			
			content.append(mem.toString());
			
			mem.setPosition(0);
			
			InputStream sigIn = new BufferedInputStream(new InputWrapper(mem));
			
			lookAhead = readInputLine(lineOut, sigIn);
			
			processLine(sig, lineOut.toByteArray());
			
			if (lookAhead != -1) {
				do {
					lookAhead = readInputLine(lineOut, lookAhead, sigIn);
					
					sig.update((byte) '\r');
					sig.update((byte) '\n');
					
					processLine(sig, lineOut.toByteArray());
				}
				while (lookAhead != -1);
			}
			
			sigIn.close();
			
			/*
			if (sig.verify()) {
				Logger.info("signature verified.");
				
				if (sigvar != null)
					sigvar.setValue(HexUtil.bufferToHex(sig.getSignature()));
				
				return true;
			} else {
				Logger.error("signature verification failed.");
				return false;
			}
			*/
			
			return true;
		}
		catch (IOException x) {
			Logger.error("unable to read sig: " + x);
			Logger.error("signature verification failed.");
			return false;
		}
		catch (PGPException x) {
			Logger.error("unable to verify pgp: " + x);
			Logger.error("signature verification failed.");
			return false;
		}
		catch (SignatureException x) {
			Logger.error("unable to verify sig: " + x);
			Logger.error("signature verification failed.");
			return false;
		}
	}
	
	private static byte[] getLineSeparator()
	{
		String nl = Strings.lineSeparator();
		byte[] nlBytes = new byte[nl.length()];
		
		for (int i = 0; i != nlBytes.length; i++)
		{
			nlBytes[i] = (byte)nl.charAt(i);
		}
		
		return nlBytes;
	}
	
	/*
	 * create a clear text signed file.
	 */
	/*
	private static void signFile(
			String          fileName,
			InputStream     keyIn,
			OutputStream    out,
			char[]          pass,
			String          digestName)
			throws IOException, NoSuchAlgorithmException, NoSuchProviderException, PGPException, SignatureException
	{
		int digest;
		
		if (digestName.equals("SHA256"))
		{
			digest = PGPUtil.SHA256;
		}
		else if (digestName.equals("SHA384"))
		{
			digest = PGPUtil.SHA384;
		}
		else if (digestName.equals("SHA512"))
		{
			digest = PGPUtil.SHA512;
		}
		else if (digestName.equals("MD5"))
		{
			digest = PGPUtil.MD5;
		}
		else if (digestName.equals("RIPEMD160"))
		{
			digest = PGPUtil.RIPEMD160;
		}
		else
		{
			digest = PGPUtil.SHA1;
		}
		
		PGPSecretKey                    pgpSecKey = PGPExampleUtil.readSecretKey(keyIn);
		PGPPrivateKey                   pgpPrivKey = pgpSecKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(pass));
		PGPSignatureGenerator           sGen = new PGPSignatureGenerator(new JcaPGPContentSignerBuilder(pgpSecKey.getPublicKey().getAlgorithm(), digest).setProvider("BC"));
		PGPSignatureSubpacketGenerator  spGen = new PGPSignatureSubpacketGenerator();
		
		sGen.init(PGPSignature.CANONICAL_TEXT_DOCUMENT, pgpPrivKey);
		
		Iterator    it = pgpSecKey.getPublicKey().getUserIDs();
		if (it.hasNext())
		{
			spGen.setSignerUserID(false, (String)it.next());
			sGen.setHashedSubpackets(spGen.generate());
		}
		
		InputStream fIn = new BufferedInputStream(new FileInputStream(fileName));
		ArmoredOutputStream aOut = new ArmoredOutputStream(out);
		
		aOut.beginClearText(digest);
		
		//
		// note the last \n/\r/\r\n in the file is ignored
		//
		ByteArrayOutputStream lineOut = new ByteArrayOutputStream();
		int lookAhead = readInputLine(lineOut, fIn);
		
		processLine(aOut, sGen, lineOut.toByteArray());
		
		if (lookAhead != -1)
		{
			do
			{
				lookAhead = readInputLine(lineOut, lookAhead, fIn);
				
				sGen.update((byte)'\r');
				sGen.update((byte)'\n');
				
				processLine(aOut, sGen, lineOut.toByteArray());
			}
			while (lookAhead != -1);
		}
		
		fIn.close();
		
		aOut.endClearText();
		
		BCPGOutputStream            bOut = new BCPGOutputStream(aOut);
		
		sGen.generate().encode(bOut);
		
		aOut.close();
	}
	*/
	
	private static void processLine(PGPSignature sig, byte[] line)
			throws SignatureException, IOException
	{
		int length = getLengthWithoutWhiteSpace(line);
		if (length > 0)
		{
			sig.update(line, 0, length);
		}
	}
	
	private static void processLine(OutputStream aOut, PGPSignatureGenerator sGen, byte[] line)
			throws SignatureException, IOException
	{
		// note: trailing white space needs to be removed from the end of
		// each line for signature calculation RFC 4880 Section 7.1
		int length = getLengthWithoutWhiteSpace(line);
		if (length > 0)
		{
			sGen.update(line, 0, length);
		}
		
		aOut.write(line, 0, line.length);
	}
	
	private static int getLengthWithoutSeparatorOrTrailingWhitespace(byte[] line)
	{
		int    end = line.length - 1;
		
		while (end >= 0 && isWhiteSpace(line[end]))
		{
			end--;
		}
		
		return end + 1;
	}
	
	private static boolean isLineEnding(byte b)
	{
		return b == '\r' || b == '\n';
	}
	
	private static int getLengthWithoutWhiteSpace(byte[] line)
	{
		int    end = line.length - 1;
		
		while (end >= 0 && isWhiteSpace(line[end]))
		{
			end--;
		}
		
		return end + 1;
	}
	
	private static boolean isWhiteSpace(byte b)
	{
		return isLineEnding(b) || b == '\t' || b == ' ';
	}
	
	/*
	public static void main(
			String[] args)
			throws Exception
	{
		Security.addProvider(new BouncyCastleProvider());
		
		if (args[0].equals("-s"))
		{
			InputStream        keyIn = PGPUtil.getDecoderStream(new FileInputStream(args[2]));
			FileOutputStream   out = new FileOutputStream(args[1] + ".asc");
			
			if (args.length == 4)
			{
				signFile(args[1], keyIn, out, args[3].toCharArray(), "SHA1");
			}
			else
			{
				signFile(args[1], keyIn, out, args[3].toCharArray(), args[4]);
			}
		}
		else if (args[0].equals("-v"))
		{
			if (args[1].indexOf(".asc") < 0)
			{
				System.err.println("file needs to end in \".asc\"");
				System.exit(1);
			}
			FileInputStream    in = new FileInputStream(args[1]);
			InputStream        keyIn = PGPUtil.getDecoderStream(new FileInputStream(args[2]));
			
			verifyFile(in, keyIn, args[1].substring(0, args[1].length() - 4));
		}
		else
		{
			System.err.println("usage: ClearSignedFileProcessor [-s file keyfile passPhrase]|[-v sigFile keyFile]");
		}
	}
	*/
}
