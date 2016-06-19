node {

  stage 'Checkout'
  checkout scm
  
  stage 'Compile'
  sh 'set'
  
  stage 'Test'
  sh 'sbt test'
  
  stage 'Docker'
  sh 'sbt docker'
 }
 