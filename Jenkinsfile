node {

  stage 'Checkout'
  checkout scm
  
  stage 'Compile'
  sh 'sbt compile'
  
  stage 'Test'
  sh 'sbt test'
  
  stage 'Docker'
  sh 'sbt docker'
 }
 