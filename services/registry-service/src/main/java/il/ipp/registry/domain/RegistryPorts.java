package il.ipp.registry.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound ports (Hexagonal). Implemented in adapters.out.*. */
public final class RegistryPorts {
  private RegistryPorts() {}

  public interface PersonRepository {
    Optional<Person> findById(UUID id);

    Optional<Person> findByNationalId(String nationalId);

    Optional<Person> findByUsername(String username);

    /** Keyset pagination: rows with id &gt; after, ordered by id. */
    List<Person> search(String region, UUID employerId, UUID after, int limit);

    long count();

    void insertAll(List<Person> persons);
  }

  public interface EmployerRepository {
    Optional<Employer> findById(UUID id);

    Optional<Employer> findByUsername(String username);

    List<Employer> all();

    void insertAll(List<Employer> employers);
  }

  public interface PhotoStore {
    Optional<byte[]> get(String key);

    void put(String key, byte[] content, String contentType);
  }
}
