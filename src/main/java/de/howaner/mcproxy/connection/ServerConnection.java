package de.howaner.mcproxy.connection;

import de.howaner.mcproxy.ProxyServer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.nio.channels.ClosedChannelException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ServerConnection extends SimpleChannelInboundHandler<ByteBuf> {
	@Getter private final ClientConnection clientConnection;
	@Getter private Channel channel;
	private boolean disconnected = false;

	public boolean isChannelOpen() {
		return (this.channel != null && this.channel.isOpen() && !this.disconnected);
	}

	public void closeChannel() {
		if (this.channel != null && this.channel.isOpen()) {
			this.channel.config().setAutoRead(false);
			this.channel.close();
		}

		this.disconnected = true;

		if (this.clientConnection.isChannelOpen())
			this.clientConnection.closeChannel();
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		this.channel = ctx.channel();

		this.clientConnection.onServerConnected();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		this.closeChannel();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		if (cause instanceof ClosedChannelException || !this.isChannelOpen()) return;

		ProxyServer.getServer().getLogger().error("Exception occurred on server connection " + this.channel.remoteAddress().toString(), cause);
		this.closeChannel();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
		msg.retain();
		this.clientConnection.fastWrite(msg);
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

	@RequiredArgsConstructor
	public static class ServerConnectionInitializer extends ChannelInitializer {
		private final ServerConnection serverConnection;

		@Override
		protected void initChannel(Channel channel) throws Exception {
			channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
			channel.pipeline().addLast("handler", this.serverConnection);
		}
	}

}
