package org.tron.p2p.connection.socket;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;

import static org.tron.p2p.utils.NetUtil.getEventLoopGroup;
import static org.tron.p2p.utils.NetUtil.setChannel;

@Slf4j(topic = "net")
public class PeerServer {

  private ChannelFuture channelFuture;
  private boolean listening;

  public void init() {
    int port = Parameter.p2pConfig.getPort();
    if (port > 0) {
      new Thread(() -> start(port), "PeerServer").start();
    }
  }

  public void close() {
    if (listening && channelFuture != null && channelFuture.channel().isOpen()) {
      try {
        log.info("Closing TCP server...");
        channelFuture.channel().close().sync();
      } catch (Exception e) {
        log.warn("Closing TCP server failed.", e);
      }
    }
  }

  public void start(int port) {
    EventLoopGroup bossGroup = getEventLoopGroup(1, "peerBoss");
    //if threads = 0, it is number of core * 2
    EventLoopGroup workerGroup = getEventLoopGroup(Parameter.TCP_NETTY_WORK_THREAD_NUM, "peerWorker-%d");
    P2pChannelInitializer p2pChannelInitializer = new P2pChannelInitializer("", false, true);
    try {
      ServerBootstrap b = new ServerBootstrap();

      b.group(bossGroup, workerGroup);
      setChannel(b);

      b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
      b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Parameter.NODE_CONNECTION_TIMEOUT);
      b.childOption(ChannelOption.TCP_NODELAY, true);

      b.handler(new LoggingHandler());
      b.childHandler(p2pChannelInitializer);

      // Start the client.
      log.info("TCP listener started, bind port {}", port);

      channelFuture = b.bind(port).sync();

      listening = true;

      // Wait until the connection is closed.
      channelFuture.channel().closeFuture().sync();

      log.info("TCP listener closed");

    } catch (Exception e) {
      log.error("Start TCP server failed", e);
    } finally {
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
      listening = false;
    }
  }

}
