package dev.galaxyexplorer.ingestion.connector.image;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hosts com que a ingestao de imagens pode falar. Comparacao EXATA de host (sem
 * sufixo/curinga), so {@code https}, so porta padrao, sem credenciais na URL.
 * Fecha SSRF: nem uma URL vinda de uma resposta (redirect, AVM) sai daqui. Puro.
 */
public final class HostAllowlist {

    private final Set<String> hosts;

    public HostAllowlist(Set<String> hosts) {
        this.hosts = hosts.stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public boolean allows(URI uri) {
        if (uri == null || !uri.isAbsolute() || uri.isOpaque()) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null) {
            return false;
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            return false;
        }
        String host = uri.getHost();
        return host != null && hosts.contains(host.toLowerCase(Locale.ROOT));
    }
}
