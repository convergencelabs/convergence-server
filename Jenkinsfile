node {

  stage 'Checkout'
  checkout scm
  
  
  stage 'Compile'
  sh 'sbt compile'
  
  
  stage 'Test'
  sh 'sbt test'
  
  
  stage 'Server Node Pack'
  sh 'sbt serverNode/pack'
  
  
  stage 'Server Node Docker (Dev)'
  echo "Current build number is ${env.BUILD_NUMBER}"
  
  sh '''
    echo "Creating docker target directory"
    cp -a server-node/src/docker/ server-node/target/docker
    cp -a server-node/target/pack server-node/target/docker/pack
    
    echo "Building the container"
    docker build -t nexus.convergencelabs.tech:18444/convergence-server-node server-node/target/docker
    
    echo "Publishing the container"
    docker push nexus.convergencelabs.tech:18444/convergence-server-node
  '''
 }
 