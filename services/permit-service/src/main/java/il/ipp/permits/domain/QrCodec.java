package il.ipp.permits.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * QR content = base64url(payloadJson) + "." + base64url(Ed25519 signature over payloadJson).
 * Ed25519 is built into the JDK (15+) and into modern browsers' WebCrypto, so the crossing terminal
 * can verify the signature OFFLINE with only the public key.
 */
public final class QrCodec {
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder D64 = Base64.getUrlDecoder();

  private QrCodec() {}

  public static String sign(String payloadJson, PrivateKey key) {
    try {
      Signature s = Signature.getInstance("Ed25519");
      s.initSign(key);
      byte[] data = payloadJson.getBytes(StandardCharsets.UTF_8);
      s.update(data);
      return B64.encodeToString(data) + "." + B64.encodeToString(s.sign());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** @return the payload JSON if the signature is valid, otherwise null. */
  public static String verify(String qr, PublicKey key) {
    try {
      int dot = qr.indexOf('.');
      if (dot < 1) {
        return null;
      }
      byte[] data = D64.decode(qr.substring(0, dot));
      byte[] sig = D64.decode(qr.substring(dot + 1));
      Signature s = Signature.getInstance("Ed25519");
      s.initVerify(key);
      s.update(data);
      return s.verify(sig) ? new String(data, StandardCharsets.UTF_8) : null;
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      return null;
    }
  }

  public static PrivateKey privateKey(byte[] pkcs8) throws GeneralSecurityException {
    return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
  }

  public static PublicKey publicKey(byte[] spki) throws GeneralSecurityException {
    return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
  }
}
