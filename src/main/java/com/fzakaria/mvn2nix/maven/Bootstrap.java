package com.fzakaria.mvn2nix.maven;

import com.fzakaria.mvn2nix.maven.listener.LoggingRepositoryListener;
import com.fzakaria.mvn2nix.maven.listener.LoggingTransferListener;
import org.apache.maven.lifecycle.LifeCyclePluginAnalyzer;
import org.apache.maven.lifecycle.internal.DefaultLifecyclePluginAnalyzer;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A helper to boot the repository system and a repository system session.
 */
public final class Bootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrap.class);

    public static ServiceLocator serviceLocator() {
        /*
         * Aether's components implement org.eclipse.aether.spi.locator.Service to ease manual wiring and using the
         * prepopulated DefaultServiceLocator, we only need to register the repository connector and transporter
         * factories.
         */
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService( RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class );
        locator.addService( TransporterFactory.class, FileTransporterFactory.class );
        locator.addService( TransporterFactory.class, HttpTransporterFactory.class );
        locator.addService(LifeCyclePluginAnalyzer.class, DefaultLifecyclePluginAnalyzer.class );

        locator.setErrorHandler( new DefaultServiceLocator.ErrorHandler()
        {
            @Override
            public void serviceCreationFailed( Class<?> type, Class<?> impl, Throwable exception )
            {
                LOGGER.error( "Service creation failed for {} with implementation {}",
                        type, impl, exception );
            }
        } );

        return locator;
    }

    public static RepositorySystem newRepositorySystem(ServiceLocator locator) {
        return locator.getService( RepositorySystem.class );
    }

    public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system ) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, defaultLocalRepository() ) );
        session.setTransferListener(new LoggingTransferListener());
        session.setRepositoryListener(new LoggingRepositoryListener());

        return session;
    }

    public static LocalRepository defaultLocalRepository() {
        return new LocalRepository(new File(new File(System.getProperty("user.home"), ".m2"), "repository"));
    }

    public static Settings defaultMavenSettings() {
        try {
            File settingsXml = new File(new File(System.getProperty("user.home"), ".m2"), "settings.xml");
            if(! settingsXml.canRead()) {
                LOGGER.info("Could no read settings file.");
                new Settings();
            }
            SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
            SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
            request.setSystemProperties(System.getProperties());
            request.setUserSettingsFile(settingsXml);

            return settingsBuilder.build(request).getEffectiveSettings();
        } catch (SettingsBuildingException ex) {
            LOGGER.warn("Could not build settings from user settings.xml.", ex);
            throw new RuntimeException(ex);
        }
    }

    public static List<RemoteRepository> newRemoteRepositories() {
        return new ArrayList<>(Collections.singletonList( newCentralRepository() ) );
    }

    public static RemoteRepository newCentralRepository() {
        return createRemoteRepository("central", "default", "https://repo.maven.apache.org/maven2/");
    }

    /**
     * Creates a {@link RemoteRepository} instance from the elements of a maven artifact specification.
     *
     * @param id    some user defined ID for the repository
     * @param type  the repository type. typically "default".
     * @param url   the repository URL.
     *
     * @return the {@link RemoteRepository} specification.
     */
    public static RemoteRepository createRemoteRepository(String id, String type, String url) {
        return new RemoteRepository.Builder(id, type, url).build();
    }

    public static ModelResolver createModelResolver(ServiceLocator locator) {
        final RepositorySystemSession session = newRepositorySystemSession(newRepositorySystem(locator));
        ModelResolver modelResolver;
        try {
            Constructor<?> constr = Class.forName("org.apache.maven.repository.internal.DefaultModelResolver").getConstructors()[0];
            constr.setAccessible(true);
            modelResolver = (ModelResolver) constr.newInstance(session, null, null,
                    locator.getService(ArtifactResolver.class),
                    locator.getService(VersionRangeResolver.class),
                    locator.getService(RemoteRepositoryManager.class),
                    newRemoteRepositories());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        return modelResolver;
    }

    /**
     * Resolve the effective Maven model (pom) for a POM file.
     *
     * This resolves the POM hierarchy (parents and modules) and creates an
     * overall model.
     *
     * @param pom the POM file to resolve.
     * @return the effective model.
     */
    public static Model getEffectiveModel(ServiceLocator locator, File pom) {
        ModelBuildingRequest request = new DefaultModelBuildingRequest()
                .setProcessPlugins(true)
                .setPomFile(pom)
                .setModelResolver(Bootstrap.createModelResolver(locator))
                .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
                .setSystemProperties(System.getProperties());

        DefaultModelBuilder builder = new DefaultModelBuilderFactory().newInstance();
        try {
            return builder.build(request).getEffectiveModel();
        } catch(ModelBuildingException ex) {
            LOGGER.warn("Could not build maven model.", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Every version of maven has a set of default plugins which can be found in
     * <b>default-bindings.xml</b> in maven-core.
     * Rather than setup a whole Plexus container; we hardcode the list here for our particular version.
     */
    public static List<Plugin> defaultPlugins() {
        List<Plugin> plugins = new ArrayList<>();
        plugins.add( newPlugin( "maven-compiler-plugin", "3.1") );
        plugins.add( newPlugin( "maven-resources-plugin", "2.6" ) );
        plugins.add( newPlugin( "maven-surefire-plugin", "2.12.4" ) );
        plugins.add( newPlugin( "maven-jar-plugin", "2.4" ) );
        plugins.add( newPlugin( "maven-install-plugin", "2.4" ) );
        plugins.add( newPlugin( "maven-deploy-plugin", "2.7" ) );
        return plugins;
    }

    private static Plugin newPlugin( String artifactId, String version) {
        Plugin plugin = new Plugin();
        plugin.setGroupId( "org.apache.maven.plugins" );
        plugin.setArtifactId( artifactId );
        plugin.setVersion(version);
        return plugin;
    }


}
