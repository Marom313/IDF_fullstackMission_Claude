// Runs once on first Mongo init. Least-privilege users per service.
// audit_svc can only INSERT and FIND in ipp_audit.audit (append-only enforced by the database, not just code);
// it may also update the dlt collection (replayed flag).
const auditDb = db.getSiblingDB("ipp_audit");
auditDb.createRole({
  role: "auditAppendOnly",
  privileges: [
    { resource: { db: "ipp_audit", collection: "audit" }, actions: ["find", "insert", "createIndex", "listIndexes"] },
    { resource: { db: "ipp_audit", collection: "dlt" }, actions: ["find", "insert", "update", "createIndex", "listIndexes"] },
    { resource: { db: "ipp_audit", collection: "" }, actions: ["listCollections", "createCollection"] },
  ],
  roles: [],
});
auditDb.createUser({
  user: "audit_svc",
  pwd: process.env.MONGO_AUDIT_PASSWORD,
  roles: [{ role: "auditAppendOnly", db: "ipp_audit" }],
});

// policy-service (built by the cloud agent) – read/write on its own database only
db.getSiblingDB("ipp_policies").createUser({
  user: "policy_svc",
  pwd: process.env.MONGO_POLICY_PASSWORD,
  roles: [{ role: "readWrite", db: "ipp_policies" }],
});
