package il.ipp.registry.application;

import il.ipp.common.security.CurrentUser;
import il.ipp.common.web.DomainException;
import il.ipp.registry.domain.AvatarGenerator;
import il.ipp.registry.domain.Employer;
import il.ipp.registry.domain.Person;
import il.ipp.registry.domain.RegistryPorts.EmployerRepository;
import il.ipp.registry.domain.RegistryPorts.PersonRepository;
import il.ipp.registry.domain.RegistryPorts.PhotoStore;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Use cases + ABAC rules for persons and employers. */
@Service
public class RegistryService {
  private static final String[] STAFF = {"CLERK", "CROSSING_OFFICER", "ENFORCEMENT_OFFICER", "AUDITOR",
      "SYS_ADMIN", "POLICY_ADMIN"};
  private final PersonRepository persons;
  private final EmployerRepository employers;
  private final PhotoStore photos;

  public RegistryService(PersonRepository persons, EmployerRepository employers, PhotoStore photos) {
    this.persons = persons;
    this.employers = employers;
    this.photos = photos;
  }

  public Person get(UUID id) {
    Person p = persons.findById(id).orElseThrow(() -> DomainException.notFound("Person"));
    authorizeRead(p);
    return p;
  }

  public Person byNationalId(String nationalId) {
    Person p = persons.findByNationalId(nationalId).orElseThrow(() -> DomainException.notFound("Person"));
    authorizeRead(p);
    return p;
  }

  public Person me() {
    return persons.findByUsername(CurrentUser.get().username())
        .orElseThrow(() -> DomainException.notFound("Person profile for current user"));
  }

  public Employer myEmployer() {
    return employers.findByUsername(CurrentUser.get().username())
        .orElseThrow(() -> DomainException.notFound("Employer for current user"));
  }

  public Employer employer(UUID id) {
    CurrentUser u = CurrentUser.get();
    Employer e = employers.findById(id).orElseThrow(() -> DomainException.notFound("Employer"));
    if (u.has("EMPLOYER") && !u.hasAny(STAFF) && !u.username().equals(e.username())) {
      throw DomainException.forbidden("Employers may only view themselves");
    }
    return e;
  }

  public List<Employer> employers() {
    return employers.all();
  }

  /** ABAC: an EMPLOYER always gets only their own workers, whatever employerId was asked for. */
  public List<Person> search(String region, UUID employerId, UUID after, int limit) {
    CurrentUser u = CurrentUser.get();
    UUID effectiveEmployer = employerId;
    if (!u.hasAny(STAFF)) {
      if (!u.has("EMPLOYER")) {
        throw DomainException.forbidden("Not allowed to search persons");
      }
      effectiveEmployer = myEmployer().id();
    }
    return persons.search(region, effectiveEmployer, after, Math.min(Math.max(limit, 1), 200));
  }

  /** Photos are generated on first access (generic silhouette) and stored in S3 (SeaweedFS). */
  public byte[] photo(UUID personId) {
    Person p = get(personId);
    String key = "persons/" + p.id() + ".svg";
    return photos.get(key).orElseGet(() -> {
      byte[] svg = AvatarGenerator.svgFor(p.id());
      photos.put(key, svg, "image/svg+xml");
      return svg;
    });
  }

  private void authorizeRead(Person p) {
    CurrentUser u = CurrentUser.get();
    if (u.hasAny(STAFF)) {
      return;
    }
    if (p.username() != null && p.username().equals(u.username())) {
      return;
    }
    if (u.has("EMPLOYER") && p.employerId() != null && p.employerId().equals(myEmployer().id())) {
      return;
    }
    // Do not reveal existence to unauthorized callers (IDOR protection)
    throw DomainException.notFound("Person");
  }
}
