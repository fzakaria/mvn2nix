package com.fzakaria.mvn2nix.maven.listener;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;

/**
 * Outputs Repository Events to a logger.
 *
 * @author daniel
 */
public class LoggingRepositoryListener extends AbstractRepositoryListener {

    private final static Logger logger =
            LoggerFactory.getLogger(LoggingRepositoryListener.class);

    @Override
    public void artifactDeployed(RepositoryEvent event) {
        logger.info("Deployed {} to {}.", event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactDeploying(RepositoryEvent event) {
        logger.info("Deploying {} to {}.", event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactDescriptorInvalid(RepositoryEvent event) {
        logger.error("Invalid artifact descriptor for {}: {}.", event.getArtifact(), event.getException().getMessage());
    }

    @Override
    public void artifactDescriptorMissing(RepositoryEvent event) {
        logger.error("Missing artifact descriptor for {}.", event.getArtifact());
    }

    @Override
    public void artifactInstalled(RepositoryEvent event) {
        logger.info("Installed {} to {}.", event.getArtifact(), event.getFile());
    }

    @Override
    public void artifactInstalling(RepositoryEvent event) {
        logger.info("Installing {} to {}.", event.getArtifact(), event.getFile());
    }

    @Override
    public void artifactResolved(RepositoryEvent event) {
        logger.info("Resolved artifact {} from {}.", event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactDownloading(RepositoryEvent event) {
        logger.info("Downloading artifact {} from {}.", event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactDownloaded(RepositoryEvent event) {
        logger.info("Downloaded artifact {} from {}.", event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactResolving(RepositoryEvent event) {
        logger.info("Resolving artifact {}.", event.getArtifact());
    }

    @Override
    public void metadataDeployed(RepositoryEvent event) {
        logger.info("Deployed {} to {}.", event.getMetadata(), event.getRepository());
    }

    @Override
    public void metadataDeploying(RepositoryEvent event) {
        logger.info("Deploying {} to {}.", event.getMetadata(), event.getRepository());
    }

    @Override
    public void metadataInstalled(RepositoryEvent event) {
        logger.info("Installed {} to {}.", event.getMetadata(), event.getFile());
    }

    @Override
    public void metadataInstalling(RepositoryEvent event) {
        logger.info("Installing {} to {}.", event.getMetadata(), event.getFile());
    }

    @Override
    public void metadataInvalid(RepositoryEvent event) {
        logger.info("Invalid metadata {}.", event.getMetadata());
    }

    @Override
    public void metadataResolved(RepositoryEvent event) {
        logger.info("Resolved metadata {} from {}.", event.getMetadata(), event.getRepository());
    }

    @Override
    public void metadataResolving(RepositoryEvent event) {
        logger.info("Resolving metadata {} from {}.", event.getMetadata(), event.getRepository());
    }

    @Override
    public void metadataDownloaded(RepositoryEvent event) {
        logger.info("Downloaded metadata {} from {}.", event.getMetadata(), event.getRepository());
    }

    @Override
    public void metadataDownloading(RepositoryEvent event) {
        logger.info("Downloading metadata {} from {}.", event.getMetadata(), event.getRepository());
    }
}