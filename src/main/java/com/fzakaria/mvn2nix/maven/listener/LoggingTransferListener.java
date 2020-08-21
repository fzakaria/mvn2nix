package com.fzakaria.mvn2nix.maven.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;

/**
 * Outputs Transfer Events to a logger.
 *
 * @author daniel
 */
public class LoggingTransferListener extends AbstractTransferListener {

    private final static Logger logger =
            LoggerFactory.getLogger(LoggingRepositoryListener.class);

    @Override
    public void transferFailed(TransferEvent event) {
        logger.error("{} failed: {}.", getTransferType(event), event.getException().getMessage());
    }

    @Override
    public void transferInitiated(TransferEvent event) throws TransferCancelledException {
        logger.info("{}: {}{}.", getTransferType(event), event.getResource().getRepositoryUrl(), event.getResource().getResourceName());
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        logger.info("{} completed: {}{}.", getTransferType(event), event.getResource().getRepositoryUrl(), event.getResource().getResourceName());
    }

    private String getTransferType(TransferEvent event) {
        String transferType = "Downloading";
        if(event.getRequestType() == TransferEvent.RequestType.PUT) {
            transferType = "Uploading";
        }
        return transferType;
    }
}