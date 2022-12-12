// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//

package com.wire.bots.recording;

import com.wire.bots.recording.model.Config;
import com.wire.bots.recording.utils.ImagesBundle;
import com.wire.lithium.Server;
import com.wire.xenon.MessageHandlerBase;
import com.wire.xenon.factories.StorageFactory;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.MetricsServlet;

import java.util.concurrent.ExecutorService;

public class Service extends Server<Config> {
    public static Service instance;

    public static void main(String[] args) throws Exception {
        instance = new Service();
        instance.run(args);
    }

    @Override
    public void initialize(Bootstrap<Config> bootstrap) {
        String workingDir = System.getProperty("user.dir");

        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false)));
        bootstrap.addBundle(new AssetsBundle("/scripts", "/scripts", "index.htm", "scripts"));
        bootstrap.addBundle(new ImagesBundle(workingDir + "/avatars", "/avatars", "avatars"));
        bootstrap.addBundle(new ImagesBundle(workingDir + "/html", "/channel", "channels"));
        bootstrap.addBundle(new ImagesBundle(workingDir + "/assets", "/assets", "assets", "application/octet-stream"));

        Application<Config> application = bootstrap.getApplication();
        instance = (Service) application;
    }

    @Override
    protected MessageHandlerBase createHandler(Config config, Environment env) {
        StorageFactory storageFactory = getStorageFactory();
        CommandManager commandManager = new CommandManager(getJdbi(), storageFactory);
        return new MessageHandler(getJdbi(), storageFactory, commandManager);
    }

    protected void onRun(Config config, Environment env) {
        CollectorRegistry.defaultRegistry.register(new DropwizardExports(env.metrics()));
        env.getApplicationContext().addServlet(MetricsServlet.class, "/metrics");

        Startup startup = new Startup(getJdbi());
        ExecutorService warmup = env.lifecycle().executorService("warmup").build();
        warmup.submit(() -> startup.warmup(getRepo()));
    }
}
