import java.util.HashMap;

import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.ODatabaseType;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;

public class OdbScriptTest {
  public static final String SQL = "sql";
  public static final String DB_NAME = "test";
  
  public static void main(String[] args) {
    final OrientDB odb = new OrientDB("remote:localhost", "root", "root", OrientDBConfig.defaultConfig());
    if (!odb.exists(DB_NAME)) {
      odb.create(DB_NAME, ODatabaseType.PLOCAL);
    }
    final ODatabaseSession db = odb.open(DB_NAME, "admin", "admin");

    final String schema = 
        "CREATE CLASS Child;\n" + 
        "CREATE PROPERTY Child.id STRING;\n" +
        "CREATE CLASS Parent;\n" +
        "CREATE PROPERTY Parent.children LINKMAP Child;\n" + 
        "CREATE PROPERTY Parent.id STRING;";

    db.execute(SQL, schema, new HashMap<String, Object>());

    final String data = 
        "INSERT INTO Child SET id = \"child\";\n" +
        "INSERT INTO Parent SET children = {}, id = \"parent\";\n" +
        "LET newChild = SELECT FROM Child WHERE id = \"child\";\n" +
        "UPDATE Parent SET children[\"child\"] = $newChild[0];";

    db.execute(SQL, data, new HashMap<>());

    System.out.println("Child Exists: " + db.command("SELECT FROM Child", new HashMap<>()).next());
    System.out.println("Parent has Child: " + db.command("SELECT FROM Parent", new HashMap<>()).next());

    final String delete = 
        "LET parents = SELECT FROM Parent WHERE id = \"parent\";\n" + 
        "LET p = $parents[0];\n" + 
        "LET directChildrenToDelete = SELECT expand($p.children.`child`);\n" +
        "LET allChildrenToDelete = TRAVERSE children FROM (SELECT expand($directChildrenToDelete));\n" +
        "DELETE FROM (SELECT expand($allChildrenToDelete));\n" +
        "UPDATE (SELECT expand($p)) REMOVE children.`child`;";

    db.begin();
    db.execute(SQL, delete, new HashMap<>());
    db.commit();

    Long childCount = db
        .command("SELECT count(*) as count FROM Child", new HashMap<>())
        .next()
        .toElement()
        .getProperty("count");
    System.out.println("Child deleted: " + childCount);

    System.out.println("Parent not updated: " + db.command("SELECT FROM Parent", new HashMap<String, Object>()).next());

    db.close();
    odb.close();
  }
}
