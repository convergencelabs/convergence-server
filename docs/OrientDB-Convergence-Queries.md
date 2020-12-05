# Orient DB Convergence Queries
This document contains useful OrientDB Queries for the Convergence Schema.

## Get domain databases for all domains
```SQL
SELECT
  databaseName, 
  namespace.id as namespace, 
  id
FROM
  Domain
```

## Get domain databases for a domain
```SQL
SELECT
  databaseName, 
  namespace.id as namespace, 
  id
FROM
  Domain
WHERE
  namespace.id = "convergence" AND
  id = "default";
```

## Get current versions of all domains
```SQL
SELECT
  domain.namespace,
  domain.id,
  max(delta.deltaNo) as version
FROM
  DomainDeltaHistory
WHERE
  status = "success"
GROUP BY
  domain.namespace,
  domain.id
ORDER BY
  domain.namespace,
  domain.id;
```

## Get any domains less than a specified version
```SQL
SELECT FROM (
  SELECT
    domain.namespace,
    domain.id,
    max(delta.deltaNo) as version
  FROM
    DomainDeltaHistory
  WHERE
    status = "success"
  GROUP BY
    domain.namespace,
    domain.id
  ORDER BY
    domain.namespace,
    domain.id
)
WHERE version < 10;
```

## Get all domains with an error status
```SQL
SELECT
  domain.namespace,
  domain.id,
  delta.deltaNo,
  message
FROM
  DomainDeltaHistory
WHERE
  status = "error"
ORDER BY
  domain.namespace,
  domain.id;
```
