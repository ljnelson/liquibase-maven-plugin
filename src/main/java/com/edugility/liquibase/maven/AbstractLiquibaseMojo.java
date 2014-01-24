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

import org.apache.maven.plugin.AbstractMojo;

import org.apache.maven.plugins.annotations.Component;

import org.apache.maven.project.MavenProject;

/**
 * An {@link AbstractMojo} housing <a
 * href="http://www.liquibase.org/">Liquibase</a> functionality.
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see AbstractMojo
 *
 * @see <a href="http://www.liquibase.org/">Liquibase</a>
 */
public abstract class AbstractLiquibaseMojo extends AbstractMojo {

  /**
   * The {@link MavenProject} currently in effect.
   *
   * <p>This field may be {@code null}.</p>
   *
   * @see #getProject()
   *
   * @see #setProject(MavenProject)
   */
  @Component
  private MavenProject project;

  /**
   * Creates a new {@link AbstractLiquibaseMojo}.
   */
  protected AbstractLiquibaseMojo() {
    super();
  }

  /**
   * Returns the {@link MavenProject} affiliated with this {@link
   * AbstractLiquibaseMojo}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return a {@link MavenProject}, or {@code null}
   *
   * @see #setProject(MavenProject)
   */
  public MavenProject getProject() {
    return this.project;
  }

  /**
   * Affiliates the supplied {@link MavenProject} with this {@link
   * AbstractLiquibaseMojo}.
   *
   * @param project the {@link MavenProject} to affiliate; may be
   * {@code null}
   *
   * @see #getProject()
   */
  public void setProject(final MavenProject project) {
    this.project = project;
  }

}
