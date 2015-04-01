/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright (c) 2013-2014 Edugility LLC.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.edugility.maven.Artifacts;

import org.apache.maven.artifact.Artifact;

import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest; // for javadoc only

import org.apache.maven.artifact.resolver.filter.ArtifactFilter;

import org.apache.maven.artifact.repository.ArtifactRepository;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.apache.maven.plugin.logging.Log;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.apache.maven.model.Build;

import org.apache.maven.project.MavenProject;

import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;

import org.mvel2.integration.impl.MapVariableResolverFactory;

import org.mvel2.templates.CompiledTemplate;
import org.mvel2.templates.TemplateCompiler;
import org.mvel2.templates.TemplateRuntime;

/**
 * Scans the test classpath in dependency order for <a
 * href="http://www.liquibase.org/">Liquibase</a> <a
 * href="http://www.liquibase.org/documentation/databasechangelog.html">changelog</a>
 * fragments and assembles a master changelog that <a
 * href="http://www.liquibase.org/documentation/include.html">includes</a>
 * them all in dependency order.
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see AbstractLiquibaseMojo
 */
@Mojo(name = "assembleChangeLog", requiresDependencyResolution = ResolutionScope.TEST)
public class AssembleChangeLogMojo extends AbstractLiquibaseMojo {


  /*
   * Static fields.
   */


  /**
   * The platform's line separator; "{@code \\n}" by default.  This
   * field is never {@code null}.
   */
  private static final String LS = System.getProperty("line.separator", "\n");


  /*
   * Instance fields and plugin parameters.
   */


  /**
   * The {@link DependencyGraphBuilder} injected by Maven.  This field
   * is never {@code null} during normal plugin execution.
   *
   * @see #getDependencyGraphBuilder()
   *
   * @see #setDependencyGraphBuilder(DependencyGraphBuilder)
   */
  @Component
  private DependencyGraphBuilder dependencyGraphBuilder;

  /**
   * The {@link ArtifactResolver} injected by Maven.  This field is
   * never {@code null} during normal plugin execution.
   *
   * @see #getArtifactResolver()
   *
   * @see #setArtifactResolver(ArtifactResolver)
   */
  @Component
  private ArtifactResolver artifactResolver;

  /**
   * Whether or not this plugin execution should be skipped; {@code
   * false} by default.
   *
   * @see #getSkip()
   *
   * @see #setSkip(boolean)
   */
  @Parameter(defaultValue = "false")
  private boolean skip;

  /**
   * The local {@link ArtifactRepository} injected by Maven.  This
   * field is never {@code null} during normal plugin execution.
   *
   * @see #getLocalRepository()
   *
   * @see #setLocalRepository(ArtifactRepository)
   */
  @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
  private ArtifactRepository localRepository;

  /**
   * An {@link ArtifactFilter} to use to limit what dependencies are
   * scanned for changelog fragments; {@code null} by default.
   *
   * @see #getArtifactFilter()
   *
   * @see #setArtifactFilter(ArtifactFilter)
   *
   * @see <a
   * href="http://maven.apache.org/guides/mini/guide-configuring-plugins.html#Mapping_Complex_Objects">Guide
   * to Configuring Plug-Ins</a>
   */
  @Parameter
  private ArtifactFilter artifactFilter;

  /**
   * A list of classpath resource names that identity <a
   * href="http://liquibase.org/">Liquibase</a> changelogs; {@code
   * META-INF/liquibase/changelog.xml} by default.
   *
   * @see #getChangeLogResourceNames()
   *
   * @see #setChangeLogResourceNames(List)
   */
  @Parameter(defaultValue = "META-INF/liquibase/changelog.xml", required = true)
  private List<String> changeLogResourceNames;

  /**
   * The classpath resource name of the <a
   * href="http://mvel.codehaus.org/">MVEL</a> template that will be
   * used to aggregate all the changelog fragments together; {@code
   * changelog-template.mvl} by default; typically found within this
   * plugin's own {@code .jar} file, but users may wish to supply an
   * alternate template.
   *
   * @see #getChangeLogTemplateResourceName()
   *
   * @see #setChangeLogTemplateResourceName(String)
   */
  @Parameter
  private String changeLogTemplateResourceName;

  /**
   * The full path to the changelog that will be generated;
   * <code>${project.build.directory}/generated-sources/liquibase/changelog.xml</code>
   * by default.
   *
   * @see #getOutputFile()
   *
   * @see #setOutputFile(File)
   *
   * @see <a
   * href="http://maven.apache.org/guides/mini/guide-configuring-plugins.html#Configuring_Parameters">Guide
   * to Configuring Plug-Ins</a>
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-sources/liquibase/changelog.xml", required = true)
  private File outputFile;

  /**
   * The version of the proper XSD file to use that defines the
   * contents of a <a href="http://liquibase.org/">Liquibase</a>
   * changelog file; {@code 3.0} by default.
   *
   * @see #getDatabaseChangeLogXsdVersion()
   *
   * @see #setDatabaseChangeLogXsdVersion(String)
   */
  @Parameter(defaultValue = "3.0", required = true)
  private String databaseChangeLogXsdVersion;

  /**
   * A set of {@link Properties} defining <a
   * href="http://www.liquibase.org/documentation/changelog_parameters.html">changelog
   * parameters</a>; {@code null} by default.
   *
   * @see #getChangeLogParameters()
   *
   * @see #setChangeLogParameters(Properties)
   *
   * @see <a
   * href="http://maven.apache.org/guides/mini/guide-configuring-plugins.html#Mapping_Properties">Guide
   * to Configuring Plug-Ins</a>
   */
  @Parameter
  private Properties changeLogParameters;

  /**
   * The character encoding to use while reading in the changelog <a
   * href="http://mvel.codehaus.org/">MVEL</a> template;
   * <code>${project.build.sourceEncoding}</code> by default.
   *
   * @see #getTemplateCharacterEncoding()
   *
   * @see #setTemplateCharacterEncoding(String)
   *
   * @see <a
   * href="http://docs.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html#iana">Standard
   * character encoding names provided by Java SE</a>
   */
  @Parameter(required = true, defaultValue = "${project.build.sourceEncoding}")
  private String templateCharacterEncoding;

  /**
   * The character encoding to use while writing the generated
   * changelog; <code>${project.build.sourceEncoding}</code> by
   * default.
   *
   * @see #getChangeLogCharacterEncoding()
   *
   * @see #setChangeLogCharacterEncoding(String)
   *
   * @see <a
   * href="http://docs.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html#iana">Standard
   * character encoding names provided by Java SE</a>
   */
  @Parameter(required = true, defaultValue = "${project.build.sourceEncoding}")
  private String changeLogCharacterEncoding;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link AssembleChangeLogMojo}.
   */
  public AssembleChangeLogMojo() {
    super();
  }


  /*
   * Instance methods.
   */


  /*
   * Simple properties.
   */


  /**
   * Returns {@code true} if the {@link #execute()} method should take
   * no action.
   *
   * @return {@code true} if the {@link #execute()} method should take
   * no action; {@code false} otherwise
   *
   * @see #setSkip(boolean)
   */
  public boolean getSkip() {
    return this.skip;
  }

  /**
   * Sets whether the {@link #execute()} method will take any action.
   *
   * @param skip if {@code true}, then the {@link #execute()} method
   * will take no action
   *
   * @see #getSkip()
   */
  public void setSkip(final boolean skip) {
    this.skip = skip;
  }


  /**
   * Returns the {@link ArtifactRepository} that represents the
   * current local Maven repository.  This method may return {@code
   * null}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return an {@link ArtifactRepository}, or {@code null}
   *
   * @see #setLocalRepository(ArtifactRepository)
   */
  public ArtifactRepository getLocalRepository() {
    return this.localRepository;
  }

  /**
   * Sets the {@link ArtifactRepository} to be used as the current
   * local Maven repository.
   *
   * <p>This method is normally used for testing and mocking purposes
   * only.</p>
   *
   * @param localRepository the {@link ArtifactRepository}
   * representing the current local Maven repository; must not be
   * {@code null}
   *
   * @exception IllegalArgumentException if {@code localRepository} is
   * {@code null}
   *
   * @see #getLocalRepository()
   */
  public void setLocalRepository(final ArtifactRepository localRepository) {
    if (localRepository == null) {
      throw new IllegalArgumentException("localRepository", new NullPointerException("localRepository"));
    }
    this.localRepository = localRepository;
  }


  /**
   * Returns an {@link ArtifactFilter} that may be used to filter the
   * {@link Artifact}s whose {@linkplain Artifact#getFile() associated
   * <code>File</code>s} are inspected for changelog fragments.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return an {@link ArtifactFilter}, or {@code null}
   *
   * @see #setArtifactFilter(ArtifactFilter)
   *
   * @see <a
   * href="http://maven.apache.org/guides/mini/guide-configuring-plugins.html#Mapping_Complex_Objects">Guide
   * to Configuring Plug-Ins</a>
   */
  public ArtifactFilter getArtifactFilter() {
    return this.artifactFilter;
  }

  /**
   * Installs an {@link ArtifactFilter} that will be used to filter
   * the {@link Artifact}s whose {@linkplain Artifact#getFile()
   * associated <code>File</code>s} are inspected for changelog
   * fragments.
   *
   * @param filter the new {@link ArtifactFilter}; may be {@code null}
   *
   * @see #getArtifactFilter()
   *
   * @see <a
   * href="http://maven.apache.org/guides/mini/guide-configuring-plugins.html#Mapping_Complex_Objects">Guide
   * to Configuring Plug-Ins</a>
   */
  public void setArtifactFilter(final ArtifactFilter filter) {
    this.artifactFilter = filter;
  }


  /**
   * Returns the {@link ArtifactResolver} that will be used internally
   * to {@linkplain
   * ArtifactResolver#resolve(ArtifactResolutionRequest) resolve}
   * {@link Artifact}s representing the {@linkplain #getProject()
   * current project}'s dependencies.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return an {@link ArtifactResolver}, or {@code null}
   *
   * @see #setArtifactResolver(ArtifactResolver)
   *
   * @see ArtifactResolver#resolve(ArtifactResolutionRequest)
   */
  public ArtifactResolver getArtifactResolver() {
    return this.artifactResolver;
  }

  /**
   * Sets the {@link ArtifactResolver} that will be used internally
   * to {@linkplain
   * ArtifactResolver#resolve(ArtifactResolutionRequest) resolve}
   * {@link Artifact}s representing the {@linkplain #getProject()
   * current project}'s dependencies.
   * 
   * @param resolver the new {@link ArtifactResolver}; may be {@code
   * null}
   *
   * @see #getArtifactResolver()
   *
   * @see ArtifactResolver#resolve(ArtifactResolutionRequest)
   */
  public void setArtifactResolver(final ArtifactResolver resolver) {
    this.artifactResolver = resolver;
  }


  /**
   * Returns a {@link List} of {@link String}s, each element of which
   * is a name of a {@linkplain ClassLoader#getResource(String)
   * classpath resource} that might identify an actual changelog
   * fragment.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return a {@link List} of {@link String}s, or {@code null}
   *
   * @see #setChangeLogResourceNames(List)
   *
   * @see ClassLoader#getResource(String)
   */
  public List<String> getChangeLogResourceNames() {
    return this.changeLogResourceNames;
  }

  /**
   * Sets the {@link List} of {@link String}s identifying {@linkplain
   * ClassLoader#getResource(String) classpath resources} that might
   * identify actual changelog fragments.
   *
   * @param changeLogResourceNames a {@link List} of {@link String}s,
   * each element of which is a name of a {@linkplain
   * ClassLoader#getResource(String) classpath resource} that might
   * identify an actual changelog fragment; may be {@code null}
   *
   * @see #getChangeLogResourceNames()
   */
  public void setChangeLogResourceNames(final List<String> changeLogResourceNames) {
    this.changeLogResourceNames = changeLogResourceNames;
  }


  /**
   * Returns the {@linkplain ClassLoader#getResource(String) classpath
   * resource} name of an <a href="http://mvel.codehaus.org/">MVEL</a>
   * template that will aggregate changelog fragments together.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return the {@linkplain ClassLoader#getResource(String) classpath
   * resource} name of an <a href="http://mvel.codehaus.org/">MVEL</a>
   * template that will aggregate changelog fragments together, or
   * {@code null}
   *
   * @see #setChangeLogTemplateResourceName(String)
   *
   * @see TemplateCompiler#compileTemplate(String)
   *
   * @see ClassLoader#getResource(String)
   */
  public String getChangeLogTemplateResourceName() {
    return this.changeLogTemplateResourceName;
  }

  /**
   * Sets the {@linkplain ClassLoader#getResource(String) classpath
   * resource} name of an <a href="http://mvel.codehaus.org/">MVEL</a>
   * template that will aggregate changelog fragments together.
   *
   * @param name the {@linkplain ClassLoader#getResource(String)
   * classpath resource} name of an <a
   * href="http://mvel.codehaus.org/">MVEL</a> template that will
   * aggregate changelog fragments together; may be {@code null}
   *
   * @see #getChangeLogTemplateResourceName()
   *
   * @see ClassLoader#getResource(String)
   */
  public void setChangeLogTemplateResourceName(final String name) {
    this.changeLogTemplateResourceName = name;
  }


  /**
   * Returns a {@link String} representing the version of the <a
   * href="http://www.liquibase.org/">Liquibase</a> {@code
   * dbchangelog} schema to use.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return a {@link String} representing the version of the <a
   * href="http://www.liquibase.org/">Liquibase</a> {@code
   * dbchangelog} schema to use, or {@code null}
   *
   * @see #setDatabaseChangeLogXsdVersion(String)
   */
  public String getDatabaseChangeLogXsdVersion() {
    return this.databaseChangeLogXsdVersion;
  }

  /**
   * Sets the {@link String} representing the version of the <a
   * href="http://www.liquibase.org/">Liquibase</a> {@code
   * dbchangelog} schema to use.
   *
   * @param databaseChangeLogXsdVersion the new version formatted as a
   * decimal number, hopefully identifying a valid version of the <a
   * href="http://www.liquibase.org/">Liquibase</a> {@code
   * dbchangelog} schema; may be {@code null} in which case {@code
   * 3.0} will be used instead
   *
   * @see #getDatabaseChangeLogXsdVersion()
   */
  public void setDatabaseChangeLogXsdVersion(final String databaseChangeLogXsdVersion) {
    if (databaseChangeLogXsdVersion == null) {
      this.databaseChangeLogXsdVersion = "3.0";
    } else {
      this.databaseChangeLogXsdVersion = databaseChangeLogXsdVersion;
    }
  }


  /**
   * Returns a {@link Properties} object containing <a
   * href="http://www.liquibase.org/documentation/changelog_parameters.html">changelog
   * parameters</a>.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return a {@link Properties} object containing <a
   * href="http://www.liquibase.org/documentation/changelog_parameters.html">changelog
   * parameters</a>, or {@code null}
   *
   * @see #setChangeLogParameters(Properties)
   */
  public Properties getChangeLogParameters() {
    return this.changeLogParameters;
  }

  /**
   * Installs a {@link Properties} object containing <a
   * href="http://www.liquibase.org/documentation/changelog_parameters.html">changelog
   * parameters</a>.
   *
   * @param parameters the changelog parameters to use; may be {@code
   * null}
   *
   * @see #getChangeLogParameters()
   */
  public void setChangeLogParameters(final Properties parameters) {
    this.changeLogParameters = parameters;
  }


  /**
   * Returns the character encoding used to {@linkplain #write(String,
   * Collection, File) write the assembled changelog}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return a {@link String} representing a character encoding, or
   * {@code null}
   *
   * @see #setChangeLogCharacterEncoding(String)
   *
   * @see <a
   * href="http://docs.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html#iana">Standard
   * character encoding names provided by Java SE</a>
   */
  public String getChangeLogCharacterEncoding() {
    return this.changeLogCharacterEncoding;
  }

  /**
   * Sets the character encoding used to {@linkplain #write(String,
   * Collection, File) write the assembled changelog}.
   *
   * @param encoding a {@link String} representing a character
   * encoding; may be {@code null} in which case "{@code UTF-8}" will
   * be used instead
   *
   * @see #getChangeLogCharacterEncoding()
   *
   * @see <a
   * href="http://docs.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html#iana">Standard
   * character encoding names provided by Java SE</a>
   */
  public void setChangeLogCharacterEncoding(final String encoding) {
    if (encoding == null) {
      this.changeLogCharacterEncoding = "UTF-8";
    } else {
      this.changeLogCharacterEncoding = encoding;
    }
  }


  /**
   * Returns the character encoding used to read the <a
   * href="http://mvel.codehaus.org/">MVEL</a> template used to
   * aggregate changelog fragments together.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return a {@link String} representing a character encoding, or
   * {@code null}
   *
   * @see #setTemplateCharacterEncoding(String)
   *
   * @see <a
   * href="http://docs.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html#iana">Standard
   * character encoding names provided by Java SE</a>
   */
  public String getTemplateCharacterEncoding() {
    return this.templateCharacterEncoding;
  }

  /**
   * Sets the character encoding used to read the <a
   * href="http://mvel.codehaus.org/">MVEL</a> template used to
   * aggregate changelog fragments together.
   *
   * @param encoding a {@link String} representing a character
   * encoding; may be {@code null} in which case "{@code UTF-8}" will
   * be used instead
   *
   * @see #getTemplateCharacterEncoding()
   *
   * @see <a
   * href="http://docs.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html#iana">Standard
   * character encoding names provided by Java SE</a>
   */
  public void setTemplateCharacterEncoding(final String encoding) {
    if (encoding == null) {
      this.templateCharacterEncoding = "UTF-8";
    } else {
      this.templateCharacterEncoding = encoding;
    }
  }


  /**
   * Returns the {@link DependencyGraphBuilder} used by this {@link
   * AssembleChangeLogMojo} to perform dependency resolution.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return a {@link DependencyGraphBuilder}, or {@code null}
   *
   * @see DependencyGraphBuilder
   *
   * @see #setDependencyGraphBuilder(DependencyGraphBuilder)
   */
  public DependencyGraphBuilder getDependencyGraphBuilder() {
    return this.dependencyGraphBuilder;
  }

  /**
   * Sets the {@link DependencyGraphBuilder} used by this {@link
   * AssembleChangeLogMojo} to perform dependency resolution.
   *
   * @param dependencyGraphBuilder the {@link DependencyGraphBuilder}
   * to use; must not be {@code null}
   *
   * @exception IllegalArgumentException if {@code
   * dependencyGraphBuilder} is {@code null}
   *
   * @see DependencyGraphBuilder
   *
   * @see #getDependencyGraphBuilder()
   */
  public void setDependencyGraphBuilder(final DependencyGraphBuilder dependencyGraphBuilder) {
    if (dependencyGraphBuilder == null) {
      throw new IllegalArgumentException("dependencyGraphBuilder", new NullPointerException("dependencyGraphBuilder"));
    }
    this.dependencyGraphBuilder = dependencyGraphBuilder;
  }


  /*
   * Non-trivial accessors and mutators.
   */


  /**
   * Examines the {@linkplain #getProject() current
   * <code>MavenProject</code>}'s dependencies, {@linkplain
   * #getArtifactResolver() resolving} them if necessary, and
   * assembles and returns a {@link Collection} whose elements are
   * {@link URL}s representing reachable changelog fragments that are
   * classpath resources.
   *
   * <p>This method may return {@code null}.</p>
   *
   * <p>This method calls the {@link #getChangeLogResources(Iterable)}
   * method.</p>
   *
   * <p>This method invokes the {@link
   * Artifacts#getArtifactsInTopologicalOrder(MavenProject,
   * DependencyGraphBuilder, ArtifactFilter, ArtifactResolver,
   * ArtifactRepository)} method.</p>
   *
   * @return a {@link Collection} of {@link URL}s to classpath
   * resources that are changelog fragments, or {@code null}
   *
   * @exception IllegalStateException if the return value of {@link
   * #getProject()}, {@link #getDependencyGraphBuilder()} or {@link
   * #getArtifactResolver()} is {@code null}
   * 
   * @exception ArtifactResolutionException if there was a problem
   * {@linkplain ArtifactResolver#resolve(ArtifactResolutionRequest)
   * resolving} a given {@link Artifact} representing a dependency
   *
   * @exception DependencyGraphBuilderException if there was a problem
   * with dependency resolution
   *
   * @exception IOException if there was a problem with input or
   * output
   *
   * @see #getChangeLogResources(Iterable)
   *
   * @see Artifacts#getArtifactsInTopologicalOrder(MavenProject,
   * DependencyGraphBuilder, ArtifactFilter, ArtifactResolver,
   * ArtifactRepository)
   */
  public final Collection<? extends URL> getChangeLogResources() throws ArtifactResolutionException, DependencyGraphBuilderException, IOException {
    final MavenProject project = this.getProject();
    if (project == null) {
      throw new IllegalStateException("this.getProject()", new NullPointerException("this.getProject()"));
    }
    final DependencyGraphBuilder dependencyGraphBuilder = this.getDependencyGraphBuilder();
    if (dependencyGraphBuilder == null) {
      throw new IllegalStateException("this.getDependencyGraphBuilder()", new NullPointerException("this.getDependencyGraphBuilder()"));
    }
    final ArtifactResolver resolver = this.getArtifactResolver();
    if (resolver == null) {
      throw new IllegalStateException("this.getArtifactResolver()", new NullPointerException("this.getArtifactResolver()"));
    }
    final Collection<? extends Artifact> artifacts = new Artifacts().getArtifactsInTopologicalOrder(project,
                                                                                                    dependencyGraphBuilder,
                                                                                                    this.getArtifactFilter(),
                                                                                                    resolver,
                                                                                                    this.getLocalRepository());
    Collection<? extends URL> urls = null;
    if (artifacts != null && !artifacts.isEmpty()) {
      urls = getChangeLogResources(artifacts);
    }
    if (urls == null) {
      urls = Collections.emptySet();
    }
    return urls;
  }

  /**
   * Given an {@link Iterable} of {@link Artifact}s, and given a
   * non-{@code null}, non-empty return value from the {@link
   * #getChangeLogResourceNames()} method, this method returns a
   * {@link Collection} of {@link URL}s representing changelog
   * {@linkplain ClassLoader#getResources(String) resources found}
   * among the supplied {@link Artifact}s.
   *
   * @param artifacts an {@link Iterable} of {@link Artifact}s; may be
   * {@code null}
   *
   * @return a {@link Collection} of {@link URL}s representing
   * changelog resources found among the supplied {@link Artifact}s
   *
   * @exception IOException if an input/output error occurs such as
   * the kind that might be thrown by the {@link
   * ClassLoader#getResources(String)} method
   *
   * @see #getChangeLogResourceNames()
   *
   * @see ClassLoader#getResources(String)
   */
  public Collection<? extends URL> getChangeLogResources(final Iterable<? extends Artifact> artifacts) throws IOException {
    final Log log = this.getLog();
    Collection<URL> returnValue = null;
    final ClassLoader loader = this.toClassLoader(artifacts);
    if (loader != null) {
      final Iterable<String> changeLogResourceNames = this.getChangeLogResourceNames();
      if (log != null && log.isDebugEnabled()) {
        log.debug(String.format("Change log resource names: %s", changeLogResourceNames));
      }
      if (changeLogResourceNames == null) {
        throw new IllegalStateException("this.getChangeLogResourceNames()", new NullPointerException("this.getChangeLogResourceNames()"));
      }
      returnValue = new ArrayList<URL>();
      for (final String name : changeLogResourceNames) {
        if (name != null) {
          final Enumeration<URL> urls = loader.getResources(name);
          if (urls != null) {
            returnValue.addAll(Collections.list(urls));
          }
        }
      }
    }
    if (returnValue == null) {
      returnValue = Collections.emptySet();
    }
    return returnValue;
  }


  /**
   * Using an appropriate {@link ClassLoader}, returns a {@link URL}
   * that may be used to {@linkplain URL#openStream() get} the
   * changelog template {@linkplain
   * #getChangeLogTemplateResourceName() associated with} this {@link
   * AssembleChangeLogMojo}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @return a {@link URL} to an <a
   * href="http://mvel.codehaus.org/">MVEL</a> template, or {@code
   * null}
   *
   * @see #getChangeLogTemplateResourceName()
   */
  public URL getChangeLogTemplateResource() {
    final String changeLogTemplateResourceName = this.getChangeLogTemplateResourceName();
    final String resourceName;
    final ClassLoader loader;
    if (changeLogTemplateResourceName == null) {
      resourceName = "changelog-template.mvl";
      loader = this.getClass().getClassLoader();
    } else {
      resourceName = changeLogTemplateResourceName;
      final ClassLoader candidate = Thread.currentThread().getContextClassLoader();
      if (candidate != null) {
        loader = candidate;
      } else {
        loader = this.getClass().getClassLoader();
      }
    }
    assert loader != null;
    final URL resource = loader.getResource(resourceName);
    return resource;
  }


  /**
   * Validates the current {@link File} that represents the full path
   * to the file that will result after the {@link #execute()} method
   * runs successfully and returns it.
   *
   * <p>This method may invoke {@link File#mkdirs()} and {@link
   * File#createNewFile()} as part of its operation.</p>
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @return the {@link File} that represents the full path to the
   * file that will result after the {@link #execute()} method runs
   * successfully; never {@code null}
   *
   * @exception IllegalStateException if somehow the current {@link
   * File} {@linkplain File#isDirectory() is a directory} or if it is
   * somehow {@code null}
   *
   * @exception IOException if {@linkplain File#mkdirs() directory
   * creation} fails or if a new file whose pathname is described by
   * the current output file could not be {@linkplain
   * File#createNewFile() created} or if a prior version of the file
   * existed but could not be {@linkplain File#delete() deleted}
   */
  public File getOutputFile() throws IOException {
    if (this.outputFile == null) {
      throw new IllegalStateException("this.outputFile", new NullPointerException("this.outputFile"));
    } else if (this.outputFile.isDirectory()) {
      throw new IllegalStateException("this.outputFile.isDirectory()");
    }
    final File parent = this.outputFile.getParentFile();
    if (parent != null) {
      if (!parent.exists() && !parent.mkdirs()) {
        throw new IOException("Could not create parent directory chain for " + this.outputFile);
      }
    }
    if (this.outputFile.exists()) {
      if (!this.outputFile.delete()) {
        throw new IOException("Could not delete extant " + this.outputFile);
      }
      if (!this.outputFile.createNewFile()) {
        throw new IOException("Could not create a new file corresponding to the path named " + this.outputFile);
      }
    }
    assert this.outputFile != null;
    assert this.outputFile.exists();
    assert !this.outputFile.isDirectory();
    return this.outputFile;
  }

  /**
   * Sets the {@link File} that represents the full path to the file
   * that will result after the {@link #execute()} method runs
   * successfully.
   *
   * @param file the file; if non-{@code null}, then must not be
   * {@linkplain File#isDirectory() a directory}
   *
   * @see #getOutputFile()
   */
  public void setOutputFile(final File file) {
    if (file != null && file.isDirectory()) {
      throw new IllegalArgumentException("file", new IOException("file.isDirectory()"));
    }
    this.outputFile = file;
  }


  /*
   * Operations.
   */


  /**
   * Executes this {@link AssembleChangeLogMojo} by calling the {@link
   * #assembleChangeLog()} method.
   *
   * @exception MojoFailureException if an error occurs; under normal
   * circumstances this goal will not fail so this method does not
   * throw {@link MojoExecutionException}
   *
   * @exception TemplateSyntaxError if the supplied {@code template}
   * contained syntax errors
   *
   * @exception TemplateRuntimeError if there was a problem merging
   * the supplied {@link Collection} of {@link URL}s with the compiled
   * version of the supplied {@code template}
   *
   * @see #assembleChangeLog()
   */
  @Override
  public void execute() throws MojoFailureException {
    final Log log = this.getLog();
    if (this.getSkip()) {
      if (log != null && log.isDebugEnabled()) {
        log.debug("Skipping execution by request");
      }
    } else {
      try {
        this.assembleChangeLog();
      } catch (final RuntimeException e) {
        throw e;
      } catch (final IOException e) {
        throw new MojoFailureException("Failure assembling changelog", e);
      } catch (final ArtifactResolutionException e) {
        throw new MojoFailureException("Failure assembling changelog", e);
      } catch (final DependencyGraphBuilderException e) {
        throw new MojoFailureException("Failure assembling changelog", e);
      }
    }
  }

  /**
   * Assembles a <a href="http://www.liquibase.org/">Liquibase</a> <a
   * href="http://www.liquibase.org/documentation/databasechangelog.html">changelog</a>
   * from changelog fragments {@linkplain #getChangeLogResources()
   * found in topological dependency order among the dependencies} of
   * the {@linkplain #getProject() current project}.
   *
   * <p>This method:</p>
   *
   * <ul>
   *
   * <li>{@linkplain #getChangeLogTemplateResource() Verifies that
   * there is a template} that exists either in the {@linkplain
   * #getProject() project} or (more commonly) in this plugin</li>
   *
   * <li>Verifies that the template can be read and has contents</li>
   *
   * <li>{@linkplain
   * Artifacts#getArtifactsInTopologicalOrder(MavenProject,
   * DependencyGraphBuilder, ArtifactFilter, ArtifactResolver,
   * ArtifactRepository) Retrieves and resolves the project's
   * dependencies and sorts them in topological order} from the
   * artifact with the least dependencies to {@linkplain #getProject()
   * the current project} (which by definition has the most
   * dependencies)</li>
   *
   * <li>Builds a {@link ClassLoader} that can "see" the {@linkplain
   * Artifact#getFile() <code>File</code>s associated with those
   * <code>Artifact</code>s} and uses it to {@linkplain
   * ClassLoader#getResources(String) find} the {@linkplain
   * #getChangeLogResourceNames() specified changelog resources}</li>
   * 
   * <li>Passes a {@link Collection} of {@link URL}s representing (in
   * most cases) {@code file:} or {@code jar:} {@link URL}s through
   * the {@linkplain TemplateRuntime MVEL template engine}, thus
   * merging the template and the {@link URL}s into an aggregating
   * changelog</li>
   *
   * <li>{@linkplain #write(String, Collection, File) Writes} the
   * resulting changelog to the destination denoted by the {@link
   * #getOutputFile() outputFile} parameter</li>
   * 
   * </ul>
   *
   * @exception ArtifactResolutionException if there was a problem
   * {@linkplain ArtifactResolver#resolve(ArtifactResolutionRequest)
   * resolving} a given {@link Artifact} representing a dependency
   *
   * @exception DependencyGraphBuilderException if there was a problem
   * with dependency resolution
   *
   * @exception IOException if there was a problem with input or
   * output
   *
   * @see #getChangeLogTemplateResource()
   *
   * @see #getChangeLogResources()
   *
   * @see #getOutputFile()
   *
   * @see #write(String, Collection, File)
   */
  public final void assembleChangeLog() throws ArtifactResolutionException, DependencyGraphBuilderException, IOException {
    final Log log = this.getLog();
    final URL changeLogTemplateResource = this.getChangeLogTemplateResource();
    if (log != null && log.isDebugEnabled()) {
      log.debug(String.format("Change log template resource: %s", changeLogTemplateResource));
    }
    if (changeLogTemplateResource != null) {
      final String templateContents = this.readTemplate(changeLogTemplateResource);
      if (log != null && log.isDebugEnabled()) {
        log.debug(String.format("Change log template contents: %s", templateContents));
      }
      if (templateContents != null) {
        final Collection<? extends URL> urls = this.getChangeLogResources();
        if (log != null && log.isDebugEnabled()) {
          log.debug(String.format("Change log resources: %s", urls));
        }
        if (urls != null && !urls.isEmpty()) {
          final File outputFile = this.getOutputFile();
          if (log != null && log.isDebugEnabled()) {
            log.debug(String.format("Output file: %s", outputFile));
          }
          if (outputFile != null) {
            this.write(templateContents, urls, outputFile);
          }
        }
      }
    }
  }

  /**
   * Writes appropriate representations of the supplied {@link URL}s
   * as interpreted and merged into the supplied {@code template}
   * contents to the {@link File} represented by the {@code
   * outputFile} parameter value.
   *
   * @param template an <a href="http://mvel.codehaus.org/">MVEL</a>
   * template; may be {@code null} in which case no action will be
   * taken
   *
   * @param urls a {@link Collection} of {@link URL}s representing
   * existing changelog fragment resources, sorted in topological
   * dependency order; may be {@code null} in which case no action
   * will be taken
   *
   * @param outputFile a {@link File} representing the full path to
   * the location where an aggregate changelog should be written; may
   * be {@code null} in which case no action will be taken; not
   * validated in any way by this method
   *
   * @exception IOException if there was a problem writing to the supplied {@link File}
   *
   * @exception TemplateSyntaxError if the supplied {@code template}
   * contained syntax errors
   *
   * @exception TemplateRuntimeError if there was a problem merging
   * the supplied {@link Collection} of {@link URL}s with the compiled
   * version of the supplied {@code template}
   *
   * @see #getOutputFile()
   *
   * @see #getChangeLogResources()
   *
   * @see #getChangeLogResourceNames()
   */
  public void write(final String template, final Collection<? extends URL> urls, final File outputFile) throws IOException {
    if (template != null && urls != null && !urls.isEmpty() && outputFile != null) {
      final CompiledTemplate compiledTemplate = TemplateCompiler.compileTemplate(template);
      assert compiledTemplate != null;
      final Map<Object, Object> variables = new HashMap<Object, Object>();
      variables.put("databaseChangeLogXsdVersion", this.getDatabaseChangeLogXsdVersion());
      variables.put("changeLogParameters", this.getChangeLogParameters());
      variables.put("resources", urls);
      String encoding = this.getChangeLogCharacterEncoding();
      if (encoding == null) {
        encoding = "UTF-8";
      }
      final Log log = this.getLog();
      if (log != null && log.isDebugEnabled()) {
        log.debug(String.format("Writing change log to %s using character encoding %s", outputFile, encoding));
      }
      final Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), encoding));
      try {
        TemplateRuntime.execute(compiledTemplate, this, new MapVariableResolverFactory(variables), null /* no TemplateRegistry */, new TemplateOutputWriter(writer));
      } finally {
        try {
          writer.flush();
        } catch (final IOException ignore) {
          // ignore on purpose
        }
        try {
          writer.close();
        } catch (final IOException ignore) {
          // ignore on purpose
        }
      }
    }
  }

  /**
   * Given a {@link URL} to a changelog template, fully reads that
   * template into memory and returns it, uninterpolated, as a {@link
   * String}.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @param changeLogTemplateResource a {@link URL} to an <a
   * href="http://mvel.codehaus.org/">MVEL<a> template; must not be
   * {@code null}
   *
   * @return the contents of the template, uninterpolated, or {@code
   * null}
   *
   * @exception IOException if an input/output error occurs
   *
   * @see #getTemplateCharacterEncoding()
   */
  private final String readTemplate(final URL changeLogTemplateResource) throws IOException {
    final Log log = this.getLog();
    if (changeLogTemplateResource == null) {
      throw new IllegalArgumentException("changeLogTemplateResource", new NullPointerException("changeLogTemplateResource"));
    }
    String returnValue = null;
    final InputStream rawStream = changeLogTemplateResource.openStream();
    if (rawStream != null) {
      BufferedReader reader = null;
      String templateCharacterEncoding = this.getTemplateCharacterEncoding();
      if (templateCharacterEncoding == null) {
        templateCharacterEncoding = "UTF-8";
      }
      if (log != null && log.isDebugEnabled()) {
        log.debug(String.format("Reading change log template from %s using character encoding %s", changeLogTemplateResource, templateCharacterEncoding));
      }
      try {
        reader = new BufferedReader(new InputStreamReader(rawStream, templateCharacterEncoding));
        String line = null;
        final StringBuilder sb = new StringBuilder();
        while ((line = reader.readLine()) != null) {
          sb.append(line);
          sb.append(LS);
        }
        returnValue = sb.toString();
      } finally {
        try {
          rawStream.close();
        } catch (final IOException nothingWeCanDo) {

        }
        if (reader != null) {
          try {
            reader.close();
          } catch (final IOException nothingWeCanDo) {

          }
        }
      }
    } else if (log != null && log.isDebugEnabled()) {
      log.debug(String.format("Opening change log template %s results in a null InputStream.", changeLogTemplateResource));
    }
    return returnValue;
  }

  /**
   * Creates and returns a new {@link ClassLoader} whose classpath
   * encompasses reachable changelog resources found among the
   * supplied {@link Artifact}s.
   *
   * <p>This method may return {@code null}.</p>
   *
   * @param artifacts an {@link Iterable} of {@link Artifact}s, some
   * of whose members may house changelog fragments; may be {@code
   * null} in which case {@code null} will be returned
   *
   * @return an appropriate {@link ClassLoader}, or {@code null}
   *
   * @exception MalformedURLException if during classpath assembly a
   * bad {@link URL} was encountered
   *
   * @see #toURLs(Artifact)
   */
  private final ClassLoader toClassLoader(final Iterable<? extends Artifact> artifacts) throws MalformedURLException {
    final Log log = this.getLog();
    ClassLoader loader = null;
    if (artifacts != null) {
      final Collection<URL> urls = new ArrayList<URL>();
      for (final Artifact artifact : artifacts) {
        final Collection<? extends URL> classpathElements = this.toURLs(artifact);
        if (classpathElements != null && !classpathElements.isEmpty()) {
          urls.addAll(classpathElements);
        }
      }
      if (!urls.isEmpty()) {
        if (log != null && log.isDebugEnabled()) {
          log.debug(String.format("Creating URLClassLoader with the following classpath: %s", urls));
        }
        loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), Thread.currentThread().getContextClassLoader());
      }
    }
    return loader;
  }

  /**
   * Returns a {@link Collection} of {@link URL}s representing the
   * locations of the given {@link Artifact}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <h4>Design Notes</h4>
   *
   * <p>This method returns a {@link Collection} of {@link URL}s
   * instead of a single {@link URL} because an {@link Artifact}
   * representing the {@linkplain #getProject() current project being
   * built} has two conceptual locations for our purposes: the test
   * output directory and the build output directory.  All other
   * {@link Artifact}s have exactly one location, <em>viz.</em> {@link
   * Artifact#getFile()}.</p>
   *
   * @param artifact the {@link Artifact} for which {@link URL}s
   * should be returned; may be {@code null} in which case an
   * {@linkplain Collection#emptySet() empty <code>Collection</code>}
   * will be returned
   *
   * @return a {@link Collection} of {@link URL}s; never {@code null}
   *
   * @exception MalformedURLException if an {@link Artifact}'s
   * {@linkplain Artifact#getFile() associated <code>File</code>}
   * could not be {@linkplain URI#toURL() converted into a
   * <code>URL</code>}
   *
   * @see Artifact#getFile()
   *
   * @see Build#getTestOutputDirectory()
   *
   * @see Build#getOutputDirectory()
   *
   * @see File#toURI()
   *
   * @see URI#toURL()
   */
  private final Collection<? extends URL> toURLs(final Artifact artifact) throws MalformedURLException {
    Collection<URL> urls = null;
    if (artifact != null) {

      // If the artifact represents the current project itself, then
      // we need to look in the reactor first (i.e. the
      // project.build.testOutpuDirectory and the
      // project.build.outputDirectory areas), since a .jar file for
      // the project in all likelihood has not yet been created.
      final String groupId = artifact.getGroupId();
      if (groupId != null) {
        final MavenProject project = this.getProject();
        if (project != null && groupId.equals(project.getGroupId())) {
          final String artifactId = artifact.getArtifactId();
          if (artifactId != null && artifactId.equals(project.getArtifactId())) {
            final Build build = project.getBuild();
            if (build != null) {
              urls = new ArrayList<URL>();
              urls.add(new File(build.getTestOutputDirectory()).toURI().toURL());
              urls.add(new File(build.getOutputDirectory()).toURI().toURL());
            }
          }
        }
      }

      // If on the other hand the artifact was just a garden-variety
      // direct or transitive dependency, then just add its file: URL
      // directly.
      if (urls == null) {
        final File file = artifact.getFile();
        if (file != null) {
          final URI uri = file.toURI();
          if (uri != null) {
            urls = Collections.singleton(uri.toURL());
          }
        }
      }

    }
    if (urls == null) {
      urls = Collections.emptySet();
    }
    return urls;
  }

}
