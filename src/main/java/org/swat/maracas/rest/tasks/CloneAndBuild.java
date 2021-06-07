package org.swat.maracas.rest.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jgit.api.Git;

/**
 * Clones & builds a repository, returns the JAR
 */
public class CloneAndBuild implements Supplier<Path> {
	private static final Logger logger = LogManager.getLogger(CloneAndBuild.class);
	private final String url;
	private final String ref;
	private final Path dest;
	
	public CloneAndBuild(String url, String ref, Path dest) {
		this.url = url;
		this.ref = ref;
		this.dest = dest;
	}

	@Override
	public Path get() {
		clone(url, ref, dest);
		return build(dest);
	}

	private void clone(String url, String ref, Path dest) throws CloneException {
		if (!dest.toFile().exists()) {
			logger.info("Cloning {} [{}]", url, ref);
			String fullRef = "refs/heads/" + ref; // FIXME

			try {
				Git.cloneRepository()
					.setURI(url)
					.setBranchesToClone(Collections.singletonList(fullRef))
					.setBranch(fullRef)
					.setDirectory(dest.toFile())
					.call();
			} catch (Exception e) {
				throw new CloneException(e);
			}
		} else logger.info("{} exists. Skipping.", dest);
	}

	// FIXME: Will only work with a pom.xml file located in the projet's root
	// producing a JAR in the project's root /target/
	private Path build(Path local) throws BuildException {
		Path target = local.resolve("target");
		Path pom = local.resolve("pom.xml");
		if (!target.toFile().exists()) {
			if (!pom.toFile().exists())
				throw new BuildException("No root pom.xml file in " + local);

			logger.info("Building {}", pom);
			Properties properties = new Properties();
			properties.setProperty("skipTests", "true");
			
		    InvocationRequest request = new DefaultInvocationRequest();
		    request.setPomFile(pom.toFile());
		    request.setGoals(Collections.singletonList("package"));
		    request.setProperties(properties);
		    request.setBatchMode(true);

		    try {
			    Invoker invoker = new DefaultInvoker();
			    InvocationResult result = invoker.execute(request);
	
			    if (result.getExecutionException() != null)
			    	throw new BuildException("'package' goal failed: " + result.getExecutionException().getMessage());
			    if (result.getExitCode() != 0)
			    	throw new BuildException("'package' goal failed: " + result.getExitCode());
			    if (!target.toFile().exists())
			    	throw new BuildException("'package' goal did not produce a /target/");
		    } catch (MavenInvocationException e) {
		    	throw new BuildException(e);
		    }
		} else logger.info("{} has already been built. Skipping.", local);

		// FIXME: Just returning whatever .jar we found in /target/
	    try (Stream<Path> walk = Files.walk(target, 1)) {
	        Optional<Path> res = walk.filter(f -> f.toString().endsWith(".jar")).findFirst();
	        if (res.isPresent())
	        	return res.get();
	        else
	        	throw new BuildException("'package' goal on " + pom + " did not produce a JAR");
	    } catch (IOException e) {
	    	throw new BuildException(e);
		}
	}
}
