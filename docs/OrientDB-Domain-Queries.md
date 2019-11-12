# Orient DB Domain Queries
This document contains useful OrientDB Queries for the Domain Schema.

## Get Model As JSON
```SQL
SELECT @this.toJson('fetchPlan:*:-1') FROM Model WHERE id = '<my-model-id>';
```

## Find Model Permissions Where User is Gone
```SQL
SELECT * FROM `ModelUserPermissions` WHERE user.username IS NULL;
```

## Delete Models that are Orphaned
```SQL
DELETE FROM ModelUserPermissions WHERE user.username IS NULL;
DELETE FROM DataValue WHERE model IN (SELECT * FROM Model WHERE userPermissions = [NULL]);
DELETE FROM ModelSnapshot WHERE model IN (SELECT * FROM Model WHERE userPermissions = [NULL]);
DELETE FROM ModelOperation WHERE model IN (SELECT * FROM Model WHERE userPermissions = [NULL]);
DELETE FROM Model WHERE userPermissions = [NULL];
```
