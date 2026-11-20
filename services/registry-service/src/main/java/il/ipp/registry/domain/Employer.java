package il.ipp.registry.domain;

import java.util.UUID;

public record Employer(UUID id, String name, String businessNumber, String sector, String region, String username) {}
