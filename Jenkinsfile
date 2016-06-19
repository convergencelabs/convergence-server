node {

  stage 'Checkout'
  checkout scm
  
  stage 'Compile'
  sh '/usr/local/sbt/bin/sbt compile'
  
  stage 'Test'
  sh '/usr/local/sbt/bin/sbt test'
  
  stage 'Docker'
  sh 'sbt docker'
 }
 