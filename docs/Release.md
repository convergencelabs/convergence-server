To release the Convergence Server follow these steps:

```shell script
sbt clean dist/clean
sbt compile
sbt test

sbt dist/stage

sbt publishSigned
sbt dist/publishSigned
```
