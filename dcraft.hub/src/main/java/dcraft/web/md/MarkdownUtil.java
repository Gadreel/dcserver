package dcraft.web.md;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.web.md.process.Block;
import dcraft.xml.XElement;

import java.io.Reader;
import java.io.StringReader;

public class MarkdownUtil {
	public static XElement process(String input, boolean safe) throws OperatingContextException {
		return safe ? MarkdownUtil.processSafe(input) : MarkdownUtil.processUnsafe(input);
	}
	
	public static XElement process(Reader reader, boolean safe) throws OperatingContextException {
		return safe ? MarkdownUtil.processSafe(reader) : MarkdownUtil.processUnsafe(reader);
	}
	
	public static XElement processSafe(String input) throws OperatingContextException {
		return MarkdownUtil.process(ProcessContext.of(ResourceHub.getResources().getMarkdown().getSafeConfig()), input);
	}

	public static XElement processSafe(Reader reader) throws OperatingContextException {
		return MarkdownUtil.process(ProcessContext.of(ResourceHub.getResources().getMarkdown().getSafeConfig()), reader);
	}

	public static XElement processUnsafe(String input) throws OperatingContextException {
		return MarkdownUtil.process(ProcessContext.of(ResourceHub.getResources().getMarkdown().getUnsafeConfig()), input);
	}

	public static XElement processUnsafe(Reader reader) throws OperatingContextException {
		return MarkdownUtil.process(ProcessContext.of(ResourceHub.getResources().getMarkdown().getUnsafeConfig()), reader);
	}

	public static XElement process(ProcessContext ctx, String input) throws OperatingContextException {
		try (StringReader in = new StringReader(input)) {
			return MarkdownUtil.process(ctx, in);
		}
	}

	public static XElement process(ProcessContext ctx, Reader reader) throws OperatingContextException {
		Processor p = Processor.of(ctx, reader);

		return (p != null) ? p.toXml() : null;
	}
	
	public static Block parse(String input, boolean safe) throws OperatingContextException {
		return MarkdownUtil.parse(safe
				?  ProcessContext.of(ResourceHub.getResources().getMarkdown().getSafeConfig())
				: ProcessContext.of(ResourceHub.getResources().getMarkdown().getUnsafeConfig()), input);
	}
	
	public static Block parse(Reader reader, boolean safe) throws OperatingContextException {
		return MarkdownUtil.parse(safe
				?  ProcessContext.of(ResourceHub.getResources().getMarkdown().getSafeConfig())
				: ProcessContext.of(ResourceHub.getResources().getMarkdown().getUnsafeConfig()), reader);
	}

	public static Block parse(ProcessContext ctx, String input) throws OperatingContextException {
		try (StringReader in = new StringReader(input)) {
			return MarkdownUtil.parse(ctx, in);
		}
	}

	public static Block parse(ProcessContext ctx, Reader reader) throws OperatingContextException {
		Processor p = Processor.of(ctx, reader);

		return (p != null) ? p.toBlocks() : null;
	}

}
