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

import org.mvel2.templates.util.io.StandardOutputStream; // for javadoc only

/**
 * A {@link TemplateOutputStream} that delegates to a {@link Writer}.
 *
 * <h3>Design Notes</h3>
 *
 * <p>The <a href="http://mvel.codehaus.org/">MVEL</a> project's
 * {@link TemplateOutputStream} class is really a {@code char}-based
 * construct, and hence has semantics very close to the {@link Writer}
 * class, despite the fact that it sounds by convention like it should
 * work with {@code byte} streams.</p>
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see TemplateOutputStream
 *
 * @see Writer
 */
public class TemplateOutputWriter implements TemplateOutputStream {

  /**
   * The {@link Writer} instance to which all work is delegated.
   *
   * <p>This field is never {@code null}.</p>
   *
   * @see #TemplateOutputWriter(Writer)
   */
  private final Writer delegate;

  /**
   * Creates a new {@link TemplateOutputWriter}.
   *
   * @param delegate the {@link Writer} to which all operations will
   * be delegated; must not be {@code null}.  This {@link Writer} is
   * never {@linkplain Writer#close() closed} by any of this {@link
   * TemplateOutputWriter}'s operations.
   *
   * @exception IllegalArgumentException if {@code delegate} is {@code
   * null}
   */
  public TemplateOutputWriter(final Writer delegate) {
    super();
    if (delegate == null) {
      throw new IllegalArgumentException("delegate", new NullPointerException("delegate"));
    }
    this.delegate = delegate;
  }

  /**
   * {@linkplain Writer#append(CharSequence) Appends} the supplied
   * {@link CharSequence} to the {@link Writer} supplied {@linkplain
   * #TemplateOutputWriter(Writer) at construction time} and returns
   * this {@link TemplateOutputWriter}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param c the {@link CharSequence} in question; may be {@code
   * null} in which case "{@code null}" is appended instead
   *
   * @return this {@link TemplateOutputWriter}; never {@code null}
   *
   * @exception RuntimeException if any underlying {@link IOException}
   * is thrown in keeping with the somewhat questionable design of
   * {@link TemplateOutputStream}; examine its {@linkplain
   * Throwable#getCause() root cause} for the causing {@link
   * IOException}
   *
   * @see Writer#append(CharSequence)
   */
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

  /**
   * {@linkplain Writer#write(char[]) Writes} the supplied {@code
   * char} array to the {@link Writer} supplied {@linkplain
   * #TemplateOutputWriter(Writer) at construction time} and returns
   * this {@link TemplateOutputWriter}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param c the {@code char} array in question; per the
   * documentation for the {@link Writer#write(char[])} method,
   * behavior is unspecified if this parameter is {@code null}
   *
   * @exception RuntimeException if any underlying {@link IOException}
   * is thrown in keeping with the somewhat questionable design of
   * {@link TemplateOutputStream}; examine its {@linkplain
   * Throwable#getCause() root cause} for the causing {@link
   * IOException}
   *
   * @see Writer#append(CharSequence)
   */
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

  /**
   * Returns {@code null} when invoked, following the undocumented
   * practice and convention of the {@link StandardOutputStream}
   * class.
   *
   * @return {@code null} when invoked
   *
   * @see StandardOutputStream#toString()
   */
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
