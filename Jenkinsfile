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
  echo "Current build number is ${env.BUILD_NUMBER}
  sh 'docker build -t convergence-server-node'
 }
 