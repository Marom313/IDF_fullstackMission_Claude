package il.ipp.registry.adapters.out.s3;

import il.ipp.registry.domain.RegistryPorts.PhotoStore;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * S3 adapter against any S3-compatible endpoint (SeaweedFS in dev, organisational S3 in prod).
 * pathStyleAccess: SeaweedFS/most on-prem S3 don't support virtual-host bucket DNS.
 */
@Component
class S3PhotoStore implements PhotoStore {
  private static final Logger log = LoggerFactory.getLogger(S3PhotoStore.class);
  private final S3Client s3;
  private final String bucket;

  S3PhotoStore(@Value("${ipp.s3.endpoint}") String endpoint, @Value("${ipp.s3.access-key}") String ak,
               @Value("${ipp.s3.secret-key}") String sk, @Value("${ipp.s3.bucket:photos}") String bucket) {
    this.bucket = bucket;
    this.s3 = S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ak, sk)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .httpClient(UrlConnectionHttpClient.create())
        .build();
  }

  @PostConstruct
  void ensureBucket() {
    try {
      s3.headBucket(b -> b.bucket(bucket));
    } catch (Exception notFound) {
      try {
        s3.createBucket(b -> b.bucket(bucket));
        log.info("created bucket {}", bucket);
      } catch (Exception e) {
        log.warn("could not create bucket {}: {}", bucket, e.toString());
      }
    }
  }

  @Override
  public Optional<byte[]> get(String key) {
    try {
      return Optional.of(s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray());
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }

  @Override
  public void put(String key, byte[] content, String contentType) {
    s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(content));
  }
}
