package il.ipp.permits.adapters.out.crypto;

import il.ipp.permits.domain.QrCodec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ed25519 key for signing permit QR codes. DEMO ONLY: generated on first start and stored in the
 * service's own schema so all instances share it. Production: HSM/KMS (see GAPS G-07).
 */
@Component
public class QrKeyStore implements il.ipp.permits.domain.PermitPorts.QrSigner {
  private final PrivateKey privateKey;
  private final PublicKey publicKey;
  private final String publicKeySpkiB64;

  public QrKeyStore(JdbcTemplate jdbc, TransactionTemplate tx) throws Exception {
    byte[][] pair = tx.execute(s -> {
      jdbc.queryForObject("select pg_advisory_xact_lock(42002)", Object.class);
      var rows = jdbc.query("select private_pkcs8, public_spki from qr_key where id = 1",
          (rs, i) -> new byte[][] {rs.getBytes(1), rs.getBytes(2)});
      if (!rows.isEmpty()) {
        return rows.get(0);
      }
      try {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[][] p = {kp.getPrivate().getEncoded(), kp.getPublic().getEncoded()};
        jdbc.update("insert into qr_key(id, private_pkcs8, public_spki, created_at) values (1, ?, ?, now())", p[0], p[1]);
        return p;
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
    this.privateKey = QrCodec.privateKey(pair[0]);
    this.publicKey = QrCodec.publicKey(pair[1]);
    this.publicKeySpkiB64 = Base64.getEncoder().encodeToString(pair[1]);
  }

  @Override
  public String sign(String payloadJson) {
    return QrCodec.sign(payloadJson, privateKey);
  }

  @Override
  public String verify(String qr) {
    return QrCodec.verify(qr, publicKey);
  }

  @Override
  public String publicKeySpkiBase64() {
    return publicKeySpkiB64;
  }
}
