BEGIN;

DELETE FROM Namespace;
DELETE FROM Domain;

DELETE FROM Permission;
DELETE FROM User;
DELETE FROM Role;
DELETE FROM UserRole;

LET n1 = INSERT INTO Namespace SET id = "namespace1", displayName = "Namespace 1";
LET d1 = INSERT INTO Domain SET namespace = $n1[0], id = "domain1", displayName = "Domain 1", status = "", statusMessage = "", databaseName = "dbName", databaseUsername = "dbUsername", databasePassword = "dbPassword", databaseAdminUsername = "dbAdminUsername", databaseAdminPassword = "dbAdminPassword";

LET p1 = INSERT INTO Permission SET id = 'permission1', name = 'Permission 1', description = 'The first permission';
LET p2 = INSERT INTO Permission SET id = 'permission2', name = 'Permission 2', description = 'The second permission';
LET p3 = INSERT INTO Permission SET id = 'permission3', name = 'Permission 3', description = 'The third permission';

LET r1 = INSERT INTO Role SET name = 'Role 1', description = 'The first role', permissions = [$p1[0], $p2[0]];
LET r2 = INSERT INTO Role SET name = 'Role 2', description = 'The second role', permissions = [$p3[0]];

LET u1 = INSERT INTO User SET username = 'test1', displayName = 'Test One', firstName = 'Test', lastName = 'One', email='test1@example.com', password='fake', bearerToken='token';

INSERT INTO UserRole SET user = $u1[0], role = $r1[0], target = $d1[0];
INSERT INTO UserRole SET user = $u1[0], role = $r2[0];
COMMIT;


SELECT expand(set(role.permissions)) FROM UserRole WHERE user.username = 'test1' AND target IN (SELECT FROM Domain WHERE namespace.id = 'namespace1' AND id = 'domain1');

SELECT expand(set(role.permissions)) FROM UserRole WHERE user.username = 'test1' AND target IS NULL;
