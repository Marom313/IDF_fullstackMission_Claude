package il.ipp.ingest.verifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Command line entry point, run by NiFi's ExecuteStreamCommand: the batch zip arrives on STDIN.
 *
 * <pre>
 * java -jar ingest-verifier.jar --key /opt/ipp/keys/ingest.pub.pem --receipts /ingest/.receipts --name ${filename}
 * </pre>
 *
 * STDOUT: one JSON object per line, each with {@code _topic} and {@code _key} for PublishKafka:
 * every accepted crossing ({@code crossing.event.recorded.v1}) and one audit record ({@code audit.ingest.v1}).
 * STDERR: a one-line human reason (NiFi keeps it in {@code execution.error}).
 * Exit code: 0 accepted · 3 quarantined · 2 usage/configuration error.
 * {@code --format console} prints {@code topic<TAB>key<TAB>json} instead (for kafka-console-producer without NiFi).
 */
public final class IngestVerifier {

    static final String CROSSING_TOPIC = "crossing.event.recorded.v1";
    static final String AUDIT_TOPIC = "audit.ingest.v1";
    public static final int ACCEPTED = 0, USAGE = 2, QUARANTINED = 3;

    private IngestVerifier() { }

    public static void main(String[] args) {
        System.exit(run(args, System.in, System.out, System.err, Clock.systemUTC()));
    }

    static int run(String[] args, InputStream stdin, PrintStream out, PrintStream err, Clock clock) {
        Map<String, String> opts = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (!args[i].startsWith("--")) {
                err.println("usage: --key <pub.pem> [--receipts <file>] [--name <batch>] [--format nifi|console]");
                return USAGE;
            }
            opts.put(args[i].substring(2), args[i + 1]);
        }
        String keyPath = opts.get("key");
        if (keyPath == null) {
            err.println("usage: --key <pub.pem> [--receipts <file>] [--name <batch>] [--format nifi|console]");
            return USAGE;
        }
        String name = opts.getOrDefault("name", "batch");
        boolean console = "console".equals(opts.get("format"));
        BatchVerifier verifier;
        try {
            verifier = new BatchVerifier(BatchVerifier.readPublicKey(Files.readString(Path.of(keyPath))),
                    opts.containsKey("receipts") ? Path.of(opts.get("receipts")) : null, clock, BatchVerifier.Limits.defaults());
        } catch (Exception e) {
            err.println("CONFIG: cannot load public key " + keyPath + ": " + e.getMessage());
            return USAGE;
        }

        byte[] zip;
        try {
            zip = readUpTo(stdin, BatchVerifier.Limits.defaults().maxZipBytes() + 1);
        } catch (IOException e) {
            err.println("UNREADABLE: " + e.getMessage());
            return USAGE;
        }

        BatchVerifier.Result r = verifier.verify(zip, name);
        for (Map<String, Object> c : r.crossings()) {
            emit(out, console, CROSSING_TOPIC, (String) c.get("personId"), c);
        }
        emit(out, console, AUDIT_TOPIC, r.sha256(), audit(r, name, clock));
        out.flush();
        err.println(String.join(" | ", r.reasons()));
        return r.accepted() ? ACCEPTED : QUARANTINED;
    }

    /** Envelope shape that audit-service on main reads (eventId, occurredAt, correlationId, payload.*). */
    static Map<String, Object> audit(BatchVerifier.Result r, String name, Clock clock) {
        String decision = r.accepted() ? "ACCEPTED" : "QUARANTINED";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "nifi-ingest");
        payload.put("action", r.accepted() ? "INGEST_ACCEPTED" : "INGEST_QUARANTINED");
        payload.put("entityType", "BATCH");
        payload.put("entityId", name);
        payload.put("actor", "nifi");
        payload.put("decision", decision);
        payload.put("reasons", r.reasons());
        payload.put("rows", r.totalRows());
        payload.put("rejectedRows", r.rejectedRows());
        payload.put("sha256", r.sha256());
        Map<String, Object> env = new LinkedHashMap<>();
        // same batch + same verdict → same id: a NiFi retry is idempotent in audit-service (Mongo _id)
        env.put("eventId", UUID.nameUUIDFromBytes((r.sha256() + ":" + decision + ":" + name).getBytes(StandardCharsets.UTF_8)).toString());
        env.put("occurredAt", clock.instant().toString());
        env.put("correlationId", name);
        env.put("payload", payload);
        return env;
    }

    private static void emit(PrintStream out, boolean console, String topic, String key, Map<String, Object> body) {
        if (console) {
            out.println(topic + "\t" + key + "\t" + MiniJson.write(body));
            return;
        }
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("_topic", topic);
        line.put("_key", key);
        line.putAll(body);
        out.println(MiniJson.write(line));
    }

    private static byte[] readUpTo(InputStream in, long max) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) {
                bos.write(buf, 0, n);
                break;        // the verifier rejects it on size; no need to read the rest
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
