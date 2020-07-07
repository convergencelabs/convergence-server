To release the Convergence Server, follow these steps:

```shell script
sbt clean; dist/clean
sbt compile
sbt test

sbt publishSigned

sbt dist/stage
sbt dist/publishSigned
```
