package de.howaner.mcproxy.connection;

import com.google.common.collect.Queues;
import de.howaner.mcproxy.ProxyConfig;
import de.howaner.mcproxy.ProxyServer;
import de.howaner.mcproxy.util.ConnectionState;
import de.howaner.mcproxy.util.HandshakeData;
import de.howaner.mcproxy.util.PacketUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Queue;
import lombok.Getter;

/**
 * Connection with a minecraft client.
 */
public class ClientConnection extends SimpleChannelInboundHandler<ByteBuf> {
	@Getter private Channel channel;
	@Getter private ConnectionState state = ConnectionState.DEFAULT;
	@Getter private HandshakeData handshake;
	@Getter private ServerConnection serverConnection;

	private String username;
	private Queue<byte[]> queuedPackets = Queues.newArrayDeque();

	public boolean isChannelOpen() {
		return (this.channel != null && this.channel.isOpen() && this.state != ConnectionState.DISCONNECTED);
	}

	public void closeChannel() {
		if (this.channel != null && this.channel.isOpen()) {
			this.channel.config().setAutoRead(false);
			this.channel.close();
		}

		this.state = ConnectionState.DISCONNECTED;
		if (this.queuedPackets != null) {
			this.queuedPackets.clear();
			this.queuedPackets = null;
		}

		// Only show close message on login sessions
		if (this.handshake != null && this.handshake.getNextState() == 2)
			ProxyServer.getServer().getLogger().info("[{}] Closed client connection", this.getName());

		if (this.serverConnection != null && this.serverConnection.isChannelOpen())
			this.serverConnection.closeChannel();
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		this.channel = ctx.channel();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		this.closeChannel();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		if (cause instanceof ClosedChannelException || !this.isChannelOpen()) return;

		ProxyServer.getServer().getLogger().error("Exception occurred on connection " + this.channel.remoteAddress().toString(), cause);
		this.closeChannel();
	}

	public String getName() {
		if (this.username != null)
			return this.channel.remoteAddress() + " | " + this.username;
		else
			return this.channel.remoteAddress().toString();
	}

	private void addToQueue(ByteBuf in) {
		if (this.queuedPackets == null)
			return;

		int readerIndex = in.readerIndex();
		byte[] bytes = new byte[in.readableBytes()];
		in.readBytes(bytes);

		this.queuedPackets.add(bytes);
		in.readerIndex(readerIndex);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
		int packetId;
		switch (state) {
			case DEFAULT:
				this.addToQueue(in);

				packetId = PacketUtils.readVarInt(in);
				if (packetId != 0)
					throw new IOException("Invalid packet id received: " + packetId + ", connecting state");

				this.handshake = new HandshakeData();
				this.handshake.read(in);

				this.state = ConnectionState.HANDSHAKE_RECEIVED;
				break;
			case HANDSHAKE_RECEIVED:
				this.addToQueue(in);

				packetId = PacketUtils.readVarInt(in);
				if (packetId != 0)
					throw new IOException("Invalid packet id received: " + packetId + ", handshake received state");

				if (this.handshake.getNextState() == 2)
					this.username = PacketUtils.readString(in);
				ProxyServer.getServer().getLogger().info("[{}] Client connection established: hostname={}, port={}", this.getName(), this.handshake.getHostname(), this.handshake.getPort());

				this.connectToServer();
				break;
			case CONNECTING:
				// Shouldn't happen because autoread is set to false, but who care ...
				this.addToQueue(in);
				break;
			case CONNECTED:
				in.retain();
				this.serverConnection.fastWrite(in);
				break;
		}
	}

	protected void onServerConnected() {
		if (!this.isChannelOpen()) {
			// Client disconnected
			this.serverConnection.closeChannel();
			return;
		}

		if (this.state != ConnectionState.CONNECTING)
			throw new RuntimeException("Invalid state: " + this.state);

		this.channel.pipeline().remove("splitter");
		if (this.queuedPackets != null) {
			for (byte[] packet : this.queuedPackets) {
				int length = packet.length;
				int size = PacketUtils.getVarIntLength(length) + length;

				ByteBuf buf = Unpooled.buffer(size, size);
				PacketUtils.writeVarInt(buf, length);
				buf.writeBytes(packet);

				this.serverConnection.fastWrite(buf);
			}
			this.queuedPackets.clear();
			this.queuedPackets = null;
		}

		this.state = ConnectionState.CONNECTED;
		this.channel.config().setAutoRead(true);
		ProxyServer.getServer().getLogger().debug("[{}] Server connection established.", this.getName());
	}

	protected void fastWrite(ByteBuf buf) {
		this.channel.writeAndFlush(buf);
	}

	public void write(Object obj, ChannelFutureListener ... futureListeners) {
		if (!this.isChannelOpen())
			return;

		if (this.channel.eventLoop().inEventLoop()) {
			this.channel.writeAndFlush(obj).addListeners(futureListeners).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		} else {
			this.channel.eventLoop().execute(() -> this.channel.writeAndFlush(obj).addListeners(futureListeners).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
		}
	}

	public void connectToServer() {
		ProxyConfig.ServerData serverData = ProxyServer.getServer().getConfig().getServers().get(this.handshake.getHostname().toLowerCase());
		if (serverData == null)
			serverData = ProxyServer.getServer().getConfig().getServers().get("default");

		if (serverData == null) {
			ProxyServer.getServer().getLogger().info("[{}] No server for hostname {}, disconnect.", this.getName(), this.handshake.getHostname());
			this.closeChannel();
			return;
		}

		this.connectToServer(serverData);
	}

	public void connectToServer(ProxyConfig.ServerData server) {
		this.state = ConnectionState.CONNECTING;
		this.channel.config().setAutoRead(false);
		this.serverConnection = new ServerConnection(this);

		ProxyServer.getServer().getLogger().info("[{}] Connect to server {}", this.getName(), server.getAddress().getHostName());

		Class<? extends SocketChannel> channelClass = Epoll.isAvailable() ? EpollSocketChannel.class : NioSocketChannel.class;
		new Bootstrap().channel(channelClass)
				.option(ChannelOption.TCP_NODELAY, true)
				.option(ChannelOption.IP_TOS, 24)
				.handler(new ServerConnection.ServerConnectionInitializer(this.serverConnection))
				.group(this.channel.eventLoop())
				.remoteAddress(server.getAddress(), server.getPort())
				.connect()
				.addListener((Future<? super Void> future) -> {
					if (!future.isSuccess()) {
						ProxyServer.getServer().getLogger().warn("Exception while connecting to server " + server.getAddress().getHostName(), future.cause());
						this.closeChannel();
					}
				});
	}

}
