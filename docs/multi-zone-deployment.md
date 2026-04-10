# Multi-Zone Collector Deployment

## Overview

The wafer-alarm system supports deploying multiple collector instances across
different network zones. Each collector binary is configured at startup with the
IDs of the source systems it owns, ensuring that each collector only pulls data
from sources in its network zone.

## Configuration

### Owned Source System IDs

Set `app.collector.owned-source-system-ids` to a comma-separated list of
source_system IDs that this collector instance is responsible for.

```yaml
# application.yml (or application-zone-a.yml)
app:
  collector:
    owned-source-system-ids: 1,2,3
```

Or via environment variable:

```bash
APP_COLLECTOR_OWNED_SOURCE_SYSTEM_IDS=1,2,3
```

Or via command-line argument:

```bash
java -jar wafer-alarm.jar --app.collector.owned-source-system-ids=1,2,3
```

**If left empty or unset, the collector will pull from ALL enabled source
systems.** This is the default for single-instance deployments.

### Finding Source System IDs

Source system IDs are assigned when you create a source system via the admin UI
(`/source-systems.html`) or the REST API (`GET /api/source-systems`). Each
source system has a numeric `id` field.

## Deployment Topology

### Single Instance (default)

No special configuration needed. The single binary runs the web server,
evaluator, and collector for all source systems.

```
+-------------------+
| VM: all-in-one    |
| web + eval + coll |
|  (all sources)    |
+-------------------+
        |
    [MySQL]
```

### Multi-Zone

Deploy one web/eval VM and one collector VM per network zone:

```
+-------------------+     +-------------------+     +-------------------+
| VM: web-eval      |     | VM: collector-a   |     | VM: collector-b   |
| web + evaluator   |     | collector only    |     | collector only    |
| (no collector)    |     | owns: 1,2         |     | owns: 3,4         |
+-------------------+     +-------------------+     +-------------------+
        |                         |                         |
        +----------- [MySQL] ----+-------------------------+
                                  |                         |
                          [Zone A sources]          [Zone B sources]
```

**Web/eval VM**: Disable the collector by not assigning any source system IDs
that exist. Or set `app.collector.poll-interval-seconds` to a very high value.

**Collector VMs**: Each gets a disjoint set of source system IDs matching the
sources reachable from its network zone.

## Sample Configuration Files

### Web/Eval VM (`application-webeval.yml`)

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql-host:3306/wafer_alarm
    username: wafer_app
    password: ${DB_PASSWORD}

app:
  collector:
    poll-interval-seconds: 999999  # effectively disabled
  evaluator:
    pool-size: 4
    tick-interval-seconds: 60
    overlap-window-minutes: 10
```

### Collector VM Zone A (`application-zone-a.yml`)

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql-host:3306/wafer_alarm
    username: wafer_app
    password: ${DB_PASSWORD}

app:
  collector:
    owned-source-system-ids: 1,2
    pool-size: 2
    poll-interval-seconds: 60
  evaluator:
    tick-interval-seconds: 999999  # effectively disabled on collector VM
```

### Collector VM Zone B (`application-zone-b.yml`)

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql-host:3306/wafer_alarm
    username: wafer_app
    password: ${DB_PASSWORD}

app:
  collector:
    owned-source-system-ids: 3,4
    pool-size: 2
    poll-interval-seconds: 60
  evaluator:
    tick-interval-seconds: 999999  # effectively disabled on collector VM
```

## Overlap Detection

Each collector instance registers itself in the `collector_registration` table
at startup and sends heartbeats every 60 seconds. The system health page
(`/health.html`) and API (`GET /api/health/collectors`) show:

- All registered collectors and their owned source system IDs
- Heartbeat status (active, stale, or offline)
- **Overlap warnings** when two or more collectors claim the same source system

If overlapping ownership is detected, the health page displays a prominent
warning with the affected source system IDs and collector instances.

## Troubleshooting

**Collector not pulling data**: Check that the source system IDs in the config
match the actual IDs in the database. Verify via `GET /api/source-systems`.

**Duplicate measurements**: Ensure no two collectors have overlapping
`owned-source-system-ids`. Check the health page for overlap warnings.

**Stale heartbeat**: If a collector's heartbeat shows as stale (>2 minutes), the
collector process may have stopped or lost database connectivity.
