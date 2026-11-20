package il.ipp.registry.adapters.in.web;

import il.ipp.registry.application.RegistryService;
import il.ipp.registry.domain.Employer;
import il.ipp.registry.domain.Person;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registry")
class RegistryController {
  private final RegistryService service;

  RegistryController(RegistryService service) {
    this.service = service;
  }

  record PersonDto(UUID id, String nationalId, String firstName, String lastName, String fullName,
                   LocalDate birthDate, int age, String magneticCard, LocalDate magneticCardValidTo,
                   String securityStatus, String region, UUID employerId, String photoUrl) {
    static PersonDto of(Person p) {
      return new PersonDto(p.id(), p.nationalId(), p.firstName(), p.lastName(), p.fullName(), p.birthDate(),
          p.ageOn(LocalDate.now()), p.magneticCard(), p.magneticCardValidTo(), p.securityStatus().name(), p.region(),
          p.employerId(), "/api/registry/persons/" + p.id() + "/photo");
    }
  }

  record PersonPage(List<PersonDto> items, UUID nextCursor) {}

  @GetMapping("/persons/{id}")
  PersonDto get(@PathVariable UUID id) {
    return PersonDto.of(service.get(id));
  }

  @GetMapping("/persons/by-national-id/{nationalId}")
  PersonDto byNationalId(@PathVariable String nationalId) {
    return PersonDto.of(service.byNationalId(nationalId));
  }

  @GetMapping("/persons/me")
  PersonDto me() {
    return PersonDto.of(service.me());
  }

  @GetMapping("/persons")
  PersonPage search(@RequestParam(required = false) String region, @RequestParam(required = false) UUID employerId,
                    @RequestParam(required = false) UUID after, @RequestParam(defaultValue = "50") int limit) {
    List<PersonDto> items = service.search(region, employerId, after, limit).stream().map(PersonDto::of).toList();
    UUID next = items.size() == Math.min(Math.max(limit, 1), 200) ? items.get(items.size() - 1).id() : null;
    return new PersonPage(items, next);
  }

  @GetMapping(value = "/persons/{id}/photo", produces = "image/svg+xml")
  ResponseEntity<byte[]> photo(@PathVariable UUID id) {
    return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml"))
        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
        .body(service.photo(id));
  }

  @GetMapping("/employers/me")
  Employer myEmployer() {
    return service.myEmployer();
  }

  @GetMapping("/employers/{id}")
  Employer employer(@PathVariable UUID id) {
    return service.employer(id);
  }

  @GetMapping("/employers")
  List<Employer> employers() {
    return service.employers();
  }
}
