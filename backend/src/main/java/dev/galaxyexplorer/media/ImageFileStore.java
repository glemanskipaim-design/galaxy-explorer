package dev.galaxyexplorer.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Arquivos de imagem enderecados por conteudo: {@code <raiz>/ab/<sha256>.jpg}.
 * Conteudo identico = mesmo arquivo (nunca regravado). Gravacao atomica
 * (temporario no mesmo diretorio + move): um leitor nunca ve arquivo pela
 * metade. Nao conhece objeto celeste nem fonte.
 */
public class ImageFileStore {

    /** Unica forma de chave aceita. A chave vem do banco, nunca da URL — ainda assim, validada. */
    static final Pattern STORAGE_KEY = Pattern.compile("^[0-9a-f]{2}/[0-9a-f]{64}[.]jpg$");

    private final Path root;

    public ImageFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public StoredFile store(byte[] bytes) {
        String sha = sha256Hex(bytes);
        String key = sha.substring(0, 2) + "/" + sha + ".jpg";
        Path target = root.resolve(key);
        try {
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Path tmp = Files.createTempFile(target.getParent(), sha.substring(0, 8), ".tmp");
                try {
                    Files.write(tmp, bytes);
                    move(tmp, target);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar " + key, e);
        }
        return new StoredFile(key, sha, bytes.length);
    }

    private static void move(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
            // outro gravador chegou primeiro com o MESMO conteudo (mesmo sha): nada a fazer
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Caminho do arquivo — so se a chave tem o formato esperado, fica dentro da raiz e o arquivo existe. */
    public Optional<Path> resolve(String storageKey) {
        if (storageKey == null || !STORAGE_KEY.matcher(storageKey).matches()) {
            return Optional.empty();
        }
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }
}
