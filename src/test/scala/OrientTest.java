import com.orientechnologies.orient.core.db.ODatabaseType;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.OElement;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.executor.OResultSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OrientTest {
    private static final boolean transactions = true;

    public static void main(String[] args) {
        String url = "memory:target/orientdb/PersistenceStoreSpec/OrientTest";
        OrientDB orientDB = new OrientDB(url, OrientDBConfig.defaultConfig());

        String dbName = "OrientTest";

        try {
            System.out.println("Creating database");
            orientDB.create(dbName, ODatabaseType.MEMORY);
            ODatabaseDocument db = orientDB.open(dbName, "admin", "admin");

            System.out.println("Creating schema");
            OSchema schema = db.getMetadata().getSchema();

            OClass permissionClass = schema.createClass("Permission");
            OClass targetClass = schema.createClass("Target");

            permissionClass.createProperty("id", OType.STRING);
            permissionClass.createProperty("target", OType.LINKSET, targetClass);

            targetClass.createProperty("id", OType.STRING);
            targetClass.createProperty("permissions", OType.LINKSET, permissionClass);
            targetClass.createIndex("target.id", OClass.INDEX_TYPE.UNIQUE, "id");

            OElement target1 = db.newElement("Target");
            target1.setProperty("id", "t1");
            target1.save();
            System.out.println("Done creating schema");

            // First Run
            Set<String> permissions1 = new HashSet<>();
            permissions1.add("p1");
            permissions1.add("p2");
            addPermissions(db, permissions1, "t1");

            // Second Run
            Set<String> permissions2 = new HashSet<>();
            permissions2.add("p3");
            permissions2.add("p4");
            addPermissions(db, permissions2, "t1");

        } finally {
            orientDB.drop(dbName);
        }
    }

    private static void addPermissions(ODatabaseDocument db, Set<String> permissions, String targetId) {

        if (transactions) {
            db.begin();
        }

        System.out.println("Adding permissions to target: " + targetId);
        OIndex<?> index = db.getMetadata().getIndexManager().getIndex("target.id");

        OIdentifiable doc = (OIdentifiable) index.get(targetId);
        ORID targetRid = doc.getIdentity();

        Set<ORID> permissionRids = permissions
                .stream()
                .map(permission -> {
                    ODocument newPermission = db.newInstance("Permission");
                    newPermission.setProperty("permission", permission);
                    newPermission.setProperty("target", targetRid);
                    db.save(newPermission);
                    return newPermission.getIdentity();
                })
                .collect(Collectors.toSet());

        String command = "UPDATE :target SET permissions = permissions || :permissions";
        Map<String, Object> params = new HashMap<>();
        params.put("target", targetRid);
        params.put("permissions", permissionRids);

        OResultSet results = db.command(command, params);
        OElement result = results.next().toElement();
        long count = result.getProperty("count");
        System.out.println("Mutated targets: " + count);

        results.close();

        if (transactions) {
            db.commit();
        }
    }
}
