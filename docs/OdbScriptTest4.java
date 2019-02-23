
import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.ODatabaseType;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.OElement;

public class OdbScriptTest4 {
  public static final String DB_NAME = "test";
  public static final String TEST_CLASS = "Test";
  public static final String TEST_ID = "TEST_ID";

  public static void main(String[] args) {
    final OrientDB odb = new OrientDB("remote:localhost", "root", "password", OrientDBConfig.defaultConfig());
    if (!odb.exists(DB_NAME)) {
      odb.create(DB_NAME, ODatabaseType.PLOCAL);
    }

    final ODatabaseSession db = odb.open(DB_NAME, "admin", "admin");

    final OSchema schema = db.getMetadata().getSchema();

    if (schema.existsClass(TEST_CLASS)) {
      schema.dropClass(TEST_CLASS);
    }

    final OClass childClass = schema.createClass(TEST_CLASS);
    childClass.createProperty("id", OType.STRING);
    
    db.command("CREATE INDEX TEST_ID ON Test (id) UNIQUE_HASH_INDEX");
    
    final OIndex<?> index = db.getMetadata().getIndexManager().getIndex(TEST_ID);
    
    final OElement doc = db.newElement(TEST_CLASS);
    doc.setProperty("id", "id1");
    doc.save();
    
    final ORecordId fromIndex = (ORecordId) index.get("id1");
    System.out.println(fromIndex);
    
    final boolean removed = index.remove("id1");
    System.out.println(removed);
    
    System.out.println(index.get("id1"));
    
    db.close();
    odb.close();
  }
}