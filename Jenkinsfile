node {

  stage 'Checkout'
  checkout scm
  
  stage 'Compile'
  sh 'sbt compile'
  
  stage 'Test'
  sh 'sbt test'
  
  stage 'Server Node Pack'
  sh 'sbt serverNode/pack'
  
  stage 'Server Node Docker'
  sh 'sbt serverNode/dockerBuild'
 }
 