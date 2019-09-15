sbtPod { label ->
  def containerName = "convergence-server"
  
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
        sh 'sbt stage'
      }
      
      stage('Publish Universal') {
        sh 'sbt universal:publish'
      }
    }
    
    container('docker') {
      stage('Docker Prep') { 
        sh '''
        cp -a src/docker/ target/docker
        cp -a target/universal/stage target/docker/stage
        '''
      }
    
      stage('Docker Build') {
        dir('target/docker') {
          dockerBuild(containerName)
        } 
      }

      stage('Docker Push') {
        dockerPush(containerName, ["latest", env.GIT_COMMIT])
      }
    }
  }
}
