/**
 * Copyright (c) 2008-2014, http://www.snakeyaml.org
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
package dcraft.util.io;

/**
 version: 1.1 / 2007-01-25
 - changed BOM recognition ordering (longer boms first)

 Original pseudocode   : Thomas Weidenfeller
 Implementation tweaked: Aki Nieminen
 Implementation changed: Andrey Somov 
 * UTF-32 removed because it is not supported by YAML
 * no default encoding

 http://www.unicode.org/unicode/faq/utf_bom.html
 BOMs:
 00 00 FE FF    = UTF-32, big-endian
 FF FE 00 00    = UTF-32, little-endian
 EF BB BF       = UTF-8,
 FE FF          = UTF-16, big-endian
 FF FE          = UTF-16, little-endian

 Win2k Notepad:
 Unicode format = UTF-16LE
 ***/

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Generic unicode textreader, which will use BOM mark to identify the encoding
 * to be used. If BOM is not found then use a given default or system encoding.
 */
public class UnicodeReader extends Reader {
    protected PushbackInputStream internalIn = null;
    protected InputStreamReader internalIn2 = null;

    private static final int BOM_SIZE = 3;  // TODO add support back for utf-32

    /**
     * @param in
     *            InputStream to be read
     */
    public UnicodeReader(InputStream in) {
        this.internalIn = new PushbackInputStream(in, BOM_SIZE);
    }

    /**
     * Get stream encoding or NULL if stream is uninitialized. Call init() or
     * read() method to initialize it.
     * 
     * @return encoding used
     */
    public String getEncoding() {
        return this.internalIn2.getEncoding();
    }

    /**
     * Read-ahead four bytes and check for BOM marks. Extra bytes are unread
     * back to the stream, only BOM bytes are skipped.
     * 
     * @throws IOException unable to read from stream or incomplete stream
     */
    protected void init() throws IOException {
        if (this.internalIn2 != null)
            return;

        Charset encoding;
        byte[] bom = new byte[BOM_SIZE];
        int n, unread;
        n = this.internalIn.read(bom, 0, bom.length);

        if ((bom[0] == (byte) 0xEF) && (bom[1] == (byte) 0xBB) && (bom[2] == (byte) 0xBF)) {
            encoding = StandardCharsets.UTF_8;
            unread = n - 3;
        }
        else if ((bom[0] == (byte) 0xFE) && (bom[1] == (byte) 0xFF)) {
            encoding = StandardCharsets.UTF_16BE;
            unread = n - 2;
        }
        else if ((bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE)) {
            encoding = StandardCharsets.UTF_16LE;
            unread = n - 2;
        }
        else {
            // Unicode BOM mark not found, unread all bytes
            encoding = StandardCharsets.UTF_8;
            unread = n;
        }

        if (unread > 0)
            this.internalIn.unread(bom, (n - unread), unread);

        // Use given encoding
        CharsetDecoder decoder = encoding.newDecoder().onUnmappableCharacter(CodingErrorAction.REPORT);

        this.internalIn2 = new InputStreamReader(this.internalIn, decoder);
    }

    public void close() throws IOException {
        this.init();
        this.internalIn2.close();
    }

    public int read(char[] cbuf, int off, int len) throws IOException {
        this.init();
        return this.internalIn2.read(cbuf, off, len);
    }
}