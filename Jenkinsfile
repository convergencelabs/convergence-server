sbtPod { label ->
  def containerName = "convergence-server-node"
  
  runInNode(label) {
      
    container('sbt') {
      injectIvyCredentials()
      
      stage('Compile') {
        sh 'sbt -J-Xmx3G -J-Xss5M compile'
      }
      
      stage('Test') {
        sh 'sbt -J-Xmx3G -J-Xss5M test'
      }
      
      stage('Package') {
        sh 'sbt serverNode/stage'
      }
      
      stage('Publish Universal') {
        sh 'sbt serverNode/universal:publish'
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
