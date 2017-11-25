package de.howaner.mcproxy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.howaner.mcproxy.connection.ClientConnection;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.net.InetAddress;
import lombok.Getter;
import tv.rewinside.replayserver.netty.MCLengthDeserializer;

public class NetworkManager {
	@Getter private ChannelFuture endpoint;
	@Getter private EventLoopGroup eventLoops;

	public void createEventLoopGroup() {
		if (this.eventLoops != null)
			throw new RuntimeException("Event loops are already created.");

		if (Epoll.isAvailable()) {
			this.eventLoops = new EpollEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Epoll Server IO #%d").setDaemon(true).build());
			ProxyServer.getServer().getLogger().info("Using epoll channel type");
		} else {
			this.eventLoops = new NioEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Server IO #%d").setDaemon(true).build());
			ProxyServer.getServer().getLogger().info("Using default channel type");
		}
	}

	public void startServer() throws Exception {
		InetAddress address = InetAddress.getByName(ProxyServer.getServer().getConfig().getBindingIp());

		ChannelInitializer initializer = new ChannelInitializer() {
			@Override
			protected void initChannel(Channel channel) throws Exception {
				channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
				channel.pipeline().addLast("splitter", new MCLengthDeserializer());
				channel.pipeline().addLast("packet_handler", new ClientConnection());
			}
		};

		Class<? extends ServerSocketChannel> channelClass = Epoll.isAvailable() ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
		this.endpoint = new ServerBootstrap().channel(channelClass)
				.childOption(ChannelOption.IP_TOS, 24)
				.childOption(ChannelOption.TCP_NODELAY, true)
				.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
				.option(ChannelOption.SO_REUSEADDR, true)
				.childHandler(initializer)
				.group(this.eventLoops)
				.localAddress(address, ProxyServer.getServer().getConfig().getBindingPort())
				.bind().syncUninterruptibly();

		ProxyServer.getServer().getLogger().info("Started tcp server at {}:{}", address.getHostAddress(), ProxyServer.getServer().getConfig().getBindingPort());
	}

}
