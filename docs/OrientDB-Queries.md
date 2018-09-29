# Get domain database for domains
```
SELECT
  *
FROM
  DomainDatabase
WHERE
  domain.namespace = "alec" AND
  domain.id = "livefile-dev";
```

Database: 8038374697382339574
Username: 24acceb5-4e61-47b2-9a8e-dad8acd62a7c
Password: e17ff47a-8fd3-4d90-a4d1-1f57cf503b1a



# Get Current Version of All Domains
```    
(SELECT
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

# Get any domains less than a specified version
```
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

# Get Any Domain With an Error
```    
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
