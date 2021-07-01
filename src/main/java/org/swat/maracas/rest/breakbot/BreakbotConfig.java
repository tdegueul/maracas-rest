package org.swat.maracas.rest.breakbot;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class BreakbotConfig {
	private String mvnPom;
	private List<String> mvnGoals = new ArrayList<>();
	private List<String> mvnProperties = new ArrayList<>();
	private String jarLocation;
	private List<String> githubClients = new ArrayList<>();

	public String getMvnPom() {
		return mvnPom;
	}

	public List<String> getMvnGoals() {
		return mvnGoals;
	}

	public List<String> getMvnProperties() {
		return mvnProperties;
	}

	public String getJarLocation() {
		return jarLocation;
	}

	public List<String> getGithubClients() {
		return githubClients;
	}

	@JsonProperty("build")
	private void unpackBuild(Map<String, String> build) {
		if (build.containsKey("pom"))
			mvnPom = build.get("pom");
		if (build.containsKey("goals"))
			mvnGoals = Arrays.asList(build.get("goals").split(" "));
		if (build.containsKey("properties"))
			mvnProperties = Arrays.asList(build.get("properties").split(" "));
		if (build.containsKey("jar"))
			jarLocation = build.get("jar");
	}

	@JsonProperty("clients")
	private void unpackClients(Map<String, List<String>> clients) {
		githubClients = clients.get("github");
	}

	public static BreakbotConfig defaultConfig() {
		return new BreakbotConfig();
	}

	public static BreakbotConfig fromYaml(InputStream in) {
		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			return mapper.readValue(in, BreakbotConfig.class);
		} catch (IOException e) {
			return defaultConfig();
		}
	}
}