sbtPod { label ->
  def containerName = "convergence-server-node"
  
  runInNode(label) {
    injectIvyCredentials()
      
    stage('Compile') { 
      container('sbt') {
        sh 'sbt -d -J-Xmx3G -J-Xss5M compile'
      }
    }
    
    stage('Test') {
      container('sbt') {
        sh 'sbt -d -J-Xmx3G -J-Xss5M test'
      }
    }
    
    stage('Package') {
      container('sbt') {
        sh 'sbt serverNode/stage'
      }
    }
    
    stage('Docker Prep') { 
      sh '''
      cp -a server-node/src/docker/ server-node/target/docker
      cp -a server-node/target/universal/stage server-node/target/docker/stage
      '''
    }
    
    stage('Docker Build') {
      container('docker') {
        dir('server-node/target/docker') {
          dockerBuild(containerName)
        }
      }
    }

    stage('Docker Push') {
      container('docker') {
        dockerPush(containerName, ["latest", env.GIT_COMMIT])
      }
    }
  }
}
