## Server Overlaps Client
### Transformation Case
```
ServerOp(index: 2, delete: 5, insert: "XYZ") 
ClientOp(index: 4, delete: 4, insert: "QRS")

Value:      ABCDEFGHIJ
Indices:    0123456789

ServerOp:     ^---^         
              XYZ
              
Client Op:      ^--^        
                QRS
                
ServerOp'(index: 2, delete: 5, insert: "XYZ") 
ClientOp'(index: 5, delete: 1, insert: "")
```

### Server State Path
```
Value:      ABCDEFGHIJ
Indices:    0123456789

ServerOp:     ^---^         
               XYZ                  

Value:      ABXYZHIJ
Indices:    01234567

ClientOp':       ^           

Value:      ABXYZIJ
Indices:    0123456           
```


### Client State Path
```
Value:      ABCDEFGHIJ
Indices:    0123456789

ClientOp:       ^--^        
                QRS               

Value:      ABCDQRSIJ
Indices:    012345678

ServerOp':    ^---^
              XYZ           

Value:      ABXYZIJ
Indices:    0123456
```