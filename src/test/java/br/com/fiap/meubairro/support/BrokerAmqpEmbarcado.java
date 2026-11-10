package br.com.fiap.meubairro.support;

import org.apache.qpid.server.SystemLauncher;
import org.apache.qpid.server.model.SystemConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class BrokerAmqpEmbarcado {

    private static BrokerAmqpEmbarcado instancia;

    private final SystemLauncher launcher = new SystemLauncher();
    private final int porta;

    private BrokerAmqpEmbarcado() throws Exception {
        this.porta = buscarPortaLivre();
        Path pastaTrabalho = Files.createTempDirectory("qpid-meubairro");
        URL configuracao = BrokerAmqpEmbarcado.class.getClassLoader().getResource("qpid-config.json");

        Map<String, Object> atributos = new HashMap<>();
        atributos.put(SystemConfig.TYPE, "Memory");
        atributos.put(SystemConfig.INITIAL_CONFIGURATION_LOCATION, configuracao.toExternalForm());
        atributos.put(SystemConfig.STARTUP_LOGGED_TO_SYSTEM_OUT, false);
        atributos.put(SystemConfig.CONTEXT, Map.of(
                "qpid.amqp_port", String.valueOf(porta),
                "qpid.work_dir", pastaTrabalho.toString()));
        launcher.startup(atributos);
        Runtime.getRuntime().addShutdownHook(new Thread(launcher::shutdown));
    }

    public static synchronized BrokerAmqpEmbarcado iniciar() {
        if (instancia == null) {
            try {
                instancia = new BrokerAmqpEmbarcado();
            } catch (Exception e) {
                throw new IllegalStateException("Não foi possível iniciar o broker AMQP embarcado", e);
            }
        }
        return instancia;
    }

    public int getPorta() {
        return porta;
    }

    private static int buscarPortaLivre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
