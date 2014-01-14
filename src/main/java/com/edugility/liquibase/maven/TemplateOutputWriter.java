/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright (c) 2013 Edugility LLC.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT.  IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * The original copy of this license is available at
 * http://www.opensource.org/license/mit-license.html.
 */
package com.edugility.liquibase.maven;

import java.io.IOException;
import java.io.Writer;

import org.mvel2.templates.util.TemplateOutputStream;

public class TemplateOutputWriter implements TemplateOutputStream {

  private final Writer delegate;

  public TemplateOutputWriter(final Writer delegate) {
    super();
    if (delegate == null) {
      throw new IllegalArgumentException("delegate", new NullPointerException("delegate"));
    }
    this.delegate = delegate;
  }

  @Override
  public TemplateOutputStream append(final CharSequence c) {
    try {
      this.delegate.append(c);
    } catch (final IOException e) {
      // This choice of RuntimeException and its message were made to
      // line up with org.mvel2.templates.util.io.StandardOutputStream
      // which handles errors in exactly this manner for better or for
      // worse.
      throw new RuntimeException("failed to write to stream", e);
    }
    return this;
  }

  @Override
  public TemplateOutputStream append(final char[] c) {
    try {
      this.delegate.write(c);
    } catch (final IOException e) {
      // This choice of RuntimeException and its message were made to
      // line up with org.mvel2.templates.util.io.StandardOutputStream
      // which handles errors in exactly this manner for better or for
      // worse.
      throw new RuntimeException("failed to write to stream", e);
    }
    return this;
  }

  @Override
  public String toString() {
    // The purpose of the toString() method of an arbitrary
    // TemplateOutpuStream is not documented anywhere in MVEL so we
    // emulate the behavior of
    // org.mvel2.templates.util.io.StandardOutputStream, which returns
    // null from this method.  The whole thing smells like some MVEL
    // code somewhere might call this method to see if this particular
    // TemplateOutputStream is capable of returning all the stuff that
    // was written to it as a String, which this implementation most
    // certainly is not.
    return null;
  }

}
