package com.artandmagic.agony.data.original;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Fetches the three Agony disks when none are at hand.
 *
 * <p>The port ships no game data: everything it shows and plays is decoded
 * from the original disk images at start up.  When no image is found, the
 * hard disk release of the game is downloaded from the Amiga Museum, one zip
 * archive per disk, and the {@code .adf} inside each is kept in the port's
 * folder in the user's home.  Each image is checked against the SHA-256 of the
 * known good dump before it is kept, so a broken or different download is
 * never mistaken for the game.
 */
public final class DiskDownloader {

    /** One disk: where its archive is and what the image inside must hash to. */
    private record Disk(String url, String file, String sha256) {
    }

    private static final String BASE =
            "https://amigamuseum.emu-france.info/Fichiers/ADF/A/";

    private static final Disk[] DISKS = {
        new Disk(BASE + "Agony%20(1992)(Psygnosis)(Disk%201%20of%203)%5bHD%5d.zip",
                "Agony (1992)(Psygnosis)(Disk 1 of 3)[HD].adf",
                "efb187763e832fa07820952f729df5e1cc684f3f1a5eb81d516890d5756e8246"),
        new Disk(BASE + "Agony%20(1992)(Psygnosis)(Disk%202%20of%203)%5bHD%5d.zip",
                "Agony (1992)(Psygnosis)(Disk 2 of 3)[HD].adf",
                "c3744ec318f39d3a82ddf666c949cf5974dc9d142e4ac27c77479d1d2c1608a1"),
        new Disk(BASE + "Agony%20(1992)(Psygnosis)(Disk%203%20of%203)%5bHD%5d.zip",
                "Agony (1992)(Psygnosis)(Disk 3 of 3)[HD].adf",
                "1c06030bc405b6bbb39303e7ccdde427a86d608ea9a09439696b18b56e9be5a9"),
    };

    /** Size of a double density disk image. */
    private static final int ADF_SIZE = 901_120;

    private DiskDownloader() {
    }

    /** The folder the port keeps downloaded disks in. */
    public static Path defaultFolder() {
        return Path.of(System.getProperty("user.home"), ".agony-java", "adf");
    }

    /** True when a folder holds at least one disk image. */
    public static boolean hasDisks(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return false;
        }
        try (var list = Files.list(folder)) {
            return list.anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".adf"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Downloads the disks that are missing from a folder.
     *
     * @return true when the three images are there afterwards
     */
    public static boolean download(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            System.out.println("Cannot create " + folder + ": " + e.getMessage());
            return false;
        }
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        boolean all = true;
        for (Disk disk : DISKS) {
            Path target = folder.resolve(disk.file());
            if (Files.isRegularFile(target) && sha256(target).equals(disk.sha256())) {
                continue;
            }
            System.out.println("Downloading " + disk.file() + " ...");
            try {
                byte[] adf = fetch(http, disk);
                String hash = sha256(adf);
                if (!hash.equals(disk.sha256())) {
                    System.out.println("  rejected: the image does not match the known dump ("
                            + hash + ")");
                    all = false;
                    continue;
                }
                Path part = folder.resolve(disk.file() + ".part");
                Files.write(part, adf);
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                System.out.println("  ok");
            } catch (IOException e) {
                System.out.println("  failed: " + e.getMessage());
                all = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return all;
    }

    /** Downloads one archive and pulls the disk image out of it. */
    private static byte[] fetch(HttpClient http, Disk disk) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(disk.url()))
                .header("User-Agent", "agony-java")
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode());
        }
        try (ZipInputStream zip = new ZipInputStream(response.body())) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.isDirectory() || !e.getName().toLowerCase().endsWith(".adf")) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream(ADF_SIZE);
                byte[] buffer = new byte[64 * 1024];
                for (int n = zip.read(buffer); n > 0; n = zip.read(buffer)) {
                    out.write(buffer, 0, n);
                    if (out.size() > 2 * ADF_SIZE) {
                        throw new IOException("disk image too large");
                    }
                }
                return out.toByteArray();
            }
        }
        throw new IOException("no disk image in the archive");
    }

    private static String sha256(Path file) {
        try {
            return sha256(Files.readAllBytes(file));
        } catch (IOException e) {
            return "";
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
