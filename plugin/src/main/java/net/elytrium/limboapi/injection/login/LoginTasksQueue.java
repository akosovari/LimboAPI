/*
 * Copyright (C) 2021 - 2022 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file contains some parts of Velocity, licensed under the AGPLv3 License (AGPLv3).
 *
 * Copyright (C) 2018 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.elytrium.limboapi.injection.login;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.client.InitialConnectSessionHandler;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftDecoder;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import io.netty.channel.ChannelPipeline;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import net.elytrium.java.commons.reflection.ReflectionException;
import net.elytrium.limboapi.LimboAPI;
import net.elytrium.limboapi.api.event.SafeGameProfileRequestEvent;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

public class LoginTasksQueue {

  private static final Constructor<InitialConnectSessionHandler> initialCtor;
  private static final Field loginConnectionField;
  private static final Field defaultPermissions;
  private static final Field association;
  private static final Field state;
  private static final Field profile;
  private static final Method setPermissionFunction;
  private static final Method connectToInitialServer;

  private final LimboAPI plugin;
  private final Object handler;
  private final VelocityServer server;
  private final ConnectedPlayer player;
  private final InboundConnection inbound;
  private final Queue<Runnable> queue;

  public LoginTasksQueue(LimboAPI plugin, Object handler, VelocityServer server, ConnectedPlayer player,
      InboundConnection inbound, Queue<Runnable> queue) {
    this.plugin = plugin;
    this.handler = handler;
    this.server = server;
    this.player = player;
    this.inbound = inbound;
    this.queue = queue;
  }

  static {
    try {
      initialCtor = InitialConnectSessionHandler.class.getDeclaredConstructor(ConnectedPlayer.class);
      initialCtor.setAccessible(true);

      loginConnectionField = LoginListener.LOGIN_CLASS.getDeclaredField("mcConnection");
      loginConnectionField.setAccessible(true);

      defaultPermissions = ConnectedPlayer.class.getDeclaredField("DEFAULT_PERMISSIONS");
      defaultPermissions.setAccessible(true);

      association = MinecraftConnection.class.getDeclaredField("association");
      association.setAccessible(true);

      state = MinecraftConnection.class.getDeclaredField("state");
      state.setAccessible(true);

      profile = ConnectedPlayer.class.getDeclaredField("profile");
      profile.setAccessible(true);

      setPermissionFunction = ConnectedPlayer.class.getDeclaredMethod("setPermissionFunction", PermissionFunction.class);
      setPermissionFunction.setAccessible(true);

      connectToInitialServer = LoginListener.LOGIN_CLASS.getDeclaredMethod("connectToInitialServer", ConnectedPlayer.class);
      connectToInitialServer.setAccessible(true);
    } catch (NoSuchFieldException | NoSuchMethodException e) {
      throw new ReflectionException(e);
    }
  }

  public void next() {
    if (this.player.getConnection().isClosed()) {
      return;
    }

    if (this.queue.size() == 0) {
      this.player.getConnection().eventLoop().execute(this::finish);
    } else {
      this.player.getConnection().eventLoop().execute(Objects.requireNonNull(this.queue.poll()));
    }
  }

  @SuppressWarnings("deprecation")
  private void finish() {
    this.plugin.removeLoginQueue(this.player);
    MinecraftConnection connection = this.player.getConnection();
    Logger logger = LimboAPI.getLogger();

    this.server.getEventManager()
        .fire(new GameProfileRequestEvent(this.inbound, this.player.getGameProfile(), this.player.isOnlineMode()))
        .thenAcceptAsync(gameProfile -> this.server.getEventManager()
            .fire(new SafeGameProfileRequestEvent(gameProfile.getGameProfile(), gameProfile.isOnlineMode()))
            .thenAcceptAsync(safeGameProfile -> {
              try {
                profile.set(this.player, safeGameProfile.getGameProfile());
                // From Velocity.
                this.server.getEventManager()
                    .fire(new PermissionsSetupEvent(this.player, (PermissionProvider) defaultPermissions.get(null)))
                    .thenAcceptAsync(event -> {
                      if (!connection.isClosed()) {
                        // Wait for permissions to load, then set the players' permission function.
                        PermissionFunction function = event.createFunction(this.player);
                        if (function == null) {
                          logger.error(
                              "A plugin permission provider {} provided an invalid permission function"
                                  + " for player {}. This is a bug in the plugin, not in Velocity. Falling"
                                  + " back to the default permission function.",
                              event.getProvider().getClass().getName(),
                              this.player.getUsername()
                          );
                        } else {
                          try {
                            setPermissionFunction.invoke(this.player, function);
                          } catch (IllegalAccessException | InvocationTargetException ex) {
                            logger.error("Exception while completing injection to {}", this.player, ex);
                          }
                        }
                        try {
                          this.initialize(connection);
                        } catch (IllegalAccessException e) {
                          e.printStackTrace();
                        }
                      }
                    });
              } catch (IllegalAccessException e) {
                logger.error("Exception while completing injection to {}", this.player, e);
              }
            }, connection.eventLoop()), connection.eventLoop());
  }

  // From Velocity.
  private void initialize(MinecraftConnection connection) throws IllegalAccessException {
    association.set(connection, this.player);

    state.set(connection, StateRegistry.PLAY);
    ChannelPipeline pipeline = connection.getChannel().pipeline();
    pipeline.get(MinecraftEncoder.class).setState(StateRegistry.PLAY);
    pipeline.get(MinecraftDecoder.class).setState(StateRegistry.PLAY);

    Logger logger = LimboAPI.getLogger();
    this.server.getEventManager().fire(new LoginEvent(this.player))
        .thenAcceptAsync(event -> {
          if (connection.isClosed()) {
            // The player was disconnected.
            this.server.getEventManager().fireAndForget(new DisconnectEvent(this.player, DisconnectEvent.LoginStatus.CANCELLED_BY_USER_BEFORE_COMPLETE));
            return;
          }

          Optional<Component> reason = event.getResult().getReasonComponent();
          if (reason.isPresent()) {
            this.player.disconnect0(reason.get(), true);
          } else {
            if (!this.server.registerConnection(this.player)) {
              this.player.disconnect0(Component.translatable("velocity.error.already-connected-proxy"), true);
              return;
            }

            try {
              connection.setSessionHandler(initialCtor.newInstance(this.player));
              this.server.getEventManager()
                  .fire(new PostLoginEvent(this.player))
                  .thenAccept((ignored) -> {
                    try {
                      loginConnectionField.set(this.handler, connection);
                      connectToInitialServer.invoke(this.handler, this.player);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                      logger.error("Exception while connecting {} to initial server", this.player, e);
                    }
                  });
            } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
              e.printStackTrace();
            }
          }
        }, connection.eventLoop())
        .exceptionally(t -> {
          logger.error("Exception while completing login initialisation phase for {}", this.player, t);
          return null;
        });
  }
}
