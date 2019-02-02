import java.util.HashMap;
import java.util.Map;

import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.ODatabaseType;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.OElement;

public class OdbScriptTest2 {
  public static final String SQL = "sql";
  public static final String DB_NAME = "test";
  public static final String CHILD = "Child";
  public static final String PARENT = "Parent";

  public static void main(String[] args) {
    final OrientDB odb = new OrientDB("remote:localhost", "root", "password", OrientDBConfig.defaultConfig());
    if (!odb.exists(DB_NAME)) {
      odb.create(DB_NAME, ODatabaseType.PLOCAL);
    }

    final ODatabaseSession db = odb.open(DB_NAME, "admin", "admin");

    final OSchema schema = db.getMetadata().getSchema();

    if (schema.existsClass(CHILD)) {
      schema.dropClass(CHILD);
    }

    final OClass childClass = schema.createClass("Child");
    childClass.createProperty("id", OType.STRING);

    if (schema.existsClass(PARENT)) {
      schema.dropClass(PARENT);
    }

    final OClass parentClass = schema.createClass("Parent");
    parentClass.createProperty("id", OType.STRING);
    parentClass.createProperty("children", OType.LINKMAP, childClass);

    //
    // Create the Data
    //
    
    // Child
    final OElement childDoc = db.newElement("Child");
    childDoc.setProperty("id", "child");
    childDoc.save();

    // Parent
    final OElement parentDoc = db.newElement("Parent");
    parentDoc.setProperty("id", "parent");
    final Map<String, OElement> children = new HashMap<>();
    children.put("child", childDoc);
    parentDoc.setProperty("children", children);
    parentDoc.save();
    
    // Check The Current Database State

    System.out.println("Child exists: " + db.command("SELECT FROM Child", new HashMap<>()).next());
    System.out.println("Parent has Child: " + db.command("SELECT FROM Parent", new HashMap<>()).next());

    // This script should delete the child, and remove the child from the parent's map.
    final String delete = "LET parents = SELECT FROM Parent WHERE id = \"parent\";\n" 
        + "LET p = $parents[0];\n"
        + "LET directChildrenToDelete = SELECT expand($p.children.`child`);\n"
        + "LET allChildrenToDelete = TRAVERSE children FROM (SELECT expand($directChildrenToDelete));\n"
        + "DELETE FROM (SELECT expand($allChildrenToDelete));\n"
        + "UPDATE (SELECT expand($p)) REMOVE children.`child`;";

    // Update inside a transaction
    db.begin();
    db.execute(SQL, delete, new HashMap<>());
    db.commit();

    // Check the updated state
    final Long childCount = db
        .command("SELECT count(*) as count FROM Child", new HashMap<>())
        .next()
        .toElement()
        .getProperty("count");
    System.out.println("Child deleted. Number of Child documents: " + childCount);

    final OElement updatedParent = db
        .command("SELECT FROM Parent", new HashMap<String, Object>())
        .next()
        .toElement();
    System.out.println("Parent not updated: " + updatedParent);
    
    final Map<String, OElement> updatedChildren = updatedParent.getProperty("children");
    System.out.println("Updated children: " + updatedChildren);
    
    final OElement updatedChild = updatedChildren.get(CHILD);
    System.out.println("Child does not exist: " + updatedChild);
    
    db.close();
    odb.close();
  }
}