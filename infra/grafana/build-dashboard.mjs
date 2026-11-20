// Generates infra/grafana/dashboards/ipp-overview.json (run: node infra/grafana/build-dashboard.mjs).
// Kept as code so panels stay consistent and reviewable in git.
import { writeFileSync } from 'node:fs';

const ds = { type: 'prometheus', uid: 'ipp-prom' };
let id = 1;
const stat = (title, expr, x, y, opts = {}) => ({
  id: id++, type: 'stat', title, datasource: ds, gridPos: { x, y, w: 6, h: 4 },
  targets: [{ refId: 'A', expr, legendFormat: opts.legend ?? '' }],
  fieldConfig: { defaults: { unit: opts.unit ?? 'none', decimals: opts.decimals ?? 1,
    thresholds: { mode: 'absolute', steps: opts.steps ?? [{ color: 'green', value: null }] } }, overrides: [] },
  options: { reduceOptions: { calcs: ['lastNotNull'] }, colorMode: 'background', graphMode: 'area', textMode: 'auto' },
  description: opts.description ?? '',
});
const ts = (title, targets, x, y, opts = {}) => ({
  id: id++, type: 'timeseries', title, datasource: ds, gridPos: { x, y, w: opts.w ?? 12, h: 8 },
  targets: targets.map(([expr, legend], i) => ({ refId: String.fromCharCode(65 + i), expr, legendFormat: legend })),
  fieldConfig: { defaults: { unit: opts.unit ?? 'short', custom: { lineWidth: 2, fillOpacity: 10 },
    thresholds: { mode: 'absolute', steps: opts.steps ?? [{ color: 'green', value: null }] } }, overrides: [] },
  options: { legend: { displayMode: 'table', placement: 'bottom', calcs: ['lastNotNull', 'max'] }, tooltip: { mode: 'multi' } },
  description: opts.description ?? '',
});

const panels = [
  stat('Services up', 'count(up == 1)', 0, 0, { decimals: 0, description: 'Scraped service instances that are up',
    steps: [{ color: 'red', value: null }, { color: 'green', value: 9 }] }),
  stat('Eligibility p95 (ms)', 'max(ipp_eligibility_check_seconds{quantile="0.95"}) * 1000', 6, 0,
    { unit: 'ms', description: 'Target < 150ms', steps: [{ color: 'green', value: null }, { color: 'orange', value: 100 }, { color: 'red', value: 150 }] }),
  stat('Cache hit ratio', 'sum(rate(ipp_eligibility_cache_total{result="hit"}[5m])) / clamp_min(sum(rate(ipp_eligibility_cache_total[5m])), 1e-9)',
    12, 0, { unit: 'percentunit', description: 'Target > 90%', steps: [{ color: 'red', value: null }, { color: 'orange', value: 0.8 }, { color: 'green', value: 0.9 }] }),
  stat('Open circuit breakers', 'sum(resilience4j_circuitbreaker_state{state="open"}) or vector(0)', 18, 0,
    { decimals: 0, description: 'policy / mockState breakers in permit-service', steps: [{ color: 'green', value: null }, { color: 'red', value: 1 }] }),

  ts('Requests per second by service', [['sum by (job) (rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[1m]))', '{{job}}']],
    0, 4, { unit: 'reqps' }),
  ts('Eligibility check latency', [
    ['max(ipp_eligibility_check_seconds{quantile="0.5"}) * 1000', 'p50'],
    ['max(ipp_eligibility_check_seconds{quantile="0.95"}) * 1000', 'p95'],
    ['max(ipp_eligibility_check_seconds{quantile="0.99"}) * 1000', 'p99'],
  ], 12, 4, { unit: 'ms', steps: [{ color: 'green', value: null }, { color: 'red', value: 150 }] }),

  ts('HTTP 5xx per second', [['sum by (job) (rate(http_server_requests_seconds_count{status=~"5.."}[1m]))', '{{job}}'], ['vector(0)', 'baseline']], 0, 12, { unit: 'reqps' }),
  ts('HTTP 4xx per second (incl. 403/404 from RBAC/ABAC)', [['sum by (job, status) (rate(http_server_requests_seconds_count{status=~"4.."}[1m]))', '{{job}} {{status}}'], ['vector(0)', 'baseline']], 12, 12, { unit: 'reqps' }),

  ts('Kafka listener messages per second', [['sum by (job, name) (rate(spring_kafka_listener_seconds_count[1m]))', '{{job}} {{name}}']], 0, 20, { unit: 'short' }),
  ts('Circuit breaker state (1 = in state)', [['max by (name, state) (resilience4j_circuitbreaker_state)', '{{name}} {{state}}']], 12, 20),

  ts('JVM heap used', [['sum by (job) (jvm_memory_used_bytes{area="heap"})', '{{job}}']], 0, 28, { unit: 'bytes' }),
  ts('DB connections in use (HikariCP → PgBouncer)', [['sum by (job) (hikaricp_connections_active)', '{{job}} active'], ['sum by (job) (hikaricp_connections_pending)', '{{job}} pending']], 12, 28),
];

const dashboard = {
  uid: 'ipp-overview', title: 'IPP – System overview', tags: ['ipp'], timezone: 'Asia/Jerusalem',
  schemaVersion: 39, version: 1, refresh: '10s', time: { from: 'now-30m', to: 'now' },
  panels,
  description: 'IPP demo: hot-path latency, cache, resilience, Kafka, JVM, DB pool. Data: Prometheus scraping /actuator/prometheus.',
};
writeFileSync(new URL('./dashboards/ipp-overview.json', import.meta.url), JSON.stringify(dashboard, null, 2));
console.log(`dashboard with ${panels.length} panels written`);
