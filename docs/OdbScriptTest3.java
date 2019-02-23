import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.ODatabaseType;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;

public class OdbScriptTest3 {
  public static final String DB_NAME = "test";
  public static final String TEST_CLASS = "Test";
  public static final String TEST_ID = "TEST_ID";

  public static void main(String[] args) {
    final OrientDB odb = new OrientDB("remote:localhost", "root", "root", OrientDBConfig.defaultConfig());
    if (!odb.exists(DB_NAME)) {
      odb.create(DB_NAME, ODatabaseType.PLOCAL);
    }

    final ODatabaseSession db = odb.open(DB_NAME, "admin", "admin");

    final OSchema schema = db.getMetadata().getSchema();

    if (schema.existsClass(TEST_CLASS)) {
      schema.dropClass(TEST_CLASS);
    }

    final OClass childClass = schema.createClass(TEST_CLASS);
    childClass.createProperty("id1", OType.STRING);
    childClass.createProperty("id2", OType.STRING);
    
    db.command("CREATE INDEX TEST_ID ON Test (id1, id2) UNIQUE");
    
    db.command("INSERT INTO Test SET id1 = '1', id2='2'");
    db.command("INSERT INTO Test SET id1 = '3', id2='4'");
    db.command("INSERT INTO Test SET id1 = '5', id2='6'");
    
    final OIndex<?> index = db.getMetadata().getIndexManager().getIndex(TEST_ID);
    
    final List<Object>keys = new ArrayList<Object>();
    keys.add(Arrays.asList("1", "2"));
    keys.add(Arrays.asList("3", "4"));
    
//    keys.add(new OCompositeKey(Arrays.asList("1", "2")));
//    keys.add(new OCompositeKey(Arrays.asList("2", "4")));
//    
    index.iterateEntries(keys, false).toEntries().forEach(entry -> {
    	System.out.println(entry.getValue().getRecord());
    });
    
    System.out.println("Done");
    
    db.close();
    odb.close();
  }
}