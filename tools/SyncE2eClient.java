import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Small diagnostic client for exercising the real local sync listener through adb forward. */
public final class SyncE2eClient {
    private static final String END = "__CASA_SYNC_END__";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("host port pairingCode deviceId [encryptedOperationJson]");
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String token = args[2];
        String deviceId = args[3];
        byte[] key = sha256(token);
        String clientNonce = nonce(24);

        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            writer.println("CASA_HELLO:" + deviceId + ":" + clientNonce);
            String challenge = requireLine(reader);
            if (!challenge.startsWith("CASA_CHALLENGE:")) throw new IOException("Invalid challenge");
            String serverNonce = challenge.substring("CASA_CHALLENGE:".length());
            writer.println("CASA_RESPONSE:" + proof(key, clientNonce, serverNonce));
            String auth = requireLine(reader);
            System.out.println("auth=" + auth);
            if (!"CASA_AUTH_OK".equals(auth)) return;
            if (args.length > 4) {
                StringBuilder operation = new StringBuilder();
                if ("-".equals(args[4])) {
                    try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = input.readLine()) != null) operation.append(line);
                    }
                } else {
                    operation.append(args[4]);
                    for (int i = 5; i < args.length; i++) operation.append(' ').append(args[i]);
                }
                System.out.println("sending=" + operation);
                writer.println(encrypt(operation.toString(), key));
            }
            writer.println(END);
            int responses = 0;
            while (true) {
                String line = reader.readLine();
                if (line == null || END.equals(line)) break;
                responses++;
                System.out.println("response=" + decrypt(line, key));
            }
            System.out.println("encryptedResponses=" + responses);
        }
    }

    private static String encrypt(String value, byte[] key) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] output = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, output, 0, iv.length);
        System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(output);
    }

    private static String proof(byte[] key, String clientNonce, String serverNonce) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal((clientNonce + serverNonce).getBytes(StandardCharsets.UTF_8)));
    }

    private static String decrypt(String value, byte[] key) throws Exception {
        byte[] input = Base64.getDecoder().decode(value);
        byte[] iv = new byte[12];
        System.arraycopy(input, 0, iv, 0, iv.length);
        byte[] encrypted = new byte[input.length - iv.length];
        System.arraycopy(input, iv.length, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String nonce(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String requireLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) throw new EOFException("Connection closed during handshake");
        return line;
    }
}
