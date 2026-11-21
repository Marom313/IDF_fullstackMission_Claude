#!/usr/bin/env node
// Generates infra/nifi/flow/ipp-ingest.flow.json, a NiFi 2.x flow definition that is imported once in the UI
// (drag "Process Group" onto the canvas → upload icon → choose the file). Kept as code so the flow is
// reviewable in a diff and regenerated, not hand-edited. Run: node scripts/ingest/build-nifi-flow.mjs
//
//  ListFile(/ingest/inbox) → FetchFile(delete source) → ExecuteStreamCommand(java -jar ingest-verifier.jar)
//     ├─ output stream / nonzero status → SplitText(1 line) → EvaluateJsonPath(_topic,_key)
//     │                                   → ReplaceText(strip _topic,_key) → PublishKafka(${ipp.topic}, key ${ipp.key})
//     └─ original → RouteOnAttribute(execution.status == 0)
//          ├─ accepted  → PutFile(/ingest/archive)
//          └─ unmatched → PutFile(/ingest/quarantine) + ReplaceText(${execution.error}) → UpdateAttribute(.reason) → PutFile
import { createHash } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const NIFI = '2.9.0';
const GROUP = id('group');

/** Stable ids: same input → same file, so a regenerated flow shows no noise in git. */
function id(name) {
  const h = createHash('sha256').update(`ipp-ingest:${name}`).digest('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20, 32)}`;
}

const std = { group: 'org.apache.nifi', artifact: 'nifi-standard-nar', version: NIFI };
const kafkaNar = { group: 'org.apache.nifi', artifact: 'nifi-kafka-nar', version: NIFI };

function processor(name, type, bundle, x, y, properties, { autoTerminate = [], schedule = '0 sec', comments = '' } = {}) {
  return {
    identifier: id(name), instanceIdentifier: id(`${name}:instance`), name, comments,
    position: { x, y }, type, bundle, properties, propertyDescriptors: {}, style: {},
    schedulingPeriod: schedule, schedulingStrategy: 'TIMER_DRIVEN', executionNode: name === 'List inbox' ? 'PRIMARY' : 'ALL',
    penaltyDuration: '30 sec', yieldDuration: '1 sec', bulletinLevel: 'WARN', runDurationMillis: 0,
    concurrentlySchedulableTaskCount: 1, autoTerminatedRelationships: autoTerminate, scheduledState: 'ENABLED',
    retryCount: 10, retriedRelationships: [], backoffMechanism: 'PENALIZE_FLOWFILE', maxBackoffPeriod: '10 mins',
    componentType: 'PROCESSOR', groupIdentifier: GROUP,
  };
}

function connection(from, to, relationships) {
  const name = `${from} → ${to} [${relationships.join(',')}]`;
  const end = (n) => ({ id: id(n), type: 'PROCESSOR', groupId: GROUP, name: n });
  return {
    identifier: id(name), instanceIdentifier: id(`${name}:instance`), name: '', source: end(from), destination: end(to),
    labelIndex: 1, zIndex: 0, selectedRelationships: relationships,
    backPressureObjectThreshold: 10000, backPressureDataSizeThreshold: '1 GB', flowFileExpiration: '0 sec',
    prioritizers: [], bends: [], loadBalanceStrategy: 'DO_NOT_LOAD_BALANCE', partitioningAttribute: '',
    loadBalanceCompression: 'DO_NOT_COMPRESS', componentType: 'CONNECTION', groupIdentifier: GROUP,
  };
}

const KAFKA_SERVICE = id('Kafka');
const controllerServices = [{
  identifier: KAFKA_SERVICE, instanceIdentifier: id('Kafka:instance'), name: 'Kafka (kafka:9092)',
  comments: 'PLAINTEXT inside the Docker network, like the Spring services. Production: SASL_SSL.',
  type: 'org.apache.nifi.kafka.service.Kafka3ConnectionService',
  bundle: { group: 'org.apache.nifi', artifact: 'nifi-kafka-3-service-nar', version: NIFI },
  properties: { 'bootstrap.servers': 'kafka:9092' }, propertyDescriptors: {},
  controllerServiceApis: [{
    type: 'org.apache.nifi.kafka.service.api.KafkaConnectionService',
    bundle: { group: 'org.apache.nifi', artifact: 'nifi-kafka-service-api-nar', version: NIFI },
  }],
  scheduledState: 'ENABLED', bulletinLevel: 'WARN', componentType: 'CONTROLLER_SERVICE', groupIdentifier: GROUP,
}];

const P = 'org.apache.nifi.processors.standard.';
const processors = [
  processor('List inbox', `${P}ListFile`, std, 0, 0, {
    'Input Directory': '/ingest/inbox',
    'File Filter': '[^\\.].*\\.zip',            // dot-files (".x.zip.part" while being written) are ignored
    'Recurse Subdirectories': 'false',
    'Minimum File Age': '5 sec',                // second guard against half-written files
  }, { schedule: '10 sec', comments: 'Primary node only: one listing, no double ingest in a cluster.' }),
  processor('Fetch batch', `${P}FetchFile`, std, 0, 200, {
    'File to Fetch': '${absolute.path}/${filename}',
    'Completion Strategy': 'Delete File',       // the copy lives on in NiFi until archive/quarantine
  }, { autoTerminate: ['not.found', 'permission.denied', 'failure'] }),
  processor('Verify (signature, SHA-256, schema)', `${P}ExecuteStreamCommand`, std, 0, 400, {
    'Command Path': 'java',
    'Command Arguments': '-jar;/opt/ipp/ingest-verifier.jar;--key;/opt/ipp/keys/ingest.pub.pem;--receipts;/ingest/.receipts;--name;${filename}',
    'Argument Delimiter': ';',
    'Ignore STDIN': 'false',
    'Working Directory': '/tmp',
  }, { comments: 'Exit 0 accepted · 3 quarantined · 2 config error. stdout = NDJSON for Kafka, stderr = reason.' }),
  processor('One message per line', `${P}SplitText`, std, -450, 600, {
    'Line Split Count': '1', 'Header Line Count': '0', 'Remove Trailing Newlines': 'true',
  }, { autoTerminate: ['original', 'failure'] }),
  processor('Read topic and key', `${P}EvaluateJsonPath`, std, -450, 800, {
    Destination: 'flowfile-attribute', 'Return Type': 'scalar',
    'ipp.topic': '$._topic', 'ipp.key': '$._key',
  }, { autoTerminate: ['unmatched', 'failure'] }),
  processor('Strip routing fields', `${P}ReplaceText`, std, -450, 1000, {
    'Replacement Strategy': 'Regex Replace', 'Evaluation Mode': 'Entire text',
    'Regular Expression': '^\\{"_topic":"[^"]*","_key":"[^"]*",', 'Replacement Value': '{',
  }, { autoTerminate: ['failure'] }),
  processor('Publish to Kafka', 'org.apache.nifi.kafka.processors.PublishKafka', kafkaNar, -450, 1200, {
    'Kafka Connection Service': KAFKA_SERVICE,
    'Topic Name': '${ipp.topic}',
    'Kafka Key': '${ipp.key}',                  // personId → all events of one person stay in order
    'Transactions Enabled': 'false',
    'Failure Strategy': 'Rollback',             // Kafka down → keep the message and retry, never drop it
  }, { autoTerminate: ['success'] }),
  processor('Accepted?', `${P}RouteOnAttribute`, std, 450, 600, {
    'Routing Strategy': 'Route to Property name',
    accepted: "${execution.status:equals('0')}",
  }),
  processor('Archive', `${P}PutFile`, std, 300, 800, {
    Directory: '/ingest/archive', 'Conflict Resolution Strategy': 'replace', 'Create Missing Directories': 'true',
  }, { autoTerminate: ['success', 'failure'] }),
  processor('Quarantine', `${P}PutFile`, std, 650, 800, {
    Directory: '/ingest/quarantine', 'Conflict Resolution Strategy': 'replace', 'Create Missing Directories': 'true',
  }, { autoTerminate: ['success', 'failure'] }),
  processor('Reason text', `${P}ReplaceText`, std, 1000, 800, {
    'Replacement Strategy': 'Always Replace', 'Evaluation Mode': 'Entire text',
    'Replacement Value': '${execution.error}\n',
  }, { autoTerminate: ['failure'] }),
  processor('Name .reason', 'org.apache.nifi.processors.attributes.UpdateAttribute',
    { group: 'org.apache.nifi', artifact: 'nifi-update-attribute-nar', version: NIFI }, 1000, 1000, {
      filename: '${filename}.reason',
    }),
  processor('Write reason', `${P}PutFile`, std, 1000, 1200, {
    Directory: '/ingest/quarantine', 'Conflict Resolution Strategy': 'replace', 'Create Missing Directories': 'true',
  }, { autoTerminate: ['success', 'failure'] }),
];

const connections = [
  connection('List inbox', 'Fetch batch', ['success']),
  connection('Fetch batch', 'Verify (signature, SHA-256, schema)', ['success']),
  connection('Verify (signature, SHA-256, schema)', 'One message per line', ['output stream', 'nonzero status']),
  connection('Verify (signature, SHA-256, schema)', 'Accepted?', ['original']),
  connection('One message per line', 'Read topic and key', ['splits']),
  connection('Read topic and key', 'Strip routing fields', ['matched']),
  connection('Strip routing fields', 'Publish to Kafka', ['success']),
  connection('Publish to Kafka', 'Publish to Kafka', ['failure']),     // retry loop, visible as a queue in the UI
  connection('Accepted?', 'Archive', ['accepted']),
  connection('Accepted?', 'Quarantine', ['unmatched']),
  connection('Accepted?', 'Reason text', ['unmatched']),
  connection('Reason text', 'Name .reason', ['success']),
  connection('Name .reason', 'Write reason', ['success']),
];

const flow = {
  flowContents: {
    identifier: GROUP, instanceIdentifier: id('group:instance'), name: 'IPP ingest (whitening)',
    comments: 'Crossing batches from outside: verify Ed25519 manifest signature + SHA-256 + schema, publish to Kafka, '
      + 'archive or quarantine. ALL DATA IS MOCK. See docs/NIFI.md.',
    position: { x: 0, y: 0 }, processGroups: [], remoteProcessGroups: [], processors, inputPorts: [], outputPorts: [],
    connections, labels: [], funnels: [], controllerServices,
    defaultFlowFileExpiration: '0 sec', defaultBackPressureObjectThreshold: 10000, defaultBackPressureDataSizeThreshold: '1 GB',
    flowFileConcurrency: 'UNBOUNDED', flowFileOutboundPolicy: 'STREAM_WHEN_AVAILABLE', componentType: 'PROCESS_GROUP',
  },
  externalControllerServices: {}, parameterContexts: {}, parameterProviders: {}, flowEncodingVersion: '1.0', latest: false,
};

const out = join(ROOT, 'infra', 'nifi', 'flow', 'ipp-ingest.flow.json');
writeFileSync(out, JSON.stringify(flow, null, 2) + '\n');
console.log(`wrote ${out} (${processors.length} processors, ${connections.length} connections)`);
