/*
 * Copyright (C) 2011 René Jeschke <rene_jeschke@yahoo.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dcraft.web.md;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.web.md.process.Block;
import dcraft.web.md.process.BlockType;
import dcraft.web.md.process.Emitter;
import dcraft.web.md.process.Line;
import dcraft.web.md.process.LineType;
import dcraft.web.md.process.LinkRef;
import dcraft.web.md.process.Utils;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

/*
 * this whole thing needs to be rewritten to use one string builder with markers pointing to the blocks, etc
 */
public class Processor {
	static public Processor of(ProcessContext ctx, Reader reader) {
		Processor p = new Processor();
		
		p.ctx = ctx;
		p.reader = reader;
		p.emitter = new Emitter(ctx);
		
		return p;
	}
	
	public static Block parseToBlock(ProcessContext ctx, String input) throws IOException {
		try (StringReader in = new StringReader(input)) {
			return parseToBlock(ctx, in);
		}
	}
	
	public static Block parseToBlock(ProcessContext ctx, Reader reader) throws IOException {
		try {
			Processor p = Processor.of(ctx, reader);
			return p.toBlocks();
		}
		catch (Exception x) {
			Logger.warn("Inline MD problems: " + x);
			throw x;
		}
	}
	
	// instance code
    
    protected Reader reader = null;
    protected Emitter emitter = null;
    protected ProcessContext ctx = null;
    
    protected Processor() {
    }

    public XElement toXml() throws OperatingContextException {
    	XElement root = W3.tag("div");
        
        Block block = this.toBlocks();

        while (block != null) {
            this.emitter.emit(root, block);
            block = block.next;
        }
        
        //System.out.println(" ============================= ");
        //System.out.println(root.toString(true));
        //System.out.println(" ============================= ");
        
        return root;
    }

    public Block toBlocks() {
        Block parent = this.readLines();
        parent.removeSurroundingEmptyLines();

        this.recurse(parent, false);
        
        return parent.blocks;
    }

    /**
     * Reads all lines from our reader.
     * <p>
     * Takes care of markdown link references.
     * </p>
     * 
     * @return A Block containing all lines.
     * @throws IOException
     *             If an IO error occurred.
     */
    protected Block readLines() {
        Block block = new Block();
        StringBuilder sb = new StringBuilder(1000);
		LinkRef lastLinkRef = null;

        try {
			int c = this.reader.read();

			while(c != -1) {
				sb.setLength(0);
				int pos = 0;
				boolean eol = false;
				while (!eol) {
					switch (c) {
						case -1:
							eol = true;
							break;
						case '\n':
							c = this.reader.read();
							if (c == '\r')
								c = this.reader.read();
							eol = true;
							break;
						case '\r':
							c = this.reader.read();
							if (c == '\n')
								c = this.reader.read();
							eol = true;
							break;
						case '\t': {
							int np = pos + (4 - (pos & 3));
							while (pos < np) {
								sb.append(' ');
								pos++;
							}
							c = this.reader.read();
							break;
						}
						default:
							pos++;
							sb.append((char) c);
							c = this.reader.read();
							break;
					}
				}
				
				Line line = new Line();
				line.value = sb.toString();
				line.init();
				
				// Check for link definitions
				boolean isLinkRef = false;
				
				String id = null, link = null, comment = null;
				
				if(!line.isEmpty && line.leading < 4 && line.value.charAt(line.leading) == '[') {
					line.pos = line.leading + 1;
					// Read ID up to ']'
					id = line.readUntil(']');
					// Is ID valid and are there any more characters?
					if(id != null && line.pos + 2 < line.value.length()) {
						// Check for ':' ([...]:...)
						if(line.value.charAt(line.pos + 1) == ':') {
							line.pos += 2;
							line.skipSpaces();
							// Check for link syntax
							if(line.value.charAt(line.pos) == '<') {
								line.pos++;
								link = line.readUntil('>');
								line.pos++;
							}
							else
								link = line.readUntil(' ', '\n');
							
							// Is link valid?
							if(link != null) {
								// Any non-whitespace characters following?
								if(line.skipSpaces()) {
									char ch = line.value.charAt(line.pos);
									// Read comment
									if(ch == '\"' || ch == '\'' || ch == '(') {
										line.pos++;
										comment = line.readUntil(ch == '(' ? ')' : ch);
										// Valid linkRef only if comment is valid
										if(comment != null)
											isLinkRef = true;
									}
								}
								else
									isLinkRef = true;
							}
						}
					}
				}
				
				// To make compiler happy: add != null checks
				if(isLinkRef && id != null && link != null) {
					// Store linkRef and skip line
					LinkRef lr = new LinkRef(link, comment, comment != null
							&& (link.length() == 1 && link.charAt(0) == '*'));
					
					this.emitter.addLinkRef(id, lr);
					
					if(comment == null)
						lastLinkRef = lr;
				}
				else {
					comment = null;
					
					// Check for multi-line linkRef
					if(!line.isEmpty && lastLinkRef != null) {
						line.pos = line.leading;
						char ch = line.value.charAt(line.pos);
						
						if(ch == '\"' || ch == '\'' || ch == '(') {
							line.pos++;
							comment = line.readUntil(ch == '(' ? ')' : ch);
						}
						
						if(comment != null)
							lastLinkRef.title = comment;
						
						lastLinkRef = null;
					}
					
					// No multi-line linkRef, store line
					if(comment == null) {
						line.pos = 0;
						block.appendLine(line);
					}
				}
			}
		}
		catch (IOException x) {
			Logger.error("Problem reading Markdown lines: " + x);
			return null;
		}

        return block;
    }

    /**
     * Initializes a list block by separating it into list item blocks.
     * 
     * @param root
     *            The Block to process.
     */
    protected void initListBlock(Block root) {
        Line line = root.lines;
        line = line.next;
        while(line != null)
        {
            LineType t = line.getLineType(this.ctx);
            if((t == LineType.OLIST || t == LineType.ULIST)
                    || (!line.isEmpty && (line.prevEmpty && line.leading == 0 && !(t == LineType.OLIST || t == LineType.ULIST))))
            {
                root.split(line.previous).type = BlockType.LIST_ITEM;
            }
            line = line.next;
        }
        root.split(root.lineTail).type = BlockType.LIST_ITEM;
    }

    /**
     * Recursively process the given Block.
     * 
     * @param root
     *            The Block to process.
     * @param listMode
     *            Flag indicating that we're in a list item block.
     */
    protected void recurse(Block root, boolean listMode) {
        Block block, list;
        Line line = root.lines;

        if(listMode)
        {
            root.removeListIndent(this.ctx);
            if(root.lines != null && root.lines.getLineType(this.ctx) != LineType.CODE)
            {
                root.id = root.lines.stripID();
            }
        }

        while(line != null && line.isEmpty)
            line = line.next;
        if(line == null)
            return;

        while(line != null) {
            LineType type = line.getLineType(this.ctx);
            switch(type)
            {
            case OTHER:
            {
                boolean wasEmpty = line.prevEmpty;
                while(line != null && !line.isEmpty)
                {
                    LineType t = line.getLineType(this.ctx);
                    
                    // removed  || t == LineType.XML
                    if (t == LineType.OLIST || t == LineType.ULIST || t == LineType.CODE 
                    		|| t == LineType.FENCED_CODE || t == LineType.PLUGIN || t == LineType.HEADLINE 
                    		|| t == LineType.HEADLINE1 || t == LineType.HEADLINE2 || t == LineType.HR
                            || t == LineType.BQUOTE)
                        break;
                    
                    line = line.next;
                }
                BlockType bt;
                if(line != null && !line.isEmpty)
                {
                    bt = (listMode && !wasEmpty) ? BlockType.NONE : BlockType.PARAGRAPH;
                    root.split(line.previous).type = bt;
                    root.removeLeadingEmptyLines();
                }
                else
                {
                    bt = (listMode && (line == null || !line.isEmpty) && !wasEmpty) ? BlockType.NONE
                            : BlockType.PARAGRAPH;
                    root.split(line == null ? root.lineTail : line).type = bt;
                    root.removeLeadingEmptyLines();
                }
                line = root.lines;
                break;
            }
            case CODE:
                while(line != null && (line.isEmpty || line.leading > 3))
                {
                    line = line.next;
                }
                block = root.split(line != null ? line.previous : root.lineTail);
                block.type = BlockType.CODE;
                block.removeSurroundingEmptyLines();
                break;
            case XML:
                if(line.previous != null)
                {
                    // FIXME ... this looks wrong
                    root.split(line.previous);
                }
                root.split(line.xmlEndLine).type = BlockType.XML;
                root.removeLeadingEmptyLines();
                line = root.lines;
                break;
            case BQUOTE:
                while(line != null) {
                    if(!line.isEmpty && (line.prevEmpty && line.leading == 0 && line.getLineType(this.ctx) != LineType.BQUOTE))
                        break;
                    
                    line = line.next;
                }
                
                block = root.split(line != null ? line.previous : root.lineTail);
                block.type = BlockType.BLOCKQUOTE;
                block.removeSurroundingEmptyLines();
                block.removeBlockQuotePrefix();
                this.recurse(block, false);
                line = root.lines;
                break;
            case HR:
                if(line.previous != null)
                {
                    // FIXME ... this looks wrong
                    root.split(line.previous);
                }
                root.split(line).type = BlockType.RULER;
                root.removeLeadingEmptyLines();
                line = root.lines;
                break;
            case IMAGE_BLOCK:
                if(line.previous != null)
                {
                    // FIXME ... this looks wrong
                    root.split(line.previous);
                }
                root.split(line).type = BlockType.IMAGE_BLOCK;
                root.removeLeadingEmptyLines();
                line = root.lines;
                break;
            case FENCED_CODE:
                line = line.next;
                
                while(line != null) {
                    if(line.getLineType(this.ctx) == LineType.FENCED_CODE)
                        break;
                    // TODO ... is this really necessary? Maybe add a special
                    // flag?
                    line = line.next;
                }
                
                if(line != null)
                    line = line.next;
                
                block = root.split(line != null ? line.previous : root.lineTail);
                block.type = BlockType.FENCED_CODE;
                block.meta = Utils.getMetaFromFence(block.lines.value);
                block.lines.setEmpty();
                if(block.lineTail.getLineType(this.ctx) == LineType.FENCED_CODE)
                    block.lineTail.setEmpty();
                block.removeSurroundingEmptyLines();
                break;
            case PLUGIN:
            	// plugins may end on same line
            	if ((line.value.length() > 3) && !line.value.endsWith("%%%")) {
	                line = line.next;
	                
	                while(line != null) {
	                    if(line.getLineType(this.ctx) == LineType.PLUGIN)
	                        break;
	                    // TODO ... is this really necessary? Maybe add a special
	                    // flag?
	                    line = line.next;
	                }
            	}
            
                if(line != null)
                    line = line.next;
            	
                block = root.split(line != null ? line.previous : root.lineTail);
                block.type = BlockType.PLUGIN;
                
                block.removeSurroundingEmptyLines();
                
                block.meta = Utils.getMetaFromFence(block.lines.value);  // TODO handle if the %%% is at the end
                
                block.lines.setEmpty();
                
                if (block.lineTail.getLineType(this.ctx) == LineType.PLUGIN)
                    block.lineTail.setEmpty();
                
                block.removeSurroundingEmptyLines();
                break;
            case HEADLINE:
            case HEADLINE1:
            case HEADLINE2:
                if(line.previous != null)
                    root.split(line.previous);
                
                if(type != LineType.HEADLINE)
                    line.next.setEmpty();
                
                block = root.split(line);
                block.type = BlockType.HEADLINE;
                
                if(type != LineType.HEADLINE)
                    block.hlDepth = type == LineType.HEADLINE1 ? 1 : 2;

                block.id = block.lines.stripID();
                block.transfromHeadline();
                root.removeLeadingEmptyLines();
                line = root.lines;
                break;
            case OLIST:
            case ULIST:
                while(line != null) {
                    LineType t = line.getLineType(this.ctx);
                    if(!line.isEmpty
                            && (line.prevEmpty && line.leading == 0 && !(t == LineType.OLIST || t == LineType.ULIST)))
                        break;
                    line = line.next;
                }
                
                list = root.split(line != null ? line.previous : root.lineTail);
                list.type = type == LineType.OLIST ? BlockType.ORDERED_LIST : BlockType.UNORDERED_LIST;
                list.lines.prevEmpty = false;
                list.lineTail.nextEmpty = false;
                list.removeSurroundingEmptyLines();
                list.lines.prevEmpty = list.lineTail.nextEmpty = false;
                initListBlock(list);
                block = list.blocks;
                
                while(block != null) {
                    this.recurse(block, true);
                    block = block.next;
                }
                
                list.expandListParagraphs();
                break;
            default:
                line = line.next;
                break;
            }
        }
    }
}
