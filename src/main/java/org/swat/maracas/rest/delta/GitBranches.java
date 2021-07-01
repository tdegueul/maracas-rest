package org.swat.maracas.rest.delta;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.swat.maracas.rest.breakbot.BreakbotConfig;
import org.swat.maracas.rest.data.MaracasReport;
import org.swat.maracas.rest.tasks.BuildException;
import org.swat.maracas.rest.tasks.CloneAndBuild;
import org.swat.maracas.rest.tasks.CloneException;
import org.swat.maracas.spoon.MaracasAnalysis;

public class GitBranches implements Diffable {
	private final GitHub github;
	private final BreakbotConfig config;
	private final GHBranch base;
	private final GHBranch head;
	private final String clonePath;
	private static final Logger logger = LogManager.getLogger(GitBranches.class);

	public GitBranches(GHBranch base, GHBranch head, BreakbotConfig config, GitHub github, String clonePath) {
		this.base = base;
		this.head = head;
		this.config = config;
		this.github = github;
		this.clonePath = clonePath;
	}

	@Override
	public MaracasReport diff() {
		try {
			Path basePath = Paths.get(clonePath)
				.resolve(String.valueOf(base.getOwner().getId()))
				.resolve(base.getSHA1());
			Path headPath = Paths.get(clonePath)
				.resolve(String.valueOf(base.getOwner().getId()))
				.resolve(head.getSHA1());

			// Clone and build both repos
			CompletableFuture<Path> baseFuture = CompletableFuture.supplyAsync(
					new CloneAndBuild(base.getOwner().getHttpTransportUrl(), base.getName(),
						basePath, config));
			CompletableFuture<Path> headFuture = CompletableFuture.supplyAsync(
					new CloneAndBuild(head.getOwner().getHttpTransportUrl(), head.getName(),
						headPath, config));
			CompletableFuture.allOf(baseFuture, headFuture).join();
			Path j1 = baseFuture.get();
			Path j2 = headFuture.get();

			// Compute delta model
			MaracasAnalysis analysis = new MaracasAnalysis(j1, j2);
			logger.info("Computing delta {} -> {}", j1, j2);
			analysis.computeDelta();

			config.getGithubClients().parallelStream().forEach(c -> {
				try {
					// Clone the client
					logger.info("Building client {}", c);
					GHRepository clientRepo = github.getRepository(c);
					String clientBranch = clientRepo.getDefaultBranch();
					Path clientPath = Paths.get(clonePath)
						.resolve(clientRepo.getOwnerName())
						.resolve(clientRepo.getBranch(clientBranch).getSHA1());

					String fullRef = "refs/heads/" + clientBranch; // FIXME
					CloneCommand clone =
						Git.cloneRepository()
							.setURI(clientRepo.getHttpTransportUrl())
							.setBranchesToClone(Collections.singletonList(fullRef))
							.setBranch(fullRef)
							.setDirectory(clientPath.toFile());

					try (Git g = clone.call()) {
						// Let me please try-with-resource without a variable :(
					} catch (GitAPIException e) {
						// Rethrow unchecked to get past
						// the Supplier interface
						throw new CloneException(e);
					}

					logger.info("Computing detections on client {}", c);
					analysis.computeDetections(clientPath);
				} catch (IOException e) {
					logger.error(e);
				}
			});

			return new MaracasReport(analysis);
		} catch (ExecutionException | InterruptedException e) {
			logger.error(e);
			Thread.currentThread().interrupt();
			return new MaracasReport(e);
		} catch (BuildException | CloneException e) {
			return new MaracasReport(e);
		}
	}
}
