package l1j.server.server.model.character.transform;

/** Immutable shared appearance and eligibility data. */
public record CTransformCommonData(int id, String name, int sprite, int minimumLevel, int causeMask, int option) {}
