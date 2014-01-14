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
 * href="http://www.liquibase.org/">Liquibase</a> changelog fragments
 * and assembles a master changelog that includes them all in
 * dependency order.
 *
 * @author <a href="http://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see AbstractLiquibaseMojo
 */
@Mojo(name = "assembleChangeLog", requiresDependencyResolution = ResolutionScope.TEST)
public class AssembleChangeLogMojo extends AbstractLiquibaseMojo {

  /**
   * The platform's line separator; "{@code \\n}" by default.  This
   * field is never {@code null}.
   */
  private static final String LS = System.getProperty("line.separator", "\n");

  /**
   * The {@link DependencyGraphBuilder} injected by Maven.  This field
   * is never {@code null} during normal plugin execution.
   */
  @Component
  private DependencyGraphBuilder dependencyGraphBuilder;

  /**
   * The {@link ArtifactResolver} injected by Maven.  This field is
   * never {@code null} during normal plugin execution.
   */
  @Component
  private ArtifactResolver artifactResolver;

  /**
   * Whether or not this plugin execution should be skipped; {@code
   * false} by default.
   */
  @Parameter(defaultValue = "false")
  private boolean skip;

  /**
   * The local {@link ArtifactRepository} injected by Maven.  This
   * field is never {@code null} during normal plugin execution.
   */
  @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
  private ArtifactRepository localRepository;

  /**
   * An {@link ArtifactFilter} to use to limit what dependencies are
   * scanned for changelog fragments; {@code null} by default.
   */
  @Parameter
  private ArtifactFilter artifactFilter;

  /**
   * A list of classpath resource names that identity <a
   * href="http://liquibase.org/">Liquibase</a> changelogs; {@code
   * META-INF/liquibase/changelog.xml} by default.
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
   */
  @Parameter(defaultValue = "changelog-template.mvl", required = true)
  private String changeLogTemplateResourceName;

  /**
   * The full path to the changelog that will be generated;
   * <code>${project.build.directory}/generated-sources/liquibase/changelog.xml</code>
   * by default.
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-sources/liquibase/changelog.xml", required = true)
  private File outputFile;

  /**
   * The version of the proper XSD file to use that defines the
   * contents of a <a href="http://liquibase.org/">Liquibase</a>
   * changelog file; {@code 3.0} by default.
   */
  @Parameter(defaultValue = "3.0", required = true)
  private String databaseChangeLogXsdVersion;

  /**
   * A set of {@link Properties} defining changelog parameters; {@code
   * null} by default.
   */
  @Parameter
  private Properties changeLogParameters;

  /**
   * The character encoding to use while reading in the changelog <a
   * href="http://mvel.codehaus.org/">MVEL</a> template;
   * <code>${project.build.sourceEncoding}</code> by default.
   */
  @Parameter(required = true, defaultValue = "${project.build.sourceEncoding}")
  private String templateCharacterEncoding;

  /**
   * The character encoding to use while writing the generated
   * changelog; <code>${project.build.sourceEncoding}</code> by
   * default.
   */
  @Parameter(required = true, defaultValue = "${project.build.sourceEncoding}")
  private String changelogCharacterEncoding;


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


  /**
   * 
   */
  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!this.getSkip()) {
      try {
        this.write();
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

  public boolean getSkip() {
    return this.skip;
  }

  public void setSkip(final boolean skip) {
    this.skip = skip;
  }

  public ArtifactRepository getLocalRepository() {
    return this.localRepository;
  }

  public void setLocalRepository(final ArtifactRepository localRepository) {
    if (localRepository == null) {
      throw new IllegalArgumentException("localRepository", new NullPointerException("localRepository"));
    }
    this.localRepository = localRepository;
  }

  public ArtifactFilter getArtifactFilter() {
    return this.artifactFilter;
  }

  public void setArtifactFilter(final ArtifactFilter filter) {
    this.artifactFilter = filter;
  }

  public ArtifactResolver getArtifactResolver() {
    return this.artifactResolver;
  }

  public void setArtifactResolver(final ArtifactResolver resolver) {
    this.artifactResolver = resolver;
  }

  public List<String> getChangeLogResourceNames() {
    return this.changeLogResourceNames;
  }

  public void setChangeLogResourceNames(final List<String> changeLogResourceNames) {
    this.changeLogResourceNames = changeLogResourceNames;
  }

  public URL getChangeLogTemplateResource() {
    final String resourceName;
    final ClassLoader loader;
    if (this.changeLogTemplateResourceName == null) {
      resourceName = "changelog-template.mvl";
      loader = this.getClass().getClassLoader();
    } else {
      resourceName = this.changeLogTemplateResourceName;
      loader = Thread.currentThread().getContextClassLoader();
    }
    URL resource = null;
    if (loader != null) {
      resource = loader.getResource(resourceName);
    }
    return resource;
  }

  public void setOutputFile(final File file) {
    if (file != null && file.isDirectory()) {
      throw new IllegalArgumentException("file", new IOException("file.isDirectory()"));
    }
    this.outputFile = file;
  }

  public File getOutputFile() throws IOException {
    if (this.outputFile == null) {
      throw new IllegalStateException("this.outputFile", new NullPointerException("this.outputFile"));
    }
    if (this.outputFile.isDirectory()) {
      throw new IllegalStateException("this.outputFile.isDirectory()");
    }
    final File parent = this.outputFile.getParentFile();
    if (parent != null) {
      if (!parent.mkdirs()) {
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

  public void write(final String templateContents, final Collection<? extends URL> urls, final File outputFile) throws ArtifactResolutionException, DependencyGraphBuilderException, IOException {
    if (templateContents != null && urls != null && !urls.isEmpty() && outputFile != null) {
      final CompiledTemplate template = TemplateCompiler.compileTemplate(templateContents);
      assert template != null;
      final Map<Object, Object> variables = new HashMap<Object, Object>();
      variables.put("databaseChangeLogXsdVersion", this.getDatabaseChangeLogXsdVersion());
      variables.put("changeLogParameters", this.getChangeLogParameters());
      variables.put("resources", urls);
      final Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), this.changelogCharacterEncoding));
      try {
        TemplateRuntime.execute(template, this, new MapVariableResolverFactory(variables), null /* no TemplateRegistry */, new TemplateOutputWriter(writer));
      } finally {
        try {
          writer.flush();
          writer.close();
        } catch (final IOException ignore) {

        }
      }
    }
  }

  public void write() throws ArtifactResolutionException, DependencyGraphBuilderException, IOException {
    final MavenProject project = this.getProject();
    if (project == null) {
      throw new IllegalStateException("this.getProject()", new NullPointerException("this.getProject()"));
    }
    final URL changeLogTemplateResource = this.getChangeLogTemplateResource();
    if (changeLogTemplateResource != null) {
      final String templateContents = this.readTemplate(changeLogTemplateResource);
      if (templateContents != null) {
        final Collection<? extends URL> urls = this.getChangeLogResources();
        if (urls != null && !urls.isEmpty()) {
          final File outputFile = this.getOutputFile();
          if (outputFile != null) {
            this.write(templateContents, urls, outputFile);
          }
        }
      }
    }
  }

  public String getDatabaseChangeLogXsdVersion() {
    return this.databaseChangeLogXsdVersion;
  }

  public void setDatabaseChangeLogXsdVersion(final String databaseChangeLogXsdVersion) {
    this.databaseChangeLogXsdVersion = databaseChangeLogXsdVersion;
  }

  public Properties getChangeLogParameters() {
    return this.changeLogParameters;
  }

  public void setChangeLogParameters(final Properties parameters) {
    this.changeLogParameters = parameters;
  }

  public DependencyGraphBuilder getDependencyGraphBuilder() {
    return this.dependencyGraphBuilder;
  }

  public void setDependencyGraphBuilder(final DependencyGraphBuilder dependencyGraphBuilder) {
    if (dependencyGraphBuilder == null) {
      throw new IllegalArgumentException("dependencyGraphBuilder", new NullPointerException("dependencyGraphBuilder"));
    }
    this.dependencyGraphBuilder = dependencyGraphBuilder;
  }

  private final Collection<? extends URL> getChangeLogResources() throws ArtifactResolutionException, DependencyGraphBuilderException, IOException {
    final MavenProject project = this.getProject();
    if (project == null) {
      throw new IllegalStateException("this.getProject()", new NullPointerException("this.getProject()"));
    }
    final DependencyGraphBuilder dependencyGraphBuilder = this.getDependencyGraphBuilder();
    if (dependencyGraphBuilder == null) {
      throw new IllegalStateException("this.getDependencyGraphBuilder()", new NullPointerException("this.getDependencyGraphBuilder()"));
    }
    final Collection<? extends Artifact> artifacts = new Artifacts().getArtifactsInTopologicalOrder(project,
                                                                                                    dependencyGraphBuilder,
                                                                                                    this.getArtifactFilter(),
                                                                                                    this.getArtifactResolver(),
                                                                                                    this.getLocalRepository());
    Collection<? extends URL> urls = null;
    if (artifacts != null && !artifacts.isEmpty()) {
      urls = getChangeLogResources(artifacts);
    }
    return urls;
  }

  private final Collection<? extends URL> getChangeLogResources(final Iterable<? extends Artifact> artifacts) throws IOException {
    Collection<URL> returnValue = null;
    final ClassLoader loader = this.toClassLoader(artifacts);
    if (loader != null) {
      final Iterable<String> changeLogResourceNames = this.getChangeLogResourceNames();
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
    return returnValue;
  }

  private final String readTemplate(final URL changeLogTemplateResource) throws IOException {
    if (changeLogTemplateResource == null) {
      throw new IllegalArgumentException("changeLogTemplateResource", new NullPointerException("changeLogTemplateResource"));
    }
    String returnValue = null;
    final InputStream rawStream = changeLogTemplateResource.openStream();
    if (rawStream != null) {
      BufferedReader reader = null;
      try {
        reader = new BufferedReader(new InputStreamReader(rawStream, this.templateCharacterEncoding));
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
    }
    return returnValue;
  }

  private final ClassLoader toClassLoader(final Iterable<? extends Artifact> artifacts) throws MalformedURLException {
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
        loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), Thread.currentThread().getContextClassLoader());
      }
    }
    return loader;
  }

  private final Collection<? extends URL> toURLs(final Artifact artifact) throws MalformedURLException {
    Collection<URL> urls = null;
    if (artifact != null) {
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
    return urls;
  }

}
