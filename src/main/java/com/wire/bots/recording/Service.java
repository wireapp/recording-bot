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

import com.wire.bots.recording.DAO.ChannelsDAO;
import com.wire.bots.recording.DAO.EventsDAO;
import com.wire.bots.recording.model.Config;
import com.wire.bots.recording.utils.ImagesBundle;
import com.wire.bots.sdk.MessageHandlerBase;
import com.wire.bots.sdk.Server;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.util.concurrent.ExecutorService;

public class Service extends Server<Config> {
    public static Service instance;

    private MessageHandler messageHandler;

    public static void main(String[] args) throws Exception {
        instance = new Service();
        instance.run(args);
    }

    @Override
    public void initialize(Bootstrap<Config> bootstrap) {
        super.initialize(bootstrap);
        String workingDir = System.getProperty("user.dir");

        bootstrap.addBundle(new AssetsBundle("/scripts", "/scripts", "index.htm", "scripts"));
        bootstrap.addBundle(new ImagesBundle(workingDir + "/avatars", "/avatars", "avatars"));
        bootstrap.addBundle(new ImagesBundle(workingDir + "/html", "/channel", "channels"));
        bootstrap.addBundle(new ImagesBundle(workingDir + "/assets", "/assets", "assets"));

        Application<Config> application = bootstrap.getApplication();
        instance = (Service) application;
    }

    @Override
    protected MessageHandlerBase createHandler(Config config, Environment env) {
        final EventsDAO eventsDAO = jdbi.onDemand(EventsDAO.class);
        final ChannelsDAO channelsDAO = jdbi.onDemand(ChannelsDAO.class);

        messageHandler = new MessageHandler(eventsDAO, channelsDAO);
        return messageHandler;
    }

    protected void onRun(Config config, Environment env) {
        ExecutorService warmup = env.lifecycle().executorService("warmup").build();
        warmup.submit(() -> messageHandler.warmup(getRepo()));
    }
}
