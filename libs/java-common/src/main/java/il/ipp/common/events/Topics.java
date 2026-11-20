package il.ipp.common.events;

/** Topic names follow &lt;domain&gt;.&lt;entity&gt;.&lt;event&gt;.v1 */
public final class Topics {
  public static final String PERSON_UPSERTED = "registry.person.upserted.v1";
  public static final String PERMIT_STATUS_CHANGED = "permits.permit.status-changed.v1";
  public static final String CROSSING_RECORDED = "crossing.event.recorded.v1";
  public static final String SANCTION_IMPOSED = "enforcement.sanction.imposed.v1";
  public static final String SANCTION_LIFTED = "enforcement.sanction.lifted.v1";
  public static final String AUDIT_DECISION = "audit.decision.v1";
  public static final String AUDIT_INGEST = "audit.ingest.v1";

  private Topics() {}
}
