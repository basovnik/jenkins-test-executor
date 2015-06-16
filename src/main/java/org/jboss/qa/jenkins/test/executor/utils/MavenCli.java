/*
 * Copyright 2015 Red Hat Inc. and/or its affiliates and other contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.qa.jenkins.test.executor.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MavenCli {

	protected final File mavenHome;
	protected final File javaHome;
	protected final String xms;
	protected final String xmx;
	protected final String minPermSize;
	protected final String maxPermSize;
	protected final File pom;
	protected final boolean alsoMake;
	protected final boolean alsoMakeDependents;
	protected final boolean debug;
	protected final boolean nonRecursive;
	protected final boolean failAtEnd;
	protected final List<String> goals;
	protected final Set<String> profiles;
	protected final Set<String> projects;
	protected final Set<String> mavenOpts;
	protected final Map<String, String> sysProps;
	protected final Set<String> params;

	private MavenCli(Builder builder) {
		// Mandatory properties
		checkMandatoryProperty("pom", pom = builder.pom);
		checkMandatoryProperty("goals", goals = builder.goals);
		// Optional properties
		javaHome = builder.javaHome;
		mavenHome = builder.mavenHome;
		xms = builder.xms;
		xmx = builder.xmx;
		minPermSize = builder.minPermSize;
		maxPermSize = builder.maxPermSize;
		mavenOpts = builder.mavenOpts;
		sysProps = builder.sysProps;
		profiles = builder.profiles;
		projects = builder.projects;
		params = builder.params;
		alsoMake = builder.alsoMake;
		debug = builder.debug;
		alsoMakeDependents = builder.alsoMakeDependents;
		nonRecursive = builder.nonRecursive;
		failAtEnd = builder.failAtEnd;
	}

	protected void checkMandatoryProperty(String name, Object value) {
		if (value == null) {
			throw new IllegalArgumentException(String.format("Property '%s' is mandatory", name));
		}
	}

	public String getXms() {
		return xms;
	}

	public String getXmx() {
		return xmx;
	}

	public String getMinPermSize() {
		return minPermSize;
	}

	public String getMaxPermSize() {
		return maxPermSize;
	}

	public Set<String> getParams() {
		return params;
	}

	public File getMavenHome() {
		return mavenHome;
	}

	public File getJavaHome() {
		return javaHome;
	}

	public File getPom() {
		return pom;
	}

	public boolean isAlsoMake() {
		return alsoMake;
	}

	public boolean isDebug() {
		return debug;
	}

	public boolean isNonRecursive() {
		return nonRecursive;
	}

	public boolean isAlsoMakeDependents() {
		return alsoMakeDependents;
	}

	public boolean isFailAtEnd() {
		return failAtEnd;
	}

	public Set<String> getMavenOpts() {
		return mavenOpts;
	}

	public Map<String, String> getSysProps() {
		return sysProps;
	}

	public List<String> getGoals() {
		return goals;
	}

	public Set<String> getProfiles() {
		return profiles;
	}

	public Set<String> getProjects() {
		return projects;
	}

	private List<String> generateCommand() {
		final List<String> cmd = new ArrayList<>();

		// Maven
		if (SystemUtils.IS_OS_WINDOWS) {
			cmd.add("cmd");
			cmd.add("/c");
			cmd.add(mavenHome != null ? mavenHome + File.separator + "bin" + File.separator + "mvn.bat" : "mvn.bat");
		} else {
			cmd.add("bash");
			cmd.add(mavenHome != null ? mavenHome + File.separator + "bin" + File.separator + "mvn" : "mvn");
		}

		// Maven opts
		if (xms != null) {
			mavenOpts.add("-Xms" + xms);
		}
		if (xmx != null) {
			mavenOpts.add("-Xmx" + xmx);
		}
		if (minPermSize != null) {
			mavenOpts.add("-XX:PermSize=" + minPermSize);
		}
		if (maxPermSize != null) {
			mavenOpts.add("-XX:MaxPermSize=" + maxPermSize);
		}

		// Path to POM file
		cmd.add("-f");
		cmd.add(pom.getAbsolutePath());

		cmd.addAll(goals);

		// Profiles
		if (!profiles.isEmpty()) {
			cmd.add("-P" + StringUtils.join(profiles, ","));
		}

		// Projects
		if (!projects.isEmpty()) {
			cmd.add("-pl");
			cmd.add(StringUtils.join(projects, ","));
		}

		// If project list is specified, also build projects required by the list
		if (alsoMake) {
			cmd.add("-am");
		}

		// If project list is specified, also build projects that depend on projects on the list
		if (alsoMakeDependents) {
			cmd.add("-amd");
		}

		// Produce execution debug output
		if (debug) {
			cmd.add("-X");
		}

		if (nonRecursive) {
			cmd.add("-N");
		}

		// Only fail the build afterwards; allow all non-impacted builds to continue
		if (failAtEnd) {
			cmd.add("-fae");
		}

		// System properties
		for (Map.Entry<String, String> entry : sysProps.entrySet()) {
			cmd.add(String.format("-D%s=%s", entry.getKey(), entry.getValue()));
		}

		if (params != null) {
			cmd.addAll(params);
		}
		log.info("Process arguments: " + cmd.toString());
		return cmd;
	}

	public int run() throws Exception {

		final ProcessBuilder processBuilder = new ProcessBuilder(generateCommand());
		//processBuilder.inheritIO();
		File fout = new File("mvn-out.log");
		fout.createNewFile();
		File ferr = new File("mvn-err.log");
		ferr.createNewFile();
		File fin = new File("mvn-in.log");
		fin.createNewFile();
		processBuilder.redirectOutput(fout);
		processBuilder.redirectError(ferr);
		processBuilder.redirectInput(fin);
		processBuilder.environment().putAll(System.getenv());
		if (javaHome != null) {
			processBuilder.environment().put("JAVA_HOME", javaHome.getAbsolutePath());
		}
		if (mavenHome != null) {
			processBuilder.environment().put("M2_HOME", mavenHome.getAbsolutePath());
		}
		processBuilder.environment().put("MAVEN_OPTS", StringUtils.join(mavenOpts, " "));

		log.info("JAVA_HOME={}", processBuilder.environment().get("JAVA_HOME"));
		log.info("M2_HOME={}", processBuilder.environment().get("M2_HOME"));
		log.info("MAVEN_OPTS={}", processBuilder.environment().get("MAVEN_OPTS"));

		final Process process = processBuilder.start();
		return process.waitFor();
	}

	public static class Builder {
		private File mavenHome;
		private File javaHome;
		private String xms;
		private String xmx;
		private String minPermSize;
		private String maxPermSize;
		private File pom;
		private boolean alsoMake;
		private boolean alsoMakeDependents;
		private boolean debug;
		private boolean nonRecursive;
		private boolean failAtEnd;
		private List<String> goals;
		private Set<String> profiles;
		private Set<String> projects;
		private Set<String> mavenOpts;
		private Map<String, String> sysProps;
		private Set<String> params;

		public Builder() {
			alsoMake = false;
			alsoMakeDependents = false;
			debug = false;
			nonRecursive = false;
			failAtEnd = false;
			xms = "64m";
			xmx = "256m";
			goals = new ArrayList<>();
			profiles = new HashSet<>();
			projects = new HashSet<>();
			mavenOpts = new HashSet<>();
			sysProps = new HashMap<>();
			params = new HashSet<>();
		}

		public Builder mavenHome(File mavenHome) {
			this.mavenHome = mavenHome;
			return this;
		}

		public Builder javaHome(File javaHome) {
			this.javaHome = javaHome;
			return this;
		}

		public Builder xms(String xms) {
			this.xms = xms;
			return this;
		}

		public Builder xmx(String xmx) {
			this.xmx = xmx;
			return this;
		}

		public Builder minPermSize(String minPermSize) {
			this.minPermSize = minPermSize;
			return this;
		}

		public Builder maxPermSize(String maxPermSize) {
			this.maxPermSize = maxPermSize;
			return this;
		}

		public Builder alsoMake(boolean alsoMake) {
			this.alsoMake = alsoMake;
			return this;
		}

		public Builder alsoMakeDependents(boolean alsoMakeDependents) {
			this.alsoMakeDependents = alsoMakeDependents;
			return this;
		}

		public Builder debug(boolean debug) {
			this.debug = debug;
			return this;
		}

		public Builder nonRecursive(boolean nonRecursive) {
			this.nonRecursive = nonRecursive;
			return this;
		}

		public Builder failAtEnd(boolean failAtEnd) {
			this.failAtEnd = failAtEnd;
			return this;
		}

		public Builder pom(File pom) {
			this.pom = pom;
			return this;
		}

		public Builder pom(String pom) {
			this.pom = new File(pom);
			return this;
		}

		public Builder goals(String... goals) {
			this.goals.addAll(Arrays.asList(goals));
			return this;
		}

		public Builder goals(Collection<String> goals) {
			this.goals.addAll(goals);
			return this;
		}

		public Builder profiles(String... profiles) {
			this.profiles.addAll(Arrays.asList(profiles));
			return this;
		}

		public Builder profiles(Collection<String> profiles) {
			this.profiles.addAll(profiles);
			return this;
		}

		public Builder projects(String... projects) {
			this.projects.addAll(Arrays.asList(projects));
			return this;
		}

		public Builder projects(Collection<String> projects) {
			this.projects.addAll(projects);
			return this;
		}

		public Builder mavenOpts(String... mavenOpts) {
			this.mavenOpts.addAll(Arrays.asList(mavenOpts));
			return this;
		}

		public Builder mavenOpts(Collection<String> mavenOpts) {
			this.mavenOpts.addAll(mavenOpts);
			return this;
		}

		public Builder sysProp(String key, String value) {
			this.sysProps.put(key, value);
			return this;
		}

		public Builder params(String... params) {
			this.params.addAll(Arrays.asList(params));
			return this;
		}

		public Builder params(Collection<String> params) {
			this.params.addAll(params);
			return this;
		}

		public MavenCli build() {
			if (mavenHome != null && !mavenHome.exists()) {
				throw new IllegalArgumentException(String.format("Maven home does not exist: %s", mavenHome.getAbsolutePath()));
			}
			if (javaHome != null && !javaHome.exists()) {
				throw new IllegalArgumentException(String.format("Java home does not exist: %s", javaHome.getAbsolutePath()));
			}
			return new MavenCli(this);
		}
	}
}
