package de.howaner.mcproxy;

import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.yaml.snakeyaml.Yaml;

@Getter
@ToString
public class ProxyConfig {
	public static final File CONFIG_FILE = new File("config.yml");

	private String bindingIp = "127.0.0.1";
	private int bindingPort = 25565;
	private Map<String, ServerData> servers;

	public void loadConfig() throws IOException {
		Yaml yaml = new Yaml();
		Map<String, Object> values = yaml.load(new FileInputStream(CONFIG_FILE));

		Map<String, Object> bindingMap = (Map<String, Object>) values.get("binding");
		this.bindingIp = (String) bindingMap.get("ip");
		this.bindingPort = (int) bindingMap.get("port");

		this.servers = new HashMap<>();
		Map<String, String> serversMap = (Map<String, String>) values.get("servers");
		for (Map.Entry<String, String> e : serversMap.entrySet()) {
			this.servers.put(e.getKey().toLowerCase(), new ServerData(e.getValue()));
		}
	}

	public void createDefaultConfig() throws IOException {
		try (InputStream input = ProxyServer.class.getResourceAsStream("/config.yml")) {
			try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
				ByteStreams.copy(input, output);
				output.flush();
			}
		}
	}

	@AllArgsConstructor
	@Getter
	public static class ServerData {
		private final InetAddress address;
		private final int port;

		public ServerData(String str) throws UnknownHostException {
			int splitter = str.indexOf(':');
			if (splitter == -1) {
				this.address = InetAddress.getByName(str);
				this.port = 25565;
			} else {
				this.address = InetAddress.getByName(str.substring(0, splitter));
				this.port = Integer.parseInt(str.substring(splitter + 1));
			}
		}
	}

}
