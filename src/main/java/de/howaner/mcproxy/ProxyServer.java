package de.howaner.mcproxy;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProxyServer {
	private static final ProxyServer INSTANCE = new ProxyServer();
	@Getter private Logger logger;
	@Getter private ProxyConfig config;
	@Getter private NetworkManager network;

	private void init() {
		this.logger = LogManager.getRootLogger();
		this.logger.info("Start proxy server ...");
		this.logger.info("Read config ...");

		this.config = new ProxyConfig();
		try {
			if (!ProxyConfig.CONFIG_FILE.exists())
				this.config.createDefaultConfig();
			this.config.loadConfig();
		} catch (Exception ex) {
			this.logger.error("Exception while loading configuartion", ex);
			return;
		}

		try {
			this.network = new NetworkManager();
			this.network.createEventLoopGroup();
			this.network.startServer();

			// Wait until stop
			this.network.getEndpoint().channel().closeFuture().sync();
		} catch (Exception ex) {
			this.logger.error("Exception", ex);
		}
	}

	public static void main(String[] args) {
		ProxyServer.INSTANCE.init();
	}

	public static ProxyServer getServer() {
		return INSTANCE;
	}

}
